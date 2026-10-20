package org.javaup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 注册请求 DTO
 */
@Data
public class RegisterFormDTO {

    /**
     * 手机号（作为登录用户名）
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 密码（至少8位，包含大小写字母和数字）
     */
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d@$!%*#?&]{8,}$",
            message = "密码至少8位，需包含大小写字母和数字")
    private String password;

    /**
     * 昵称（可选）
     */
    private String nickname;

    /**
     * 图形验证码 ID（连续失败多次后需要）
     */
    private String captchaId;

    /**
     * 验证码值
     */
    private String captchaCode;
}
