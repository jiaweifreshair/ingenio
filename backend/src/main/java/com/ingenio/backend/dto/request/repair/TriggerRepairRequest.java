package com.ingenio.backend.dto.request.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 触发AI修复请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
public class TriggerRepairRequest {

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
     * 失败类型
     * - compile: 编译错误
     * - test: 测试失败
     * - type_error: 类型错误
     * - dependency: 依赖缺失
     * - business_logic: 业务逻辑错误
     */
    @NotBlank(message = "失败类型不能为空")
    private String failureType;

    /**
     * 错误详情列表
     */
    @NotNull(message = "错误详情不能为空")
    private List<ErrorDetail> errorDetails;

    public TriggerRepairRequest() {
    }

    public TriggerRepairRequest(UUID appSpecId, UUID tenantId, String failureType, List<ErrorDetail> errorDetails) {
        this.appSpecId = appSpecId;
        this.tenantId = tenantId;
        this.failureType = failureType;
        this.errorDetails = errorDetails;
    }

    // Builder
    public static TriggerRepairRequestBuilder builder() {
        return new TriggerRepairRequestBuilder();
    }

    public static class TriggerRepairRequestBuilder {
        private UUID appSpecId;
        private UUID tenantId;
        private String failureType;
        private List<ErrorDetail> errorDetails;

        public TriggerRepairRequestBuilder appSpecId(UUID appSpecId) {
            this.appSpecId = appSpecId;
            return this;
        }

        public TriggerRepairRequestBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public TriggerRepairRequestBuilder failureType(String failureType) {
            this.failureType = failureType;
            return this;
        }

        public TriggerRepairRequestBuilder errorDetails(List<ErrorDetail> errorDetails) {
            this.errorDetails = errorDetails;
            return this;
        }

        public TriggerRepairRequest build() {
            return new TriggerRepairRequest(appSpecId, tenantId, failureType, errorDetails);
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

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public List<ErrorDetail> getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(List<ErrorDetail> errorDetails) {
        this.errorDetails = errorDetails;
    }

    /**
     * 错误详情
     */
    public static class ErrorDetail {
        private Integer line;
        private String message;
        private String errorCode;
        private String filePath;
        private String stackTrace;

        public ErrorDetail() {
        }

        public ErrorDetail(Integer line, String message, String errorCode, String filePath, String stackTrace) {
            this.line = line;
            this.message = message;
            this.errorCode = errorCode;
            this.filePath = filePath;
            this.stackTrace = stackTrace;
        }

        public static ErrorDetailBuilder builder() {
            return new ErrorDetailBuilder();
        }

        public static class ErrorDetailBuilder {
            private Integer line;
            private String message;
            private String errorCode;
            private String filePath;
            private String stackTrace;

            public ErrorDetailBuilder line(Integer line) {
                this.line = line;
                return this;
            }

            public ErrorDetailBuilder message(String message) {
                this.message = message;
                return this;
            }

            public ErrorDetailBuilder errorCode(String errorCode) {
                this.errorCode = errorCode;
                return this;
            }

            public ErrorDetailBuilder filePath(String filePath) {
                this.filePath = filePath;
                return this;
            }

            public ErrorDetailBuilder stackTrace(String stackTrace) {
                this.stackTrace = stackTrace;
                return this;
            }

            public ErrorDetail build() {
                return new ErrorDetail(line, message, errorCode, filePath, stackTrace);
            }
        }

        // Getters and Setters
        public Integer getLine() {
            return line;
        }

        public void setLine(Integer line) {
            this.line = line;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public void setStackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
        }
    }
}
