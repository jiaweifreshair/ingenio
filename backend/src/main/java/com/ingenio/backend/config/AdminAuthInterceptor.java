package com.ingenio.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Admin API 认证拦截器 (标准 Spring Interceptor)
 *
 * 替代 SaInterceptor，用于对 /api/admin/** 路径进行服务间认证。
 * 使用 preHandle 方法，如果认证失败，返回 false 以中止请求链。
 */
@Slf4j
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 模拟验证逻辑，实际应替换为JWT库的验证
        String token = request.getHeader("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("Admin API认证失败: 缺少服务间认证Token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401, \"message\":\"Missing Service Token\"}");
            return false;
        }

        String serviceToken = token.substring(7);
        // TODO: 使用JWT库（如java-jwt）和公钥验证token
        if (!"valid-service-token".equals(serviceToken)) {
            log.warn("Admin API认证失败: 无效的服务间Token");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401, \"message\":\"Invalid Service Token\"}");
            return false;
        }

        log.info("Admin API Service Token 验证通过");
        return true; // 验证通过，继续执行后续的处理器
    }
}
