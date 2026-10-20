package org.javaup.redis.config;

import org.javaup.redis.RedisCacheImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @program: movie-ticket
 * @description: redis封装实现配置
 * @author: xiong
 **/
@Configuration
public class RedisCacheAutoConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public RedisCacheImpl redisCache(@Qualifier("redisToolStringRedisTemplate") StringRedisTemplate stringRedisTemplate){
        return new RedisCacheImpl(stringRedisTemplate);
    }
}
