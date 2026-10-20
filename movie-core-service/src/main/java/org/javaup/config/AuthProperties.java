package org.javaup.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 登录模块配置属性
 * 密钥从环境变量读取，禁止硬编码
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /**
     * JWT 签名密钥（必须通过环境变量 AUTH_JWT_SECRET 注入）
     * 建议使用至少 256 位的随机字符串
     */
    private String jwtSecret = "${AUTH_JWT_SECRET:changeme-in-production}";

    /**
     * Access Token 过期时间（分钟）
     */
    private int accessTokenExpireMinutes = 15;

    /**
     * Refresh Token 过期时间（天）
     */
    private int refreshTokenExpireDays = 7;

    /**
     * Refresh Token Cookie 名称
     */
    private String refreshTokenCookieName = "refresh_token";

    /**
     * Refresh Token Cookie Domain
     */
    private String refreshTokenCookieDomain = "";

    /**
     * Refresh Token Cookie Path
     */
    private String refreshTokenCookiePath = "/";

    /**
     * 是否启用 Secure Cookie（生产环境必须启用）
     */
    private boolean cookieSecure = false;

    /**
     * Cookie SameSite 策略（Strict / Lax / None）
     */
    private String cookieSameSite = "Lax";
}
