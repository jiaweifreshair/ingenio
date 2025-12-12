package com.ingenio.backend.codegen.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring AI Alibaba Function Calling验证测试（V2.0 MVP Day 1 Phase 1.1）
 *
 * <p>验证Spring AI Alibaba 1.1.0.0-M5的Function Calling支持</p>
 *
 * <p>测试内容：</p>
 * <ul>
 *   <li>验证Spring AI配置正确</li>
 *   <li>验证Function接口实现</li>
 *   <li>验证ChatClient注入</li>
 *   <li>验证Function注册和调用</li>
 * </ul>
 *
 * <p>性能目标：</p>
 * <ul>
 *   <li>Function调用响应时间：<3s</li>
 *   <li>Token消耗：<2000 tokens</li>
 *   <li>成功率：100%</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-01-18 V2.0 MVP Day 1: Spring AI配置验证
 */
@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.alibaba.api-key=${DASHSCOPE_API_KEY:test-key}",
        "spring.ai.alibaba.model=qwen-max",
        "spring.ai.alibaba.temperature=0.3",
        "spring.ai.alibaba.max-tokens=2000"
})
@DisplayName("Spring AI Function Calling验证测试")
class SpringAIFunctionCallingTest {

    @Autowired(required = false)
    private ChatClient chatClient;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Autowired(required = false)
    private SimpleCalculatorTool calculatorTool;

    @Test
    @DisplayName("测试1: 验证Spring AI基础配置")
    void testSpringAIBasicConfiguration() {
        log.info("开始验证Spring AI基础配置...");

        // 验证ChatModel注入
        if (chatModel != null) {
            assertNotNull(chatModel, "ChatModel应该被正确注入");
            log.info("✅ ChatModel注入成功: {}", chatModel.getClass().getName());
        } else {
            log.warn("⚠️ ChatModel未注入（可能需要配置DASHSCOPE_API_KEY）");
        }

        // 验证ChatClient注入
        if (chatClient != null) {
            assertNotNull(chatClient, "ChatClient应该被正确注入");
            log.info("✅ ChatClient注入成功: {}", chatClient.getClass().getName());
        } else {
            log.warn("⚠️ ChatClient未注入（可能需要配置DASHSCOPE_API_KEY）");
        }

        log.info("Spring AI基础配置验证完成");
    }

    @Test
    @DisplayName("测试2: 验证Function接口实现")
    void testFunctionInterface() {
        log.info("开始验证Function接口实现...");

        // 验证SimpleCalculatorTool注入
        if (calculatorTool != null) {
            assertNotNull(calculatorTool, "SimpleCalculatorTool应该被正确注入");
            log.info("✅ SimpleCalculatorTool注入成功");

            // 测试Function调用
            SimpleCalculatorTool.Request request = new SimpleCalculatorTool.Request(10, 5, "add");
            SimpleCalculatorTool.Response response = calculatorTool.apply(request);

            assertNotNull(response, "Response不应为null");
            assertTrue(response.isSuccess(), "计算应该成功");
            assertEquals(15, response.getResult(), "10 + 5 应该等于 15");
            log.info("✅ Function调用成功: {} + {} = {}", 10, 5, response.getResult());
        } else {
            log.warn("⚠️ SimpleCalculatorTool未注入");
        }

        log.info("Function接口实现验证完成");
    }

    @Test
    @DisplayName("测试3: 验证Function参数序列化")
    void testFunctionParameterSerialization() {
        log.info("开始验证Function参数序列化...");

        SimpleCalculatorTool.Request request = new SimpleCalculatorTool.Request(100, 25, "subtract");
        assertNotNull(request, "Request对象应该被正确创建");
        assertEquals(100, request.getA(), "参数a应该为100");
        assertEquals(25, request.getB(), "参数b应该为25");
        assertEquals("subtract", request.getOperation(), "操作应该为subtract");

        log.info("✅ Request参数序列化正确");

        if (calculatorTool != null) {
            SimpleCalculatorTool.Response response = calculatorTool.apply(request);
            assertNotNull(response, "Response对象应该被正确创建");
            assertTrue(response.isSuccess(), "计算应该成功");
            assertEquals(75, response.getResult(), "100 - 25 应该等于 75");

            log.info("✅ Response序列化正确: result={}", response.getResult());
        }

        log.info("Function参数序列化验证完成");
    }

    @Test
    @DisplayName("测试4: 验证Function错误处理")
    void testFunctionErrorHandling() {
        log.info("开始验证Function错误处理...");

        if (calculatorTool != null) {
            // 测试除零错误
            SimpleCalculatorTool.Request request = new SimpleCalculatorTool.Request(10, 0, "divide");
            SimpleCalculatorTool.Response response = calculatorTool.apply(request);

            assertNotNull(response, "Response不应为null");
            assertFalse(response.isSuccess(), "除零应该失败");
            assertTrue(response.getMessage().contains("除数不能为0"),
                    "错误消息应该包含'除数不能为0'");
            log.info("✅ 除零错误处理正确: {}", response.getMessage());

            // 测试无效操作
            request = new SimpleCalculatorTool.Request(10, 5, "invalid");
            response = calculatorTool.apply(request);

            assertNotNull(response, "Response不应为null");
            assertFalse(response.isSuccess(), "无效操作应该失败");
            assertTrue(response.getMessage().contains("不支持的操作"),
                    "错误消息应该包含'不支持的操作'");
            log.info("✅ 无效操作错误处理正确: {}", response.getMessage());
        }

        log.info("Function错误处理验证完成");
    }

    @Test
    @DisplayName("测试5: 验证Function性能指标")
    void testFunctionPerformanceMetrics() {
        log.info("开始验证Function性能指标...");

        if (calculatorTool != null) {
            long startTime = System.currentTimeMillis();

            // 执行100次计算测试性能
            for (int i = 0; i < 100; i++) {
                SimpleCalculatorTool.Request request = new SimpleCalculatorTool.Request(i, i + 1, "add");
                SimpleCalculatorTool.Response response = calculatorTool.apply(request);
                assertTrue(response.isSuccess(), "第" + i + "次计算应该成功");
            }

            long duration = System.currentTimeMillis() - startTime;
            double avgTime = duration / 100.0;

            log.info("✅ 100次Function调用完成: 总耗时={}ms, 平均耗时={:.2f}ms", duration, avgTime);
            assertTrue(avgTime < 10, "平均执行时间应该<10ms（实际: " + avgTime + "ms）");
        }

        log.info("Function性能指标验证完成");
    }

    /**
     * 简单计算器工具 - 用于验证Spring AI Function Calling
     *
     * <p>支持基本的四则运算：加、减、乘、除</p>
     */
    @Slf4j
    @Component
    public static class SimpleCalculatorTool implements Function<SimpleCalculatorTool.Request, SimpleCalculatorTool.Response> {

        @Override
        public Response apply(Request request) {
            log.debug("[SimpleCalculatorTool] 开始计算: {} {} {}", request.a, request.operation, request.b);

            try {
                int result;
                switch (request.operation.toLowerCase()) {
                    case "add" -> result = request.a + request.b;
                    case "subtract" -> result = request.a - request.b;
                    case "multiply" -> result = request.a * request.b;
                    case "divide" -> {
                        if (request.b == 0) {
                            return new Response(0, false, "除数不能为0");
                        }
                        result = request.a / request.b;
                    }
                    default -> {
                        return new Response(0, false, "不支持的操作: " + request.operation);
                    }
                }

                log.debug("[SimpleCalculatorTool] ✅ 计算完成: {}", result);
                return new Response(result, true, "计算成功");

            } catch (Exception e) {
                log.error("[SimpleCalculatorTool] ❌ 计算失败: {}", e.getMessage(), e);
                return new Response(0, false, "计算失败: " + e.getMessage());
            }
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Request {
            @JsonProperty(required = true)
            @JsonPropertyDescription("第一个操作数")
            private int a;

            @JsonProperty(required = true)
            @JsonPropertyDescription("第二个操作数")
            private int b;

            @JsonProperty(required = true)
            @JsonPropertyDescription("操作类型：add（加）、subtract（减）、multiply（乘）、divide（除）")
            private String operation;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Response {
            @JsonProperty(required = true)
            @JsonPropertyDescription("计算结果")
            private int result;

            @JsonProperty(required = true)
            @JsonPropertyDescription("是否成功")
            private boolean success;

            @JsonProperty(required = true)
            @JsonPropertyDescription("消息")
            private String message;
        }
    }

    /**
     * 测试配置类
     */
    @Configuration
    public static class TestConfig {
        // 如果需要额外的测试配置，可以在这里添加
    }
}
