package com.ingenio.backend.codegen.schema;

/**
 * PostgreSQL字段类型枚举
 *
 * <p>定义Supabase/PostgreSQL支持的核心数据类型</p>
 *
 * <p>类型分类：</p>
 * <ul>
 *   <li>数值类型：INTEGER、BIGINT、NUMERIC、REAL</li>
 *   <li>字符串类型：VARCHAR、TEXT</li>
 *   <li>日期时间类型：DATE、TIME、TIMESTAMPTZ</li>
 *   <li>布尔类型：BOOLEAN</li>
 *   <li>UUID类型：UUID</li>
 *   <li>JSON类型：JSON、JSONB</li>
 *   <li>数组类型：TEXT_ARRAY、INTEGER_ARRAY</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Field emailField = Field.builder()
 *     .name("email")
 *     .type(FieldType.VARCHAR)
 *     .length(255)
 *     .build();
 *
 * Field tagsField = Field.builder()
 *     .name("tags")
 *     .type(FieldType.TEXT_ARRAY)
 *     .build();
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 2: 数据库Schema生成器
 */
public enum FieldType {

    // ==========================================
    // 数值类型（Numeric Types）
    // ==========================================

    /**
     * 整数类型（-2,147,483,648 到 2,147,483,647）
     * 适用场景：计数、年龄、数量等
     */
    INTEGER,

    /**
     * 长整数类型（-9,223,372,036,854,775,808 到 9,223,372,036,854,775,807）
     * 适用场景：大数值ID、时间戳（毫秒）、统计计数
     */
    BIGINT,

    /**
     * 精确数值类型（自定义精度和小数位）
     * 适用场景：金额、价格、精确计算
     * 示例：NUMERIC(10, 2) 表示最多10位数字，其中2位小数
     */
    NUMERIC,

    /**
     * 浮点数类型（单精度，6位小数精度）
     * 适用场景：科学计算、坐标、评分
     */
    REAL,

    // ==========================================
    // 字符串类型（String Types）
    // ==========================================

    /**
     * 变长字符串（需指定长度）
     * 适用场景：邮箱、用户名、短文本
     * 示例：VARCHAR(255)
     */
    VARCHAR,

    /**
     * 文本类型（无长度限制）
     * 适用场景：文章内容、描述、长文本
     */
    TEXT,

    // ==========================================
    // 日期时间类型（Date/Time Types）
    // ==========================================

    /**
     * 日期类型（仅日期，无时间）
     * 适用场景：生日、截止日期
     * 格式：YYYY-MM-DD
     */
    DATE,

    /**
     * 时间类型（仅时间，无日期）
     * 适用场景：营业时间、闹钟时间
     * 格式：HH:MM:SS
     */
    TIME,

    /**
     * 时间戳类型（带时区）
     * 适用场景：创建时间、更新时间、事件发生时间
     * 格式：YYYY-MM-DD HH:MM:SS.ssssss+TZ
     * Supabase推荐：所有时间字段使用TIMESTAMPTZ
     */
    TIMESTAMPTZ,

    // ==========================================
    // 布尔类型（Boolean Type）
    // ==========================================

    /**
     * 布尔值类型（true/false）
     * 适用场景：开关、状态标志、权限标志
     */
    BOOLEAN,

    // ==========================================
    // UUID类型（UUID Type）
    // ==========================================

    /**
     * 通用唯一标识符类型
     * 适用场景：主键、外键、分布式ID
     * Supabase推荐：所有主键使用UUID
     * 默认值示例：uuid_generate_v4()
     */
    UUID,

    // ==========================================
    // JSON类型（JSON Types）
    // ==========================================

    /**
     * JSON数据类型（存储为文本）
     * 适用场景：配置数据、元数据
     * 查询性能：低于JSONB
     */
    JSON,

    /**
     * JSONB数据类型（二进制格式存储）
     * 适用场景：复杂数据结构、动态字段、扩展属性
     * 查询性能：支持索引，查询速度快
     * Supabase推荐：优先使用JSONB而非JSON
     */
    JSONB,

    // ==========================================
    // 数组类型（Array Types）
    // ==========================================

    /**
     * 文本数组类型
     * 适用场景：标签列表、关键词列表、邮箱列表
     * PostgreSQL语法：TEXT[]
     * 示例：['tag1', 'tag2', 'tag3']
     */
    TEXT_ARRAY,

    /**
     * 整数数组类型
     * 适用场景：ID列表、评分列表
     * PostgreSQL语法：INTEGER[]
     * 示例：[1, 2, 3, 4, 5]
     */
    INTEGER_ARRAY;

    /**
     * 获取PostgreSQL DDL中的类型字符串
     *
     * @return PostgreSQL类型字符串
     */
    public String toPostgreSQLType() {
        return switch (this) {
            case INTEGER -> "INTEGER";
            case BIGINT -> "BIGINT";
            case NUMERIC -> "NUMERIC";  // 需要额外指定精度和小数位
            case REAL -> "REAL";
            case VARCHAR -> "VARCHAR";  // 需要额外指定长度
            case TEXT -> "TEXT";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIMESTAMPTZ -> "TIMESTAMPTZ";
            case BOOLEAN -> "BOOLEAN";
            case UUID -> "UUID";
            case JSON -> "JSON";
            case JSONB -> "JSONB";
            case TEXT_ARRAY -> "TEXT[]";
            case INTEGER_ARRAY -> "INTEGER[]";
        };
    }

    /**
     * 判断该类型是否需要指定长度
     *
     * @return true如果需要长度参数（如VARCHAR）
     */
    public boolean requiresLength() {
        return this == VARCHAR;
    }

    /**
     * 判断该类型是否需要指定精度
     *
     * @return true如果需要精度参数（如NUMERIC）
     */
    public boolean requiresPrecision() {
        return this == NUMERIC;
    }

    /**
     * 判断该类型是否为数值类型
     *
     * @return true如果是数值类型
     */
    public boolean isNumeric() {
        return this == INTEGER || this == BIGINT || this == NUMERIC || this == REAL;
    }

    /**
     * 判断该类型是否为字符串类型
     *
     * @return true如果是字符串类型
     */
    public boolean isString() {
        return this == VARCHAR || this == TEXT;
    }

    /**
     * 判断该类型是否为日期时间类型
     *
     * @return true如果是日期时间类型
     */
    public boolean isDateTime() {
        return this == DATE || this == TIME || this == TIMESTAMPTZ;
    }

    /**
     * 判断该类型是否为数组类型
     *
     * @return true如果是数组类型
     */
    public boolean isArray() {
        return this == TEXT_ARRAY || this == INTEGER_ARRAY;
    }

    /**
     * 获取对应的Java类型（完整类名）
     *
     * <p>映射关系：</p>
     * <ul>
     *   <li>UUID → java.util.UUID</li>
     *   <li>INTEGER → java.lang.Integer</li>
     *   <li>BIGINT → java.lang.Long</li>
     *   <li>NUMERIC → java.math.BigDecimal</li>
     *   <li>REAL → java.lang.Float</li>
     *   <li>VARCHAR, TEXT → java.lang.String</li>
     *   <li>DATE → java.time.LocalDate</li>
     *   <li>TIME → java.time.LocalTime</li>
     *   <li>TIMESTAMPTZ → java.time.OffsetDateTime</li>
     *   <li>BOOLEAN → java.lang.Boolean</li>
     *   <li>JSON, JSONB → java.lang.String</li>
     *   <li>TEXT_ARRAY → java.util.List&lt;String&gt;</li>
     *   <li>INTEGER_ARRAY → java.util.List&lt;Integer&gt;</li>
     * </ul>
     *
     * @return Java类型的完整类名
     */
    public String toJavaType() {
        return switch (this) {
            case UUID -> "java.util.UUID";
            case INTEGER -> "java.lang.Integer";
            case BIGINT -> "java.lang.Long";
            case NUMERIC -> "java.math.BigDecimal";
            case REAL -> "java.lang.Float";
            case VARCHAR, TEXT -> "java.lang.String";
            case DATE -> "java.time.LocalDate";
            case TIME -> "java.time.LocalTime";
            case TIMESTAMPTZ -> "java.time.OffsetDateTime";
            case BOOLEAN -> "java.lang.Boolean";
            case JSON, JSONB -> "java.lang.String";
            case TEXT_ARRAY -> "java.util.List<String>";
            case INTEGER_ARRAY -> "java.util.List<Integer>";
        };
    }
}
