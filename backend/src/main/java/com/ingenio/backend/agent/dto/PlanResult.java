package com.ingenio.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * PlanAgent输出结果DTO
 * 包含规划的功能模块列表、复杂度评估等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResult {

    /**
     * 功能模块列表
     */
    private List<FunctionModule> modules;

    /**
     * 总体复杂度评估（1-10分）
     */
    private Integer complexityScore;

    /**
     * 推理过程
     */
    private String reasoning;

    /**
     * 建议的技术栈
     */
    private List<String> suggestedTechStack;

    /**
     * 预估开发时间（小时）
     */
    private Integer estimatedHours;

    /**
     * 额外建议
     */
    private String recommendations;

    /**
     * AI能力需求（新增）
     * 识别应用是否需要AI能力，以及需要哪些AI能力
     */
    private AICapabilityRequirement aiCapability;

    /**
     * V2.0意图识别结果（新增）
     * Plan阶段第一步的意图识别结果，用于智能路由到不同分支
     */
    private IntentClassificationResult intentClassificationResult;

    /**
     * V2.0设计规范（AI生成的设计系统）
     * 当用户选择风格后，从AppSpec.metadata提取并填充此字段
     * 包含: colorTheme, typography, layout, components等设计约束
     */
    private Map<String, Object> designSpec;

    /**
     * V2.0前端原型（OpenLovable生成）
     * 包含: sandboxId, previewUrl, provider, generatedAt等信息
     * 用于ExecuteAgent直接使用已生成的原型，避免重复生成
     */
    private Map<String, Object> frontendPrototype;

    /**
     * V2.0详细分析上下文（来自SSE分析流）
     * 包含: entities, relationships, operations, techStack等结构化数据
     * 用于提升代码生成的精准度
     */
    private Map<String, Object> analysisContext;

    /**
     * 功能模块
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionModule {

        /**
         * 模块名称
         */
        private String name;

        /**
         * 模块描述
         */
        private String description;

        /**
         * 优先级（high/medium/low）
         */
        private String priority;

        /**
         * 模块复杂度（1-10）
         */
        private Integer complexity;

        /**
         * 依赖的其他模块
         */
        private List<String> dependencies;

        /**
         * 需要的数据模型
         */
        private List<String> dataModels;

        /**
         * 需要的页面
         */
        private List<String> pages;
    }
}
