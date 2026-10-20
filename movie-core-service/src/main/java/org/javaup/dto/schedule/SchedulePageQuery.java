package org.javaup.dto.schedule;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 场次分页查询参数（继承通用分页基类）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SchedulePageQuery extends BasePageQuery {

    /** 电影 ID */
    private Long movieId;

    /** 影院 ID */
    private Long cinemaId;

    /** 影厅 ID */
    private Long hallId;

    /** 只查某天（格式 yyyy-MM-dd） */
    private String date;

    /** 1=可售 0=停售 */
    private Integer status;
}
