package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SuperDesign设计请求
 *
 * 用于调用SuperDesign API生成UI设计方案
 */
@Data
@Builder
public class DesignRequest {

    /**
     * 任务ID
     */
    private UUID taskId;

    /**
     * 用户原始需求描述
     */
    private String userPrompt;

    /**
     * 实体列表（用于生成对应的UI界面）
     */
    private List<EntityInfo> entities;

    /**
     * 目标平台
     * android / ios / web
     */
    @Builder.Default
    private String targetPlatform = "android";

    /**
     * UI框架
     * compose_multiplatform / react_native / flutter
     */
    @Builder.Default
    private String uiFramework = "compose_multiplatform";

    /**
     * 色彩方案偏好
     * light / dark / auto
     */
    @Builder.Default
    private String colorScheme = "light";

    /**
     * 是否包含资源文件
     * 图标、图片等
     */
    @Builder.Default
    private Boolean includeAssets = true;

    /**
     * 额外的设计约束
     */
    private Map<String, Object> constraints;

    /**
     * 实体信息
     */
    @Data
    @Builder
    public static class EntityInfo {
        /**
         * 实体名称
         */
        private String name;

        /**
         * 显示名称
         */
        private String displayName;

        /**
         * 主要字段列表
         */
        private List<String> primaryFields;

        /**
         * 实体类型
         * list / detail / form / dashboard
         */
        private String viewType;
    }
}
