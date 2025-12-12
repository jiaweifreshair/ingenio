package com.ingenio.backend.codegen.ai.model;

import lombok.Builder;
import lombok.Data;

/**
 * 业务规则定义（V2.0 Phase 4.1）
 *
 * <p>描述一个业务规则，用于生成业务逻辑代码</p>
 *
 * <p>业务规则类型：</p>
 * <ul>
 *   <li>VALIDATION：验证规则（邮箱格式、密码强度、年龄限制）</li>
 *   <li>CALCULATION：计算规则（订单总价、积分计算、优惠金额）</li>
 *   <li>WORKFLOW：工作流规则（订单状态流转、审批流程）</li>
 *   <li>NOTIFICATION：通知规则（发送邮件、短信、推送）</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * BusinessRule ageValidation = BusinessRule.builder()
 *     .name("validateAge")
 *     .description("验证用户年龄必须≥18岁")
 *     .type(BusinessRuleType.VALIDATION)
 *     .entity("User")
 *     .method("register")
 *     .logic("检查age字段，如果小于18则抛出BusinessException")
 *     .priority(10)
 *     .build();
 * }</pre>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 4.1: AI需求理解服务
 */
@Data
@Builder
public class BusinessRule {

    /**
     * 规则名称（camelCase）
     * 示例：validateEmailFormat、calculateTotalPrice
     */
    private String name;

    /**
     * 规则描述
     * 示例：验证邮箱格式是否合法
     */
    private String description;

    /**
     * 规则类型
     * VALIDATION、CALCULATION、WORKFLOW、NOTIFICATION
     */
    private BusinessRuleType type;

    /**
     * 关联的实体名称
     * 示例：User、Order、Product
     */
    private String entity;

    /**
     * 关联的方法名称
     * 示例：register、createOrder、updateProfile
     */
    private String method;

    /**
     * 业务逻辑描述（自然语言）
     * 示例：检查邮箱是否匹配正则表达式^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$
     */
    private String logic;

    /**
     * 优先级（1-10）
     * 数字越大优先级越高，优先级高的规则先执行
     */
    private Integer priority;

    /**
     * 业务规则类型枚举
     */
    public enum BusinessRuleType {
        /**
         * 验证规则：用于数据校验
         * 示例：邮箱格式验证、密码强度验证、年龄限制
         */
        VALIDATION,

        /**
         * 计算规则：用于业务计算
         * 示例：订单总价计算、会员等级计算、积分计算
         */
        CALCULATION,

        /**
         * 工作流规则：用于状态流转
         * 示例：订单状态流转、审批流程、退款流程
         */
        WORKFLOW,

        /**
         * 通知规则：用于消息通知
         * 示例：发送欢迎邮件、发送验证码、推送消息
         */
        NOTIFICATION
    }
}
