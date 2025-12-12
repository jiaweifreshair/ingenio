package com.ingenio.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.entity.GenerationTaskEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 生成任务状态管理器（Redis缓存实现）
 *
 * 功能：
 * - 缓存任务状态到Redis，减少数据库查询
 * - 支持任务状态快速读写
 * - 自动过期清理（TTL: 1小时）
 *
 * Redis存储设计：
 * - Key格式：ingenio:generation:task:{taskId}
 * - 数据类型：String（JSON序列化）
 * - TTL：1小时（任务完成后延长到24小时）
 *
 * 参考设计：
 * - 参考BuildStatusManager的Redis缓存模式
 * - 统一使用Jackson序列化/反序列化
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerationTaskStatusManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis Key前缀（从配置文件读取）
     */
    @Value("${ingenio.generation.task-status.key-prefix:ingenio:generation:task:}")
    private String keyPrefix;

    /**
     * TTL（小时）- 运行中任务的缓存时间
     */
    @Value("${ingenio.generation.task-status.running-ttl-hours:1}")
    private long runningTtlHours;

    /**
     * TTL（小时）- 已完成任务的缓存时间
     */
    @Value("${ingenio.generation.task-status.completed-ttl-hours:24}")
    private long completedTtlHours;

    /**
     * 保存任务状态到Redis
     *
     * @param taskId 任务ID
     * @param task 任务实体
     */
    public void saveTaskStatus(UUID taskId, GenerationTaskEntity task) {
        try {
            String key = buildKey(taskId);
            String json = objectMapper.writeValueAsString(task);

            // 根据任务状态设置不同的TTL
            long ttl = task.isFinished() ? completedTtlHours : runningTtlHours;
            redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.HOURS);

            log.debug("Redis保存任务状态 - taskId: {}, status: {}, ttl: {}h, size: {}B",
                    taskId, task.getStatus(), ttl, json.length());
        } catch (JsonProcessingException e) {
            log.error("序列化GenerationTaskEntity失败 - taskId: {}", taskId, e);
            throw new RuntimeException("保存任务状态失败", e);
        }
    }

    /**
     * 从Redis获取任务状态
     *
     * @param taskId 任务ID
     * @return 任务实体，如果不存在返回null
     */
    public GenerationTaskEntity getTaskStatus(UUID taskId) {
        try {
            String key = buildKey(taskId);
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                log.debug("Redis任务不存在（缓存miss） - taskId: {}", taskId);
                return null;
            }

            GenerationTaskEntity task = objectMapper.readValue(json, GenerationTaskEntity.class);
            log.debug("Redis查询任务状态（缓存hit） - taskId: {}, status: {}, size: {}B",
                    taskId, task.getStatus(), json.length());
            return task;
        } catch (JsonProcessingException e) {
            log.error("反序列化GenerationTaskEntity失败 - taskId: {}", taskId, e);
            throw new RuntimeException("查询任务状态失败", e);
        }
    }

    /**
     * 更新任务状态字段
     *
     * 从Redis读取→修改→写回Redis
     *
     * @param taskId 任务ID
     * @param status 新状态
     * @param currentAgent 当前Agent
     * @param progress 进度
     */
    public void updateTaskStatus(UUID taskId, String status, String currentAgent, Integer progress) {
        try {
            // 从Redis读取当前状态
            GenerationTaskEntity task = getTaskStatus(taskId);
            if (task == null) {
                log.warn("任务不存在，无法更新状态 - taskId: {}", taskId);
                return;
            }

            // 更新字段
            if (status != null) {
                task.setStatus(status);
            }
            if (currentAgent != null) {
                task.setCurrentAgent(currentAgent);
            }
            if (progress != null) {
                task.setProgress(progress);
            }

            // 写回Redis
            saveTaskStatus(taskId, task);

            log.info("更新任务状态 - taskId: {}, status: {}, agent: {}, progress: {}%",
                    taskId, status, currentAgent, progress);
        } catch (Exception e) {
            log.error("更新任务状态失败 - taskId: {}", taskId, e);
            throw new RuntimeException("更新任务状态失败", e);
        }
    }

    /**
     * 更新任务错误信息
     *
     * @param taskId 任务ID
     * @param errorMessage 错误信息
     */
    public void updateTaskError(UUID taskId, String errorMessage) {
        try {
            GenerationTaskEntity task = getTaskStatus(taskId);
            if (task == null) {
                log.warn("任务不存在，无法更新错误信息 - taskId: {}", taskId);
                return;
            }

            task.setErrorMessage(errorMessage);
            task.setStatus(GenerationTaskEntity.Status.FAILED.getValue());
            task.setCompletedAt(java.time.Instant.now());

            saveTaskStatus(taskId, task);

            log.error("记录任务错误 - taskId: {}, error: {}", taskId, errorMessage);
        } catch (Exception e) {
            log.error("更新任务错误失败 - taskId: {}", taskId, e);
        }
    }

    /**
     * 删除任务缓存
     *
     * @param taskId 任务ID
     */
    public void removeTaskStatus(UUID taskId) {
        String key = buildKey(taskId);
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("Redis删除任务状态 - taskId: {}", taskId);
        } else {
            log.warn("任务不存在，无法删除 - taskId: {}", taskId);
        }
    }

    /**
     * 获取所有任务数量
     *
     * @return 任务数量
     */
    public int getTaskCount() {
        try {
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");
            int count = keys != null ? keys.size() : 0;
            log.debug("Redis查询任务数量 - count: {}", count);
            return count;
        } catch (Exception e) {
            log.error("查询任务数量失败", e);
            return 0;
        }
    }

    /**
     * 清空所有任务缓存（慎用）
     */
    public void clearAll() {
        try {
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");
            int count = keys != null ? keys.size() : 0;

            if (count > 0 && keys != null) {
                redisTemplate.delete(keys);
                log.warn("Redis清空所有任务状态 - 删除数量: {}", count);
            } else {
                log.warn("Redis没有任务状态需要清空");
            }
        } catch (Exception e) {
            log.error("清空任务状态失败", e);
        }
    }

    /**
     * 生成Redis Key
     *
     * @param taskId 任务ID
     * @return Redis Key
     */
    private String buildKey(UUID taskId) {
        return keyPrefix + taskId.toString();
    }
}
