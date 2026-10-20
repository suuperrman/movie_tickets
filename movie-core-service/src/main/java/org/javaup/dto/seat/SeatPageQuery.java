package org.javaup.dto.seat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 座位分页查询参数（继承通用分页基类）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SeatPageQuery extends BasePageQuery {

    /** 影厅 ID（精确过滤） */
    private Long hallId;

    /** 1=启用 0=停用 */
    private Integer isActive;

    /** 行号（精确） */
    private Integer rowNo;
}
