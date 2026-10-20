package org.javaup.dto.movie;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 电影分页查询
 *
 * 继承通用分页基类 BasePageQuery（包含 pageNo/pageSize），
 * 只在这里加业务特有的筛选字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MoviePageQuery extends BasePageQuery {

    /** 电影名称 / 导演模糊搜索 */
    private String keyword;

    /** 1=上架 0=下架；不传则全部 */
    private Integer status;
}
