package org.javaup.service;

import org.javaup.model.SeckillVoucherFullModel;

/**
 * 秒杀优惠券服务接口
 */
public interface ISeckillVoucherService {

    /**
     * 根据优惠券id查询秒杀优惠券完整信息（带 Redis 缓存，并初始化库存缓存）
     */
    SeckillVoucherFullModel queryByVoucherId(Long voucherId);

    /**
     * 秒杀下单：Redis Lua 原子预扣库存 → 投递 Kafka 异步落库
     *
     * @return 订单ID，前端凭此轮询下单结果
     */
    Long seckill(Long voucherId, Long userId);

    /**
     * 查询秒杀下单结果状态
     *
     * @return PENDING / SUCCESS / NOT_FOUND
     */
    String getOrderStatus(Long orderId);
}
