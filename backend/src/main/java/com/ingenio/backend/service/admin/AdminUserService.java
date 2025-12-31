package com.ingenio.backend.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin API - 用户管理服务
 *
 * 用途：供 JeecgBoot 管理 C端用户
 *
 * 核心功能：
 * 1. 用户列表查询（分页、筛选）
 * 2. 用户状态变更（冻结/解冻）
 * 3. 用户详情查询
 *
 * 安全说明：
 * - 不暴露密码哈希等敏感字段
 * - 状态变更需要记录审计日志
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final AdminAuditLogService auditLogService;

    /**
     * 分页查询用户列表
     *
     * @param tenantId 租户ID（可选）
     * @param status   用户状态（可选）
     * @param keyword  搜索关键词（用户名/邮箱模糊匹配，可选）
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 用户分页列表
     */
    public Page<UserEntity> listUsers(
            String tenantId,
            String status,
            String keyword,
            int pageNum,
            int pageSize
    ) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();

        // 租户筛选
        if (StringUtils.hasText(tenantId)) {
            try {
                wrapper.eq(UserEntity::getTenantId, UUID.fromString(tenantId));
            } catch (IllegalArgumentException e) {
                log.warn("无效的租户ID格式: {}", tenantId);
            }
        }

        // 状态筛选
        if (StringUtils.hasText(status)) {
            wrapper.eq(UserEntity::getStatus, status);
        }

        // 关键词搜索（用户名或邮箱）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(UserEntity::getUsername, keyword)
                    .or()
                    .like(UserEntity::getEmail, keyword)
            );
        }

        // 按创建时间倒序
        wrapper.orderByDesc(UserEntity::getCreatedAt);

        Page<UserEntity> page = new Page<>(pageNum, pageSize);
        Page<UserEntity> result = userMapper.selectPage(page, wrapper);

        // 脱敏：移除密码哈希
        result.getRecords().forEach(user -> user.setPasswordHash(null));

        return result;
    }

    /**
     * 查询用户详情
     *
     * @param userId 用户ID
     * @return 用户实体（脱敏后）
     */
    public UserEntity getUser(UUID userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user != null) {
            // 脱敏：移除密码哈希
            user.setPasswordHash(null);
        }
        return user;
    }

    /**
     * 冻结用户
     *
     * @param userId   用户ID
     * @param operator 操作人
     * @param reason   冻结原因
     * @return 更新后的用户实体
     */
    @Transactional
    public UserEntity freezeUser(UUID userId, String operator, String reason) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }

        String beforeStatus = user.getStatus();
        if ("suspended".equals(beforeStatus)) {
            log.info("用户已处于冻结状态: userId={}", userId);
            user.setPasswordHash(null);
            return user;
        }

        log.info("冻结用户: userId={}, operator={}, reason={}", userId, operator, reason);

        user.setStatus("suspended");
        user.setUpdatedAt(Instant.now());
        userMapper.updateById(user);

        // 记录审计日志
        auditLogService.log(
                user.getTenantId() != null ? user.getTenantId().toString() : null,
                null, // traceId
                operator,
                "ADMIN",
                "ACCOUNT_FREEZE",
                "USER",
                userId.toString(),
                java.util.Map.of("status", beforeStatus),
                java.util.Map.of("status", "suspended", "reason", reason != null ? reason : ""),
                "SUCCESS",
                null,
                null, null, null, null,
                java.util.Map.of("reason", reason != null ? reason : "")
        );

        user.setPasswordHash(null);
        return user;
    }

    /**
     * 解冻用户
     *
     * @param userId   用户ID
     * @param operator 操作人
     * @return 更新后的用户实体
     */
    @Transactional
    public UserEntity unfreezeUser(UUID userId, String operator) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }

        String beforeStatus = user.getStatus();
        if ("active".equals(beforeStatus)) {
            log.info("用户已处于活跃状态: userId={}", userId);
            user.setPasswordHash(null);
            return user;
        }

        log.info("解冻用户: userId={}, operator={}", userId, operator);

        user.setStatus("active");
        user.setUpdatedAt(Instant.now());
        userMapper.updateById(user);

        // 记录审计日志
        auditLogService.log(
                user.getTenantId() != null ? user.getTenantId().toString() : null,
                null, // traceId
                operator,
                "ADMIN",
                "ACCOUNT_UNFREEZE",
                "USER",
                userId.toString(),
                java.util.Map.of("status", beforeStatus),
                java.util.Map.of("status", "active"),
                "SUCCESS",
                null,
                null, null, null, null, null
        );

        user.setPasswordHash(null);
        return user;
    }

    /**
     * 更新用户状态
     *
     * @param userId   用户ID
     * @param status   目标状态（active/inactive/suspended）
     * @param operator 操作人
     * @return 更新后的用户实体
     */
    @Transactional
    public UserEntity updateUserStatus(UUID userId, String status, String operator) {
        // 验证状态值
        if (!isValidStatus(status)) {
            throw new IllegalArgumentException("无效的状态值: " + status);
        }

        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }

        String beforeStatus = user.getStatus();
        if (status.equals(beforeStatus)) {
            log.info("用户状态未变化: userId={}, status={}", userId, status);
            user.setPasswordHash(null);
            return user;
        }

        log.info("更新用户状态: userId={}, beforeStatus={}, afterStatus={}, operator={}",
                userId, beforeStatus, status, operator);

        user.setStatus(status);
        user.setUpdatedAt(Instant.now());
        userMapper.updateById(user);

        // 记录审计日志
        String action = "suspended".equals(status) ? "ACCOUNT_FREEZE" :
                        "active".equals(status) ? "ACCOUNT_UNFREEZE" : "USER_UPDATE";
        auditLogService.log(
                user.getTenantId() != null ? user.getTenantId().toString() : null,
                null,
                operator,
                "ADMIN",
                action,
                "USER",
                userId.toString(),
                java.util.Map.of("status", beforeStatus),
                java.util.Map.of("status", status),
                "SUCCESS",
                null,
                null, null, null, null, null
        );

        user.setPasswordHash(null);
        return user;
    }

    /**
     * 验证状态值是否合法
     */
    private boolean isValidStatus(String status) {
        return "active".equals(status)
                || "inactive".equals(status)
                || "suspended".equals(status);
    }

    /**
     * 统计用户数量
     *
     * @param tenantId 租户ID（可选）
     * @param status   状态（可选）
     * @return 用户数量
     */
    public long countUsers(String tenantId, String status) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(tenantId)) {
            try {
                wrapper.eq(UserEntity::getTenantId, UUID.fromString(tenantId));
            } catch (IllegalArgumentException e) {
                log.warn("无效的租户ID格式: {}", tenantId);
            }
        }

        if (StringUtils.hasText(status)) {
            wrapper.eq(UserEntity::getStatus, status);
        }

        return userMapper.selectCount(wrapper);
    }
}
