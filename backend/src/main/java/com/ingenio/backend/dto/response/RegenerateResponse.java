package com.ingenio.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * 重新生成响应DTO
 */
@Data
@Builder
public class RegenerateResponse {

    /**
     * 项目ID
     */
    private UUID projectId;

    /**
     * 生成任务ID
     */
    private UUID taskId;

    /**
     * 新AppSpec版本ID
     */
    private UUID newVersionId;

    /**
     * 新AppSpec版本号
     */
    private Integer newVersion;
}
