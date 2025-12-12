package com.ingenio.backend.codegen.ai.generator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 业务规则模式匹配器（V2.0 Phase 4.2）
 *
 * <p>负责将自然语言描述的业务逻辑（BusinessRule.logic）转换为结构化数据，供模板引擎渲染</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>VALIDATION规则模式匹配：识别字段验证、范围检查、格式校验等模式</li>
 *   <li>CALCULATION规则模式匹配：识别算术计算、聚合函数、公式表达式等模式</li>
 *   <li>WORKFLOW规则模式匹配：识别状态流转、条件分支、审批流程等模式</li>
 *   <li>NOTIFICATION规则模式匹配：识别消息发送、邮件通知、推送等模式</li>
 *   <li>参数提取：从自然语言中提取字段名、阈值、运算符等关键参数</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // Phase 4.1生成的BusinessRule
 * BusinessRule rule = BusinessRule.builder()
 *     .name("validateAge")
 *     .description("验证用户年龄必须≥18岁")
 *     .type(VALIDATION)
 *     .logic("检查age字段，小于18抛出BusinessException")  // ← 自然语言描述
 *     .build();
 *
 * // 使用RulePatternMatcher提取结构化参数
 * RulePatternMatcher matcher = new RulePatternMatcher();
 * Map<String, Object> params = matcher.matchValidationPattern(rule.getLogic());
 *
 * // 提取结果示例：
 * // {
 * //   "patternType": "RANGE_CHECK",
 * //   "fieldName": "age",
 * //   "operator": ">=",
 * //   "threshold": "18",
 * //   "errorMessage": "用户年龄必须≥18岁"
 * // }
 *
 * // 将参数传递给FreeMarker模板渲染
 * String code = templateEngine.render("ValidationRule.ftl", params);
 * }</pre>
 *
 * <p>输入示例（BusinessRule.logic自然语言）：</p>
 * <pre>{@code
 * // VALIDATION类型
 * "检查age字段，小于18抛出BusinessException"
 * "验证email字段匹配正则表达式^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
 * "username长度必须在3-20字符之间"
 *
 * // CALCULATION类型
 * "订单总价 = Σ(商品单价 * 数量)"
 * "会员积分 = 订单金额 * 积分倍率"
 * "优惠金额 = 原价 * 折扣率"
 *
 * // WORKFLOW类型
 * "订单状态从待支付变更为已支付"
 * "如果请假天数>3天，需要部门经理审批"
 *
 * // NOTIFICATION类型
 * "发送欢迎邮件给新注册用户"
 * "推送订单状态变更通知"
 * }</pre>
 *
 * <p>输出示例（Map参数）：</p>
 * <pre>{@code
 * // VALIDATION模式输出
 * {
 *   "patternType": "RANGE_CHECK",       // 模式类型
 *   "fieldName": "age",                 // 字段名称
 *   "operator": ">=",                   // 运算符
 *   "threshold": "18",                  // 阈值
 *   "errorCode": "INVALID_AGE",         // 错误码（自动生成）
 *   "errorMessage": "用户年龄必须≥18岁" // 错误信息
 * }
 *
 * // CALCULATION模式输出
 * {
 *   "patternType": "AGGREGATION",       // 模式类型
 *   "targetField": "totalPrice",        // 目标字段
 *   "sourceCollection": "orderItems",   // 来源集合
 *   "formula": "unitPrice * quantity",  // 计算公式
 *   "aggregationFunc": "sum"            // 聚合函数
 * }
 * }</pre>
 *
 * <p>模式库设计（Phase 4.2.2-4.2.4逐步实现）：</p>
 * <ul>
 *   <li><b>VALIDATION模式</b>：范围检查、格式验证、唯一性校验、非空检查、长度限制</li>
 *   <li><b>CALCULATION模式</b>：算术计算、聚合函数、条件计算、公式表达式</li>
 *   <li><b>WORKFLOW模式</b>：状态流转、条件分支、审批流程、定时任务</li>
 *   <li><b>NOTIFICATION模式</b>：邮件发送、短信通知、站内推送、Webhook调用</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-18 V2.0 Phase 4.2.1: 业务规则模式匹配器
 * @see com.ingenio.backend.codegen.ai.generator.BusinessLogicGenerator Phase 4.2 业务逻辑生成器
 * @see com.ingenio.backend.codegen.template.TemplateEngine 模板引擎
 */
@Slf4j
@Component
public class RulePatternMatcher {

    /**
     * 匹配VALIDATION规则模式
     *
     * <p>识别验证规则的自然语言描述，提取关键参数用于代码生成</p>
     *
     * <p>支持的验证模式：</p>
     * <ul>
     *   <li><b>范围检查</b>：age ≥ 18、price > 0、quantity between 1 and 100</li>
     *   <li><b>格式验证</b>：email格式、手机号格式、身份证号格式</li>
     *   <li><b>唯一性校验</b>：username唯一、email唯一</li>
     *   <li><b>非空检查</b>：字段不能为空、必填字段</li>
     *   <li><b>长度限制</b>：username长度3-20、password长度≥8</li>
     * </ul>
     *
     * <p>输入示例：</p>
     * <pre>{@code
     * "检查age字段，小于18抛出BusinessException"
     * "验证email字段匹配正则表达式^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
     * "username长度必须在3-20字符之间"
     * "价格必须大于0"
     * }</pre>
     *
     * <p>输出示例：</p>
     * <pre>{@code
     * {
     *   "patternType": "RANGE_CHECK",       // RANGE_CHECK | FORMAT_CHECK | UNIQUENESS_CHECK | NOT_NULL_CHECK | LENGTH_CHECK
     *   "fieldName": "age",                 // 字段名称
     *   "operator": ">=",                   // >=, >, <=, <, ==, !=, between
     *   "threshold": "18",                  // 阈值（单值）
     *   "thresholdMin": "3",                // 阈值范围最小值（范围检查）
     *   "thresholdMax": "20",               // 阈值范围最大值（范围检查）
     *   "regex": "^[a-zA-Z0-9._%+-]+@...",  // 正则表达式（格式验证）
     *   "errorCode": "INVALID_AGE",         // 错误码（自动生成：INVALID_{FIELD_NAME_UPPER}）
     *   "errorMessage": "用户年龄必须≥18岁" // 错误信息（从rule.description提取）
     * }
     * }</pre>
     *
     * @param logic 自然语言描述的验证逻辑（来自BusinessRule.logic）
     * @return 结构化参数Map，供FreeMarker模板使用
     */
    public Map<String, Object> matchValidationPattern(String logic) {
        log.debug("[RulePatternMatcher] 开始匹配VALIDATION规则模式: logic={}", logic);

        Map<String, Object> params = new HashMap<>();

        // Step 1: 识别模式类型（按优先级）
        String patternType = identifyValidationPattern(logic);
        params.put("patternType", patternType);
        log.debug("[RulePatternMatcher] 识别到验证模式: patternType={}", patternType);

        // Step 2: 根据模式类型提取参数
        switch (patternType) {
            case "RANGE_CHECK":
                // 范围检查：提取字段名、运算符、阈值
                params.put("fieldName", extractFieldName(logic));
                params.put("operator", extractOperator(logic));

                // 判断是否为范围检查（between）
                if ("between".equals(params.get("operator"))) {
                    String[] range = extractRange(logic);
                    params.put("thresholdMin", range[0]);
                    params.put("thresholdMax", range[1]);
                } else {
                    params.put("threshold", extractThreshold(logic));
                }
                break;

            case "FORMAT_CHECK":
                // 格式验证：提取字段名和正则表达式
                params.put("fieldName", extractFieldName(logic));
                params.put("regex", extractRegex(logic));
                break;

            case "NOT_NULL_CHECK":
                // 非空检查：只需提取字段名
                params.put("fieldName", extractFieldName(logic));
                break;

            case "LENGTH_CHECK":
                // 长度限制：提取字段名、运算符、阈值
                params.put("fieldName", extractFieldName(logic));

                // 判断是否为范围限制（between）
                if (logic.contains("之间") || logic.matches(".*\\d+-\\d+.*")) {
                    String[] range = extractRange(logic);
                    params.put("thresholdMin", range[0]);
                    params.put("thresholdMax", range[1]);
                    params.put("operator", "between");
                } else {
                    params.put("operator", extractOperator(logic));
                    params.put("threshold", extractThreshold(logic));
                }
                break;

            case "UNIQUENESS_CHECK":
                // 唯一性校验：提取字段名
                params.put("fieldName", extractFieldName(logic));
                break;

            default:
                log.warn("[RulePatternMatcher] ⚠️ 未知的VALIDATION模式: patternType={}", patternType);
                params.put("fieldName", "unknownField");
                break;
        }

        // Step 3: 生成通用参数（所有模式都需要）
        String fieldName = (String) params.get("fieldName");
        if (fieldName != null && !fieldName.isEmpty()) {
            params.put("getter", "get" + capitalize(fieldName));
            params.put("errorCode", generateErrorCode(fieldName));
        } else {
            params.put("getter", "getUnknownField");
            params.put("errorCode", "UNKNOWN_ERROR");
        }

        log.debug("[RulePatternMatcher] VALIDATION规则模式匹配完成: patternType={}, fieldName={}, params={}",
                patternType, fieldName, params);
        return params;
    }


    /**
     * 匹配WORKFLOW规则模式
     *
     * <p>识别工作流规则的自然语言描述，提取状态流转和分支逻辑</p>
     *
     * <p>支持的工作流模式：</p>
     * <ul>
     *   <li><b>状态流转</b>：订单状态从待支付变更为已支付</li>
     *   <li><b>条件分支</b>：如果金额>1000，需要经理审批</li>
     *   <li><b>审批流程</b>：员工→主管→经理→总监</li>
     *   <li><b>定时任务</b>：订单创建后24小时自动取消</li>
     * </ul>
     *
     * <p>输入示例：</p>
     * <pre>{@code
     * "订单状态从待支付变更为已支付"
     * "如果请假天数>3天，需要部门经理审批"
     * "审批流程：申请人→直属领导→部门经理→总经理"
     * }</pre>
     *
     * <p>输出示例：</p>
     * <pre>{@code
     * {
     *   "patternType": "STATE_TRANSITION",  // STATE_TRANSITION | CONDITIONAL_BRANCH | APPROVAL_FLOW | SCHEDULED_TASK
     *   "stateField": "orderStatus",        // 状态字段
     *   "fromState": "PENDING_PAYMENT",     // 起始状态
     *   "toState": "PAID",                  // 目标状态
     *   "condition": "amount > 1000",       // 条件表达式
     *   "approvers": ["manager", "director"] // 审批人列表
     * }
     * }</pre>
     *
     * @param logic 自然语言描述的工作流逻辑（来自BusinessRule.logic）
     * @return 结构化参数Map，供FreeMarker模板使用
     */
    public Map<String, Object> matchWorkflowPattern(String logic) {
        log.debug("[RulePatternMatcher] 开始匹配WORKFLOW规则模式: logic={}", logic);

        Map<String, Object> params = new HashMap<>();

        // Step 1: 识别模式类型
        String patternType = identifyWorkflowPattern(logic);
        params.put("patternType", patternType);

        if ("STATE_TRANSITION".equals(patternType)) {
            // STATE_TRANSITION模式参数提取
            params.put("currentState", extractStateValue(logic, "从|当前状态"));
            params.put("targetState", extractStateValue(logic, "到|目标状态|变更为"));
            params.put("statusField", "status");
        } else if ("CONDITIONAL_BRANCH".equals(patternType)) {
            // CONDITIONAL_BRANCH模式参数提取
            params.put("condition", extractConditionExpression(logic));
            params.put("trueState", extractStateValue(logic, "则|then"));
            params.put("falseState", extractStateValue(logic, "否则|else"));
            params.put("statusField", "status");
        }

        log.debug("[RulePatternMatcher] WORKFLOW规则参数: params={}", params);
        return params;
    }

    /**
     * 识别WORKFLOW模式类型
     *
     * @param logic 业务规则逻辑文本
     * @return WORKFLOW模式类型
     */
    private String identifyWorkflowPattern(String logic) {
        // 优先级1: CONDITIONAL_BRANCH（条件分支）
        if (logic.matches(".*如果.*则.*否则.*") || logic.matches(".*if.*then.*else.*")) {
            log.debug("[RulePatternMatcher] 识别为CONDITIONAL_BRANCH模式");
            return "CONDITIONAL_BRANCH";
        }

        // 优先级2: STATE_TRANSITION（状态流转）
        if (logic.contains("从") || logic.contains("到") || logic.contains("状态")) {
            log.debug("[RulePatternMatcher] 识别为STATE_TRANSITION模式");
            return "STATE_TRANSITION";
        }

        // 默认
        log.debug("[RulePatternMatcher] 默认使用STATE_TRANSITION模式");
        return "STATE_TRANSITION";
    }

    /**
     * 提取状态值
     *
     * @param text 业务规则逻辑文本
     * @param keywords 关键词正则（用|分隔）
     * @return 状态值
     */
    private String extractStateValue(String text, String keywords) {
        if (text == null || text.isEmpty()) {
            return "默认状态";
        }

        // 尝试匹配：关键词后面跟引号包裹的内容
        String pattern = String.format("(?:%s)[^'\"]*['\"]([^'\"]+)['\"]", keywords);
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String value = m.group(1);
            log.debug("[RulePatternMatcher] 提取状态值: value={}", value);
            return value;
        }

        // 尝试匹配：关键词后面跟中文
        String pattern2 = String.format("(?:%s)([\\u4e00-\\u9fa5]+)", keywords);
        Pattern p2 = Pattern.compile(pattern2);
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            String value = m2.group(1);
            log.debug("[RulePatternMatcher] 从中文提取状态值: value={}", value);
            return value;
        }

        log.warn("[RulePatternMatcher] 无法提取状态值，使用默认值");
        return "默认状态";
    }

    /**
     * 提取条件表达式
     *
     * @param text 业务规则逻辑文本
     * @return 条件表达式
     */
    private String extractConditionExpression(String text) {
        if (text == null || text.isEmpty()) {
            return "true";
        }

        // 尝试匹配：如果xxx则
        Pattern p = Pattern.compile("如果(.+?)则");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }

        // 尝试匹配：if xxx then
        Pattern p2 = Pattern.compile("if\\s+(.+?)\\s+then");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            return m2.group(1).trim();
        }

        return "true";
    }

    /**
     * 匹配NOTIFICATION规则模式
     *
     * <p>识别通知规则的自然语言描述，提取消息类型和接收人</p>
     *
     * <p>支持的通知模式：</p>
     * <ul>
     *   <li><b>邮件发送</b>：发送欢迎邮件给新注册用户</li>
     *   <li><b>短信通知</b>：发送验证码短信</li>
     *   <li><b>站内推送</b>：推送订单状态变更通知</li>
     *   <li><b>Webhook调用</b>：调用第三方回调接口</li>
     * </ul>
     *
     * <p>输入示例：</p>
     * <pre>{@code
     * "发送欢迎邮件给新注册用户"
     * "推送订单状态变更通知给用户"
     * "发送短信验证码到用户手机"
     * }</pre>
     *
     * <p>输出示例：</p>
     * <pre>{@code
     * {
     *   "patternType": "EMAIL",             // EMAIL | SMS | PUSH | WEBHOOK
     *   "recipientField": "user.email",     // 接收人字段
     *   "templateName": "welcome_email",    // 消息模板名称
     *   "messageType": "NOTIFICATION",      // 消息类型
     *   "async": true                       // 是否异步发送
     * }
     * }</pre>
     *
     * @param logic 自然语言描述的通知逻辑（来自BusinessRule.logic）
     * @return 结构化参数Map，供FreeMarker模板使用
     */
    public Map<String, Object> matchNotificationPattern(String logic) {
        log.debug("[RulePatternMatcher] 开始匹配NOTIFICATION规则模式: logic={}", logic);

        Map<String, Object> params = new HashMap<>();

        // Step 1: 识别通知类型
        String patternType = identifyNotificationType(logic);
        params.put("patternType", patternType);

        // Step 2: 提取通用参数
        params.put("recipientField", determineRecipientField(patternType));
        params.put("titleTemplate", extractNotificationTitle(logic));
        params.put("contentTemplate", extractNotificationContent(logic));
        params.put("triggerCondition", extractTriggerCondition(logic));

        log.debug("[RulePatternMatcher] NOTIFICATION规则参数: params={}", params);
        return params;
    }

    /**
     * 识别通知类型
     *
     * @param logic 业务规则逻辑文本
     * @return 通知类型
     */
    private String identifyNotificationType(String logic) {
        if (logic.contains("邮件") || logic.contains("email") || logic.contains("Email")) {
            return "EMAIL";
        }
        if (logic.contains("短信") || logic.contains("sms") || logic.contains("SMS")) {
            return "SMS";
        }
        if (logic.contains("站内") || logic.contains("消息") || logic.contains("通知")) {
            return "SYSTEM_MESSAGE";
        }
        return "EMAIL"; // 默认邮件
    }

    /**
     * 确定接收者字段名
     *
     * @param notificationType 通知类型
     * @return 接收者字段名
     */
    private String determineRecipientField(String notificationType) {
        switch (notificationType) {
            case "EMAIL":
                return "userEmail";
            case "SMS":
                return "userPhone";
            case "SYSTEM_MESSAGE":
                return "userId";
            default:
                return "userEmail";
        }
    }

    /**
     * 提取通知标题
     *
     * @param text 业务规则逻辑文本
     * @return 通知标题
     */
    private String extractNotificationTitle(String text) {
        if (text == null || text.isEmpty()) {
            return "系统通知";
        }

        // 尝试匹配：标题：xxx
        Pattern p = Pattern.compile("标题[：:]\\s*['\"]([^'\"]+)['\"]");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1);
        }

        return "系统通知";
    }

    /**
     * 提取通知内容
     *
     * @param text 业务规则逻辑文本
     * @return 通知内容
     */
    private String extractNotificationContent(String text) {
        if (text == null || text.isEmpty()) {
            return "通知内容";
        }

        // 尝试匹配：内容：xxx
        Pattern p = Pattern.compile("内容[：:]\\s*['\"]([^'\"]+)['\"]");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1);
        }

        // 尝试匹配整个文本
        return text.replaceAll("发送.*通知", "").trim();
    }

    /**
     * 提取触发条件
     *
     * @param text 业务规则逻辑文本
     * @return 触发条件
     */
    private String extractTriggerCondition(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // 尝试匹配：当xxx时
        Pattern p = Pattern.compile("当(.+?)时");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }

        return null;
    }

    // ========== 工具方法（Phase 4.2.2-4.2.4实现） ==========

    /**
     * 从自然语言中提取字段名
     *
     * <p>支持多种表达方式：</p>
     * <ul>
     *   <li>"检查age字段" → "age"</li>
     *   <li>"用户名username" → "username"</li>
     *   <li>"验证email" → "email"</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return 字段名（驼峰命名），如果未识别则返回null
     */
    private String extractFieldName(String text) {
        // 模式1: "检查{字段名}字段" - 最精确
        Pattern pattern1 = Pattern.compile("检查(\\w+)字段");
        Matcher matcher1 = pattern1.matcher(text);
        if (matcher1.find()) {
            return matcher1.group(1);
        }

        // 模式2: "验证{字段名}" - 格式验证场景
        Pattern pattern2 = Pattern.compile("验证(\\w+)");
        Matcher matcher2 = pattern2.matcher(text);
        if (matcher2.find()) {
            return matcher2.group(1);
        }

        // 模式3: "{字段名}(必须|不能|应该|长度)" - 通用模式
        Pattern pattern3 = Pattern.compile("(\\w+)(必须|不能|应该|长度|格式)");
        Matcher matcher3 = pattern3.matcher(text);
        if (matcher3.find()) {
            return matcher3.group(1);
        }

        log.warn("[RulePatternMatcher] ⚠️ 无法从文本中提取字段名: text={}", text);
        return "unknownField";  // 返回默认值而非null，避免NPE
    }

    /**
     * 从自然语言中提取运算符
     *
     * <p>支持的运算符表达：</p>
     * <ul>
     *   <li>"大于" / ">" → ">"</li>
     *   <li>"小于" / "<" → "<"</li>
     *   <li>"大于等于" / "≥" / ">=" → ">="</li>
     *   <li>"等于" / "==" → "=="</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return 标准化运算符，如果未识别则返回null
     */
    private String extractOperator(String text) {
        // 优先匹配：符号 > 两字词 > 单字词
        // 符号优先级最高（避免歧义）
        if (text.contains(">=") || text.contains("≥")) return ">=";
        if (text.contains("<=") || text.contains("≤")) return "<=";
        if (text.contains("==")) return "==";
        if (text.contains("!=") || text.contains("≠")) return "!=";

        // 两字词（优先于单字，避免"大于等于"被识别为"大于"）
        if (text.contains("大于等于") || text.contains("不小于")) return ">=";
        if (text.contains("小于等于") || text.contains("不大于")) return "<=";
        if (text.contains("不等于")) return "!=";
        if (text.contains("之间")) return "between";

        // 单字词
        if (text.contains("大于") || text.contains(">")) return ">";
        if (text.contains("小于") || text.contains("<")) return "<";
        if (text.contains("等于")) return "==";

        log.warn("[RulePatternMatcher] ⚠️ 无法从文本中提取运算符: text={}", text);
        return "==";  // 默认相等比较
    }

    /**
     * 从自然语言中提取数值阈值
     *
     * <p>支持的数值表达：</p>
     * <ul>
     *   <li>"小于18" → "18"</li>
     *   <li>"在3-20之间" → thresholdMin="3", thresholdMax="20"</li>
     *   <li>"至少8位" → "8"</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return 阈值（字符串形式），如果未识别则返回null
     */
    private String extractThreshold(String text) {
        // 提取第一个数字作为阈值
        Pattern numberPattern = Pattern.compile("(\\d+)");
        Matcher matcher = numberPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        log.warn("[RulePatternMatcher] ⚠️ 无法从文本中提取阈值: text={}", text);
        return "0";  // 返回默认值
    }

    /**
     * 生成错误码
     *
     * <p>根据字段名自动生成错误码，遵循命名规范：INVALID_{FIELD_NAME_UPPER}</p>
     *
     * @param fieldName 字段名（驼峰命名）
     * @return 错误码（大写下划线命名），例如 "INVALID_USER_NAME"
     */
    private String generateErrorCode(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return "INVALID_FIELD";
        }

        // 驼峰转下划线：userName → user_name
        String snakeCase = fieldName.replaceAll("([a-z])([A-Z])", "$1_$2");

        // 转大写并添加INVALID_前缀
        return "INVALID_" + snakeCase.toUpperCase();
    }

    // ========== VALIDATION模式识别辅助方法（Phase 4.2.2实现完成） ==========

    /**
     * 识别VALIDATION规则模式类型
     *
     * <p>根据自然语言关键词识别5种验证模式，优先级从高到低：</p>
     * <ol>
     *   <li>FORMAT_CHECK（格式验证）- 正则、匹配、格式</li>
     *   <li>UNIQUENESS_CHECK（唯一性校验）- 唯一</li>
     *   <li>NOT_NULL_CHECK（非空检查）- 不能为空、必填、null</li>
     *   <li>LENGTH_CHECK（长度限制）- 长度</li>
     *   <li>RANGE_CHECK（范围检查）- 大于、小于、等于、范围、之间</li>
     * </ol>
     *
     * @param logic 自然语言描述
     * @return 模式类型字符串
     */
    private String identifyValidationPattern(String logic) {
        if (logic == null || logic.isEmpty()) {
            return "RANGE_CHECK";  // 默认
        }

        // 优先级从高到低
        // 1. FORMAT_CHECK: 正则、匹配、格式
        if (logic.matches(".*正则.*|.*匹配.*|.*格式.*")) {
            return "FORMAT_CHECK";
        }

        // 2. UNIQUENESS_CHECK: 唯一
        if (logic.matches(".*唯一.*")) {
            return "UNIQUENESS_CHECK";
        }

        // 3. NOT_NULL_CHECK: 不能为空、必填、null相关
        if (logic.matches(".*不能为空.*|.*必填.*|.*必须.*null.*")) {
            return "NOT_NULL_CHECK";
        }

        // 4. LENGTH_CHECK: 长度
        if (logic.matches(".*长度.*")) {
            return "LENGTH_CHECK";
        }

        // 5. RANGE_CHECK: 大于、小于、等于、范围、之间
        if (logic.matches(".*大于.*|.*小于.*|.*等于.*|.*范围.*|.*之间.*")) {
            return "RANGE_CHECK";
        }

        // 默认返回RANGE_CHECK（最常见）
        return "RANGE_CHECK";
    }

    /**
     * 首字母大写
     *
     * <p>用于生成getter方法名：age → Age → getAge()</p>
     *
     * @param str 字符串
     * @return 首字母大写的字符串
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 从自然语言中提取范围值（用于between操作）
     *
     * <p>支持的表达方式：</p>
     * <ul>
     *   <li>"在3-20之间" → ["3", "20"]</li>
     *   <li>"3到20" → ["3", "20"]</li>
     *   <li>"在3和20之间" → ["3", "20"]</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return 范围数组 [最小值, 最大值]
     */
    private String[] extractRange(String text) {
        // 模式1: "3-20" 或 "3~20"
        Pattern pattern1 = Pattern.compile("(\\d+)[-~](\\d+)");
        Matcher matcher1 = pattern1.matcher(text);
        if (matcher1.find()) {
            return new String[]{matcher1.group(1), matcher1.group(2)};
        }

        // 模式2: "在3和20之间" 或 "3到20"
        Pattern pattern2 = Pattern.compile("(\\d+)[和到](\\d+)");
        Matcher matcher2 = pattern2.matcher(text);
        if (matcher2.find()) {
            return new String[]{matcher2.group(1), matcher2.group(2)};
        }

        // 提取所有数字，取前两个
        Pattern numberPattern = Pattern.compile("(\\d+)");
        Matcher numberMatcher = numberPattern.matcher(text);
        String first = "0", second = "100";
        if (numberMatcher.find()) {
            first = numberMatcher.group(1);
        }
        if (numberMatcher.find()) {
            second = numberMatcher.group(1);
        }

        log.warn("[RulePatternMatcher] ⚠️ 范围提取使用默认值: text={}, range=[{}, {}]",
                text, first, second);
        return new String[]{first, second};
    }

    /**
     * 从自然语言中提取正则表达式（用于格式验证）
     *
     * <p>支持的表达方式：</p>
     * <ul>
     *   <li>"匹配正则表达式^[a-z]+$" → "^[a-z]+$"</li>
     *   <li>"验证email字段匹配..." → 尝试提取正则</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return 正则表达式字符串
     */
    private String extractRegex(String text) {
        // 尝试提取常见的正则表达式模式
        // 正则表达式通常以^开头，以$结尾
        Pattern regexPattern = Pattern.compile("(\\^[^\\s]+\\$)");
        Matcher matcher = regexPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 尝试提取引号中的内容（可能是正则）
        Pattern quotedPattern = Pattern.compile("[\"`]([^\"`]+)[\"`]");
        Matcher quotedMatcher = quotedPattern.matcher(text);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1);
        }

        // 常见格式预设
        if (text.contains("email") || text.contains("邮箱")) {
            return "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}$";
        }
        if (text.contains("手机") || text.contains("电话")) {
            return "^1[3-9]\\\\d{9}$";
        }
        if (text.contains("身份证")) {
            return "^[1-9]\\\\d{5}(18|19|20)\\\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\\\d|3[01])\\\\d{3}[\\\\dXx]$";
        }

        log.warn("[RulePatternMatcher] ⚠️ 无法提取正则表达式，使用默认模式: text={}", text);
        return ".*";  // 默认匹配任意字符
    }

    // ========== CALCULATION规则工具方法（Phase 4.2.3 实现） ==========

    /**
     * 从自然语言中提取目标字段（计算结果字段）
     *
     * <p>提取模式示例：</p>
     * <ul>
     *   <li>"totalPrice = ..." → "totalPrice"</li>
     *   <li>"计算订单总价" → "totalPrice"（需要推理）</li>
     *   <li>"订单总价应该等于..." → "totalPrice"</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return 目标字段名
     * @since Phase 4.2.3
     */
    private String extractTargetField(String text) {
        if (text == null || text.isEmpty()) {
            log.warn("[RulePatternMatcher] ⚠️ 文本为空，无法提取目标字段");
            return "calculatedField";
        }

        // 模式1: "{目标字段} = ..." - 最精确（匹配等号左边）
        Pattern pattern1 = Pattern.compile("(\\w+)\\s*=");
        Matcher matcher1 = pattern1.matcher(text);
        if (matcher1.find()) {
            String field = matcher1.group(1);
            log.debug("[RulePatternMatcher] 从等号左边提取目标字段: field={}", field);
            return field;
        }

        // 模式2: "计算{目标字段}" - 计算场景
        Pattern pattern2 = Pattern.compile("计算(\\w+)");
        Matcher matcher2 = pattern2.matcher(text);
        if (matcher2.find()) {
            String field = matcher2.group(1);
            log.debug("[RulePatternMatcher] 从计算关键词提取目标字段: field={}", field);
            return field;
        }

        // 模式3: "{目标字段}应该等于" - 自然语言描述
        Pattern pattern3 = Pattern.compile("(\\w+)应该等于");
        Matcher matcher3 = pattern3.matcher(text);
        if (matcher3.find()) {
            String field = matcher3.group(1);
            log.debug("[RulePatternMatcher] 从\u2018应该等于\u2018关键词提取目标字段: field={}", field);
            return field;
        }

        // 模式4: 从常见关键词推理
        if (text.contains("总价") || text.contains("总金额")) {
            return "totalPrice";
        }
        if (text.contains("总和") || text.contains("合计")) {
            return "totalAmount";
        }
        if (text.contains("平均值") || text.contains("均值")) {
            return "averageValue";
        }
        if (text.contains("数量") || text.contains("计数")) {
            return "count";
        }

        log.warn("[RulePatternMatcher] ⚠️ 无法从文本中提取目标字段: text={}", text);
        return "calculatedField";  // 默认值
    }

    /**
     * 从自然语言中提取源字段（集合字段）
     *
     * <p>提取模式示例：</p>
     * <ul>
     *   <li>"Σ(orderItems...)" → "orderItems"</li>
     *   <li>"订单明细的总和" → "orderItems"</li>
     *   <li>"商品列表" → "productList"</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return 源集合字段名
     * @since Phase 4.2.3
     */
    private String extractSourceField(String text) {
        if (text == null || text.isEmpty()) {
            log.warn("[RulePatternMatcher] ⚠️ 文本为空，无法提取源字段");
            return "items";
        }

        // 模式1: "Σ({源字段}...)" - 数学符号表示
        Pattern pattern1 = Pattern.compile("Σ\\((\\w+)");
        Matcher matcher1 = pattern1.matcher(text);
        if (matcher1.find()) {
            String field = matcher1.group(1);
            log.debug("[RulePatternMatcher] 从Σ符号提取源字段: field={}", field);
            return field;
        }

        // 模式2: "{源字段}.stream()" - Java Stream语法
        Pattern pattern2 = Pattern.compile("(\\w+)\\.stream\\(\\)");
        Matcher matcher2 = pattern2.matcher(text);
        if (matcher2.find()) {
            String field = matcher2.group(1);
            log.debug("[RulePatternMatcher] 从.stream()提取源字段: field={}", field);
            return field;
        }

        // 模式3: "{源字段}的..." - 自然语言描述
        Pattern pattern3 = Pattern.compile("(\\w+Items|\\w+List|\\w+明细)");
        Matcher matcher3 = pattern3.matcher(text);
        if (matcher3.find()) {
            String field = matcher3.group(1);
            log.debug("[RulePatternMatcher] 从集合关键词提取源字段: field={}", field);
            return field;
        }

        // 默认推理
        if (text.contains("订单明细") || text.contains("订单项")) {
            return "orderItems";
        }
        if (text.contains("商品") || text.contains("产品")) {
            return "productList";
        }

        log.warn("[RulePatternMatcher] ⚠️ 无法从文本中提取源字段: text={}", text);
        return "items";  // 默认值
    }

    /**
     * 从自然语言中识别聚合类型
     *
     * <p>支持的聚合类型：</p>
     * <ul>
     *   <li>sum: 求和、总和、合计、Σ</li>
     *   <li>avg: 平均、均值</li>
     *   <li>max: 最大、最高</li>
     *   <li>min: 最小、最低</li>
     *   <li>count: 计数、数量</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return 聚合类型（sum/avg/max/min/count）
     * @since Phase 4.2.3
     */
    private String extractAggregationType(String text) {
        if (text == null || text.isEmpty()) {
            log.warn("[RulePatternMatcher] ⚠️ 文本为空，无法识别聚合类型");
            return "sum";
        }

        // 优先级从高到低
        // 1. 求和（最常见）
        if (text.matches(".*求和.*|.*总和.*|.*合计.*|.*Σ.*|.*sum.*")) {
            log.debug("[RulePatternMatcher] 识别为SUM聚合");
            return "sum";
        }

        // 2. 平均值
        if (text.matches(".*平均.*|.*均值.*|.*avg.*|.*average.*")) {
            log.debug("[RulePatternMatcher] 识别为AVG聚合");
            return "avg";
        }

        // 3. 最大值
        if (text.matches(".*最大.*|.*最高.*|.*max.*|.*maximum.*")) {
            log.debug("[RulePatternMatcher] 识别为MAX聚合");
            return "max";
        }

        // 4. 最小值
        if (text.matches(".*最小.*|.*最低.*|.*min.*|.*minimum.*")) {
            log.debug("[RulePatternMatcher] 识别为MIN聚合");
            return "min";
        }

        // 5. 计数
        if (text.matches(".*计数.*|.*数量.*|.*个数.*|.*count.*")) {
            log.debug("[RulePatternMatcher] 识别为COUNT聚合");
            return "count";
        }

        log.warn("[RulePatternMatcher] ⚠️ 无法识别聚合类型，默认使用sum: text={}", text);
        return "sum";  // 默认求和
    }

    /**
     * 从自然语言中提取值表达式（用于聚合函数）
     *
     * <p>提取模式示例：</p>
     * <ul>
     *   <li>"Σ(商品单价 * 数量)" → "item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))"</li>
     *   <li>"每个商品的价格" → "item.getPrice()"</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return Java表达式字符串
     * @since Phase 4.2.3
     */
    private String extractValueExpression(String text) {
        if (text == null || text.isEmpty()) {
            log.warn("[RulePatternMatcher] ⚠️ 文本为空，无法提取值表达式");
            return "item.getValue()";
        }

        // 模式1: "Σ(...)" 括号内的内容
        Pattern pattern1 = Pattern.compile("Σ\\(([^)]+)\\)");
        Matcher matcher1 = pattern1.matcher(text);
        if (matcher1.find()) {
            String expression = matcher1.group(1);
            log.debug("[RulePatternMatcher] 从Σ括号提取值表达式: expression={}", expression);
            return convertToJavaExpression(expression);
        }

        // 模式2: "单价 * 数量" - 直接的数学表达式
        if (text.contains("*") || text.contains("×")) {
            if (text.contains("单价") && text.contains("数量")) {
                log.debug("[RulePatternMatcher] 识别为单价*数量表达式");
                return "item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))";
            }
            if (text.contains("价格") && text.contains("数量")) {
                log.debug("[RulePatternMatcher] 识别为价格*数量表达式");
                return "item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))";
            }
        }

        // 模式3: 简单字段引用
        if (text.contains("价格") || text.contains("单价")) {
            return "item.getPrice()";
        }
        if (text.contains("金额") || text.contains("总额")) {
            return "item.getAmount()";
        }

        log.warn("[RulePatternMatcher] ⚠️ 无法提取值表达式，使用默认: text={}", text);
        return "item.getValue()";  // 默认值
    }

    /**
     * 将自然语言表达式转换为Java表达式
     *
     * <p>转换规则：</p>
     * <ul>
     *   <li>"单价 * 数量" → "item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))"</li>
     *   <li>"价格" → "item.getPrice()"</li>
     * </ul>
     *
     * @param expression 自然语言表达式
     * @return Java表达式
     * @since Phase 4.2.3
     */
    private String convertToJavaExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            return "item.getValue()";
        }

        // 处理乘法表达式
        if (expression.contains("*") || expression.contains("×")) {
            if (expression.contains("单价") && expression.contains("数量")) {
                return "item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))";
            }
            if (expression.contains("价格") && expression.contains("数量")) {
                return "item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))";
            }
        }

        // 简单字段映射
        if (expression.contains("单价")) {
            return "item.getUnitPrice()";
        }
        if (expression.contains("价格")) {
            return "item.getPrice()";
        }
        if (expression.contains("数量")) {
            return "BigDecimal.valueOf(item.getQuantity())";
        }
        if (expression.contains("金额")) {
            return "item.getAmount()";
        }

        return "item.getValue()";
    }

    /**
     * 从自然语言中提取算术运算符
     *
     * <p>支持的运算符：+、-、*、/</p>
     *
     * @param text 自然语言文本
     * @return 运算符(+, -, *, /)
     * @since Phase 4.2.3
     */
    private String extractArithmeticOperator(String text) {
        if (text == null || text.isEmpty()) {
            log.warn("[RulePatternMatcher] ⚠️ 文本为空，无法提取运算符");
            return "+";
        }

        // 符号优先（最精确）
        if (text.contains("*") || text.contains("×")) return "*";
        if (text.contains("/") || text.contains("÷")) return "/";
        if (text.contains("+")) return "+";
        if (text.contains("-")) return "-";

        // 文字表示
        if (text.contains("乘以") || text.contains("乘")) return "*";
        if (text.contains("除以") || text.contains("除")) return "/";
        if (text.contains("加上") || text.contains("加")) return "+";
        if (text.contains("减去") || text.contains("减")) return "-";

        log.warn("[RulePatternMatcher] ⚠️ 无法提取运算符，默认使用+: text={}", text);
        return "+";  // 默认加法
    }

    /**
     * 推断数据类型（用于选择合适的计算方法）
     *
     * <p>推断规则：</p>
     * <ul>
     *   <li>金额、价格、总额 → BigDecimal</li>
     *   <li>数量、计数 → Integer</li>
     *   <li>百分比、比率 → Double</li>
     * </ul>
     *
     * @param text 自然语言文本
     * @return 数据类型（BigDecimal/Integer/Double）
     * @since Phase 4.2.3
     */
    private String inferDataType(String text) {
        if (text == null || text.isEmpty()) {
            log.warn("[RulePatternMatcher] ⚠️ 文本为空，无法推断数据类型");
            return "BigDecimal";
        }

        // 优先级从高到低
        // 1. BigDecimal：金额、价格相关（需要精确计算）
        if (text.matches(".*金额.*|.*价格.*|.*总额.*|.*费用.*|.*单价.*|.*总价.*")) {
            log.debug("[RulePatternMatcher] 推断数据类型为BigDecimal");
            return "BigDecimal";
        }

        // 2. Integer：整数数量
        if (text.matches(".*数量.*|.*个数.*|.*计数.*|.*count.*")) {
            log.debug("[RulePatternMatcher] 推断数据类型为Integer");
            return "Integer";
        }

        // 3. Double：小数、百分比
        if (text.matches(".*百分比.*|.*比率.*|.*平均.*|.*percent.*|.*rate.*")) {
            log.debug("[RulePatternMatcher] 推断数据类型为Double");
            return "Double";
        }

        log.debug("[RulePatternMatcher] 默认数据类型为BigDecimal");
        return "BigDecimal";  // 默认使用BigDecimal（最安全）
    }

    // ========== matchCalculationPattern() 主方法（Phase 4.2.3 核心）==========

    /**
     * 匹配CALCULATION规则模式（V2.0 Phase 4.2.3）
     *
     * <p>支持的4种计算模式：</p>
     * <ol>
     *   <li>ARITHMETIC: 简单算术计算（totalPrice = unitPrice × quantity）</li>
     *   <li>AGGREGATION: 聚合函数（sum、avg、max、min、count）</li>
     *   <li>CONDITIONAL: 条件计算（if-then-else逻辑）</li>
     *   <li>FORMULA: 公式表达式（复杂数学公式）</li>
     * </ol>
     *
     * <p>输入示例：</p>
     * <pre>{@code
     * logic = "订单总价 = Σ(商品单价 * 数量)"
     * }</pre>
     *
     * <p>输出参数Map：</p>
     * <pre>{@code
     * {
     *   "patternType": "AGGREGATION",
     *   "targetField": "totalPrice",
     *   "sourceField": "orderItems",
     *   "aggregationType": "sum",
     *   "valueExpression": "item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()))",
     *   "dataType": "BigDecimal"
     * }
     * }</pre>
     *
     * @param logic 自然语言业务逻辑描述
     * @return 模板参数Map
     * @since Phase 4.2.3
     */
    public Map<String, Object> matchCalculationPattern(String logic) {
        log.debug("[RulePatternMatcher] 开始匹配CALCULATION规则模式: logic={}", logic);

        Map<String, Object> params = new HashMap<>();

        // Step 1: 识别模式类型（按优先级）
        String patternType = identifyCalculationPattern(logic);
        params.put("patternType", patternType);
        log.debug("[RulePatternMatcher] 识别为计算模式: patternType={}", patternType);

        // Step 2: 提取通用参数（所有模式都需要）
        String targetField = extractTargetField(logic);
        params.put("targetField", targetField);
        params.put("dataType", inferDataType(logic));

        // Step 3: 根据模式类型提取特定参数
        switch (patternType) {
            case "AGGREGATION":
                // 聚合函数：sum/avg/max/min/count
                params.put("sourceField", extractSourceField(logic));
                params.put("aggregationType", extractAggregationType(logic));
                params.put("valueExpression", extractValueExpression(logic));
                log.debug("[RulePatternMatcher] AGGREGATION参数: sourceField={}, aggregationType={}, valueExpression={}",
                        params.get("sourceField"), params.get("aggregationType"), params.get("valueExpression"));
                break;

            case "ARITHMETIC":
                // 简单算术计算：a = b op c
                params.put("operator", extractArithmeticOperator(logic));
                // 提取左右操作数（简化处理：假设格式为"A = B op C"）
                params.put("leftOperand", "leftValue");  // TODO: 需要更精细的解析
                params.put("rightOperand", "rightValue");
                log.debug("[RulePatternMatcher] ARITHMETIC参数: operator={}", params.get("operator"));
                break;

            case "CONDITIONAL":
                // 条件计算：if-then-else
                params.put("condition", "condition");  // TODO: 提取条件表达式
                params.put("trueValue", "trueValue");
                params.put("falseValue", "falseValue");
                log.debug("[RulePatternMatcher] CONDITIONAL参数（待完善）");
                break;

            case "FORMULA":
                // 复杂公式表达式
                params.put("formula", logic);  // 原始公式
                params.put("formulaExpression", "BigDecimal.ZERO");  // TODO: 解析复杂表达式
                log.debug("[RulePatternMatcher] FORMULA参数: formula={}", logic);
                break;

            default:
                log.warn("[RulePatternMatcher] ⚠️ 未知的CALCULATION模式: patternType={}", patternType);
                break;
        }

        log.debug("[RulePatternMatcher] CALCULATION规则模式匹配完成: patternType={}, targetField={}, params={}",
                patternType, targetField, params);
        return params;
    }

    /**
     * 识别CALCULATION规则的模式类型
     *
     * <p>模式识别优先级：</p>
     * <ol>
     *   <li>AGGREGATION: 包含Σ、求和、平均、最大、最小等聚合关键词</li>
     *   <li>CONDITIONAL: 包含if-then-else、如果-则-否则等条件逻辑</li>
     *   <li>FORMULA: 包含复杂数学表达式（多个运算符）</li>
     *   <li>ARITHMETIC: 简单算术运算（单个运算符）</li>
     * </ol>
     *
     * @param logic 自然语言文本
     * @return 模式类型（AGGREGATION/ARITHMETIC/CONDITIONAL/FORMULA）
     * @since Phase 4.2.3
     */
    private String identifyCalculationPattern(String logic) {
        if (logic == null || logic.isEmpty()) {
            log.warn("[RulePatternMatcher] ⚠️ 文本为空，默认使用ARITHMETIC模式");
            return "ARITHMETIC";
        }

        // 优先级1: AGGREGATION（聚合函数）- 最精确
        if (logic.matches(".*Σ.*|.*求和.*|.*总和.*|.*合计.*|.*平均.*|.*均值.*|.*最大.*|.*最高.*|.*最小.*|.*最低.*|.*计数.*|.*count.*|.*sum.*|.*avg.*|.*max.*|.*min.*")) {
            log.debug("[RulePatternMatcher] 识别为AGGREGATION模式");
            return "AGGREGATION";
        }

        // 优先级2: CONDITIONAL（条件计算）
        if (logic.matches(".*如果.*则.*否则.*|.*when.*then.*else.*|.*if.*then.*else.*|.*\\?.*:.*")) {
            log.debug("[RulePatternMatcher] 识别为CONDITIONAL模式");
            return "CONDITIONAL";
        }

        // 优先级3: FORMULA（复杂公式）- 包含多个运算符或括号
        long operatorCount = logic.chars().filter(ch -> ch == '+' || ch == '-' || ch == '*' || ch == '/').count();
        if (operatorCount >= 2 || (logic.contains("(") && logic.contains(")"))) {
            log.debug("[RulePatternMatcher] 识别为FORMULA模式（多个运算符或包含括号）");
            return "FORMULA";
        }

        // 优先级4: ARITHMETIC（简单算术）- 包含单个运算符
        if (logic.matches(".*[+\\-*/×÷].*|.*乘以.*|.*除以.*|.*加上.*|.*减去.*")) {
            log.debug("[RulePatternMatcher] 识别为ARITHMETIC模式");
            return "ARITHMETIC";
        }

        // 默认
        log.debug("[RulePatternMatcher] 默认使用ARITHMETIC模式");
        return "ARITHMETIC";
    }

}
