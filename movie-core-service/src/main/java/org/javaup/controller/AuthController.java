package org.javaup.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.*;
import org.javaup.service.AuthService;
import org.javaup.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证控制器（纯净版）
 *
 * 设计原则：
 * 1. Controller 方法签名不涉及 HttpServletRequest / HttpServletResponse
 *    （Service 内部通过 RequestContextUtils 获取请求上下文）
 * 2. 受保护接口通过 UserHolder.getUser().getId() 获取当前登录用户
 *    （LoginInterceptor 已完成 JWT 校验 + ThreadLocal 存储）
 * 3. 异常处理统一在 movie-common 的 WebExceptionAdvice 中完成
 *    （Controller 不再声明 @ExceptionHandler）
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ==================== POST /api/auth/register 注册（公开） ====================

    @PostMapping("/register")
    public Result<UserInfoVO> register(@Valid @RequestBody RegisterFormDTO dto) {
        log.info("收到注册请求 phone={}", dto.getPhone());
        return Result.ok(authService.register(dto));
    }

    // ==================== POST /api/auth/login 登录（公开） ====================

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequestDTO dto) {
        log.info("收到登录请求 phone={}", dto.getPhone());
        return Result.ok(authService.login(dto));
    }

    // ==================== GET /api/auth/captcha 图形验证码（公开） ====================

    @GetMapping("/captcha")
    public Result<CaptchaResponseDTO> captcha(@RequestParam("phone") String phone) {
        return Result.ok(authService.getCaptcha(phone));
    }

    // ==================== POST /api/auth/refresh 刷新令牌（公开，但需要 Refresh Token Cookie） ====================

    @PostMapping("/refresh")
    public Result<TokenResponse> refresh() {
        log.info("收到刷新令牌请求");
        return Result.ok(authService.refresh());
    }

    // ==================== POST /api/auth/logout 登出（公开，但需要 Refresh Token Cookie） ====================

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.ok();
    }

    // ==================== 以下接口均需要 Access Token（LoginInterceptor 校验） ====================

    /**
     * GET /api/auth/me  当前登录用户信息
     */
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(authService.me(userId));
    }

    /**
     * GET /api/auth/sessions  活跃会话列表
     */
    @GetMapping("/sessions")
    public Result<List<SessionVO>> sessions() {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(authService.sessions(userId));
    }

    /**
     * DELETE /api/auth/sessions/{sessionId}  强制下线某个会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> revokeSession(@PathVariable("sessionId") String sessionId) {
        Long userId = UserHolder.getUser().getId();
        authService.revokeSession(userId, sessionId);
        return Result.ok();
    }

    /**
     * POST /api/auth/change-password  已登录用户修改密码
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDTO dto) {
        Long userId = UserHolder.getUser().getId();
        authService.changePassword(userId, dto);
        return Result.ok();
    }

    /**
     * PUT /api/auth/profile  修改用户资料（昵称、头像）
     */
    @PutMapping("/profile")
    public Result<UserInfoVO> updateProfile(@Valid @RequestBody UpdateProfileDTO dto) {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(authService.updateProfile(userId, dto));
    }

    // ==================== 以下接口需要单独白名单（LoginInterceptor excludePathPatterns） ====================

    /**
     * POST /api/auth/forgot-password  发起忘记密码（公开）
     */
    @PostMapping("/forgot-password")
    public Result<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO dto) {
        log.info("收到忘记密码请求 phone={}", dto.getPhone());
        return Result.ok(authService.forgotPassword(dto));
    }

    /**
     * POST /api/auth/reset-password  提交重置密码（公开）
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO dto) {
        authService.resetPassword(dto);
        return Result.ok();
    }
}
