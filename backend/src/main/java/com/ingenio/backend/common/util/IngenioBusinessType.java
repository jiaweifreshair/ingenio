package com.ingenio.backend.common.util;

import lombok.Getter;

/**
 * Ingenio业务类型枚举 - 基于UUIDv8标准
 *
 * <p>用于UUIDv8业务标识嵌入，每种业务实体对应一个唯一的12位类型码（0x000-0xFFF）
 *
 * <p>布局设计：
 * <ul>
 *   <li>0x000: 保留（未定义类型）</li>
 *   <li>0x001-0x0FF: 核心业务实体（256种）</li>
 *   <li>0x100-0x1FF: 扩展业务实体（256种）</li>
 *   <li>0x200-0xFFF: 预留扩展（3584种）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 生成用户UUID
 * UUID userId = UUIDv8Generator.generate(IngenioBusinessType.USER, 1001);
 *
 * // 生成项目UUID
 * UUID projectId = UUIDv8Generator.generate(IngenioBusinessType.PROJECT, 1001);
 *
 * // 解析UUID
 * UUIDv8Info info = UUIDv8Generator.parse(projectId);
 * IngenioBusinessType type = (IngenioBusinessType) info.getBusinessType();
 * </pre>
 *
 * @author Ingenio Team
 * @since 2025-11-05
 */
@Getter
public enum IngenioBusinessType {

    // ============ 核心业务实体 (0x001-0x0FF) ============

    /**
     * 租户实体
     * 对应表: tenants
     */
    TENANT(0x001, "租户实体", "tenants", 1),

    /**
     * 用户实体
     * 对应表: users
     */
    USER(0x002, "用户实体", "users", 1),

    /**
     * 项目实体
     * 对应表: projects
     */
    PROJECT(0x003, "项目实体", "projects", 1),

    /**
     * 应用规范实体
     * 对应表: app_specs
     */
    APP_SPEC(0x004, "应用规范实体", "app_specs", 1),

    /**
     * 应用规范版本实体
     * 对应表: app_spec_versions
     */
    APP_SPEC_VERSION(0x005, "应用规范版本实体", "app_spec_versions", 1),

    /**
     * 生成代码实体
     * 对应表: generated_code
     */
    GENERATED_CODE(0x006, "生成代码实体", "generated_code", 1),

    /**
     * 项目分叉实体
     * 对应表: forks
     */
    FORK(0x007, "项目分叉实体", "forks", 1),

    /**
     * 社交互动实体
     * 对应表: social_interactions
     */
    SOCIAL_INTERACTION(0x008, "社交互动实体", "social_interactions", 1),

    /**
     * 魔法提示词实体
     * 对应表: magic_prompts
     */
    MAGIC_PROMPT(0x009, "魔法提示词实体", "magic_prompts", 1),

    /**
     * Agent执行记录
     * 对应表: agent_executions
     */
    AGENT_EXECUTION(0x00A, "Agent执行记录", "agent_executions", 1),

    /**
     * 用户会话
     * 对应表: user_sessions
     */
    USER_SESSION(0x00B, "用户会话", "user_sessions", 1),

    /**
     * 系统配置
     * 对应表: system_configs
     */
    SYSTEM_CONFIG(0x00C, "系统配置", "system_configs", 1),

    /**
     * 操作日志
     * 对应表: operation_logs
     */
    OPERATION_LOG(0x00D, "操作日志", "operation_logs", 1),

    // ============ 扩展业务实体 (0x100-0x1FF) ============

    /**
     * 系统审计日志
     * 对应表: audit_logs
     */
    AUDIT_LOG(0x100, "系统审计日志", "audit_logs", 1),

    /**
     * 通知消息
     * 对应表: notifications
     */
    NOTIFICATION(0x101, "通知消息", "notifications", 1),

    /**
     * 文件附件
     * 对应表: attachments
     */
    ATTACHMENT(0x102, "文件附件", "attachments", 1);

    /**
     * 业务类型代码 (12 bits: 0x000-0xFFF)
     */
    private final int code;

    /**
     * 业务类型描述
     */
    private final String description;

    /**
     * 对应的数据库表名
     */
    private final String tableName;

    /**
     * 业务类型版本号（从1开始）
     */
    private final int version;

    /**
     * 构造函数
     *
     * @param code 业务类型代码 (12 bits: 0x000-0xFFF)
     * @param description 类型描述
     * @param tableName 对应表名
     * @param version 版本号（从1开始）
     */
    IngenioBusinessType(int code, String description, String tableName, int version) {
        if (!isValidBusinessTypeCode(code)) {
            throw new IllegalArgumentException(
                String.format("业务类型代码必须在0x%03X-0x%03X范围内: 0x%03X",
                    0x000, 0xFFF, code)
            );
        }
        if (version < 1) {
            throw new IllegalArgumentException("业务类型版本号必须≥1: " + version);
        }
        this.code = code;
        this.description = description;
        this.tableName = tableName;
        this.version = version;
    }

    /**
     * 根据代码获取业务类型（返回最新版本）
     *
     * @param code 业务类型代码
     * @return 对应的IngenioBusinessType枚举
     * @throws IllegalArgumentException 如果代码无效
     */
    public static IngenioBusinessType fromCode(int code) {
        for (IngenioBusinessType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的业务类型代码: 0x" + Integer.toHexString(code));
    }

    /**
     * 根据表名获取业务类型
     *
     * @param tableName 表名
     * @return 对应的IngenioBusinessType枚举，如果不存在返回null
     */
    public static IngenioBusinessType fromTableName(String tableName) {
        for (IngenioBusinessType type : values()) {
            if (type.tableName.equalsIgnoreCase(tableName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查代码是否有效
     *
     * @param code 业务类型代码
     * @return true表示有效
     */
    public static boolean isValidCode(int code) {
        try {
            fromCode(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("%s(0x%03X, v%d)", name(), code, version);
    }

    /**
     * 验证业务类型代码是否有效
     *
     * @param code 业务类型代码
     * @return true表示有效（在0x000-0xFFF范围内）
     */
    private static boolean isValidBusinessTypeCode(int code) {
        return code >= 0x000 && code <= 0xFFF;
    }
}