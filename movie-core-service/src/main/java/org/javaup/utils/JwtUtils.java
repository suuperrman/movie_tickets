package org.javaup.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.config.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（JJWT 0.12.6）
 *
 * JJWT 0.12.x API 变更要点：
 * ────────────────────────────────────────────────────────────────────
 * 1. 依赖拆分为三件套：jjwt-api / jjwt-impl / jjwt-jackson
 * 2. 密钥构造：Keys.hmacShaKeyFor(byte[]) 返回 SecretKey（弃用 SignatureAlgorithm.getJcaName）
 * 3. builder API 变化：
 *    - 旧：builder.setClaims(map) + signWith(SignatureAlgorithm.HS256, keyBytes)
 *    - 新：builder.claim(name, val) + signWith(key, Jwts.SIG.HS256)
 *    - subject() / issuedAt() / expiration() / compact() 保留
 * 4. parser API 变化：
 *    - 旧：parser().setSigningKey(key).parseClaimsJws(token).getBody()
 *    - 新：parser().verifyWith(key).build().parseSignedClaims(token).getPayload()
 * 5. 过期异常仍为 ExpiredJwtException，在 parseSignedClaims 抛出
 * ────────────────────────────────────────────────────────────────────
 *
 * Access Token：15 分钟，type="access"，用于业务接口鉴权
 * Refresh Token：7 天，type="refresh"，仅用于刷新，存 Redis 做重放检测
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final AuthProperties authProperties;

    /**
     * 获取 HMAC-SHA256 签名密钥
     * 使用 Keys.hmacShaKeyFor()（0.12.x 推荐方式）
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(Long userId, String phone, Integer role) {
        long expireMillis = authProperties.getAccessTokenExpireMinutes() * 60 * 1000L;
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMillis);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expireAt)
                .claim("uid", userId)
                .claim("phone", phone)
                .claim("role", role)
                .claim("type", "access")
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 生成 Refresh Token
     *
     * @param userId   用户 ID
     * @param tokenId  Refresh Token 唯一 ID（UUID），Redis 存储键
     * @param familyId Token Family ID（同一轮换链的所有 Refresh Token 共享）
     */
    public String generateRefreshToken(Long userId, String tokenId, String familyId) {
        long expireMillis = authProperties.getRefreshTokenExpireDays() * 24L * 60 * 60 * 1000;
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMillis);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expireAt)
                .claim("uid", userId)
                .claim("tid", tokenId)
                .claim("fid", familyId)
                .claim("type", "refresh")
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并验证 JWT
     *
     * @param token JWT 字符串
     * @return Claims；过期/无效返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期");
            return null;
        } catch (UnsupportedJwtException | MalformedJwtException
                | SignatureException | IllegalArgumentException e) {
            log.warn("JWT 无效: {}", e.getMessage());
            return null;
        }
    }

    // ==================== Claims 读取工具 ====================

    public Long getUserId(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object uid = claims.get("uid");
        return uid == null ? null : Long.valueOf(uid.toString());
    }

    public String getPhone(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object phone = claims.get("phone");
        return phone == null ? null : phone.toString();
    }

    public Integer getRole(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object role = claims.get("role");
        return role == null ? 0 : Integer.valueOf(role.toString());
    }

    public String getTokenId(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object tid = claims.get("tid");
        return tid == null ? null : tid.toString();
    }

    public String getFamilyId(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object fid = claims.get("fid");
        return fid == null ? null : fid.toString();
    }

    public boolean isAccessToken(Claims claims) {
        return claims != null && "access".equals(claims.get("type"));
    }

    public boolean isRefreshToken(Claims claims) {
        return claims != null && "refresh".equals(claims.get("type"));
    }

    /** Access Token 过期秒数 */
    public long getAccessTokenExpireSeconds() {
        return authProperties.getAccessTokenExpireMinutes() * 60L;
    }
}
