package org.javaup.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用户端：我的订单列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOrderListVO {
    private Long id;
    private String orderNo;
    /** 0待支付 1已支付 2已取消 3退款中 4已退款 5已完成 */
    private Integer status;
    private String movieName;
    private String moviePoster;
    private String cinemaName;
    private String hallName;
    private String startTime;
    private Integer seatCount;
    private BigDecimal payAmount;
    private String createdAt;
    private String payTime;
}
