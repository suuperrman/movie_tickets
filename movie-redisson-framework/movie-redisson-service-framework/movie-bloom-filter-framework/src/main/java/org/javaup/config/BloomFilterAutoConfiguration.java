package org.javaup.config;

import org.javaup.handler.BloomFilterHandlerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Configuration;

/**
 * @program: movie-ticket 
 * @description: 布隆过滤器 配置
 * @author: xiong
 **/
@EnableConfigurationProperties(BloomFilterProperties.class)
@Configuration
public class BloomFilterAutoConfiguration {
    
    @Bean
    public BloomFilterHandlerFactory bloomFilterHandlerFactory(){
        return new BloomFilterHandlerFactory();
    }

    @Bean
    public BloomFilterHandlerRegistrar bloomFilterHandlerRegistrar(Environment environment){
        return new BloomFilterHandlerRegistrar(environment);
    }
}
