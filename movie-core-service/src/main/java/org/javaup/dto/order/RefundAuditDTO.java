package org.javaup.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端：退款审核入参
 */
@Data
public class RefundAuditDTO {

    /** 是否同意退款：true同意（退款中→已退款）false拒绝（退款中→已支付） */
    @NotNull(message = "审核结果不能为空")
    private Boolean approve;

    /** 审核备注（选填，最多 200 字） */
    @Size(max = 200, message = "审核备注不能超过 200 字")
    private String reason;
}
