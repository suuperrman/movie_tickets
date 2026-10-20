package org.javaup.dto.hall;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 影厅分页查询参数（继承通用分页基类）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HallPageQuery extends BasePageQuery {

    /** 影厅名模糊搜索 */
    private String keyword;

    /** 影院 ID（精确过滤） */
    private Long cinemaId;

    /** 影厅类型 */
    private Integer type;
}
