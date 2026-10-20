package org.javaup.dto.seat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 座位列表项 / 详情 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatVO {
    private Long id;
    private Long hallId;
    private String hallName;
    private Integer rowNo;
    private Integer colNo;
    private String seatCode;
    /** 1=启用 0=停用 */
    private Integer isActive;
}
