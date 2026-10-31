package org.javaup.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户端：发布电影评价入参
 */
@Data
public class ReviewCreateDTO {

    /** 电影ID */
    @NotNull(message = "电影ID不能为空")
    private Long movieId;

    /** 关联订单ID（可选，前端从订单页发起时会带上） */
    private Long orderId;

    /** 评分 1-5 星 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低 1 星")
    @Max(value = 5, message = "评分最高 5 星")
    private Integer rating;

    /** 评价内容 */
    @Size(max = 500, message = "评价内容不能超过 500 字")
    private String content;
}
