package org.javaup.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用户端：购票入口 — 影院 + 该电影在指定日期的场次聚合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaScheduleVO {
    private Long cinemaId;
    private String cinemaName;
    private String city;
    private String address;
    private String phone;
    private BigDecimal minPrice;
    private String firstScheduleTime;
    private Integer scheduleCount;
}
