package com.ingenio.backend.dto.request.repair;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 迭代修复请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IterateRepairRequest {

    /**
     * AppSpec ID
     */
    @NotNull(message = "AppSpec ID不能为空")
    private UUID appSpecId;

    /**
     * 租户ID
     */
    @NotNull(message = "租户ID不能为空")
    private UUID tenantId;

    /**
     * 修复记录ID
     */
    @NotNull(message = "修复记录ID不能为空")
    private UUID repairId;

    /**
     * 最大迭代次数（默认3次）
     */
    @Min(value = 1, message = "最大迭代次数必须≥1")
    @Max(value = 5, message = "最大迭代次数必须≤5")
    @Builder.Default
    private Integer maxIterations = 3;

    /**
     * 是否自动升级（3次失败后）
     */
    @Builder.Default
    private Boolean autoEscalate = true;
}
