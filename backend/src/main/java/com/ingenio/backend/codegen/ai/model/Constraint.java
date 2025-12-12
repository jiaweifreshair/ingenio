package com.ingenio.backend.codegen.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 约束条件定义（V2.0 Phase 4.1）
 *
 * <p>描述实体字段的约束条件，用于数据校验和数据库约束</p>
 *
 * <p>约束类型：</p>
 * <ul>
 *   <li>UNIQUE：唯一性约束（邮箱、用户名）</li>
 *   <li>NOT_NULL：非空约束（必填字段）</li>
 *   <li>CHECK：检查约束（年龄范围、枚举值）</li>
 *   <li>FOREIGN_KEY：外键约束（关联表）</li>
 *   <li>PRIMARY_KEY：主键约束</li>
 *   <li>DEFAULT：默认值约束</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Constraint emailUnique = Constraint.builder()
 *     .entity("User")
 *     .field("email")
 *     .type(ConstraintType.UNIQUE)
 *     .name("uk_user_email")
 *     .errorMessage("邮箱已被注册")
 *     .build();
 *
 * Constraint ageCheck = Constraint.builder()
 *     .entity("User")
 *     .field("age")
 *     .type(ConstraintType.CHECK)
 *     .expression("age >= 0 AND age <= 150")
 *     .errorMessage("年龄必须在0-150之间")
 *     .build();
 * }</pre>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 4.1: AI需求理解服务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Constraint {

    /**
     * 约束名称
     * 示例：uk_user_email（唯一约束）、ck_age_range（检查约束）
     */
    private String name;

    /**
     * 约束类型
     * UNIQUE、NOT_NULL、CHECK、FOREIGN_KEY、PRIMARY_KEY、DEFAULT
     */
    private ConstraintType type;

    /**
     * 关联的实体名称
     * 示例：User、Order、Product
     */
    private String entity;

    /**
     * 关联的字段名称
     * 示例：email、age、userId
     */
    private String field;

    /**
     * 约束描述
     * 示例：邮箱必须唯一、年龄必须大于0
     */
    private String description;

    /**
     * 约束表达式（CHECK约束使用）
     * 示例：age >= 0 AND age <= 150
     */
    private String expression;

    /**
     * 默认值（DEFAULT约束使用）
     * 示例：'ACTIVE'、0、CURRENT_TIMESTAMP
     */
    private String defaultValue;

    /**
     * 外键引用的表名（FOREIGN_KEY约束使用）
     * 示例：users、orders
     */
    private String referencedTable;

    /**
     * 外键引用的字段名（FOREIGN_KEY约束使用）
     * 示例：id
     */
    private String referencedField;

    /**
     * 删除规则（FOREIGN_KEY约束使用）
     * CASCADE、SET_NULL、RESTRICT、NO_ACTION
     */
    private String onDelete;

    /**
     * 更新规则（FOREIGN_KEY约束使用）
     * CASCADE、SET_NULL、RESTRICT、NO_ACTION
     */
    private String onUpdate;

    /**
     * 错误提示信息
     * 示例：邮箱已被注册、年龄必须在0-150之间
     */
    private String errorMessage;

    /**
     * 是否在数据库层面强制执行
     * true：生成数据库约束
     * false：仅在应用层校验
     */
    private Boolean enforcedAtDatabase;

    /**
     * 约束类型枚举
     */
    public enum ConstraintType {
        /**
         * 唯一性约束
         * 示例：邮箱、用户名不能重复
         */
        UNIQUE,

        /**
         * 非空约束
         * 示例：用户名、邮箱必须填写
         */
        NOT_NULL,

        /**
         * 检查约束
         * 示例：年龄范围、状态枚举值
         */
        CHECK,

        /**
         * 外键约束
         * 示例：订单的用户ID必须存在于用户表
         */
        FOREIGN_KEY,

        /**
         * 主键约束
         * 示例：用户ID作为主键
         */
        PRIMARY_KEY,

        /**
         * 默认值约束
         * 示例：创建时间默认为当前时间
         */
        DEFAULT
    }
}
