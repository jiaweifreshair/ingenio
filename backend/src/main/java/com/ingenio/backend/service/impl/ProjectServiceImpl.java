package com.ingenio.backend.service.impl;

import java.time.Instant;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ingenio.backend.common.annotation.RequireOwnership;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.entity.ForkEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.entity.SocialInteractionEntity;
import com.ingenio.backend.mapper.ForkMapper;
import com.ingenio.backend.mapper.ProjectMapper;
import com.ingenio.backend.mapper.SocialInteractionMapper;
import com.ingenio.backend.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 项目Service实现类
 * 提供项目相关的完整业务逻辑，包括CRUD、社交互动、派生等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, ProjectEntity> implements ProjectService {

    private final ProjectMapper projectMapper;
    private final SocialInteractionMapper socialInteractionMapper;
    private final ForkMapper forkMapper;

    /**
     * 创建项目
     *
     * @param project 项目实体
     * @return 创建的项目
     * @throws BusinessException 当参数校验失败或创建失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectEntity createProject(ProjectEntity project) {
        // 参数校验
        if (project == null || project.getTenantId() == null || project.getUserId() == null) {
            log.error("创建项目失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 设置默认值
        if (project.getStatus() == null || project.getStatus().isEmpty()) {
            project.setStatus(ProjectEntity.Status.DRAFT.getValue());
        }
        if (project.getVisibility() == null || project.getVisibility().isEmpty()) {
            project.setVisibility(ProjectEntity.Visibility.PRIVATE.getValue());
        }
        if (project.getViewCount() == null) {
            project.setViewCount(0);
        }
        if (project.getLikeCount() == null) {
            project.setLikeCount(0);
        }
        if (project.getForkCount() == null) {
            project.setForkCount(0);
        }
        if (project.getCommentCount() == null) {
            project.setCommentCount(0);
        }
        if (project.getMetadata() == null) {
            project.setMetadata(new HashMap<>());
        }

        // 保存到数据库
        if (!save(project)) {
            log.error("创建项目失败: 数据库保存失败 - tenantId={}, userId={}",
                    project.getTenantId(), project.getUserId());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        log.info("创建项目成功: id={}, name={}, tenantId={}, userId={}",
                project.getId(), project.getName(), project.getTenantId(), project.getUserId());
        return project;
    }

    /**
     * 更新项目
     *
     * @param projectId 项目ID
     * @param project 更新的项目信息
     * @return 更新后的项目
     * @throws BusinessException 当项目不存在或更新失败时抛出
     */
    @Override
    @RequireOwnership(resourceType = "project", idParam = "projectId")
    @Transactional(rollbackFor = Exception.class)
    public ProjectEntity updateProject(UUID projectId, ProjectEntity project) {
        if (projectId == null || project == null) {
            log.error("更新项目失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 查询原项目
        ProjectEntity existingProject = getById(projectId);
        if (existingProject == null) {
            log.warn("更新项目失败: 项目不存在 - projectId={}", projectId);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // 更新字段
        if (project.getName() != null) {
            existingProject.setName(project.getName());
        }
        if (project.getDescription() != null) {
            existingProject.setDescription(project.getDescription());
        }
        if (project.getCoverImageUrl() != null) {
            existingProject.setCoverImageUrl(project.getCoverImageUrl());
        }
        if (project.getVisibility() != null) {
            existingProject.setVisibility(project.getVisibility());
        }
        if (project.getTags() != null) {
            existingProject.setTags(project.getTags());
        }
        if (project.getAgeGroup() != null) {
            existingProject.setAgeGroup(project.getAgeGroup());
        }
        if (project.getMetadata() != null) {
            existingProject.setMetadata(project.getMetadata());
        }

        // 保存更新
        if (!updateById(existingProject)) {
            log.error("更新项目失败: 数据库更新失败 - projectId={}", projectId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        log.info("更新项目成功: id={}, name={}", projectId, existingProject.getName());
        return existingProject;
    }

    /**
     * 根据ID和租户ID查询项目（租户隔离）
     *
     * @param id 项目ID
     * @param tenantId 租户ID
     * @return 项目实体
     * @throws BusinessException 当项目不存在时抛出
     */
    @Override
    public ProjectEntity getByIdAndTenantId(UUID id, UUID tenantId) {
        if (id == null || tenantId == null) {
            log.error("查询项目失败: 参数不能为空 - id={}, tenantId={}", id, tenantId);
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        ProjectEntity project = getById(id);
        if (project == null) {
            log.warn("项目不存在: id={}", id);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // 租户隔离检查
        if (!project.getTenantId().equals(tenantId)) {
            log.warn("无权访问项目: id={}, tenantId={}, projectTenantId={}",
                    id, tenantId, project.getTenantId());
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED);
        }

        return project;
    }

    /**
     * 分页查询用户的项目列表
     *
     * @param userId 用户ID
     * @param current 当前页码
     * @param size 每页数量
     * @return 分页结果
     */
    @Override
    public Page<ProjectEntity> pageByUser(UUID userId, Long current, Long size) {
        if (userId == null) {
            log.error("分页查询用户项目失败: userId不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        Page<ProjectEntity> page = new Page<>(current, size);
        projectMapper.selectPageByUserId(page, userId);
        return page;
    }

    /**
     * 分页查询公开项目（社区广场）
     *
     * @param current 当前页码
     * @param size 每页数量
     * @return 分页结果
     */
    @Override
    public Page<ProjectEntity> pagePublicProjects(Long current, Long size) {
        Page<ProjectEntity> page = new Page<>(current, size);
        projectMapper.selectPageByVisibilityAndStatus(
                page,
                ProjectEntity.Visibility.PUBLIC.getValue(),
                ProjectEntity.Status.PUBLISHED.getValue()
        );
        return page;
    }

    /**
     * 根据年龄分组查询热门项目
     *
     * @param ageGroup 年龄分组
     * @return 项目列表
     */
    @Override
    public List<ProjectEntity> listTopByAgeGroup(String ageGroup) {
        if (ageGroup == null || ageGroup.isEmpty()) {
            log.error("查询热门项目失败: ageGroup不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        return projectMapper.findTopByAgeGroupAndVisibility(
                ageGroup,
                ProjectEntity.Visibility.PUBLIC.getValue()
        );
    }

    /**
     * 全文搜索项目
     *
     * @param searchQuery 搜索关键词
     * @param current 当前页码
     * @param size 每页数量
     * @return 分页结果
     */
    @Override
    public Page<ProjectEntity> searchProjects(String searchQuery, Long current, Long size) {
        if (searchQuery == null || searchQuery.isEmpty()) {
            log.error("搜索项目失败: searchQuery不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        Page<ProjectEntity> page = new Page<>(current, size);
        projectMapper.searchProjects(page, searchQuery);
        return page;
    }

    /**
     * 派生项目（Fork）
     *
     * @param sourceProjectId 源项目ID
     * @param userId 派生者用户ID
     * @param tenantId 租户ID
     * @return 新项目
     * @throws BusinessException 当源项目不存在或已派生时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectEntity forkProject(UUID sourceProjectId, UUID userId, UUID tenantId) {
        if (sourceProjectId == null || userId == null || tenantId == null) {
            log.error("派生项目失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 查询源项目
        ProjectEntity sourceProject = getById(sourceProjectId);
        if (sourceProject == null) {
            log.warn("派生项目失败: 源项目不存在 - sourceProjectId={}", sourceProjectId);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // 检查是否已派生（可选：根据业务需求决定是否允许多次派生）
        // Optional<ForkEntity> existingFork = forkMapper.findBySourceProjectIdAndUserId(sourceProjectId, userId);
        // if (existingFork.isPresent()) {
        //     log.warn("派生项目失败: 已派生过该项目 - sourceProjectId={}, userId={}", sourceProjectId, userId);
        //     throw new BusinessException(ErrorCode.PROJECT_ALREADY_FORKED);
        // }

        // 创建派生项目
        ProjectEntity forkedProject = ProjectEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .name(sourceProject.getName() + " (Fork)")
                .description(sourceProject.getDescription())
                .coverImageUrl(sourceProject.getCoverImageUrl())
                .appSpecId(sourceProject.getAppSpecId()) // 复用相同的AppSpec
                .status(ProjectEntity.Status.DRAFT.getValue())
                .visibility(ProjectEntity.Visibility.PRIVATE.getValue())
                .viewCount(0)
                .likeCount(0)
                .forkCount(0)
                .commentCount(0)
                .tags(sourceProject.getTags())
                .ageGroup(sourceProject.getAgeGroup())
                .metadata(new HashMap<>())
                .build();

        if (!save(forkedProject)) {
            log.error("派生项目失败: 数据库保存失败 - sourceProjectId={}, userId={}",
                    sourceProjectId, userId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        // 记录派生关系
        ForkEntity fork = ForkEntity.builder()
                .sourceProjectId(sourceProjectId)
                .forkedProjectId(forkedProject.getId())
                .forkedByTenantId(tenantId)
                .forkedByUserId(userId)
                .customizations(new HashMap<>())
                .forkDescription("Fork from " + sourceProject.getName())
                .metadata(new HashMap<>())
                .build();

        forkMapper.insert(fork);

        // 增加源项目的派生数
        projectMapper.incrementForkCount(sourceProjectId);

        log.info("派生项目成功: sourceProjectId={}, forkedProjectId={}, userId={}",
                sourceProjectId, forkedProject.getId(), userId);
        return forkedProject;
    }

    /**
     * 增加项目浏览次数
     *
     * @param projectId 项目ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void incrementViewCount(UUID projectId) {
        if (projectId == null) {
            log.error("增加浏览次数失败: projectId不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        int affectedRows = projectMapper.incrementViewCount(projectId);
        if (affectedRows == 0) {
            log.warn("增加浏览次数失败: 项目不存在 - projectId={}", projectId);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        log.debug("增加项目浏览次数: projectId={}", projectId);
    }

    /**
     * 点赞项目
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     * @throws BusinessException 当已点赞时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeProject(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            log.error("点赞项目失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 检查是否已点赞
        Optional<SocialInteractionEntity> existingLike = socialInteractionMapper.findByUserIdAndProjectIdAndType(
                userId, projectId, SocialInteractionEntity.InteractionType.LIKE.getValue()
        );

        if (existingLike.isPresent()) {
            log.warn("点赞项目失败: 已点赞 - projectId={}, userId={}", projectId, userId);
            return; // 幂等处理：已点赞则直接返回
        }

        // 查询项目（确保项目存在）
        ProjectEntity project = getById(projectId);
        if (project == null) {
            log.warn("点赞项目失败: 项目不存在 - projectId={}", projectId);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // 创建点赞记录
        SocialInteractionEntity like = SocialInteractionEntity.builder()
                .tenantId(project.getTenantId())
                .userId(userId)
                .projectId(projectId)
                .interactionType(SocialInteractionEntity.InteractionType.LIKE.getValue())
                .metadata(new HashMap<>())
                .build();

        socialInteractionMapper.insert(like);

        // 增加项目点赞数
        projectMapper.incrementLikeCount(projectId);

        log.info("点赞项目成功: projectId={}, userId={}", projectId, userId);
    }

    /**
     * 取消点赞项目
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeProject(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            log.error("取消点赞失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 删除点赞记录
        int affectedRows = socialInteractionMapper.deleteByUserIdAndProjectIdAndType(
                userId, projectId, SocialInteractionEntity.InteractionType.LIKE.getValue()
        );

        if (affectedRows > 0) {
            // 减少项目点赞数
            projectMapper.decrementLikeCount(projectId);
            log.info("取消点赞成功: projectId={}, userId={}", projectId, userId);
        } else {
            log.warn("取消点赞失败: 未找到点赞记录 - projectId={}, userId={}", projectId, userId);
        }
    }

    /**
     * 收藏项目
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favoriteProject(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            log.error("收藏项目失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 检查是否已收藏
        Optional<SocialInteractionEntity> existingFavorite = socialInteractionMapper.findByUserIdAndProjectIdAndType(
                userId, projectId, SocialInteractionEntity.InteractionType.FAVORITE.getValue()
        );

        if (existingFavorite.isPresent()) {
            log.warn("收藏项目失败: 已收藏 - projectId={}, userId={}", projectId, userId);
            return; // 幂等处理：已收藏则直接返回
        }

        // 查询项目（确保项目存在）
        ProjectEntity project = getById(projectId);
        if (project == null) {
            log.warn("收藏项目失败: 项目不存在 - projectId={}", projectId);
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // 创建收藏记录
        SocialInteractionEntity favorite = SocialInteractionEntity.builder()
                .tenantId(project.getTenantId())
                .userId(userId)
                .projectId(projectId)
                .interactionType(SocialInteractionEntity.InteractionType.FAVORITE.getValue())
                .metadata(new HashMap<>())
                .build();

        socialInteractionMapper.insert(favorite);

        log.info("收藏项目成功: projectId={}, userId={}", projectId, userId);
    }

    /**
     * 取消收藏项目
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavoriteProject(UUID projectId, UUID userId) {
        if (projectId == null || userId == null) {
            log.error("取消收藏失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 删除收藏记录
        int affectedRows = socialInteractionMapper.deleteByUserIdAndProjectIdAndType(
                userId, projectId, SocialInteractionEntity.InteractionType.FAVORITE.getValue()
        );

        if (affectedRows > 0) {
            log.info("取消收藏成功: projectId={}, userId={}", projectId, userId);
        } else {
            log.warn("取消收藏失败: 未找到收藏记录 - projectId={}, userId={}", projectId, userId);
        }
    }

    /**
     * 发布项目
     *
     * @param projectId 项目ID
     * @param tenantId 租户ID
     * @throws BusinessException 当项目不存在或无权操作时抛出
     */
    @Override
    @RequireOwnership(resourceType = "project", idParam = "projectId")
    @Transactional(rollbackFor = Exception.class)
    public void publishProject(UUID projectId, UUID tenantId) {
        if (projectId == null || tenantId == null) {
            log.error("发布项目失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 查询项目（租户隔离）
        ProjectEntity project = getByIdAndTenantId(projectId, tenantId);

        // 更新状态为已发布
        project.setStatus(ProjectEntity.Status.PUBLISHED.getValue());
        project.setPublishedAt(Instant.now());

        if (!updateById(project)) {
            log.error("发布项目失败: 数据库更新失败 - projectId={}", projectId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        log.info("发布项目成功: projectId={}, tenantId={}", projectId, tenantId);
    }

    /**
     * 归档项目
     *
     * @param projectId 项目ID
     * @param tenantId 租户ID
     * @throws BusinessException 当项目不存在或无权操作时抛出
     */
    @Override
    @RequireOwnership(resourceType = "project", idParam = "projectId")
    @Transactional(rollbackFor = Exception.class)
    public void archiveProject(UUID projectId, UUID tenantId) {
        if (projectId == null || tenantId == null) {
            log.error("归档项目失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 查询项目（租户隔离）
        ProjectEntity project = getByIdAndTenantId(projectId, tenantId);

        // 更新状态为已归档
        project.setStatus(ProjectEntity.Status.ARCHIVED.getValue());

        if (!updateById(project)) {
            log.error("归档项目失败: 数据库更新失败 - projectId={}", projectId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        log.info("归档项目成功: projectId={}, tenantId={}", projectId, tenantId);
    }

    /**
     * 删除项目（软删除）
     *
     * @param projectId 项目ID
     * @param tenantId 租户ID
     * @throws BusinessException 当项目不存在或无权操作时抛出
     */
    @Override
    @RequireOwnership(resourceType = "project", idParam = "projectId")
    @Transactional(rollbackFor = Exception.class)
    public void softDelete(UUID projectId, UUID tenantId) {
        if (projectId == null || tenantId == null) {
            log.error("删除项目失败: 参数不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 查询项目（租户隔离）
        ProjectEntity project = getByIdAndTenantId(projectId, tenantId);

        // 执行软删除
        if (!removeById(projectId)) {
            log.error("删除项目失败: 数据库删除失败 - projectId={}", projectId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        log.info("删除项目成功: projectId={}, tenantId={}", projectId, tenantId);
    }
}
