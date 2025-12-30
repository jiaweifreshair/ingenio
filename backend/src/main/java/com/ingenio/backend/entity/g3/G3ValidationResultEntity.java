package com.ingenio.backend.entity.g3;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * G3引擎验证结果实体类
 * 存储每次沙箱验证的详细结果
 *
 * 验证类型包括：
 * - COMPILE: Maven/Gradle 编译
 * - UNIT_TEST: 单元测试执行
 * - INTEGRATION_TEST: 集成测试
 * - RUNTIME: 运行时检查（应用启动）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "g3_validation_results", autoResultMap = true)
public class G3ValidationResultEntity {

    /**
     * 验证结果ID（UUID主键）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    /**
     * 关联的G3任务ID
     */
    @TableField(value = "job_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID jobId;

    /**
     * 验证轮次
     */
    @TableField("round")
    private Integer round;

    /**
     * 验证类型
     * @see ValidationType
     */
    @TableField("validation_type")
    private String validationType;

    /**
     * 是否通过验证
     */
    @TableField("passed")
    private Boolean passed;

    /**
     * 执行的命令（如 mvn compile）
     */
    @TableField("command")
    private String command;

    /**
     * 命令退出码
     */
    @TableField("exit_code")
    private Integer exitCode;

    /**
     * 标准输出
     */
    @TableField("stdout")
    private String stdout;

    /**
     * 错误输出
     */
    @TableField("stderr")
    private String stderr;

    /**
     * 执行耗时（毫秒）
     */
    @TableField("duration_ms")
    private Integer durationMs;

    /**
     * 解析后的错误列表
     * 格式：[{file, line, column, message, severity}]
     */
    @TableField(value = "parsed_errors", typeHandler = JacksonTypeHandler.class)
    private List<ParsedError> parsedErrors;

    /**
     * 错误数量
     */
    @TableField("error_count")
    private Integer errorCount;

    /**
     * 警告数量
     */
    @TableField("warning_count")
    private Integer warningCount;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 验证类型枚举
     */
    public enum ValidationType {
        COMPILE("COMPILE", "编译检查"),
        UNIT_TEST("UNIT_TEST", "单元测试"),
        INTEGRATION_TEST("INTEGRATION_TEST", "集成测试"),
        RUNTIME("RUNTIME", "运行时检查");

        private final String value;
        private final String description;

        ValidationType(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 解析后的错误条目
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedError {
        /**
         * 文件路径
         */
        private String file;

        /**
         * 行号
         */
        private Integer line;

        /**
         * 列号
         */
        private Integer column;

        /**
         * 错误消息
         */
        private String message;

        /**
         * 严重程度（error/warning/info）
         */
        private String severity;
    }

    /**
     * 创建编译验证结果
     * 注意：手动生成UUID，因为Builder模式绕过了MyBatis-Plus的ASSIGN_UUID策略
     */
    public static G3ValidationResultEntity createCompileResult(
            UUID jobId,
            int round,
            boolean passed,
            String command,
            int exitCode,
            String stdout,
            String stderr,
            int durationMs
    ) {
        return G3ValidationResultEntity.builder()
            .id(UUID.randomUUID())  // 手动生成UUID
            .jobId(jobId)
            .round(round)
            .validationType(ValidationType.COMPILE.getValue())
            .passed(passed)
            .command(command)
            .exitCode(exitCode)
            .stdout(stdout)
            .stderr(stderr)
            .durationMs(durationMs)
            .parsedErrors(new ArrayList<>())
            .errorCount(0)
            .warningCount(0)
            .build();
    }

    /**
     * 添加解析后的错误
     */
    public void addParsedError(String file, Integer line, Integer column, String message, String severity) {
        if (this.parsedErrors == null) {
            this.parsedErrors = new ArrayList<>();
        }
        this.parsedErrors.add(ParsedError.builder()
            .file(file)
            .line(line)
            .column(column)
            .message(message)
            .severity(severity)
            .build());

        // 更新计数
        if ("error".equals(severity)) {
            this.errorCount = (this.errorCount == null ? 0 : this.errorCount) + 1;
        } else if ("warning".equals(severity)) {
            this.warningCount = (this.warningCount == null ? 0 : this.warningCount) + 1;
        }
    }

    /**
     * 获取格式化的错误摘要
     */
    public String getErrorSummary() {
        if (passed) {
            return "验证通过";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("验证失败: %d 个错误, %d 个警告", errorCount, warningCount));
        if (parsedErrors != null && !parsedErrors.isEmpty()) {
            sb.append("\n主要错误:");
            int count = 0;
            for (ParsedError error : parsedErrors) {
                if (count >= 5) {
                    sb.append(String.format("\n... 还有 %d 个错误", parsedErrors.size() - 5));
                    break;
                }
                if ("error".equals(error.getSeverity())) {
                    sb.append(String.format("\n  - %s:%d: %s", error.getFile(), error.getLine(), error.getMessage()));
                    count++;
                }
            }
        }
        return sb.toString();
    }
}
