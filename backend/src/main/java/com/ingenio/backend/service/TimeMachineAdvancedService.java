package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.GenerationVersionEntity;
import com.ingenio.backend.dto.VersionTimelineItem;
import com.ingenio.backend.dto.VersionType;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.mapper.GenerationVersionMapper;
import com.ingenio.backend.service.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 时光机高级功能服务
 */
@Service
public class TimeMachineAdvancedService {

    private static final Logger log = LoggerFactory.getLogger(TimeMachineAdvancedService.class);

    private final GenerationVersionMapper versionMapper;
    private final GenerationTaskMapper taskMapper;
    private final VersionSnapshotService snapshotService;
    private final MinioService minioService;

    public TimeMachineAdvancedService(GenerationVersionMapper versionMapper, GenerationTaskMapper taskMapper,
            VersionSnapshotService snapshotService, MinioService minioService) {
        this.versionMapper = versionMapper;
        this.taskMapper = taskMapper;
        this.snapshotService = snapshotService;
        this.minioService = minioService;
    }

    @Transactional
    public GenerationVersionEntity addTag(UUID versionId, String tag) {
        log.info("[TimeMachine] 添加版本标签: versionId={}, tag={}", versionId, tag);

        GenerationVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("版本不存在: " + versionId);
        }

        Map<String, Object> snapshot = version.getSnapshot();
        if (snapshot == null) {
            snapshot = new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) snapshot.getOrDefault("tags", new ArrayList<>());
        if (tags == null) {
            tags = new ArrayList<>();
        }

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

    public List<GenerationVersionEntity> getVersionsByTag(UUID taskId, String tag) {
        log.info("[TimeMachine] 按标签查询版本: taskId={}, tag={}", taskId, tag);

        List<GenerationVersionEntity> allVersions = versionMapper.selectList(
                new QueryWrapper<GenerationVersionEntity>()
                        .eq("task_id", taskId)
                        .orderByDesc("created_at"));

        return allVersions.stream()
                .filter(v -> {
                    Map<String, Object> snapshot = v.getSnapshot();
                    if (snapshot == null)
                        return false;
                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) snapshot.get("tags");
                    return tags != null && tags.contains(tag);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public BranchResult createBranch(UUID versionId, String branchName) {
        log.info("[TimeMachine] 创建版本分支: versionId={}, branchName={}", versionId, branchName);

        GenerationVersionEntity sourceVersion = versionMapper.selectById(versionId);
        if (sourceVersion == null) {
            throw new RuntimeException("源版本不存在: " + versionId);
        }

        GenerationTaskEntity sourceTask = taskMapper.selectById(sourceVersion.getTaskId());
        if (sourceTask == null) {
            throw new RuntimeException("源任务不存在");
        }

        GenerationTaskEntity branchTask = new GenerationTaskEntity();
        branchTask.setId(UUID.randomUUID());
        branchTask.setTenantId(sourceTask.getTenantId());
        branchTask.setUserId(sourceTask.getUserId());
        String branchTaskName = sourceTask.getTaskName() != null
                ? sourceTask.getTaskName() + " [分支: " + branchName + "]"
                : "分支任务: " + branchName;
        branchTask.setTaskName(branchTaskName);
        branchTask.setUserRequirement(sourceTask.getUserRequirement() != null
                ? sourceTask.getUserRequirement()
                : "从版本 " + versionId + " 创建的分支");
        branchTask.setStatus("pending");
        branchTask.setCreatedAt(Instant.now());
        branchTask.setUpdatedAt(Instant.now());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("branch_name", branchName);
        metadata.put("source_task_id", sourceTask.getId().toString());
        metadata.put("source_version_id", versionId.toString());
        metadata.put("source_version_number", sourceVersion.getVersionNumber());
        metadata.put("branched_at", Instant.now().toString());
        branchTask.setMetadata(metadata);

        taskMapper.insert(branchTask);

        Map<String, Object> branchSnapshot = new HashMap<>(sourceVersion.getSnapshot());
        branchSnapshot.put("branch_source", Map.of(
                "task_id", sourceTask.getId().toString(),
                "version_id", versionId.toString(),
                "version_number", sourceVersion.getVersionNumber()));
        branchSnapshot.put("branch_name", branchName);

        GenerationVersionEntity branchVersion = snapshotService.createSnapshot(
                branchTask.getId(),
                branchTask.getTenantId(),
                VersionType.PLAN,
                branchSnapshot);

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

    public static class BranchResult {
        private UUID branchTaskId;
        private UUID branchVersionId;
        private String branchName;
        private UUID sourceVersionId;
        private Integer sourceVersionNumber;
        private Instant createdAt;

        public BranchResult() {
        }

        public BranchResult(UUID branchTaskId, UUID branchVersionId, String branchName, UUID sourceVersionId,
                Integer sourceVersionNumber, Instant createdAt) {
            this.branchTaskId = branchTaskId;
            this.branchVersionId = branchVersionId;
            this.branchName = branchName;
            this.sourceVersionId = sourceVersionId;
            this.sourceVersionNumber = sourceVersionNumber;
            this.createdAt = createdAt;
        }

        public static BranchResultBuilder builder() {
            return new BranchResultBuilder();
        }

        public static class BranchResultBuilder {
            private UUID branchTaskId;
            private UUID branchVersionId;
            private String branchName;
            private UUID sourceVersionId;
            private Integer sourceVersionNumber;
            private Instant createdAt;

            public BranchResultBuilder branchTaskId(UUID branchTaskId) {
                this.branchTaskId = branchTaskId;
                return this;
            }

            public BranchResultBuilder branchVersionId(UUID branchVersionId) {
                this.branchVersionId = branchVersionId;
                return this;
            }

            public BranchResultBuilder branchName(String branchName) {
                this.branchName = branchName;
                return this;
            }

            public BranchResultBuilder sourceVersionId(UUID sourceVersionId) {
                this.sourceVersionId = sourceVersionId;
                return this;
            }

            public BranchResultBuilder sourceVersionNumber(Integer sourceVersionNumber) {
                this.sourceVersionNumber = sourceVersionNumber;
                return this;
            }

            public BranchResultBuilder createdAt(Instant createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public BranchResult build() {
                return new BranchResult(branchTaskId, branchVersionId, branchName, sourceVersionId, sourceVersionNumber,
                        createdAt);
            }
        }

        public UUID getBranchTaskId() {
            return branchTaskId;
        }

        public UUID getBranchVersionId() {
            return branchVersionId;
        }

        public String getBranchName() {
            return branchName;
        }

        public UUID getSourceVersionId() {
            return sourceVersionId;
        }

        public Integer getSourceVersionNumber() {
            return sourceVersionNumber;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

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

        @SuppressWarnings("unchecked")
        Map<String, String> files1 = extractCodeFiles(snapshot1);
        @SuppressWarnings("unchecked")
        Map<String, String> files2 = extractCodeFiles(snapshot2);

        Set<String> allFilePaths = new HashSet<>();
        allFilePaths.addAll(files1.keySet());
        allFilePaths.addAll(files2.keySet());

        for (String filePath : allFilePaths) {
            String content1 = files1.get(filePath);
            String content2 = files2.get(filePath);

            if (content1 == null && content2 != null) {
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

        public CodeLevelDiff() {
        }

        public CodeLevelDiff(UUID version1Id, UUID version2Id, Integer version1Number, Integer version2Number,
                List<FileDiff> fileDiffs, Integer addedFiles, Integer removedFiles, Integer modifiedFiles,
                Integer totalLinesAdded, Integer totalLinesRemoved) {
            this.version1Id = version1Id;
            this.version2Id = version2Id;
            this.version1Number = version1Number;
            this.version2Number = version2Number;
            this.fileDiffs = fileDiffs;
            this.addedFiles = addedFiles;
            this.removedFiles = removedFiles;
            this.modifiedFiles = modifiedFiles;
            this.totalLinesAdded = totalLinesAdded;
            this.totalLinesRemoved = totalLinesRemoved;
        }

        public static CodeLevelDiffBuilder builder() {
            return new CodeLevelDiffBuilder();
        }

        public static class CodeLevelDiffBuilder {
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

            public CodeLevelDiffBuilder version1Id(UUID version1Id) {
                this.version1Id = version1Id;
                return this;
            }

            public CodeLevelDiffBuilder version2Id(UUID version2Id) {
                this.version2Id = version2Id;
                return this;
            }

            public CodeLevelDiffBuilder version1Number(Integer version1Number) {
                this.version1Number = version1Number;
                return this;
            }

            public CodeLevelDiffBuilder version2Number(Integer version2Number) {
                this.version2Number = version2Number;
                return this;
            }

            public CodeLevelDiffBuilder fileDiffs(List<FileDiff> fileDiffs) {
                this.fileDiffs = fileDiffs;
                return this;
            }

            public CodeLevelDiffBuilder addedFiles(Integer addedFiles) {
                this.addedFiles = addedFiles;
                return this;
            }

            public CodeLevelDiffBuilder removedFiles(Integer removedFiles) {
                this.removedFiles = removedFiles;
                return this;
            }

            public CodeLevelDiffBuilder modifiedFiles(Integer modifiedFiles) {
                this.modifiedFiles = modifiedFiles;
                return this;
            }

            public CodeLevelDiffBuilder totalLinesAdded(Integer totalLinesAdded) {
                this.totalLinesAdded = totalLinesAdded;
                return this;
            }

            public CodeLevelDiffBuilder totalLinesRemoved(Integer totalLinesRemoved) {
                this.totalLinesRemoved = totalLinesRemoved;
                return this;
            }

            public CodeLevelDiff build() {
                return new CodeLevelDiff(version1Id, version2Id, version1Number, version2Number, fileDiffs, addedFiles,
                        removedFiles, modifiedFiles, totalLinesAdded, totalLinesRemoved);
            }
        }

        public UUID getVersion1Id() {
            return version1Id;
        }

        public UUID getVersion2Id() {
            return version2Id;
        }

        public Integer getVersion1Number() {
            return version1Number;
        }

        public Integer getVersion2Number() {
            return version2Number;
        }

        public List<FileDiff> getFileDiffs() {
            return fileDiffs;
        }

        public Integer getAddedFiles() {
            return addedFiles;
        }

        public Integer getRemovedFiles() {
            return removedFiles;
        }

        public Integer getModifiedFiles() {
            return modifiedFiles;
        }

        public Integer getTotalLinesAdded() {
            return totalLinesAdded;
        }

        public Integer getTotalLinesRemoved() {
            return totalLinesRemoved;
        }
    }

    public static class FileDiff {
        private String filePath;
        private String changeType;
        private Integer linesAdded;
        private Integer linesRemoved;
        private String oldContent;
        private String newContent;
        private List<String> diffHunks;

        public FileDiff() {
        }

        public FileDiff(String filePath, String changeType, Integer linesAdded, Integer linesRemoved, String oldContent,
                String newContent, List<String> diffHunks) {
            this.filePath = filePath;
            this.changeType = changeType;
            this.linesAdded = linesAdded;
            this.linesRemoved = linesRemoved;
            this.oldContent = oldContent;
            this.newContent = newContent;
            this.diffHunks = diffHunks;
        }

        public static FileDiffBuilder builder() {
            return new FileDiffBuilder();
        }

        public static class FileDiffBuilder {
            private String filePath;
            private String changeType;
            private Integer linesAdded;
            private Integer linesRemoved;
            private String oldContent;
            private String newContent;
            private List<String> diffHunks;

            public FileDiffBuilder filePath(String filePath) {
                this.filePath = filePath;
                return this;
            }

            public FileDiffBuilder changeType(String changeType) {
                this.changeType = changeType;
                return this;
            }

            public FileDiffBuilder linesAdded(Integer linesAdded) {
                this.linesAdded = linesAdded;
                return this;
            }

            public FileDiffBuilder linesRemoved(Integer linesRemoved) {
                this.linesRemoved = linesRemoved;
                return this;
            }

            public FileDiffBuilder oldContent(String oldContent) {
                this.oldContent = oldContent;
                return this;
            }

            public FileDiffBuilder newContent(String newContent) {
                this.newContent = newContent;
                return this;
            }

            public FileDiffBuilder diffHunks(List<String> diffHunks) {
                this.diffHunks = diffHunks;
                return this;
            }

            public FileDiff build() {
                return new FileDiff(filePath, changeType, linesAdded, linesRemoved, oldContent, newContent, diffHunks);
            }
        }

        public String getFilePath() {
            return filePath;
        }

        public String getChangeType() {
            return changeType;
        }

        public Integer getLinesAdded() {
            return linesAdded;
        }

        public Integer getLinesRemoved() {
            return linesRemoved;
        }

        public String getOldContent() {
            return oldContent;
        }

        public String getNewContent() {
            return newContent;
        }

        public List<String> getDiffHunks() {
            return diffHunks;
        }
    }

    public List<VersionTimelineItem> searchVersions(UUID taskId, VersionSearchParams searchParams) {
        log.info("[TimeMachine] 搜索版本: taskId={}, params={}", taskId, searchParams);

        QueryWrapper<GenerationVersionEntity> query = new QueryWrapper<>();
        query.eq("task_id", taskId);

        if (searchParams.getVersionTypes() != null && !searchParams.getVersionTypes().isEmpty()) {
            query.in("version_type", searchParams.getVersionTypes());
        }
        if (searchParams.getStartTime() != null) {
            query.ge("created_at", searchParams.getStartTime());
        }
        if (searchParams.getEndTime() != null) {
            query.le("created_at", searchParams.getEndTime());
        }
        if (searchParams.getMinVersion() != null) {
            query.ge("version_number", searchParams.getMinVersion());
        }
        if (searchParams.getMaxVersion() != null) {
            query.le("version_number", searchParams.getMaxVersion());
        }

        query.orderByDesc("created_at");

        List<GenerationVersionEntity> versions = versionMapper.selectList(query);

        if (searchParams.getStatuses() != null && !searchParams.getStatuses().isEmpty()) {
            versions = versions.stream()
                    .filter(v -> {
                        String status = determineStatus(v);
                        return searchParams.getStatuses().contains(status);
                    })
                    .collect(Collectors.toList());
        }

        if (searchParams.getTags() != null && !searchParams.getTags().isEmpty()) {
            versions = versions.stream()
                    .filter(v -> {
                        Map<String, Object> snapshot = v.getSnapshot();
                        if (snapshot == null)
                            return false;
                        @SuppressWarnings("unchecked")
                        List<String> tags = (List<String>) snapshot.get("tags");
                        return tags != null && tags.stream().anyMatch(t -> searchParams.getTags().contains(t));
                    })
                    .collect(Collectors.toList());
        }

        return versions.stream()
                .map(this::buildTimelineItem)
                .collect(Collectors.toList());
    }

    public static class VersionSearchParams {
        private List<String> versionTypes;
        private List<String> statuses;
        private List<String> tags;
        private Instant startTime;
        private Instant endTime;
        private Integer minVersion;
        private Integer maxVersion;

        public VersionSearchParams() {
        }

        public VersionSearchParams(List<String> versionTypes, List<String> statuses, List<String> tags,
                Instant startTime, Instant endTime, Integer minVersion, Integer maxVersion) {
            this.versionTypes = versionTypes;
            this.statuses = statuses;
            this.tags = tags;
            this.startTime = startTime;
            this.endTime = endTime;
            this.minVersion = minVersion;
            this.maxVersion = maxVersion;
        }

        public static VersionSearchParamsBuilder builder() {
            return new VersionSearchParamsBuilder();
        }

        public static class VersionSearchParamsBuilder {
            private List<String> versionTypes;
            private List<String> statuses;
            private List<String> tags;
            private Instant startTime;
            private Instant endTime;
            private Integer minVersion;
            private Integer maxVersion;

            public VersionSearchParamsBuilder versionTypes(List<String> versionTypes) {
                this.versionTypes = versionTypes;
                return this;
            }

            public VersionSearchParamsBuilder statuses(List<String> statuses) {
                this.statuses = statuses;
                return this;
            }

            public VersionSearchParamsBuilder tags(List<String> tags) {
                this.tags = tags;
                return this;
            }

            public VersionSearchParamsBuilder startTime(Instant startTime) {
                this.startTime = startTime;
                return this;
            }

            public VersionSearchParamsBuilder endTime(Instant endTime) {
                this.endTime = endTime;
                return this;
            }

            public VersionSearchParamsBuilder minVersion(Integer minVersion) {
                this.minVersion = minVersion;
                return this;
            }

            public VersionSearchParamsBuilder maxVersion(Integer maxVersion) {
                this.maxVersion = maxVersion;
                return this;
            }

            public VersionSearchParams build() {
                return new VersionSearchParams(versionTypes, statuses, tags, startTime, endTime, minVersion,
                        maxVersion);
            }
        }

        public List<String> getVersionTypes() {
            return versionTypes;
        }

        public List<String> getStatuses() {
            return statuses;
        }

        public List<String> getTags() {
            return tags;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public Integer getMinVersion() {
            return minVersion;
        }

        public Integer getMaxVersion() {
            return maxVersion;
        }
    }

    @Transactional
    public int deleteVersionsAfter(UUID taskId, int afterVersionNumber) {
        log.warn("[TimeMachine] 批量删除版本: taskId={}, afterVersion={}", taskId, afterVersionNumber);

        List<GenerationVersionEntity> toDelete = versionMapper.selectList(
                new QueryWrapper<GenerationVersionEntity>()
                        .eq("task_id", taskId)
                        .gt("version_number", afterVersionNumber));

        for (GenerationVersionEntity version : toDelete) {
            versionMapper.deleteById(version.getId());
        }

        log.info("[TimeMachine] 批量删除完成: 删除了 {} 个版本", toDelete.size());
        return toDelete.size();
    }

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

        public VersionExportData() {
        }

        public VersionExportData(Instant exportedAt, String exportVersion, UUID versionId, Integer versionNumber,
                String versionType, UUID taskId, UUID tenantId, Map<String, Object> snapshot,
                Map<String, Object> taskMetadata, Instant createdAt) {
            this.exportedAt = exportedAt;
            this.exportVersion = exportVersion;
            this.versionId = versionId;
            this.versionNumber = versionNumber;
            this.versionType = versionType;
            this.taskId = taskId;
            this.tenantId = tenantId;
            this.snapshot = snapshot;
            this.taskMetadata = taskMetadata;
            this.createdAt = createdAt;
        }

        public static VersionExportDataBuilder builder() {
            return new VersionExportDataBuilder();
        }

        public static class VersionExportDataBuilder {
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

            public VersionExportDataBuilder exportedAt(Instant exportedAt) {
                this.exportedAt = exportedAt;
                return this;
            }

            public VersionExportDataBuilder exportVersion(String exportVersion) {
                this.exportVersion = exportVersion;
                return this;
            }

            public VersionExportDataBuilder versionId(UUID versionId) {
                this.versionId = versionId;
                return this;
            }

            public VersionExportDataBuilder versionNumber(Integer versionNumber) {
                this.versionNumber = versionNumber;
                return this;
            }

            public VersionExportDataBuilder versionType(String versionType) {
                this.versionType = versionType;
                return this;
            }

            public VersionExportDataBuilder taskId(UUID taskId) {
                this.taskId = taskId;
                return this;
            }

            public VersionExportDataBuilder tenantId(UUID tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public VersionExportDataBuilder snapshot(Map<String, Object> snapshot) {
                this.snapshot = snapshot;
                return this;
            }

            public VersionExportDataBuilder taskMetadata(Map<String, Object> taskMetadata) {
                this.taskMetadata = taskMetadata;
                return this;
            }

            public VersionExportDataBuilder createdAt(Instant createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public VersionExportData build() {
                return new VersionExportData(exportedAt, exportVersion, versionId, versionNumber, versionType, taskId,
                        tenantId, snapshot, taskMetadata, createdAt);
            }
        }

        public Instant getExportedAt() {
            return exportedAt;
        }

        public String getExportVersion() {
            return exportVersion;
        }

        public UUID getVersionId() {
            return versionId;
        }

        public Integer getVersionNumber() {
            return versionNumber;
        }

        public String getVersionType() {
            return versionType;
        }

        public UUID getTaskId() {
            return taskId;
        }

        public UUID getTenantId() {
            return tenantId;
        }

        public Map<String, Object> getSnapshot() {
            return snapshot;
        }

        public Map<String, Object> getTaskMetadata() {
            return taskMetadata;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    @Transactional
    public GenerationVersionEntity importVersion(UUID targetTaskId, VersionExportData exportData) {
        log.info("[TimeMachine] 导入版本: targetTaskId={}, sourceVersionId={}",
                targetTaskId, exportData.getVersionId());

        GenerationTaskEntity targetTask = taskMapper.selectById(targetTaskId);
        if (targetTask == null) {
            throw new RuntimeException("目标任务不存在: " + targetTaskId);
        }

        Map<String, Object> importedSnapshot = new HashMap<>(exportData.getSnapshot());
        importedSnapshot.put("imported_from", Map.of(
                "source_version_id", exportData.getVersionId().toString(),
                "source_task_id", exportData.getTaskId().toString(),
                "imported_at", Instant.now().toString(),
                "original_created_at", exportData.getCreatedAt().toString()));

        return snapshotService.createSnapshot(
                targetTaskId,
                targetTask.getTenantId(),
                VersionType.valueOf(exportData.getVersionType().toUpperCase()),
                importedSnapshot);
    }

    public VersionStatistics getStatistics(UUID taskId) {
        log.info("[TimeMachine] 获取版本统计: taskId={}", taskId);

        List<GenerationVersionEntity> versions = versionMapper.selectList(
                new QueryWrapper<GenerationVersionEntity>()
                        .eq("task_id", taskId)
                        .orderByAsc("created_at"));

        if (versions.isEmpty()) {
            return VersionStatistics.builder()
                    .totalVersions(0)
                    .build();
        }

        Map<String, Long> byType = versions.stream()
                .collect(Collectors.groupingBy(
                        GenerationVersionEntity::getVersionType,
                        Collectors.counting()));

        Map<String, Long> byStatus = versions.stream()
                .collect(Collectors.groupingBy(
                        this::determineStatus,
                        Collectors.counting()));

        Instant firstVersion = versions.get(0).getCreatedAt();
        Instant lastVersion = versions.get(versions.size() - 1).getCreatedAt();

        long rollbackCount = versions.stream()
                .filter(v -> "rollback".equalsIgnoreCase(v.getVersionType()))
                .count();

        long taggedCount = versions.stream()
                .filter(v -> {
                    Map<String, Object> snapshot = v.getSnapshot();
                    if (snapshot == null)
                        return false;
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

    public static class VersionStatistics {
        private Integer totalVersions;
        private Map<String, Long> versionsByType;
        private Map<String, Long> versionsByStatus;
        private Instant firstVersionTime;
        private Instant lastVersionTime;
        private Integer rollbackCount;
        private Integer taggedVersionCount;

        public VersionStatistics() {
        }

        public VersionStatistics(Integer totalVersions, Map<String, Long> versionsByType,
                Map<String, Long> versionsByStatus, Instant firstVersionTime, Instant lastVersionTime,
                Integer rollbackCount, Integer taggedVersionCount) {
            this.totalVersions = totalVersions;
            this.versionsByType = versionsByType;
            this.versionsByStatus = versionsByStatus;
            this.firstVersionTime = firstVersionTime;
            this.lastVersionTime = lastVersionTime;
            this.rollbackCount = rollbackCount;
            this.taggedVersionCount = taggedVersionCount;
        }

        public static VersionStatisticsBuilder builder() {
            return new VersionStatisticsBuilder();
        }

        public static class VersionStatisticsBuilder {
            private Integer totalVersions;
            private Map<String, Long> versionsByType;
            private Map<String, Long> versionsByStatus;
            private Instant firstVersionTime;
            private Instant lastVersionTime;
            private Integer rollbackCount;
            private Integer taggedVersionCount;

            public VersionStatisticsBuilder totalVersions(Integer totalVersions) {
                this.totalVersions = totalVersions;
                return this;
            }

            public VersionStatisticsBuilder versionsByType(Map<String, Long> versionsByType) {
                this.versionsByType = versionsByType;
                return this;
            }

            public VersionStatisticsBuilder versionsByStatus(Map<String, Long> versionsByStatus) {
                this.versionsByStatus = versionsByStatus;
                return this;
            }

            public VersionStatisticsBuilder firstVersionTime(Instant firstVersionTime) {
                this.firstVersionTime = firstVersionTime;
                return this;
            }

            public VersionStatisticsBuilder lastVersionTime(Instant lastVersionTime) {
                this.lastVersionTime = lastVersionTime;
                return this;
            }

            public VersionStatisticsBuilder rollbackCount(Integer rollbackCount) {
                this.rollbackCount = rollbackCount;
                return this;
            }

            public VersionStatisticsBuilder taggedVersionCount(Integer taggedVersionCount) {
                this.taggedVersionCount = taggedVersionCount;
                return this;
            }

            public VersionStatistics build() {
                return new VersionStatistics(totalVersions, versionsByType, versionsByStatus, firstVersionTime,
                        lastVersionTime, rollbackCount, taggedVersionCount);
            }
        }

        public Integer getTotalVersions() {
            return totalVersions;
        }

        public Map<String, Long> getVersionsByType() {
            return versionsByType;
        }

        public Map<String, Long> getVersionsByStatus() {
            return versionsByStatus;
        }

        public Instant getFirstVersionTime() {
            return firstVersionTime;
        }

        public Instant getLastVersionTime() {
            return lastVersionTime;
        }

        public Integer getRollbackCount() {
            return rollbackCount;
        }

        public Integer getTaggedVersionCount() {
            return taggedVersionCount;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractCodeFiles(Map<String, Object> snapshot) {
        Map<String, String> files = new HashMap<>();

        if (snapshot == null) {
            return files;
        }

        extractFilesFromMap(snapshot, "entities", files);
        extractFilesFromMap(snapshot, "services", files);
        extractFilesFromMap(snapshot, "controllers", files);
        extractFilesFromMap(snapshot, "components", files);
        extractFilesFromMap(snapshot, "pages", files);

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

    private int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.split("\n").length;
    }

    private LineDiff calculateLineDiff(String content1, String content2) {
        String[] lines1 = content1.split("\n");
        String[] lines2 = content2.split("\n");

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
                return "pending";
        }
    }

    private VersionTimelineItem buildTimelineItem(GenerationVersionEntity version) {
        return VersionTimelineItem.builder()
                .id(version.getId())
                .versionNumber(version.getVersionNumber())
                .versionType(version.getVersionType())
                .createdAt(version.getCreatedAt())
                .build();
    }

    public byte[] exportAsZip(UUID versionId) {
        log.info("[TimeMachine] Exporting version as ZIP: versionId={}", versionId);

        GenerationVersionEntity version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new RuntimeException("Version not found: " + versionId);
        }

        Map<String, Object> snapshot = version.getSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            throw new RuntimeException("Version snapshot is empty");
        }

        // 优先从MinIO读取归档ZIP（新链路）
        byte[] minioZip = readZipFromArchiveSnapshot(snapshot);
        if (minioZip != null) {
            return minioZip;
        }

        Map<String, String> files = extractCodeFiles(snapshot);
        if (files.isEmpty()) {
            throw new RuntimeException("Version snapshot does not contain code files");
        }

        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {

            for (Map.Entry<String, String> entry : files.entrySet()) {
                String filePath = entry.getKey();
                String content = entry.getValue();

                // Simple heuristic to organize files
                String fullPath = filePath;
                if (filePath.startsWith("entities/") || filePath.startsWith("services/")
                        || filePath.startsWith("controllers/") || filePath.startsWith("mappers/")
                        || filePath.startsWith("dto/") || filePath.endsWith(".java")) {
                    fullPath = "backend/src/main/java/com/ingenio/backend/" + filePath;
                } else if (filePath.startsWith("components/") || filePath.startsWith("pages/")
                        || filePath.endsWith(".tsx") || filePath.endsWith(".ts") || filePath.endsWith(".css")) {
                    fullPath = "frontend/src/" + filePath;
                }

                java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(fullPath);
                zos.putNextEntry(zipEntry);
                zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            zos.finish();
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            log.error("Failed to create ZIP", e);
            throw new RuntimeException("Failed to generate ZIP file", e);
        }
    }

    /**
     * 从快照归档信息中读取ZIP（MinIO）
     *
     * @param snapshot 快照数据
     * @return ZIP字节数组；若未命中归档则返回null
     */
    @SuppressWarnings("unchecked")
    private byte[] readZipFromArchiveSnapshot(Map<String, Object> snapshot) {
        Object archiveObj = snapshot.get("archive");
        if (!(archiveObj instanceof Map)) {
            return null;
        }

        Map<String, Object> archive = (Map<String, Object>) archiveObj;
        String status = Objects.toString(archive.get("status"), "");
        String storageKey = Objects.toString(archive.get("storage_key"), "");

        if (!"success".equalsIgnoreCase(status) || storageKey.isBlank()) {
            return null;
        }

        try (InputStream inputStream = minioService.downloadFile(storageKey);
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("[TimeMachine] MinIO下载ZIP失败: key={}, err={}", storageKey, e.getMessage());
            return null;
        }
    }
}
