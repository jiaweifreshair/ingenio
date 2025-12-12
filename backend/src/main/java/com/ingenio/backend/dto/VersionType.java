package com.ingenio.backend.dto;

/**
 * 版本类型枚举
 *
 * 用于标识版本快照的类型，支持时光机功能
 */
public enum VersionType {
    /**
     * 规划阶段快照
     * PlanAgent完成需求分析后的版本
     */
    PLAN("规划", "PlanAgent完成需求分析"),

    /**
     * Schema生成快照
     * 数据库Schema生成完成后的版本
     */
    SCHEMA("数据库设计", "DatabaseSchemaGenerator生成DDL"),

    /**
     * 代码生成快照
     * ExecuteAgent完成代码生成后的版本
     */
    CODE("代码生成", "ExecuteAgent生成Kotlin/Compose代码"),

    /**
     * 验证失败快照
     * ValidateAgent测试失败时的版本
     */
    VALIDATION_FAILED("验证失败", "编译/测试/性能验证失败"),

    /**
     * 验证成功快照
     * ValidateAgent测试通过的版本
     */
    VALIDATION_SUCCESS("验证成功", "所有测试通过，可发布"),

    /**
     * 修复快照
     * ExecuteAgent修复bug后的版本
     */
    FIX("Bug修复", "修复验证失败的问题"),

    /**
     * 回滚快照
     * 用户主动回滚到某个历史版本
     */
    ROLLBACK("版本回滚", "回滚到历史版本"),

    /**
     * 最终发布版本
     * 用户确认发布的最终版本
     */
    FINAL("最终发布", "用户确认发布版本");

    private final String displayName;
    private final String description;

    VersionType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
