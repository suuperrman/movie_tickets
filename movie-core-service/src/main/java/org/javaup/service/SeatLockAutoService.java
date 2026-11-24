package org.javaup.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.entity.Order;
import org.javaup.entity.Schedule;
import org.javaup.entity.ScheduleSeat;
import org.javaup.mapper.OrderMapper;
import org.javaup.mapper.ScheduleMapper;
import org.javaup.mapper.ScheduleSeatMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 座位锁定超时幂等释放服务
 *
 * 与 SeatStateService 配合：Redis HASH 做原子判定，DB 做持久化落库。
 * ┌───────────────────────────────────────────────────────────────────────┐
 * │ 1. Redis releaseExpired：status=1 且 expireAt<=now 且归属该订单 → 置 0 │
 * │    ├── 返回 2（尚未到期）→ 保留全局 ZSET 条目，下一轮再试               │
 * │    ├── 返回 1（已售/已释放/归属不符）→ 移除队列条目，不做副作用          │
 * │    └── 返回 0（HASH 已释放）→ 继续走 DB                               │
 * │ 2. DB UPDATE WHERE status=1 AND lock_expire_at<=now（行级乐观锁）      │
 * │    rows=0 → 已被支付/取消/并发释放，直接结束                            │
 * │ 3. schedule.available_seat +1                                          │
 * │ 4. 订单 0→2（自动取消），WHERE status=0 保证不覆盖并发支付结果           │
 * └───────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockAutoService {

    private final ScheduleSeatMapper scheduleSeatMapper;
    private final OrderMapper orderMapper;
    private final ScheduleMapper scheduleMapper;
    private final SeatStateService seatStateService;

    /**
     * 幂等释放单个锁定（必须由其它 Bean 调用，@Transactional 才会生效）
     *
     * @return 0=已释放；1=无需处理（可移除队列条目）；2=尚未到期（保留队列条目）
     */
    @Transactional(rollbackFor = Exception.class)
    public int autoReleaseOne(Long scheduleId, Long seatId, Long orderId, Date nowDate) {
        int state = seatStateService.releaseExpired(scheduleId, seatId, orderId);
        if (state == 2) {
            return 2;
        }

        // DB 乐观释放：只有"仍是锁定且确实到期"的座位才会被更新
        int seatRows = scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getId, seatId)
                .eq(ScheduleSeat::getStatus, 1)
                .eq(ScheduleSeat::getOrderId, orderId)
                .le(ScheduleSeat::getLockExpireAt, nowDate)
                .set(ScheduleSeat::getStatus, 0)
                .set(ScheduleSeat::getLockUserId, 0L)
                .set(ScheduleSeat::getLockExpireAt, new Date(0))
                .set(ScheduleSeat::getOrderId, 0L));

        if (seatRows == 0) {
            // HASH 与 DB 可能不一致（如 HASH 缺失后重建），兜底再释放一次 HASH
            if (state != 0) {
                seatStateService.release(scheduleId, List.of(seatId), orderId, 1);
            }
            log.debug("[自动解锁跳过] seatId={} orderId={} 乐观锁未命中", seatId, orderId);
            return 1;
        }

        // 座位已释放 → 可售座位 +1
        scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                .eq(Schedule::getId, scheduleId)
                .setSql("available_seat = available_seat + 1"));

        // 订单自动取消：仅当仍是待支付，避免覆盖用户刚刚完成的支付
        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 2)
                .set(Order::getCancelTime, nowDate)
                .set(Order::getUpdatedAt, nowDate));

        if (orderRows == 0) {
            log.info("[自动解锁注意] seatId={} 已释放但 orderId={} 已被用户处理 (status≠0)", seatId, orderId);
        }

        log.info("[自动解锁成功] scheduleId={} seatId={} orderId={}", scheduleId, seatId, orderId);
        return 0;
    }
}
