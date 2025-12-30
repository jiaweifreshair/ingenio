package com.ingenio.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.filter.AdminServiceJwtFilter;
import com.ingenio.backend.common.security.AdminServiceJwtVerifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 为 AdminServiceJwtFilter 注册 FilterRegistrationBean。
 * 确保 AdminServiceJwtFilter 在所有其他过滤器（包括 SaServletFilter）之前执行。
 */
@Configuration
public class AdminFilterConfig {

    @Bean
    public AdminServiceJwtFilter adminServiceJwtFilter(AdminServiceJwtVerifier verifier, ObjectMapper objectMapper) {
        return new AdminServiceJwtFilter(verifier, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<AdminServiceJwtFilter> adminServiceJwtFilterRegistration(AdminServiceJwtFilter filter) {
        FilterRegistrationBean<AdminServiceJwtFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        // 匹配 Admin API 路径（同时兼容 context-path=/api 与 context-path=/ 两种部署方式）
        registration.addUrlPatterns("/admin", "/admin/*", "/api/admin", "/api/admin/*");
        registration.setName("adminServiceJwtFilter");
        // 设置高优先级，确保在Sa-Token等过滤器之前执行
        // 注意：禁止使用 Ordered.HIGHEST_PRECEDENCE - N（会发生整型下溢，顺序反而靠后）
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }
}
