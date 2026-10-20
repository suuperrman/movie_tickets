package org.javaup.service;

import org.javaup.model.SeckillVoucherFullModel;

/**
 * @description: 秒杀优惠券服务接口（最小实现）
 **/
public interface ISeckillVoucherService {

    /**
     * 根据优惠券id查询秒杀优惠券完整信息
     * */
    SeckillVoucherFullModel queryByVoucherId(Long voucherId);
}
