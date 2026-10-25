package org.javaup.redis;


import org.javaup.core.RedisKeyManage;
import org.javaup.core.SpringUtil;
import lombok.Getter;

import java.util.Objects;

/**
 * @program: movie-ticket
 * @description: redis key包装
 * @author: xiong
 **/
@Getter
public final class RedisKeyBuild {
    /**
     * 实际使用的key
     * */
    private final String relKey;

    private RedisKeyBuild(String relKey) {
        this.relKey = relKey;
    }

    /**
     * 构建真实的key
     * @param redisKeyManage key的枚举
     * @param args 占位符的值
     * */
    public static RedisKeyBuild createRedisKey(RedisKeyManage redisKeyManage, Object... args){
        String redisRelKey = String.format(redisKeyManage.getKey(),args);
        return new RedisKeyBuild(SpringUtil.getPrefixDistinctionName() + "-" + redisRelKey);
    }
    
    public static String getRedisKey(RedisKeyManage redisKeyManage) {
        return SpringUtil.getPrefixDistinctionName() + "-" + redisKeyManage.getKey();
    }

    /**
     * 直接从字符串构建 RedisKeyBuild（用于锁key等非业务key场景）
     *
     * @param key 完整的redis key
     * @return RedisKeyBuild
     */
    public static RedisKeyBuild of(String key) {
        return new RedisKeyBuild(key);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RedisKeyBuild that = (RedisKeyBuild) o;
        return relKey.equals(that.relKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relKey);
    }
}
