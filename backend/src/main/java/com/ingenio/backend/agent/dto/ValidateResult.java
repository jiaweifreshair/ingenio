package com.ingenio.backend.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ValidateAgent输出结果DTO
 * 包含验证结果、质量评分、错误列表等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateResult {

    /**
     * 是否通过验证
     */
    private Boolean isValid;

    /**
     * 质量评分（0-100）
     */
    private Integer qualityScore;

    /**
     * 错误列表
     */
    private List<ValidationError> errors;

    /**
     * 警告列表
     */
    private List<ValidationWarning> warnings;

    /**
     * 优化建议
     */
    private List<String> suggestions;

    /**
     * 验证摘要
     */
    private String summary;

    /**
     * 验证错误
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {

        /**
         * 错误代码
         */
        private String code;

        /**
         * 错误位置（path）
         */
        private String path;

        /**
         * 错误消息
         */
        private String message;

        /**
         * 严重程度（critical/high/medium/low）
         */
        private String severity;
    }

    /**
     * 验证警告
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarning {

        /**
         * 警告位置（path）
         */
        private String path;

        /**
         * 警告消息
         */
        private String message;

        /**
         * 建议修复方案
         */
        private String suggestion;
    }
}
