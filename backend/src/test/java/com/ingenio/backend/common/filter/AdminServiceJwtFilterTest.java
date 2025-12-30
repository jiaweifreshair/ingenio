package com.ingenio.backend.common.filter;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.security.AdminServiceJwtVerifier;
import com.ingenio.backend.config.AdminServiceJwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdminServiceJwtFilter 单元测试：
 * - 未携带/无效服务JWT必须返回401；
 * - 有效服务JWT可放行；
 * - 非 /admin/** 或 /api/admin/** 路径不受影响。
 */
class AdminServiceJwtFilterTest {

    private static final String SECRET = "unit-test-admin-service-jwt-secret";
    private static final String ISSUER = "ingenio-internal";
    private static final String AUDIENCE = "ingenio-admin-api";

    private AdminServiceJwtFilter filter;

    @BeforeEach
    void setUp() {
        AdminServiceJwtProperties properties = new AdminServiceJwtProperties();
        properties.setEnabled(true);
        properties.setSecretKey(SECRET);
        properties.setIssuer(ISSUER);
        properties.setAudience(AUDIENCE);
        properties.setClockSkewSeconds(0);

        AdminServiceJwtVerifier verifier = new AdminServiceJwtVerifier(properties);
        filter = new AdminServiceJwtFilter(verifier, new ObjectMapper());
    }

    @Test
    void shouldPassThroughForNonAdminPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/auth/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FlagFilterChain chain = new FlagFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called, "非Admin路径应放行");
        assertEquals(200, response.getStatus(), "非Admin路径不应被改写状态码");
    }

    @Test
    void shouldPassThroughForOptionsPreflight() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/admin/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FlagFilterChain chain = new FlagFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called, "OPTIONS预检应放行");
    }

    @Test
    void shouldReturn401WhenMissingToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FlagFilterChain chain = new FlagFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called, "缺少服务JWT必须拦截");
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"401\""));
    }

    @Test
    void shouldReturn401WhenInvalidToken() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/status");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FlagFilterChain chain = new FlagFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called, "无效服务JWT必须拦截");
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":\"401\""));
    }

    @Test
    void shouldReturn401WhenContextPathApiAndMissingToken() throws ServletException, IOException {
        // 模拟 server.servlet.context-path=/api 的部署方式
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/status");
        request.setContextPath("/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FlagFilterChain chain = new FlagFilterChain();

        filter.doFilter(request, response, chain);

        assertFalse(chain.called, "缺少服务JWT必须拦截");
        assertEquals(401, response.getStatus());
    }

    @Test
    void shouldAllowWhenValidToken() throws ServletException, IOException {
        String token = createValidToken();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/status");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FlagFilterChain chain = new FlagFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.called, "有效服务JWT应放行");
        assertEquals(200, response.getStatus(), "放行后默认应为200（由下游Controller决定）");
    }

    private String createValidToken() {
        Date expiresAt = new Date(System.currentTimeMillis() + 60_000);
        return JWT.create()
            .setIssuer(ISSUER)
            .setAudience(AUDIENCE)
            .setExpiresAt(expiresAt)
            .setSigner(JWTSignerUtil.hs256(SECRET.getBytes(StandardCharsets.UTF_8)))
            .sign();
    }

    private static class FlagFilterChain implements FilterChain {
        private boolean called = false;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            this.called = true;
        }
    }
}

