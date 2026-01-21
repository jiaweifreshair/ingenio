package com.ingenio.backend.dto.request.repair;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 迭代修复请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
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
    private Integer maxIterations = 3;

    /**
     * 是否自动升级（3次失败后）
     */
    private Boolean autoEscalate = true;

    public IterateRepairRequest() {
    }

    public IterateRepairRequest(UUID appSpecId, UUID tenantId, UUID repairId, Integer maxIterations,
            Boolean autoEscalate) {
        this.appSpecId = appSpecId;
        this.tenantId = tenantId;
        this.repairId = repairId;
        if (maxIterations != null)
            this.maxIterations = maxIterations;
        if (autoEscalate != null)
            this.autoEscalate = autoEscalate;
    }

    public static IterateRepairRequestBuilder builder() {
        return new IterateRepairRequestBuilder();
    }

    public static class IterateRepairRequestBuilder {
        private UUID appSpecId;
        private UUID tenantId;
        private UUID repairId;
        private Integer maxIterations = 3;
        private Boolean autoEscalate = true;

        public IterateRepairRequestBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public IterateRepairRequestBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public IterateRepairRequestBuilder repairId(UUID repairId) {
            this.repairId = repairId;
            return this;
        }

        public IterateRepairRequestBuilder maxIterations(Integer maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public IterateRepairRequestBuilder autoEscalate(Boolean autoEscalate) {
            this.autoEscalate = autoEscalate;
            return this;
        }

        public IterateRepairRequest build() {
            return new IterateRepairRequest(appSpecId, tenantId, repairId, maxIterations, autoEscalate);
        }
    }

    // Getters and Setters
    public UUID getAppSpecId() {
        return appSpecId;
    }

    public void setAppSpecId(UUID appSpecId) {
        this.appSpecId = appSpecId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getRepairId() {
        return repairId;
    }

    public void setRepairId(UUID repairId) {
        this.repairId = repairId;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }

    public Boolean getAutoEscalate() {
        return autoEscalate;
    }

    public void setAutoEscalate(Boolean autoEscalate) {
        this.autoEscalate = autoEscalate;
    }
}
