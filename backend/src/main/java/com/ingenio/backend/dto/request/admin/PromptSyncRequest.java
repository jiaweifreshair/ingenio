package com.ingenio.backend.dto.request.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API - Prompt同步请求DTO
 *
 * 用途：接收 JeecgBoot 推送的 Prompt 数据
 *
 * 同步动作：
 * - publish: 发布 Prompt（新增或更新）
 * - unpublish: 下架 Prompt
 * - update: 更新 Prompt 内容
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptSyncRequest {

    /**
     * 同步动作
     * - publish: 发布 Prompt（新增或更新）
     * - unpublish: 下架 Prompt
     * - update: 更新 Prompt 内容
     */
    @NotBlank(message = "action 不能为空")
    private String action;

    /**
     * Prompt 数据
     */
    @NotNull(message = "prompt 不能为空")
    @Valid
    private PromptData prompt;

    /**
     * 操作人ID（来自 JeecgBoot）
     */
    private String operator;

    /**
     * 租户ID（可选）
     */
    private String tenantId;

    /**
     * Prompt 数据结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptData {

        /**
         * Prompt ID（JeecgBoot 侧的ID，UUID格式）
         * 首次同步时可不传，系统会自动生成
         */
        private UUID id;

        /**
         * Prompt 标题
         */
        @NotBlank(message = "title 不能为空")
        private String title;

        /**
         * Prompt 描述
         */
        private String description;

        /**
         * 封面图片URL
         */
        private String coverImageUrl;

        /**
         * Prompt 模板内容
         * 包含变量占位符，如 "创建一个{subject}学习{tool_type}"
         */
        @NotBlank(message = "promptTemplate 不能为空")
        private String promptTemplate;

        /**
         * 预填充变量（JSON）
         * 例如：{ "subject": "数学", "tool_type": "工具" }
         */
        private Map<String, Object> defaultVariables;

        /**
         * 年龄分组
         * 可选值：elementary/middle_school/high_school/university
         */
        private String ageGroup;

        /**
         * 分类
         * 可选值：education/game/productivity/creative/social
         */
        private String category;

        /**
         * 难度级别
         * 可选值：beginner/intermediate/advanced
         */
        private String difficultyLevel;

        /**
         * 示例项目ID（可选）
         */
        private UUID exampleProjectId;

        /**
         * 标签数组
         */
        private List<String> tags;

        /**
         * 元数据（扩展字段）
         */
        private Map<String, Object> metadata;

        /**
         * 创建者用户ID
         */
        private UUID createdByUserId;
    }
}
