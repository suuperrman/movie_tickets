package org.javaup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 忘记密码请求 DTO
 *
 * 简化版：仅需手机号即可生成重置令牌
 * （开发阶段 resetToken 直接返回给前端，生产环境应通过短信发送）
 */
@Data
public class ForgotPasswordRequestDTO {

    /**
     * 注册手机号
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
