package org.javaup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 重置密码请求 DTO
 */
@Data
public class ResetPasswordRequestDTO {

    /**
     * 重置令牌（一次性，有效期30分钟）
     */
    @NotBlank(message = "重置令牌不能为空")
    private String resetToken;

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d@$!%*#?&]{8,}$",
            message = "密码至少8位，需包含大小写字母和数字")
    private String newPassword;
}
