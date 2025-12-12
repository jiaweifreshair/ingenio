package com.ingenio.backend.dto.nl2backend;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * NL2Backend响应（统一响应DTO）
 *
 * 包含生成任务的状态和结果
 */
@Data
@Builder
public class NL2BackendResponse {

    /**
     * 生成任务ID
     */
    private String taskId;

    /**
     * 任务状态：pending/analyzing/generating/completed/failed
     */
    private String status;

    /**
     * 进度百分比（0-100）
     */
    private Integer progress;

    /**
     * 当前阶段描述
     */
    private String currentStage;

    /**
     * 结构化需求ID
     */
    private String requirementId;

    /**
     * 生成的Schema ID
     */
    private String schemaId;

    /**
     * 生成的API ID
     */
    private String apiId;

    /**
     * 实体列表（简化展示）
     */
    private List<EntitySummary> entities;

    /**
     * API端点列表（简化展示）
     */
    private List<EndpointSummary> endpoints;

    /**
     * 生成的代码文件数量
     */
    private Integer codeFilesCount;

    /**
     * 错误信息
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
     * 实体摘要
     */
    @Data
    @Builder
    public static class EntitySummary {
        private String name;
        private String description;
        private Integer attributesCount;
    }

    /**
     * API端点摘要
     */
    @Data
    @Builder
    public static class EndpointSummary {
        private String method;
        private String path;
        private String summary;
    }
}
