package org.javaup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.dto.order.*;
import org.javaup.entity.Order;

/**
 * 订单模块服务接口
 */
public interface OrderService extends IService<Order> {

    // ========== 用户端 ==========
    Order create(Long userId, OrderCreateDTO dto);
    Page<UserOrderListVO> myOrders(Long userId, UserOrderPageQuery query);
    OrderDetailVO userDetail(Long userId, Long orderId);
    void userCancel(Long userId, Long orderId);
    void userPay(Long userId, Long orderId);
    /** 用户申请退款：已支付(1) → 退款中(3) */
    void userApplyRefund(Long userId, Long orderId, String reason);

    // ========== 管理端 ==========
    Page<AdminOrderListVO> adminPage(AdminOrderPageQuery query);
    OrderDetailVO adminDetail(Long orderId);
    void adminRefund(Long orderId, String reason);
    /** 管理端退款审核：approve=true 同意（退款中→已退款），approve=false 拒绝（退款中→已支付） */
    void adminAuditRefund(Long orderId, Boolean approve, String reason);
}
