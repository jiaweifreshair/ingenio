package com.ingenio.backend.codegen.ai.autofix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoFixResult - 自动修复结果数据类
 *
 * <p>封装AutoFixOrchestrator的执行结果，包含修复历史和最终代码</p>
 *
 * <p>核心信息：</p>
 * <ul>
 *   <li>success: 是否修复成功（达到质量评分≥70分）</li>
 *   <li>finalCode: 最终修复后的代码（success=true时有效）</li>
 *   <li>iterations: 修复迭代次数（1-3次）</li>
 *   <li>fixHistory: 修复历史记录（每次迭代的修复信息）</li>
 *   <li>failureReason: 失败原因（success=false时有效）</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * AutoFixResult result = autoFixOrchestrator.attemptAutoFix(
 *     initialCode, entity, "createUser"
 * );
 *
 * if (result.isSuccess()) {
 *     System.out.println("修复成功，迭代次数: " + result.getIterations());
 *     System.out.println("最终代码: " + result.getFinalCode());
 *     System.out.println("质量评分: " + result.getFinalQualityScore());
 * } else {
 *     System.out.println("修复失败原因: " + result.getFailureReason());
 * }
 * }</pre>
 *
 * @author Ingenio AutoFix Orchestrator
 * @since 2025-11-19 P0 Phase 2: AutoFixResult数据类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoFixResult {

    /**
     * 是否修复成功
     *
     * <p>成功标准：</p>
     * <ul>
     *   <li>ValidationTool评分≥70分</li>
     *   <li>在3次迭代内完成修复</li>
     * </ul>
     */
    private boolean success;

    /**
     * 最终修复后的代码
     *
     * <p>仅在success=true时有效</p>
     */
    private String finalCode;

    /**
     * 修复迭代次数（1-3次）
     *
     * <p>迭代次数说明：</p>
     * <ul>
     *   <li>1次：初始代码即通过验证</li>
     *   <li>2-3次：经过1-2次修复后通过验证</li>
     *   <li>3次：达到最大迭代限制但未通过</li>
     * </ul>
     */
    private int iterations;

    /**
     * 最终质量评分（0-100分）
     *
     * <p>来自ValidationTool的最后一次评分</p>
     */
    private int finalQualityScore;

    /**
     * 失败原因（success=false时有效）
     *
     * <p>失败原因包括：</p>
     * <ul>
     *   <li>"Max iterations reached": 达到3次迭代限制</li>
     *   <li>"No applicable fix strategy": 没有适用的修复策略</li>
     *   <li>"Fix strategy did not change code": 修复策略未改变代码</li>
     * </ul>
     */
    private String failureReason;

    /**
     * 修复历史记录
     *
     * <p>记录每次迭代的修复信息，用于调试和分析</p>
     */
    @Builder.Default
    private List<FixHistoryEntry> fixHistory = new ArrayList<>();

    /**
     * 总耗时（毫秒）
     */
    private long totalDurationMs;

    /**
     * 修复历史条目
     *
     * <p>记录单次修复迭代的详细信息</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixHistoryEntry {

        /**
         * 迭代次数（1-3）
         */
        private int iteration;

        /**
         * 本次修复前的质量评分
         */
        private int scoreBeforeFix;

        /**
         * 本次修复后的质量评分
         */
        private int scoreAfterFix;

        /**
         * 发现的问题列表
         */
        private List<ValidationIssue> issuesFound;

        /**
         * 应用的修复策略名称
         */
        private String appliedStrategy;

        /**
         * 本次修复是否成功（代码是否有改变）
         */
        private boolean fixApplied;

        /**
         * 本次修复耗时（毫秒）
         */
        private long durationMs;

        /**
         * 修复备注（可选）
         */
        private String notes;
    }

    /**
     * 添加修复历史条目
     *
     * @param entry 修复历史条目
     */
    public void addFixHistoryEntry(FixHistoryEntry entry) {
        if (fixHistory == null) {
            fixHistory = new ArrayList<>();
        }
        fixHistory.add(entry);
    }

    /**
     * 创建成功的AutoFixResult
     *
     * @param finalCode     最终修复后的代码
     * @param iterations    修复迭代次数
     * @param qualityScore  最终质量评分
     * @return 成功的AutoFixResult
     */
    public static AutoFixResult success(String finalCode, int iterations, int qualityScore) {
        return AutoFixResult.builder()
                .success(true)
                .finalCode(finalCode)
                .iterations(iterations)
                .finalQualityScore(qualityScore)
                .build();
    }

    /**
     * 创建失败的AutoFixResult
     *
     * @param failureReason 失败原因
     * @param iterations    修复迭代次数
     * @param lastScore     最后一次质量评分
     * @return 失败的AutoFixResult
     */
    public static AutoFixResult failure(String failureReason, int iterations, int lastScore) {
        return AutoFixResult.builder()
                .success(false)
                .finalCode(null)
                .iterations(iterations)
                .finalQualityScore(lastScore)
                .failureReason(failureReason)
                .build();
    }

    /**
     * 获取修复摘要信息（用于日志）
     *
     * @return 修复摘要字符串
     */
    public String getSummary() {
        if (success) {
            return String.format("✅ 修复成功 - 迭代次数:%d, 最终评分:%d分",
                    iterations, finalQualityScore);
        } else {
            return String.format("❌ 修复失败 - 迭代次数:%d, 最终评分:%d分, 原因:%s",
                    iterations, finalQualityScore, failureReason);
        }
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
