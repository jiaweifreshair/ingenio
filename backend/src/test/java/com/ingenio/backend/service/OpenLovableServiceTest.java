package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.request.OpenLovableGenerateRequest;
import com.ingenio.backend.dto.response.OpenLovableGenerateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * OpenLovableService 单元测试
 *
 * 覆盖重点：
 * - streaming=false 时必须阻塞等待生成完成，并校验返回内容包含 <file>（避免“返回空原型/未完成就开始创建沙箱应用”）
 * - 生成失败时应返回 success=false，避免上层把 prototypeUrl 误标记为已生成
 */
@ExtendWith(MockitoExtension.class)
class OpenLovableServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private UIFunctionalValidator uiFunctionalValidator;

    private OpenLovableService openLovableService;

    private static final String BASE_URL = "http://localhost:3001";

    @BeforeEach
    void setUp() {
        openLovableService = new OpenLovableService(restTemplate, objectMapper, uiFunctionalValidator);
        ReflectionTestUtils.setField(openLovableService, "openLovableBaseUrl", BASE_URL);
    }

    @Test
    @DisplayName("streaming=false：当生成返回包含 <file> 时应判定成功并返回可用 previewUrl")
    void shouldReturnSuccessWhenBlockingGenerationProducesFileTags() {
        when(restTemplate.getForEntity(eq(BASE_URL + "/api/health"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "ok")));

        when(restTemplate.postForEntity(eq(BASE_URL + "/api/create-ai-sandbox-v2"), isNull(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "sandboxId", "sb_test",
                        "url", "https://example.e2b.app",
                        "provider", "e2b")));

        String sseBody = "data: {\"type\":\"complete\",\"generatedCode\":\"<file path=\\\"src/App.jsx\\\">OK</file>\"}\n\n";
        when(restTemplate.postForEntity(eq(BASE_URL + "/api/generate-ai-code-stream"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(sseBody));

        OpenLovableGenerateRequest request = OpenLovableGenerateRequest.builder()
                .userRequirement("做一个博客平台，支持Markdown编辑与评论功能")
                .referenceUrls(java.util.List.of("https://example.com"))
                .needsCrawling(true)
                .streaming(false)
                .aiModel(null)
                .timeoutSeconds(5)
                .build();

        OpenLovableGenerateResponse response = openLovableService.generatePrototype(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getPreviewUrl()).isEqualTo("https://example.e2b.app");
        assertThat(response.getSandboxId()).isEqualTo("sb_test");
        assertThat(response.getGenerationLog()).contains("hasFileTag=true");
    }

    @Test
    @DisplayName("streaming=false：当生成未返回 <file> 时应判定失败（避免误标记 prototypeGenerated）")
    void shouldReturnFailureWhenBlockingGenerationReturnsNoFileTags() {
        when(restTemplate.getForEntity(eq(BASE_URL + "/api/health"), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "ok")));

        when(restTemplate.postForEntity(eq(BASE_URL + "/api/create-ai-sandbox-v2"), isNull(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "sandboxId", "sb_test",
                        "url", "https://example.e2b.app",
                        "provider", "e2b")));

        String sseBody = "data: {\"type\":\"complete\",\"generatedCode\":\"\"}\n\n";
        when(restTemplate.postForEntity(eq(BASE_URL + "/api/generate-ai-code-stream"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(sseBody));

        OpenLovableGenerateRequest request = OpenLovableGenerateRequest.builder()
                .userRequirement("做一个博客平台，支持Markdown编辑与评论功能")
                .streaming(false)
                .aiModel(null)
                .timeoutSeconds(5)
                .build();

        OpenLovableGenerateResponse response = openLovableService.generatePrototype(request);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("未返回 <file>");
        // 失败场景仍会返回 sandboxId/url，便于排查（但上层不应据此判定 prototypeGenerated）
        assertThat(response.getSandboxId()).isEqualTo("sb_test");
        assertThat(response.getPreviewUrl()).isEqualTo("https://example.e2b.app");
    }
}

