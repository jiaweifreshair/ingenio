package com.ingenio.backend.dto.request.repair;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 自动修复并重新验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
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
    private Integer maxIterations = 3;

    /**
     * 是否自动升级（3次失败后）
     */
    private Boolean autoEscalate = true;

    /**
     * 是否在修复成功后自动部署
     */
    private Boolean autoDeploy = false;

    public AutoRepairRequest() {
    }

    public AutoRepairRequest(UUID appSpecId, UUID tenantId, Integer maxIterations, Boolean autoEscalate,
            Boolean autoDeploy) {
        this.appSpecId = appSpecId;
        this.tenantId = tenantId;
        if (maxIterations != null)
            this.maxIterations = maxIterations;
        if (autoEscalate != null)
            this.autoEscalate = autoEscalate;
        if (autoDeploy != null)
            this.autoDeploy = autoDeploy;
    }

    public static AutoRepairRequestBuilder builder() {
        return new AutoRepairRequestBuilder();
    }

    public static class AutoRepairRequestBuilder {
        private UUID appSpecId;
        private UUID tenantId;
        private Integer maxIterations = 3;
        private Boolean autoEscalate = true;
        private Boolean autoDeploy = false;

        public AutoRepairRequestBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public AutoRepairRequestBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public AutoRepairRequestBuilder maxIterations(Integer maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public AutoRepairRequestBuilder autoEscalate(Boolean autoEscalate) {
            this.autoEscalate = autoEscalate;
            return this;
        }

        public AutoRepairRequestBuilder autoDeploy(Boolean autoDeploy) {
            this.autoDeploy = autoDeploy;
            return this;
        }

        public AutoRepairRequest build() {
            return new AutoRepairRequest(appSpecId, tenantId, maxIterations, autoEscalate, autoDeploy);
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

    public Boolean getAutoDeploy() {
        return autoDeploy;
    }

    public void setAutoDeploy(Boolean autoDeploy) {
        this.autoDeploy = autoDeploy;
    }
}
