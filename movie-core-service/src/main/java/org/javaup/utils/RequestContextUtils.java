package org.javaup.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * RequestContext 工具类（基于 RequestContextHolder）
 *
 * 让 Service 层在不需要 Controller 传 req/res 的情况下，
 * 也能访问当前请求上下文，用于：
 * - 获取客户端 IP
 * - 获取 User-Agent
 * - 操作 Cookie（读取 Refresh Token / 设置 Refresh Token）
 * - 设置 HttpOnly Cookie
 */
public final class RequestContextUtils {

    private RequestContextUtils() {}

    /**
     * 获取当前 HttpServletRequest
     */
    public static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("当前线程中没有 HTTP 请求上下文");
        }
        return attrs.getRequest();
    }

    /**
     * 获取当前 HttpServletResponse
     */
    public static HttpServletResponse getResponse() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("当前线程中没有 HTTP 请求上下文");
        }
        HttpServletResponse response = attrs.getResponse();
        if (response == null) {
            throw new IllegalStateException("当前线程中没有 HTTP 响应上下文");
        }
        return response;
    }

    /**
     * 获取客户端真实 IP（处理 X-Forwarded-For / X-Real-IP 代理头）
     */
    public static String getClientIp() {
        HttpServletRequest request = getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理代理链，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取 User-Agent（用于识别设备类型）
     */
    public static String getUserAgent() {
        return getRequest().getHeader("User-Agent");
    }

    /**
     * 从 Cookie 中获取指定名称的值
     */
    public static String getCookieValue(String name) {
        HttpServletRequest request = getRequest();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    /**
     * 设置 HttpOnly + Secure + SameSite Cookie（手动拼 Set-Cookie Header，支持 SameSite）
     */
    public static void setCookie(String name, String value, int maxAgeSeconds,
                                  String path, boolean httpOnly, boolean secure, String sameSite) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(value);
        sb.append("; Path=").append(path == null ? "/" : path);
        if (httpOnly) sb.append("; HttpOnly");
        if (secure) sb.append("; Secure");
        if (sameSite != null && !sameSite.isEmpty()) {
            sb.append("; SameSite=").append(sameSite);
        }
        sb.append("; Max-Age=").append(maxAgeSeconds);
        getResponse().addHeader("Set-Cookie", sb.toString());
    }

    /**
     * 清除 Cookie（Max-Age=0）
     */
    public static void clearCookie(String name, String path, boolean secure, String sameSite) {
        setCookie(name, "deleted", 0, path, true, secure, sameSite);
    }
}
