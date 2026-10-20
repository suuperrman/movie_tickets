package org.javaup.dto.movie;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 用户端：电影搜索查询参数
 *
 * 继承通用分页基类 BasePageQuery（pageNo / pageSize），
 * 只加业务特有的筛选字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MovieSearchQuery extends BasePageQuery {

    /** 搜索关键字（匹配片名 / 导演 / 演员） */
    private String keyword;

    /** 分类过滤（可选） */
    private Long categoryId;
}
