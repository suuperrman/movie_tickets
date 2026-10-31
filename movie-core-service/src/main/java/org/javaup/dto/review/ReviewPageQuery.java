package org.javaup.dto.review;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 管理端：评价分页查询
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewPageQuery extends BasePageQuery {

    /** 电影ID过滤 */
    private Long movieId;

    /** 审核状态过滤：0待审核 1已通过 2已拒绝 */
    private Integer status;

    /** 评分过滤：1-5 */
    private Integer rating;

    /** 评价内容关键字 */
    private String keyword;
}
