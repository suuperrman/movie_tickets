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

    // ========== IP 限流（只统计失败次数） ==========

    /**
     * 按 IP 统计登录失败次数
     *
     * <p>只统计失败：成功登录不计数，避免正常用户/自动化测试被误伤；
     * 失败次数超阈值说明该 IP 正在暴力破解。
     */
    public static final String LOGIN_IP_COUNT = "auth:login:ip:";
    public static final long LOGIN_IP_COUNT_TTL_SECONDS = 60L;
    public static final int LOGIN_IP_THRESHOLD = 20;

    // ========== 账号维度会话签发限流 ==========

    /**
     * 按账号统计「新签发会话」次数
     *
     * <p>只在真正生成新 Token（新增 Token Family）时计数，
     * 幂等复用已有 Token 的登录不计数，因此用户反复登录不会触发限流，
     * 但恶意脚本不断开新会话会被限制。
     */
    public static final String LOGIN_ACCOUNT_COUNT = "auth:login:account:";
    public static final long LOGIN_ACCOUNT_COUNT_TTL_SECONDS = 60L;
    public static final int LOGIN_ACCOUNT_THRESHOLD = 10;

    // ========== 登录幂等（Token 复用） ==========

    /**
     * 登录结果缓存 key = auth:login:token:{userId}:{deviceHash}
     * value = 已签发的 Token 快照，TTL 内同账号同设备重复登录直接复用
     */
    public static final String LOGIN_TOKEN_CACHE_KEY = "auth:login:token:";
    public static final long LOGIN_TOKEN_CACHE_TTL_SECONDS = 30L;

    // ========== 用户信息缓存 ==========

    /** 按手机号缓存用户信息 key = auth:user:cache:{phone} */
    public static final String USER_CACHE_KEY = "auth:user:cache:";
    public static final long USER_CACHE_TTL_SECONDS = 60L;

    /** 用户 ID -> 手机号 的缓存索引 key = auth:user:cache:id:{userId} */
    public static final String USER_CACHE_ID_KEY = "auth:user:cache:id:";

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

    /** 单用户最大并发 Token Family 数，超出则吊销最旧的 */
    public static final int MAX_TOKEN_FAMILY_PER_USER = 5;

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
