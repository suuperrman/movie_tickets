package org.javaup.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.javaup.entity.Shop;
import org.javaup.mapper.ShopMapper;
import org.javaup.service.IShopService;
import org.springframework.stereotype.Service;

/**
 * @description: 店铺服务实现（最小实现）
 **/
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
}
