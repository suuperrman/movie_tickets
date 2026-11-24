package org.javaup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.core.RedisKeyManage;
import org.javaup.entity.ScheduleSeat;
import org.javaup.mapper.ScheduleSeatMapper;
import org.javaup.redis.RedisKeyBuild;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 场次座位实时状态服务
 *
 * 权威状态存放在每场次一个 Redis HASH：movie:seat:state:{scheduleId}
 *   field = seatId（schedule_seat.id）
 *   value = status:orderId:expireAtMs   （0=可售 1=锁定 2=已售）
 *
 * 设计要点：
 * 1. 所有状态迁移（锁座/支付/取消/退款/自动解锁）都用 Lua 原子完成，避免并发读写 HASH 时丢更新
 * 2. DB（schedule_seat）仍是持久化真相，HASH 丢失/过期时可从 DB 重建；锁定前缀 owner=0 表示"从 DB 重建的无主锁"
 * 3. 锁定超时使用单一全局 ZSET（seat:lock:timeout），member=scheduleId:seatId:orderId，score=到期毫秒
 * 4. 座位图读取时按 HASH 覆盖状态，缓存中只保留静态布局，不再因座位状态变化而失效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatStateService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ScheduleSeatMapper scheduleSeatMapper;

    /** 状态 HASH 的保留时长（DB 是真相，过期后读路径会从 DB 重建） */
    private static final long STATE_TTL_HOURS = 24L;

    /**
     * 原子锁座：所有座位可售（或原锁定已过期）才全部锁定，否则一个都不锁。
     * ARGV: [1]=座位数 n, [2]=nowMs, [3]=orderId, [4]=expireAtMs, [5..]=seatId
     * 返回：0=成功；-2=HASH不存在；-3=field缺失；正数=冲突座位下标(1-based)
     */
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -2
            end
            local n = tonumber(ARGV[1])
            local nowMs = tonumber(ARGV[2])
            for i = 1, n do
                local v = redis.call('HGET', KEYS[1], ARGV[4 + i])
                if not v then
                    return -3
                end
                local st, ow, ex = string.match(v, '^(%d+):(%d+):(%d+)$')
                if st == nil then
                    return -3
                end
                st = tonumber(st)
                ex = tonumber(ex)
                if st == 2 or (st == 1 and ex > nowMs) then
                    return i
                end
            end
            for i = 1, n do
                redis.call('HSET', KEYS[1], ARGV[4 + i], '1:' .. ARGV[3] .. ':' .. ARGV[4])
            end
            return 0
            """, Long.class);

    /**
     * 原子支付确认：锁定(1) → 已售(2)，并校验锁定归属订单（owner=0 视为重建无主锁，允许确认）
     * ARGV: [1]=座位数 n, [2]=orderId, [3..]=seatId
     * 返回：0=成功；-2=HASH不存在；-3=field缺失；-5=状态不是锁定；-6=锁不属于该订单
     */
    private static final DefaultRedisScript<Long> CONFIRM_SOLD_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -2
            end
            local n = tonumber(ARGV[1])
            local owner = ARGV[2]
            for i = 1, n do
                local v = redis.call('HGET', KEYS[1], ARGV[2 + i])
                if not v then
                    return -3
                end
                local st, ow = string.match(v, '^(%d+):(%d+):(%d+)$')
                if st == nil then
                    return -3
                end
                if tonumber(st) ~= 1 then
                    return -5
                end
                if ow ~= '0' and ow ~= owner then
                    return -6
                end
            end
            for i = 1, n do
                redis.call('HSET', KEYS[1], ARGV[2 + i], '2:' .. owner .. ':0')
            end
            return 0
            """, Long.class);

    /**
     * 原子释放：fromStatus → 可售(0)，只释放归属该订单（或重建无主锁）的座位
     * ARGV: [1]=座位数 n, [2]=orderId, [3]=fromStatus, [4..]=seatId
     * 返回：实际释放座位数
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local n = tonumber(ARGV[1])
            local owner = ARGV[2]
            local from = tonumber(ARGV[3])
            local cnt = 0
            for i = 1, n do
                local v = redis.call('HGET', KEYS[1], ARGV[3 + i])
                if v then
                    local st, ow = string.match(v, '^(%d+):(%d+):(%d+)$')
                    if st ~= nil and tonumber(st) == from and (ow == '0' or ow == owner) then
                        redis.call('HSET', KEYS[1], ARGV[3 + i], '0:0:0')
                        cnt = cnt + 1
                    end
                end
            end
            return cnt
            """, Long.class);

    /**
     * 原子释放单个超时锁定
     * ARGV: [1]=seatId, [2]=nowMs, [3]=orderId
     * 返回：0=已释放；1=无需释放（已售/已释放/归属不符）；2=尚未到期
     */
    private static final DefaultRedisScript<Long> RELEASE_EXPIRED_SCRIPT = new DefaultRedisScript<>("""
            local v = redis.call('HGET', KEYS[1], ARGV[1])
            if not v then
                return 1
            end
            local st, ow, ex = string.match(v, '^(%d+):(%d+):(%d+)$')
            if st == nil then
                return 1
            end
            st = tonumber(st)
            ex = tonumber(ex)
            if st ~= 1 then
                return 1
            end
            if ex > tonumber(ARGV[2]) then
                return 2
            end
            if ow ~= '0' and ow ~= ARGV[3] then
                return 1
            end
            redis.call('HSET', KEYS[1], ARGV[1], '0:0:0')
            return 0
            """, Long.class);

    /**
     * 从 DB 补全 HASH：只写缺失 field（HSETNX），绝不覆盖已在 Redis 中的实时状态
     * ARGV: [1]=座位数 n, [2..] 依次为 field、value
     */
    private static final DefaultRedisScript<Long> FILL_SCRIPT = new DefaultRedisScript<>("""
            local n = tonumber(ARGV[1])
            local cnt = 0
            for i = 1, n do
                local field = ARGV[1 + (i - 1) * 2 + 1]
                local value = ARGV[1 + (i - 1) * 2 + 2]
                if redis.call('HSETNX', KEYS[1], field, value) == 1 then
                    cnt = cnt + 1
                end
            end
            return cnt
            """, Long.class);

    // ==================== 锁座 / 支付 / 释放 ====================

    /**
     * 原子锁座（All-or-Nothing）。HASH 缺失时先按 DB 重建再重试一次。
     *
     * @return 0=成功；正数=冲突座位下标(1-based)；-1=Redis异常/初始化失败
     */
    public long reserve(Long scheduleId, Collection<Long> seatIds, Long orderId, long expireAtMs) {
        String key = stateKey(scheduleId);
        for (int attempt = 0; attempt < 2; attempt++) {
            List<String> args = new ArrayList<>(4 + seatIds.size());
            args.add(String.valueOf(seatIds.size()));
            args.add(String.valueOf(System.currentTimeMillis()));
            args.add(String.valueOf(orderId));
            args.add(String.valueOf(expireAtMs));
            seatIds.forEach(id -> args.add(String.valueOf(id)));
            Long result = run(RESERVE_SCRIPT, key, args);
            if (result == null) {
                return -1;
            }
            if (result == -2L || result == -3L) {
                initIfAbsent(scheduleId);
                continue;
            }
            return result;
        }
        return -1;
    }

    /**
     * 原子支付确认（1→2）。
     *
     * @return 0=成功；-1=失败（状态不是锁定/归属不符/Redis异常）
     */
    public long confirmSold(Long scheduleId, Collection<Long> seatIds, Long orderId) {
        String key = stateKey(scheduleId);
        for (int attempt = 0; attempt < 2; attempt++) {
            List<String> args = new ArrayList<>(2 + seatIds.size());
            args.add(String.valueOf(seatIds.size()));
            args.add(String.valueOf(orderId));
            seatIds.forEach(id -> args.add(String.valueOf(id)));
            Long result = run(CONFIRM_SOLD_SCRIPT, key, args);
            if (result == null) {
                return -1;
            }
            if (result == -2L || result == -3L) {
                initIfAbsent(scheduleId);
                continue;
            }
            return result;
        }
        return -1;
    }

    /**
     * 原子释放：fromStatus → 可售(0)。
     *
     * @return 实际释放座位数
     */
    public long release(Long scheduleId, Collection<Long> seatIds, Long orderId, int fromStatus) {
        String key = stateKey(scheduleId);
        List<String> args = new ArrayList<>(3 + seatIds.size());
        args.add(String.valueOf(seatIds.size()));
        args.add(String.valueOf(orderId));
        args.add(String.valueOf(fromStatus));
        seatIds.forEach(id -> args.add(String.valueOf(id)));
        Long result = run(RELEASE_SCRIPT, key, args);
        return result == null ? 0L : result;
    }

    /**
     * 释放单个到期锁定（自动解锁任务使用）。
     *
     * @return 0=已释放；1=无需释放；2=尚未到期
     */
    public int releaseExpired(Long scheduleId, Long seatId, Long orderId) {
        String key = stateKey(scheduleId);
        List<String> args = List.of(String.valueOf(seatId), String.valueOf(System.currentTimeMillis()), String.valueOf(orderId));
        Long result = run(RELEASE_EXPIRED_SCRIPT, key, args);
        if (result == null) {
            return 1;
        }
        return result.intValue();
    }

    // ==================== 延时队列 ====================

    /** 事务提交后把锁座条目投递到全局超时 ZSET（事务回滚则不投递） */
    public void scheduleTimeoutAfterCommit(Long scheduleId, Collection<Long> seatIds, Long orderId, long expireAtMs) {
        runAfterCommit(() -> {
            try {
                String key = timeoutKey();
                for (Long seatId : seatIds) {
                    stringRedisTemplate.opsForZSet().add(key, member(scheduleId, seatId, orderId), (double) expireAtMs);
                }
            } catch (Exception e) {
                log.warn("[锁座延时队列失败] scheduleId={} orderId={}: {}", scheduleId, orderId, e.getMessage());
            }
        });
    }

    /** 事务提交后从全局超时 ZSET 移除订单对应的锁座条目 */
    public void cancelTimeoutAfterCommit(Long scheduleId, Collection<Long> seatIds, Long orderId) {
        runAfterCommit(() -> {
            try {
                String key = timeoutKey();
                for (Long seatId : seatIds) {
                    stringRedisTemplate.opsForZSet().remove(key, member(scheduleId, seatId, orderId));
                }
            } catch (Exception e) {
                log.warn("[锁座延时队列移除失败] scheduleId={} orderId={}: {}", scheduleId, orderId, e.getMessage());
            }
        });
    }

    private String member(Long scheduleId, Long seatId, Long orderId) {
        return scheduleId + ":" + seatId + ":" + orderId;
    }

    // ==================== 读路径 ====================

    /**
     * 读取场次全部座位实时状态（seatId → status）。
     * HASH 不存在时从 DB 重建；已过期的锁定在读取时归一化为可售，避免前端看到僵尸锁。
     */
    public Map<String, Integer> getSeatStates(Long scheduleId) {
        String key = stateKey(scheduleId);
        Map<String, String> raw = stringRedisTemplate.<String, String>opsForHash().entries(key);
        if (raw.isEmpty()) {
            initIfAbsent(scheduleId);
            raw = stringRedisTemplate.<String, String>opsForHash().entries(key);
        }
        long now = System.currentTimeMillis();
        Map<String, Integer> result = new HashMap<>(Math.max(16, raw.size() * 2));
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            result.put(entry.getKey(), parseStatus(entry.getValue(), now));
        }
        return result;
    }

    // ==================== HASH 初始化 ====================

    /** 用 DB 中的持久化状态补全 HASH（只补缺失 field，不覆盖实时值） */
    public void initIfAbsent(Long scheduleId) {
        List<ScheduleSeat> seats = scheduleSeatMapper.selectList(
                new LambdaQueryWrapper<ScheduleSeat>()
                        .select(ScheduleSeat::getId, ScheduleSeat::getStatus,
                                ScheduleSeat::getOrderId, ScheduleSeat::getLockExpireAt)
                        .eq(ScheduleSeat::getScheduleId, scheduleId));
        if (seats.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<String> args = new ArrayList<>(1 + seats.size() * 2);
        args.add(String.valueOf(seats.size()));
        for (ScheduleSeat seat : seats) {
            int status = seat.getStatus() == null ? 0 : seat.getStatus();
            long owner = seat.getOrderId() == null ? 0L : seat.getOrderId();
            long expireAt = seat.getLockExpireAt() == null ? 0L : seat.getLockExpireAt().getTime();
            // 锁定已过期（或缺失到期时间）的脏数据，重建时直接视为可售
            if (status == 1 && expireAt <= now) {
                status = 0;
                owner = 0;
                expireAt = 0;
            }
            if (status == 0) {
                owner = 0;
                expireAt = 0;
            }
            if (status == 2) {
                expireAt = 0;
            }
            args.add(String.valueOf(seat.getId()));
            args.add(status + ":" + owner + ":" + expireAt);
        }
        run(FILL_SCRIPT, stateKey(scheduleId), args);
        try {
            stringRedisTemplate.expire(stateKey(scheduleId), STATE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[座位状态Hash设置TTL失败] scheduleId={}: {}", scheduleId, e.getMessage());
        }
        log.info("[座位状态Hash重建] scheduleId={} seats={}", scheduleId, seats.size());
    }

    // ==================== 内部工具 ====================

    private int parseStatus(String value, long now) {
        if (value == null) {
            return 0;
        }
        String[] parts = value.split(":");
        if (parts.length < 3) {
            return 0;
        }
        try {
            int status = Integer.parseInt(parts[0]);
            long expireAt = Long.parseLong(parts[2]);
            if (status == 1 && expireAt <= now) {
                return 0;
            }
            return status >= 0 && status <= 2 ? status : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Long run(DefaultRedisScript<Long> script, String key, List<String> args) {
        return stringRedisTemplate.execute(script, Collections.singletonList(key), args.toArray());
    }

    private String stateKey(Long scheduleId) {
        return RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_STATE_KEY, scheduleId).getRelKey();
    }

    private String timeoutKey() {
        return RedisKeyBuild.createRedisKey(RedisKeyManage.SEAT_LOCK_TIMEOUT_ZSET).getRelKey();
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
