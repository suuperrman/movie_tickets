package org.javaup.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理端：评价审核入参
 */
@Data
public class ReviewStatusDTO {

    /** 审核状态：0待审核 1已通过 2已拒绝 */
    @NotNull(message = "审核状态不能为空")
    @Min(value = 0, message = "非法的审核状态")
    @Max(value = 2, message = "非法的审核状态")
    private Integer status;
}
