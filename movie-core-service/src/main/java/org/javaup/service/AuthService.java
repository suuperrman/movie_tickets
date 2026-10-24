package org.javaup.service;

import org.javaup.dto.*;

import java.util.List;

/**
 * 认证服务接口
 *
 * 所有方法签名均不涉及 HttpServletRequest / HttpServletResponse。
 * Service 内部通过  获取请求上下文，
 * 通过 {@link org.javaup.utils.UserHolder} 获取当前登录用户。
 */
public interface AuthService {

    /** 注册新用户 */
    UserInfoVO register(RegisterFormDTO dto);

    /** 用户登录（内部通过 RequestContextUtils 设置 Refresh Token Cookie） */
    TokenResponse login(LoginRequestDTO dto);

    /** 获取图形验证码 */
    CaptchaResponseDTO getCaptcha(String phone);

    /** 刷新 Access Token（Refresh Token Rotation） */
    TokenResponse refresh();

    /** 登出（吊销 Refresh Token） */
    void logout();

    /** 获取指定用户信息（内部可通过 UserHolder.getUser().getId() 获取当前用户） */
    UserInfoVO me(Long userId);

    /** 发起忘记密码 */
    String forgotPassword(ForgotPasswordRequestDTO dto);

    /** 重置密码 */
    void resetPassword(ResetPasswordRequestDTO dto);

    /** 已登录用户修改密码 */
    void changePassword(Long userId, ChangePasswordRequestDTO dto);

    /** 获取当前用户的活跃会话列表 */
    List<SessionVO> sessions(Long userId);

    /** 强制下线某个会话 */
    void revokeSession(Long userId, String sessionId);

    /** 修改用户资料（昵称、头像） */
    UserInfoVO updateProfile(Long userId, UpdateProfileDTO dto);
}
