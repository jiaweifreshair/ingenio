package com.ingenio.backend.dto.request.validation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * 质量门禁验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityGateRequest {

    /**
     * AppSpec ID
     */
    @NotNull(message = "AppSpec ID不能为空")
    private UUID appSpecId;

    /**
     * 质量指标
     * - coverage: 测试覆盖率（0-100）
     * - complexity: 圈复杂度（0-100）
     * - duplicatio n: 代码重复率（0-100）
     */
    @NotNull(message = "质量指标不能为空")
    private Map<String, Integer> metrics;

    /**
     * 覆盖率阈值（默认85%）
     */
    @Min(value = 0, message = "覆盖率阈值必须≥0")
    @Max(value = 100, message = "覆盖率阈值必须≤100")
    @Builder.Default
    private Integer coverageThreshold = 85;

    /**
     * 圈复杂度阈值（默认10）
     */
    @Min(value = 0, message = "圈复杂度阈值必须≥0")
    @Builder.Default
    private Integer complexityThreshold = 10;
}
