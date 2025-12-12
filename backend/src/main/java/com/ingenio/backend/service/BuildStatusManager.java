package com.ingenio.backend.service;

import java.time.Instant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.response.PublishResponse;
import com.ingenio.backend.dto.response.PublishResponse.PlatformBuildResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 构建状态管理器（Redis持久化实现）
 *
 * 功能：
 * - 存储构建任务的状态信息到Redis
 * - 更新平台构建进度和状态
 * - 查询构建任务状态
 * - 自动过期清理（30天TTL）
 *
 * Redis存储设计：
 * - Key格式：ingenio:publish:build:{buildId}
 * - 数据类型：String（JSON序列化）
 * - TTL：30天自动过期
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuildStatusManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis Key前缀（从配置文件读取）
     */
    @Value("${ingenio.publish.build-status.key-prefix:ingenio:publish:build:}")
    private String keyPrefix;

    /**
     * TTL（天）- 从配置文件读取
     */
    @Value("${ingenio.publish.build-status.ttl-days:30}")
    private long ttlDays;

    /**
     * 保存构建任务初始状态
     *
     * @param buildId 构建任务ID
     * @param response 初始状态响应
     */
    public void saveBuildStatus(String buildId, PublishResponse response) {
        try {
            String key = buildKey(buildId);
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, ttlDays, TimeUnit.DAYS);

            log.info("Redis保存构建状态 - buildId: {}, status: {}, platforms: {}, size: {}B",
                    buildId, response.getStatus(), response.getPlatforms(), json.length());
        } catch (JsonProcessingException e) {
            log.error("序列化PublishResponse失败 - buildId: {}", buildId, e);
            throw new RuntimeException("保存构建状态失败", e);
        }
    }

    /**
     * 获取构建任务状态
     *
     * @param buildId 构建任务ID
     * @return 构建状态响应，如果不存在返回null
     */
    public PublishResponse getBuildStatus(String buildId) {
        try {
            String key = buildKey(buildId);
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                log.warn("Redis构建任务不存在 - buildId: {}", buildId);
                return null;
            }

            PublishResponse response = objectMapper.readValue(json, PublishResponse.class);
            log.debug("Redis查询构建状态 - buildId: {}, status: {}, size: {}B",
                    buildId, response.getStatus(), json.length());
            return response;
        } catch (JsonProcessingException e) {
            log.error("反序列化PublishResponse失败 - buildId: {}", buildId, e);
            throw new RuntimeException("查询构建状态失败", e);
        }
    }

    /**
     * 更新平台构建状态
     *
     * @param buildId 构建任务ID
     * @param platform 平台类型
     * @param status 状态（PENDING, IN_PROGRESS, SUCCESS, FAILED, CANCELLED）
     * @param progress 进度（0-100）
     */
    public void updatePlatformStatus(String buildId, String platform, String status, int progress) {
        try {
            // 从Redis读取当前状态
            PublishResponse response = getBuildStatus(buildId);
            if (response == null) {
                log.warn("构建任务不存在，无法更新平台状态 - buildId: {}, platform: {}", buildId, platform);
                return;
            }

            // 获取平台构建结果
            PlatformBuildResult platformResult = response.getPlatformResults().get(platform);
            if (platformResult == null) {
                log.warn("平台不存在 - buildId: {}, platform: {}", buildId, platform);
                return;
            }

            // 更新平台状态
            platformResult.setStatus(status);
            platformResult.setProgress(progress);

            // 更新时间
            if ("IN_PROGRESS".equals(status) && platformResult.getStartedAt() == null) {
                platformResult.setStartedAt(Instant.now());
            }
            if (("SUCCESS".equals(status) || "FAILED".equals(status)) && platformResult.getCompletedAt() == null) {
                platformResult.setCompletedAt(Instant.now());
            }

            // 更新整体状态
            updateOverallStatus(response);

            // 更新最后修改时间
            response.setUpdatedAt(Instant.now());

            // 保存回Redis
            saveBuildStatus(buildId, response);

            log.info("更新平台状态 - buildId: {}, platform: {}, status: {}, progress: {}%",
                    buildId, platform, status, progress);
        } catch (Exception e) {
            log.error("更新平台状态失败 - buildId: {}, platform: {}", buildId, platform, e);
            throw new RuntimeException("更新平台状态失败", e);
        }
    }

    /**
     * 更新平台构建错误信息
     *
     * @param buildId 构建任务ID
     * @param platform 平台类型
     * @param errorMessage 错误信息
     */
    public void updatePlatformError(String buildId, String platform, String errorMessage) {
        try {
            PublishResponse response = getBuildStatus(buildId);
            if (response == null) {
                log.warn("构建任务不存在，无法更新错误信息 - buildId: {}, platform: {}", buildId, platform);
                return;
            }

            PlatformBuildResult platformResult = response.getPlatformResults().get(platform);
            if (platformResult != null) {
                platformResult.setErrorMessage(errorMessage);
                platformResult.setStatus("FAILED");
                platformResult.setProgress(100);
                platformResult.setCompletedAt(Instant.now());

                updateOverallStatus(response);
                response.setUpdatedAt(Instant.now());

                // 保存回Redis
                saveBuildStatus(buildId, response);

                log.error("记录平台构建错误 - buildId: {}, platform: {}, error: {}",
                        buildId, platform, errorMessage);
            }
        } catch (Exception e) {
            log.error("更新平台错误失败 - buildId: {}, platform: {}", buildId, platform, e);
        }
    }

    /**
     * 更新平台下载URL
     *
     * @param buildId 构建任务ID
     * @param platform 平台类型
     * @param downloadUrl 下载URL
     */
    public void updatePlatformDownloadUrl(String buildId, String platform, String downloadUrl) {
        try {
            PublishResponse response = getBuildStatus(buildId);
            if (response == null) {
                log.warn("构建任务不存在，无法更新下载URL - buildId: {}, platform: {}", buildId, platform);
                return;
            }

            PlatformBuildResult platformResult = response.getPlatformResults().get(platform);
            if (platformResult != null) {
                platformResult.setDownloadUrl(downloadUrl);
                response.setUpdatedAt(Instant.now());

                // 保存回Redis
                saveBuildStatus(buildId, response);

                log.info("更新平台下载URL - buildId: {}, platform: {}, url: {}",
                        buildId, platform, downloadUrl);
            }
        } catch (Exception e) {
            log.error("更新下载URL失败 - buildId: {}, platform: {}", buildId, platform, e);
        }
    }

    /**
     * 删除构建任务状态（清理缓存）
     *
     * @param buildId 构建任务ID
     */
    public void removeBuildStatus(String buildId) {
        String key = buildKey(buildId);
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("Redis删除构建状态 - buildId: {}", buildId);
        } else {
            log.warn("构建任务不存在，无法删除 - buildId: {}", buildId);
        }
    }

    /**
     * 更新整体构建状态
     *
     * 根据所有平台的状态计算整体状态：
     * - 所有平台SUCCESS -> SUCCESS
     * - 任意平台FAILED -> FAILED
     * - 任意平台IN_PROGRESS -> IN_PROGRESS
     * - 所有平台PENDING -> PENDING
     *
     * @param response 发布响应
     */
    private void updateOverallStatus(PublishResponse response) {
        Map<String, PlatformBuildResult> platformResults = response.getPlatformResults();

        boolean hasInProgress = false;
        boolean hasFailed = false;
        boolean allSuccess = true;

        for (PlatformBuildResult result : platformResults.values()) {
            String status = result.getStatus();

            if ("FAILED".equals(status)) {
                hasFailed = true;
                allSuccess = false;
            } else if ("IN_PROGRESS".equals(status)) {
                hasInProgress = true;
                allSuccess = false;
            } else if (!"SUCCESS".equals(status)) {
                allSuccess = false;
            }
        }

        // 确定整体状态
        String overallStatus;
        if (hasFailed) {
            overallStatus = "FAILED";
        } else if (hasInProgress) {
            overallStatus = "IN_PROGRESS";
        } else if (allSuccess) {
            overallStatus = "SUCCESS";
        } else {
            overallStatus = "PENDING";
        }

        response.setStatus(overallStatus);
        log.debug("更新整体状态 - buildId: {}, status: {}", response.getBuildId(), overallStatus);
    }

    /**
     * 获取所有构建任务数量
     *
     * @return 任务数量
     */
    public int getBuildCount() {
        try {
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");
            int count = keys != null ? keys.size() : 0;
            log.debug("Redis查询构建任务数量 - count: {}", count);
            return count;
        } catch (Exception e) {
            log.error("查询构建任务数量失败", e);
            return 0;
        }
    }

    /**
     * 清空所有构建任务状态（慎用）
     */
    public void clearAll() {
        try {
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");
            int count = keys != null ? keys.size() : 0;

            if (count > 0 && keys != null) {
                redisTemplate.delete(keys);
                log.warn("Redis清空所有构建状态 - 删除数量: {}", count);
            } else {
                log.warn("Redis没有构建状态需要清空");
            }
        } catch (Exception e) {
            log.error("清空构建状态失败", e);
        }
    }

    /**
     * 生成Redis Key
     *
     * @param buildId 构建任务ID
     * @return Redis Key
     */
    private String buildKey(String buildId) {
        return keyPrefix + buildId;
    }
}
