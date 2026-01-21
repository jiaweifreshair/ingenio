package com.ingenio.backend.codegen.ai.generator;

import com.ingenio.backend.codegen.ai.model.BusinessRule;
import com.ingenio.backend.codegen.ai.model.BusinessRule.BusinessRuleType;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.template.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务逻辑代码生成器（V2.0 Phase 4.2）
 */
@Service
public class BusinessLogicGenerator {

    private static final Logger log = LoggerFactory.getLogger(BusinessLogicGenerator.class);

    private final TemplateEngine templateEngine;
    private final RulePatternMatcher rulePatternMatcher;

    @Autowired
    public BusinessLogicGenerator(TemplateEngine templateEngine,
            RulePatternMatcher rulePatternMatcher) {
        this.templateEngine = templateEngine;
        this.rulePatternMatcher = rulePatternMatcher;
    }

    public String generateBusinessLogic(List<BusinessRule> rules, Entity entity, String method) {
        log.info("[BusinessLogicGenerator] 开始生成业务逻辑代码: entity={}, method={}, 规则数量={}",
                entity.getName(), method, rules.size());

        List<BusinessRule> methodRules = rules.stream()
                .filter(rule -> method.equals(rule.getMethod()))
                .collect(Collectors.toList());

        if (methodRules.isEmpty()) {
            log.warn("[BusinessLogicGenerator] ⚠️ 当前方法没有业务规则: entity={}, method={}",
                    entity.getName(), method);
            return "// TODO: 暂无业务规则，请根据需求添加";
        }

        List<BusinessRule> sortedRules = sortByPriority(methodRules);
        log.debug("[BusinessLogicGenerator] 业务规则已按优先级排序: 规则数量={}", sortedRules.size());

        Map<BusinessRuleType, List<BusinessRule>> groupedRules = groupByType(sortedRules);
        log.debug("[BusinessLogicGenerator] 业务规则已分组: VALIDATION={}, CALCULATION={}, WORKFLOW={}, NOTIFICATION={}",
                groupedRules.getOrDefault(BusinessRuleType.VALIDATION, List.of()).size(),
                groupedRules.getOrDefault(BusinessRuleType.CALCULATION, List.of()).size(),
                groupedRules.getOrDefault(BusinessRuleType.WORKFLOW, List.of()).size(),
                groupedRules.getOrDefault(BusinessRuleType.NOTIFICATION, List.of()).size());

        StringBuilder codeBuilder = new StringBuilder();

        if (groupedRules.containsKey(BusinessRuleType.VALIDATION)) {
            String validationCode = generateValidationCode(groupedRules.get(BusinessRuleType.VALIDATION), entity);
            codeBuilder.append(validationCode).append("\n\n");
        }

        if (groupedRules.containsKey(BusinessRuleType.CALCULATION)) {
            String calculationCode = generateCalculationCode(groupedRules.get(BusinessRuleType.CALCULATION), entity);
            codeBuilder.append(calculationCode).append("\n\n");
        }

        if (groupedRules.containsKey(BusinessRuleType.WORKFLOW)) {
            String workflowCode = generateWorkflowCode(groupedRules.get(BusinessRuleType.WORKFLOW), entity);
            codeBuilder.append(workflowCode).append("\n\n");
        }

        if (groupedRules.containsKey(BusinessRuleType.NOTIFICATION)) {
            String notificationCode = generateNotificationCode(groupedRules.get(BusinessRuleType.NOTIFICATION), entity);
            codeBuilder.append(notificationCode).append("\n");
        }

        String result = codeBuilder.toString().trim();
        log.info("[BusinessLogicGenerator] ✅ 业务逻辑代码生成完成: entity={}, method={}, 代码长度={} 字符",
                entity.getName(), method, result.length());

        return result;
    }

    private List<BusinessRule> sortByPriority(List<BusinessRule> rules) {
        return rules.stream()
                .sorted(Comparator.comparingInt(BusinessRule::getPriority).reversed())
                .collect(Collectors.toList());
    }

    private Map<BusinessRuleType, List<BusinessRule>> groupByType(List<BusinessRule> rules) {
        return rules.stream()
                .collect(Collectors.groupingBy(BusinessRule::getType));
    }

    private String generateValidationCode(List<BusinessRule> rules, Entity entity) {
        log.debug("[BusinessLogicGenerator] 开始生成VALIDATION规则代码: 规则数量={}", rules.size());

        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append("// ========== VALIDATION规则（数据验证） ==========\n");

        for (BusinessRule rule : rules) {
            try {
                Map<String, Object> params = rulePatternMatcher.matchValidationPattern(rule.getLogic());
                params.put("entityName", entity.getName());

                String entityVarName = entity.getName().substring(0, 1).toLowerCase() +
                        entity.getName().substring(1);
                params.put("entityVarName", entityVarName);
                params.put("errorMessage", rule.getDescription());

                log.debug("[BusinessLogicGenerator] 规则参数提取完成: ruleName={}, patternType={}, params={}",
                        rule.getName(), params.get("patternType"), params);

                String ruleCode = templateEngine.render("ai/ValidationRule.ftl", params);
                codeBuilder.append(ruleCode).append("\n");

                log.debug("[BusinessLogicGenerator] 规则代码生成完成: ruleName={}, codeLength={}",
                        rule.getName(), ruleCode.length());

            } catch (Exception e) {
                log.error("[BusinessLogicGenerator] ❌ 规则代码生成失败: ruleName={}, error={}",
                        rule.getName(), e.getMessage(), e);
                codeBuilder.append("// TODO: 规则生成失败 - ").append(rule.getName()).append("\n");
                codeBuilder.append("// 描述: ").append(rule.getDescription()).append("\n");
                codeBuilder.append("// 错误: ").append(e.getMessage()).append("\n\n");
            }
        }
        return codeBuilder.toString();
    }

    private String generateCalculationCode(List<BusinessRule> rules, Entity entity) {
        log.debug("[BusinessLogicGenerator] 开始生成CALCULATION规则代码: 规则数量={}", rules.size());

        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append("// ========== CALCULATION规则（业务计算） ==========\n");

        for (BusinessRule rule : rules) {
            try {
                Map<String, Object> params = rulePatternMatcher.matchCalculationPattern(rule.getLogic());
                params.put("entityName", entity.getName());

                String entityVarName = entity.getName().substring(0, 1).toLowerCase() +
                        entity.getName().substring(1);
                params.put("entityVarName", entityVarName);
                params.put("description", rule.getDescription());

                log.debug("[BusinessLogicGenerator] 规则参数提取完成: ruleName={}, patternType={}, params={}",
                        rule.getName(), params.get("patternType"), params);

                String ruleCode = templateEngine.render("ai/CalculationRule.ftl", params);
                codeBuilder.append(ruleCode).append("\n");

                log.debug("[BusinessLogicGenerator] 规则代码生成完成: ruleName={}, codeLength={}",
                        rule.getName(), ruleCode.length());

            } catch (Exception e) {
                log.error("[BusinessLogicGenerator] ❌ 规则代码生成失败: ruleName={}, error={}",
                        rule.getName(), e.getMessage(), e);
                codeBuilder.append("// TODO: 规则生成失败 - ").append(rule.getName()).append("\n");
                codeBuilder.append("// 描述: ").append(rule.getDescription()).append("\n");
                codeBuilder.append("// 错误: ").append(e.getMessage()).append("\n\n");
            }
        }
        return codeBuilder.toString();
    }

    private String generateWorkflowCode(List<BusinessRule> rules, Entity entity) {
        log.debug("[BusinessLogicGenerator] 开始生成WORKFLOW规则代码: 规则数量={}", rules.size());

        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append("// ========== WORKFLOW规则（状态流转） ==========\n");

        for (BusinessRule rule : rules) {
            try {
                Map<String, Object> params = rulePatternMatcher.matchWorkflowPattern(rule.getLogic());
                params.put("entityName", entity.getName());

                String entityVarName = entity.getName().substring(0, 1).toLowerCase() +
                        entity.getName().substring(1);
                params.put("entityVarName", entityVarName);
                params.put("description", rule.getDescription());

                log.debug("[BusinessLogicGenerator] 规则参数提取完成: ruleName={}, patternType={}, params={}",
                        rule.getName(), params.get("patternType"), params);

                String ruleCode = templateEngine.render("ai/WorkflowRule.ftl", params);
                codeBuilder.append(ruleCode).append("\n");

                log.debug("[BusinessLogicGenerator] 规则代码生成完成: ruleName={}, codeLength={}",
                        rule.getName(), ruleCode.length());

            } catch (Exception e) {
                log.error("[BusinessLogicGenerator] ❌ 规则代码生成失败: ruleName={}, error={}",
                        rule.getName(), e.getMessage(), e);
                codeBuilder.append("// TODO: 规则生成失败 - ").append(rule.getName()).append("\n");
                codeBuilder.append("// 描述: ").append(rule.getDescription()).append("\n");
                codeBuilder.append("// 错误: ").append(e.getMessage()).append("\n\n");
            }
        }
        return codeBuilder.toString();
    }

    private String generateNotificationCode(List<BusinessRule> rules, Entity entity) {
        log.debug("[BusinessLogicGenerator] 开始生成NOTIFICATION规则代码: 规则数量={}", rules.size());

        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append("// ========== NOTIFICATION规则（消息通知） ==========\n");

        for (BusinessRule rule : rules) {
            try {
                Map<String, Object> params = rulePatternMatcher.matchNotificationPattern(rule.getLogic());
                params.put("entityName", entity.getName());

                String entityVarName = entity.getName().substring(0, 1).toLowerCase() +
                        entity.getName().substring(1);
                params.put("entityVarName", entityVarName);
                params.put("description", rule.getDescription());

                log.debug("[BusinessLogicGenerator] 规则参数提取完成: ruleName={}, notificationType={}, params={}",
                        rule.getName(), params.get("notificationType"), params);

                String ruleCode = templateEngine.render("ai/NotificationRule.ftl", params);
                codeBuilder.append(ruleCode).append("\n");

                log.debug("[BusinessLogicGenerator] 规则代码生成完成: ruleName={}, codeLength={}",
                        rule.getName(), ruleCode.length());

            } catch (Exception e) {
                log.error("[BusinessLogicGenerator] ❌ 规则代码生成失败: ruleName={}, error={}",
                        rule.getName(), e.getMessage(), e);
                codeBuilder.append("// TODO: 规则生成失败 - ").append(rule.getName()).append("\n");
                codeBuilder.append("// 描述: ").append(rule.getDescription()).append("\n");
                codeBuilder.append("// 错误: ").append(e.getMessage()).append("\n\n");
            }
        }
        return codeBuilder.toString();
    }
}
