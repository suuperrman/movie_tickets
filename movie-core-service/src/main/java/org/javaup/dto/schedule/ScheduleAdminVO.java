package org.javaup.dto.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 场次列表项 / 详情 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleAdminVO {
    private Long id;
    private Long movieId;
    private String movieName;
    private Long cinemaId;
    private String cinemaName;
    private Long hallId;
    private String hallName;
    private String startTime;
    private String endTime;
    private BigDecimal price;
    private Integer totalSeat;
    private Integer availableSeat;
    /** 1=可售 0=停售 */
    private Integer status;
    private Integer version;
    private String createdAt;
}
