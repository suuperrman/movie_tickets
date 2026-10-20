package org.javaup.dto.movie;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 用户端：正在热映 / 即将上映 查询参数
 *
 * 继承通用分页基类 BasePageQuery（pageNo / pageSize），
 * 只加业务特有的筛选字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MovieListQuery extends BasePageQuery {

    /** 城市（可选） */
    private String city;
}
