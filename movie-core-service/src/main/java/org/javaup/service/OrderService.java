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

    // ========== 管理端 ==========
    Page<AdminOrderListVO> adminPage(AdminOrderPageQuery query);
    OrderDetailVO adminDetail(Long orderId);
    void adminRefund(Long orderId, String reason);
}
