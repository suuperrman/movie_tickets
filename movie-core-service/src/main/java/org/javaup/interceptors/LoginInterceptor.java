package org.javaup.interceptors;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.UserDTO;
import org.javaup.utils.JwtUtils;
import org.javaup.utils.UserHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录验证拦截器（统一入口）
 *
 * 职责：
 * 1. 从 Authorization Header 提取 Bearer Token
 * 2. 调用 JwtUtils 解析验证 Access Token
 * 3. 校验成功：构建 UserDTO 存入 UserHolder（ThreadLocal）
 * 4. 校验失败：返回 401 状态码
 * 5. afterCompletion 中清理 ThreadLocal，防止内存泄漏
 *
 * 执行顺序：
 * - 此拦截器 order=0（最先执行）
 * - 对公开接口（/api/auth/login、/api/auth/register 等）不拦截（MvcConfig 中 excludePathPatterns 配置）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取 Authorization Header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("缺少 Authorization Header, URI={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        log.info("获取Authorization：{}",authHeader);
        String token = authHeader.substring(7);

        // 2. 解析并验证 JWT
        Claims claims = jwtUtils.parseToken(token);
        if (claims == null) {
            log.warn("JWT 无效或已过期, URI={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 3. 只接受 Access Token 类型（Refresh Token 不能访问业务接口）
        if (!jwtUtils.isAccessToken(claims)) {
            log.warn("Token 类型错误, 期望 Access Token, URI={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 4. 构建 UserDTO 并存入 ThreadLocal
        Long userId = jwtUtils.getUserId(claims);
        String phone = jwtUtils.getPhone(claims);

        if (userId == null) {
            log.warn("JWT 缺少用户 ID, URI={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        UserDTO userDTO = new UserDTO();
        userDTO.setId(userId);
        userDTO.setNickName(phone != null ? phone : "");

        UserHolder.saveUser(userDTO);
        log.debug("JWT 鉴权通过 userId={}, URI={}", userId, request.getRequestURI());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理 ThreadLocal，防止内存泄漏和线程复用时的数据污染
        UserHolder.removeUser();
    }
}
