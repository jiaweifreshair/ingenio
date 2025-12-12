package com.ingenio.backend.dto.response.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通用验证响应
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResponse {

    /**
     * 验证结果ID
     */
    private UUID validationId;

    /**
     * 验证类型
     */
    private String validationType;

    /**
     * 是否通过验证
     */
    private Boolean passed;

    /**
     * 验证状态
     */
    private String status;

    /**
     * 质量评分（0-100）
     */
    private Integer qualityScore;

    /**
     * 验证详情
     */
    private Map<String, Object> details;

    /**
     * 错误消息列表
     */
    private List<String> errors;

    /**
     * 警告消息列表
     */
    private List<String> warnings;

    /**
     * 验证耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 完成时间
     */
    private Instant completedAt;
}
