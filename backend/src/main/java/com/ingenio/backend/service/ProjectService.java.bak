package com.ingenio.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ingenio.backend.entity.ProjectEntity;

import java.util.List;
import java.util.UUID;

/**
 * 项目Service接口
 * 提供项目相关的业务逻辑
 */
public interface ProjectService extends IService<ProjectEntity> {

    /**
     * 创建项目
     *
     * @param project 项目实体
     * @return 创建的项目
     */
    ProjectEntity createProject(ProjectEntity project);

    /**
     * 更新项目
     *
     * @param projectId 项目ID
     * @param project 更新的项目信息
     * @return 更新后的项目
     */
    ProjectEntity updateProject(UUID projectId, ProjectEntity project);

    /**
     * 根据ID和租户ID查询项目（租户隔离）
     *
     * @param id 项目ID
     * @param tenantId 租户ID
     * @return 项目实体
     */
    ProjectEntity getByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * 分页查询用户的项目列表
     *
     * @param userId 用户ID
     * @param current 当前页码
     * @param size 每页数量
     * @return 分页结果
     */
    Page<ProjectEntity> pageByUser(UUID userId, Long current, Long size);

    /**
     * 分页查询公开项目（社区广场）
     *
     * @param current 当前页码
     * @param size 每页数量
     * @return 分页结果
     */
    Page<ProjectEntity> pagePublicProjects(Long current, Long size);

    /**
     * 根据年龄分组查询热门项目
     *
     * @param ageGroup 年龄分组
     * @return 项目列表
     */
    List<ProjectEntity> listTopByAgeGroup(String ageGroup);

    /**
     * 全文搜索项目
     *
     * @param searchQuery 搜索关键词
     * @param current 当前页码
     * @param size 每页数量
     * @return 分页结果
     */
    Page<ProjectEntity> searchProjects(String searchQuery, Long current, Long size);

    /**
     * 派生项目（Fork）
     *
     * @param sourceProjectId 源项目ID
     * @param userId 派生者用户ID
     * @param tenantId 租户ID
     * @return 新项目
     */
    ProjectEntity forkProject(UUID sourceProjectId, UUID userId, UUID tenantId);

    /**
     * 增加项目浏览次数
     *
     * @param projectId 项目ID
     */
    void incrementViewCount(UUID projectId);

    /**
     * 点赞项目
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     */
    void likeProject(UUID projectId, UUID userId);

    /**
     * 取消点赞项目
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     */
    void unlikeProject(UUID projectId, UUID userId);

    /**
     * 收藏项目
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     */
    void favoriteProject(UUID projectId, UUID userId);

    /**
     * 取消收藏项目
     *
     * @param projectId 项目ID
     * @param userId 用户ID
     */
    void unfavoriteProject(UUID projectId, UUID userId);

    /**
     * 发布项目
     *
     * @param projectId 项目ID
     * @param tenantId 租户ID
     */
    void publishProject(UUID projectId, UUID tenantId);

    /**
     * 归档项目
     *
     * @param projectId 项目ID
     * @param tenantId 租户ID
     */
    void archiveProject(UUID projectId, UUID tenantId);

    /**
     * 删除项目（软删除）
     *
     * @param projectId 项目ID
     * @param tenantId 租户ID
     */
    void softDelete(UUID projectId, UUID tenantId);
}
