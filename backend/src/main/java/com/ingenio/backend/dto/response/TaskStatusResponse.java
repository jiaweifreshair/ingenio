package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 任务状态响应DTO
 * 返回生成任务的详细状态信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusResponse {

    /**
     * 任务ID
     */
    private UUID taskId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 用户原始需求
     */
    private String userRequirement;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDescription;

    /**
     * 当前执行的Agent
     */
    private String currentAgent;

    /**
     * 任务进度（0-100）
     */
    private Integer progress;

    /**
     * Agent状态列表
     */
    private List<AgentStatusInfo> agents;

    /**
     * 任务开始时间
     */
    private Instant startedAt;

    /**
     * 任务完成时间
     */
    private Instant completedAt;

    /**
     * 预估剩余时间（秒）
     */
    private Long estimatedRemainingSeconds;

    /**
     * AppSpec ID（如果已完成）
     */
    private UUID appSpecId;

    /**
     * 质量评分（0-100）
     */
    private Integer qualityScore;

    /**
     * 代码下载链接
     */
    private String downloadUrl;

    /**
     * 预览链接
     */
    private String previewUrl;

    /**
     * Token使用统计
     */
    private TokenUsageInfo tokenUsage;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    /**
     * Agent状态信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentStatusInfo {

        /**
         * Agent类型
         */
        private String agentType;

        /**
         * Agent名称
         */
        private String agentName;

        /**
         * Agent状态
         */
        private String status;

        /**
         * 状态描述
         */
        private String statusDescription;

        /**
         * 开始时间
         */
        private Instant startedAt;

        /**
         * 完成时间
         */
        private Instant completedAt;

        /**
         * 执行时长（毫秒）
         */
        private Long durationMs;

        /**
         * 进度（0-100）
         */
        private Integer progress;

        /**
         * 结果摘要
         */
        private String resultSummary;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * Agent元数据
         */
        private Map<String, Object> metadata;
    }

    /**
     * Token使用信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsageInfo {

        /**
         * PlanAgent使用的Token数
         */
        private Long planTokens;

        /**
         * ExecuteAgent使用的Token数
         */
        private Long executeTokens;

        /**
         * ValidateAgent使用的Token数
         */
        private Long validateTokens;

        /**
         * 总Token使用数
         */
        private Long totalTokens;

        /**
         * 预估费用（美元）
         */
        private Double estimatedCost;
    }
}