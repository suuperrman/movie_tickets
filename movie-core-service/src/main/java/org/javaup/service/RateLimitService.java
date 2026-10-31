package org.javaup.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.core.RedisKeyManage;
import org.javaup.exception.MovieFrameException;
import org.javaup.redis.RedisKeyBuild;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 通用接口限流服务（固定窗口计数）。
 * <p>
 * 基于 Redis INCR + EXPIRE 实现，支持按用户ID或IP限流。
 * 窗口内请求数超过阈值时抛异常，防止接口被恶意刷量。
 * </p>
 * <p>
 * 注意：固定窗口在窗口切换瞬间存在临界问题（可能允许2倍流量通过），
 * 对精度要求极高的场景可升级为滑动窗口或令牌桶。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取访问令牌（固定窗口限流）
     *
     * @param scene         场景标识（如 order_create、seat_map）
     * @param identifier    限流对象标识（用户ID或IP地址）
     * @param maxAttempts   窗口内最大允许次数
     * @param windowSeconds 窗口大小（秒）
     * @throws MovieFrameException 被限流时抛出
     */
    public void tryAcquire(String scene, String identifier, int maxAttempts, int windowSeconds) {
        String key = RedisKeyBuild.createRedisKey(RedisKeyManage.RATE_LIMIT_KEY, scene, identifier).getRelKey();
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        if (count != null && count > maxAttempts) {
            log.warn("[限流拦截] scene={} identifier={} count={}/{} window={}s",
                    scene, identifier, count, maxAttempts, windowSeconds);
            throw new MovieFrameException("操作过于频繁，请稍后再试");
        }
    }

    /**
     * 尝试获取访问令牌，被限流时静默返回false（不抛异常）
     *
     * @return true=允许通过，false=被限流
     */
    public boolean tryAcquireQuietly(String scene, String identifier, int maxAttempts, int windowSeconds) {
        String key = RedisKeyBuild.createRedisKey(RedisKeyManage.RATE_LIMIT_KEY, scene, identifier).getRelKey();
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count != null && count <= maxAttempts;
    }
}
