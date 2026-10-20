package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.order.*;
import org.javaup.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端订单接口（/admin/order）
 *
 * 接口清单：
 *   GET   /admin/order/page              分页查询
 *   GET   /admin/order/{id}              详情
 *   POST  /admin/order/{id}/refund       手动退款
 */
@Slf4j
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping("/page")
    public Result<Page<AdminOrderListVO>> page(AdminOrderPageQuery query) {
        return Result.ok(orderService.adminPage(query));
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(orderService.adminDetail(id));
    }

    @PostMapping("/{id}/refund")
    public Result<Void> refund(@PathVariable("id") Long id,
                               @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        log.info("[Admin] 退款 orderId={} reason={}", id, reason);
        orderService.adminRefund(id, reason);
        return Result.ok();
    }
}
