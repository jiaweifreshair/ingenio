package com.ingenio.backend.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.common.security.AdminServiceJwtVerifier;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Admin API 服务间 JWT 认证 Filter。
 * 独立于 Sa-Token 的 FilterChain，拥有更高优先级，专门处理 /api/admin/** 路径。
 * 如果认证失败，直接中断请求并返回 401。
 */
@Slf4j
public class AdminServiceJwtFilter implements Filter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminServiceJwtVerifier verifier;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数（通过Spring注入依赖）。
     *
     * 说明：
     * - 不在Filter里抛出NotLoginException：Sa-Token的Servlet Filter可能会捕获异常并统一渲染为HTTP 200，
     *   这会导致“认证失败但状态码仍是200”的问题复现。
     */
    public AdminServiceJwtFilter(AdminServiceJwtVerifier verifier, ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = getPathWithinApplication(httpRequest);
        
        // 仅处理 Admin API 路径
        // - 当 server.servlet.context-path=/api 时，外部路径 /api/admin/** 在应用内表现为 /admin/**
        // - 当 context-path=/ 时，外部路径 /api/admin/** 在应用内表现为 /api/admin/**
        if (!isAdminPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // CORS预检请求直接放行，避免浏览器调用被拦截
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        log.debug("AdminServiceJwtFilter 拦截到请求: {}", path);

        String authorizationHeader = httpRequest.getHeader(AUTH_HEADER);
        String token = null;
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX)) {
            token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        }

        AdminServiceJwtVerifier.VerificationResult result = verifier.verify(token);
        if (!result.valid()) {
            log.warn("Admin API认证失败: {} - {}", result.message(), path);
            writeUnauthorized(httpResponse, result.message());
            return;
        }

        log.info("Admin API 服务间Token验证通过: {}", path);
        chain.doFilter(request, response);
    }

    private boolean isAdminPath(String path) {
        return path != null && (path.equals("/admin") || path.startsWith("/admin/")
            || path.equals("/api/admin") || path.startsWith("/api/admin/"));
    }

    private String getPathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!StringUtils.hasText(contextPath)) {
            return requestUri;
        }
        if (requestUri != null && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        // 以“直接写响应并flush”的方式终止请求，避免异常被上游Filter捕获后改写为200
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("WWW-Authenticate", "Bearer");

        Result<Object> body = Result.error("401", StringUtils.hasText(message) ? message : "未授权");
        objectMapper.writeValue(response.getWriter(), body);
        response.flushBuffer();
    }
}
