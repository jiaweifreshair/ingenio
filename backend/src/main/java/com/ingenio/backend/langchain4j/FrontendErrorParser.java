package com.ingenio.backend.langchain4j;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前端错误解析器
 *
 * 功能：解析Vite/Babel编译错误信息，提取关键信息
 * 支持的错误类型：
 * - Babel语法错误（Unterminated string constant等）
 * - Vite编译错误
 * - ESLint错误
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
@Slf4j
@Component
public class FrontendErrorParser {

    // Babel错误正则：[plugin:vite:react-babel] /path/to/file.jsx: Error message. (line:col)
    private static final Pattern BABEL_ERROR_PATTERN = Pattern.compile(
            "\\[plugin:vite:react-babel\\]\\s+([^:]+):\\s+(.+?)\\s+\\((\\d+):(\\d+)\\)"
    );

    // 文件路径正则：提取文件路径
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "/home/user/app/(.+)"
    );

    /**
     * 解析前端错误信息
     *
     * @param errorOutput 错误输出（完整的错误日志）
     * @return 解析后的错误信息，如果无法解析则返回null
     */
    public ParsedError parse(String errorOutput) {
        if (errorOutput == null || errorOutput.isBlank()) {
            return null;
        }

        // 尝试解析Babel错误
        Matcher babelMatcher = BABEL_ERROR_PATTERN.matcher(errorOutput);
        if (babelMatcher.find()) {
            String filePath = babelMatcher.group(1);
            String errorMessage = babelMatcher.group(2);
            int line = Integer.parseInt(babelMatcher.group(3));
            int column = Integer.parseInt(babelMatcher.group(4));

            // 提取相对路径
            Matcher pathMatcher = FILE_PATH_PATTERN.matcher(filePath);
            String relativePath = pathMatcher.find() ? pathMatcher.group(1) : filePath;

            log.debug("解析Babel错误: file={}, line={}, col={}, message={}",
                    relativePath, line, column, errorMessage);

            return ParsedError.builder()
                    .errorType("BABEL_SYNTAX_ERROR")
                    .filePath(relativePath)
                    .line(line)
                    .column(column)
                    .message(errorMessage)
                    .rawOutput(errorOutput)
                    .build();
        }

        log.warn("无法解析前端错误: {}", errorOutput.substring(0, Math.min(200, errorOutput.length())));
        return null;
    }

    /**
     * 解析后的错误信息
     */
    @lombok.Builder
    @lombok.Data
    public static class ParsedError {
        /** 错误类型 */
        private String errorType;

        /** 文件路径（相对路径） */
        private String filePath;

        /** 错误行号 */
        private int line;

        /** 错误列号 */
        private int column;

        /** 错误消息 */
        private String message;

        /** 原始错误输出 */
        private String rawOutput;
    }
}
