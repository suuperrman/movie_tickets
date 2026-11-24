package org.javaup.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀下单消息体（Redis 预扣库存成功后投递，消费端异步落库）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable {

    /** 订单ID（雪花ID，生产者生成，消费端直接作为主键） */
    private Long orderId;

    /** 优惠券ID */
    private Long voucherId;

    /** 用户ID */
    private Long userId;

    /** 店铺ID */
    private Long shopId;
}
