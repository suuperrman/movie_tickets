package org.javaup.dto.order;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户端：退款申请入参
 */
@Data
public class RefundApplyDTO {

    /** 退款原因（选填，最多 200 字） */
    @Size(max = 200, message = "退款原因不能超过 200 字")
    private String reason;
}
