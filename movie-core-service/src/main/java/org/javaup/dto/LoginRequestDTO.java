package org.javaup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 登录请求 DTO
 */
@Data
public class LoginRequestDTO {

    /**
     * 手机号（用户名）
     */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 图形验证码 ID（连续失败3次后需要）
     */
    private String captchaId;

    /**
     * 图形验证码值
     */
    private String captchaCode;
}
