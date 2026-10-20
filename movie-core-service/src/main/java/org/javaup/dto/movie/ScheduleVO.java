package org.javaup.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 用户端：场次列表 VO
 *
 * 对应数据库 schedule 表，用于 GET /movie/{id}/sessions 返回值。
 * 注意：接口路径叫 sessions（用户视角"放映场次"），
 * 但内部类名/字段名统一叫 Schedule，与数据库表 schedule 对齐。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleVO {
    private Long scheduleId;
    private Long hallId;
    private String hallName;
    /** 1=普通 2=IMAX 3=情侣厅 */
    private Integer hallType;
    private String startTime;
    private String endTime;
    private BigDecimal price;
    private Integer totalSeat;
    private Integer availableSeat;
    /** 1=可售 0=停售 */
    private Integer status;
}
