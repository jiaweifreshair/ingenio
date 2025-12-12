package com.ingenio.backend.common.enums;

/**
 * 系统权限枚举
 * 定义系统中所有可用的权限
 *
 * 权限命名规范：
 * - 格式：{resource}:{action}
 * - resource: user, project, appspec, system等资源
 * - action: read, write, manage, delete等操作
 *
 * 权限分组：
 * 1. 用户权限（USER_*）- 用户管理相关
 * 2. 项目权限（PROJECT_*）- 项目管理相关
 * 3. AppSpec权限（APPSPEC_*）- 应用规范管理相关
 * 4. 系统权限（SYSTEM_*）- 系统配置和管理相关
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
public enum Permission {

    // ==================== 用户权限 ====================

    /**
     * 读取自己的用户信息
     * 所有角色都有此权限
     */
    USER_READ("user:read", "读取用户信息"),

    /**
     * 修改自己的用户信息
     * 所有角色都有此权限
     */
    USER_WRITE("user:write", "修改用户信息"),

    /**
     * 管理其他用户（查看、编辑、禁用）
     * 仅ADMIN和SUPER_ADMIN拥有
     */
    USER_MANAGE("user:manage", "管理用户"),

    /**
     * 删除用户
     * 仅SUPER_ADMIN拥有
     */
    USER_DELETE("user:delete", "删除用户"),

    // ==================== 项目权限 ====================

    /**
     * 读取项目信息（自己创建的项目或公开项目）
     * 所有角色都有此权限
     */
    PROJECT_READ("project:read", "读取项目"),

    /**
     * 创建和修改项目（仅自己的项目）
     * 所有角色都有此权限
     */
    PROJECT_WRITE("project:write", "创建和修改项目"),

    /**
     * 管理所有项目（包括其他用户的项目）
     * 仅ADMIN和SUPER_ADMIN拥有
     */
    PROJECT_MANAGE("project:manage", "管理所有项目"),

    /**
     * 删除项目（包括其他用户的项目）
     * 仅SUPER_ADMIN拥有
     */
    PROJECT_DELETE("project:delete", "删除项目"),

    // ==================== AppSpec权限 ====================

    /**
     * 读取AppSpec（自己创建的或公开的）
     * 所有角色都有此权限
     */
    APPSPEC_READ("appspec:read", "读取应用规范"),

    /**
     * 创建和修改AppSpec（仅自己的）
     * 所有角色都有此权限
     */
    APPSPEC_WRITE("appspec:write", "创建和修改应用规范"),

    /**
     * 管理所有AppSpec（包括其他用户的）
     * 仅ADMIN和SUPER_ADMIN拥有
     */
    APPSPEC_MANAGE("appspec:manage", "管理所有应用规范"),

    /**
     * 删除AppSpec（包括其他用户的）
     * 仅SUPER_ADMIN拥有
     */
    APPSPEC_DELETE("appspec:delete", "删除应用规范"),

    // ==================== 生成任务权限 ====================

    /**
     * 读取生成任务（自己创建的任务）
     * 所有角色都有此权限
     */
    GENERATION_TASK_READ("generation_task:read", "读取生成任务"),

    /**
     * 创建和管理生成任务（仅自己的任务）
     * 所有角色都有此权限
     */
    GENERATION_TASK_WRITE("generation_task:write", "创建和管理生成任务"),

    /**
     * 管理所有生成任务
     * 仅ADMIN和SUPER_ADMIN拥有
     */
    GENERATION_TASK_MANAGE("generation_task:manage", "管理所有生成任务"),

    // ==================== 系统权限 ====================

    /**
     * 查看系统信息（统计数据、日志等）
     * 仅ADMIN和SUPER_ADMIN拥有
     */
    SYSTEM_READ("system:read", "查看系统信息"),

    /**
     * 管理系统设置（配置、参数等）
     * 仅SUPER_ADMIN拥有
     */
    SYSTEM_MANAGE("system:manage", "管理系统设置");

    /**
     * 权限编码（如：user:read）
     */
    private final String code;

    /**
     * 权限描述
     */
    private final String description;

    /**
     * 构造函数
     *
     * @param code 权限编码
     * @param description 权限描述
     */
    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取权限编码
     *
     * @return 权限编码字符串
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取权限描述
     *
     * @return 权限描述字符串
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据权限编码查找对应的权限枚举
     *
     * @param code 权限编码
     * @return 权限枚举，如果未找到返回null
     */
    public static Permission fromCode(String code) {
        for (Permission permission : Permission.values()) {
            if (permission.code.equals(code)) {
                return permission;
            }
        }
        return null;
    }

    /**
     * 判断权限是否为管理员专属权限
     * 管理员专属权限包括：*_MANAGE、*_DELETE、SYSTEM_*
     *
     * @return true如果是管理员专属权限
     */
    public boolean isAdminOnly() {
        return code.contains(":manage") ||
               code.contains(":delete") ||
               code.startsWith("system:");
    }

    /**
     * 判断权限是否为超级管理员专属权限
     * 超级管理员专属权限包括：*_DELETE、SYSTEM_MANAGE
     *
     * @return true如果是超级管理员专属权限
     */
    public boolean isSuperAdminOnly() {
        return code.contains(":delete") ||
               code.equals("system:manage");
    }

    @Override
    public String toString() {
        return code;
    }
}
