package org.javaup.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.core.RedisKeyManage;
import org.javaup.entity.Order;
import org.javaup.entity.Schedule;
import org.javaup.entity.ScheduleSeat;
import org.javaup.mapper.OrderMapper;
import org.javaup.mapper.ScheduleMapper;
import org.javaup.mapper.ScheduleSeatMapper;
import org.javaup.redis.RedisCache;
import org.javaup.redis.RedisKeyBuild;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;

/**
 * 座位锁定幂等释放服务
 *
 * 职责：给定一个 ZSET member（格式: seatId:orderId），自动释放锁定。
 *
 * 乐观锁先行（核心设计，减少无用 DB 查询）：
 * ┌───────────────────────────────────────────────────────────────────────┐
 * │ 1. 直接 UPDATE schedule_seat WHERE id=seatId                         │
 * │     AND status=1              （只释放"锁定中"的座位）                  │
 * │     AND lock_expire_at <= now  （只释放"已到期"的锁定）                 │
 * │ 2. rows == 0 → 没东西好做（被支付/取消/并发释放了），返回 false          │
 * │ 3. rows > 0  → 释放成功，查 order 处理后续                              │
 * │     ├── 订单已支付/已取消？→ 竞争中支付者赢了，order UPDATE rows=0        │
 * │     └── 订单仍待支付？    → status 0→2（自动取消）                      │
 * │ 4. schedule.available_seat +1（释放回库存）                             │
 * │ 5. 清除 Redis 座位图缓存                                              │
 * └───────────────────────────────────────────────────────────────────────┘
 *
 * 并发安全性：
 *   - 多实例 + 用户取消 + 用户支付 同时竞争同一个 seatId
 *   - UPDATE WHERE status=1 AND lock_expire_at<=now 做行级乐观锁
 *   - 只有最多 1 个请求能更新成功（DB 行级锁竞争）
 *   - 其他请求 rows=0 → ZREM member 即可，不做任何副作用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockAutoService {

    private final ScheduleSeatMapper scheduleSeatMapper;
    private final OrderMapper orderMapper;
    private final ScheduleMapper scheduleMapper;
    private final RedisCache redisCache;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 幂等释放单个锁定（独立 Service 方法，@Transactional 生效）
     *
     * @param member 格式: seatId:orderId
     * @param nowDate 当前时间
     * @return true=本线程释放成功 / false=已被其他请求处理
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean autoReleaseOne(String member, Date nowDate) {
        String[] parts = member.split(":");
        if (parts.length != 2) {
            log.warn("[自动解锁跳过] member 格式错误: {}", member);
            return false;
        }
        Long seatId, orderId;
        try {
            seatId = Long.parseLong(parts[0]);
            orderId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("[自动解锁跳过] member 非数字: {}", member);
            return false;
        }

        // ─── 阶段 1：乐观锁先行释放座位 ──────────────────────────────────
        // 直接 UPDATE，用 WHERE status=1 AND lock_expire_at<=now 做双重校验
        int seatRows = scheduleSeatMapper.update(null, new LambdaUpdateWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getId, seatId)
                .eq(ScheduleSeat::getStatus, 1)
                .le(ScheduleSeat::getLockExpireAt, nowDate)
                .set(ScheduleSeat::getStatus, 0)
                .set(ScheduleSeat::getLockUserId, 0L)
                .set(ScheduleSeat::getLockExpireAt, new Date(0)));

        if (seatRows == 0) {
            // 座位已被支付（status=2）/ 已被用户取消（status=0）/ 已被其他自动解锁释放 / 没到期
            log.debug("[自动解锁跳过] seatId={} 乐观锁未命中 (并发竞争输掉或未到期)", seatId);
            return false;
        }

        // ─── 阶段 2：更新 schedule.available_seat +1 ────────────────────
        // 需要 scheduleId → 从 order 取（只在 seatRows>0 时才查 order，避免无用查询）
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            // order 不存在但 seat 释放了 → 脏数据
            // 必须清缓存让下次查询重新从 DB 拿正确状态
            Long schedId = getScheduleIdBySeat(seatId);
            if (schedId != null) {
                scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                        .eq(Schedule::getId, schedId)
                        .setSql("available_seat = available_seat + 1"));
            }
            log.warn("[自动解锁异常] seatId={} 释放成功但 orderId={} 不存在", seatId, orderId);
            evictSeatMapCacheAfterCommit(schedId);
            return false;
        }

        Long scheduleId = order.getScheduleId();

        scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                .eq(Schedule::getId, scheduleId)
                .setSql("available_seat = available_seat + 1"));

        // ─── 阶段 3：订单自动取消（乐观锁竞争）───────────────────────────
        // 极端并发：用户在座位释放的同时刚好完成了支付
        // 用 WHERE status=0 乐观锁：只有仍然"待支付"的订单才能被自动取消
        // 如果用户刚好支付了（status 变成 1），rows=0，不破坏支付结果
        int orderRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 2)
                .set(Order::getCancelTime, nowDate));

        if (orderRows == 0) {
            log.info("[自动解锁注意] seatId={} 已释放但 orderId={} 已被用户处理 (status≠0)", seatId, orderId);
        }

        // ─── 阶段 4：清除 Redis 座位图缓存 ────────────────────────────
        // 关键修复：事务提交后再删缓存！
        //   旧代码在事务中先 DEL cache → 并发 seatMap 进来 → cache MISS
        //   → SELECT FROM DB（READ_COMMITTED 隔离级）→ 读到 OLD 数据（事务没提交）
        //   → 把旧状态写回缓存！这是经典「先删缓存后提交事务」Race Condition
        //
        // 修复：用 TransactionSynchronizationManager 注册提交后回调
        //   事务 COMMIT → 回调触发 → 再 DEL cache
        //   此时并发 seatMap 进来：要么 cache HIT（还没 DEL）读到正确旧状态
        //   要么 cache MISS（DEL 已发生）→ SELECT FROM DB 读到 COMMITED 的新状态
        evictSeatMapCacheAfterCommit(scheduleId);

        log.info("[自动解锁成功] seatId={} orderId={} scheduleId={}", seatId, orderId, scheduleId);
        return true;
    }

    /**
     * 事务提交后清除座位图缓存（解决「先删缓存后提交事务」Race Condition）
     */
    private void evictSeatMapCacheAfterCommit(Long scheduleId) {
        if (scheduleId == null) return;
        String cacheKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_MAP_KEY, scheduleId).getRelKey();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 在 Spring 事务中 → 注册提交后回调
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvictCache(cacheKey);
                }
            });
        } else {
            // 不在事务中（单测/手动调用）→ 直接清
            doEvictCache(cacheKey);
        }
    }

    private void doEvictCache(String cacheKey) {
        try {
            stringRedisTemplate.delete(cacheKey);
            log.info("[自动解锁] 座位图缓存已清除 key={}", cacheKey);
        } catch (Exception e) {
            log.warn("[自动解锁] 座位图缓存清除失败 key={}: {}", cacheKey, e.getMessage());
        }
    }

    /** 辅助：seatId → scheduleId（order 不存在时的兜底查询）*/
    private Long getScheduleIdBySeat(Long seatId) {
        ScheduleSeat seat = scheduleSeatMapper.selectById(seatId);
        return seat != null ? seat.getScheduleId() : null;
    }

    /**
     * 带异常保护的释放（吞掉异常，不影响批量任务继续跑）
     */
    public boolean autoReleaseOneSafe(String member, Date nowDate) {
        try {
            return autoReleaseOne(member, nowDate);
        } catch (Exception e) {
            log.error("[自动解锁异常] member={}: {}", member, e.getMessage(), e);
            return false;
        }
    }
}
