package com.ingenio.backend.dto.request.validation;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 三环集成验证请求
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullValidationRequest {

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
     * 验证阶段列表
     * - compile: 编译验证
     * - test: 测试验证
     * - business: 业务验证
     */
    @NotNull(message = "验证阶段不能为空")
    private List<String> stages;

    /**
     * 代码内容（可选，用于编译验证）
     */
    private String code;

    /**
     * 编程语言（可选，用于编译验证）
     */
    private String language;

    /**
     * 是否并行验证（默认串行）
     */
    @Builder.Default
    private Boolean parallel = false;

    /**
     * 失败快速返回（默认true）
     * true: 某个阶段失败立即返回
     * false: 执行所有阶段后返回
     */
    @Builder.Default
    private Boolean failFast = true;
}
