package org.javaup.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 电影列表 VO（管理端分页 / 用户端热映 / 即将上映 共用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieListItemVO {
    private Long id;
    private String name;
    private String director;
    private Integer duration;
    private String releaseDate;
    private String posterUrl;
    private Integer status;
    private String description;
    private String categories;
    /** 最低票价（用户端使用） */
    private java.math.BigDecimal minPrice;
    /** 有排片的影院数量（用户端使用） */
    private Integer cinemaCount;
    /** 排片数量（管理端使用） */
    private Integer scheduleCount;
    /** 平均评分（1 位小数），无评价时为 null */
    private java.math.BigDecimal avgRating;
    /** 已通过审核的评价数量 */
    private Integer reviewCount;
}
