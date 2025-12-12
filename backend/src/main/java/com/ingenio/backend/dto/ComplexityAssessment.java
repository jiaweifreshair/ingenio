package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 项目复杂度评估结果
 *
 * 基于实体数量、关系复杂度等指标评估项目复杂度
 */
@Data
@Builder
public class ComplexityAssessment {

    /**
     * 复杂度级别
     */
    private ComplexityLevel level;

    /**
     * 实体数量
     */
    private Integer entityCount;

    /**
     * 关系数量
     */
    private Integer relationshipCount;

    /**
     * 操作数量
     */
    private Integer operationCount;

    /**
     * 预估开发天数
     */
    private Integer estimatedDays;

    /**
     * 预估代码行数
     */
    private Integer estimatedLines;

    /**
     * 评估置信度 (0-1)
     */
    @Builder.Default
    private Double confidence = 0.80;

    /**
     * 详细说明
     */
    private String description;

    /**
     * 复杂度级别枚举
     */
    public enum ComplexityLevel {
        /**
         * 简单: <= 5个实体, <= 5个关系
         * 示例: 图书管理、待办事项
         */
        SIMPLE("简单", 1, 3),

        /**
         * 中等: 5-15个实体, <= 20个关系
         * 示例: 电商系统、社区论坛
         */
        MEDIUM("中等", 3, 7),

        /**
         * 复杂: > 15个实体, > 20个关系
         * 示例: ERP系统、社交平台
         */
        COMPLEX("复杂", 7, 14);

        private final String displayName;
        private final int minDays;
        private final int maxDays;

        ComplexityLevel(String displayName, int minDays, int maxDays) {
            this.displayName = displayName;
            this.minDays = minDays;
            this.maxDays = maxDays;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getMinDays() {
            return minDays;
        }

        public int getMaxDays() {
            return maxDays;
        }
    }
}
