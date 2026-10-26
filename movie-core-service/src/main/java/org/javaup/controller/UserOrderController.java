package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.order.*;
import org.javaup.entity.Order;
import org.javaup.service.OrderService;
import org.javaup.service.RateLimitService;
import org.javaup.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端订单接口（/order，必须登录）
 *
 * 接口清单：
 *   POST   /order                    创建订单（锁座）
 *   GET    /order/my                 我的订单列表
 *   GET    /order/{id}               订单详情（含座位明细）
 *   POST   /order/{id}/cancel        取消订单（释放锁座）
 *   POST   /order/{id}/pay           支付成功（锁座→已售）
 */
@Slf4j
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class UserOrderController {

    private final OrderService orderService;
    private final RateLimitService rateLimitService;

    @PostMapping
    public Result<Order> create(@Valid @RequestBody OrderCreateDTO dto) {
        Long userId = UserHolder.getUser().getId();
        // 用户级限流：10秒内最多3次下单请求，防止恶意刷票
        rateLimitService.tryAcquire("order_create", String.valueOf(userId), 3, 10);
        log.info("[User] 创建订单 userId={} scheduleId={} seatCount={}", userId, dto.getScheduleId(), dto.getSeatIds().size());
        return Result.ok(orderService.create(userId, dto));
    }

    @GetMapping("/my")
    public Result<Page<UserOrderListVO>> myOrders(UserOrderPageQuery query) {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(orderService.myOrders(userId, query));
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable("id") Long id) {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(orderService.userDetail(userId, id));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        Long userId = UserHolder.getUser().getId();
        log.info("[User] 取消订单 userId={} orderId={}", userId, id);
        orderService.userCancel(userId, id);
        return Result.ok();
    }

    @PostMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable("id") Long id) {
        Long userId = UserHolder.getUser().getId();
        // 用户级限流：10秒内最多3次支付请求，防止重复提交
        rateLimitService.tryAcquire("order_pay", String.valueOf(userId), 3, 10);
        log.info("[User] 支付成功 userId={} orderId={}", userId, id);
        orderService.userPay(userId, id);
        return Result.ok();
    }

    @PostMapping("/{id}/refund")
    public Result<Void> applyRefund(@PathVariable("id") Long id, @Valid @RequestBody RefundApplyDTO dto) {
        Long userId = UserHolder.getUser().getId();
        // 用户级限流：60秒内最多3次退款申请，防止恶意刷接口
        rateLimitService.tryAcquire("order_refund", String.valueOf(userId), 3, 60);
        log.info("[User] 申请退款 userId={} orderId={}", userId, id);
        orderService.userApplyRefund(userId, id, dto.getReason());
        return Result.ok();
    }
}
