package org.javaup.config;

import jakarta.annotation.Resource;
import org.javaup.interceptors.AdminInterceptor;
import org.javaup.interceptors.LoginInterceptor;
import org.javaup.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 拦截器配置
 *
 * 执行顺序（order 越小越先执行）：
 * 1. RefreshTokenInterceptor (order=0) - 老方案 Redis Token 刷新拦截（兼容保留）
 * 2. LoginInterceptor (order=1)        - JWT Access Token 校验 + UserHolder ThreadLocal 存储
 *                                       对公开接口（/api/auth/login 等）不拦截
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private LoginInterceptor loginInterceptor;

    @Resource
    private AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // ==================== 1. 老方案 Redis Token 刷新拦截器（兼容保留） ====================
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .order(0);

        // ==================== 2. JWT 登录校验拦截器 ====================
        // 对公开接口不拦截（这些接口不需要 token 也能访问）
        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        // ========= 登录模块公开接口 =========
                        "/auth/register",
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/captcha",
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        // ========= 电影模块公开接口（列表/详情/场次/座位图） =========
                        "/movie/**",
                        // ========= Actuator =========
                        "/actuator/**"
                ).order(1);

        // ==================== 3. 管理员权限拦截器 ====================
        // 只拦截 /admin/**，需要登录后才能进入此拦截器
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/admin/**")
                .order(2);
    }
}
