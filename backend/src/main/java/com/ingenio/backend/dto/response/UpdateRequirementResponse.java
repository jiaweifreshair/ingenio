package com.ingenio.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * 更新需求响应DTO
 */
@Data
@Builder
public class UpdateRequirementResponse {

    /**
     * 项目ID
     */
    private UUID projectId;

    /**
     * 意图识别结果（如果重新识别了意图）
     */
    private Map<String, Object> intentClassificationResult;
}
