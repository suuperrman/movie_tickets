package org.javaup.config;

import org.javaup.constant.LockInfoType;
import org.springframework.context.annotation.Configuration;
import org.javaup.handle.RedissonDataHandle;
import org.springframework.context.annotation.Configuration;
import org.javaup.locallock.LocalLockCache;
import org.springframework.context.annotation.Configuration;
import org.javaup.lockinfo.LockInfoHandle;
import org.springframework.context.annotation.Configuration;
import org.javaup.lockinfo.factory.LockInfoHandleFactory;
import org.springframework.context.annotation.Configuration;
import org.javaup.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import org.springframework.context.annotation.Configuration;
import org.javaup.repeatexecutelimit.aspect.RepeatExecuteLimitAspect;
import org.springframework.context.annotation.Configuration;
import org.javaup.servicelock.factory.ServiceLockFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: movie-ticket 
 * @description: 配置
 * @author: xiong
 **/
@Configuration
public class RepeatExecuteLimitAutoConfiguration {
    
    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle(){
        return new RepeatExecuteLimitLockInfoHandle();
    }
    
    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(LocalLockCache localLockCache,
                                                             LockInfoHandleFactory lockInfoHandleFactory,
                                                             ServiceLockFactory serviceLockFactory,
                                                             RedissonDataHandle redissonDataHandle){
        return new RepeatExecuteLimitAspect(localLockCache, lockInfoHandleFactory,serviceLockFactory,redissonDataHandle);
    }
}
    