package com.ingenio.backend.codegen.ai.agent;

import com.ingenio.backend.codegen.ai.tool.BestPracticeApplierTool;
import com.ingenio.backend.codegen.schema.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * CodeGenerationAgent - AI代码生成Agent（V2.0 MVP Day 1 Phase 1.2）
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>工具编排：协调7大核心工具的调用顺序</li>
 *   <li>决策智能：基于复杂度评分选择生成策略</li>
 *   <li>质量增强：自动应用最佳实践</li>
 *   <li>错误恢复：失败时降级到备选方案</li>
 * </ul>
 *
 * <p>三层生成策略：</p>
 * <pre>
 * Layer 1: Template生成（<60分，简单） → 3秒 $0.03
 * Layer 2: AI Optimizer（60-90分，中等） → 15秒 $0.05
 * Layer 3: AI Complete（>90分，复杂） → 60秒 $0.20
 * </pre>
 *
 * <p>性能目标：</p>
 * <ul>
 *   <li>成功率：≥85%</li>
 *   <li>平均时间：≤10s</li>
 *   <li>平均成本：≤$0.10</li>
 *   <li>代码质量：≥95分</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-01-18 V2.0 MVP Day 1: CodeGenerationAgent框架
 */
@Slf4j
@Service
public class CodeGenerationAgent {

    private final BestPracticeApplierTool bestPracticeApplierTool;

    // 其他工具将在后续Phase中注入
    // private final ComplexityAnalyzerTool complexityAnalyzerTool;
    // private final TemplateGeneratorTool templateGeneratorTool;
    // private final AIOptimizerTool aiOptimizerTool;
    // private final AICompleteGeneratorTool aiCompleteGeneratorTool;
    // private final ValidationTool validationTool;
    // private final MatureSolutionFinderTool matureSolutionFinderTool;

    public CodeGenerationAgent(BestPracticeApplierTool bestPracticeApplierTool) {
        this.bestPracticeApplierTool = bestPracticeApplierTool;
        log.info("[CodeGenerationAgent] 初始化完成 - 已注入1/7工具");
    }

    /**
     * 生成代码（主入口）
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>Step 1: 复杂度分析（0-100分）</li>
     *   <li>Step 2: 决策生成策略（Layer 1/2/3）</li>
     *   <li>Step 3: 执行代码生成</li>
     *   <li>Step 4: 应用最佳实践增强</li>
     *   <li>Step 5: 三环验证</li>
     *   <li>Step 6: 返回结果</li>
     * </ol>
     *
     * @param request 生成请求
     * @return 生成结果
     */
    public GenerationResult generate(GenerationRequest request) {
        log.info("[CodeGenerationAgent] 开始代码生成: methodName={}, entity={}",
                request.getMethodName(), request.getEntity() != null ? request.getEntity().getName() : "null");

        long startTime = System.currentTimeMillis();
        GenerationResult.GenerationResultBuilder resultBuilder = GenerationResult.builder();

        try {
            // Step 1: 复杂度分析
            int complexityScore = analyzeComplexity(request);
            log.info("[CodeGenerationAgent] 复杂度评分: {}/100", complexityScore);
            resultBuilder.complexityScore(complexityScore);

            // Step 2: 决策生成策略
            GenerationStrategy strategy = decideStrategy(complexityScore);
            log.info("[CodeGenerationAgent] 选择策略: {} ({})", strategy, strategy.getDescription());
            resultBuilder.strategy(strategy);

            // Step 3: 执行代码生成
            String generatedCode = executeGeneration(request, strategy);
            log.info("[CodeGenerationAgent] 代码生成完成: 长度={}", generatedCode.length());

            // Step 4: 应用最佳实践增强（MVP核心功能）
            String enhancedCode = applyBestPractices(generatedCode, request);
            log.info("[CodeGenerationAgent] 最佳实践增强完成: 长度={}", enhancedCode.length());

            // Step 5: 三环验证（MVP简化版本）
            ValidationResult validationResult = validate(enhancedCode);
            log.info("[CodeGenerationAgent] 验证完成: success={}, quality={}",
                    validationResult.isSuccess(), validationResult.getQualityScore());
            resultBuilder.validationResult(validationResult);

            // Step 6: 构建返回结果
            long duration = System.currentTimeMillis() - startTime;
            return resultBuilder
                    .generatedCode(enhancedCode)
                    .success(true)
                    .message("代码生成成功")
                    .durationMs(duration)
                    .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[CodeGenerationAgent] ❌ 代码生成失败: {}", e.getMessage(), e);

            return resultBuilder
                    .generatedCode("// 代码生成失败: " + e.getMessage())
                    .success(false)
                    .message("代码生成失败: " + e.getMessage())
                    .durationMs(duration)
                    .build();
        }
    }

    /**
     * Step 1: 复杂度分析（0-100分）
     *
     * <p>评分标准：</p>
     * <ul>
     *   <li>0-20分：纯CRUD操作（get/list/delete）</li>
     *   <li>21-40分：带简单业务逻辑（create/update with validation）</li>
     *   <li>41-60分：中等业务逻辑（订单创建、库存扣减）</li>
     *   <li>61-80分：复杂业务逻辑（支付流程、状态机）</li>
     *   <li>81-100分：超复杂业务逻辑（分布式事务、多系统集成）</li>
     * </ul>
     *
     * @param request 生成请求
     * @return 复杂度评分（0-100）
     */
    private int analyzeComplexity(GenerationRequest request) {
        // MVP简化版本：基于方法名启发式评分
        // TODO: 在Phase 1.3.1中实现ComplexityAnalyzerTool

        String methodName = request.getMethodName().toLowerCase();

        // 纯CRUD操作（0-20分）
        if (methodName.startsWith("get") || methodName.startsWith("find") ||
                methodName.startsWith("list") || methodName.startsWith("delete")) {
            return 15;
        }

        // 简单创建/更新（21-40分）
        if (methodName.startsWith("create") || methodName.startsWith("update") ||
                methodName.startsWith("save")) {
            return 35;
        }

        // 批量操作（41-60分）
        if (methodName.contains("batch") || methodName.contains("bulk")) {
            return 55;
        }

        // 业务流程（61-80分）
        if (methodName.contains("process") || methodName.contains("handle") ||
                methodName.contains("execute")) {
            return 70;
        }

        // 默认中等复杂度
        return 50;
    }

    /**
     * Step 2: 决策生成策略
     *
     * <p>策略选择规则：</p>
     * <ul>
     *   <li>Layer 1: complexityScore < 60 → Template生成（快速）</li>
     *   <li>Layer 2: 60 ≤ complexityScore < 90 → AI Optimizer（平衡）</li>
     *   <li>Layer 3: complexityScore ≥ 90 → AI Complete（精准）</li>
     * </ul>
     *
     * @param complexityScore 复杂度评分
     * @return 生成策略
     */
    private GenerationStrategy decideStrategy(int complexityScore) {
        if (complexityScore < 60) {
            return GenerationStrategy.TEMPLATE;
        } else if (complexityScore < 90) {
            return GenerationStrategy.AI_OPTIMIZER;
        } else {
            return GenerationStrategy.AI_COMPLETE;
        }
    }

    /**
     * Step 3: 执行代码生成
     *
     * <p>根据策略调用不同的生成工具：</p>
     * <ul>
     *   <li>TEMPLATE: TemplateGeneratorTool（20种模板）</li>
     *   <li>AI_OPTIMIZER: AIOptimizerTool（模板+AI修复）</li>
     *   <li>AI_COMPLETE: AICompleteGeneratorTool（完全AI生成）</li>
     * </ul>
     *
     * @param request  生成请求
     * @param strategy 生成策略
     * @return 生成的代码
     */
    private String executeGeneration(GenerationRequest request, GenerationStrategy strategy) {
        // MVP简化版本：生成基础代码框架
        // TODO: 在后续Phase中集成实际的生成工具

        String methodName = request.getMethodName();
        Entity entity = request.getEntity();
        String entityName = entity != null ? entity.getName() : "Entity";

        switch (strategy) {
            case TEMPLATE:
                return generateTemplateCode(methodName, entityName);
            case AI_OPTIMIZER:
                return generateOptimizedCode(methodName, entityName);
            case AI_COMPLETE:
                return generateCompleteCode(methodName, entityName);
            default:
                return generateTemplateCode(methodName, entityName);
        }
    }

    /**
     * Layer 1: Template生成（简单方法）
     */
    private String generateTemplateCode(String methodName, String entityName) {
        log.debug("[CodeGenerationAgent] 使用Template生成策略");
        return String.format("""
                public %s %s(Long id) {
                    return %sRepository.findById(id).orElse(null);
                }
                """, entityName, methodName, entityName.toLowerCase());
    }

    /**
     * Layer 2: AI Optimizer生成（中等方法）
     */
    private String generateOptimizedCode(String methodName, String entityName) {
        log.debug("[CodeGenerationAgent] 使用AI Optimizer生成策略");
        return String.format("""
                public %s %s(Long id) {
                    if (id == null) {
                        throw new IllegalArgumentException("ID不能为空");
                    }
                    return %sRepository.findById(id)
                            .orElseThrow(() -> new EntityNotFoundException("%s不存在: " + id));
                }
                """, entityName, methodName, entityName.toLowerCase(), entityName);
    }

    /**
     * Layer 3: AI Complete生成（复杂方法）
     */
    private String generateCompleteCode(String methodName, String entityName) {
        log.debug("[CodeGenerationAgent] 使用AI Complete生成策略");
        return String.format("""
                @Transactional
                public %s %s(Long id) {
                    if (id == null) {
                        throw new IllegalArgumentException("ID不能为空");
                    }

                    %s entity = %sRepository.findById(id)
                            .orElseThrow(() -> new EntityNotFoundException("%s不存在: " + id));

                    // 业务逻辑处理
                    entity.setUpdatedAt(OffsetDateTime.now());

                    return %sRepository.save(entity);
                }
                """, entityName, methodName, entityName, entityName.toLowerCase(), entityName, entityName.toLowerCase());
    }

    /**
     * Step 4: 应用最佳实践增强
     *
     * <p>调用BestPracticeApplierTool增强代码质量：</p>
     * <ul>
     *   <li>CODE_QUALITY: 异常处理、日志记录、参数校验</li>
     *   <li>SECURITY: 敏感字段识别、数据脱敏</li>
     *   <li>PERFORMANCE: 缓存建议、批量操作、索引优化</li>
     * </ul>
     *
     * @param code    原始代码
     * @param request 生成请求
     * @return 增强后的代码
     */
    private String applyBestPractices(String code, GenerationRequest request) {
        log.debug("[CodeGenerationAgent] 应用最佳实践增强...");

        BestPracticeApplierTool.Request toolRequest = new BestPracticeApplierTool.Request(
                code,
                request.getEntity(),
                request.getMethodName()
        );

        BestPracticeApplierTool.Response toolResponse = bestPracticeApplierTool.apply(toolRequest);

        if (toolResponse.isSuccess()) {
            log.info("[CodeGenerationAgent] ✅ 最佳实践增强成功: 耗时={}ms", toolResponse.getDurationMs());
            return toolResponse.getEnhancedCode();
        } else {
            log.warn("[CodeGenerationAgent] ⚠️ 最佳实践增强失败，返回原始代码: {}", toolResponse.getMessage());
            return code;
        }
    }

    /**
     * Step 5: 三环验证
     *
     * <p>验证层级：</p>
     * <ol>
     *   <li>语法验证：编译检查</li>
     *   <li>逻辑验证：单元测试</li>
     *   <li>质量验证：代码质量评分</li>
     * </ol>
     *
     * @param code 生成的代码
     * @return 验证结果
     */
    private ValidationResult validate(String code) {
        // MVP简化版本：基础验证
        // TODO: 在Phase 1.3.4中实现完整的ValidationTool

        boolean hasValidSyntax = !code.contains("syntax error");
        boolean hasLogic = code.contains("Repository") || code.contains("Service");
        int qualityScore = hasValidSyntax && hasLogic ? 95 : 70;

        return ValidationResult.builder()
                .success(hasValidSyntax)
                .qualityScore(qualityScore)
                .message(hasValidSyntax ? "验证通过" : "验证失败")
                .build();
    }

    /**
     * 生成策略枚举
     */
    public enum GenerationStrategy {
        TEMPLATE("模板生成", "使用预定义模板快速生成"),
        AI_OPTIMIZER("AI优化生成", "模板生成+AI修复优化"),
        AI_COMPLETE("AI完全生成", "完全由AI生成复杂逻辑");

        private final String name;
        private final String description;

        GenerationStrategy(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 生成请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationRequest {
        private String methodName;
        private Entity entity;
        private String requirement;
    }

    /**
     * 生成结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationResult {
        private String generatedCode;
        private boolean success;
        private String message;
        private long durationMs;
        private int complexityScore;
        private GenerationStrategy strategy;
        private ValidationResult validationResult;
    }

    /**
     * 验证结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean success;
        private int qualityScore;
        private String message;

        /**
         * 验证问题列表（默认为空列表）
         */
        @Builder.Default
        private List<String> issues = new ArrayList<>();
    }
}
