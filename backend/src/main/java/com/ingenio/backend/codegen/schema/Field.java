package com.ingenio.backend.codegen.schema;

import lombok.Builder;
import lombok.Data;

/**
 * 数据库字段定义
 *
 * <p>表示数据库表中的一个字段，包括字段名、类型、约束等</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * Field emailField = Field.builder()
 *     .name("email")
 *     .type(FieldType.VARCHAR)
 *     .length(255)
 *     .nullable(false)
 *     .unique(true)
 *     .description("用户邮箱")
 *     .build();
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 2: 数据库Schema生成器
 */
@Data
@Builder
public class Field {

    /**
     * 字段名（PostgreSQL规范：小写+下划线）
     * 示例：id, email, created_at, order_total_amount
     */
    private String name;

    /**
     * 字段类型
     */
    private FieldType type;

    /**
     * 字段长度（仅VARCHAR类型需要）
     * 示例：email VARCHAR(255)
     */
    private Integer length;

    /**
     * 数值精度（仅NUMERIC类型需要）
     * 示例：price NUMERIC(10, 2)
     */
    private Integer precision;

    /**
     * 数值小数位数（仅NUMERIC类型需要）
     */
    private Integer scale;

    /**
     * 是否主键
     */
    @Builder.Default
    private boolean primaryKey = false;

    /**
     * 是否唯一约束
     */
    @Builder.Default
    private boolean unique = false;

    /**
     * 是否允许NULL
     */
    @Builder.Default
    private boolean nullable = true;

    /**
     * 默认值（SQL表达式）
     * 示例：
     * - "uuid_generate_v4()"（UUID主键）
     * - "NOW()"（时间戳）
     * - "'pending'"（字符串字面量）
     * - "0"（数值字面量）
     */
    private String defaultValue;

    /**
     * CHECK约束（SQL表达式）
     * 示例：
     * - "age >= 0 AND age <= 150"
     * - "email ~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z]{2,}$'"
     * - "status IN ('pending', 'approved', 'rejected')"
     */
    private String checkConstraint;

    /**
     * 外键引用
     * 格式：表名.字段名
     * 示例：users.id, products.id
     */
    private String foreignKey;

    /**
     * 外键删除策略
     * CASCADE: 级联删除
     * SET NULL: 设置为NULL
     * RESTRICT: 限制删除（默认）
     */
    @Builder.Default
    private String onDelete = "RESTRICT";

    /**
     * 字段描述（用于生成注释）
     */
    private String description;

    /**
     * 是否创建索引
     * true: 为该字段创建单独的索引
     */
    @Builder.Default
    private boolean indexed = false;
}
