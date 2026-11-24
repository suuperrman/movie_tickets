package org.javaup.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.service.ISeckillVoucherService;
import org.javaup.service.RateLimitService;
import org.javaup.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀接口（必须登录）
 *
 * 接口清单：
 *   POST /seckill/{voucherId}          秒杀下单，返回订单ID（异步落库）
 *   GET  /seckill/order/{orderId}      查询下单结果：PENDING / SUCCESS / NOT_FOUND
 */
@Slf4j
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final ISeckillVoucherService seckillVoucherService;
    private final RateLimitService rateLimitService;

    @PostMapping("/{voucherId}")
    public Result<Long> seckill(@PathVariable("voucherId") Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 用户级限流：10秒内最多5次，防止脚本刷接口
        rateLimitService.tryAcquire("seckill_order", String.valueOf(userId), 5, 10);
        log.info("[User] 秒杀下单 userId={} voucherId={}", userId, voucherId);
        return Result.ok(seckillVoucherService.seckill(voucherId, userId));
    }

    @GetMapping("/order/{orderId}")
    public Result<String> orderStatus(@PathVariable("orderId") Long orderId) {
        return Result.ok(seckillVoucherService.getOrderStatus(orderId));
    }
}
