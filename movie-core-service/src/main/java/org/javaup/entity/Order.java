package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 订单表
 * @TableName order
 */
@TableName(value ="`order`")
@Data
public class Order implements Serializable {
    /**
     * 主键，雪花ID，应用层生成
     */
    @TableId
    private Long id;

    /**
     * 业务订单号，对外展示，雪花ID/年月日+序列
     */
    private String orderNo;

    /**
     * 下单用户ID，关联 sys_user.id
     */
    private Long userId;

    /**
     * 场次ID，关联 schedule.id
     */
    private Long scheduleId;

    /**
     * 影片ID，冗余字段，关联 movie.id
     */
    private Long movieId;

    /**
     * 影院ID，冗余字段，关联 cinema.id
     */
    private Long cinemaId;

    /**
     * 购票座位数
     */
    private Integer seatCount;

    /**
     * 原始订单总额(座位票价之和)
     */
    private BigDecimal totalAmount;

    /**
     * 优惠金额，优惠券/活动抵扣
     */
    private BigDecimal discount;

    /**
     * 实付金额 = total_amount - discount
     */
    private BigDecimal payAmount;

    /**
     * 订单状态：0待支付 1已支付 2已取消 3退款中 4已退款 5已完成
     */
    private Integer status;

    /**
     * 支付超时时间，超时未支付自动取消(通常下单后15分钟)
     */
    private Date expireAt;

    /**
     * 支付成功时间
     */
    private Date payTime;

    /**
     * 订单取消时间
     */
    private Date cancelTime;

    /**
     * 退款原因（用户申请或管理员填写）
     */
    private String refundReason;

    /**
     * 退款申请时间
     */
    private Date refundApplyAt;

    /**
     * 退款审核时间
     */
    private Date refundAuditAt;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}