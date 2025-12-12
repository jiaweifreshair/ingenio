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
 * 自动修复并重新验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoRepairRequest {

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

    /**
     * 是否在修复成功后自动部署
     */
    @Builder.Default
    private Boolean autoDeploy = false;
}
