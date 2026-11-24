package org.javaup.dto.hall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 影厅列表项 / 详情 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HallVO {
    private Long id;
    private Long cinemaId;
    private String cinemaName;
    private String name;
    private Integer rowCount;
    private Integer colCount;
    /** 总座位数 */
    private Integer totalSeat;
    private String createdAt;
}
