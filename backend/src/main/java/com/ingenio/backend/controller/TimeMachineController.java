package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.VersionDiff;
import com.ingenio.backend.dto.VersionTimelineItem;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.GenerationVersionEntity;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.service.TimeMachineAdvancedService;
import com.ingenio.backend.service.TimeMachineAdvancedService.*;
import com.ingenio.backend.service.VersionSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 时光机API控制器
 *
 * <p>
 * 基础功能：
 * </p>
 * <ol>
 * <li>获取版本历史时间线</li>
 * <li>对比版本差异</li>
 * <li>回滚到指定版本</li>
 * <li>查看版本详情</li>
 * </ol>
 *
 * <p>
 * V2.0高级功能：
 * </p>
 * <ol>
 * <li>版本标签管理（添加/移除/按标签查询）</li>
 * <li>分支创建（从任意版本创建新分支）</li>
 * <li>代码级别对比（文件级别差异）</li>
 * <li>版本搜索（按类型/日期/状态筛选）</li>
 * <li>批量操作（批量删除/批量标记）</li>
 * <li>版本导出导入</li>
 * <li>版本统计</li>
 * </ol>
 *
 * 所有接口需要登录
 *
 * @author Justin
 * @since 2025-11-17 V2.0高级功能升级
 */
@RestController
@RequestMapping("/v1/timemachine")
@RequiredArgsConstructor
public class TimeMachineController {

    private final VersionSnapshotService snapshotService;
    private final TimeMachineAdvancedService advancedService;
    private final GenerationTaskMapper generationTaskMapper;

    /**
     * 获取版本历史时间线
     *
     * @param taskId 任务ID
     * @return 时间线条目列表（按时间倒序）
     */
    @GetMapping("/timeline/{taskId}")
    @SaCheckLogin
    public Result<List<VersionTimelineItem>> getTimeline(@PathVariable String taskId) {
        UUID uuid = UUID.fromString(taskId);
        List<VersionTimelineItem> timeline = snapshotService.getTimeline(uuid);
        return Result.success(timeline);
    }

    /**
     * 对比版本差异
     *
     * @param version1 版本1 ID
     * @param version2 版本2 ID
     * @return 差异对比结果
     */
    @GetMapping("/diff")
    @SaCheckLogin
    public Result<VersionDiff> compareVersions(
            @RequestParam String version1,
            @RequestParam String version2) {
        UUID v1Id = UUID.fromString(version1);
        UUID v2Id = UUID.fromString(version2);

        VersionDiff diff = snapshotService.compareVersions(v1Id, v2Id);
        return Result.success(diff);
    }

    /**
     * 回滚到指定版本
     *
     * 创建新任务，复制目标版本的快照数据
     *
     * @param versionId 目标版本ID
     * @return 新创建的ROLLBACK版本
     */
    @PostMapping("/rollback/{versionId}")
    @SaCheckLogin
    public Result<GenerationVersionEntity> rollback(@PathVariable String versionId) {
        UUID uuid = UUID.fromString(versionId);
        GenerationVersionEntity rollbackVersion = snapshotService.rollbackToVersion(uuid);
        return Result.success(rollbackVersion);
    }

    /**
     * 获取版本详情
     *
     * @param versionId 版本ID
     * @return 版本实体（包含完整快照数据）
     */
    @GetMapping("/version/{versionId}")
    @SaCheckLogin
    public Result<GenerationVersionEntity> getVersion(@PathVariable String versionId) {
        UUID uuid = UUID.fromString(versionId);
        GenerationVersionEntity version = snapshotService.getVersion(uuid);
        return Result.success(version);
    }

    /**
     * 删除版本（慎用）
     *
     * @param versionId 版本ID
     * @return 成功消息
     */
    @DeleteMapping("/version/{versionId}")
    @SaCheckLogin
    public Result<String> deleteVersion(@PathVariable String versionId) {
        UUID uuid = UUID.fromString(versionId);
        snapshotService.deleteVersion(uuid);
        return Result.success("版本已删除");
    }

    // ==================== V2.0 高级功能 API ====================

    /**
     * 为版本添加标签
     *
     * @param versionId 版本ID
     * @param tag       标签名称
     * @return 更新后的版本
     */
    @PostMapping("/version/{versionId}/tag")
    @SaCheckLogin
    public Result<GenerationVersionEntity> addTag(
            @PathVariable String versionId,
            @RequestParam String tag) {
        UUID uuid = UUID.fromString(versionId);
        GenerationVersionEntity version = advancedService.addTag(uuid, tag);
        return Result.success(version);
    }

    /**
     * 移除版本标签
     *
     * @param versionId 版本ID
     * @param tag       要移除的标签
     * @return 更新后的版本
     */
    @DeleteMapping("/version/{versionId}/tag")
    @SaCheckLogin
    public Result<GenerationVersionEntity> removeTag(
            @PathVariable String versionId,
            @RequestParam String tag) {
        UUID uuid = UUID.fromString(versionId);
        GenerationVersionEntity version = advancedService.removeTag(uuid, tag);
        return Result.success(version);
    }

    /**
     * 按标签获取版本列表
     *
     * @param taskId 任务ID
     * @param tag    标签名称
     * @return 带该标签的版本列表
     */
    @GetMapping("/task/{taskId}/versions/by-tag")
    @SaCheckLogin
    public Result<List<GenerationVersionEntity>> getVersionsByTag(
            @PathVariable String taskId,
            @RequestParam String tag) {
        UUID uuid = UUID.fromString(taskId);
        List<GenerationVersionEntity> versions = advancedService.getVersionsByTag(uuid, tag);
        return Result.success(versions);
    }

    /**
     * 从版本创建分支
     *
     * <p>
     * 从指定版本创建新的任务分支，用于尝试不同的实现方案
     * </p>
     *
     * @param versionId  源版本ID
     * @param branchName 分支名称
     * @return 分支创建结果
     */
    @PostMapping("/version/{versionId}/branch")
    @SaCheckLogin
    public Result<BranchResult> createBranch(
            @PathVariable String versionId,
            @RequestParam String branchName) {
        UUID uuid = UUID.fromString(versionId);
        BranchResult result = advancedService.createBranch(uuid, branchName);
        return Result.success(result);
    }

    /**
     * 获取代码级别差异
     *
     * <p>
     * 详细的文件级别差异对比，包含新增/删除/修改的文件及行数统计
     * </p>
     *
     * @param version1 版本1 ID
     * @param version2 版本2 ID
     * @return 代码级别差异
     */
    @GetMapping("/code-diff")
    @SaCheckLogin
    public Result<CodeLevelDiff> getCodeLevelDiff(
            @RequestParam String version1,
            @RequestParam String version2) {
        UUID v1Id = UUID.fromString(version1);
        UUID v2Id = UUID.fromString(version2);
        CodeLevelDiff diff = advancedService.getCodeLevelDiff(v1Id, v2Id);
        return Result.success(diff);
    }

    /**
     * 搜索版本
     *
     * <p>
     * 按条件筛选版本：类型、状态、标签、时间范围、版本号范围
     * </p>
     *
     * @param taskId       任务ID
     * @param versionTypes 版本类型列表（可选）
     * @param statuses     状态列表（可选）
     * @param tags         标签列表（可选）
     * @param startTime    开始时间（可选）
     * @param endTime      结束时间（可选）
     * @param minVersion   最小版本号（可选）
     * @param maxVersion   最大版本号（可选）
     * @return 符合条件的版本列表
     */
    @GetMapping("/task/{taskId}/search")
    @SaCheckLogin
    public Result<List<VersionTimelineItem>> searchVersions(
            @PathVariable String taskId,
            @RequestParam(required = false) List<String> versionTypes,
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime,
            @RequestParam(required = false) Integer minVersion,
            @RequestParam(required = false) Integer maxVersion) {
        UUID uuid = UUID.fromString(taskId);

        VersionSearchParams params = VersionSearchParams.builder()
                .versionTypes(versionTypes)
                .statuses(statuses)
                .tags(tags)
                .startTime(startTime)
                .endTime(endTime)
                .minVersion(minVersion)
                .maxVersion(maxVersion)
                .build();

        List<VersionTimelineItem> results = advancedService.searchVersions(uuid, params);
        return Result.success(results);
    }

    /**
     * 批量删除版本
     *
     * <p>
     * 删除指定版本号之后的所有版本（用于重置到某个状态）
     * </p>
     *
     * @param taskId             任务ID
     * @param afterVersionNumber 删除此版本号之后的所有版本
     * @return 删除的版本数量
     */
    @DeleteMapping("/task/{taskId}/versions/after/{afterVersionNumber}")
    @SaCheckLogin
    public Result<Integer> deleteVersionsAfter(
            @PathVariable String taskId,
            @PathVariable int afterVersionNumber) {
        UUID uuid = UUID.fromString(taskId);
        int deletedCount = advancedService.deleteVersionsAfter(uuid, afterVersionNumber);
        return Result.success(deletedCount);
    }

    /**
     * 批量添加标签
     *
     * @param versionIds 版本ID列表
     * @param tag        标签名称
     * @return 成功标记的数量
     */
    @PostMapping("/versions/batch-tag")
    @SaCheckLogin
    public Result<Integer> batchAddTag(
            @RequestBody List<String> versionIds,
            @RequestParam String tag) {
        List<UUID> uuids = versionIds.stream()
                .map(UUID::fromString)
                .toList();
        int successCount = advancedService.batchAddTag(uuids, tag);
        return Result.success(successCount);
    }

    /**
     * 导出版本
     *
     * <p>
     * 导出版本快照数据，用于备份或分享
     * </p>
     *
     * @param versionId 版本ID
     * @return 导出数据
     */
    @GetMapping("/version/{versionId}/export")
    @SaCheckLogin
    public Result<VersionExportData> exportVersion(@PathVariable String versionId) {
        UUID uuid = UUID.fromString(versionId);
        VersionExportData exportData = advancedService.exportVersion(uuid);
        return Result.success(exportData);
    }

    /**
     * 导入版本
     *
     * <p>
     * 从导出数据创建新版本
     * </p>
     *
     * @param taskId     目标任务ID
     * @param exportData 导出数据
     * @return 新创建的版本
     */
    @PostMapping("/task/{taskId}/import")
    @SaCheckLogin
    public Result<GenerationVersionEntity> importVersion(
            @PathVariable String taskId,
            @RequestBody VersionExportData exportData) {
        UUID uuid = UUID.fromString(taskId);
        GenerationVersionEntity version = advancedService.importVersion(uuid, exportData);
        return Result.success(version);
    }

    /**
     * 获取版本统计
     *
     * <p>
     * 统计信息包含：总版本数、按类型/状态分布、回滚次数、标签版本数等
     * </p>
     *
     * @param taskId 任务ID
     * @return 版本统计信息
     */
    @GetMapping("/task/{taskId}/statistics")
    @SaCheckLogin
    public Result<VersionStatistics> getStatistics(@PathVariable String taskId) {
        UUID uuid = UUID.fromString(taskId);
        VersionStatistics stats = advancedService.getStatistics(uuid);
        return Result.success(stats);
    }

    /**
     * 下载版本代码 (ZIP)
     *
     * @param versionId 版本ID
     * @param response  HttpServletResponse
     */
    @GetMapping("/version/{versionId}/download")
    @SaCheckLogin
    public void downloadVersion(
            @PathVariable String versionId,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        UUID uuid = UUID.fromString(versionId);
        byte[] zipBytes = advancedService.exportAsZip(uuid);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"ingenio-app-" + versionId + ".zip\"");
        response.getOutputStream().write(zipBytes);
        response.getOutputStream().flush();
    }

    /**
     * 下载该任务最新版本的代码 (ZIP)
     *
     * @param taskId   任务ID
     * @param response HttpServletResponse
     */
    @GetMapping("/task/{taskId}/download-latest")
    @SaCheckLogin
    public void downloadLatestVersion(
            @PathVariable String taskId,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        UUID taskUuid = UUID.fromString(taskId);

        // Find latest version
        List<VersionTimelineItem> versions = advancedService.searchVersions(taskUuid,
                TimeMachineAdvancedService.VersionSearchParams.builder().build());

        if (versions.isEmpty()) {
            response.sendError(404, "No versions found for task");
            return;
        }

        // Assuming sorted by created_at desc
        UUID latestVersionId = versions.get(0).getVersionId();
        byte[] zipBytes = advancedService.exportAsZip(latestVersionId);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"ingenio-app-" + latestVersionId + ".zip\"");
        response.getOutputStream().write(zipBytes);
        response.getOutputStream().flush();
    }

    /**
     * 按AppSpec下载最新版本代码 (ZIP)
     *
     * @param appSpecId AppSpec ID
     * @param response HttpServletResponse
     */
    @GetMapping("/appspec/{appSpecId}/download-latest")
    @SaCheckLogin
    public void downloadLatestByAppSpec(
            @PathVariable String appSpecId,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        UUID appSpecUuid = UUID.fromString(appSpecId);

        GenerationTaskEntity task = generationTaskMapper.selectOne(
                new QueryWrapper<GenerationTaskEntity>()
                        .eq("app_spec_id", appSpecUuid)
                        .orderByDesc("created_at")
                        .last("LIMIT 1"));

        if (task == null) {
            response.sendError(404, "No generation task found for AppSpec");
            return;
        }

        List<VersionTimelineItem> versions = advancedService.searchVersions(task.getId(),
                TimeMachineAdvancedService.VersionSearchParams.builder().build());

        if (versions.isEmpty()) {
            response.sendError(404, "No versions found for task");
            return;
        }

        UUID latestVersionId = versions.get(0).getVersionId();
        byte[] zipBytes = advancedService.exportAsZip(latestVersionId);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"ingenio-app-" + latestVersionId + ".zip\"");
        response.getOutputStream().write(zipBytes);
        response.getOutputStream().flush();
    }
}
