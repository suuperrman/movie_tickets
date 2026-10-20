package org.javaup.dto.cinema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 影院分页查询参数（继承通用分页基类）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CinemaPageQuery extends BasePageQuery {

    /** 名称模糊搜索 */
    private String keyword;

    /** 城市（精确） */
    private String city;

    /** 1=正常 0=停用 */
    private Integer status;
}
