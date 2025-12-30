package com.ingenio.backend.controller.admin;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.filter.AdminServiceJwtFilter;
import com.ingenio.backend.common.security.AdminServiceJwtVerifier;
import com.ingenio.backend.config.AdminServiceJwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminStatusController + AdminServiceJwtFilter 组合测试（不启动完整Spring容器）。
 *
 * 目标：
 * - /api/admin/** 走独立的服务JWT鉴权；
 * - 未携带/无效Token必须返回HTTP 401；
 * - 有效Token可访问并返回HTTP 200。
 *
 * 说明：
 * - 该测试使用 MockMvc standaloneSetup，避免加载主应用（含 @MapperScan）导致的数据库依赖；
 * - 通过 contextPath=/api 来模拟 server.servlet.context-path=/api 的真实部署路径映射。
 */
class AdminStatusControllerAuthStandaloneTest {

    private static final String SECRET = "integration-test-admin-service-jwt-secret";
    private static final String ISSUER = "ingenio-internal";
    private static final String AUDIENCE = "ingenio-admin-api";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminServiceJwtProperties properties = new AdminServiceJwtProperties();
        properties.setEnabled(true);
        properties.setSecretKey(SECRET);
        properties.setIssuer(ISSUER);
        properties.setAudience(AUDIENCE);
        properties.setClockSkewSeconds(0);

        AdminServiceJwtVerifier verifier = new AdminServiceJwtVerifier(properties);
        AdminServiceJwtFilter filter = new AdminServiceJwtFilter(verifier, new ObjectMapper());

        mockMvc = MockMvcBuilders
            .standaloneSetup(new AdminStatusController())
            .addFilters(filter)
            .build();
    }

    @Test
    void shouldReturn401WhenMissingToken() throws Exception {
        mockMvc.perform(get("/api/admin/status").contextPath("/api"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void shouldReturn401WhenInvalidToken() throws Exception {
        mockMvc.perform(get("/api/admin/status")
                .contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void shouldReturn200WhenValidToken() throws Exception {
        String token = createValidToken();
        mockMvc.perform(get("/api/admin/status")
                .contextPath("/api")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }

    private static String createValidToken() {
        Date expiresAt = new Date(System.currentTimeMillis() + 60_000);
        return JWT.create()
            .setIssuer(ISSUER)
            .setAudience(AUDIENCE)
            .setExpiresAt(expiresAt)
            .setSigner(JWTSignerUtil.hs256(SECRET.getBytes(StandardCharsets.UTF_8)))
            .sign();
    }
}
