package org.javaup.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
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
import org.javaup.service.BloomFilterService;
import org.javaup.utils.CaptchaGenerator;
import org.javaup.utils.JwtUtils;
import org.javaup.utils.MaskUtil;
import org.javaup.utils.RequestContextUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final BloomFilterService bloomFilterService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private static final DateTimeFormatter SDF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        user.setNickname(StrUtil.isBlank(dto.getNickname()) ? "user_" + dto.getPhone().substring(7) : dto.getNickname());
        user.setStatus(1);
        user.setRole(0); // 默认普通用户
        user.setCreatedAt(new Date());
        user.setUpdatedAt(new Date());

        sysUserMapper.insert(user);
        // 同步加入布隆过滤器，保证该手机号后续登录不会被误判为「不存在」
        bloomFilterService.addPhone(dto.getPhone());
        log.info("用户注册成功 phone={}", dto.getPhone());
        return buildUserInfoVO(user);
    }

    // ==================== 登录 ====================

    @Override
    public TokenResponse login(LoginRequestDTO dto) {
        String phone = dto.getPhone();
        String clientIp = RequestContextUtils.getClientIp();

        // 1. IP 失败次数检查（只统计登录失败，防暴力破解；成功登录不计入）
        if (isIpRateLimited(clientIp)) {
            log.warn("IP 登录失败次数超限 ip={}", clientIp);
            throw new MovieFrameException(BaseCode.IP_RATE_LIMITED);
        }

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

        // 4. 查询用户（走 Redis 缓存，统一错误提示，防暴力破解）
        SysUser user = findUserByPhone(phone);
        if (user == null) {
            incrementLoginFail(phone);
            incrementIpCount(clientIp);
            log.warn("登录失败-用户不存在 phone={}", phone);
            throw new MovieFrameException(BaseCode.AUTH_FAILED);
        }

        // 5. 状态检查
        if (user.getStatus() != null && user.getStatus() != 1) {
            incrementLoginFail(phone);
            incrementIpCount(clientIp);
            throw new MovieFrameException(BaseCode.ACCOUNT_DISABLED);
        }

        // 6. 密码校验
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            incrementLoginFail(phone);
            incrementIpCount(clientIp);
            log.warn("登录失败-密码错误 phone={}", phone);
            throw new MovieFrameException(BaseCode.AUTH_FAILED);
        }

        // 7. 清除失败计数
        stringRedisTemplate.delete(LOGIN_FAIL_COUNT + phone);

        // 8. 登录幂等：密码校验通过后，若同账号 + 同设备指纹在 TTL 内已登录过，
        //    直接复用已签发的 Token —— 不再查库、不再重复生成 JWT、不再新增 Token Family。
        //    注意：必须在密码校验之后，否则知道手机号即可拿到他人 Token。
        LoginTokenCacheDTO cached = getCachedLoginToken(phone);
        if (cached != null) {
            log.info("登录命中幂等缓存，直接复用 Token userId={}", cached.getUserId());
            return buildTokenResponse(cached);
        }

        // 9. 账号维度限流：只统计「新签发会话」的次数。
        //    幂等复用（第 8 步）不会走到这里，因此正常用户反复登录不受限，
        //    而恶意脚本不断开新会话会被拦截。
        if (isAccountRateLimited(phone)) {
            log.warn("账号新会话签发次数超限 phone={}", phone);
            throw new MovieFrameException(BaseCode.LOGIN_RATE_LIMITED);
        }
        incrementAccountCount(phone);

        // 10. 签发 Token（内部通过 RequestContextUtils 设置 Cookie）
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
        SysUser user = findUserById(userId);
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
            // 登出后登录幂等缓存必须失效，否则登出瞬间重复登录会复用已吊销的 Token
            SysUser user = findUserById(userId);
            if (user != null) {
                evictUserAllCaches(user.getPhone());
            }
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
        evictUserAllCaches(user.getPhone());
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
        evictUserAllCaches(user.getPhone());
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

    // ==================== 修改资料 ====================

    @Override
    public UserInfoVO updateProfile(Long userId, UpdateProfileDTO dto) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
        }

        boolean changed = false;

        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname());
            changed = true;
        }

        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
            changed = true;
        }

        if (changed) {
            user.setUpdatedAt(new Date());
            sysUserMapper.updateById(user);
            log.info("用户资料更新 userId={}", userId);
        }

        return buildUserInfoVO(user);
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

        String accessToken = jwtUtils.generateAccessToken(userId, phone, user.getRole());
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
                .ip(clientIp).loginTime(LocalDateTime.now().format(SDF))
                .build();
        stringRedisTemplate.opsForValue().set(
                TOKEN_SESSION_KEY + refreshTokenId, JSON.toJSONString(session),
                TOKEN_SESSION_TTL_SECONDS, TimeUnit.SECONDS);

        // 超出并发上限时吊销最旧的 Family，避免 Redis 中 Family 无限膨胀
        evictExcessTokenFamilies(userId);

        // 写入登录幂等缓存：TTL 内同账号同设备重复登录直接复用，不查库不重复签发
        cacheLoginToken(phone, LoginTokenCacheDTO.builder()
                .userId(userId)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .refreshTokenId(refreshTokenId)
                .familyId(familyId)
                .user(buildUserInfoVO(user))
                .build());

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

    /**
     * 超出单用户最大并发 Family 数时，吊销最旧的 Family
     *
     * <p>否则同一用户反复登录会在 Redis 中堆积大量 Family（Set 只增不减），
     * 既是内存膨胀，也是安全隐患（旧 Token 长期有效）。
     */
    private void evictExcessTokenFamilies(Long userId) {
        String key = USER_TOKEN_FAMILIES_KEY + userId;
        Set<String> familyIds = stringRedisTemplate.opsForSet().members(key);
        if (familyIds == null || familyIds.size() <= MAX_TOKEN_FAMILY_PER_USER) {
            return;
        }
        // 按创建时间升序，吊销最旧的多余 Family
        List<String> ordered = new ArrayList<>(familyIds);
        ordered.sort(Comparator.comparing(this::familyCreateTime));
        int excess = ordered.size() - MAX_TOKEN_FAMILY_PER_USER;
        for (int i = 0; i < excess; i++) {
            String familyId = ordered.get(i);
            revokeTokenFamily(familyId);
            stringRedisTemplate.opsForSet().remove(key, familyId);
            log.info("超出最大并发会话数，吊销最旧会话 familyId={} userId={}", familyId, userId);
        }
    }

    /**
     * 取 Family 中最早一次登录的时间（用于排序最旧会话）
     */
    private long familyCreateTime(String familyId) {
        Set<String> tokenIds = stringRedisTemplate.opsForSet().members(TOKEN_FAMILY_KEY + familyId);
        if (tokenIds == null || tokenIds.isEmpty()) {
            return 0L;
        }
        long earliest = Long.MAX_VALUE;
        for (String tokenId : tokenIds) {
            String sessionJson = stringRedisTemplate.opsForValue().get(TOKEN_SESSION_KEY + tokenId);
            if (sessionJson == null) {
                continue;
            }
            try {
                SessionVO vo = JSON.parseObject(sessionJson, SessionVO.class);
                if (vo.getLoginTime() != null) {
                    long ts = LocalDateTime.parse(vo.getLoginTime(), SDF)
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    earliest = Math.min(earliest, ts);
                }
            } catch (Exception ignore) {}
        }
        return earliest == Long.MAX_VALUE ? 0L : earliest;
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

    @Override
    public void invalidateAllUserTokens(Long userId) {
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

    /**
     * 按手机号查询用户（布隆过滤器 + Redis 缓存）
     *
     * <p>先走布隆过滤器：手机号一定不存在时直接返回 null，避免大量不存在
     * 的手机号穿透到数据库（防止缓存穿透/攻击者用随机手机号打库）。
     *
     * <p>缓存 TTL 较短（默认 60s），既避免同一用户反复登录时每次都查库，
     * 又能在改密/禁用后较快生效。改密、禁用时主动调用 {@link #evictUserCache} 失效。
     */
    private SysUser findUserByPhone(String phone) {
        // 布隆过滤器只会「误判存在」，不会「漏判存在」，
        // 返回 false 表示一定不存在，可直接拦截，不必再查 Redis / DB。
        if (!bloomFilterService.containsPhone(phone)) {
            return null;
        }

        String cacheKey = USER_CACHE_KEY + phone;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                SysUser user = JSON.parseObject(cached, SysUser.class);
                if (user != null) {
                    return user;
                }
            } catch (Exception ignore) {}
        }

        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, phone));
        if (user != null) {
            cacheUser(user);
        }
        return user;
    }

    /**
     * 失效用户信息缓存（改密码、禁用账号时调用）
     */
    public void evictUserCache(String phone) {
        if (StrUtil.isBlank(phone)) {
            return;
        }
        // 先读出用户信息拿到 id，再删除缓存（顺序不能反，否则索引永远清不掉）
        String cached = stringRedisTemplate.opsForValue().get(USER_CACHE_KEY + phone);
        if (cached != null) {
            try {
                SysUser user = JSON.parseObject(cached, SysUser.class);
                if (user != null && user.getId() != null) {
                    stringRedisTemplate.delete(USER_CACHE_ID_KEY + user.getId());
                }
            } catch (Exception ignore) {}
        }
        stringRedisTemplate.delete(USER_CACHE_KEY + phone);
    }

    /**
     * 按用户 ID 查询用户（带 Redis 缓存，走手机号维度）
     */
    private SysUser findUserById(Long userId) {
        SysUser cached = getCachedUserById(userId);
        if (cached != null) {
            return cached;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            cacheUser(user);
        }
        return user;
    }

    /**
     * 读取用户 ID -> 用户信息 的缓存索引
     */
    private SysUser getCachedUserById(Long userId) {
        String phone = stringRedisTemplate.opsForValue().get(USER_CACHE_ID_KEY + userId);
        if (StrUtil.isBlank(phone)) {
            return null;
        }
        String cached = stringRedisTemplate.opsForValue().get(USER_CACHE_KEY + phone);
        if (cached == null) {
            return null;
        }
        try {
            SysUser user = JSON.parseObject(cached, SysUser.class);
            return (user != null && user.getId().equals(userId)) ? user : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 同时写入「手机号 -> 用户」与「用户 ID -> 手机号」两份缓存
     */
    private void cacheUser(SysUser user) {
        if (user.getPhone() == null || user.getId() == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                USER_CACHE_KEY + user.getPhone(), JSON.toJSONString(user),
                USER_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(
                USER_CACHE_ID_KEY + user.getId(), user.getPhone(),
                USER_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 失效用户所有相关缓存（改密码、禁用账号时调用）
     */
    @Override
    public void evictUserAllCaches(String phone) {
        evictUserCache(phone);
        if (StrUtil.isNotBlank(phone)) {
            stringRedisTemplate.delete(loginTokenCacheKey(phone));
        }
    }

    // ----- 登录幂等（Token 复用） -----

    /**
     * 构造登录幂等缓存 key
     *
     * <p>key 绑定「手机号 + 设备指纹」，因此同一账号在不同设备上登录互不影响，
     * 只有同一台设备的重复登录才会命中缓存。
     */
    private String loginTokenCacheKey(String phone) {
        String deviceHash = DigestUtil.md5Hex(
                RequestContextUtils.getClientIp() + "|" + RequestContextUtils.getUserAgent());
        return LOGIN_TOKEN_CACHE_KEY + phone + ":" + deviceHash;
    }

    /**
     * 读取登录幂等缓存
     */
    private LoginTokenCacheDTO getCachedLoginToken(String phone) {
        String json = stringRedisTemplate.opsForValue().get(loginTokenCacheKey(phone));
        if (json == null) {
            return null;
        }
        try {
            LoginTokenCacheDTO cached = JSON.parseObject(json, LoginTokenCacheDTO.class);
            if (cached == null || cached.getAccessToken() == null) {
                return null;
            }
            // 缓存里的 Refresh Token 已被吊销（登出/改密）时，缓存失效
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(REFRESH_TOKEN_KEY + cached.getRefreshTokenId()))) {
                stringRedisTemplate.delete(loginTokenCacheKey(phone));
                return null;
            }
            return cached;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 写入登录幂等缓存
     */
    private void cacheLoginToken(String phone, LoginTokenCacheDTO dto) {
        stringRedisTemplate.opsForValue().set(
                loginTokenCacheKey(phone), JSON.toJSONString(dto),
                LOGIN_TOKEN_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 由缓存快照构造响应（复用 Token 时重新下发 Refresh Cookie）
     */
    private TokenResponse buildTokenResponse(LoginTokenCacheDTO cached) {
        RequestContextUtils.setCookie(
                authProperties.getRefreshTokenCookieName(),
                cached.getRefreshToken(),
                authProperties.getRefreshTokenExpireDays() * 24 * 60 * 60,
                authProperties.getRefreshTokenCookiePath(),
                true,
                authProperties.isCookieSecure(),
                authProperties.getCookieSameSite()
        );
        return TokenResponse.builder()
                .accessToken(cached.getAccessToken())
                .expiresIn(jwtUtils.getAccessTokenExpireSeconds())
                .tokenType("Bearer")
                .user(cached.getUser())
                .build();
    }

    private UserInfoVO buildUserInfoVO(SysUser user) {
        return UserInfoVO.builder()
                .id(user.getId()).phone(MaskUtil.maskPhone(user.getPhone()))
                .nickname(user.getNickname()).avatar(user.getAvatar())
                .role(user.getRole())
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

    /**
     * 账号维度登录限流：防止同一账号高频反复登录
     *
     * <p>IP 限流挡不住「同一账号从不同 IP 登录」，账号维度补上这一层。
     */
    private boolean isAccountRateLimited(String phone) {
        String count = stringRedisTemplate.opsForValue().get(LOGIN_ACCOUNT_COUNT + phone);
        return count != null && Integer.parseInt(count) >= LOGIN_ACCOUNT_THRESHOLD;
    }

    private void incrementAccountCount(String phone) {
        String key = LOGIN_ACCOUNT_COUNT + phone;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, LOGIN_ACCOUNT_COUNT_TTL_SECONDS, TimeUnit.SECONDS);
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
