package org.javaup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 响应（Access Token 在 JSON 中返回，Refresh Token 通过 HttpOnly Cookie 设置）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    /**
     * JWT Access Token（存前端内存）
     */
    private String accessToken;

    /**
     * Access Token 过期时间（秒）
     */
    private Long expiresIn;

    /**
     * Token 类型（Bearer）
     */
    private String tokenType;

    /**
     * 当前用户基本信息
     */
    private UserInfoVO user;
}
