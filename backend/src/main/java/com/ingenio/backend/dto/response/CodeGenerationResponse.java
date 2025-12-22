package com.ingenio.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 代码生成响应DTO
 *
 * V2.0 Execute阶段API返回值：
 * 触发代码生成后，返回任务状态和预计完成时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenerationResponse {

    /**
     * 操作是否成功
     */
    private boolean success;

    /**
     * AppSpec ID
     */
    private UUID appSpecId;

    /**
     * 新创建的项目ID（如果生成成功）
     */
    private UUID projectId;

    /**
     * 生成任务ID（用于轮询进度）
     */
    private UUID generationTaskId;

    /**
     * 预计完成时间（秒）
     */
    private Integer estimatedCompletionTime;

    /**
     * 当前状态
     * PENDING: 排队中
     * GENERATING: 生成中
     * VALIDATING: 验证中
     * COMPLETED: 已完成
     * FAILED: 失败
     */
    private String status;

    /**
     * 操作消息
     */
    private String message;

    /**
     * 生成进度（0-100）
     */
    private Integer progress;

    /**
     * 任务开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant startedAt;

    /**
     * 前端预览URL（如果有OpenLovable原型）
     */
    private String previewUrl;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;
}
