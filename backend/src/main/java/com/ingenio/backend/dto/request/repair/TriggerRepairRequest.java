package com.ingenio.backend.dto.request.repair;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 触发AI修复请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    /**
     * 错误详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        /**
         * 错误行号
         */
        private Integer line;

        /**
         * 错误消息
         */
        private String message;

        /**
         * 错误代码（如TypeScript错误码：TS2322）
         */
        private String errorCode;

        /**
         * 文件路径
         */
        private String filePath;

        /**
         * 堆栈信息
         */
        private String stackTrace;
    }
}
