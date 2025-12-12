package com.ingenio.backend.codegen.schema;

import lombok.Builder;
import lombok.Data;

/**
 * 实体关系定义
 *
 * <p>表示两个数据库实体（表）之间的关系</p>
 *
 * <p>关系类型：</p>
 * <ul>
 *   <li>ONE_TO_ONE (1:1)：一对一关系（如User和UserProfile）</li>
 *   <li>ONE_TO_MANY (1:N)：一对多关系（如User和Post）</li>
 *   <li>MANY_TO_MANY (N:M)：多对多关系（如Post和Tag，需要中间表）</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 1:1关系：User和UserProfile
 * EntityRelationship userProfile = EntityRelationship.builder()
 *     .fromEntity("users")
 *     .toEntity("user_profiles")
 *     .type(RelationType.ONE_TO_ONE)
 *     .foreignKeyField("user_id")
 *     .build();
 *
 * // 1:N关系：User和Post
 * EntityRelationship userPosts = EntityRelationship.builder()
 *     .fromEntity("users")
 *     .toEntity("posts")
 *     .type(RelationType.ONE_TO_MANY)
 *     .foreignKeyField("author_id")
 *     .build();
 *
 * // N:M关系：Post和Tag（通过post_tags中间表）
 * EntityRelationship postTags = EntityRelationship.builder()
 *     .fromEntity("posts")
 *     .toEntity("tags")
 *     .type(RelationType.MANY_TO_MANY)
 *     .foreignKeyField("post_id")  // 中间表中的外键字段
 *     .build();
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 2: 数据库Schema生成器
 */
@Data
@Builder
public class EntityRelationship {

    /**
     * 源实体（表名）
     * 关系的"一"方或"主"方
     * 示例：users（一个用户有多个帖子）
     */
    private String fromEntity;

    /**
     * 目标实体（表名）
     * 关系的"多"方或"从"方
     * 示例：posts（一个帖子属于一个用户）
     */
    private String toEntity;

    /**
     * 关系类型
     * ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY
     */
    private RelationType type;

    /**
     * 外键字段名
     * 位于目标实体（toEntity）中的外键字段
     * 示例：author_id（posts表中指向users表的外键）
     *
     * 对于MANY_TO_MANY关系：
     * - 此字段指向中间表中指向fromEntity的外键
     * - 例如：post_tags表中的post_id字段
     */
    private String foreignKeyField;

    /**
     * 中间表名称（仅用于MANY_TO_MANY关系）
     * 示例：post_tags（连接posts和tags）
     */
    private String junctionTable;

    /**
     * 中间表中指向目标实体的外键字段（仅用于MANY_TO_MANY关系）
     * 示例：tag_id（post_tags表中指向tags表的外键）
     */
    private String targetForeignKeyField;

    /**
     * 关系类型枚举
     */
    public enum RelationType {
        /**
         * 一对一关系（1:1）
         *
         * <p>特征：</p>
         * <ul>
         *   <li>每个A记录对应唯一的B记录</li>
         *   <li>B表的外键字段通常有UNIQUE约束</li>
         * </ul>
         *
         * <p>常见场景：</p>
         * <ul>
         *   <li>User和UserProfile（用户和用户资料）</li>
         *   <li>Order和OrderPayment（订单和支付信息）</li>
         * </ul>
         *
         * <p>数据库设计：</p>
         * <pre>
         * users (id, email, ...)
         * user_profiles (id, user_id UNIQUE, bio, avatar, ...)
         * </pre>
         */
        ONE_TO_ONE,

        /**
         * 一对多关系（1:N）
         *
         * <p>特征：</p>
         * <ul>
         *   <li>每个A记录可以对应多个B记录</li>
         *   <li>每个B记录只能对应一个A记录</li>
         *   <li>B表有指向A表的外键</li>
         * </ul>
         *
         * <p>常见场景：</p>
         * <ul>
         *   <li>User和Post（一个用户有多个帖子）</li>
         *   <li>Category和Product（一个分类有多个产品）</li>
         *   <li>Order和OrderItem（一个订单有多个订单项）</li>
         * </ul>
         *
         * <p>数据库设计：</p>
         * <pre>
         * users (id, email, ...)
         * posts (id, author_id, title, content, ...)
         *   FOREIGN KEY (author_id) REFERENCES users(id)
         * </pre>
         */
        ONE_TO_MANY,

        /**
         * 多对多关系（N:M）
         *
         * <p>特征：</p>
         * <ul>
         *   <li>每个A记录可以对应多个B记录</li>
         *   <li>每个B记录也可以对应多个A记录</li>
         *   <li>需要中间表（Junction Table）来存储关系</li>
         * </ul>
         *
         * <p>常见场景：</p>
         * <ul>
         *   <li>Post和Tag（一个帖子有多个标签，一个标签有多个帖子）</li>
         *   <li>Student和Course（一个学生选多门课，一门课有多个学生）</li>
         *   <li>User和Role（一个用户有多个角色，一个角色有多个用户）</li>
         * </ul>
         *
         * <p>数据库设计：</p>
         * <pre>
         * posts (id, title, content, ...)
         * tags (id, name, ...)
         * post_tags (post_id, tag_id)
         *   PRIMARY KEY (post_id, tag_id)
         *   FOREIGN KEY (post_id) REFERENCES posts(id)
         *   FOREIGN KEY (tag_id) REFERENCES tags(id)
         * </pre>
         */
        MANY_TO_MANY;

        /**
         * 判断是否为一对一关系
         */
        public boolean isOneToOne() {
            return this == ONE_TO_ONE;
        }

        /**
         * 判断是否为一对多关系
         */
        public boolean isOneToMany() {
            return this == ONE_TO_MANY;
        }

        /**
         * 判断是否为多对多关系
         */
        public boolean isManyToMany() {
            return this == MANY_TO_MANY;
        }
    }

    /**
     * 判断是否为一对一关系
     */
    public boolean isOneToOne() {
        return type == RelationType.ONE_TO_ONE;
    }

    /**
     * 判断是否为一对多关系
     */
    public boolean isOneToMany() {
        return type == RelationType.ONE_TO_MANY;
    }

    /**
     * 判断是否为多对多关系
     */
    public boolean isManyToMany() {
        return type == RelationType.MANY_TO_MANY;
    }

    /**
     * 生成外键约束SQL片段
     *
     * <p>用于生成目标表的外键约束定义</p>
     *
     * <p>示例输出：</p>
     * <pre>
     * FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
     * </pre>
     *
     * @param onDelete 删除策略（CASCADE, SET NULL, RESTRICT）
     * @return 外键约束SQL片段
     */
    public String toForeignKeyConstraintSQL(String onDelete) {
        if (isManyToMany()) {
            // 多对多关系需要生成中间表的两个外键约束
            return String.format(
                    "FOREIGN KEY (%s) REFERENCES %s(id) ON DELETE %s,\n" +
                    "  FOREIGN KEY (%s) REFERENCES %s(id) ON DELETE %s",
                    foreignKeyField, fromEntity, onDelete,
                    targetForeignKeyField, toEntity, onDelete
            );
        } else {
            // 一对一或一对多关系
            return String.format(
                    "FOREIGN KEY (%s) REFERENCES %s(id) ON DELETE %s",
                    foreignKeyField, fromEntity, onDelete != null ? onDelete : "RESTRICT"
            );
        }
    }

    /**
     * 生成中间表的CREATE TABLE语句（仅用于MANY_TO_MANY关系）
     *
     * <p>示例输出：</p>
     * <pre>
     * CREATE TABLE post_tags (
     *   post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
     *   tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
     *   created_at TIMESTAMPTZ DEFAULT NOW(),
     *   PRIMARY KEY (post_id, tag_id)
     * );
     *
     * CREATE INDEX post_tags_post_id_idx ON post_tags(post_id);
     * CREATE INDEX post_tags_tag_id_idx ON post_tags(tag_id);
     * </pre>
     *
     * @return CREATE TABLE SQL语句
     */
    public String generateJunctionTableSQL() {
        if (!isManyToMany()) {
            throw new UnsupportedOperationException("仅多对多关系需要生成中间表");
        }

        if (junctionTable == null || junctionTable.isEmpty()) {
            // 自动生成中间表名：table1_table2
            junctionTable = fromEntity + "_" + toEntity;
        }

        if (targetForeignKeyField == null || targetForeignKeyField.isEmpty()) {
            // 自动推断目标外键字段名：去掉's'后缀 + '_id'
            String singularToEntity = toEntity.endsWith("s") ?
                    toEntity.substring(0, toEntity.length() - 1) : toEntity;
            targetForeignKeyField = singularToEntity + "_id";
        }

        StringBuilder sql = new StringBuilder();

        // CREATE TABLE
        sql.append("-- ==========================================\n");
        sql.append("-- Junction Table: ").append(junctionTable).append("\n");
        sql.append("-- ==========================================\n");
        sql.append("CREATE TABLE ").append(junctionTable).append(" (\n");

        // 第一个外键字段
        sql.append("  ").append(foreignKeyField)
           .append(" UUID NOT NULL REFERENCES ")
           .append(fromEntity).append("(id) ON DELETE CASCADE,\n");

        // 第二个外键字段
        sql.append("  ").append(targetForeignKeyField)
           .append(" UUID NOT NULL REFERENCES ")
           .append(toEntity).append("(id) ON DELETE CASCADE,\n");

        // 创建时间
        sql.append("  created_at TIMESTAMPTZ DEFAULT NOW(),\n");

        // 复合主键
        sql.append("  PRIMARY KEY (")
           .append(foreignKeyField).append(", ")
           .append(targetForeignKeyField).append(")\n");

        sql.append(");\n\n");

        // 索引（优化查询性能）
        sql.append("CREATE INDEX ").append(junctionTable).append("_")
           .append(foreignKeyField.replace("_id", "")).append("_idx")
           .append(" ON ").append(junctionTable).append("(")
           .append(foreignKeyField).append(");\n");

        sql.append("CREATE INDEX ").append(junctionTable).append("_")
           .append(targetForeignKeyField.replace("_id", "")).append("_idx")
           .append(" ON ").append(junctionTable).append("(")
           .append(targetForeignKeyField).append(");\n");

        return sql.toString();
    }

    /**
     * 获取关系描述（用于日志和文档）
     *
     * @return 关系描述字符串
     */
    public String getDescription() {
        String typeDesc = switch (type) {
            case ONE_TO_ONE -> "1:1";
            case ONE_TO_MANY -> "1:N";
            case MANY_TO_MANY -> "N:M";
        };

        if (isManyToMany()) {
            return String.format("%s (%s) %s [通过%s]",
                    fromEntity, typeDesc, toEntity, junctionTable != null ? junctionTable : "未命名中间表");
        } else {
            return String.format("%s (%s) %s [外键: %s]",
                    fromEntity, typeDesc, toEntity, foreignKeyField);
        }
    }
}
