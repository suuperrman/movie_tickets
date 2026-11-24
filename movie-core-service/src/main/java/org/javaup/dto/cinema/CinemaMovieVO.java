package org.javaup.dto.cinema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 影院详情页：该影院当日正在放映的电影
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaMovieVO {
    private Long id;
    private String name;
    private String posterUrl;
    private Integer duration;
    private String director;
    /** 当日最低票价 */
    private BigDecimal minPrice;
    /** 当日场次数量 */
    private Integer scheduleCount;
}
