package org.javaup.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.utils.UserHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器
 *
 * 拦截 /admin/** 路径，校验当前登录用户是否为管理员（role=1）。
 * 必须在 LoginInterceptor 之后执行（order=2），因为依赖 UserHolder 中的用户信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Integer role = UserHolder.getUser().getRole();
        if (role == null || role != 1) {
            log.warn("非管理员用户访问管理接口, URI={}, userId={}, role={}",
                    request.getRequestURI(), UserHolder.getUser().getId(), role);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            Result<Void> result = Result.fail("没有权限访问管理后台");
            response.getWriter().write(objectMapper.writeValueAsString(result));
            return false;
        }
        return true;
    }
}
