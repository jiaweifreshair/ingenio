package com.ingenio.backend.service;

import java.time.Instant;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.VersionDiff;
import com.ingenio.backend.dto.VersionTimelineItem;
import com.ingenio.backend.dto.VersionType;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.GenerationVersionEntity;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.mapper.GenerationVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 版本快照服务
 *
 * 核心功能：
 * 1. 创建版本快照 - 记录每个阶段的完整状态
 * 2. 版本历史查询 - 时光机时间线展示
 * 3. 版本对比 - 对比任意两个版本的差异
 * 4. 版本回滚 - 回退到历史版本
 *
 * 支持时光机调试功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionSnapshotService {

    private final GenerationVersionMapper versionMapper;
    private final GenerationTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    /**
     * 创建版本快照
     *
     * @param taskId       任务ID
     * @param tenantId     租户ID
     * @param versionType  版本类型
     * @param snapshotData 快照数据（完整状态）
     * @return 版本实体
     */
    public GenerationVersionEntity createSnapshot(
            UUID taskId,
            UUID tenantId,
            VersionType versionType,
            Map<String, Object> snapshotData
    ) {
        log.info("创建版本快照: taskId={}, type={}", taskId, versionType);

        try {
            // 获取当前最新版本号和时间戳
            GenerationVersionEntity latestVersionEntity = versionMapper.selectOne(
                    new QueryWrapper<GenerationVersionEntity>()
                            .eq("task_id", taskId)
                            .orderByDesc("created_at")
                            .last("LIMIT 1")
            );

            // 计算下一个版本号
            int nextVersion = 1;
            Instant createdAt = Instant.now();

            if (latestVersionEntity != null) {
                nextVersion = latestVersionEntity.getVersionNumber() + 1;

                // 确保新版本的创建时间晚于最新版本（处理测试中使用未来时间戳的情况）
                if (latestVersionEntity.getCreatedAt() != null &&
                    latestVersionEntity.getCreatedAt().isAfter(createdAt)) {
                    createdAt = latestVersionEntity.getCreatedAt().plusSeconds(1);
                }
            }

            // 创建版本实体
            GenerationVersionEntity entity = new GenerationVersionEntity();
            entity.setId(UUID.randomUUID());
            entity.setTenantId(tenantId);
            entity.setTaskId(taskId);
            entity.setVersionNumber(nextVersion);
            entity.setVersionType(versionType.name().toLowerCase());

            // 添加元数据
            Map<String, Object> enrichedData = new HashMap<>(snapshotData);
            enrichedData.put("version_type_display", versionType.getDisplayName());
            enrichedData.put("version_type_description", versionType.getDescription());
            enrichedData.put("created_at", createdAt.toString());

            entity.setSnapshot(enrichedData);
            entity.setCreatedAt(createdAt);

            // 保存到数据库
            versionMapper.insert(entity);

            log.info("版本快照创建成功: taskId={}, versionId={}, versionNumber={}",
                    taskId, entity.getId(), nextVersion);

            return entity;

        } catch (Exception e) {
            log.error("创建版本快照失败: taskId={}, type={}", taskId, versionType, e);
            throw new RuntimeException("创建版本快照失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取版本历史时间线
     *
     * @param taskId 任务ID
     * @return 时间线条目列表（倒序）
     */
    public List<VersionTimelineItem> getTimeline(UUID taskId) {
        log.info("获取版本历史时间线: taskId={}", taskId);

        List<GenerationVersionEntity> versions = versionMapper.selectList(
                new QueryWrapper<GenerationVersionEntity>()
                        .eq("task_id", taskId)
                        .orderByDesc("created_at")
        );

        return versions.stream()
                .map(this::buildTimelineItem)
                .collect(Collectors.toList());
    }

    /**
     * 对比两个版本
     *
     * @param version1Id 版本1 ID
     * @param version2Id 版本2 ID
     * @return 差异结果
     */
    public VersionDiff compareVersions(UUID version1Id, UUID version2Id) {
        log.info("对比版本: v1={}, v2={}", version1Id, version2Id);

        GenerationVersionEntity v1 = versionMapper.selectById(version1Id);
        GenerationVersionEntity v2 = versionMapper.selectById(version2Id);

        if (v1 == null || v2 == null) {
            throw new RuntimeException("版本不存在");
        }

        // 计算差异
        Map<String, Object> diff = calculateDiff(v1.getSnapshot(), v2.getSnapshot());
        int changeCount = countChanges(diff);
        boolean hasMajorChanges = detectMajorChanges(diff);
        String summary = generateChangeSummary(diff, v1, v2);

        return VersionDiff.builder()
                .version1(v1)
                .version2(v2)
                .differences(diff)
                .changeCount(changeCount)
                .changeSummary(summary)
                .hasMajorChanges(hasMajorChanges)
                .build();
    }

    /**
     * 回滚到指定版本
     *
     * @param versionId 目标版本ID
     * @return 新创建的ROLLBACK版本
     */
    @Transactional
    public GenerationVersionEntity rollbackToVersion(UUID versionId) {
        log.info("回滚到版本: versionId={}", versionId);

        GenerationVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在: " + versionId);
        }

        // 创建回滚快照（添加到原任务的版本历史中）
        Map<String, Object> rollbackSnapshot = new HashMap<>();
        rollbackSnapshot.put("rollback_from_version_id", versionId);
        rollbackSnapshot.put("rollback_from_version_number", version.getVersionNumber());
        rollbackSnapshot.put("rollback_at", Instant.now().toString());
        rollbackSnapshot.put("original_snapshot", version.getSnapshot());

        GenerationVersionEntity rollbackVersion = createSnapshot(version.getTaskId(), version.getTenantId(), VersionType.ROLLBACK, rollbackSnapshot);

        // 设置父版本ID指向被回滚的版本
        rollbackVersion.setParentVersionId(versionId);
        versionMapper.updateById(rollbackVersion);

        log.info("版本回滚成功: taskId={}, fromVersion={}, rollbackVersion={}",
                version.getTaskId(), version.getVersionNumber(), rollbackVersion.getId());

        return rollbackVersion;
    }

    /**
     * 获取指定版本详情
     *
     * @param versionId 版本ID
     * @return 版本实体
     */
    public GenerationVersionEntity getVersion(UUID versionId) {
        GenerationVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在: " + versionId);
        }
        return version;
    }

    /**
     * 删除版本（物理删除，慎用）
     *
     * @param versionId 版本ID
     */
    @Transactional
    public void deleteVersion(UUID versionId) {
        log.warn("删除版本: versionId={}", versionId);

        GenerationVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在: " + versionId);
        }

        // 检查是否有子版本依赖
        if (version.getParentVersionId() != null) {
            log.warn("警告：删除的版本有父版本引用: parentId={}", version.getParentVersionId());
        }

        versionMapper.deleteById(versionId);

        log.info("版本已删除: versionId={}", versionId);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建时间线条目
     */
    private VersionTimelineItem buildTimelineItem(GenerationVersionEntity version) {
        Map<String, Object> snapshot = version.getSnapshot();

        // 生成摘要
        String summary = generateSummary(version);

        // 判断状态
        String status = determineStatus(version);

        return VersionTimelineItem.builder()
                .versionId(version.getId())
                .versionNumber(version.getVersionNumber())
                .versionType(version.getVersionType())
                .versionTypeDisplay(snapshot.getOrDefault("version_type_display", "").toString())
                .timestamp(version.getCreatedAt())
                .summary(summary)
                .status(status)
                .canRollback(!version.getVersionType().equals("ROLLBACK"))
                .parentVersionId(version.getParentVersionId())
                .build();
    }

    /**
     * 生成版本摘要
     */
    private String generateSummary(GenerationVersionEntity version) {
        Map<String, Object> snapshot = version.getSnapshot();
        String versionType = version.getVersionType();

        switch (versionType) {
            case "PLAN":
                return String.format("分析需求，提取 %s 个实体",
                        snapshot.getOrDefault("entity_count", 0));

            case "SCHEMA":
                return String.format("生成 %s 个表的DDL",
                        snapshot.getOrDefault("table_count", 0));

            case "CODE":
                return String.format("生成 %s 个代码文件",
                        snapshot.getOrDefault("file_count", 0));

            case "VALIDATION_FAILED":
                return String.format("测试失败: %s",
                        snapshot.getOrDefault("failure_reason", "未知错误"));

            case "VALIDATION_SUCCESS":
                return String.format("所有测试通过，覆盖率 %s%%",
                        snapshot.getOrDefault("coverage", "0"));

            case "FIX":
                return String.format("修复: %s",
                        snapshot.getOrDefault("fix_description", "Bug修复"));

            case "ROLLBACK":
                return String.format("回滚到版本 %s",
                        snapshot.getOrDefault("rollback_from_version_number", ""));

            default:
                return "版本快照";
        }
    }

    /**
     * 判断版本状态
     */
    private String determineStatus(GenerationVersionEntity version) {
        String versionType = version.getVersionType();

        switch (versionType) {
            case "VALIDATION_FAILED":
                return "failed";
            case "VALIDATION_SUCCESS":
            case "FINAL":
                return "success";
            default:
                return "in_progress";
        }
    }

    /**
     * 计算两个快照的差异
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> calculateDiff(Map<String, Object> data1, Map<String, Object> data2) {
        Map<String, Object> diff = new HashMap<>();

        // 获取所有唯一键
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(data1.keySet());
        allKeys.addAll(data2.keySet());

        for (String key : allKeys) {
            Object value1 = data1.get(key);
            Object value2 = data2.get(key);

            if (value1 == null && value2 != null) {
                diff.put(key, Map.of("type", "added", "value", value2));
            } else if (value1 != null && value2 == null) {
                diff.put(key, Map.of("type", "removed", "value", value1));
            } else if (value1 != null && !value1.equals(value2)) {
                diff.put(key, Map.of(
                        "type", "changed",
                        "old_value", value1,
                        "new_value", value2
                ));
            }
        }

        return diff;
    }

    /**
     * 统计变更数量
     */
    private int countChanges(Map<String, Object> diff) {
        return diff.size();
    }

    /**
     * 检测是否有重大变更
     */
    @SuppressWarnings("unchecked")
    private boolean detectMajorChanges(Map<String, Object> diff) {
        // 如果Schema、代码、实体发生变更，视为重大变更
        for (String key : diff.keySet()) {
            if (key.contains("schema") ||
                key.contains("code") ||
                key.contains("entity") ||
                key.contains("table")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成变更摘要
     */
    private String generateChangeSummary(
            Map<String, Object> diff,
            GenerationVersionEntity v1,
            GenerationVersionEntity v2
    ) {
        if (diff.isEmpty()) {
            return "无变更";
        }

        StringBuilder summary = new StringBuilder();
        int addedCount = 0;
        int removedCount = 0;
        int changedCount = 0;

        for (Object value : diff.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> change = (Map<String, Object>) value;
            String type = (String) change.get("type");

            switch (type) {
                case "added":
                    addedCount++;
                    break;
                case "removed":
                    removedCount++;
                    break;
                case "changed":
                    changedCount++;
                    break;
            }
        }

        if (addedCount > 0) {
            summary.append(String.format("新增 %d 项；", addedCount));
        }
        if (removedCount > 0) {
            summary.append(String.format("删除 %d 项；", removedCount));
        }
        if (changedCount > 0) {
            summary.append(String.format("修改 %d 项；", changedCount));
        }

        return summary.toString();
    }

    /**
     * 获取TenantId
     */
    private UUID getTenantId(UUID taskId) {
        GenerationTaskEntity task = taskMapper.selectById(taskId);
        return task != null ? task.getTenantId() : null;
    }
}
