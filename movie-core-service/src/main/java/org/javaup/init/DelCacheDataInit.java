package org.javaup.init;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.javaup.redis.RedisCache;
import org.javaup.service.ISeckillVoucherService;
import org.javaup.service.IShopService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @program: movie-ticket
 * @description: 删除缓存数据
 * @author: xiong
 **/
@Slf4j
@Order(3)
@Component
public class DelCacheDataInit {
    
    @Resource
    private RedisCache redisCache;
    
    @Resource
    private IShopService shopService;
    
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @PostConstruct
    public void init(){

    }
}
