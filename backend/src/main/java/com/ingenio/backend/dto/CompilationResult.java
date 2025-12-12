package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 编译验证结果
 *
 * 记录编译过程的详细信息：
 * - 编译状态
 * - 错误信息
 * - 警告信息
 * - 编译耗时
 */
@Data
@Builder
public class CompilationResult {

    /**
     * 编译是否成功
     */
    @Builder.Default
    private Boolean success = false;

    /**
     * 编译器类型
     * kotlinc / javac / tsc
     */
    private String compiler;

    /**
     * 编译器版本
     */
    private String compilerVersion;

    /**
     * 编译错误列表
     */
    private List<CompilationError> errors;

    /**
     * 编译警告列表
     */
    private List<CompilationWarning> warnings;

    /**
     * 编译耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 编译输出目录
     */
    private String outputDirectory;

    /**
     * 编译命令
     */
    private String command;

    /**
     * 完整输出日志
     */
    private String fullOutput;

    /**
     * 编译错误
     */
    @Data
    @Builder
    public static class CompilationError {
        /**
         * 文件路径
         */
        private String filePath;

        /**
         * 行号
         */
        private Integer lineNumber;

        /**
         * 列号
         */
        private Integer columnNumber;

        /**
         * 错误消息
         */
        private String message;

        /**
         * 错误代码
         */
        private String errorCode;
    }

    /**
     * 编译警告
     */
    @Data
    @Builder
    public static class CompilationWarning {
        /**
         * 文件路径
         */
        private String filePath;

        /**
         * 行号
         */
        private Integer lineNumber;

        /**
         * 警告消息
         */
        private String message;

        /**
         * 警告代码
         */
        private String warningCode;
    }

    /**
     * 获取错误数量
     */
    public int getErrorCount() {
        return errors != null ? errors.size() : 0;
    }

    /**
     * 获取警告数量
     */
    public int getWarningCount() {
        return warnings != null ? warnings.size() : 0;
    }
}
