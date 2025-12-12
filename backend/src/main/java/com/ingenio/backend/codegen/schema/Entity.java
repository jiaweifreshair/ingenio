package com.ingenio.backend.codegen.schema;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据库实体定义
 *
 * <p>表示一个数据库表的完整定义，包括表名、字段、索引、约束等</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * Entity user = Entity.builder()
 *     .name("users")
 *     .description("用户表")
 *     .fields(List.of(
 *         Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
 *         Field.builder().name("email").type(FieldType.VARCHAR).length(255).unique(true).build()
 *     ))
 *     .build();
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 2: 数据库Schema生成器
 */
@Data
@Builder
public class Entity {

    /**
     * 表名（PostgreSQL规范：小写+下划线）
     * 示例：users, blog_posts, order_items
     */
    private String name;

    /**
     * 表描述（用于生成注释）
     */
    private String description;

    /**
     * 字段列表
     */
    private List<Field> fields;

    /**
     * 索引列表
     */
    @Builder.Default
    private List<Index> indexes = List.of();

    /**
     * 是否启用RLS（Row Level Security）
     * Supabase推荐：所有用户相关表都启用RLS
     */
    @Builder.Default
    private boolean rlsEnabled = true;

    /**
     * RLS策略列表
     */
    @Builder.Default
    private List<RLSPolicy> rlsPolicies = List.of();

    /**
     * 是否启用软删除
     * true: 添加deleted_at字段
     */
    @Builder.Default
    private boolean softDelete = false;

    /**
     * 是否启用时间戳
     * true: 添加created_at和updated_at字段
     */
    @Builder.Default
    private boolean timestamps = true;
}
