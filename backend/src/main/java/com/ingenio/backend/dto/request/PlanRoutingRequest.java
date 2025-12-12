package com.ingenio.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Plan阶段路由请求DTO
 *
 * 包含用户需求和上下文信息，用于意图识别和路由决策
 *
 * V2.0增强：支持用户预选复杂度分类，优化AI分析准确性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanRoutingRequest {

    /**
     * 用户需求描述
     * 自然语言描述，包含功能需求、参考网站等
     */
    @NotBlank(message = "用户需求不能为空")
    private String userRequirement;

    /**
     * 租户ID（可选）
     * 多租户隔离，如果不提供则从Session获取
     */
    private UUID tenantId;

    /**
     * 用户ID（可选）
     * 创建者标识，如果不提供则从Session获取
     */
    private UUID userId;

    /**
     * 用户预选的复杂度分类（可选）
     *
     * V2.0新增：用户从首页4种分类中选择后传入
     * - SIMPLE: 多端套壳应用 → H5+WebView
     * - MEDIUM: 纯Web应用 → React + Supabase
     * - COMPLEX: 企业级应用 → React + Spring Boot
     * - NEEDS_CONFIRMATION: 原生功能应用 → Kuikly
     *
     * 如果提供，AI分析会优先考虑该分类对应的技术栈
     * 如果不提供，AI会完全基于需求内容自动判断
     */
    private String complexityHint;

    /**
     * 用户预选的技术栈提示（可选）
     *
     * V2.0新增：用户从首页选择后传入的技术栈
     * - "H5+WebView"
     * - "React+Supabase"
     * - "React+SpringBoot"
     * - "Kuikly"
     *
     * 如果提供，AI分析会优先使用该技术栈
     */
    private String techStackHint;
}
