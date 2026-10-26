package org.javaup.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单详情（用户端 / 管理端共用结构）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetailVO {
    private Long id;
    private String orderNo;
    private Long userId;
    private String username;
    /** 0待支付 1已支付 2已取消 3退款中 4已退款 5已完成 */
    private Integer status;
    private Long movieId;
    private String movieName;
    private String moviePoster;
    private Long cinemaId;
    private String cinemaName;
    private String cinemaAddress;
    private Long hallId;
    private String hallName;
    private Integer hallType;
    private String startTime;
    private String endTime;
    private BigDecimal price;
    private Integer seatCount;
    private BigDecimal totalAmount;
    private BigDecimal discount;
    private BigDecimal payAmount;
    private String expireAt;
    private String payTime;
    private String cancelTime;
    /** 退款原因（用户申请或管理员审核填写） */
    private String refundReason;
    /** 退款申请时间 */
    private String refundApplyAt;
    /** 退款审核时间 */
    private String refundAuditAt;
    private String createdAt;
    private List<OrderItemVO> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemVO {
        private String seatCode;
        private Integer rowNo;
        private Integer colNo;
        private BigDecimal price;
    }
}
