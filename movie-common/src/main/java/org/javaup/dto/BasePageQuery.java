package org.javaup.dto;

import lombok.Data;

/**
 * 通用分页查询基类
 *
 * 所有模块的分页查询 DTO 都应该继承此类，只在子类里加业务特有字段。
 *
 * <p>注意：movie-common 模块没有 jakarta.validation 依赖，因此基类里
 * 不放 {@code @Min @Max} 等校验注解。需要校验的子类在自己模块里加。</p>
 *
 * <pre>
 *   // 子类示例（放在有 validation 依赖的模块）
 *   @Data
 *   @EqualsAndHashCode(callSuper = true)
 *   public class MoviePageQuery extends BasePageQuery {
 *       @Min(1) private Integer pageNo = 1;   // 覆写加校验
 *       @Min(1) @Max(100) private Integer pageSize = 10;
 *       private String keyword;                 // 业务字段
 *       private Integer status;
 *   }
 * </pre>
 *
 * Controller 用法：
 * <pre>
 *   @GetMapping("/page")
 *   public Result<Page<XXXVO>> page(@Valid MoviePageQuery query) { ... }
 * </pre>
 * Spring MVC 自动把 {@code ?pageNo=1&pageSize=10&keyword=xx} 绑定到 query 对象。
 */
@Data
public class BasePageQuery {

    /** 默认 1 */
    private Integer pageNo = 1;

    /** 默认 10 */
    private Integer pageSize = 10;
}
