package org.javaup.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.config.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类（jjwt 0.9.1）
 * - Access Token：15 分钟有效期
 * - Refresh Token：7 天有效期（存储在 Redis 中验证）
 *
 * jjwt 0.9.1 API：
 * - builder: setClaims / setSubject / setIssuedAt / setExpiration / signWith(algo, keyBytes)
 * - parser: parser().setSigningKey(key).parseClaimsJws(token).getBody()
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final AuthProperties authProperties;

    /**
     * 获取签名密钥（HMAC-SHA256）
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, SignatureAlgorithm.HS256.getJcaName());
    }

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(Long userId, String phone) {
        long expireMillis = authProperties.getAccessTokenExpireMinutes() * 60 * 1000L;
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMillis);

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("phone", phone);
        claims.put("type", "access");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expireAt)
                .signWith(SignatureAlgorithm.HS256, getSigningKey().getEncoded())
                .compact();
    }

    /**
     * 生成 Refresh Token（包含 tokenId，用于 Redis 存储和重放检测）
     */
    public String generateRefreshToken(Long userId, String tokenId, String familyId) {
        long expireMillis = authProperties.getRefreshTokenExpireDays() * 24 * 60 * 60 * 1000L;
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMillis);

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("tid", tokenId);
        claims.put("fid", familyId);
        claims.put("type", "refresh");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expireAt)
                .signWith(SignatureAlgorithm.HS256, getSigningKey().getEncoded())
                .compact();
    }

    /**
     * 解析并验证 Token
     *
     * @return Claims，验证失败返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.debug("JWT Token 已过期");
            return null;
        } catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
            log.warn("JWT Token 无效: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Claims 中获取用户 ID
     */
    public Long getUserId(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object uid = claims.get("uid");
        if (uid == null) {
            return null;
        }
        return Long.valueOf(uid.toString());
    }

    /**
     * 从 Claims 中获取手机号
     */
    public String getPhone(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object phone = claims.get("phone");
        return phone == null ? null : phone.toString();
    }

    /**
     * 从 Claims 中获取 Token ID（Refresh Token 专用）
     */
    public String getTokenId(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object tid = claims.get("tid");
        return tid == null ? null : tid.toString();
    }

    /**
     * 从 Claims 中获取 Token Family ID
     */
    public String getFamilyId(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object fid = claims.get("fid");
        return fid == null ? null : fid.toString();
    }

    public boolean isAccessToken(Claims claims) {
        if (claims == null) {
            return false;
        }
        Object type = claims.get("type");
        return "access".equals(type);
    }

    public boolean isRefreshToken(Claims claims) {
        if (claims == null) {
            return false;
        }
        Object type = claims.get("type");
        return "refresh".equals(type);
    }

    public long getAccessTokenExpireSeconds() {
        return authProperties.getAccessTokenExpireMinutes() * 60L;
    }
}
