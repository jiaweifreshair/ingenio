package com.ingenio.backend.codegen.ai.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.ingenio.backend.codegen.ai.generator.BusinessLogicGenerator;
import com.ingenio.backend.codegen.ai.model.BusinessRule;
import com.ingenio.backend.codegen.schema.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * TemplateGeneratorTool - 模板生成工具（V2.0 MVP Day 1 Phase 1.3.2）
 *
 * <p>封装BusinessLogicGenerator为Spring AI Function Calling工具</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>业务逻辑代码生成：基于业务规则列表生成Java代码片段</li>
 *   <li>四种规则类型：VALIDATION（验证）、CALCULATION（计算）、WORKFLOW（流转）、NOTIFICATION（通知）</li>
 *   <li>优先级排序：按priority字段排序，数字越大优先级越高</li>
 *   <li>类型分组执行：VALIDATION → CALCULATION → WORKFLOW → NOTIFICATION顺序</li>
 *   <li>模板渲染：使用FreeMarker模板生成高质量代码</li>
 * </ul>
 *
 * <p>性能指标：</p>
 * <ul>
 *   <li>执行时间：<100ms（即时生成）</li>
 *   <li>Token消耗：0（纯本地模板引擎，无AI调用）</li>
 *   <li>代码质量：生成符合企业规范的Spring Boot代码</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 定义业务规则
 * List<BusinessRule> rules = Arrays.asList(
 *     BusinessRule.builder()
 *         .name("validateAge")
 *         .description("验证用户年龄≥18岁")
 *         .type(VALIDATION)
 *         .entity("User")
 *         .method("register")
 *         .logic("检查age字段，小于18抛出BusinessException")
 *         .priority(10)
 *         .build()
 * );
 *
 * // 调用工具生成代码
 * TemplateGeneratorTool.Request request = new TemplateGeneratorTool.Request(
 *     rules,
 *     userEntity,
 *     "register"
 * );
 * TemplateGeneratorTool.Response response = templateGeneratorTool.apply(request);
 *
 * // 生成的代码示例：
 * // ========== VALIDATION规则（数据验证） ==========
 * // if (user.getAge() < 18) {
 * //     throw new BusinessException(ErrorCode.INVALID_AGE, "用户年龄必须≥18岁");
 * // }
 * }</pre>
 *
 * @author Ingenio Code Generator
 * @since 2025-01-18 V2.0 MVP Day 1 Phase 1.3.2: TemplateGeneratorTool封装
 * @see com.ingenio.backend.codegen.ai.generator.BusinessLogicGenerator 业务逻辑生成器
 * @see com.ingenio.backend.codegen.ai.model.BusinessRule 业务规则定义
 */
@Slf4j
@Component
public class TemplateGeneratorTool implements Function<TemplateGeneratorTool.Request, TemplateGeneratorTool.Response> {

    private final BusinessLogicGenerator businessLogicGenerator;

    public TemplateGeneratorTool(BusinessLogicGenerator businessLogicGenerator) {
        this.businessLogicGenerator = businessLogicGenerator;
    }

    /**
     * 生成业务逻辑代码
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>过滤当前方法的业务规则</li>
     *   <li>按优先级排序规则（priority数字越大优先级越高）</li>
     *   <li>按类型分组规则（VALIDATION/CALCULATION/WORKFLOW/NOTIFICATION）</li>
     *   <li>为每个规则类型生成代码</li>
     *   <li>组合所有代码片段</li>
     * </ol>
     *
     * @param request 包含业务规则列表、实体信息、方法名的请求
     * @return 生成的代码及执行结果
     */
    @Override
    public Response apply(Request request) {
        log.info("[TemplateGeneratorTool] 开始生成业务逻辑代码: method={}, entity={}, 规则数量={}",
                request.methodName, request.entity != null ? request.entity.getName() : "null",
                request.rules != null ? request.rules.size() : 0);

        long startTime = System.currentTimeMillis();

        try {
            // 输入参数验证
            if (request.rules == null || request.rules.isEmpty()) {
                log.warn("[TemplateGeneratorTool] ⚠️ 业务规则列表为空，返回TODO注释");
                return new Response(
                        "// TODO: 暂无业务规则，请根据需求添加",
                        true,
                        "业务规则列表为空，生成TODO注释",
                        System.currentTimeMillis() - startTime
                );
            }

            if (request.entity == null) {
                throw new IllegalArgumentException("Entity不能为null");
            }

            if (request.methodName == null || request.methodName.isEmpty()) {
                throw new IllegalArgumentException("MethodName不能为空");
            }

            // 调用BusinessLogicGenerator生成代码
            String generatedCode = businessLogicGenerator.generateBusinessLogic(
                    request.rules,
                    request.entity,
                    request.methodName
            );

            long duration = System.currentTimeMillis() - startTime;

            log.info("[TemplateGeneratorTool] ✅ 业务逻辑代码生成完成: 耗时={}ms, 代码长度={} 字符",
                    duration, generatedCode != null ? generatedCode.length() : 0);

            return new Response(
                    generatedCode,
                    true,
                    "业务逻辑代码生成成功",
                    duration
            );

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[TemplateGeneratorTool] ❌ 业务逻辑代码生成失败: {}", e.getMessage(), e);

            return new Response(
                    "// TODO: 代码生成失败 - " + e.getMessage(),
                    false,
                    "业务逻辑代码生成失败: " + e.getMessage(),
                    duration
            );
        }
    }

    /**
     * 工具请求参数
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {

        /**
         * 业务规则列表（来自RequirementAnalyzer）
         *
         * <p>每个规则包含：</p>
         * <ul>
         *   <li>name：规则名称（camelCase）</li>
         *   <li>description：规则描述</li>
         *   <li>type：规则类型（VALIDATION/CALCULATION/WORKFLOW/NOTIFICATION）</li>
         *   <li>entity：关联实体名称</li>
         *   <li>method：关联方法名称</li>
         *   <li>logic：业务逻辑描述（自然语言）</li>
         *   <li>priority：优先级（1-10，数字越大优先级越高）</li>
         * </ul>
         */
        @JsonProperty(required = true)
        @JsonPropertyDescription("业务规则列表，每个规则包含name/description/type/entity/method/logic/priority字段")
        private List<BusinessRule> rules;

        /**
         * 关联实体（用于获取字段信息）
         */
        @JsonProperty(required = true)
        @JsonPropertyDescription("关联实体，用于获取字段信息和生成实体相关代码")
        private Entity entity;

        /**
         * 关联方法名称（用于过滤规则）
         *
         * <p>只生成method字段匹配的规则代码</p>
         */
        @JsonProperty(required = true)
        @JsonPropertyDescription("关联方法名称，只生成该方法的业务规则代码")
        private String methodName;
    }

    /**
     * 工具响应结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {

        /**
         * 生成的业务逻辑代码
         *
         * <p>代码结构：</p>
         * <pre>{@code
         * // ========== VALIDATION规则（数据验证） ==========
         * if (user.getAge() < 18) {
         *     throw new BusinessException(ErrorCode.INVALID_AGE, "用户年龄必须≥18岁");
         * }
         *
         * // ========== CALCULATION规则（业务计算） ==========
         * BigDecimal totalPrice = orderItems.stream()
         *     .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
         *     .reduce(BigDecimal.ZERO, BigDecimal::add);
         * order.setTotalPrice(totalPrice);
         *
         * // ========== WORKFLOW规则（状态流转） ==========
         * order.setStatus(OrderStatus.PENDING);
         * order.setStatusHistory(OrderStatus.PENDING + " at " + OffsetDateTime.now());
         *
         * // ========== NOTIFICATION规则（消息通知） ==========
         * emailService.sendEmail(user.getEmail(), "订单创建成功", emailTemplate);
         * }</pre>
         */
        @JsonProperty(required = true)
        @JsonPropertyDescription("生成的Java代码片段，包含VALIDATION/CALCULATION/WORKFLOW/NOTIFICATION所有规则")
        private String generatedCode;

        /**
         * 是否成功
         */
        @JsonProperty(required = true)
        @JsonPropertyDescription("生成是否成功（true=成功，false=失败）")
        private boolean success;

        /**
         * 响应消息
         */
        @JsonProperty(required = true)
        @JsonPropertyDescription("执行结果消息，成功时为'业务逻辑代码生成成功'，失败时包含错误详情")
        private String message;

        /**
         * 执行时长（毫秒）
         */
        @JsonProperty(required = true)
        @JsonPropertyDescription("代码生成耗时（毫秒），目标<100ms")
        private long durationMs;
    }
}
