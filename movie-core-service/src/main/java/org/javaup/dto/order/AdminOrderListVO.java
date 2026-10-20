package org.javaup.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 管理端：订单列表项（比用户端多 userId / username）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderListVO {
    private Long id;
    private String orderNo;
    private Long userId;
    private String username;
    /** 0待支付 1已支付 2已取消 3退款中 4已退款 5已完成 */
    private Integer status;
    private String movieName;
    private String cinemaName;
    private Integer seatCount;
    private BigDecimal payAmount;
    private String createdAt;
    private String payTime;
    private String cancelTime;
}
