package com.ingenio.backend.entity.g3;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * G3 Session Memory - 参考 MetaGPT Memory 设计模式
 *
 * 核心能力：
 * 1. 存储修复历史（每轮修复的输入/输出/错误）
 * 2. 错误签名索引（快速检测重复错误）
 * 3. 提供 Coach 上下文（上 N 轮修复摘要）
 *
 * 生命周期：与单次 G3 任务执行（runJob）一致，任务结束后丢弃
 *
 * @author Ingenio Team
 * @since 2.1.0
 */
public class G3SessionMemory {

    private static final Logger log = LoggerFactory.getLogger(G3SessionMemory.class);

    /**
     * 关联的任务ID
     */
    private final UUID jobId;

    /**
     * 修复历史记录（按轮次顺序存储）
     */
    private final List<RepairAttempt> repairHistory = new ArrayList<>();

    /**
     * 错误签名 → 连续出现次数
     * 用于检测"相同错误重复出现"，避免无效修复循环
     */
    private final Map<String, Integer> errorSignatureCount = new LinkedHashMap<>();

    /**
     * 已修复过的文件集合
     * 用于 pom.xml 兜底策略：同一文件不应被反复兜底修复
     */
    private final Set<String> repairedFiles = new HashSet<>();

    /**
     * 上一轮的错误签名（用于检测连续相同错误）
     */
    private String lastErrorSignature = null;

    /**
     * 连续相同错误计数
     */
    private int consecutiveSameErrorCount = 0;

    /**
     * 最大保留历史轮次（避免内存膨胀）
     */
    private static final int MAX_HISTORY_SIZE = 10;

    /**
     * 相同错误最大容忍次数（超过则提前终止）
     */
    private static final int MAX_SAME_ERROR_TOLERANCE = 2;

    /**
     * 构造函数
     *
     * @param jobId 关联的任务ID
     */
    public G3SessionMemory(UUID jobId) {
        this.jobId = jobId;
    }

    // Getters for Data
    public UUID getJobId() {
        return jobId;
    }

    public List<RepairAttempt> getRepairHistory() {
        return repairHistory;
    }

    public Map<String, Integer> getErrorSignatureCount() {
        return errorSignatureCount;
    }

    public Set<String> getRepairedFiles() {
        return repairedFiles;
    }

    public String getLastErrorSignature() {
        return lastErrorSignature;
    }

    public int getConsecutiveSameErrorCount() {
        return consecutiveSameErrorCount;
    }

    public void setLastErrorSignature(String lastErrorSignature) {
        this.lastErrorSignature = lastErrorSignature;
    }

    public void setConsecutiveSameErrorCount(int consecutiveSameErrorCount) {
        this.consecutiveSameErrorCount = consecutiveSameErrorCount;
    }

    /**
     * 记录一次修复尝试
     */
    public void addRepairAttempt(int round, List<String> files, boolean success,
            String errorSignature, String fixSummary) {
        RepairAttempt attempt = new RepairAttempt(
                round,
                Instant.now(),
                files,
                success,
                errorSignature,
                fixSummary);

        repairHistory.add(attempt);

        // 记录已修复的文件
        if (files != null) {
            repairedFiles.addAll(files);
        }

        // 限制历史大小
        while (repairHistory.size() > MAX_HISTORY_SIZE) {
            repairHistory.remove(0);
        }

        log.debug("[G3SessionMemory] 记录修复尝试: round={}, files={}, success={}",
                round, files, success);
    }

    /**
     * 记录错误签名并检测重复
     */
    public boolean recordErrorSignature(String errorSignature) {
        if (errorSignature == null || errorSignature.isBlank()) {
            lastErrorSignature = null;
            consecutiveSameErrorCount = 0;
            return false;
        }

        // 更新签名总计数
        errorSignatureCount.merge(errorSignature, 1, Integer::sum);

        // 检测连续相同错误
        if (errorSignature.equals(lastErrorSignature)) {
            consecutiveSameErrorCount++;
            log.debug("[G3SessionMemory] 检测到连续相同错误: signature={}, count={}",
                    truncate(errorSignature, 50), consecutiveSameErrorCount);
        } else {
            lastErrorSignature = errorSignature;
            consecutiveSameErrorCount = 1;
        }

        return consecutiveSameErrorCount >= MAX_SAME_ERROR_TOLERANCE;
    }

    /**
     * 检测是否应提前终止修复循环
     */
    public boolean shouldTerminate() {
        // 条件1：连续相同错误
        if (consecutiveSameErrorCount >= MAX_SAME_ERROR_TOLERANCE) {
            log.info("[G3SessionMemory] 终止条件满足: 连续 {} 次相同错误", consecutiveSameErrorCount);
            return true;
        }

        // 条件2：多次修复但全部失败
        if (repairHistory.size() >= 3) {
            long successCount = repairHistory.stream().filter(RepairAttempt::success).count();
            if (successCount == 0) {
                log.info("[G3SessionMemory] 终止条件满足: {} 次修复全部失败", repairHistory.size());
                return true;
            }
        }

        return false;
    }

    /**
     * 检测是否为连续相同错误
     */
    public boolean isSameErrorRepeated(String newErrorSignature) {
        if (newErrorSignature == null || newErrorSignature.isBlank()) {
            return false;
        }

        if (newErrorSignature.equals(lastErrorSignature)) {
            return consecutiveSameErrorCount + 1 >= MAX_SAME_ERROR_TOLERANCE;
        }

        return false;
    }

    /**
     * 获取最近 N 轮修复历史
     */
    public List<RepairAttempt> getRecentHistory(int n) {
        if (n <= 0 || repairHistory.isEmpty()) {
            return Collections.emptyList();
        }

        int fromIndex = Math.max(0, repairHistory.size() - n);
        return new ArrayList<>(repairHistory.subList(fromIndex, repairHistory.size()));
    }

    /**
     * 构建 Coach 上下文
     */
    public String buildCoachContext() {
        if (repairHistory.isEmpty()) {
            return "(首次修复，无历史记录)\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### 修复历史\n");

        for (RepairAttempt attempt : repairHistory) {
            // v2.1.0增强：添加错误类型描述
            String errorType = G3ErrorSignature.getErrorTypeDescription(attempt.errorSignature());
            sb.append(String.format("- 第%d轮: 修复 %s → %s (%s)\n",
                    attempt.round(),
                    attempt.files(),
                    attempt.success() ? "✅ 成功" : "❌ 失败",
                    errorType));

            if (!attempt.success() && attempt.fixSummary() != null && !attempt.fixSummary().isBlank()) {
                sb.append(String.format("  失败摘要: %s\n", truncate(attempt.fixSummary(), 100)));
            }
        }

        // 添加失败方案警告
        List<RepairAttempt> failedAttempts = repairHistory.stream()
                .filter(a -> !a.success())
                .toList();

        if (!failedAttempts.isEmpty()) {
            sb.append("\n### 注意事项\n");
            sb.append("以下修复方案已尝试失败，请避免重复：\n");

            // v2.1.0增强：按错误类型分组显示
            Map<String, List<RepairAttempt>> byErrorType = failedAttempts.stream()
                    .filter(a -> a.errorSignature() != null)
                    .collect(Collectors.groupingBy(
                            a -> G3ErrorSignature.getErrorTypeDescription(a.errorSignature())));

            for (Map.Entry<String, List<RepairAttempt>> entry : byErrorType.entrySet()) {
                sb.append(String.format("- **%s** (出现 %d 次)\n", entry.getKey(), entry.getValue().size()));
                for (RepairAttempt attempt : entry.getValue()) {
                    sb.append(String.format("  - 第%d轮修复失败: %s\n",
                            attempt.round(), truncate(attempt.errorSignature(), 60)));
                }
            }
        }

        // 添加连续相同错误警告
        if (consecutiveSameErrorCount >= 2) {
            sb.append("\n### ⚠️ 警告\n");
            sb.append(String.format("已连续 %d 次出现相同类型错误，请尝试完全不同的修复策略！\n", consecutiveSameErrorCount));
            sb.append("建议考虑：\n");
            sb.append("- 检查方法签名是否与接口一致\n");
            sb.append("- 检查返回类型是否匹配（如 IPage vs Page）\n");
            sb.append("- 检查是否引用了未生成的类\n");
        }

        return sb.toString();
    }

    /**
     * 检查文件是否已修复过
     */
    public boolean hasRepairedFile(String filename) {
        if (filename == null)
            return false;
        return repairedFiles.contains(filename);
    }

    /**
     * 获取修复尝试总次数
     */
    public int getRepairAttemptCount() {
        return repairHistory.size();
    }

    /**
     * 获取成功修复次数
     */
    public long getSuccessCount() {
        return repairHistory.stream().filter(RepairAttempt::success).count();
    }

    /**
     * 截断字符串
     */
    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 单次修复尝试记录
     */
    public record RepairAttempt(
            /** 修复轮次 */
            int round,
            /** 时间戳 */
            Instant timestamp,
            /** 修复的文件列表 */
            List<String> files,
            /** 是否成功 */
            boolean success,
            /** 错误签名 */
            String errorSignature,
            /** 修复摘要（失败原因或成功说明） */
            String fixSummary) {
    }
}
