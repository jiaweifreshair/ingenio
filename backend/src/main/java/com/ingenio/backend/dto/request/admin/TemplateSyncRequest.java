package com.ingenio.backend.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API - 模板同步请求 DTO
 *
 * 用途：接收 JeecgBoot 推送的模板数据
 *
 * 同步场景：
 * 1. 模板发布（action=publish）
 * 2. 模板下线（action=unpublish）
 * 3. 模板更新（action=update）
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateSyncRequest {

    /**
     * 同步动作
     *
     * 枚举值：
     * - publish: 发布模板（创建或更新）
     * - unpublish: 下线模板（软删除）
     * - update: 仅更新模板内容
     */
    @NotBlank(message = "action 不能为空")
    private String action;

    /**
     * 模板数据
     */
    @NotNull(message = "template 不能为空")
    private TemplateData template;

    /**
     * 操作人（来自 JeecgBoot）
     */
    private String operator;

    /**
     * 租户ID（多租户场景）
     */
    private String tenantId;

    /**
     * 模板数据内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateData {

        /**
         * 模板ID（UUID）
         *
         * 由 JeecgBoot 生成并管理
         */
        private UUID id;

        /**
         * 模板名称
         */
        @NotBlank(message = "模板名称不能为空")
        private String name;

        /**
         * 模板描述
         */
        private String description;

        /**
         * 一级分类
         */
        private String category;

        /**
         * 二级分类
         */
        private String subcategory;

        /**
         * 关键词列表（用于匹配算法）
         */
        private List<String> keywords;

        /**
         * 参考网站URL
         */
        private String referenceUrl;

        /**
         * 缩略图URL
         */
        private String thumbnailUrl;

        /**
         * 预定义实体列表
         */
        private List<Map<String, Object>> entities;

        /**
         * 核心功能清单
         */
        private List<String> features;

        /**
         * 业务流程定义
         */
        private List<Map<String, Object>> workflows;

        /**
         * Blueprint 规范（核心约束）
         */
        private Map<String, Object> blueprintSpec;

        /**
         * 技术栈建议
         */
        private Map<String, List<String>> techStack;

        /**
         * 复杂度评分（1-10）
         */
        private Integer complexityScore;

        /**
         * 预估开发工时
         */
        private Integer estimatedHours;
    }

    /**
     * 验证 action 是否合法
     */
    public boolean isValidAction() {
        return "publish".equalsIgnoreCase(action)
            || "unpublish".equalsIgnoreCase(action)
            || "update".equalsIgnoreCase(action);
    }
}
