package org.javaup.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.core.RedisKeyManage;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.service.SeatLockAutoService;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 座位锁定超时自动解锁调度器
 *
 * 设计要点：
 * 1. 使用 StringRedisTemplate（与 RedisCache 完全一致）—— UTF-8 字符串读写，无序列化不匹配
 * 2. SCAN 安全迭代（替代阻塞主线程的 KEYS）
 * 3. 每 key ZRANGEBYSCORE + LIMIT 50 分批处理，避免单次循环过长
 * 4. 核心解锁逻辑抽离到 SeatLockAutoService（独立 Service，@Transactional 生效）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatLockAutoUnlockTask {

    /**
     * 关键：注入 StringRedisTemplate 而非 RedisTemplate<String, Object>
     * 因为 RedisCache（addForSortedSet）内部用 StringRedisTemplate 存 UTF-8 string member
     * 用 Object RedisTemplate 读会走 JDK 反序列化 → 失败 → 永远 rangeByScore 返回空！
     */
    private final StringRedisTemplate stringRedisTemplate;
    private final SeatLockAutoService seatLockAutoService;

    /** 每 key 单次处理的最大 member 数（限制单轮耗时） */
    private static final int BATCH_SIZE_PER_KEY = 50;

    /** 秒级轮询（1s 间隔 × 每 key 50 条 → 大规模 key 不会堆积） */
    @Scheduled(fixedRate = 1000, initialDelay = 5000)
    public void scanAndUnlock() {
        try {
            scanAndUnlockInternal();
        } catch (Exception e) {
            log.error("[自动解锁] 扫描异常: {}", e.getMessage(), e);
        }
    }

    private void scanAndUnlockInternal() {
        String pattern = buildPattern();
        long nowMs = System.currentTimeMillis();
        double nowScore = (double) nowMs;
        Date nowDate = new Date(nowMs);

        // ─── ① SCAN 安全迭代所有匹配的 ZSET Key ──────────────────────────
        Set<String> zsetKeys = scanKeysSafely(pattern);
        if (zsetKeys.isEmpty()) {
            return;
        }

        ZSetOperations<String, String> zOps = stringRedisTemplate.opsForZSet();

        // ─── ② 逐个 key 分批处理 ────────────────────────────────────────
        for (String key : zsetKeys) {
            try {
                processOneKey(key, zOps, nowScore, nowDate);
            } catch (Exception e) {
                log.warn("[自动解锁] key={} 处理失败: {}", key, e.getMessage());
            }
        }
    }

    /**
     * 用 SCAN 游标安全迭代所有匹配 KEYS（替代 KEYS 阻塞主线程）
     *
     * StringRedisTemplate.execute → 直接调用 RedisCommand.SCAN
     * ScanOptions + COUNT 50 → 每次扫描 50 个 key，游标循环直到返回 0
     */
    private Set<String> scanKeysSafely(String pattern) {
        Set<String> result = new HashSet<>();
        stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            org.springframework.data.redis.core.Cursor<byte[]> cursor =
                    connection.scan(ScanOptions.scanOptions().match(pattern).count(50).build());
            try {
                while (cursor.hasNext()) {
                    result.add(new String(cursor.next()));
                }
            } finally {
                cursor.close();
            }
            return null;
        });
        return result;
    }

    /**
     * 处理单个 ZSET Key：ZRANGEBYSCORE + LIMIT 50 分批
     *
     * 策略：取出所有到期 member → 逐个尝试释放（不管成功与否都 ZREM）
     *   成功 → Service 内部 UPDATE + 清缓存（事务提交后）
     *   跳过（rows=0）→ 已被支付/取消/并发处理过，直接 ZREM 清理
     */
    private void processOneKey(String key, ZSetOperations<String, String> zOps,
                               double nowScore, Date nowDate) {
        int totalProcessed = 0;

        while (true) {
            // ZRANGEBYSCORE key -inf now LIMIT 0 BATCH_SIZE
            Set<String> expired = zOps.rangeByScore(key, 0, nowScore, 0, BATCH_SIZE_PER_KEY);
            if (expired == null || expired.isEmpty()) {
                break;
            }

            for (String member : expired) {
                try {
                    // 调独立 Service（事务生效）
                    boolean released = seatLockAutoService.autoReleaseOneSafe(member, nowDate);
                    // 无论释放成功还是跳过，都从 ZSET 移除
                    zOps.remove(key, member);
                    totalProcessed++;
                    log.debug("[自动解锁] member={} 处理完成 released={}", member, released);
                } catch (Exception e) {
                    // Service 内部已吞掉，这里只是双保险
                    try { zOps.remove(key, member); } catch (Exception ignored) {}
                    log.warn("[自动解锁] member={} 移除异常: {}", member, e.getMessage());
                }
            }

            // 如果本次取到的数量 < BATCH_SIZE，说明没更多了
            if (expired.size() < BATCH_SIZE_PER_KEY) {
                break;
            }
        }

        if (totalProcessed > 0) {
            log.info("[自动解锁] key={} 本轮处理 {} 个到期 member", key, totalProcessed);
        }

        // ZSET 空了 → 设置 TTL 让它自动清理（避免永久积累空 key）
        Long size = zOps.size(key);
        if (size != null && size == 0) {
            stringRedisTemplate.expire(key, 10, TimeUnit.SECONDS);
        }
    }

    /** 根据 RedisKeyManage 枚举构建 SCAN pattern */
    private String buildPattern() {
        String sampleKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_LOCK_ZSET, 0L).getRelKey();
        String pattern = sampleKey.substring(0, sampleKey.length() - 1) + "*";
//        log.debug("[自动解锁] SCAN pattern={}", pattern);
        return pattern;
    }
}
