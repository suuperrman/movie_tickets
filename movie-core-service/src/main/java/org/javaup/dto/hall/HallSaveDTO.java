package org.javaup.dto.hall;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/修改影厅请求 DTO
 */
@Data
public class HallSaveDTO {

    /** 所属影院 */
    @NotNull(message = "影院 ID 不能为空")
    private Long cinemaId;

    @NotBlank(message = "影厅名不能为空")
    @Size(max = 50, message = "影厅名最长 50")
    private String name;

    @NotNull(message = "座位行数不能为空")
    @Min(value = 1, message = "行数至少 1")
    @Max(value = 50, message = "行数最多 50")
    private Integer rowCount;

    @NotNull(message = "座位列数不能为空")
    @Min(value = 1, message = "列数至少 1")
    @Max(value = 50, message = "列数最多 50")
    private Integer colCount;
}
