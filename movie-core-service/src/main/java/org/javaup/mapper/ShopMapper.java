package org.javaup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.javaup.entity.Shop;

/**
 * @description: 店铺 Mapper（最小实现）
 **/
@Mapper
public interface ShopMapper extends BaseMapper<Shop> {
}
