package com.ingenio.backend.codegen.ai.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.codegen.schema.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * AI代码优化工具
 *
 * <p>封装AI代码优化能力为Spring AI Function Calling工具，提供智能代码优化建议。</p>
 *
 * <h2>核心功能</h2>
 * <ul>
 *     <li>性能优化：数据库查询优化、集合操作优化、缓存策略</li>
 *     <li>可读性优化：变量命名、方法拆分、注释完善</li>
 *     <li>安全优化：SQL注入防护、输入验证、敏感数据处理</li>
 *     <li>最佳实践：Spring Boot注解使用、MyBatis-Plus最佳实践、设计模式应用</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * AIOptimizerTool.Request request = AIOptimizerTool.Request.builder()
 *     .generatedCode(code)
 *     .entity(entity)
 *     .methodName("createUser")
 *     .scope(OptimizationScope.ALL)
 *     .build();
 *
 * AIOptimizerTool.Response response = aiOptimizerTool.apply(request);
 *
 * if (response.hasOptimizations()) {
 *     for (Optimization opt : response.getOptimizations()) {
 *         System.out.println(opt.getCategory() + ": " + opt.getSuggestion());
 *     }
 * }
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-19
 * @see ComplexityAnalyzerTool
 * @see ValidationTool
 */
@Slf4j
@Component
public class AIOptimizerTool implements Function<AIOptimizerTool.Request, AIOptimizerTool.Response> {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param chatClient Spring AI ChatClient，用于调用AI模型
     */
    public AIOptimizerTool(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 优化范围枚举
     */
    public enum OptimizationScope {
        /** 性能优化 */
        PERFORMANCE,
        /** 可读性优化 */
        READABILITY,
        /** 安全优化 */
        SECURITY,
        /** 最佳实践优化 */
        BEST_PRACTICE,
        /** 全部优化（默认） */
        ALL
    }

    /**
     * 优化分类枚举
     */
    public enum OptimizationCategory {
        /** 性能优化 */
        PERFORMANCE("性能优化"),
        /** 可读性优化 */
        READABILITY("可读性优化"),
        /** 安全优化 */
        SECURITY("安全优化"),
        /** 最佳实践 */
        BEST_PRACTICE("最佳实践");

        private final String displayName;

        OptimizationCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 严重性级别枚举
     */
    public enum Severity {
        /** 高：必须修复（安全漏洞、性能瓶颈） */
        HIGH("高", "必须修复"),
        /** 中：建议修复（可读性、最佳实践） */
        MEDIUM("中", "建议修复"),
        /** 低：可选优化（细节优化、代码美化） */
        LOW("低", "可选优化");

        private final String displayName;
        private final String description;

        Severity(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 单个优化建议
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Optimization {
        /** 优化分类 */
        @JsonProperty("category")
        private OptimizationCategory category;

        /** 严重性级别 */
        @JsonProperty("severity")
        private Severity severity;

        /** 位置（行号或方法名） */
        @JsonProperty("location")
        private String location;

        /** 问题描述 */
        @JsonProperty("issue")
        private String issue;

        /** 优化建议 */
        @JsonProperty("suggestion")
        private String suggestion;

        /** 优化后的代码示例 */
        @JsonProperty("example")
        private String example;
    }

    /**
     * AI优化结果（用于解析AI返回的JSON）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIOptimizationResult {
        /** 是否有优化建议 */
        @JsonProperty("hasOptimizations")
        private Boolean hasOptimizations;

        /** 优化建议列表 */
        @JsonProperty("optimizations")
        private List<Optimization> optimizations;

        /** 总体评估和建议 */
        @JsonProperty("summary")
        private String summary;
    }

    /**
     * 优化请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        /** 待优化的代码（必需） */
        @JsonProperty("generatedCode")
        @JsonPropertyDescription("待优化的代码")
        private String generatedCode;

        /** 关联实体（可选，增强上下文） */
        @JsonProperty("entity")
        @JsonPropertyDescription("关联实体，增强上下文理解")
        private Entity entity;

        /** 关联方法名（可选） */
        @JsonProperty("methodName")
        @JsonPropertyDescription("关联方法名")
        private String methodName;

        /** 优化范围（默认ALL） */
        @JsonProperty("scope")
        @JsonPropertyDescription("优化范围：PERFORMANCE/READABILITY/SECURITY/BEST_PRACTICE/ALL")
        @Builder.Default
        private OptimizationScope scope = OptimizationScope.ALL;
    }

    /**
     * 优化响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        /** 是否有优化建议 */
        @JsonProperty("hasOptimizations")
        @JsonPropertyDescription("是否有优化建议")
        private Boolean hasOptimizations;

        /** 优化建议列表 */
        @JsonProperty("optimizations")
        @JsonPropertyDescription("优化建议列表")
        @Builder.Default
        private List<Optimization> optimizations = new ArrayList<>();

        /** 优化点数量 */
        @JsonProperty("optimizationCount")
        @JsonPropertyDescription("优化点数量")
        private Integer optimizationCount;

        /** 优化总结 */
        @JsonProperty("summary")
        @JsonPropertyDescription("优化总结")
        private String summary;

        /** 执行耗时（毫秒） */
        @JsonProperty("durationMs")
        @JsonPropertyDescription("执行耗时（毫秒）")
        private Long durationMs;

        /** 错误信息（如果有） */
        @JsonProperty("errorMessage")
        @JsonPropertyDescription("错误信息")
        private String errorMessage;
    }

    /**
     * 执行AI代码优化分析
     *
     * @param request 优化请求
     * @return 优化响应
     */
    @Override
    public Response apply(Request request) {
        long startTime = System.currentTimeMillis();

        log.info("开始AI代码优化分析: methodName={}, scope={}, codeLength={}",
                request.getMethodName(), request.getScope(), request.getGeneratedCode().length());

        try {
            // 1. 构建结构化Prompt
            String prompt = buildStructuredPrompt(request);

            // 2. 调用Spring AI
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("AI返回内容: {}", content);

            // 3. 解析AI返回的JSON
            AIOptimizationResult aiResult = parseAIResult(content);

            // 4. 构建响应
            long durationMs = System.currentTimeMillis() - startTime;

            Response response = Response.builder()
                    .hasOptimizations(aiResult.getHasOptimizations())
                    .optimizations(aiResult.getOptimizations())
                    .optimizationCount(aiResult.getOptimizations().size())
                    .summary(aiResult.getSummary())
                    .durationMs(durationMs)
                    .build();

            log.info("AI代码优化分析完成: hasOptimizations={}, optimizationCount={}, durationMs={}ms",
                    response.getHasOptimizations(), response.getOptimizationCount(), durationMs);

            return response;

        } catch (Exception e) {
            log.error("AI代码优化分析失败", e);

            long durationMs = System.currentTimeMillis() - startTime;
            return Response.builder()
                    .hasOptimizations(false)
                    .optimizations(new ArrayList<>())
                    .optimizationCount(0)
                    .summary("优化分析失败")
                    .errorMessage(e.getMessage())
                    .durationMs(durationMs)
                    .build();
        }
    }

    /**
     * 构建结构化Prompt
     *
     * @param request 优化请求
     * @return 结构化Prompt
     */
    private String buildStructuredPrompt(Request request) {
        StringBuilder prompt = new StringBuilder();

        // System角色定义
        prompt.append("你是一个资深的Java代码审查专家，擅长Spring Boot、MyBatis-Plus开发，\n");
        prompt.append("精通性能优化、安全加固、代码可读性提升。\n\n");

        // 任务指令
        prompt.append("请分析以下代码，提供结构化的优化建议。\n\n");

        // 代码上下文
        prompt.append("## 代码上下文\n");
        if (request.getEntity() != null) {
            prompt.append("- 实体：").append(request.getEntity().getName()).append("\n");
        }
        if (request.getMethodName() != null) {
            prompt.append("- 方法：").append(request.getMethodName()).append("\n");
        }
        prompt.append("- 优化范围：").append(request.getScope()).append("\n\n");

        // 待分析代码
        prompt.append("## 待分析代码\n```java\n");
        prompt.append(request.getGeneratedCode());
        prompt.append("\n```\n\n");

        // 输出格式要求
        prompt.append("## 输出格式（必须返回valid JSON）\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"hasOptimizations\": true,\n");
        prompt.append("  \"optimizations\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"category\": \"PERFORMANCE|READABILITY|SECURITY|BEST_PRACTICE\",\n");
        prompt.append("      \"severity\": \"HIGH|MEDIUM|LOW\",\n");
        prompt.append("      \"location\": \"行号或方法名\",\n");
        prompt.append("      \"issue\": \"问题描述\",\n");
        prompt.append("      \"suggestion\": \"优化建议\",\n");
        prompt.append("      \"example\": \"优化后的代码示例\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"summary\": \"总体评估和建议\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        // 优化维度说明
        prompt.append("## 优化维度\n");
        prompt.append("1. **性能优化**：数据库查询优化（N+1查询、索引）、集合操作优化（Stream、并行）、缓存策略\n");
        prompt.append("2. **安全加固**：SQL注入防护、输入验证、敏感数据处理\n");
        prompt.append("3. **可读性**：变量命名优化、方法拆分（单一职责）、注释完善\n");
        prompt.append("4. **最佳实践**：Spring Boot注解使用、MyBatis-Plus最佳实践、设计模式应用\n\n");

        // 严重性级别说明
        prompt.append("## 严重性级别\n");
        prompt.append("- **HIGH**：必须修复（安全漏洞、性能瓶颈）\n");
        prompt.append("- **MEDIUM**：建议修复（可读性、最佳实践）\n");
        prompt.append("- **LOW**：可选优化（细节优化、代码美化）\n\n");

        // 注意事项
        prompt.append("## 注意事项\n");
        prompt.append("1. 必须返回valid JSON格式，不要添加任何解释性文字\n");
        prompt.append("2. 如果代码已经很完美，设置hasOptimizations=false，optimizations=[]\n");
        prompt.append("3. 每个优化建议必须包含具体的代码示例\n");
        prompt.append("4. 优化建议要具体、可执行、有说服力\n");

        return prompt.toString();
    }

    /**
     * 解析AI返回的JSON结果
     *
     * @param content AI返回的内容
     * @return AI优化结果
     * @throws JsonProcessingException JSON解析异常
     */
    private AIOptimizationResult parseAIResult(String content) throws JsonProcessingException {
        // 提取JSON内容（AI可能返回带有markdown代码块的内容）
        String jsonContent = extractJsonFromContent(content);

        // 解析JSON
        AIOptimizationResult result = objectMapper.readValue(jsonContent, AIOptimizationResult.class);

        // 验证结果
        if (result.getHasOptimizations() == null) {
            result.setHasOptimizations(false);
        }
        if (result.getOptimizations() == null) {
            result.setOptimizations(new ArrayList<>());
        }
        if (result.getSummary() == null) {
            result.setSummary("无优化建议");
        }

        return result;
    }

    /**
     * 从AI返回内容中提取JSON
     *
     * <p>AI可能返回带有markdown代码块的内容，例如：</p>
     * <pre>
     * ```json
     * {"hasOptimizations": true, ...}
     * ```
     * </pre>
     *
     * @param content AI返回的原始内容
     * @return 提取的JSON字符串
     */
    private String extractJsonFromContent(String content) {
        // 去除前后空白
        String trimmed = content.trim();

        // 如果包含markdown代码块，提取其中的JSON
        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf('\n') + 1;
            int end = trimmed.lastIndexOf("```");
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        } else if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n') + 1;
            int end = trimmed.lastIndexOf("```");
            if (end > start) {
                return trimmed.substring(start, end).trim();
            }
        }

        // 如果不包含代码块，直接返回原内容
        return trimmed;
    }
}
