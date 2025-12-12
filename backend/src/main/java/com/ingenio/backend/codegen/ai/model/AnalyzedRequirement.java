package com.ingenio.backend.codegen.ai.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AI分析后的结构化需求文档（V2.0 Phase 4.1）
 *
 * <p>将用户的自然语言需求转换为结构化的数据模型</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>实体定义：包含字段、类型、约束</li>
 *   <li>业务规则：验证规则、计算规则、工作流规则</li>
 *   <li>关系定义：一对一、一对多、多对多</li>
 *   <li>约束条件：唯一性、非空、范围约束</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 用户需求：我要做一个用户管理系统，包括注册、登录、密码重置功能
 * String userRequirement = "我要做一个用户管理系统...";
 * AnalyzedRequirement requirement = requirementAnalyzer.analyze(userRequirement);
 *
 * // 输出结构化需求
 * System.out.println("领域: " + requirement.getDomain());
 * System.out.println("实体数量: " + requirement.getEntities().size());
 * System.out.println("业务规则数量: " + requirement.getBusinessRules().size());
 * }</pre>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 4.1: AI需求理解服务
 */
@Data
@Builder
public class AnalyzedRequirement {

    /**
     * 业务领域
     * 示例：用户管理、订单管理、商品管理
     */
    private String domain;

    /**
     * 领域描述
     * 示例：管理系统用户的注册、登录、权限等功能
     */
    private String description;

    /**
     * 实体列表
     * 包含系统中的所有业务实体（如User、Order、Product）
     */
    private List<EntityRequirement> entities;

    /**
     * 业务规则列表
     * 包含验证规则、计算规则、工作流规则
     */
    private List<BusinessRule> businessRules;

    /**
     * 实体关系列表
     * 定义实体之间的关联关系（一对一、一对多、多对多）
     */
    private List<Relationship> relationships;

    /**
     * 约束条件列表
     * 包含唯一性约束、非空约束、范围约束等
     */
    private List<Constraint> constraints;

    /**
     * 需求来源
     * 示例：用户输入、需求文档、API规范
     */
    private String source;

    /**
     * AI分析的置信度（0-1）
     * 0.9以上表示高置信度，0.7-0.9表示中等置信度，低于0.7需要人工确认
     */
    private Double confidence;

    /**
     * AI分析的推理过程
     * 记录AI如何理解需求的详细过程
     */
    private String reasoning;
}
