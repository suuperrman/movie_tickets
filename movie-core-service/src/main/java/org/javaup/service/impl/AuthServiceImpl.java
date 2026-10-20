package org.javaup.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.config.AuthProperties;
import org.javaup.dto.*;
import org.javaup.entity.SysUser;
import org.javaup.enums.BaseCode;
import org.javaup.exception.MovieFrameException;
import org.javaup.mapper.SysUserMapper;
import org.javaup.service.AuthService;
import org.javaup.utils.CaptchaGenerator;
import org.javaup.utils.JwtUtils;
import org.javaup.utils.RequestContextUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.javaup.utils.AuthRedisConstants.*;

/**
 * 认证服务实现
 *
 * 所有涉及 HttpServletRequest / HttpServletResponse 的操作
 * 均通过 {@link RequestContextUtils} 在内部获取，
 * 不暴露在 Service 接口签名中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtUtils jwtUtils;
    private final CaptchaGenerator captchaGenerator;
    private final AuthProperties authProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // ==================== 注册 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO register(RegisterFormDTO dto) {
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, dto.getPhone())
        );
        if (count > 0) {
            throw new MovieFrameException(BaseCode.USER_ALREADY_EXIST);
        }

        SysUser user = new SysUser();
        user.setPhone(dto.getPhone());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StrUtil.isBlank(dto.getNickname()) ? "user  _" + dto.getPhone().substring(7) : dto.getNickname());
        user.setStatus(1);
        user.setCreatedAt(new Date());
        user.setUpdatedAt(new Date());

        sysUserMapper.insert(user);
        log.info("用户注册成功 phone={}", dto.getPhone());
        return buildUserInfoVO(user);
    }

    // ==================== 登录 ====================

    @Override
    public TokenResponse login(LoginRequestDTO dto) {
        String phone = dto.getPhone();
        String clientIp = RequestContextUtils.getClientIp();

        // 1. IP 限流
//        if (isIpRateLimited(clientIp)) {
//            log.warn("IP 限流触发 ip={}", clientIp);
//            throw new MovieFrameException(BaseCode.IP_RATE_LIMITED);
//        }
//        incrementIpCount(clientIp);

        // 2. 账号锁定检查
        int failCount = getLoginFailCount(phone);
        if (failCount >= LOGIN_FAIL_THRESHOLD) {
            throw new MovieFrameException(BaseCode.ACCOUNT_LOCKED);
        }

//        // 3. 图形验证码检查
//        if (failCount >= CAPTCHA_REQUIRE_THRESHOLD) {
//            if (StrUtil.isBlank(dto.getCaptchaId()) || StrUtil.isBlank(dto.getCaptchaCode())) {
//                throw new MovieFrameException(BaseCode.CAPTCHA_REQUIRED);
//            }
//            if (!captchaGenerator.verify(dto.getCaptchaId(), dto.getCaptchaCode())) {
//                incrementLoginFail(phone);
//                throw new MovieFrameException(BaseCode.CAPTCHA_INVALID);
//            }
//        }

        // 4. 查询用户（统一错误提示，防暴力破解）
        SysUser user = findUserByPhone(phone);
        if (user == null) {
            incrementLoginFail(phone);
            log.warn("登录失败-用户不存在 phone={}", phone);
            throw new MovieFrameException(BaseCode.AUTH_FAILED);
        }

        // 5. 状态检查
        if (user.getStatus() != null && user.getStatus() != 1) {
            incrementLoginFail(phone);
            throw new MovieFrameException(BaseCode.ACCOUNT_DISABLED);
        }

        // 6. 密码校验
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            incrementLoginFail(phone);
            log.warn("登录失败-密码错误 phone={}", phone);
            throw new MovieFrameException(BaseCode.AUTH_FAILED);
        }

        // 7. 清除失败计数
        stringRedisTemplate.delete(LOGIN_FAIL_COUNT + phone);

        // 8. 签发 Token（内部通过 RequestContextUtils 设置 Cookie）
        return issueTokens(user);
    }

    @Override
    public CaptchaResponseDTO getCaptcha(String phone) {
        int failCount = getLoginFailCount(phone);
        if (failCount < CAPTCHA_REQUIRE_THRESHOLD) {
            return CaptchaResponseDTO.builder().required(false).build();
        }
        String[] captcha = captchaGenerator.generate();
        return CaptchaResponseDTO.builder()
                .captchaId(captcha[0]).captchaImage(captcha[1]).required(true).build();
    }

    // ==================== 刷新令牌（Rotation + 重放检测 + 密码修改检测） ====================

    @Override
    public TokenResponse refresh() {
        // 1. 从 Cookie 中获取 Refresh Token
        String refreshToken = RequestContextUtils.getCookieValue(authProperties.getRefreshTokenCookieName());
        if (StrUtil.isBlank(refreshToken)) {
            throw new MovieFrameException(BaseCode.TOKEN_INVALID);
        }

        Claims claims = jwtUtils.parseToken(refreshToken);
        if (claims == null) {
            throw new MovieFrameException(BaseCode.TOKEN_EXPIRED);
        }
        if (!jwtUtils.isRefreshToken(claims)) {
            throw new MovieFrameException(BaseCode.TOKEN_INVALID);
        }

        Long userId = jwtUtils.getUserId(claims);
        String tokenId = jwtUtils.getTokenId(claims);
        String familyId = jwtUtils.getFamilyId(claims);

        // 2. 检查 Redis 中是否存在（不存在说明已被使用，重放检测）
        String tokenKey = REFRESH_TOKEN_KEY + tokenId;
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey))) {
            log.warn("Refresh Token 重放检测触发 userId={}, familyId={}", userId, familyId);
            revokeTokenFamily(familyId);
            throw new MovieFrameException(BaseCode.REFRESH_TOKEN_REPLAYED);
        }

        // 3. 获取用户
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new MovieFrameException(BaseCode.AUTH_FAILED);
        }

        // 4. 吊销旧 Refresh Token
        stringRedisTemplate.delete(tokenKey);
        stringRedisTemplate.opsForSet().remove(TOKEN_FAMILY_KEY + familyId, tokenId);
        stringRedisTemplate.delete(TOKEN_SESSION_KEY + tokenId);

        // 6. 签发新 Token（Rotation）
        return issueTokens(user);
    }

    // ==================== 登出 ====================

    @Override
    public void logout() {
        String refreshToken = RequestContextUtils.getCookieValue(authProperties.getRefreshTokenCookieName());
        if (StrUtil.isBlank(refreshToken)) {
            clearRefreshTokenCookie();
            return;
        }
        Claims claims = jwtUtils.parseToken(refreshToken);
        if (claims != null && jwtUtils.isRefreshToken(claims)) {
            String tokenId = jwtUtils.getTokenId(claims);
            String familyId = jwtUtils.getFamilyId(claims);
            Long userId = jwtUtils.getUserId(claims);

            stringRedisTemplate.delete(REFRESH_TOKEN_KEY + tokenId);
            stringRedisTemplate.opsForSet().remove(TOKEN_FAMILY_KEY + familyId, tokenId);
            stringRedisTemplate.delete(TOKEN_SESSION_KEY + tokenId);

            Long remaining = stringRedisTemplate.opsForSet().size(TOKEN_FAMILY_KEY + familyId);
            if (remaining != null && remaining == 0) {
                stringRedisTemplate.delete(TOKEN_FAMILY_KEY + familyId);
                stringRedisTemplate.opsForSet().remove(USER_TOKEN_FAMILIES_KEY + userId, familyId);
            }

            clearRefreshTokenCookie();
            log.info("用户登出 userId={}", userId);
        }
    }

    // ==================== 当前用户 ====================

    @Override
    public UserInfoVO me(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
        }
        return buildUserInfoVO(user);
    }

    // ==================== 忘记密码 ====================

    @Override
    public String forgotPassword(ForgotPasswordRequestDTO dto) {
        SysUser user = findUserByPhone(dto.getPhone());
        if (user == null) {
            log.warn("忘记密码-用户不存在 phone={}", dto.getPhone());
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
        }

        // 生成一次性重置令牌（开发阶段直接返回，生产环境应通过短信发送）
        String resetToken = IdUtil.simpleUUID();
        stringRedisTemplate.opsForValue().set(
                RESET_TOKEN_KEY + resetToken, String.valueOf(user.getId()),
                RESET_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

        log.info("生成重置密码令牌 userId={}", user.getId());
        return resetToken;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequestDTO dto) {
        String userIdStr = stringRedisTemplate.opsForValue().get(RESET_TOKEN_KEY + dto.getResetToken());
        if (userIdStr == null) {
            throw new MovieFrameException(BaseCode.RESET_TOKEN_INVALID);
        }
        stringRedisTemplate.delete(RESET_TOKEN_KEY + dto.getResetToken());

        Long userId = Long.valueOf(userIdStr);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(new Date());
        sysUserMapper.updateById(user);

        invalidateAllUserTokens(userId);
        log.info("密码重置成功 userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordRequestDTO dto) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new MovieFrameException(BaseCode.OLD_PASSWORD_WRONG);
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(new Date());
        sysUserMapper.updateById(user);

        invalidateAllUserTokens(userId);
        log.info("用户修改密码成功 userId={}", userId);
    }

    // ==================== 会话管理 ====================

    @Override
    public List<SessionVO> sessions(Long userId) {
        Set<String> familyIds = stringRedisTemplate.opsForSet().members(USER_TOKEN_FAMILIES_KEY + userId);
        List<SessionVO> result = new ArrayList<>();
        if (familyIds == null) {
            return result;
        }

        // 当前会话：从 Cookie 中的 Refresh Token 获取 tokenId
        String currentTokenId = null;
        String refreshToken = RequestContextUtils.getCookieValue(authProperties.getRefreshTokenCookieName());
        if (refreshToken != null) {
            Claims claims = jwtUtils.parseToken(refreshToken);
            if (claims != null) {
                currentTokenId = jwtUtils.getTokenId(claims);
            }
        }

        for (String familyId : familyIds) {
            Set<String> tokenIds = stringRedisTemplate.opsForSet().members(TOKEN_FAMILY_KEY + familyId);
            if (tokenIds == null) {
                continue;
            }
            for (String tokenId : tokenIds) {
                String sessionJson = stringRedisTemplate.opsForValue().get(TOKEN_SESSION_KEY + tokenId);
                if (sessionJson == null) {
                    continue;
                }
                try {
                    SessionVO vo = JSON.parseObject(sessionJson, SessionVO.class);
                    vo.setCurrent(tokenId.equals(currentTokenId));
                    result.add(vo);
                } catch (Exception ignore) {}
            }
        }
        return result;
    }

    @Override
    public void revokeSession(Long userId, String sessionId) {
        String stored = stringRedisTemplate.opsForValue().get(REFRESH_TOKEN_KEY + sessionId);
        if (stored != null) {
            stringRedisTemplate.delete(REFRESH_TOKEN_KEY + sessionId);
            stringRedisTemplate.delete(TOKEN_SESSION_KEY + sessionId);
            String familyId = stored.split(":")[1];
            stringRedisTemplate.opsForSet().remove(TOKEN_FAMILY_KEY + familyId, sessionId);
            log.info("强制下线 sessionId={} userId={}", sessionId, userId);
        }
    }

    // ==================== 核心私有方法 ====================

    /**
     * 签发 Token + 设置 Refresh Token Cookie
     */
    private TokenResponse issueTokens(SysUser user) {
        Long userId = user.getId();
        String phone = user.getPhone();

        String familyId = IdUtil.fastSimpleUUID();
        String refreshTokenId = IdUtil.fastSimpleUUID();

        String accessToken = jwtUtils.generateAccessToken(userId, phone);
        String refreshToken = jwtUtils.generateRefreshToken(userId, refreshTokenId, familyId);

        // 存储 Refresh Token
        stringRedisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY + refreshTokenId, userId + ":" + familyId,
                REFRESH_TOKEN_TTL_SECONDS, TimeUnit.SECONDS);

        // Token Family
        stringRedisTemplate.opsForSet().add(TOKEN_FAMILY_KEY + familyId, refreshTokenId);
        stringRedisTemplate.expire(TOKEN_FAMILY_KEY + familyId, TOKEN_FAMILY_TTL_SECONDS, TimeUnit.SECONDS);

        // userId → familyIds 映射
        stringRedisTemplate.opsForSet().add(USER_TOKEN_FAMILIES_KEY + userId, familyId);
        stringRedisTemplate.expire(USER_TOKEN_FAMILIES_KEY + userId, USER_TOKEN_FAMILIES_TTL_SECONDS, TimeUnit.SECONDS);

        // 会话记录
        String clientIp = RequestContextUtils.getClientIp();
        String userAgent = RequestContextUtils.getUserAgent();
        String deviceType = parseDeviceType(userAgent);
        SessionVO session = SessionVO.builder()
                .sessionId(refreshTokenId).deviceType(deviceType)
                .ip(clientIp).loginTime(SDF.format(new Date()))
                .build();
        stringRedisTemplate.opsForValue().set(
                TOKEN_SESSION_KEY + refreshTokenId, JSON.toJSONString(session),
                TOKEN_SESSION_TTL_SECONDS, TimeUnit.SECONDS);

        // 设置 Refresh Token Cookie
        RequestContextUtils.setCookie(
                authProperties.getRefreshTokenCookieName(),
                refreshToken,
                authProperties.getRefreshTokenExpireDays() * 24 * 60 * 60,
                authProperties.getRefreshTokenCookiePath(),
                true,
                authProperties.isCookieSecure(),
                authProperties.getCookieSameSite()
        );

        return TokenResponse.builder()
                .accessToken(accessToken)
                .expiresIn(jwtUtils.getAccessTokenExpireSeconds())
                .tokenType("Bearer")
                .user(buildUserInfoVO(user))
                .build();
    }

    private void revokeTokenFamily(String familyId) {
        if (StrUtil.isBlank(familyId)) {
            return;
        }
        Set<String> tokenIds = stringRedisTemplate.opsForSet().members(TOKEN_FAMILY_KEY + familyId);
        if (tokenIds != null) {
            for (String tokenId : tokenIds) {
                stringRedisTemplate.delete(REFRESH_TOKEN_KEY + tokenId);
                stringRedisTemplate.delete(TOKEN_SESSION_KEY + tokenId);
            }
        }
        stringRedisTemplate.delete(TOKEN_FAMILY_KEY + familyId);
    }

    private void invalidateAllUserTokens(Long userId) {
        Set<String> familyIds = stringRedisTemplate.opsForSet().members(USER_TOKEN_FAMILIES_KEY + userId);
        if (familyIds != null) {
            for (String familyId : familyIds) {
                Set<String> tokenIds = stringRedisTemplate.opsForSet().members(TOKEN_FAMILY_KEY + familyId);
                if (tokenIds != null) {
                    for (String tokenId : tokenIds) {
                        stringRedisTemplate.delete(REFRESH_TOKEN_KEY + tokenId);
                        stringRedisTemplate.delete(TOKEN_SESSION_KEY + tokenId);
                    }
                }
                stringRedisTemplate.delete(TOKEN_FAMILY_KEY + familyId);
            }
            stringRedisTemplate.delete(USER_TOKEN_FAMILIES_KEY + userId);
            log.info("已吊销用户所有 Refresh Token userId={}", userId);
        }
    }

    private void clearRefreshTokenCookie() {
        RequestContextUtils.clearCookie(
                authProperties.getRefreshTokenCookieName(),
                authProperties.getRefreshTokenCookiePath(),
                authProperties.isCookieSecure(),
                authProperties.getCookieSameSite()
        );
    }

    // ----- 工具方法 -----

    private SysUser findUserByPhone(String phone) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone));
    }

    private UserInfoVO buildUserInfoVO(SysUser user) {
        return UserInfoVO.builder()
                .id(user.getId()).phone(user.getPhone())
                .nickname(user.getNickname()).avatar(user.getAvatar())
                .build();
    }

    private String parseDeviceType(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            return "iOS";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac")) {
            return "Mac";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "Other";
    }

    // ----- 限流方法 -----

    private boolean isIpRateLimited(String ip) {
        String count = stringRedisTemplate.opsForValue().get(LOGIN_IP_COUNT + ip);
        return count != null && Integer.parseInt(count) >= LOGIN_IP_THRESHOLD;
    }

    private void incrementIpCount(String ip) {
        String key = LOGIN_IP_COUNT + ip;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, LOGIN_IP_COUNT_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private int getLoginFailCount(String phone) {
        String count = stringRedisTemplate.opsForValue().get(LOGIN_FAIL_COUNT + phone);
        return count == null ? 0 : Integer.parseInt(count);
    }

    private void incrementLoginFail(String phone) {
        String key = LOGIN_FAIL_COUNT + phone;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, LOGIN_FAIL_COUNT_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }
}
