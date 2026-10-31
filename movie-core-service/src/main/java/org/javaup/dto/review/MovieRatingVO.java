package org.javaup.dto.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 电影评分聚合 VO（平均分 + 评价数）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieRatingVO {

    /** 平均评分（保留 1 位小数），无评价时为 null */
    private BigDecimal avgRating;

    /** 已通过审核的评价数量 */
    private Integer reviewCount;

    /** 未评分时返回 0 评价数的空对象 */
    public static MovieRatingVO empty() {
        return MovieRatingVO.builder().avgRating(null).reviewCount(0).build();
    }
}
