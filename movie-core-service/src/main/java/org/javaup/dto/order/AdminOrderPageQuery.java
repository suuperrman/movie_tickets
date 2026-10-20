package org.javaup.dto.order;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 管理端：订单分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOrderPageQuery extends BasePageQuery {

    /** 精确匹配 */
    private String orderNo;
    private Long userId;
    private Long movieId;
    private Long cinemaId;
    private Integer status;
    /** 按下单日期过滤（yyyy-MM-dd） */
    private String date;
}
