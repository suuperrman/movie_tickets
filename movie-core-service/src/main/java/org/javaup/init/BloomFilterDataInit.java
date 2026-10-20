package org.javaup.init;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.javaup.handler.BloomFilterHandlerFactory;
import org.javaup.service.ISeckillVoucherService;
import org.javaup.service.IShopService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @program: movie-ticket
 * @description: 布隆过滤器初始化
 * @author: xiong
 **/
@Slf4j
@Order(1)
@Component
public class BloomFilterDataInit {
    
    @Resource
    private IShopService shopService;
    
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    
    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;

    @PostConstruct
    public void init() {

    }
}
