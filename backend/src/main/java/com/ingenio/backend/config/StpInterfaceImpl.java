package com.ingenio.backend.config;

import cn.dev33.satoken.stp.StpInterface;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ingenio.backend.common.enums.Permission;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SaToken权限接口实现
 *
 * 功能：
 * - 实现SaToken的权限查询接口
 * - 提供用户权限列表查询（用于@SaCheckPermission注解）
 * - 提供用户角色列表查询（用于@SaCheckRole注解）
 *
 * 使用场景：
 * 1. 当使用@SaCheckPermission("user:edit")时，SaToken会调用getPermissionList()
 * 2. 当使用@SaCheckRole("admin")时，SaToken会调用getRoleList()
 * 3. 权限验证失败时抛出NotPermissionException或NotRoleException
 *
 * 权限设计：
 * - 当前为简化实现，权限和角色一一对应
 * - USER角色拥有基础用户权限
 * - ADMIN角色拥有管理员权限
 * - SUPER_ADMIN角色拥有超级管理员权限
 *
 * 未来扩展：
 * - Phase 4会实现完整的RBAC权限模型
 * - 支持动态权限配置（数据库驱动）
 * - 支持细粒度权限（user:read, user:write, user:delete等）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;

    /**
     * 获取用户权限列表
     *
     * 根据用户ID查询用户，并返回其权限列表。
     * 权限列表用于@SaCheckPermission注解的验证。
     *
     * 权限映射策略（使用Permission枚举）：
     * - USER角色 → 基础权限（user:*, project:*, appspec:*, generation_task:* 的 read/write）
     * - VIEWER角色 → 仅查看权限（*:read）
     * - ADMIN角色 → USER权限 + 管理权限（*:manage, system:read）
     * - SUPER_ADMIN角色 → ADMIN权限 + 最高权限（*:delete, system:manage）
     *
     * @param loginId 用户ID（登录时存储的用户标识）
     * @param loginType 登录类型（默认为"login"，多账号体系时可用）
     * @return 权限列表（如：["user:read", "user:write"]）
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        log.debug("查询用户权限: userId={}, loginType={}", loginId, loginType);

        try {
            // 1. 根据loginId查询用户
            UUID userId = UUID.fromString(loginId.toString());
            QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("id", userId);
            UserEntity user = userMapper.selectOne(queryWrapper);

            if (user == null) {
                log.warn("用户不存在: userId={}", userId);
                return Collections.emptyList();
            }

            // 2. 根据用户角色返回权限列表（使用Permission枚举）
            List<Permission> permissionEnums = new ArrayList<>();
            String role = user.getRole();

            // 根据角色分配权限
            if ("VIEWER".equals(role) || "viewer".equals(role)) {
                // 查看者：仅有查看权限
                permissionEnums.add(Permission.USER_READ);
                permissionEnums.add(Permission.PROJECT_READ);
                permissionEnums.add(Permission.APPSPEC_READ);
                permissionEnums.add(Permission.GENERATION_TASK_READ);

            } else if ("USER".equals(role) || "user".equals(role)) {
                // 普通用户：基础权限（read + write）
                permissionEnums.add(Permission.USER_READ);
                permissionEnums.add(Permission.USER_WRITE);
                permissionEnums.add(Permission.PROJECT_READ);
                permissionEnums.add(Permission.PROJECT_WRITE);
                permissionEnums.add(Permission.APPSPEC_READ);
                permissionEnums.add(Permission.APPSPEC_WRITE);
                permissionEnums.add(Permission.GENERATION_TASK_READ);
                permissionEnums.add(Permission.GENERATION_TASK_WRITE);

            } else if ("ADMIN".equals(role) || "admin".equals(role)) {
                // 管理员：USER权限 + 管理权限
                permissionEnums.add(Permission.USER_READ);
                permissionEnums.add(Permission.USER_WRITE);
                permissionEnums.add(Permission.USER_MANAGE);
                permissionEnums.add(Permission.PROJECT_READ);
                permissionEnums.add(Permission.PROJECT_WRITE);
                permissionEnums.add(Permission.PROJECT_MANAGE);
                permissionEnums.add(Permission.APPSPEC_READ);
                permissionEnums.add(Permission.APPSPEC_WRITE);
                permissionEnums.add(Permission.APPSPEC_MANAGE);
                permissionEnums.add(Permission.GENERATION_TASK_READ);
                permissionEnums.add(Permission.GENERATION_TASK_WRITE);
                permissionEnums.add(Permission.GENERATION_TASK_MANAGE);
                permissionEnums.add(Permission.SYSTEM_READ);

            } else if ("SUPER_ADMIN".equals(role) || "super_admin".equals(role)) {
                // 超级管理员：拥有所有权限
                permissionEnums.addAll(List.of(Permission.values()));
            }

            // 3. 将Permission枚举转换为字符串列表
            List<String> permissions = permissionEnums.stream()
                    .map(Permission::getCode)
                    .collect(Collectors.toList());

            log.debug("用户权限查询成功: userId={}, role={}, permissions={}", userId, role, permissions);
            return permissions;

        } catch (Exception e) {
            log.error("查询用户权限失败: loginId={}", loginId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户角色列表
     *
     * 根据用户ID查询用户，并返回其角色列表。
     * 角色列表用于@SaCheckRole注解的验证。
     *
     * 角色定义：
     * - USER: 普通用户角色
     * - ADMIN: 管理员角色
     * - SUPER_ADMIN: 超级管理员角色
     *
     * 注意：
     * - 当前每个用户只有一个角色
     * - Phase 4会实现多角色支持（一个用户可以有多个角色）
     *
     * @param loginId 用户ID（登录时存储的用户标识）
     * @param loginType 登录类型（默认为"login"，多账号体系时可用）
     * @return 角色列表（如：["USER", "ADMIN"]）
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        log.debug("查询用户角色: userId={}, loginType={}", loginId, loginType);

        try {
            // 1. 根据loginId查询用户
            UUID userId = UUID.fromString(loginId.toString());
            QueryWrapper<UserEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("id", userId);
            UserEntity user = userMapper.selectOne(queryWrapper);

            if (user == null) {
                log.warn("用户不存在: userId={}", userId);
                return Collections.emptyList();
            }

            // 2. 返回用户角色列表
            String role = user.getRole();
            List<String> roles = new ArrayList<>();
            roles.add(role);

            log.debug("用户角色查询成功: userId={}, roles={}", userId, roles);
            return roles;

        } catch (Exception e) {
            log.error("查询用户角色失败: loginId={}", loginId, e);
            return Collections.emptyList();
        }
    }
}
