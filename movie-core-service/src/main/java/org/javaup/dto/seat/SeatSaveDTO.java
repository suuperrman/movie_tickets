package org.javaup.dto.seat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/修改物理座位请求 DTO
 */
@Data
public class SeatSaveDTO {

    @NotNull(message = "影厅 ID 不能为空")
    private Long hallId;

    @NotNull(message = "行号不能为空")
    @Min(1) @Max(50)
    private Integer rowNo;

    @NotNull(message = "列号不能为空")
    @Min(1) @Max(50)
    private Integer colNo;

    @Size(max = 20)
    private String seatCode;

    /** 1=启用 0=停用 */
    private Integer isActive;
}
