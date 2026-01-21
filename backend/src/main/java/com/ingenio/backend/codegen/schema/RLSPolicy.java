package com.ingenio.backend.codegen.schema;

import lombok.Builder;
import lombok.Data;

/**
 * RLS（Row Level Security）策略定义
 *
 * <p>
 * PostgreSQL行级安全策略，Supabase核心安全机制
 * </p>
 *
 * <p>
 * RLS策略控制用户对表行的访问权限，实现细粒度的数据隔离
 * </p>
 *
 * <p>
 * 策略组成：
 * </p>
 * <ul>
 * <li>operation: 操作类型（SELECT/INSERT/UPDATE/DELETE/ALL）</li>
 * <li>using: USING表达式（行可见性条件）</li>
 * <li>withCheck: WITH CHECK表达式（INSERT/UPDATE数据验证）</li>
 * </ul>
 *
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>{@code
 * // 用户只能查看自己的数据
 * RLSPolicy selectOwnData = RLSPolicy.builder()
 *         .name("users_select_own_data")
 *         .operation("SELECT")
 *         .using("auth.uid() = id")
 *         .build();
 *
 * // 用户只能更新自己的数据
 * RLSPolicy updateOwnData = RLSPolicy.builder()
 *         .name("users_update_own_data")
 *         .operation("UPDATE")
 *         .using("auth.uid() = id")
 *         .withCheck("auth.uid() = id")
 *         .build();
 *
 * // 所有人可以查看已发布的文章
 * RLSPolicy viewPublishedPosts = RLSPolicy.builder()
 *         .name("posts_view_published")
 *         .operation("SELECT")
 *         .using("status = 'published' OR author_id = auth.uid()")
 *         .build();
 *
 * // 作者可以管理自己的文章
 * RLSPolicy manageOwnPosts = RLSPolicy.builder()
 *         .name("posts_manage_own")
 *         .operation("ALL")
 *         .using("author_id = auth.uid()")
 *         .build();
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 2: 数据库Schema生成器
 */
@Data
@Builder
public class RLSPolicy {

    /**
     * 策略名称
     * 命名规范：{表名}_{操作}_{描述}
     * 示例：users_select_own_data, posts_view_published
     */
    private String name;

    /**
     * 操作类型
     * 可选值：SELECT, INSERT, UPDATE, DELETE, ALL
     * - SELECT: 查询操作
     * - INSERT: 插入操作
     * - UPDATE: 更新操作
     * - DELETE: 删除操作
     * - ALL: 所有操作（等同于分别定义SELECT/INSERT/UPDATE/DELETE）
     */
    private String operation;

    /**
     * USING表达式（行可见性条件）
     *
     * <p>
     * 定义用户能看到哪些行的条件
     * </p>
     *
     * <p>
     * 适用操作：SELECT, UPDATE, DELETE, ALL
     * </p>
     *
     * <p>
     * 常用表达式：
     * </p>
     * <ul>
     * <li>auth.uid() = user_id：用户只能访问自己的数据</li>
     * <li>is_public = true：只能访问公开数据</li>
     * <li>status = 'published'：只能访问已发布数据</li>
     * <li>auth.role() = 'admin'：管理员可以访问所有数据</li>
     * <li>user_id = auth.uid() OR is_public = true：自己的数据或公开数据</li>
     * </ul>
     *
     * <p>
     * Supabase内置函数：
     * </p>
     * <ul>
     * <li>auth.uid()：当前用户的UUID</li>
     * <li>auth.role()：当前用户的角色</li>
     * <li>auth.email()：当前用户的邮箱</li>
     * </ul>
     */
    private String using;

    /**
     * WITH CHECK表达式（数据验证条件）
     *
     * <p>
     * 定义用户能插入/更新什么样的数据
     * </p>
     *
     * <p>
     * 适用操作：INSERT, UPDATE
     * </p>
     *
     * <p>
     * 常用表达式：
     * </p>
     * <ul>
     * <li>auth.uid() = user_id：只能插入/更新自己的数据</li>
     * <li>status IN ('draft', 'published')：只能设置有效状态</li>
     * <li>price > 0：价格必须大于0</li>
     * <li>created_at <= NOW()：创建时间不能是未来</li>
     * </ul>
     *
     * <p>
     * 注意：
     * </p>
     * <ul>
     * <li>如果未指定WITH CHECK，默认使用USING表达式</li>
     * <li>INSERT操作：只检查WITH CHECK</li>
     * <li>UPDATE操作：先检查USING（是否可见），再检查WITH CHECK（是否可修改）</li>
     * </ul>
     */
    private String withCheck;

    // Manual Boilerplate for Lombok Failure
    public RLSPolicy() {
    }

    public RLSPolicy(String name, String operation, String using, String withCheck) {
        this.name = name;
        this.operation = operation;
        this.using = using;
        this.withCheck = withCheck;
    }

    public static RLSPolicyBuilder builder() {
        return new RLSPolicyBuilder();
    }

    /**
     * 生成PostgreSQL CREATE POLICY语句
     *
     * <p>
     * 示例输出：
     * </p>
     * 
     * <pre>
     * CREATE POLICY "users_select_own_data"
     *   ON users FOR SELECT
     *   USING (auth.uid() = id);
     *
     * CREATE POLICY "posts_update_own"
     *   ON posts FOR UPDATE
     *   USING (author_id = auth.uid())
     *   WITH CHECK (author_id = auth.uid());
     * </pre>
     *
     * @param tableName 表名
     * @return CREATE POLICY SQL语句
     */
    public String toCreatePolicySQL(String tableName) {
        StringBuilder sql = new StringBuilder();

        // CREATE POLICY "策略名"
        sql.append("CREATE POLICY \"").append(name).append("\"\n");

        // ON 表名
        sql.append("  ON ").append(tableName);

        // FOR 操作类型
        sql.append(" FOR ").append(operation.toUpperCase());

        // USING表达式（适用于SELECT/UPDATE/DELETE/ALL）
        if (using != null && !using.isEmpty()) {
            sql.append("\n  USING (").append(using).append(")");
        }

        // WITH CHECK表达式（适用于INSERT/UPDATE）
        if (withCheck != null && !withCheck.isEmpty()) {
            sql.append("\n  WITH CHECK (").append(withCheck).append(")");
        }

        sql.append(";");

        return sql.toString();
    }

    /**
     * 判断策略是否适用于SELECT操作
     *
     * @return true如果适用于SELECT
     */
    public boolean appliesToSelect() {
        return "SELECT".equalsIgnoreCase(operation) || "ALL".equalsIgnoreCase(operation);
    }

    /**
     * 判断策略是否适用于INSERT操作
     *
     * @return true如果适用于INSERT
     */
    public boolean appliesToInsert() {
        return "INSERT".equalsIgnoreCase(operation) || "ALL".equalsIgnoreCase(operation);
    }

    /**
     * 判断策略是否适用于UPDATE操作
     *
     * @return true如果适用于UPDATE
     */
    public boolean appliesToUpdate() {
        return "UPDATE".equalsIgnoreCase(operation) || "ALL".equalsIgnoreCase(operation);
    }

    /**
     * 判断策略是否适用于DELETE操作
     *
     * @return true如果适用于DELETE
     */
    public boolean appliesToDelete() {
        return "DELETE".equalsIgnoreCase(operation) || "ALL".equalsIgnoreCase(operation);
    }

    /**
     * 判断策略是否需要WITH CHECK表达式
     *
     * @return true如果操作类型为INSERT或UPDATE
     */
    public boolean requiresWithCheck() {
        return appliesToInsert() || appliesToUpdate();
    }

    /**
     * 预定义策略模板：用户只能访问自己的数据
     *
     * @param tableName    表名
     * @param userIdColumn 用户ID字段名（默认：user_id）
     * @return RLSPolicy实例
     */
    public static RLSPolicy selectOwnDataPolicy(String tableName, String userIdColumn) {
        return RLSPolicy.builder()
                .name(tableName + "_select_own_data")
                .operation("SELECT")
                .using("auth.uid() = " + userIdColumn)
                .build();
    }

    /**
     * 预定义策略模板：用户只能插入自己的数据
     *
     * @param tableName    表名
     * @param userIdColumn 用户ID字段名（默认：user_id）
     * @return RLSPolicy实例
     */
    public static RLSPolicy insertOwnDataPolicy(String tableName, String userIdColumn) {
        return RLSPolicy.builder()
                .name(tableName + "_insert_own_data")
                .operation("INSERT")
                .withCheck("auth.uid() = " + userIdColumn)
                .build();
    }

    /**
     * 预定义策略模板：用户只能更新自己的数据
     *
     * @param tableName    表名
     * @param userIdColumn 用户ID字段名（默认：user_id）
     * @return RLSPolicy实例
     */
    public static RLSPolicy updateOwnDataPolicy(String tableName, String userIdColumn) {
        return RLSPolicy.builder()
                .name(tableName + "_update_own_data")
                .operation("UPDATE")
                .using("auth.uid() = " + userIdColumn)
                .withCheck("auth.uid() = " + userIdColumn)
                .build();
    }

    /**
     * 预定义策略模板：用户只能删除自己的数据
     *
     * @param tableName    表名
     * @param userIdColumn 用户ID字段名（默认：user_id）
     * @return RLSPolicy实例
     */
    public static RLSPolicy deleteOwnDataPolicy(String tableName, String userIdColumn) {
        return RLSPolicy.builder()
                .name(tableName + "_delete_own_data")
                .operation("DELETE")
                .using("auth.uid() = " + userIdColumn)
                .build();
    }

    /**
     * 预定义策略模板：所有人可以查看公开数据
     *
     * @param tableName    表名
     * @param publicColumn 公开标志字段名（默认：is_public）
     * @return RLSPolicy实例
     */
    public static RLSPolicy viewPublicDataPolicy(String tableName, String publicColumn) {
        return RLSPolicy.builder()
                .name(tableName + "_view_public")
                .operation("SELECT")
                .using(publicColumn + " = true")
                .build();
    }

    /**
     * 预定义策略模板：管理员全权限
     *
     * @param tableName 表名
     * @return RLSPolicy实例
     */
    public static RLSPolicy adminFullAccessPolicy(String tableName) {
        return RLSPolicy.builder()
                .name(tableName + "_admin_full_access")
                .operation("ALL")
                .using("auth.role() = 'admin'")
                .build();
    }

    public String getName() {
        return name;
    }

    public String getOperation() {
        return operation;
    }

    public String getUsing() {
        return using;
    }

    public String getWithCheck() {
        return withCheck;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void setUsing(String using) {
        this.using = using;
    }

    public void setWithCheck(String withCheck) {
        this.withCheck = withCheck;
    }

    public static class RLSPolicyBuilder {
        private String name;
        private String operation;
        private String using;
        private String withCheck;

        RLSPolicyBuilder() {
        }

        public RLSPolicyBuilder name(String name) {
            this.name = name;
            return this;
        }

        public RLSPolicyBuilder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public RLSPolicyBuilder using(String using) {
            this.using = using;
            return this;
        }

        public RLSPolicyBuilder withCheck(String withCheck) {
            this.withCheck = withCheck;
            return this;
        }

        public RLSPolicy build() {
            return new RLSPolicy(name, operation, using, withCheck);
        }
    }
}
