package com.ingenio.backend.dto.response.repair;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 修复响应
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
public class RepairResponse {

    /**
     * 修复记录ID
     */
    private UUID repairId;

    /**
     * 修复状态
     */
    private String status;

    /**
     * 是否修复成功
     */
    private Boolean isSuccess;

    /**
     * 当前迭代次数
     */
    private Integer currentIteration;

    /**
     * 最大迭代次数
     */
    private Integer maxIterations;

    /**
     * 修复策略
     */
    private String repairStrategy;

    /**
     * 修复建议列表
     */
    private List<Map<String, Object>> suggestions;

    /**
     * 代码变更
     */
    private Map<String, Object> codeChanges;

    /**
     * 受影响的文件
     */
    private List<String> affectedFiles;

    /**
     * 修复后的验证结果ID
     */
    private UUID repairValidationResultId;

    /**
     * 是否已升级人工介入
     */
    private Boolean isEscalated;

    /**
     * 失败原因（如果失败）
     */
    private String failureReason;

    /**
     * 修复耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 完成时间
     */
    private Instant completedAt;

    /**
     * AI Token消耗统计
     *
     * 包含以下字段：
     * - promptTokens: 提示词消耗的Token数量
     * - completionTokens: 生成内容消耗的Token数量
     * - totalTokens: 总Token消耗
     *
     * @since 2.0.0 Phase 4（AI集成）
     */
    private Map<String, Integer> aiTokenUsage;

    public RepairResponse() {
    }

    public RepairResponse(UUID repairId, String status, Boolean isSuccess, Integer currentIteration,
            Integer maxIterations, String repairStrategy, List<Map<String, Object>> suggestions,
            Map<String, Object> codeChanges, List<String> affectedFiles, UUID repairValidationResultId,
            Boolean isEscalated, String failureReason, Long durationMs, Instant completedAt,
            Map<String, Integer> aiTokenUsage) {
        this.repairId = repairId;
        this.status = status;
        this.isSuccess = isSuccess;
        this.currentIteration = currentIteration;
        this.maxIterations = maxIterations;
        this.repairStrategy = repairStrategy;
        this.suggestions = suggestions;
        this.codeChanges = codeChanges;
        this.affectedFiles = affectedFiles;
        this.repairValidationResultId = repairValidationResultId;
        this.isEscalated = isEscalated;
        this.failureReason = failureReason;
        this.durationMs = durationMs;
        this.completedAt = completedAt;
        this.aiTokenUsage = aiTokenUsage;
    }

    public static RepairResponseBuilder builder() {
        return new RepairResponseBuilder();
    }

    public static class RepairResponseBuilder {
        private UUID repairId;
        private String status;
        private Boolean isSuccess;
        private Integer currentIteration;
        private Integer maxIterations;
        private String repairStrategy;
        private List<Map<String, Object>> suggestions;
        private Map<String, Object> codeChanges;
        private List<String> affectedFiles;
        private UUID repairValidationResultId;
        private Boolean isEscalated;
        private String failureReason;
        private Long durationMs;
        private Instant completedAt;
        private Map<String, Integer> aiTokenUsage;

        public RepairResponseBuilder repairId(UUID repairId) {
            this.repairId = repairId;
            return this;
        }

        public RepairResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public RepairResponseBuilder isSuccess(Boolean isSuccess) {
            this.isSuccess = isSuccess;
            return this;
        }

        public RepairResponseBuilder currentIteration(Integer currentIteration) {
            this.currentIteration = currentIteration;
            return this;
        }

        public RepairResponseBuilder maxIterations(Integer maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public RepairResponseBuilder repairStrategy(String repairStrategy) {
            this.repairStrategy = repairStrategy;
            return this;
        }

        public RepairResponseBuilder suggestions(List<Map<String, Object>> suggestions) {
            this.suggestions = suggestions;
            return this;
        }

        public RepairResponseBuilder codeChanges(Map<String, Object> codeChanges) {
            this.codeChanges = codeChanges;
            return this;
        }

        public RepairResponseBuilder affectedFiles(List<String> affectedFiles) {
            this.affectedFiles = affectedFiles;
            return this;
        }

        public RepairResponseBuilder repairValidationResultId(UUID repairValidationResultId) {
            this.repairValidationResultId = repairValidationResultId;
            return this;
        }

        public RepairResponseBuilder isEscalated(Boolean isEscalated) {
            this.isEscalated = isEscalated;
            return this;
        }

        public RepairResponseBuilder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public RepairResponseBuilder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public RepairResponseBuilder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public RepairResponseBuilder aiTokenUsage(Map<String, Integer> aiTokenUsage) {
            this.aiTokenUsage = aiTokenUsage;
            return this;
        }

        public RepairResponse build() {
            return new RepairResponse(repairId, status, isSuccess, currentIteration, maxIterations, repairStrategy,
                    suggestions, codeChanges, affectedFiles, repairValidationResultId, isEscalated, failureReason,
                    durationMs, completedAt, aiTokenUsage);
        }
    }

    // Getters and Setters
    public UUID getRepairId() {
        return repairId;
    }

    public void setRepairId(UUID repairId) {
        this.repairId = repairId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsSuccess() {
        return isSuccess;
    }

    public void setIsSuccess(Boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public Integer getCurrentIteration() {
        return currentIteration;
    }

    public void setCurrentIteration(Integer currentIteration) {
        this.currentIteration = currentIteration;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getRepairStrategy() {
        return repairStrategy;
    }

    public void setRepairStrategy(String repairStrategy) {
        this.repairStrategy = repairStrategy;
    }

    public List<Map<String, Object>> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<Map<String, Object>> suggestions) {
        this.suggestions = suggestions;
    }

    public Map<String, Object> getCodeChanges() {
        return codeChanges;
    }

    public void setCodeChanges(Map<String, Object> codeChanges) {
        this.codeChanges = codeChanges;
    }

    public List<String> getAffectedFiles() {
        return affectedFiles;
    }

    public void setAffectedFiles(List<String> affectedFiles) {
        this.affectedFiles = affectedFiles;
    }

    public UUID getRepairValidationResultId() {
        return repairValidationResultId;
    }

    public void setRepairValidationResultId(UUID repairValidationResultId) {
        this.repairValidationResultId = repairValidationResultId;
    }

    public Boolean getIsEscalated() {
        return isEscalated;
    }

    public void setIsEscalated(Boolean isEscalated) {
        this.isEscalated = isEscalated;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Map<String, Integer> getAiTokenUsage() {
        return aiTokenUsage;
    }

    public void setAiTokenUsage(Map<String, Integer> aiTokenUsage) {
        this.aiTokenUsage = aiTokenUsage;
    }
}
