package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 任务列表响应DTO
 *
 * 用于分页查询用户任务列表
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskListResponse {

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 页大小
     */
    private Integer pageSize;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 任务列表
     */
    private List<TaskItem> tasks;

    /**
     * 任务项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {

        /**
         * 任务ID
         */
        private UUID taskId;

        /**
         * 任务名称
         */
        private String taskName;

        /**
         * 任务状态
         */
        private String status;

        /**
         * 状态描述
         */
        private String statusDescription;

        /**
         * 任务进度（0-100）
         */
        private Integer progress;

        /**
         * 当前执行的Agent
         */
        private String currentAgent;

        /**
         * AppSpec ID（如果已完成）
         */
        private UUID appSpecId;

        /**
         * 质量评分（0-100）
         */
        private Integer qualityScore;

        /**
         * 下载链接
         */
        private String downloadUrl;

        /**
         * 错误信息（如果失败）
         */
        private String errorMessage;

        /**
         * 创建时间
         */
        private Instant createdAt;

        /**
         * 完成时间
         */
        private Instant completedAt;

        /**
         * 更新时间
         */
        private Instant updatedAt;
    }
}
