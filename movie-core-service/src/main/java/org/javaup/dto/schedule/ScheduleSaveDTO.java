package org.javaup.dto.schedule;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增/修改场次请求 DTO
 */
@Data
public class ScheduleSaveDTO {

    @NotNull(message = "电影 ID 不能为空")
    private Long movieId;

    @NotNull(message = "影院 ID 不能为空")
    private Long cinemaId;

    @NotNull(message = "影厅 ID 不能为空")
    private Long hallId;

    /** 格式 yyyy-MM-dd HH:mm */
    @NotBlank(message = "开场时间不能为空")
    private String startTime;

    /** 格式 yyyy-MM-dd HH:mm（需晚于 startTime） */
    @NotBlank(message = "散场时间不能为空")
    private String endTime;

    @NotNull(message = "票价不能为空")
    @DecimalMin(value = "0.01", message = "票价最低 0.01")
    private BigDecimal price;
}
