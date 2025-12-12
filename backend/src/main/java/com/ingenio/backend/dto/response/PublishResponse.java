package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 多端发布响应DTO
 *
 * 功能：
 * - 返回构建任务ID供前端查询进度
 * - 返回每个平台的构建状态
 * - 返回预计完成时间
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishResponse {

    /**
     * 构建任务ID
     * 全局唯一，用于查询构建进度和结果
     */
    private String buildId;

    /**
     * 项目ID
     */
    private String projectId;

    /**
     * 发布的平台列表
     */
    private List<String> platforms;

    /**
     * 总体构建状态
     * 可选值：PENDING, IN_PROGRESS, SUCCESS, FAILED, CANCELLED
     */
    private String status;

    /**
     * 每个平台的构建状态
     * Key: 平台类型（android, ios等）
     * Value: 平台构建结果
     */
    private Map<String, PlatformBuildResult> platformResults;

    /**
     * 预计完成时间（分钟）
     */
    private Integer estimatedTime;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;

    /**
     * 单个平台的构建结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformBuildResult {

        /**
         * 平台类型
         */
        private String platform;

        /**
         * 构建状态
         * 可选值：PENDING, IN_PROGRESS, SUCCESS, FAILED, CANCELLED
         */
        private String status;

        /**
         * 构建进度（0-100）
         */
        private Integer progress;

        /**
         * 构建日志URL
         */
        private String logUrl;

        /**
         * 构建产物下载URL
         */
        private String downloadUrl;

        /**
         * 错误信息（如果失败）
         */
        private String errorMessage;

        /**
         * 开始时间
         */
        private Instant startedAt;

        /**
         * 完成时间
         */
        private Instant completedAt;
    }
}
