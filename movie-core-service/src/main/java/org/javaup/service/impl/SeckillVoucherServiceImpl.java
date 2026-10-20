package org.javaup.service.impl;

import org.javaup.model.SeckillVoucherFullModel;
import org.javaup.service.ISeckillVoucherService;
import org.springframework.stereotype.Service;

/**
 * @description: 秒杀优惠券服务实现（最小实现）
 **/
@Service
public class SeckillVoucherServiceImpl implements ISeckillVoucherService {

    @Override
    public SeckillVoucherFullModel queryByVoucherId(Long voucherId) {
        return null;
    }
}
