package org.javaup.config;

import org.javaup.constant.LockInfoType;
import org.springframework.context.annotation.Configuration;
import org.javaup.core.ManageLocker;
import org.springframework.context.annotation.Configuration;
import org.javaup.lockinfo.LockInfoHandle;
import org.springframework.context.annotation.Configuration;
import org.javaup.lockinfo.factory.LockInfoHandleFactory;
import org.springframework.context.annotation.Configuration;
import org.javaup.lockinfo.impl.ServiceLockInfoHandle;
import org.springframework.context.annotation.Configuration;
import org.javaup.servicelock.aspect.ServiceLockAspect;
import org.springframework.context.annotation.Configuration;
import org.javaup.servicelock.factory.ServiceLockFactory;
import org.springframework.context.annotation.Configuration;
import org.javaup.util.ServiceLockTool;
import org.springframework.context.annotation.Configuration;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @program: movie-ticket 
 * @description: 配置
 * @author: xiong
 **/
@Configuration
public class ServiceLockAutoConfiguration {
    
    @Bean(LockInfoType.SERVICE_LOCK)
    public LockInfoHandle serviceLockInfoHandle(){
        return new ServiceLockInfoHandle();
    }
    
    @Bean
    public ManageLocker manageLocker(RedissonClient redissonClient){
        return new ManageLocker(redissonClient);
    }
    
    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker){
        return new ServiceLockFactory(manageLocker);
    }
    
    @Bean
    public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockAspect(lockInfoHandleFactory,serviceLockFactory);
    }
    
    @Bean
    public ServiceLockTool serviceLockTooL(LockInfoHandleFactory lockInfoHandleFactory,ServiceLockFactory serviceLockFactory){
        return new ServiceLockTool(lockInfoHandleFactory,serviceLockFactory);
    }
}
