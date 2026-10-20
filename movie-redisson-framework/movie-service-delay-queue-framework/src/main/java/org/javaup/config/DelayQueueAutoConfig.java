package org.javaup.config;


import org.javaup.context.DelayQueueBasePart;
import org.springframework.context.annotation.Configuration;
import org.javaup.context.DelayQueueContext;
import org.springframework.context.annotation.Configuration;
import org.javaup.event.DelayQueueInitHandler;
import org.springframework.context.annotation.Configuration;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: movie-ticket 
 * @description: 延迟队列 配置
 * @author: xiong
 **/
@EnableConfigurationProperties(DelayQueueProperties.class)
@Configuration
public class DelayQueueAutoConfig {
    
    @Bean
    public DelayQueueInitHandler delayQueueInitHandler(DelayQueueBasePart delayQueueBasePart){
        return new DelayQueueInitHandler(delayQueueBasePart);
    }
   
    @Bean
    public DelayQueueBasePart delayQueueBasePart(RedissonClient redissonClient,DelayQueueProperties delayQueueProperties){
        return new DelayQueueBasePart(redissonClient,delayQueueProperties);
    }
  
    @Bean
    public DelayQueueContext delayQueueContext(DelayQueueBasePart delayQueueBasePart){
        return new DelayQueueContext(delayQueueBasePart);
    }
}
