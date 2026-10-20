package org.javaup.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 电影详情 VO（管理端 & 用户端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieDetailVO {
    private Long id;
    private String name;
    private String director;
    private Integer duration;
    private String releaseDate;
    private String posterUrl;
    private String description;
    private Integer status;
    private List<CategoryItem> categories;
    private List<ActorItem> actors;
    /** 最低票价（用户端） */
    private java.math.BigDecimal minPrice;
    /** 有排片的影院数量（用户端） */
    private Integer cinemaCount;
    /** 场次列表（管理端详情） */
    private List<ScheduleItem> schedules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryItem {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActorItem {
        private Long id;
        private String name;
        private String avatarUrl;
        private String roleName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleItem {
        private Long scheduleId;
        private String cinemaName;
        private String hallName;
        private String startTime;
        private String endTime;
        private java.math.BigDecimal price;
        private Integer availableSeat;
    }
}
