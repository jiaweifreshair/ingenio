package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ingenio.backend.dto.VersionDiff;
import com.ingenio.backend.dto.VersionTimelineItem;
import com.ingenio.backend.dto.VersionType;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.GenerationVersionEntity;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.mapper.GenerationVersionMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 时光机高级功能服务
 *
 * <p>V2.0新增高级功能：</p>
 * <ul>
 *   <li>版本标签管理 - 为重要版本添加标签（如"已发布"、"里程碑"）</li>
 *   <li>分支创建 - 从任意版本创建新的任务分支</li>
 *   <li>代码级别对比 - 详细的代码文件级别差异对比</li>
 *   <li>版本搜索 - 按类型、日期范围、状态搜索版本</li>
 *   <li>批量操作 - 批量删除、批量标记</li>
 *   <li>版本导出 - 导出版本快照用于备份或分享</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-17 V2.0高级功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeMachineAdvancedService {

    private final GenerationVersionMapper versionMapper;
    private final GenerationTaskMapper taskMapper;
    private final VersionSnapshotService snapshotService;

    // ==================== 1. 版本标签管理 ====================

    /**
     * 为版本添加标签
     *
     * @param versionId 版本ID
     * @param tag       标签名称（如"milestone"、"released"、"stable"）
     * @return 更新后的版本
     */
    @Transactional
    public GenerationVersionEntity addTag(UUID versionId, String tag) {
        log.info("[TimeMachine] 添加版本标签: versionId={}, tag={}", versionId, tag);

        GenerationVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在: " + versionId);
        }

        // 获取或创建标签列表
        Map<String, Object> snapshot = version.getSnapshot();
        if (snapshot == null) {
            snapshot = new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) snapshot.getOrDefault("tags", new ArrayList<>());
        if (tags == null) {
            tags = new ArrayList<>();
        }

        // 添加标签（去重）
        if (!tags.contains(tag)) {
            tags = new ArrayList<>(tags);
            tags.add(tag);
            snapshot.put("tags", tags);
            snapshot.put("tagged_at", Instant.now().toString());
            version.setSnapshot(snapshot);
            versionMapper.updateById(version);
            log.info("[TimeMachine] 标签添加成功: versionId={}, tags={}", versionId, tags);
        }

        return version;
    }

    /**
     * 移除版本标签
     *
     * @param versionId 版本ID
     * @param tag       要移除的标签
     * @return 更新后的版本
     */
    @Transactional
    public GenerationVersionEntity removeTag(UUID versionId, String tag) {
        log.info("[TimeMachine] 移除版本标签: versionId={}, tag={}", versionId, tag);

        GenerationVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在: " + versionId);
        }

        Map<String, Object> snapshot = version.getSnapshot();
        if (snapshot != null) {
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) snapshot.get("tags");
            if (tags != null && tags.contains(tag)) {
                tags = new ArrayList<>(tags);
                tags.remove(tag);
                snapshot.put("tags", tags);
                version.setSnapshot(snapshot);
                versionMapper.updateById(version);
                log.info("[TimeMachine] 标签移除成功: versionId={}, remainingTags={}", versionId, tags);
            }
        }

        return version;
    }

    /**
     * 获取带指定标签的所有版本
     *
     * @param taskId 任务ID
     * @param tag    标签名称
     * @return 带该标签的版本列表
     */
    public List<GenerationVersionEntity> getVersionsByTag(UUID taskId, String tag) {
        log.info("[TimeMachine] 按标签查询版本: taskId={}, tag={}", taskId, tag);

        List<GenerationVersionEntity> allVersions = versionMapper.selectList(
                new QueryWrapper<GenerationVersionEntity>()
                        .eq("task_id", taskId)
                        .orderByDesc("created_at")
        );

        return allVersions.stream()
                .filter(v -> {
                    Map<String, Object> snapshot = v.getSnapshot();
                    if (snapshot == null) return false;
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) snapshot.get("tags");
                    return tags != null && tags.contains(tag);
                })
                .collect(Collectors.toList());
    }

    // ==================== 2. 分支创建 ====================

    /**
     * 从指定版本创建新分支（新任务）
     *
     * <p>场景：用户想从某个历史版本开始，尝试不同的实现方案</p>
     *
     * @param versionId   源版本ID
     * @param branchName  分支名称
     * @return 新创建的任务
     */
    @Transactional
    public BranchResult createBranch(UUID versionId, String branchName) {
        log.info("[TimeMachine] 创建版本分支: versionId={}, branchName={}", versionId, branchName);

        GenerationVersionEntity sourceVersion = versionMapper.selectById(versionId);
        if (sourceVersion == null) {
            throw new RuntimeException("源版本不存在: " + versionId);
        }

        // 获取原任务信息
        GenerationTaskEntity sourceTask = taskMapper.selectById(sourceVersion.getTaskId());
        if (sourceTask == null) {
            throw new RuntimeException("源任务不存在");
        }

        // 创建新任务作为分支
        GenerationTaskEntity branchTask = new GenerationTaskEntity();
        branchTask.setId(UUID.randomUUID());
        branchTask.setTenantId(sourceTask.getTenantId());
        branchTask.setUserId(sourceTask.getUserId());
        // 从源任务复制名称，添加分支标识
        String branchTaskName = sourceTask.getTaskName() != null
            ? sourceTask.getTaskName() + " [分支: " + branchName + "]"
            : "分支任务: " + branchName;
        branchTask.setTaskName(branchTaskName);
        branchTask.setUserRequirement(sourceTask.getUserRequirement() != null
            ? sourceTask.getUserRequirement()
            : "从版本 " + versionId + " 创建的分支");
        branchTask.setStatus("pending");  // 使用小写状态值
        branchTask.setCreatedAt(Instant.now());
        branchTask.setUpdatedAt(Instant.now());

        // 在metadata中记录分支来源
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("branch_name", branchName);
        metadata.put("source_task_id", sourceTask.getId().toString());
        metadata.put("source_version_id", versionId.toString());
        metadata.put("source_version_number", sourceVersion.getVersionNumber());
        metadata.put("branched_at", Instant.now().toString());
        branchTask.setMetadata(metadata);

        taskMapper.insert(branchTask);

        // 为新任务创建初始版本（复制源版本快照）
        Map<String, Object> branchSnapshot = new HashMap<>(sourceVersion.getSnapshot());
        branchSnapshot.put("branch_source", Map.of(
                "task_id", sourceTask.getId().toString(),
                "version_id", versionId.toString(),
                "version_number", sourceVersion.getVersionNumber()
        ));
        branchSnapshot.put("branch_name", branchName);

        GenerationVersionEntity branchVersion = snapshotService.createSnapshot(
                branchTask.getId(),
                branchTask.getTenantId(),
                VersionType.PLAN,
                branchSnapshot
        );

        log.info("[TimeMachine] 分支创建成功: newTaskId={}, newVersionId={}",
                branchTask.getId(), branchVersion.getId());

        return BranchResult.builder()
                .branchTaskId(branchTask.getId())
                .branchVersionId(branchVersion.getId())
                .branchName(branchName)
                .sourceVersionId(versionId)
                .sourceVersionNumber(sourceVersion.getVersionNumber())
                .createdAt(Instant.now())
                .build();
    }

    /**
     * 分支创建结果
     */
    @Data
    @Builder
    public static class BranchResult {
        private UUID branchTaskId;
        private UUID branchVersionId;
        private String branchName;
        private UUID sourceVersionId;
        private Integer sourceVersionNumber;
        private Instant createdAt;
    }

    // ==================== 3. 代码级别对比 ====================

    /**
     * 获取详细的代码文件级别差异
     *
     * @param version1Id 版本1 ID
     * @param version2Id 版本2 ID
     * @return 代码级别差异结果
     */
    public CodeLevelDiff getCodeLevelDiff(UUID version1Id, UUID version2Id) {
        log.info("[TimeMachine] 代码级别对比: v1={}, v2={}", version1Id, version2Id);

        GenerationVersionEntity v1 = versionMapper.selectById(version1Id);
        GenerationVersionEntity v2 = versionMapper.selectById(version2Id);

        if (v1 == null || v2 == null) {
            throw new RuntimeException("版本不存在");
        }

        Map<String, Object> snapshot1 = v1.getSnapshot();
        Map<String, Object> snapshot2 = v2.getSnapshot();

        List<FileDiff> fileDiffs = new ArrayList<>();
        int addedFiles = 0;
        int removedFiles = 0;
        int modifiedFiles = 0;
        int totalLinesAdded = 0;
        int totalLinesRemoved = 0;

        // 提取代码文件
        @SuppressWarnings("unchecked")
        Map<String, String> files1 = extractCodeFiles(snapshot1);
        @SuppressWarnings("unchecked")
        Map<String, String> files2 = extractCodeFiles(snapshot2);

        // 合并所有文件路径
        Set<String> allFilePaths = new HashSet<>();
        allFilePaths.addAll(files1.keySet());
        allFilePaths.addAll(files2.keySet());

        for (String filePath : allFilePaths) {
            String content1 = files1.get(filePath);
            String content2 = files2.get(filePath);

            if (content1 == null && content2 != null) {
                // 新增文件
                int lines = countLines(content2);
                fileDiffs.add(FileDiff.builder()
                        .filePath(filePath)
                        .changeType("added")
                        .linesAdded(lines)
                        .linesRemoved(0)
                        .newContent(content2)
                        .build());
                addedFiles++;
                totalLinesAdded += lines;

            } else if (content1 != null && content2 == null) {
                // 删除文件
                int lines = countLines(content1);
                fileDiffs.add(FileDiff.builder()
                        .filePath(filePath)
                        .changeType("removed")
                        .linesAdded(0)
                        .linesRemoved(lines)
                        .oldContent(content1)
                        .build());
                removedFiles++;
                totalLinesRemoved += lines;

            } else if (content1 != null && !content1.equals(content2)) {
                // 修改文件
                LineDiff lineDiff = calculateLineDiff(content1, content2);
                fileDiffs.add(FileDiff.builder()
                        .filePath(filePath)
                        .changeType("modified")
                        .linesAdded(lineDiff.added)
                        .linesRemoved(lineDiff.removed)
                        .oldContent(content1)
                        .newContent(content2)
                        .diffHunks(lineDiff.hunks)
                        .build());
                modifiedFiles++;
                totalLinesAdded += lineDiff.added;
                totalLinesRemoved += lineDiff.removed;
            }
        }

        return CodeLevelDiff.builder()
                .version1Id(version1Id)
                .version2Id(version2Id)
                .version1Number(v1.getVersionNumber())
                .version2Number(v2.getVersionNumber())
                .fileDiffs(fileDiffs)
                .addedFiles(addedFiles)
                .removedFiles(removedFiles)
                .modifiedFiles(modifiedFiles)
                .totalLinesAdded(totalLinesAdded)
                .totalLinesRemoved(totalLinesRemoved)
                .build();
    }

    /**
     * 代码级别差异结果
     */
    @Data
    @Builder
    public static class CodeLevelDiff {
        private UUID version1Id;
        private UUID version2Id;
        private Integer version1Number;
        private Integer version2Number;
        private List<FileDiff> fileDiffs;
        private Integer addedFiles;
        private Integer removedFiles;
        private Integer modifiedFiles;
        private Integer totalLinesAdded;
        private Integer totalLinesRemoved;
    }

    /**
     * 单个文件差异
     */
    @Data
    @Builder
    public static class FileDiff {
        private String filePath;
        private String changeType;
        private Integer linesAdded;
        private Integer linesRemoved;
        private String oldContent;
        private String newContent;
        private List<String> diffHunks;
    }

    // ==================== 4. 版本搜索 ====================

    /**
     * 按条件搜索版本
     *
     * @param taskId     任务ID
     * @param searchParams 搜索参数
     * @return 符合条件的版本列表
     */
    public List<VersionTimelineItem> searchVersions(UUID taskId, VersionSearchParams searchParams) {
        log.info("[TimeMachine] 搜索版本: taskId={}, params={}", taskId, searchParams);

        QueryWrapper<GenerationVersionEntity> query = new QueryWrapper<>();
        query.eq("task_id", taskId);

        // 按类型筛选
        if (searchParams.getVersionTypes() != null && !searchParams.getVersionTypes().isEmpty()) {
            query.in("version_type", searchParams.getVersionTypes());
        }

        // 按时间范围筛选
        if (searchParams.getStartTime() != null) {
            query.ge("created_at", searchParams.getStartTime());
        }
        if (searchParams.getEndTime() != null) {
            query.le("created_at", searchParams.getEndTime());
        }

        // 按版本号范围筛选
        if (searchParams.getMinVersion() != null) {
            query.ge("version_number", searchParams.getMinVersion());
        }
        if (searchParams.getMaxVersion() != null) {
            query.le("version_number", searchParams.getMaxVersion());
        }

        query.orderByDesc("created_at");

        List<GenerationVersionEntity> versions = versionMapper.selectList(query);

        // 后处理：按状态筛选（需要解析snapshot）
        if (searchParams.getStatuses() != null && !searchParams.getStatuses().isEmpty()) {
            versions = versions.stream()
                    .filter(v -> {
                        String status = determineStatus(v);
                        return searchParams.getStatuses().contains(status);
                    })
                    .collect(Collectors.toList());
        }

        // 后处理：按标签筛选
        if (searchParams.getTags() != null && !searchParams.getTags().isEmpty()) {
            versions = versions.stream()
                    .filter(v -> {
                        Map<String, Object> snapshot = v.getSnapshot();
                        if (snapshot == null) return false;
                        @SuppressWarnings("unchecked")
                        List<String> tags = (List<String>) snapshot.get("tags");
                        if (tags == null) return false;
                        return tags.stream().anyMatch(t -> searchParams.getTags().contains(t));
                    })
                    .collect(Collectors.toList());
        }

        // 转换为时间线条目
        return versions.stream()
                .map(this::buildTimelineItem)
                .collect(Collectors.toList());
    }

    /**
     * 版本搜索参数
     */
    @Data
    @Builder
    public static class VersionSearchParams {
        private List<String> versionTypes;
        private List<String> statuses;
        private List<String> tags;
        private Instant startTime;
        private Instant endTime;
        private Integer minVersion;
        private Integer maxVersion;
    }

    // ==================== 5. 批量操作 ====================

    /**
     * 批量删除版本（软删除后指定版本）
     *
     * @param taskId        任务ID
     * @param afterVersionNumber 删除此版本号之后的所有版本
     * @return 删除的版本数量
     */
    @Transactional
    public int deleteVersionsAfter(UUID taskId, int afterVersionNumber) {
        log.warn("[TimeMachine] 批量删除版本: taskId={}, afterVersion={}", taskId, afterVersionNumber);

        List<GenerationVersionEntity> toDelete = versionMapper.selectList(
                new QueryWrapper<GenerationVersionEntity>()
                        .eq("task_id", taskId)
                        .gt("version_number", afterVersionNumber)
        );

        for (GenerationVersionEntity version : toDelete) {
            versionMapper.deleteById(version.getId());
        }

        log.info("[TimeMachine] 批量删除完成: 删除了 {} 个版本", toDelete.size());
        return toDelete.size();
    }

    /**
     * 批量添加标签
     *
     * @param versionIds 版本ID列表
     * @param tag        标签
     * @return 成功标记的版本数量
     */
    @Transactional
    public int batchAddTag(List<UUID> versionIds, String tag) {
        log.info("[TimeMachine] 批量添加标签: count={}, tag={}", versionIds.size(), tag);

        int successCount = 0;
        for (UUID versionId : versionIds) {
            try {
                addTag(versionId, tag);
                successCount++;
            } catch (Exception e) {
                log.warn("[TimeMachine] 标签添加失败: versionId={}, error={}", versionId, e.getMessage());
            }
        }

        return successCount;
    }

    // ==================== 6. 版本导出 ====================

    /**
     * 导出版本快照
     *
     * @param versionId 版本ID
     * @return 导出数据（可用于备份或分享）
     */
    public VersionExportData exportVersion(UUID versionId) {
        log.info("[TimeMachine] 导出版本: versionId={}", versionId);

        GenerationVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在: " + versionId);
        }

        GenerationTaskEntity task = taskMapper.selectById(version.getTaskId());

        return VersionExportData.builder()
                .exportedAt(Instant.now())
                .exportVersion("2.0")
                .versionId(version.getId())
                .versionNumber(version.getVersionNumber())
                .versionType(version.getVersionType())
                .taskId(version.getTaskId())
                .tenantId(version.getTenantId())
                .snapshot(version.getSnapshot())
                .taskMetadata(task != null ? task.getMetadata() : null)
                .createdAt(version.getCreatedAt())
                .build();
    }

    /**
     * 导出数据格式
     */
    @Data
    @Builder
    public static class VersionExportData {
        private Instant exportedAt;
        private String exportVersion;
        private UUID versionId;
        private Integer versionNumber;
        private String versionType;
        private UUID taskId;
        private UUID tenantId;
        private Map<String, Object> snapshot;
        private Map<String, Object> taskMetadata;
        private Instant createdAt;
    }

    /**
     * 导入版本（从导出数据创建新版本）
     *
     * @param targetTaskId 目标任务ID
     * @param exportData   导出数据
     * @return 新创建的版本
     */
    @Transactional
    public GenerationVersionEntity importVersion(UUID targetTaskId, VersionExportData exportData) {
        log.info("[TimeMachine] 导入版本: targetTaskId={}, sourceVersionId={}",
                targetTaskId, exportData.getVersionId());

        GenerationTaskEntity targetTask = taskMapper.selectById(targetTaskId);
        if (targetTask == null) {
            throw new RuntimeException("目标任务不存在: " + targetTaskId);
        }

        // 在快照中记录导入来源
        Map<String, Object> importedSnapshot = new HashMap<>(exportData.getSnapshot());
        importedSnapshot.put("imported_from", Map.of(
                "source_version_id", exportData.getVersionId().toString(),
                "source_task_id", exportData.getTaskId().toString(),
                "imported_at", Instant.now().toString(),
                "original_created_at", exportData.getCreatedAt().toString()
        ));

        return snapshotService.createSnapshot(
                targetTaskId,
                targetTask.getTenantId(),
                VersionType.valueOf(exportData.getVersionType().toUpperCase()),
                importedSnapshot
        );
    }

    // ==================== 7. 版本统计 ====================

    /**
     * 获取版本统计信息
     *
     * @param taskId 任务ID
     * @return 统计信息
     */
    public VersionStatistics getStatistics(UUID taskId) {
        log.info("[TimeMachine] 获取版本统计: taskId={}", taskId);

        List<GenerationVersionEntity> versions = versionMapper.selectList(
                new QueryWrapper<GenerationVersionEntity>()
                        .eq("task_id", taskId)
                        .orderByAsc("created_at")
        );

        if (versions.isEmpty()) {
            return VersionStatistics.builder()
                    .totalVersions(0)
                    .build();
        }

        // 按类型统计
        Map<String, Long> byType = versions.stream()
                .collect(Collectors.groupingBy(
                        GenerationVersionEntity::getVersionType,
                        Collectors.counting()
                ));

        // 按状态统计
        Map<String, Long> byStatus = versions.stream()
                .collect(Collectors.groupingBy(
                        this::determineStatus,
                        Collectors.counting()
                ));

        // 计算时间跨度
        Instant firstVersion = versions.get(0).getCreatedAt();
        Instant lastVersion = versions.get(versions.size() - 1).getCreatedAt();

        // 统计回滚次数
        long rollbackCount = versions.stream()
                .filter(v -> "rollback".equalsIgnoreCase(v.getVersionType()))
                .count();

        // 统计带标签的版本
        long taggedCount = versions.stream()
                .filter(v -> {
                    Map<String, Object> snapshot = v.getSnapshot();
                    if (snapshot == null) return false;
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) snapshot.get("tags");
                    return tags != null && !tags.isEmpty();
                })
                .count();

        return VersionStatistics.builder()
                .totalVersions(versions.size())
                .versionsByType(byType)
                .versionsByStatus(byStatus)
                .firstVersionTime(firstVersion)
                .lastVersionTime(lastVersion)
                .rollbackCount((int) rollbackCount)
                .taggedVersionCount((int) taggedCount)
                .build();
    }

    /**
     * 版本统计信息
     */
    @Data
    @Builder
    public static class VersionStatistics {
        private Integer totalVersions;
        private Map<String, Long> versionsByType;
        private Map<String, Long> versionsByStatus;
        private Instant firstVersionTime;
        private Instant lastVersionTime;
        private Integer rollbackCount;
        private Integer taggedVersionCount;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 从快照中提取代码文件
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> extractCodeFiles(Map<String, Object> snapshot) {
        Map<String, String> files = new HashMap<>();

        if (snapshot == null) {
            return files;
        }

        // 提取各类代码文件
        extractFilesFromMap(snapshot, "entities", files);
        extractFilesFromMap(snapshot, "services", files);
        extractFilesFromMap(snapshot, "controllers", files);
        extractFilesFromMap(snapshot, "components", files);
        extractFilesFromMap(snapshot, "pages", files);

        // 提取单独的代码字段
        if (snapshot.containsKey("migration_sql")) {
            files.put("migration.sql", String.valueOf(snapshot.get("migration_sql")));
        }

        return files;
    }

    @SuppressWarnings("unchecked")
    private void extractFilesFromMap(Map<String, Object> snapshot, String key, Map<String, String> files) {
        Object value = snapshot.get(key);
        if (value instanceof Map) {
            Map<String, Object> codeMap = (Map<String, Object>) value;
            for (Map.Entry<String, Object> entry : codeMap.entrySet()) {
                if (entry.getValue() instanceof String) {
                    files.put(key + "/" + entry.getKey(), (String) entry.getValue());
                }
            }
        }
    }

    /**
     * 统计行数
     */
    private int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.split("\n").length;
    }

    /**
     * 计算行级别差异
     */
    private LineDiff calculateLineDiff(String content1, String content2) {
        String[] lines1 = content1.split("\n");
        String[] lines2 = content2.split("\n");

        // 简化的行差异计算
        Set<String> set1 = new HashSet<>(Arrays.asList(lines1));
        Set<String> set2 = new HashSet<>(Arrays.asList(lines2));

        int removed = 0;
        int added = 0;

        for (String line : lines1) {
            if (!set2.contains(line)) {
                removed++;
            }
        }

        for (String line : lines2) {
            if (!set1.contains(line)) {
                added++;
            }
        }

        // 生成diff hunks（简化版）
        List<String> hunks = new ArrayList<>();
        if (removed > 0) {
            hunks.add(String.format("- %d 行被删除", removed));
        }
        if (added > 0) {
            hunks.add(String.format("+ %d 行被添加", added));
        }

        LineDiff diff = new LineDiff();
        diff.added = added;
        diff.removed = removed;
        diff.hunks = hunks;
        return diff;
    }

    private static class LineDiff {
        int added;
        int removed;
        List<String> hunks;
    }

    /**
     * 判断版本状态
     */
    private String determineStatus(GenerationVersionEntity version) {
        String versionType = version.getVersionType();

        if (versionType == null) {
            return "unknown";
        }

        switch (versionType.toLowerCase()) {
            case "validation_failed":
                return "failed";
            case "validation_success":
            case "final":
                return "success";
            default:
                return "in_progress";
        }
    }

    /**
     * 构建时间线条目
     */
    private VersionTimelineItem buildTimelineItem(GenerationVersionEntity version) {
        Map<String, Object> snapshot = version.getSnapshot();
        String summary = generateSummary(version);
        String status = determineStatus(version);

        @SuppressWarnings("unchecked")
        List<String> tags = snapshot != null
                ? (List<String>) snapshot.getOrDefault("tags", Collections.emptyList())
                : Collections.emptyList();

        return VersionTimelineItem.builder()
                .versionId(version.getId())
                .versionNumber(version.getVersionNumber())
                .versionType(version.getVersionType())
                .versionTypeDisplay(snapshot != null
                        ? String.valueOf(snapshot.getOrDefault("version_type_display", ""))
                        : "")
                .timestamp(version.getCreatedAt())
                .summary(summary)
                .status(status)
                .canRollback(!version.getVersionType().equalsIgnoreCase("rollback"))
                .parentVersionId(version.getParentVersionId())
                .build();
    }

    /**
     * 生成版本摘要
     */
    private String generateSummary(GenerationVersionEntity version) {
        Map<String, Object> snapshot = version.getSnapshot();
        String versionType = version.getVersionType();

        if (versionType == null || snapshot == null) {
            return "版本快照";
        }

        switch (versionType.toLowerCase()) {
            case "plan":
                return String.format("分析需求，提取 %s 个实体",
                        snapshot.getOrDefault("entity_count", 0));
            case "schema":
                return String.format("生成 %s 个表的DDL",
                        snapshot.getOrDefault("table_count", 0));
            case "code":
                return String.format("生成 %s 个代码文件",
                        snapshot.getOrDefault("file_count", 0));
            case "validation_failed":
                return String.format("测试失败: %s",
                        snapshot.getOrDefault("failure_reason", "未知错误"));
            case "validation_success":
                return String.format("所有测试通过，覆盖率 %s%%",
                        snapshot.getOrDefault("coverage", "0"));
            case "rollback":
                return String.format("回滚到版本 %s",
                        snapshot.getOrDefault("rollback_from_version_number", ""));
            default:
                return "版本快照";
        }
    }
}
