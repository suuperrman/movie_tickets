package org.javaup.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.javaup.dto.*;

import java.util.List;

/**
 * 认证服务接口
 */
public interface AuthService {

    /** 注册新用户 */
    UserInfoVO register(RegisterFormDTO dto);

    /** 用户登录 */
    TokenResponse login(LoginRequestDTO dto, HttpServletRequest request, HttpServletResponse response);

    /** 获取图形验证码 */
    CaptchaResponseDTO getCaptcha(String phone);

    /** 刷新 Access Token（Refresh Token Rotation） */
    TokenResponse refresh(HttpServletRequest request, HttpServletResponse response);

    /** 登出（吊销 Refresh Token） */
    void logout(HttpServletRequest request, HttpServletResponse response);

    /** 获取当前登录用户信息 */
    UserInfoVO me(Long userId);

    /** 发起忘记密码 */
    String forgotPassword(ForgotPasswordRequestDTO dto);

    /** 重置密码 */
    void resetPassword(ResetPasswordRequestDTO dto);

    /** 已登录用户修改密码 */
    void changePassword(Long userId, ChangePasswordRequestDTO dto);

    /** 获取当前用户的活跃会话列表 */
    List<SessionVO> sessions(Long userId, HttpServletRequest request);

    /** 强制下线某个会话 */
    void revokeSession(Long userId, String sessionId);
}
