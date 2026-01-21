package com.ingenio.backend.codegen.schema;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据库索引定义
 *
 * <p>
 * 表示一个数据库索引，包括索引名、索引字段、索引类型等
 * </p>
 *
 * <p>
 * 索引类型说明：
 * </p>
 * <ul>
 * <li>BTREE：B树索引（默认，适用于大部分查询场景）</li>
 * <li>HASH：哈希索引（适用于等值查询）</li>
 * <li>GIN：GIN索引（适用于全文搜索、数组、JSONB）</li>
 * <li>GIST：GIST索引（适用于地理空间数据）</li>
 * </ul>
 *
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>{@code
 * // 单列索引
 * Index emailIndex = Index.builder()
 *         .name("users_email_idx")
 *         .columns(List.of("email"))
 *         .type(IndexType.BTREE)
 *         .unique(true)
 *         .build();
 *
 * // 复合索引
 * Index userPostIndex = Index.builder()
 *         .name("posts_user_created_idx")
 *         .columns(List.of("user_id", "created_at"))
 *         .type(IndexType.BTREE)
 *         .unique(false)
 *         .build();
 *
 * // JSONB索引
 * Index metadataIndex = Index.builder()
 *         .name("posts_metadata_gin_idx")
 *         .columns(List.of("metadata"))
 *         .type(IndexType.GIN)
 *         .unique(false)
 *         .build();
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 2: 数据库Schema生成器
 */
@Data
@Builder
public class Index {

    /**
     * 索引名称
     * 命名规范：{表名}_{字段名}_{索引类型}_idx
     * 示例：users_email_btree_idx, posts_metadata_gin_idx
     */
    private String name;

    /**
     * 索引字段列表
     * 单列索引：List.of("email")
     * 复合索引：List.of("user_id", "created_at")
     */
    private List<String> columns;

    /**
     * 索引类型
     * 默认：BTREE
     */
    @Builder.Default
    private IndexType type = IndexType.BTREE;

    /**
     * 是否唯一索引
     * true: UNIQUE INDEX
     * false: 普通INDEX
     */
    @Builder.Default
    private boolean unique = false;

    /**
     * 索引类型枚举
     *
     * <p>
     * PostgreSQL支持的索引类型
     * </p>
     */
    public enum IndexType {
        /**
         * B树索引（默认）
         * 适用场景：大部分查询场景（等值、范围、排序）
         * 性能特点：平衡读写性能，支持多种查询类型
         * 示例：WHERE id = 1, WHERE age > 18, ORDER BY created_at
         */
        BTREE,

        /**
         * 哈希索引
         * 适用场景：等值查询
         * 性能特点：等值查询速度快，但不支持范围查询和排序
         * 示例：WHERE email = 'user@example.com'
         */
        HASH,

        /**
         * GIN索引（Generalized Inverted Index）
         * 适用场景：全文搜索、数组、JSONB、文本搜索
         * 性能特点：查询速度快，但索引构建和更新较慢
         * 示例：
         * - 数组查询：WHERE tags @> ARRAY['tag1']
         * - JSONB查询：WHERE metadata @> '{"key": "value"}'
         * - 全文搜索：WHERE to_tsvector('english', content) @@ to_tsquery('search')
         */
        GIN,

        /**
         * GIST索引（Generalized Search Tree）
         * 适用场景：地理空间数据、范围类型、全文搜索
         * 性能特点：支持多种数据类型，但性能略低于GIN
         * 示例：WHERE location <-> point(1,1) < 10 (PostGIS地理查询)
         */
        GIST;

        /**
         * 获取PostgreSQL DDL中的索引类型字符串
         *
         * @return PostgreSQL索引类型字符串
         */
        public String toPostgreSQLType() {
            return this.name();
        }
    }

    /**
     * 生成PostgreSQL CREATE INDEX语句
     *
     * <p>
     * 示例输出：
     * </p>
     * 
     * <pre>
     * CREATE UNIQUE INDEX users_email_idx ON users USING BTREE (email);
     * CREATE INDEX posts_user_created_idx ON users USING BTREE (user_id, created_at);
     * CREATE INDEX posts_metadata_gin_idx ON posts USING GIN (metadata);
     * </pre>
     *
     * @param tableName 表名
     * @return CREATE INDEX SQL语句
     */
    public String toCreateIndexSQL(String tableName) {
        StringBuilder sql = new StringBuilder();

        // CREATE [UNIQUE] INDEX
        sql.append("CREATE ");
        if (unique) {
            sql.append("UNIQUE ");
        }
        sql.append("INDEX ");

        // 索引名
        sql.append(name);

        // ON 表名
        sql.append(" ON ").append(tableName);

        // USING 索引类型
        sql.append(" USING ").append(type.toPostgreSQLType());

        // (字段列表)
        sql.append(" (");
        sql.append(String.join(", ", columns));
        sql.append(")");

        sql.append(";");

        return sql.toString();
    }

    /**
     * 判断是否为单列索引
     *
     * @return true如果是单列索引
     */
    public boolean isSingleColumn() {
        return columns != null && columns.size() == 1;
    }

    /**
     * 判断是否为复合索引
     *
     * @return true如果是复合索引
     */
    public boolean isComposite() {
        return columns != null && columns.size() > 1;
    }

    /**
     * 获取索引的第一个字段（主字段）
     *
     * @return 第一个字段名，如果列表为空则返回null
     */
    public String getPrimaryColumn() {
        return (columns != null && !columns.isEmpty()) ? columns.get(0) : null;
    }

    public String getName() {
        return name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public IndexType getType() {
        return type;
    }

    public boolean isUnique() {
        return unique;
    }
}
