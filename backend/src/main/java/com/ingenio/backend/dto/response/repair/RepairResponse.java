package com.ingenio.backend.dto.response.repair;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
