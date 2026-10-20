package org.javaup.utils;

/**
 * 登录模块 Redis 常量
 */
public final class AuthRedisConstants {

    private AuthRedisConstants() {}

    // ========== 登录失败限流 ==========

    /** 按账号统计登录失败次数 */
    public static final String LOGIN_FAIL_COUNT = "auth:login:fail:";
    public static final long LOGIN_FAIL_COUNT_TTL_SECONDS = 15 * 60L;
    public static final int LOGIN_FAIL_THRESHOLD = 5;
    public static final int CAPTCHA_REQUIRE_THRESHOLD = 3;

    // ========== IP 限流 ==========

    /** 按 IP 统计登录请求次数 */
    public static final String LOGIN_IP_COUNT = "auth:login:ip:";
    public static final long LOGIN_IP_COUNT_TTL_SECONDS = 60L;
    public static final int LOGIN_IP_THRESHOLD = 10;

    // ========== Refresh Token 存储 ==========

    /** Refresh Token 存储 -> userId:familyId */
    public static final String REFRESH_TOKEN_KEY = "auth:refresh:";
    public static final long REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60L;

    /** Token Family 存储 -> Set<tokenId> */
    public static final String TOKEN_FAMILY_KEY = "auth:token-family:";
    public static final long TOKEN_FAMILY_TTL_SECONDS = 7 * 24 * 60 * 60L;

    /** 用户 -> Token Family 集合（用于一键吊销） */
    public static final String USER_TOKEN_FAMILIES_KEY = "auth:user-families:";
    public static final long USER_TOKEN_FAMILIES_TTL_SECONDS = 7 * 24 * 60 * 60L;

    /** Token 会话信息（记录登录设备/IP/时间） */
    public static final String TOKEN_SESSION_KEY = "auth:session:";
    public static final long TOKEN_SESSION_TTL_SECONDS = 7 * 24 * 60 * 60L;

    // ========== 重置密码 ==========

    /** 重置密码令牌 -> userId */
    public static final String RESET_TOKEN_KEY = "auth:reset-token:";
    public static final long RESET_TOKEN_TTL_SECONDS = 30 * 60L;

    // ========== 图形验证码 ==========

    /** 图形验证码 -> code */
    public static final String CAPTCHA_KEY = "auth:captcha:";
    public static final long CAPTCHA_TTL_SECONDS = 5 * 60L;
}
