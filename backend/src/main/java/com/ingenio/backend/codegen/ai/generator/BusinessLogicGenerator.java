package com.ingenio.backend.codegen.ai.generator;

import com.ingenio.backend.codegen.ai.model.BusinessRule;
import com.ingenio.backend.codegen.ai.model.BusinessRule.BusinessRuleType;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.template.TemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务逻辑代码生成器（V2.0 Phase 4.2）
 *
 * <p>负责将AI识别的业务规则（自然语言）转化为可执行的Java代码</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>业务规则代码生成：支持VALIDATION/CALCULATION/WORKFLOW/NOTIFICATION四种规则类型</li>
 *   <li>规则优先级排序：按priority字段排序，数字越大优先级越高</li>
 *   <li>规则类型分组：VALIDATION → CALCULATION → WORKFLOW → NOTIFICATION顺序执行</li>
 *   <li>模板渲染：使用FreeMarker模板生成Java代码片段</li>
 *   <li>代码组合：将多个规则代码片段组合为完整业务逻辑</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 从Phase 4.1获取业务规则列表
 * RequirementAnalysisResult analysisResult = requirementAnalyzer.analyze(userRequirement);
 * List<BusinessRule> rules = analysisResult.getBusinessRules();
 *
 * // 生成业务逻辑代码
 * String businessLogicCode = businessLogicGenerator.generateBusinessLogic(
 *     rules,
 *     entity,
 *     "createOrder"
 * );
 *
 * // 将生成的代码插入到Service方法中
 * String serviceCode = serviceTemplate.replace("// TODO: Business Logic", businessLogicCode);
 * }</pre>
 *
 * <p>输入示例：</p>
 * <pre>{@code
 * List<BusinessRule> rules = Arrays.asList(
 *     BusinessRule.builder()
 *         .name("validateAge")
 *         .description("验证用户年龄≥18岁")
 *         .type(VALIDATION)
 *         .entity("User")
 *         .method("register")
 *         .logic("检查age字段，小于18抛出BusinessException")
 *         .priority(10)
 *         .build(),
 *     BusinessRule.builder()
 *         .name("calculateTotalPrice")
 *         .description("计算订单总价")
 *         .type(CALCULATION)
 *         .entity("Order")
 *         .method("create")
 *         .logic("订单总价 = Σ(商品单价 * 数量)")
 *         .priority(5)
 *         .build()
 * );
 * }</pre>
 *
 * <p>输出示例：</p>
 * <pre>{@code
 * // VALIDATION规则
 * if (user.getAge() < 18) {
 *     throw new BusinessException(ErrorCode.INVALID_AGE, "用户年龄必须≥18岁");
 * }
 *
 * // CALCULATION规则
 * BigDecimal totalPrice = orderItems.stream()
 *     .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
 *     .reduce(BigDecimal.ZERO, BigDecimal::add);
 * order.setTotalPrice(totalPrice);
 * }</pre>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-18 V2.0 Phase 4.2: 业务逻辑代码生成器
 * @see com.ingenio.backend.codegen.ai.service.RequirementAnalyzer Phase 4.1 AI需求理解服务
 * @see com.ingenio.backend.codegen.ai.generator.RulePatternMatcher 规则模式匹配器
 */
@Slf4j
@Service
public class BusinessLogicGenerator {

    /**
     * 模板引擎（Phase 3.1）
     */
    private final TemplateEngine templateEngine;

    /**
     * 规则模式匹配器（Phase 4.2）
     */
    private final RulePatternMatcher rulePatternMatcher;

    @Autowired
    public BusinessLogicGenerator(TemplateEngine templateEngine,
                                   RulePatternMatcher rulePatternMatcher) {
        this.templateEngine = templateEngine;
        this.rulePatternMatcher = rulePatternMatcher;
    }

    /**
     * 生成业务逻辑代码
     *
     * <p>将业务规则列表转换为Java代码片段，按以下顺序执行：</p>
     * <ol>
     *   <li>VALIDATION规则（验证输入数据）</li>
     *   <li>CALCULATION规则（计算业务数据）</li>
     *   <li>WORKFLOW规则（状态流转）</li>
     *   <li>NOTIFICATION规则（发送通知）</li>
     * </ol>
     *
     * @param rules 业务规则列表（来自Phase 4.1 RequirementAnalyzer）
     * @param entity 关联实体（用于获取字段信息）
     * @param method 关联方法名称（用于过滤规则）
     * @return 生成的Java代码片段
     */
    public String generateBusinessLogic(List<BusinessRule> rules, Entity entity, String method) {
        log.info("[BusinessLogicGenerator] 开始生成业务逻辑代码: entity={}, method={}, 规则数量={}",
                entity.getName(), method, rules.size());

        // Step 1: 过滤当前方法的业务规则
        List<BusinessRule> methodRules = rules.stream()
                .filter(rule -> method.equals(rule.getMethod()))
                .collect(Collectors.toList());

        if (methodRules.isEmpty()) {
            log.warn("[BusinessLogicGenerator] ⚠️ 当前方法没有业务规则: entity={}, method={}",
                    entity.getName(), method);
            return "// TODO: 暂无业务规则，请根据需求添加";
        }

        // Step 2: 按优先级排序规则（priority数字越大优先级越高）
        List<BusinessRule> sortedRules = sortByPriority(methodRules);
        log.debug("[BusinessLogicGenerator] 业务规则已按优先级排序: 规则数量={}", sortedRules.size());

        // Step 3: 按类型分组规则
        Map<BusinessRuleType, List<BusinessRule>> groupedRules = groupByType(sortedRules);
        log.debug("[BusinessLogicGenerator] 业务规则已分组: VALIDATION={}, CALCULATION={}, WORKFLOW={}, NOTIFICATION={}",
                groupedRules.getOrDefault(BusinessRuleType.VALIDATION, List.of()).size(),
                groupedRules.getOrDefault(BusinessRuleType.CALCULATION, List.of()).size(),
                groupedRules.getOrDefault(BusinessRuleType.WORKFLOW, List.of()).size(),
                groupedRules.getOrDefault(BusinessRuleType.NOTIFICATION, List.of()).size());

        // Step 4: 为每个规则类型生成代码
        StringBuilder codeBuilder = new StringBuilder();

        // VALIDATION规则（最高优先级，先执行）
        if (groupedRules.containsKey(BusinessRuleType.VALIDATION)) {
            String validationCode = generateValidationCode(groupedRules.get(BusinessRuleType.VALIDATION), entity);
            codeBuilder.append(validationCode).append("\n\n");
        }

        // CALCULATION规则
        if (groupedRules.containsKey(BusinessRuleType.CALCULATION)) {
            String calculationCode = generateCalculationCode(groupedRules.get(BusinessRuleType.CALCULATION), entity);
            codeBuilder.append(calculationCode).append("\n\n");
        }

        // WORKFLOW规则
        if (groupedRules.containsKey(BusinessRuleType.WORKFLOW)) {
            String workflowCode = generateWorkflowCode(groupedRules.get(BusinessRuleType.WORKFLOW), entity);
            codeBuilder.append(workflowCode).append("\n\n");
        }

        // NOTIFICATION规则（最后执行）
        if (groupedRules.containsKey(BusinessRuleType.NOTIFICATION)) {
            String notificationCode = generateNotificationCode(groupedRules.get(BusinessRuleType.NOTIFICATION), entity);
            codeBuilder.append(notificationCode).append("\n");
        }

        String result = codeBuilder.toString().trim();
        log.info("[BusinessLogicGenerator] ✅ 业务逻辑代码生成完成: entity={}, method={}, 代码长度={} 字符",
                entity.getName(), method, result.length());

        return result;
    }

    /**
     * 按优先级排序业务规则（priority数字越大优先级越高）
     *
     * @param rules 业务规则列表
     * @return 排序后的业务规则列表
     */
    private List<BusinessRule> sortByPriority(List<BusinessRule> rules) {
        return rules.stream()
                .sorted(Comparator.comparingInt(BusinessRule::getPriority).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 按类型分组业务规则
     *
     * @param rules 业务规则列表
     * @return 按类型分组的Map（BusinessRuleType → List<BusinessRule>）
     */
    private Map<BusinessRuleType, List<BusinessRule>> groupByType(List<BusinessRule> rules) {
        return rules.stream()
                .collect(Collectors.groupingBy(BusinessRule::getType));
    }

    /**
     * 生成VALIDATION规则代码
     *
     * @param rules VALIDATION类型的业务规则列表
     * @param entity 关联实体
     * @return 生成的Java代码片段
     */
    private String generateValidationCode(List<BusinessRule> rules, Entity entity) {
        log.debug("[BusinessLogicGenerator] 开始生成VALIDATION规则代码: 规则数量={}", rules.size());

        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append("// ========== VALIDATION规则（数据验证） ==========\n");

        for (BusinessRule rule : rules) {
            try {
                // Step 1: 使用RulePatternMatcher提取参数
                Map<String, Object> params = rulePatternMatcher.matchValidationPattern(rule.getLogic());

                // Step 2: 补充实体相关参数
                params.put("entityName", entity.getName());

                // 生成实体变量名：User → user（首字母小写）
                String entityVarName = entity.getName().substring(0, 1).toLowerCase() +
                                       entity.getName().substring(1);
                params.put("entityVarName", entityVarName);

                // 使用rule.description作为用户友好的错误信息
                params.put("errorMessage", rule.getDescription());

                log.debug("[BusinessLogicGenerator] 规则参数提取完成: ruleName={}, patternType={}, params={}",
                        rule.getName(), params.get("patternType"), params);

                // Step 3: 使用FreeMarker渲染模板
                String ruleCode = templateEngine.render("ai/ValidationRule.ftl", params);

                // Step 4: 追加到代码构建器
                codeBuilder.append(ruleCode).append("\n");

                log.debug("[BusinessLogicGenerator] 规则代码生成完成: ruleName={}, codeLength={}",
                        rule.getName(), ruleCode.length());

            } catch (Exception e) {
                log.error("[BusinessLogicGenerator] ❌ 规则代码生成失败: ruleName={}, error={}",
                        rule.getName(), e.getMessage(), e);

                // 失败时输出注释占位符，不阻塞整体流程
                codeBuilder.append("// TODO: 规则生成失败 - ").append(rule.getName()).append("\n");
                codeBuilder.append("// 描述: ").append(rule.getDescription()).append("\n");
                codeBuilder.append("// 错误: ").append(e.getMessage()).append("\n\n");
            }
        }

        return codeBuilder.toString();
    }

    /**
     * 生成CALCULATION规则代码（V2.0 Phase 4.2.3 实现完成）
     *
     * <p>支持的4种计算模式：</p>
     * <ul>
     *   <li>ARITHMETIC: 简单算术计算（totalPrice = unitPrice × quantity）</li>
     *   <li>AGGREGATION: 聚合函数（sum、avg、max、min、count）</li>
     *   <li>CONDITIONAL: 条件计算（if-then-else逻辑）</li>
     *   <li>FORMULA: 公式表达式（复杂数学公式）</li>
     * </ul>
     *
     * @param rules CALCULATION类型的业务规则列表
     * @param entity 关联实体
     * @return 生成的Java代码片段
     * @since Phase 4.2.3
     */
    private String generateCalculationCode(List<BusinessRule> rules, Entity entity) {
        log.debug("[BusinessLogicGenerator] 开始生成CALCULATION规则代码: 规则数量={}", rules.size());

        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append("// ========== CALCULATION规则（业务计算） ==========\n");

        for (BusinessRule rule : rules) {
            try {
                // Step 1: 使用RulePatternMatcher提取参数
                Map<String, Object> params = rulePatternMatcher.matchCalculationPattern(rule.getLogic());

                // Step 2: 补充实体相关参数
                params.put("entityName", entity.getName());

                // 生成实体变量名：Order → order（首字母小写）
                String entityVarName = entity.getName().substring(0, 1).toLowerCase() +
                                       entity.getName().substring(1);
                params.put("entityVarName", entityVarName);

                // 使用rule.description作为注释
                params.put("description", rule.getDescription());

                log.debug("[BusinessLogicGenerator] 规则参数提取完成: ruleName={}, patternType={}, params={}",
                        rule.getName(), params.get("patternType"), params);

                // Step 3: 使用FreeMarker渲染模板
                String ruleCode = templateEngine.render("ai/CalculationRule.ftl", params);

                // Step 4: 追加到代码构建器
                codeBuilder.append(ruleCode).append("\n");

                log.debug("[BusinessLogicGenerator] 规则代码生成完成: ruleName={}, codeLength={}",
                        rule.getName(), ruleCode.length());

            } catch (Exception e) {
                log.error("[BusinessLogicGenerator] ❌ 规则代码生成失败: ruleName={}, error={}",
                        rule.getName(), e.getMessage(), e);

                // 失败时输出注释占位符，不阻塞整体流程
                codeBuilder.append("// TODO: 规则生成失败 - ").append(rule.getName()).append("\n");
                codeBuilder.append("// 描述: ").append(rule.getDescription()).append("\n");
                codeBuilder.append("// 错误: ").append(e.getMessage()).append("\n\n");
            }
        }

        return codeBuilder.toString();
    }

    /**
     * 生成WORKFLOW规则代码（V2.0 Phase 4.2.4实现完成）
     *
     * <p>支持的2种工作流模式：</p>
     * <ul>
     *   <li>STATE_TRANSITION: 状态流转（从状态A到状态B，带历史记录）</li>
     *   <li>CONDITIONAL_BRANCH: 条件分支流转（if条件 then状态A else状态B）</li>
     * </ul>
     *
     * @param rules WORKFLOW类型的业务规则列表
     * @param entity 关联实体
     * @return 生成的Java代码片段
     * @since Phase 4.2.4
     */
    private String generateWorkflowCode(List<BusinessRule> rules, Entity entity) {
        log.debug("[BusinessLogicGenerator] 开始生成WORKFLOW规则代码: 规则数量={}", rules.size());

        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append("// ========== WORKFLOW规则（状态流转） ==========\n");

        for (BusinessRule rule : rules) {
            try {
                // Step 1: 使用RulePatternMatcher提取参数
                Map<String, Object> params = rulePatternMatcher.matchWorkflowPattern(rule.getLogic());

                // Step 2: 补充实体相关参数
                params.put("entityName", entity.getName());

                // 生成实体变量名：Order → order（首字母小写）
                String entityVarName = entity.getName().substring(0, 1).toLowerCase() +
                                       entity.getName().substring(1);
                params.put("entityVarName", entityVarName);

                // 使用rule.description作为注释
                params.put("description", rule.getDescription());

                log.debug("[BusinessLogicGenerator] 规则参数提取完成: ruleName={}, patternType={}, params={}",
                        rule.getName(), params.get("patternType"), params);

                // Step 3: 使用FreeMarker渲染模板
                String ruleCode = templateEngine.render("ai/WorkflowRule.ftl", params);

                // Step 4: 追加到代码构建器
                codeBuilder.append(ruleCode).append("\n");

                log.debug("[BusinessLogicGenerator] 规则代码生成完成: ruleName={}, codeLength={}",
                        rule.getName(), ruleCode.length());

            } catch (Exception e) {
                log.error("[BusinessLogicGenerator] ❌ 规则代码生成失败: ruleName={}, error={}",
                        rule.getName(), e.getMessage(), e);

                // 失败时输出注释占位符，不阻塞整体流程
                codeBuilder.append("// TODO: 规则生成失败 - ").append(rule.getName()).append("\n");
                codeBuilder.append("// 描述: ").append(rule.getDescription()).append("\n");
                codeBuilder.append("// 错误: ").append(e.getMessage()).append("\n\n");
            }
        }

        return codeBuilder.toString();
    }

    /**
     * 生成NOTIFICATION规则代码（V2.0 Phase 4.2.4实现完成）
     *
     * <p>支持的3种通知模式：</p>
     * <ul>
     *   <li>EMAIL: 邮件通知</li>
     *   <li>SMS: 短信通知</li>
     *   <li>SYSTEM_MESSAGE: 站内消息通知</li>
     * </ul>
     *
     * @param rules NOTIFICATION类型的业务规则列表
     * @param entity 关联实体
     * @return 生成的Java代码片段
     * @since Phase 4.2.4
     */
    private String generateNotificationCode(List<BusinessRule> rules, Entity entity) {
        log.debug("[BusinessLogicGenerator] 开始生成NOTIFICATION规则代码: 规则数量={}", rules.size());

        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append("// ========== NOTIFICATION规则（消息通知） ==========\n");

        for (BusinessRule rule : rules) {
            try {
                // Step 1: 使用RulePatternMatcher提取参数
                Map<String, Object> params = rulePatternMatcher.matchNotificationPattern(rule.getLogic());

                // Step 2: 补充实体相关参数
                params.put("entityName", entity.getName());

                // 生成实体变量名：Order → order（首字母小写）
                String entityVarName = entity.getName().substring(0, 1).toLowerCase() +
                                       entity.getName().substring(1);
                params.put("entityVarName", entityVarName);

                // 使用rule.description作为注释
                params.put("description", rule.getDescription());

                log.debug("[BusinessLogicGenerator] 规则参数提取完成: ruleName={}, notificationType={}, params={}",
                        rule.getName(), params.get("notificationType"), params);

                // Step 3: 使用FreeMarker渲染模板
                String ruleCode = templateEngine.render("ai/NotificationRule.ftl", params);

                // Step 4: 追加到代码构建器
                codeBuilder.append(ruleCode).append("\n");

                log.debug("[BusinessLogicGenerator] 规则代码生成完成: ruleName={}, codeLength={}",
                        rule.getName(), ruleCode.length());

            } catch (Exception e) {
                log.error("[BusinessLogicGenerator] ❌ 规则代码生成失败: ruleName={}, error={}",
                        rule.getName(), e.getMessage(), e);

                // 失败时输出注释占位符，不阻塞整体流程
                codeBuilder.append("// TODO: 规则生成失败 - ").append(rule.getName()).append("\n");
                codeBuilder.append("// 描述: ").append(rule.getDescription()).append("\n");
                codeBuilder.append("// 错误: ").append(e.getMessage()).append("\n\n");
            }
        }

        return codeBuilder.toString();
    }
}
