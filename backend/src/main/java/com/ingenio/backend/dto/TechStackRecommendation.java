package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 技术栈推荐结果
 *
 * 根据需求特征自动推荐最优技术选型
 */
@Data
@Builder
public class TechStackRecommendation {

    /**
     * 目标平台
     * 示例: "Kotlin Multiplatform", "React Native", "Flutter"
     */
    private String platform;

    /**
     * UI框架
     * 示例: "Compose Multiplatform", "React", "SwiftUI"
     */
    private String uiFramework;

    /**
     * 后端方案
     * 示例: "Supabase", "Firebase", "自建Spring Boot"
     */
    private String backend;

    /**
     * 数据库
     * 示例: "PostgreSQL", "MySQL", "MongoDB"
     */
    private String database;

    /**
     * 认证方案
     * 示例: "Supabase Auth", "Firebase Auth", "OAuth2"
     */
    private String auth;

    /**
     * 存储方案
     * 示例: "Supabase Storage", "MinIO", "AWS S3"
     */
    private String storage;

    /**
     * 推荐理由
     */
    private String reason;

    /**
     * 置信度 (0-1)
     */
    @Builder.Default
    private Double confidence = 0.85;

    /**
     * 是否需要实时功能
     */
    @Builder.Default
    private Boolean needsRealtime = false;

    /**
     * 是否需要离线支持
     */
    @Builder.Default
    private Boolean needsOffline = false;
}
