package org.javaup.dto.order;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 用户端：我的订单分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserOrderPageQuery extends BasePageQuery {

    /** 0待支付 1已支付 2已取消 4已退款；不传则全部 */
    private Integer status;
}
