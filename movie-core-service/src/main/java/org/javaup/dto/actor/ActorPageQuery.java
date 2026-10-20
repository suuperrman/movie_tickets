package org.javaup.dto.actor;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.dto.BasePageQuery;

/**
 * 演员分页查询参数（继承通用分页基类）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActorPageQuery extends BasePageQuery {

    /** 姓名模糊搜索 */
    private String keyword;

    /** 1=启用 0=停用；不传则全部 */
    private Integer status;
}
