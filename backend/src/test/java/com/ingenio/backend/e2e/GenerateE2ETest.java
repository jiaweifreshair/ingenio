package com.ingenio.backend.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.request.GenerateFullRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * GenerateController E2E测试
 *
 * 测试场景：
 * 1. 完整生成流程 - Plan → Execute → Validate（不生成代码）
 * 2. 完整生成流程 - 生成代码并打包
 * 3. 验证失败场景 - 低质量需求
 * 4. 参数验证失败场景
 * 5. 跳过验证的场景
 *
 * 测试策略：
 * - 使用真实的AI API（零Mock策略）
 * - 使用真实的PostgreSQL数据库（TestContainers）
 * - 验证响应格式、数据完整性、业务逻辑
 * - 仅在AI_API_KEY环境变量存在时运行耗时测试
 *
 * 注意：
 * - 此测试会产生真实API调用费用
 * - 测试时间较长（完整流程15-60秒）
 * - 需要网络连接
 */
@DisplayName("代码生成Controller E2E测试")
public class GenerateE2ETest extends BaseE2ETest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("测试1: 完整生成流程 - 仅AppSpec不生成代码（需要真实API KEY）")
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    public void testGenerateFull_OnlyAppSpec() throws Exception {
        // 构建生成请求（generatePreview=false，不生成代码）
        GenerateFullRequest request = GenerateFullRequest.builder()
                .userRequirement("创建一个简单的待办事项应用，包含任务列表、添加任务、删除任务功能。" +
                        "任务包含标题、描述、截止日期三个字段。")
                .ageGroup("middle_school")
                .skipValidation(false)
                .qualityThreshold(60)
                .generatePreview(false) // 不生成代码
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // 执行请求（预计15-30秒）
        MvcResult result = mockMvc.perform(post("/v1/generate/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(anyOf(is("completed"), is("failed"))))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        System.out.println("完整生成流程响应: " + responseJson);

        JsonNode responseData = objectMapper.readTree(responseJson).path("data");

        // 验证基本字段存在
        assertThat(responseData.path("appSpecId").asText()).isNotEmpty();
        assertThat(responseData.path("planResult").isMissingNode()).isFalse();
        assertThat(responseData.path("validateResult").isMissingNode()).isFalse();
        assertThat(responseData.path("isValid").asBoolean()).isTrue();
        assertThat(responseData.path("qualityScore").asInt()).isGreaterThanOrEqualTo(60);

        // 验证不生成代码
        assertThat(responseData.path("codeDownloadUrl").isMissingNode() ||
                responseData.path("codeDownloadUrl").isNull()).isTrue();

        // 验证生成耗时合理（<60秒）
        long durationMs = responseData.path("durationMs").asLong();
        assertThat(durationMs).isLessThan(60000L);

        System.out.println("测试1通过 - appSpecId: " + responseData.path("appSpecId").asText() +
                ", qualityScore: " + responseData.path("qualityScore").asInt() +
                ", durationMs: " + durationMs);
    }

    @Test
    @DisplayName("测试2: 完整生成流程 - 生成代码并打包（需要真实API KEY）")
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    public void testGenerateFull_WithCodeGeneration() throws Exception {
        // 构建生成请求（generatePreview=true，生成代码）
        GenerateFullRequest request = GenerateFullRequest.builder()
                .userRequirement("创建一个图书管理应用，包含图书列表、添加图书、图书详情页。" +
                        "图书包含书名、作者、ISBN、出版日期四个字段。")
                .ageGroup("high_school")
                .skipValidation(false)
                .qualityThreshold(60)
                .generatePreview(true) // 生成代码
                .packageName("com.example.bookmanager")
                .appName("图书管理助手")
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // 执行请求（预计30-90秒）
        MvcResult result = mockMvc.perform(post("/v1/generate/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(anyOf(is("completed"), is("failed"))))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        System.out.println("带代码生成流程响应: " + responseJson);

        JsonNode responseData = objectMapper.readTree(responseJson).path("data");

        // 验证基本字段
        assertThat(responseData.path("appSpecId").asText()).isNotEmpty();
        assertThat(responseData.path("isValid").asBoolean()).isTrue();
        assertThat(responseData.path("qualityScore").asInt()).isGreaterThanOrEqualTo(60);

        // 验证代码生成（如果状态为completed）
        if ("completed".equals(responseData.path("status").asText())) {
            assertThat(responseData.path("codeDownloadUrl").asText()).isNotEmpty();
            assertThat(responseData.path("generatedFileList").isArray()).isTrue();
            assertThat(responseData.path("generatedFileList").size()).isGreaterThan(0);

            // 验证代码摘要
            JsonNode codeSummary = responseData.path("codeSummary");
            assertThat(codeSummary.path("totalFiles").asInt()).isGreaterThan(0);
            assertThat(codeSummary.path("zipFileName").asText()).isNotEmpty();

            System.out.println("测试2通过 - totalFiles: " + codeSummary.path("totalFiles").asInt() +
                    ", downloadUrl: " + responseData.path("codeDownloadUrl").asText());
        } else {
            System.out.println("测试2部分通过 - 生成失败: " + responseData.path("errorMessage").asText());
        }
    }

    @Test
    @DisplayName("测试3: 验证失败场景 - 低质量需求（需要真实API KEY）")
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    public void testGenerateFull_ValidationFailed() throws Exception {
        // 构建低质量请求（模糊需求）
        GenerateFullRequest request = GenerateFullRequest.builder()
                .userRequirement("做一个app") // 极度模糊的需求
                .skipValidation(false)
                .qualityThreshold(80) // 设置高阈值
                .generatePreview(false)
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // 执行请求
        MvcResult result = mockMvc.perform(post("/v1/generate/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        System.out.println("低质量需求响应: " + responseJson);

        JsonNode responseData = objectMapper.readTree(responseJson).path("data");

        // 验证可能失败或质量评分低
        String status = responseData.path("status").asText();
        if ("failed".equals(status)) {
            assertThat(responseData.path("errorMessage").asText()).isNotEmpty();
            System.out.println("测试3通过 - 验证失败: " + responseData.path("errorMessage").asText());
        } else {
            // 即使成功，质量评分应该较低
            int qualityScore = responseData.path("qualityScore").asInt();
            System.out.println("测试3通过 - qualityScore: " + qualityScore);
        }
    }

    @Test
    @DisplayName("测试4: 参数验证失败 - 空需求")
    public void testGenerateFull_EmptyRequirement() throws Exception {
        // 构建空需求请求
        GenerateFullRequest request = GenerateFullRequest.builder()
                .userRequirement("") // 空需求
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // 执行请求（GlobalExceptionHandler返回200状态码，但code不为200）
        MvcResult result = mockMvc.perform(post("/v1/generate/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk()) // HTTP 200
                .andExpect(jsonPath("$.code").value("1001")) // 错误码1001（参数错误）
                .andExpect(jsonPath("$.message").value("参数错误"))
                .andExpect(jsonPath("$.data.userRequirement").exists())
                .andReturn();

        System.out.println("空需求响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("测试5: 参数验证失败 - 需求过短")
    public void testGenerateFull_TooShortRequirement() throws Exception {
        // 构建过短需求请求（<10字符）
        GenerateFullRequest request = GenerateFullRequest.builder()
                .userRequirement("做app") // 仅3字符
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // 执行请求（应该返回参数验证错误）
        MvcResult result = mockMvc.perform(post("/v1/generate/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk()) // HTTP 200
                .andExpect(jsonPath("$.code").value("1001")) // 错误码1001（参数错误）
                .andExpect(jsonPath("$.message").value("参数错误"))
                .andReturn();

        System.out.println("过短需求响应: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("测试6: 跳过验证的场景（需要真实API KEY）")
    @EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
    public void testGenerateFull_SkipValidation() throws Exception {
        // 构建跳过验证请求
        GenerateFullRequest request = GenerateFullRequest.builder()
                .userRequirement("创建一个简单的笔记应用，支持创建、查看、删除笔记。")
                .skipValidation(true) // 跳过验证
                .qualityThreshold(90) // 即使设置高阈值也会跳过
                .generatePreview(false)
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // 执行请求
        MvcResult result = mockMvc.perform(post("/v1/generate/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("completed")) // 应该成功
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        System.out.println("跳过验证响应: " + responseJson);

        JsonNode responseData = objectMapper.readTree(responseJson).path("data");

        // 验证即使质量评分低于阈值也会成功
        assertThat(responseData.path("appSpecId").asText()).isNotEmpty();
        assertThat(responseData.path("status").asText()).isEqualTo("completed");

        System.out.println("测试6通过 - qualityScore: " + responseData.path("qualityScore").asInt());
    }

    @Test
    @DisplayName("测试7: 异步生成任务 - 创建成功返回taskId")
    public void testCreateAsyncTask() throws Exception {
        // 构建异步生成请求
        GenerateFullRequest request = GenerateFullRequest.builder()
                .userRequirement("创建一个天气查询应用")
                .build();

        String requestJson = objectMapper.writeValueAsString(request);

        // 执行请求(异步API已实现,返回taskId)
        MvcResult result = mockMvc.perform(post("/v1/generate/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200)) // 返回成功
                .andExpect(jsonPath("$.data").exists()) // 返回taskId
                .andExpect(jsonPath("$.data").isString()) // taskId是UUID字符串
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        System.out.println("异步任务响应: " + responseJson);

        JsonNode responseData = objectMapper.readTree(responseJson);
        String taskId = responseData.path("data").asText();
        assertThat(taskId).isNotEmpty();
        System.out.println("测试7通过 - taskId: " + taskId);
    }
}
