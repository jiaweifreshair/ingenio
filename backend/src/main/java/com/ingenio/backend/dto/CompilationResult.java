package com.ingenio.backend.dto;

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
public class CompilationResult {

    /**
     * 编译是否成功
     */
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

    public CompilationResult() {
    }

    public CompilationResult(Boolean success, String compiler, String compilerVersion, List<CompilationError> errors,
            List<CompilationWarning> warnings, Long durationMs, String outputDirectory, String command,
            String fullOutput) {
        this.success = success != null ? success : false;
        this.compiler = compiler;
        this.compilerVersion = compilerVersion;
        this.errors = errors;
        this.warnings = warnings;
        this.durationMs = durationMs;
        this.outputDirectory = outputDirectory;
        this.command = command;
        this.fullOutput = fullOutput;
    }

    public static CompilationResultBuilder builder() {
        return new CompilationResultBuilder();
    }

    public static class CompilationResultBuilder {
        private Boolean success = false;
        private String compiler;
        private String compilerVersion;
        private List<CompilationError> errors;
        private List<CompilationWarning> warnings;
        private Long durationMs;
        private String outputDirectory;
        private String command;
        private String fullOutput;

        public CompilationResultBuilder success(Boolean success) {
            this.success = success;
            return this;
        }

        public CompilationResultBuilder compiler(String compiler) {
            this.compiler = compiler;
            return this;
        }

        public CompilationResultBuilder compilerVersion(String compilerVersion) {
            this.compilerVersion = compilerVersion;
            return this;
        }

        public CompilationResultBuilder errors(List<CompilationError> errors) {
            this.errors = errors;
            return this;
        }

        public CompilationResultBuilder warnings(List<CompilationWarning> warnings) {
            this.warnings = warnings;
            return this;
        }

        public CompilationResultBuilder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public CompilationResultBuilder outputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public CompilationResultBuilder command(String command) {
            this.command = command;
            return this;
        }

        public CompilationResultBuilder fullOutput(String fullOutput) {
            this.fullOutput = fullOutput;
            return this;
        }

        public CompilationResult build() {
            return new CompilationResult(success, compiler, compilerVersion, errors, warnings, durationMs,
                    outputDirectory, command, fullOutput);
        }
    }

    // Getters and Setters
    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getCompiler() {
        return compiler;
    }

    public void setCompiler(String compiler) {
        this.compiler = compiler;
    }

    public String getCompilerVersion() {
        return compilerVersion;
    }

    public void setCompilerVersion(String compilerVersion) {
        this.compilerVersion = compilerVersion;
    }

    public List<CompilationError> getErrors() {
        return errors;
    }

    public void setErrors(List<CompilationError> errors) {
        this.errors = errors;
    }

    public List<CompilationWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<CompilationWarning> warnings) {
        this.warnings = warnings;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getFullOutput() {
        return fullOutput;
    }

    public void setFullOutput(String fullOutput) {
        this.fullOutput = fullOutput;
    }

    /**
     * 编译错误
     */
    public static class CompilationError {
        private String filePath;
        private Integer lineNumber;
        private Integer columnNumber;
        private String message;
        private String errorCode;

        public CompilationError() {
        }

        public CompilationError(String filePath, Integer lineNumber, Integer columnNumber, String message,
                String errorCode) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.message = message;
            this.errorCode = errorCode;
        }

        public static CompilationErrorBuilder builder() {
            return new CompilationErrorBuilder();
        }

        public static class CompilationErrorBuilder {
            private String filePath;
            private Integer lineNumber;
            private Integer columnNumber;
            private String message;
            private String errorCode;

            public CompilationErrorBuilder filePath(String filePath) {
                this.filePath = filePath;
                return this;
            }

            public CompilationErrorBuilder lineNumber(Integer lineNumber) {
                this.lineNumber = lineNumber;
                return this;
            }

            public CompilationErrorBuilder columnNumber(Integer columnNumber) {
                this.columnNumber = columnNumber;
                return this;
            }

            public CompilationErrorBuilder message(String message) {
                this.message = message;
                return this;
            }

            public CompilationErrorBuilder errorCode(String errorCode) {
                this.errorCode = errorCode;
                return this;
            }

            public CompilationError build() {
                return new CompilationError(filePath, lineNumber, columnNumber, message, errorCode);
            }
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Integer getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
        }

        public Integer getColumnNumber() {
            return columnNumber;
        }

        public void setColumnNumber(Integer columnNumber) {
            this.columnNumber = columnNumber;
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
    }

    /**
     * 编译警告
     */
    public static class CompilationWarning {
        private String filePath;
        private Integer lineNumber;
        private String message;
        private String warningCode;

        public CompilationWarning() {
        }

        public CompilationWarning(String filePath, Integer lineNumber, String message, String warningCode) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.message = message;
            this.warningCode = warningCode;
        }

        public static CompilationWarningBuilder builder() {
            return new CompilationWarningBuilder();
        }

        public static class CompilationWarningBuilder {
            private String filePath;
            private Integer lineNumber;
            private String message;
            private String warningCode;

            public CompilationWarningBuilder filePath(String filePath) {
                this.filePath = filePath;
                return this;
            }

            public CompilationWarningBuilder lineNumber(Integer lineNumber) {
                this.lineNumber = lineNumber;
                return this;
            }

            public CompilationWarningBuilder message(String message) {
                this.message = message;
                return this;
            }

            public CompilationWarningBuilder warningCode(String warningCode) {
                this.warningCode = warningCode;
                return this;
            }

            public CompilationWarning build() {
                return new CompilationWarning(filePath, lineNumber, message, warningCode);
            }
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public Integer getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(Integer lineNumber) {
            this.lineNumber = lineNumber;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getWarningCode() {
            return warningCode;
        }

        public void setWarningCode(String warningCode) {
            this.warningCode = warningCode;
        }
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
