package org.javaup.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.core.RedisKeyManage;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.service.SeatLockAutoService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Set;

/**
 * 座位锁定超时自动解锁调度器
 *
 * 改为读取单一全局延时队列 seat:lock:timeout（ZSET）：
 *   member = scheduleId:seatId:orderId
 *   score  = 到期毫秒时间戳
 *
 * 相比旧的"每场次一个 ZSET + 每秒 SCAN 全部 key"，全局队列只需一次 ZRANGEBYSCORE，
 * 复杂度不随场次数量增长；条目在支付/取消/退款时会被主动移除。
 *
 * 返回值语义：0/1 → ZREM（已处理）；2 → 保留（HASH 判定尚未到期，下一轮再试）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatLockAutoUnlockTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final SeatLockAutoService seatLockAutoService;

    /** 单轮最多处理多少条到期任务（限制单次调度耗时） */
    private static final int BATCH_SIZE = 200;

    /** 秒级轮询 */
    @Scheduled(fixedRate = 1000, initialDelay = 5000)
    public void scanAndUnlock() {
        try {
            processExpiredLocks();
        } catch (Exception e) {
            log.error("[自动解锁] 扫描异常: {}", e.getMessage(), e);
        }
    }

    private void processExpiredLocks() {
        String key = RedisKeyBuild.createRedisKey(RedisKeyManage.SEAT_LOCK_TIMEOUT_ZSET).getRelKey();
        ZSetOperations<String, String> zOps = stringRedisTemplate.opsForZSet();
        long nowMs = System.currentTimeMillis();
        Date nowDate = new Date(nowMs);
        int totalProcessed = 0;

        while (true) {
            Set<String> expired = zOps.rangeByScore(key, 0, (double) nowMs, 0, BATCH_SIZE);
            if (expired == null || expired.isEmpty()) {
                break;
            }
            boolean keepForNextRound = false;
            for (String member : expired) {
                totalProcessed++;
                Long scheduleId;
                Long seatId;
                Long orderId;
                try {
                    String[] parts = member.split(":");
                    if (parts.length != 3) {
                        log.warn("[自动解锁] member 格式错误: {}", member);
                        zOps.remove(key, member);
                        continue;
                    }
                    scheduleId = Long.parseLong(parts[0]);
                    seatId = Long.parseLong(parts[1]);
                    orderId = Long.parseLong(parts[2]);
                } catch (NumberFormatException e) {
                    log.warn("[自动解锁] member 非数字: {}", member);
                    zOps.remove(key, member);
                    continue;
                }
                int result;
                try {
                    // 通过代理调用，SeatLockAutoService#autoReleaseOne 上的事务才会生效
                    result = seatLockAutoService.autoReleaseOne(scheduleId, seatId, orderId, nowDate);
                } catch (Exception e) {
                    // 异常时事务已回滚，移除该条目避免死循环重试
                    log.error("[自动解锁异常] scheduleId={} seatId={} orderId={}: {}",
                            scheduleId, seatId, orderId, e.getMessage(), e);
                    zOps.remove(key, member);
                    continue;
                }
                if (result == 2) {
                    // HASH 判定尚未到期（score 与实际到期时间存在细微偏差），保留到下一轮
                    keepForNextRound = true;
                } else {
                    zOps.remove(key, member);
                }
            }
            // 本批不足 BATCH_SIZE 或存在需保留的条目 → 结束本轮，避免同一批反复读取
            if (expired.size() < BATCH_SIZE || keepForNextRound) {
                break;
            }
        }

        if (totalProcessed > 0) {
            log.info("[自动解锁] 本轮处理 {} 个到期 member", totalProcessed);
        }
    }
}
