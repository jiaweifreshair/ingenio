package com.ingenio.backend.controller;

import java.time.Instant;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.common.response.PageResult;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.CreateProjectRequest;
import com.ingenio.backend.dto.response.ProjectResponse;
import com.ingenio.backend.dto.response.ProjectStatsResponse;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * 项目管理Controller
 * 提供项目的CRUD、社交互动、搜索和派生等功能
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final GenerationTaskMapper generationTaskMapper;

    /**
     * 获取项目统计数据
     * Dashboard页面使用
     *
     * @return 统计数据
     */
    @SaCheckLogin
    @GetMapping("/stats")
    public Result<ProjectStatsResponse> getStats() {
        // 获取当前用户ID和租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : userId;

        log.info("获取项目统计: userId={}, tenantId={}", userId, tenantId);

        // 查询总应用数
        LambdaQueryWrapper<ProjectEntity> totalWrapper = new LambdaQueryWrapper<>();
        totalWrapper.eq(ProjectEntity::getUserId, userId)
                .eq(ProjectEntity::getTenantId, tenantId);
        long totalProjects = projectService.count(totalWrapper);

        // 查询本月新增应用数
        Instant monthStart = Instant.now()
                .atZone(java.time.ZoneId.systemDefault())
                .with(TemporalAdjusters.firstDayOfMonth())
                .withHour(0).withMinute(0).withSecond(0)
                .toInstant();
        LambdaQueryWrapper<ProjectEntity> monthlyWrapper = new LambdaQueryWrapper<>();
        monthlyWrapper.eq(ProjectEntity::getUserId, userId)
                .eq(ProjectEntity::getTenantId, tenantId)
                .ge(ProjectEntity::getCreatedAt, monthStart);
        long monthlyNewProjects = projectService.count(monthlyWrapper);

        // 查询已发布应用数
        LambdaQueryWrapper<ProjectEntity> publishedWrapper = new LambdaQueryWrapper<>();
        publishedWrapper.eq(ProjectEntity::getUserId, userId)
                .eq(ProjectEntity::getTenantId, tenantId)
                .eq(ProjectEntity::getStatus, ProjectEntity.Status.PUBLISHED.getValue());
        long publishedProjects = projectService.count(publishedWrapper);

        // 查询草稿应用数
        LambdaQueryWrapper<ProjectEntity> draftWrapper = new LambdaQueryWrapper<>();
        draftWrapper.eq(ProjectEntity::getUserId, userId)
                .eq(ProjectEntity::getTenantId, tenantId)
                .eq(ProjectEntity::getStatus, ProjectEntity.Status.DRAFT.getValue());
        long draftProjects = projectService.count(draftWrapper);

        // 查询已归档应用数
        LambdaQueryWrapper<ProjectEntity> archivedWrapper = new LambdaQueryWrapper<>();
        archivedWrapper.eq(ProjectEntity::getUserId, userId)
                .eq(ProjectEntity::getTenantId, tenantId)
                .eq(ProjectEntity::getStatus, ProjectEntity.Status.ARCHIVED.getValue());
        long archivedProjects = projectService.count(archivedWrapper);

        // 查询生成中的任务数
        LambdaQueryWrapper<GenerationTaskEntity> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(GenerationTaskEntity::getUserId, userId)
                .eq(GenerationTaskEntity::getTenantId, tenantId)
                .in(GenerationTaskEntity::getStatus,
                        GenerationTaskEntity.Status.PLANNING.getValue(),
                        GenerationTaskEntity.Status.EXECUTING.getValue(),
                        GenerationTaskEntity.Status.VALIDATING.getValue(),
                        GenerationTaskEntity.Status.GENERATING.getValue());
        long generatingTasks = generationTaskMapper.selectCount(taskWrapper);

        ProjectStatsResponse stats = ProjectStatsResponse.builder()
                .totalProjects((int) totalProjects)
                .monthlyNewProjects((int) monthlyNewProjects)
                .generatingTasks((int) generatingTasks)
                .publishedProjects((int) publishedProjects)
                .draftProjects((int) draftProjects)
                .archivedProjects((int) archivedProjects)
                .build();

        return Result.success(stats);
    }

    /**
     * 创建项目
     * 需要登录
     *
     * @param request 创建项目请求参数
     * @return 创建成功的项目信息
     */
    @SaCheckLogin
    @PostMapping
    public Result<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        // 获取当前用户ID和租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : userId;

        log.info("创建项目: userId={}, tenantId={}, name={}", userId, tenantId, request.getName());

        // 构建ProjectEntity
        ProjectEntity project = ProjectEntity.builder()
            .tenantId(tenantId)
            .userId(userId)
            .name(request.getName())
            .description(request.getDescription())
            .coverImageUrl(request.getCoverImageUrl())
            .appSpecId(request.getAppSpecId() != null ? UUID.fromString(request.getAppSpecId()) : null)
            .visibility(request.getVisibility() != null ? request.getVisibility() : "private")
            .tags(request.getTags())
            .ageGroup(request.getAgeGroup())
            .metadata(request.getMetadata())
            .build();

        // 调用Service层创建项目
        ProjectEntity createdProject = projectService.createProject(project);

        // 转换为ProjectResponse
        ProjectResponse response = convertToProjectResponse(createdProject);

        log.info("创建项目成功: projectId={}", createdProject.getId());
        return Result.success(response);
    }

    /**
     * 获取项目详情
     * 需要登录，租户隔离
     *
     * @param id 项目ID
     * @return 项目详细信息
     */
    @SaCheckLogin
    @GetMapping("/{id}")
    public Result<ProjectResponse> getById(@PathVariable UUID id) {
        // 获取当前用户ID和租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : UUID.fromString(userIdStr);

        log.info("查询项目详情: id={}, tenantId={}", id, tenantId);

        // 根据ID和租户ID查询（租户隔离）
        ProjectEntity project = projectService.getByIdAndTenantId(id, tenantId);
        if (project == null) {
            log.warn("项目不存在: id={}", id);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // 增加浏览次数
        projectService.incrementViewCount(id);

        ProjectResponse response = convertToProjectResponse(project);
        return Result.success(response);
    }

    /**
     * 更新项目
     * 需要登录，只能更新自己的项目
     *
     * @param id 项目ID
     * @param request 更新的项目信息
     * @return 更新后的项目信息
     */
    @SaCheckLogin
    @PutMapping("/{id}")
    public Result<ProjectResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody CreateProjectRequest request
    ) {
        // 获取当前用户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);

        log.info("更新项目: projectId={}, userId={}", id, userId);

        // 构建更新的ProjectEntity
        ProjectEntity project = ProjectEntity.builder()
            .name(request.getName())
            .description(request.getDescription())
            .coverImageUrl(request.getCoverImageUrl())
            .visibility(request.getVisibility())
            .tags(request.getTags())
            .ageGroup(request.getAgeGroup())
            .metadata(request.getMetadata())
            .build();

        // 调用Service层更新项目
        ProjectEntity updatedProject = projectService.updateProject(id, project);

        ProjectResponse response = convertToProjectResponse(updatedProject);

        log.info("更新项目成功: projectId={}", id);
        return Result.success(response);
    }

    /**
     * 删除项目（软删除）
     * 需要登录，只能删除自己的项目
     *
     * @param id 项目ID
     * @return 操作结果
     */
    @SaCheckLogin
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable UUID id) {
        // 获取租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : UUID.fromString(userIdStr);

        log.info("删除项目: projectId={}, tenantId={}", id, tenantId);

        // 调用Service层软删除
        projectService.softDelete(id, tenantId);

        log.info("删除项目成功: projectId={}", id);
        return Result.success("删除成功", null);
    }

    /**
     * 分页查询用户的项目列表
     * 查询当前用户创建的项目列表
     *
     * @param current 当前页码
     * @param size 每页数量
     * @param status 状态筛选（可选）
     * @param keyword 搜索关键词（可选）
     * @return 分页结果
     */
    @SaCheckLogin
    @GetMapping
    public Result<PageResult<ProjectResponse>> list(
        @RequestParam(defaultValue = "1") Long current,
        @RequestParam(defaultValue = "10") Long size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword
    ) {
        // 获取当前用户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : userId;

        log.info("分页查询用户项目列表: userId={}, current={}, size={}, status={}, keyword={}",
                userId, current, size, status, keyword);

        // 构建查询条件
        Page<ProjectEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<ProjectEntity> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(ProjectEntity::getUserId, userId)
                .eq(ProjectEntity::getTenantId, tenantId);

        // 状态筛选
        if (status != null && !status.isBlank()) {
            wrapper.eq(ProjectEntity::getStatus, status);
        }

        // 关键词搜索
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ProjectEntity::getName, keyword)
                    .or()
                    .like(ProjectEntity::getDescription, keyword));
        }

        // 按更新时间倒序
        wrapper.orderByDesc(ProjectEntity::getUpdatedAt);

        // 分页查询
        Page<ProjectEntity> result = projectService.page(page, wrapper);

        // 转换为PageResult
        PageResult<ProjectResponse> pageResult = convertToPageResult(result);

        return Result.success(pageResult);
    }

    /**
     * 查询公开项目（社区广场）
     * 公开接口，所有人可访问
     *
     * @param current 当前页码
     * @param size 每页数量
     * @param ageGroup 年龄分组过滤（可选）
     * @return 分页结果
     */
    @GetMapping("/public")
    public Result<PageResult<ProjectResponse>> listPublicProjects(
        @RequestParam(defaultValue = "1") Long current,
        @RequestParam(defaultValue = "10") Long size,
        @RequestParam(required = false) String ageGroup
    ) {
        log.info("查询公开项目: current={}, size={}, ageGroup={}", current, size, ageGroup);

        // 分页查询公开项目
        Page<ProjectEntity> page = projectService.pagePublicProjects(current, size);

        // 如果指定了年龄分组，进行过滤（简化处理，实际应在Service层实现）
        if (ageGroup != null && !ageGroup.isBlank()) {
            // TODO: 在Service层实现按ageGroup过滤
            log.info("按年龄分组过滤: ageGroup={}", ageGroup);
        }

        // 转换为PageResult
        PageResult<ProjectResponse> pageResult = convertToPageResult(page);

        return Result.success(pageResult);
    }

    /**
     * 派生项目（Fork）
     * 需要登录
     *
     * @param id 源项目ID
     * @return 派生后的新项目信息
     */
    @SaCheckLogin
    @PostMapping("/{id}/fork")
    public Result<ProjectResponse> forkProject(@PathVariable UUID id) {
        // 获取当前用户ID和租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : userId;

        log.info("派生项目: sourceProjectId={}, userId={}", id, userId);

        // 调用Service层派生项目
        ProjectEntity forkedProject = projectService.forkProject(id, userId, tenantId);

        ProjectResponse response = convertToProjectResponse(forkedProject);

        log.info("派生项目成功: newProjectId={}, sourceProjectId={}", forkedProject.getId(), id);
        return Result.success(response);
    }

    /**
     * 点赞项目
     * 需要登录
     *
     * @param id 项目ID
     * @return 操作结果
     */
    @SaCheckLogin
    @PostMapping("/{id}/like")
    public Result<Void> likeProject(@PathVariable UUID id) {
        // 获取当前用户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);

        log.info("点赞项目: projectId={}, userId={}", id, userId);

        // 调用Service层点赞
        projectService.likeProject(id, userId);

        log.info("点赞项目成功: projectId={}", id);
        return Result.success("点赞成功", null);
    }

    /**
     * 取消点赞项目
     * 需要登录
     *
     * @param id 项目ID
     * @return 操作结果
     */
    @SaCheckLogin
    @DeleteMapping("/{id}/like")
    public Result<Void> unlikeProject(@PathVariable UUID id) {
        // 获取当前用户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);

        log.info("取消点赞项目: projectId={}, userId={}", id, userId);

        // 调用Service层取消点赞
        projectService.unlikeProject(id, userId);

        log.info("取消点赞项目成功: projectId={}", id);
        return Result.success("取消点赞成功", null);
    }

    /**
     * 收藏项目
     * 需要登录
     *
     * @param id 项目ID
     * @return 操作结果
     */
    @SaCheckLogin
    @PostMapping("/{id}/favorite")
    public Result<Void> favoriteProject(@PathVariable UUID id) {
        // 获取当前用户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);

        log.info("收藏项目: projectId={}, userId={}", id, userId);

        // 调用Service层收藏
        projectService.favoriteProject(id, userId);

        log.info("收藏项目成功: projectId={}", id);
        return Result.success("收藏成功", null);
    }

    /**
     * 取消收藏项目
     * 需要登录
     *
     * @param id 项目ID
     * @return 操作结果
     */
    @SaCheckLogin
    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavoriteProject(@PathVariable UUID id) {
        // 获取当前用户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);

        log.info("取消收藏项目: projectId={}, userId={}", id, userId);

        // 调用Service层取消收藏
        projectService.unfavoriteProject(id, userId);

        log.info("取消收藏项目成功: projectId={}", id);
        return Result.success("取消收藏成功", null);
    }

    /**
     * 发布项目
     * 将项目状态从draft变为published
     *
     * @param id 项目ID
     * @return 操作结果
     */
    @SaCheckLogin
    @PostMapping("/{id}/publish")
    public Result<Void> publishProject(@PathVariable UUID id) {
        // 获取租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : UUID.fromString(userIdStr);

        log.info("发布项目: projectId={}, tenantId={}", id, tenantId);

        // 调用Service层发布项目
        projectService.publishProject(id, tenantId);

        log.info("发布项目成功: projectId={}", id);
        return Result.success("发布成功", null);
    }

    /**
     * 归档项目
     * 将项目状态变为archived
     *
     * @param id 项目ID
     * @return 操作结果
     */
    @SaCheckLogin
    @PostMapping("/{id}/archive")
    public Result<Void> archiveProject(@PathVariable UUID id) {
        // 获取租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : UUID.fromString(userIdStr);

        log.info("归档项目: projectId={}, tenantId={}", id, tenantId);

        // 调用Service层归档项目
        projectService.archiveProject(id, tenantId);

        log.info("归档项目成功: projectId={}", id);
        return Result.success("归档成功", null);
    }

    /**
     * 获取项目执行历史
     * 查询项目关联的所有生成任务
     *
     * @param id 项目ID
     * @return 执行历史列表
     */
    @SaCheckLogin
    @GetMapping("/{id}/execution-history")
    public Result<java.util.List<GenerationTaskEntity>> getExecutionHistory(@PathVariable UUID id) {
        // 获取租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : UUID.fromString(userIdStr);

        log.info("获取项目执行历史: projectId={}, tenantId={}", id, tenantId);

        // 验证项目存在且属于当前租户
        ProjectEntity project = projectService.getByIdAndTenantId(id, tenantId);
        if (project == null) {
            log.warn("项目不存在: id={}", id);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // 查询项目关联的所有生成任务
        LambdaQueryWrapper<GenerationTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenerationTaskEntity::getTenantId, tenantId)
                .and(w -> w.eq(GenerationTaskEntity::getAppSpecId, project.getAppSpecId())
                        .or()
                        .like(GenerationTaskEntity::getUserRequirement, project.getName()))
                .orderByDesc(GenerationTaskEntity::getCreatedAt);

        java.util.List<GenerationTaskEntity> tasks = generationTaskMapper.selectList(wrapper);

        log.info("获取项目执行历史成功: projectId={}, taskCount={}", id, tasks.size());
        return Result.success(tasks);
    }

    /**
     * 将ProjectEntity转换为ProjectResponse
     *
     * @param project 项目实体
     * @return 项目响应DTO
     */
    private ProjectResponse convertToProjectResponse(ProjectEntity project) {
        return ProjectResponse.builder()
            .id(project.getId())
            .tenantId(project.getTenantId())
            .userId(project.getUserId())
            .name(project.getName())
            .description(project.getDescription())
            .coverImageUrl(project.getCoverImageUrl())
            .appSpecId(project.getAppSpecId())
            .status(project.getStatus())
            .visibility(project.getVisibility())
            .viewCount(project.getViewCount())
            .likeCount(project.getLikeCount())
            .forkCount(project.getForkCount())
            .commentCount(project.getCommentCount())
            .tags(project.getTags())
            .ageGroup(project.getAgeGroup())
            .createdAt(project.getCreatedAt())
            .updatedAt(project.getUpdatedAt())
            .publishedAt(project.getPublishedAt())
            .metadata(project.getMetadata())
            .build();
    }

    /**
     * 将Page转换为PageResult
     *
     * @param page MyBatis-Plus分页对象
     * @return PageResult
     */
    private PageResult<ProjectResponse> convertToPageResult(Page<ProjectEntity> page) {
        return PageResult.<ProjectResponse>builder()
            .records(page.getRecords().stream()
                .map(this::convertToProjectResponse)
                .toList())
            .total(page.getTotal())
            .size(page.getSize())
            .current(page.getCurrent())
            .pages(page.getPages())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();
    }
}
