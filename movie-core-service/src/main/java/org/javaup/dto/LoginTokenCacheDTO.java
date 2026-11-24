package org.javaup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录结果快照（Redis 缓存，TTL 内同账号同设备重复登录直接复用）
 *
 * <p>存在意义：同一个用户短时间内反复点"登录"时，
 * 不必每次都查库 + 重新签发 JWT，直接复用上一次的 Token。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginTokenCacheDTO {

    /** 用户 ID */
    private Long userId;

    /** Access Token */
    private String accessToken;

    /** Refresh Token */
    private String refreshToken;

    /** Refresh Token ID（Redis 键后缀） */
    private String refreshTokenId;

    /** Token Family ID */
    private String familyId;

    /** 用户信息 */
    private UserInfoVO user;
}
