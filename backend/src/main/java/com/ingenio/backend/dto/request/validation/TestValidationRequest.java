package com.ingenio.backend.dto.request.validation;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 测试验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestValidationRequest {

    /**
     * AppSpec ID
     */
    @NotNull(message = "AppSpec ID不能为空")
    private UUID appSpecId;

    /**
     * 测试类型
     * - unit: 单元测试
     * - integration: 集成测试
     * - e2e: 端到端测试
     */
    @NotNull(message = "测试类型不能为空")
    private String testType;

    /**
     * 测试文件列表
     */
    private List<String> testFiles;

    /**
     * 是否生成覆盖率报告
     */
    @Builder.Default
    private Boolean generateCoverage = true;
}
