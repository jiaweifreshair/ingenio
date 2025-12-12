package com.ingenio.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ingenio.backend.entity.AppSpecEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AppSpec Service接口
 * 提供AppSpec相关的业务逻辑
 */
public interface AppSpecService extends IService<AppSpecEntity> {

    /**
     * 创建AppSpec
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param specContent AppSpec JSON内容
     * @return AppSpec实体
     */
    AppSpecEntity createAppSpec(UUID tenantId, UUID userId, Map<String, Object> specContent);

    /**
     * 根据ID和租户ID查询AppSpec（租户隔离）
     *
     * @param id AppSpec ID
     * @param tenantId 租户ID
     * @return AppSpec实体
     */
    AppSpecEntity getByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * 分页查询用户的AppSpec列表
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param current 当前页码
     * @param size 每页数量
     * @return 分页结果
     */
    Page<AppSpecEntity> pageByUser(UUID tenantId, UUID userId, Long current, Long size);

    /**
     * 根据状态查询AppSpec列表
     *
     * @param tenantId 租户ID
     * @param status 状态
     * @return AppSpec列表
     */
    List<AppSpecEntity> listByStatus(UUID tenantId, String status);

    /**
     * 更新AppSpec状态
     *
     * @param id AppSpec ID
     * @param status 新状态
     */
    void updateStatus(UUID id, String status);

    /**
     * 更新AppSpec质量评分
     *
     * @param id AppSpec ID
     * @param qualityScore 质量评分（0-100）
     */
    void updateQualityScore(UUID id, Integer qualityScore);

    /**
     * 创建AppSpec的新版本
     *
     * @param parentId 父版本ID
     * @param specContent 新的AppSpec内容
     * @return 新版本AppSpec实体
     */
    AppSpecEntity createVersion(UUID parentId, Map<String, Object> specContent);

    /**
     * 删除AppSpec（软删除）
     *
     * @param id AppSpec ID
     * @param tenantId 租户ID
     */
    void softDelete(UUID id, UUID tenantId);
}
