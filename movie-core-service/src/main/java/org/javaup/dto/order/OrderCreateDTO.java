package org.javaup.dto.order;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户端：创建订单请求
 */
@Data
public class OrderCreateDTO {

    /** 场次 ID */
    private Long scheduleId;

    /** 选中的 schedule_seat ID 列表 */
    @Size(min = 1, max = 15, message = "每次最多选 15 个座位")
    private List<Long> seatIds;
}
