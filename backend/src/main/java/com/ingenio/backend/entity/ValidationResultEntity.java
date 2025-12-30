package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * V2.0 验证结果实体
 *
 * 用途：
 * - 记录三环验证（编译→测试→业务）的完整结果
 * - 支持并行验证和串行验证两种模式
 * - 为AI自动修复提供验证历史
 *
 * 三环验证说明：
 * 1. 编译验证（Compile Validation）：TypeScript/Java编译检查
 * 2. 测试验证（Test Validation）：单元测试+覆盖率+质量门禁
 * 3. 业务验证（Business Validation）：API契约+Schema+业务流程
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "validation_results")
public class ValidationResultEntity {

    /**
     * 验证结果ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;

    /**
     * 租户ID - 用于租户隔离
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 关联的AppSpec ID
     */
    @TableField("app_spec_id")
    private UUID appSpecId;

    /**
     * 验证类型
     * - compile: 编译验证
     * - test: 测试验证（单元测试+覆盖率）
     * - quality_gate: 质量门禁验证
     * - contract: API契约验证
     * - schema: 数据库Schema验证
     * - business_flow: 业务流程验证
     * - full: 三环集成验证
     */
    @TableField("validation_type")
    private String validationType;

    /**
     * 验证状态
     * - running: 正在验证
     * - passed: 验证通过
     * - failed: 验证失败
     * - skipped: 跳过（前置验证失败）
     */
    @TableField("status")
    private String status;

    /**
     * 是否通过验证
     */
    @TableField("is_passed")
    private Boolean isPassed;

    /**
     * 验证详细结果（JSON格式）
     * 编译验证：{ compileSuccess: true, errors: [] }
     * 测试验证：{ totalTests: 10, passedTests: 9, failedTests: 1 }
     * 覆盖率验证：{ lineCoverage: 88.5, branchCoverage: 85.2 }
     */
    @TableField(value = "validation_details", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> validationDetails;

    /**
     * 错误消息列表（JSON数组）
     */
    @TableField(value = "error_messages", typeHandler = JacksonTypeHandler.class)
    private java.util.List<String> errorMessages;

    /**
     * 警告消息列表（JSON数组）
     */
    @TableField(value = "warning_messages", typeHandler = JacksonTypeHandler.class)
    private java.util.List<String> warningMessages;

    /**
     * 质量评分（0-100）
     * 编译验证：100分或0分
     * 测试覆盖率：实际覆盖率百分比
     * 质量门禁：综合评分
     */
    @TableField("quality_score")
    private Integer qualityScore;

    /**
     * 开始时间
     */
    @TableField("started_at")
    private Instant startedAt;

    /**
     * 完成时间
     */
    @TableField("completed_at")
    private Instant completedAt;

    /**
     * 验证耗时（毫秒）
     */
    @TableField("duration_ms")
    private Long durationMs;

    /**
     * 元数据（JSON格式）
     * 存储额外信息，如：
     * - 编译器版本
     * - 测试框架版本
     * - 验证工具配置
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /**
     * 验证类型枚举
     */
    public enum ValidationType {
        COMPILE("compile"),
        TEST("test"),
        COVERAGE("coverage"),
        QUALITY_GATE("quality_gate"),
        CONTRACT("contract"),
        SCHEMA("schema"),
        BUSINESS_FLOW("business_flow"),
        FULL("full");

        private final String value;

        ValidationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 验证状态枚举
     */
    public enum Status {
        RUNNING("running"),
        PASSED("passed"),
        FAILED("failed"),
        SKIPPED("skipped");

        private final String value;

        Status(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
