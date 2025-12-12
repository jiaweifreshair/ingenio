package com.ingenio.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 意图识别请求DTO
 * 用于V2.0新三Agent架构中Plan阶段的意图识别
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-16 (Phase X.3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentClassifyRequest {

    /**
     * 用户原始需求描述
     * 必填，将被IntentClassifier分析以识别意图类型
     *
     * 示例：
     * - "创建一个类似airbnb的民宿预订平台"
     * - "我想做一个在线教育平台，参考网易云课堂，但是要添加AI助教功能"
     * - "开发一个电商应用，功能包括商品展示、购物车、支付"
     */
    @NotBlank(message = "用户需求不能为空")
    private String userRequirement;

    /**
     * 是否包含调试信息
     * 可选，默认false
     * 为true时返回promptUsed和rawResponse字段（用于调试）
     */
    private Boolean includeDebugInfo;

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
