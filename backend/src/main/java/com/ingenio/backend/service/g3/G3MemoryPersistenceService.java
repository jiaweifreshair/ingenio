package com.ingenio.backend.service.g3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.entity.g3.G3SessionMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * G3 Memory 持久化服务
 *
 * 核心功能：
 * 1. 将 G3SessionMemory 持久化到 Redis
 * 2. 支持跨请求/重启恢复任务上下文
 * 3. 自动过期清理（默认24小时）
 *
 * Redis存储设计：
 * - Key格式：g3:memory:{jobId}
 * - 数据类型：String（JSON序列化）
 * - TTL：24小时自动过期
 *
 * 使用场景：
 * - 多阶段任务执行时保存中间状态
 * - 任务中断后恢复执行
 * - Coach 修复历史跨轮次保留
 *
 * @author Claude
 * @since 2.1.0 (M1: 扩展 SessionMemory 生命周期)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class G3MemoryPersistenceService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis Key前缀
     */
    @Value("${ingenio.g3.memory.key-prefix:g3:memory:}")
    private String keyPrefix;

    /**
     * 默认过期时间（小时）
     */
    @Value("${ingenio.g3.memory.ttl-hours:24}")
    private int ttlHours;

    /**
     * 保存 SessionMemory 到 Redis
     *
     * @param memory 要保存的 SessionMemory
     */
    public void saveMemory(G3SessionMemory memory) {
        if (memory == null || memory.getJobId() == null) {
            log.warn("[G3MemoryPersistence] 无法保存空的 SessionMemory");
            return;
        }

        String key = buildKey(memory.getJobId());
        try {
            // 序列化为 JSON
            String json = objectMapper.writeValueAsString(new SerializableMemory(memory));

            // 保存到 Redis，设置过期时间
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(ttlHours));

            log.debug("[G3MemoryPersistence] 保存 SessionMemory: jobId={}, ttl={}h",
                    memory.getJobId(), ttlHours);

        } catch (JsonProcessingException e) {
            log.error("[G3MemoryPersistence] 序列化 SessionMemory 失败: jobId={}, error={}",
                    memory.getJobId(), e.getMessage());
        }
    }

    /**
     * 从 Redis 加载 SessionMemory
     *
     * @param jobId 任务ID
     * @return SessionMemory（如果存在）
     */
    public Optional<G3SessionMemory> loadMemory(UUID jobId) {
        if (jobId == null) {
            return Optional.empty();
        }

        String key = buildKey(jobId);
        try {
            String json = redisTemplate.opsForValue().get(key);

            if (json == null || json.isBlank()) {
                log.debug("[G3MemoryPersistence] SessionMemory 不存在: jobId={}", jobId);
                return Optional.empty();
            }

            // 反序列化
            SerializableMemory sm = objectMapper.readValue(json, SerializableMemory.class);
            G3SessionMemory memory = sm.toSessionMemory();

            log.debug("[G3MemoryPersistence] 加载 SessionMemory: jobId={}, repairCount={}",
                    jobId, memory.getRepairAttemptCount());

            return Optional.of(memory);

        } catch (JsonProcessingException e) {
            log.error("[G3MemoryPersistence] 反序列化 SessionMemory 失败: jobId={}, error={}",
                    jobId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 删除 SessionMemory
     *
     * @param jobId 任务ID
     */
    public void deleteMemory(UUID jobId) {
        if (jobId == null) {
            return;
        }

        String key = buildKey(jobId);
        Boolean deleted = redisTemplate.delete(key);

        log.debug("[G3MemoryPersistence] 删除 SessionMemory: jobId={}, deleted={}",
                jobId, deleted);
    }

    /**
     * 检查 SessionMemory 是否存在
     *
     * @param jobId 任务ID
     * @return 是否存在
     */
    public boolean existsMemory(UUID jobId) {
        if (jobId == null) {
            return false;
        }

        String key = buildKey(jobId);
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 刷新 SessionMemory 的过期时间
     *
     * @param jobId 任务ID
     */
    public void refreshExpiration(UUID jobId) {
        if (jobId == null) {
            return;
        }

        String key = buildKey(jobId);
        redisTemplate.expire(key, Duration.ofHours(ttlHours));

        log.debug("[G3MemoryPersistence] 刷新过期时间: jobId={}, ttl={}h", jobId, ttlHours);
    }

    /**
     * 获取或创建 SessionMemory
     *
     * 如果 Redis 中存在则加载，否则创建新的
     *
     * @param jobId 任务ID
     * @return SessionMemory
     */
    public G3SessionMemory getOrCreate(UUID jobId) {
        return loadMemory(jobId).orElseGet(() -> {
            log.debug("[G3MemoryPersistence] 创建新的 SessionMemory: jobId={}", jobId);
            return new G3SessionMemory(jobId);
        });
    }

    /**
     * 构建 Redis Key
     */
    private String buildKey(UUID jobId) {
        return keyPrefix + jobId.toString();
    }

    /**
     * 可序列化的 Memory 包装类
     *
     * G3SessionMemory 包含 final 字段，不能直接反序列化
     * 使用此类作为中间层
     */
    public static class SerializableMemory {
        private UUID jobId;
        private java.util.List<RepairAttemptDTO> repairHistory;
        private java.util.Map<String, Integer> errorSignatureCount;
        private java.util.Set<String> repairedFiles;
        private String lastErrorSignature;
        private int consecutiveSameErrorCount;

        // 默认构造函数（Jackson 需要）
        public SerializableMemory() {}

        public SerializableMemory(G3SessionMemory memory) {
            this.jobId = memory.getJobId();
            this.repairHistory = memory.getRepairHistory().stream()
                    .map(RepairAttemptDTO::from)
                    .toList();
            this.errorSignatureCount = new java.util.HashMap<>(memory.getErrorSignatureCount());
            this.repairedFiles = new java.util.HashSet<>(memory.getRepairedFiles());
            this.lastErrorSignature = memory.getLastErrorSignature();
            this.consecutiveSameErrorCount = memory.getConsecutiveSameErrorCount();
        }

        public G3SessionMemory toSessionMemory() {
            G3SessionMemory memory = new G3SessionMemory(jobId);

            // 恢复修复历史
            for (RepairAttemptDTO dto : repairHistory) {
                memory.addRepairAttempt(
                        dto.round,
                        dto.files,
                        dto.success,
                        dto.errorSignature,
                        dto.fixSummary
                );
            }

            // 恢复错误签名计数
            memory.getErrorSignatureCount().putAll(errorSignatureCount);

            // 恢复已修复文件集合
            memory.getRepairedFiles().addAll(repairedFiles);

            // 恢复连续错误状态
            if (lastErrorSignature != null) {
                for (int i = 0; i < consecutiveSameErrorCount; i++) {
                    memory.recordErrorSignature(lastErrorSignature);
                }
            }

            return memory;
        }

        // Getters and Setters（Jackson 需要）
        public UUID getJobId() { return jobId; }
        public void setJobId(UUID jobId) { this.jobId = jobId; }
        public java.util.List<RepairAttemptDTO> getRepairHistory() { return repairHistory; }
        public void setRepairHistory(java.util.List<RepairAttemptDTO> repairHistory) { this.repairHistory = repairHistory; }
        public java.util.Map<String, Integer> getErrorSignatureCount() { return errorSignatureCount; }
        public void setErrorSignatureCount(java.util.Map<String, Integer> errorSignatureCount) { this.errorSignatureCount = errorSignatureCount; }
        public java.util.Set<String> getRepairedFiles() { return repairedFiles; }
        public void setRepairedFiles(java.util.Set<String> repairedFiles) { this.repairedFiles = repairedFiles; }
        public String getLastErrorSignature() { return lastErrorSignature; }
        public void setLastErrorSignature(String lastErrorSignature) { this.lastErrorSignature = lastErrorSignature; }
        public int getConsecutiveSameErrorCount() { return consecutiveSameErrorCount; }
        public void setConsecutiveSameErrorCount(int consecutiveSameErrorCount) { this.consecutiveSameErrorCount = consecutiveSameErrorCount; }
    }

    /**
     * RepairAttempt 的 DTO（用于序列化）
     */
    public static class RepairAttemptDTO {
        public int round;
        public java.time.Instant timestamp;
        public java.util.List<String> files;
        public boolean success;
        public String errorSignature;
        public String fixSummary;

        public RepairAttemptDTO() {}

        public static RepairAttemptDTO from(G3SessionMemory.RepairAttempt attempt) {
            RepairAttemptDTO dto = new RepairAttemptDTO();
            dto.round = attempt.round();
            dto.timestamp = attempt.timestamp();
            dto.files = attempt.files();
            dto.success = attempt.success();
            dto.errorSignature = attempt.errorSignature();
            dto.fixSummary = attempt.fixSummary();
            return dto;
        }
    }
}
