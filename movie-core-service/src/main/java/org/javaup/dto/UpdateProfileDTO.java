package org.javaup.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改用户资料请求 DTO
 *
 * 所有字段均为可选，只传需要修改的字段。
 * 手机号不可修改（作为登录账号）。
 */
@Data
public class UpdateProfileDTO {

    /**
     * 昵称（最长 50 个字符）
     */
    @Size(max = 50, message = "昵称最长 50 个字符")
    private String nickname;

    /**
     * 头像 URL
     */
    @Size(max = 255, message = "头像 URL 过长")
    private String avatar;
}
