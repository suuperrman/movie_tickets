package org.javaup.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.javaup.redis.RedisCache;
import org.javaup.redis.RedisKeyBuild;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存服务（Caffeine 本地缓存 + Redis 分布式缓存）。
 * <p>
 * 读取顺序：Caffeine → Redis → DB
 * 写入顺序：Caffeine + Redis 双写
 * 删除顺序：Caffeine + Redis 双删
 * </p>
 * <p>
 * 适用场景：读多写少、变更频率低、对实时性要求不极端的数据
 * （如分类列表、电影详情、热门电影列表）。
 * </p>
 * <p>
 * 注意：本地缓存与 Redis 之间不保证强一致，允许短暂（秒级）不一致。
 * 对一致性要求高的场景（如座位状态），请直接走 Redis。
 * </p>
 */
@Slf4j
@Service
public class MultiLevelCacheService {

    private final RedisCache redisCache;

    /** Caffeine 本地缓存，默认最大1000条，写入后5分钟过期 */
    private final Cache<String, Object> localCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();

    public MultiLevelCacheService(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    /**
     * 多级缓存读取
     *
     * @param redisKeyBuild 缓存key
     * @param clazz         返回类型
     * @param dbFallback    缓存未命中时的DB查询逻辑
     * @param redisTtl      Redis缓存过期时间
     * @param timeUnit      时间单位
     * @param <T>           泛型
     * @return 查询结果
     */
    public <T> T get(RedisKeyBuild redisKeyBuild, Class<T> clazz, Supplier<T> dbFallback, long redisTtl, TimeUnit timeUnit) {
        String key = redisKeyBuild.getRelKey();

        // 1. 查 Caffeine 本地缓存
        Object localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            log.debug("[多级缓存-本地命中] key={}", key);
            return clazz.cast(localValue);
        }

        // 2. 查 Redis
        T redisValue = redisCache.get(redisKeyBuild, clazz);
        if (redisValue != null) {
            log.debug("[多级缓存-Redis命中] key={}", key);
            // 回填本地缓存
            localCache.put(key, redisValue);
            return redisValue;
        }

        // 3. 查 DB
        T dbValue = dbFallback.get();
        if (dbValue == null) {
            return null;
        }

        // 4. 双写：本地缓存 + Redis
        localCache.put(key, dbValue);
        redisCache.set(redisKeyBuild, dbValue, redisTtl, timeUnit);
        log.debug("[多级缓存-写入] key={}", key);
        return dbValue;
    }

    /**
     * 多级缓存读取（支持泛型类型，如 {@code Page<XxxVO>}）
     *
     * @param redisKeyBuild 缓存key
     * @param type          返回类型（含泛型信息）
     * @param dbFallback    缓存未命中时的DB查询逻辑
     * @param redisTtl      Redis缓存过期时间
     * @param timeUnit      时间单位
     * @param <T>           泛型
     * @return 查询结果
     */
    @SuppressWarnings("unchecked")
    public <T> T get(RedisKeyBuild redisKeyBuild, Type type, Supplier<T> dbFallback, long redisTtl, TimeUnit timeUnit) {
        String key = redisKeyBuild.getRelKey();

        // 1. 查 Caffeine 本地缓存
        Object localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            log.debug("[多级缓存-本地命中] key={}", key);
            return (T) localValue;
        }

        // 2. 查 Redis
        T redisValue = redisCache.getByType(redisKeyBuild, type);
        if (redisValue != null) {
            log.debug("[多级缓存-Redis命中] key={}", key);
            localCache.put(key, redisValue);
            return redisValue;
        }

        // 3. 查 DB
        T dbValue = dbFallback.get();
        if (dbValue == null) {
            return null;
        }

        // 4. 双写：本地缓存 + Redis
        localCache.put(key, dbValue);
        redisCache.set(redisKeyBuild, dbValue, redisTtl, timeUnit);
        log.debug("[多级缓存-写入] key={}", key);
        return dbValue;
    }

    /**
     * 删除缓存（本地 + Redis）
     */
    public void evict(RedisKeyBuild redisKeyBuild) {
        String key = redisKeyBuild.getRelKey();
        localCache.invalidate(key);
        redisCache.del(redisKeyBuild);
        log.debug("[多级缓存-删除] key={}", key);
    }

    /**
     * 根据前缀删除缓存（仅删除本地缓存，Redis 由原有逻辑处理）
     */
    public void evictLocalByPrefix(String prefix) {
        localCache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
        log.debug("[多级缓存-本地批量删除] prefix={}", prefix);
    }

    /**
     * 获取本地缓存统计信息
     */
    public String getStats() {
        return localCache.stats().toString();
    }
}
