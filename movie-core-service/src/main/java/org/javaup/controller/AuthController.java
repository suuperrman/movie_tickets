package org.javaup.controller;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.*;
import org.javaup.exception.AuthBusinessException;
import org.javaup.service.AuthService;
import org.javaup.utils.JwtUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证控制器
 * RESTful API 路径：/api/auth/*
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;

    // ==================== POST /api/auth/register 注册 ====================

    @PostMapping("/register")
    public ApiResponse<UserInfoVO> register(@Valid @RequestBody RegisterFormDTO dto) {
        log.info("收到注册请求 phone={}", dto.getPhone());
        return ApiResponse.ok(authService.register(dto));
    }

    // ==================== POST /api/auth/login 登录 ====================

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequestDTO dto,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        log.info("收到登录请求 phone={}", dto.getPhone());
        return ApiResponse.ok(authService.login(dto, request, response));
    }

    // ==================== GET /api/auth/captcha 图形验证码 ====================

    @GetMapping("/captcha")
    public ApiResponse<CaptchaResponseDTO> captcha(@RequestParam String phone) {
        return ApiResponse.ok(authService.getCaptcha(phone));
    }

    // ==================== POST /api/auth/refresh 刷新令牌 ====================

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        log.info("收到刷新令牌请求");
        return ApiResponse.ok(authService.refresh(request, response));
    }

    // ==================== POST /api/auth/logout 登出 ====================

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ApiResponse.ok();
    }

    // ==================== GET /api/auth/me 当前用户 ====================

    @GetMapping("/me")
    public ApiResponse<UserInfoVO> me(HttpServletRequest request) {
        return ApiResponse.ok(authService.me(getUserIdFromAccessToken(request)));
    }

    // ==================== GET /api/auth/sessions 活跃会话列表 ====================

    @GetMapping("/sessions")
    public ApiResponse<List<SessionVO>> sessions(HttpServletRequest request) {
        return ApiResponse.ok(authService.sessions(getUserIdFromAccessToken(request), request));
    }

    // ==================== DELETE /api/auth/sessions/{sessionId} 强制下线 ====================

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> revokeSession(@PathVariable String sessionId, HttpServletRequest request) {
        authService.revokeSession(getUserIdFromAccessToken(request), sessionId);
        return ApiResponse.ok();
    }

    // ==================== POST /api/auth/forgot-password 忘记密码 ====================

    @PostMapping("/forgot-password")
    public ApiResponse<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO dto) {
        log.info("收到忘记密码请求 phone={}", dto.getPhone());
        return ApiResponse.ok(authService.forgotPassword(dto));
    }

    // ==================== POST /api/auth/reset-password 重置密码 ====================

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO dto) {
        authService.resetPassword(dto);
        return ApiResponse.ok();
    }

    // ==================== POST /api/auth/change-password 修改密码 ====================

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDTO dto,
                                            HttpServletRequest request) {
        authService.changePassword(getUserIdFromAccessToken(request), dto);
        return ApiResponse.ok();
    }

    // ==================== 异常处理 ====================

    @ExceptionHandler(AuthBusinessException.class)
    public ApiResponse<Void> handleAuthException(AuthBusinessException e) {
        log.warn("认证业务异常 code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ApiResponse.fail(e.getErrorCode());
    }

    /**
     * 从 Authorization Header 的 Bearer Token 中解析用户 ID
     */
    private Long getUserIdFromAccessToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AuthBusinessException(BaseErrorCode.TOKEN_INVALID);
        }
        String token = authHeader.substring(7);
        Claims claims = jwtUtils.parseToken(token);
        if (claims == null) {
            throw new AuthBusinessException(BaseErrorCode.TOKEN_EXPIRED);
        }
        if (!jwtUtils.isAccessToken(claims)) {
            throw new AuthBusinessException(BaseErrorCode.TOKEN_INVALID);
        }
        Long userId = jwtUtils.getUserId(claims);
        if (userId == null) {
            throw new AuthBusinessException(BaseErrorCode.TOKEN_INVALID);
        }
        return userId;
    }
}
