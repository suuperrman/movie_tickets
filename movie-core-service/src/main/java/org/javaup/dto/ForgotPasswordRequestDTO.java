package org.javaup.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 忘记密码请求 DTO
 */
@Data
public class ForgotPasswordRequestDTO {

    /**
     * 注册手机号
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 邮箱（重置链接发送到该邮箱）
     */
    @NotBlank(message = "邮箱不能为空")
    private String email;
}
