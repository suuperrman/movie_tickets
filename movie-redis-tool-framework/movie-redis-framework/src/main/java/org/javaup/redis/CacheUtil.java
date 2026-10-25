package org.javaup.redis;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.util.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @program: movie-ticket
 * @description: 缓存对象映射工具
 * @author: xiong
 **/
public class CacheUtil {

    public static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.SECONDS;

    /**
     * 随机TTL偏移比例，用于防止缓存雪崩。
     * 实际TTL = 基础TTL + [0, 基础TTL * RANDOM_TTL_OFFSET_RATIO) 的随机偏移
     */
    private static final double RANDOM_TTL_OFFSET_RATIO = 0.1D;

    /**
     * 随机TTL最小偏移量（秒），避免短TTL场景下偏移量过小。
     */
    private static final long RANDOM_TTL_MIN_OFFSET_SECONDS = 5L;

    /**
     * 构建类型
     *
     * @param types
     * @return
     */
    public static Type buildType(Type... types) {
        ParameterizedTypeImpl beforeType = null;
        if (types != null && types.length > 0) {
            if (types.length == 1) {
                return new ParameterizedTypeImpl(new Type[]{null}, null,
                        types[0]);
            }

            for (int i = types.length - 1; i > 0; i--) {
                beforeType = new ParameterizedTypeImpl(new Type[]{beforeType == null ? types[i] : beforeType}, null,
                        types[i - 1]);
            }
        }
        return beforeType;
    }

    /**
     * 检查 Key 是否为空或空的字符串
     *
     * @param key
     */
    public static void checkNotBlank(String... key) {
        for (String s : key) {
            if (StrUtil.isEmpty(s)) {
                throw new RuntimeException("请求参数缺失");
            }
        }
    }

    /**
     * 检查 redisKeyBuild 中的key是否为空或空的字符串
     *
     * @param redisKeyBuild key包装
     */
    public static void checkNotBlank(RedisKeyBuild redisKeyBuild) {
        if (StrUtil.isEmpty(redisKeyBuild.getRelKey())) {
            throw new RuntimeException("请求参数缺失");
        }
    }

    /**
     * 检查 list 是否为空或空的字符串
     *
     * @param list
     */
    public static void checkNotBlank(Collection<String> list) {
        for (String s : list) {
            if (StrUtil.isEmpty(s)) {
                throw new RuntimeException("请求参数缺失");
            }
        }
    }

    /**
     * 检查 list 是否为空或空的字符串
     *
     * @param list key集合
     */
    public static void checkNotEmpty(Collection<?> list) {
        for (Object o : list) {
            if (o == null) {
                throw new RuntimeException("请求参数缺失");
            }
        }
    }

    /**
     * 检查 object 是否为空
     *
     * @param object
     */
    public static void checkNotEmpty(Object object) {
        if (isEmpty(object)) {
            throw new RuntimeException("请求参数缺失");
        }
    }
    
    /**
     * 判断 object 是否为空
     *
     */
    public static boolean isEmpty(Object object) {
        if (object == null) {
            return true;
        }
        if (object instanceof String) {
            return StrUtil.isEmpty((String) object);
        }
        if (object instanceof Collection) {
            return ((Collection<?>) object).isEmpty();
        }
        return false;
    }

    public static List<String> getBatchKey(Collection<RedisKeyBuild> list){
        return list.stream().map(RedisKeyBuild::getRelKey).collect(Collectors.toList());
    }

    public static <T> List<T> optimizeRedisList(List<T> list){
        if (Objects.isNull(list)) {
            return new ArrayList<>();
        }
        if (list.size() == 0 || Objects.isNull(list.get(0))) {
            return new ArrayList<>();
        }
        return list;
    }
    
    public static boolean checkRedisListIsEmpty(List<?> list){
        if (Objects.isNull(list)) {
            return true;
        }
        return list.size() == 0 || Objects.isNull(list.get(0));
    }

    /**
     * 生成带随机偏移的TTL，用于防止大量缓存同时过期导致的缓存雪崩。
     * <p>
     * 实际TTL = 基础TTL + 随机偏移量，其中随机偏移量取
     * {@code max(基础TTL * RANDOM_TTL_OFFSET_RATIO, RANDOM_TTL_MIN_OFFSET_SECONDS)}
     * 范围内的随机值（单位与传入的timeUnit一致）。
     * </p>
     *
     * @param baseTtl  基础过期时间
     * @param timeUnit 时间单位
     * @return 带随机偏移的过期时间（与timeUnit同单位）
     */
    public static long randomTtl(long baseTtl, TimeUnit timeUnit) {
        if (baseTtl <= 0) {
            return baseTtl;
        }
        // 将基础TTL转换为秒，计算偏移上限
        long baseTtlSeconds = timeUnit.toSeconds(baseTtl);
        long offsetUpperBoundSeconds = Math.max(
                (long) (baseTtlSeconds * RANDOM_TTL_OFFSET_RATIO),
                RANDOM_TTL_MIN_OFFSET_SECONDS
        );
        // 生成 [0, offsetUpperBoundSeconds) 的随机偏移秒数
        long randomOffsetSeconds = ThreadLocalRandom.current().nextLong(offsetUpperBoundSeconds);
        // 将偏移量转回原时间单位并叠加到基础TTL上
        long randomOffset = timeUnit.convert(randomOffsetSeconds, TimeUnit.SECONDS);
        return baseTtl + randomOffset;
    }
}
