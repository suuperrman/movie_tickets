package org.javaup.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户端：创建订单请求
 */
@Data
public class OrderCreateDTO {

    /** 场次 ID */
    @NotNull(message = "场次 ID 不能为空")
    private Long scheduleId;

    /** 选中的座位位置（行/列），服务端按场次解析为 schedule_seat */
    @Valid
    @NotNull(message = "请选择座位")
    @Size(min = 1, max = 15, message = "每次最多选 15 个座位")
    private List<SeatPosition> seats;

    /** 座位位置：行号、列号（与影厅布局一一对应） */
    @Data
    public static class SeatPosition {

        @NotNull(message = "行号不能为空")
        private Integer rowNo;

        @NotNull(message = "列号不能为空")
        private Integer colNo;
    }
}
