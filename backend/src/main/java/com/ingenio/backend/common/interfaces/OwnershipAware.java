package com.ingenio.backend.common.interfaces;

import java.util.UUID;

/**
 * 所有权感知接口
 * 用于实现Row Level Security（行级安全）
 *
 * 实现此接口的实体类表示该资源有明确的所有者，
 * 需要进行所有权验证以确保用户只能访问自己的资源。
 *
 * 适用场景：
 * - ProjectEntity: 项目属于创建者
 * - AppSpecEntity: AppSpec属于创建者
 * - GenerationTaskEntity: 生成任务属于创建者
 * - ApiKeyEntity: API密钥属于创建者
 *
 * 使用方式：
 * <pre>{@code
 * @Service
 * public class ProjectService {
 *     public void updateProject(UUID projectId, UpdateProjectDTO dto) {
 *         ProjectEntity project = projectRepository.findById(projectId);
 *
 *         // 验证所有权
 *         UUID currentUserId = getCurrentUserId();
 *         if (!project.isOwnedBy(currentUserId)) {
 *             throw new ForbiddenException("无权操作此项目");
 *         }
 *
 *         // 执行更新操作
 *         project.setName(dto.getName());
 *         projectRepository.save(project);
 *     }
 * }
 * }</pre>
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
public interface OwnershipAware {

    /**
     * 获取资源所有者的用户ID
     *
     * 实现示例：
     * <pre>{@code
     * @Override
     * public UUID getOwnerId() {
     *     return this.userId;  // 或 this.createdByUserId
     * }
     * }</pre>
     *
     * @return 所有者用户ID，如果资源无所有者返回null
     */
    UUID getOwnerId();

    /**
     * 判断指定用户是否为资源所有者
     *
     * 默认实现：对比getOwnerId()与传入的userId是否相等
     * 子类可以覆盖此方法实现更复杂的所有权逻辑（如团队共享）
     *
     * @param userId 要检查的用户ID
     * @return true如果用户是所有者，false否则
     */
    default boolean isOwnedBy(UUID userId) {
        if (userId == null || getOwnerId() == null) {
            return false;
        }
        return getOwnerId().equals(userId);
    }

    /**
     * 获取资源所属的租户ID（用于多租户隔离）
     *
     * 实现示例：
     * <pre>{@code
     * @Override
     * public UUID getTenantId() {
     *     return this.tenantId;
     * }
     * }</pre>
     *
     * @return 租户ID，如果不支持多租户返回null
     */
    UUID getTenantId();

    /**
     * 判断指定租户是否拥有此资源
     *
     * 默认实现：对比getTenantId()与传入的tenantId是否相等
     *
     * @param tenantId 要检查的租户ID
     * @return true如果资源属于该租户，false否则
     */
    default boolean belongsToTenant(UUID tenantId) {
        if (tenantId == null || getTenantId() == null) {
            return false;
        }
        return getTenantId().equals(tenantId);
    }
}
