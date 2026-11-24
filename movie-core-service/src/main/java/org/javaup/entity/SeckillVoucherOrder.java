package org.javaup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀优惠券订单
 *
 * 消费端幂等依赖唯一索引 uk_voucher_user(voucher_id, user_id)。
 */
@TableName("seckill_voucher_order")
@Data
public class SeckillVoucherOrder implements Serializable {

    /** 订单ID，雪花ID，应用层生成 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 优惠券ID */
    private Long voucherId;

    /** 用户ID */
    private Long userId;

    /** 店铺ID */
    private Long shopId;

    /** 状态：1已创建 2已支付 3已取消 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
