package org.javaup.dto.movie;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 电影上下架请求 DTO
 */
@Data
public class MovieStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
