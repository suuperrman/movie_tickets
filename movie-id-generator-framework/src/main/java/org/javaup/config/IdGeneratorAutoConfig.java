package org.javaup.config;

import org.javaup.toolkit.SnowflakeIdGenerator;
import org.springframework.context.annotation.Configuration;
import org.javaup.toolkit.WorkAndDataCenterIdHandler;
import org.springframework.context.annotation.Configuration;
import org.javaup.toolkit.WorkDataCenterId;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Configuration;

/**
 * @program: movie-ticket 
 * @description: 分布式id配置
 * @author: xiong
 **/
@Configuration
public class IdGeneratorAutoConfig {
    
    @Bean
    public WorkAndDataCenterIdHandler workAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate){
        return new WorkAndDataCenterIdHandler(stringRedisTemplate);
    }
    
    @Bean
    public WorkDataCenterId workDataCenterId(WorkAndDataCenterIdHandler workAndDataCenterIdHandler){
        return workAndDataCenterIdHandler.getWorkAndDataCenterId();
    }
    
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(WorkDataCenterId workDataCenterId){
        return new SnowflakeIdGenerator(workDataCenterId);
    }
}
