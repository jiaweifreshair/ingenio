package com.ingenio.backend.codegen.model;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Entity模板数据模型（V2.0 Phase 3.2）
 *
 * <p>将Entity schema对象转换为Freemarker模板所需的数据格式</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>类型映射：PostgreSQL类型 → Java类型</li>
 *   <li>注解生成：JPA、Lombok、Supabase兼容注解</li>
 *   <li>数据转换：Entity → Map<String, Object></li>
 *   <li>代码生成：提供模板所需的所有元数据</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Entity entity = // ... from Phase 2
 * EntityTemplateModel model = EntityTemplateModel.fromEntity(entity);
 * String code = templateEngine.render("Entity.ftl", model.toMap());
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.2: Entity代码生成模板
 */
@Data
@Builder
public class EntityTemplateModel {

    /**
     * 包名（默认：com.ingenio.backend.entity）
     */
    @Builder.Default
    private String packageName = "com.ingenio.backend.entity";

    /**
     * 类名（驼峰命名，首字母大写）
     * 示例：users → User, blog_posts → BlogPost
     */
    private String className;

    /**
     * 表名（PostgreSQL规范：小写+下划线）
     */
    private String tableName;

    /**
     * 类描述
     */
    private String description;

    /**
     * 字段列表（已转换为FieldModel）
     */
    private List<FieldModel> fields;

    /**
     * 是否启用软删除
     */
    @Builder.Default
    private boolean softDelete = false;

    /**
     * 是否启用时间戳（created_at, updated_at）
     */
    @Builder.Default
    private boolean timestamps = true;

    /**
     * 作者
     */
    @Builder.Default
    private String author = "CodeGenerator V2.0";

    /**
     * 生成日期
     */
    @Builder.Default
    private String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

    /**
     * 从Entity schema对象创建TemplateModel
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @return EntityTemplateModel实例
     */
    public static EntityTemplateModel fromEntity(Entity entity) {
        // 转换表名为Java类名（users → User, blog_posts → BlogPost）
        String className = toJavaClassName(entity.getName());

        // 转换字段列表
        List<FieldModel> fields = entity.getFields().stream()
                .map(FieldModel::fromField)
                .collect(Collectors.toList());

        return EntityTemplateModel.builder()
                .className(className)
                .tableName(entity.getName())
                .description(entity.getDescription())
                .fields(fields)
                .softDelete(entity.isSoftDelete())
                .timestamps(entity.isTimestamps())
                .build();
    }

    /**
     * 转换为Map格式（Freemarker模板数据模型）
     *
     * @return Map<String, Object>
     */
    public Map<String, Object> toMap() {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("packageName", packageName);
        dataModel.put("className", className);
        dataModel.put("tableName", tableName);
        dataModel.put("description", description);
        dataModel.put("fields", fields);
        dataModel.put("softDelete", softDelete);
        dataModel.put("timestamps", timestamps);
        dataModel.put("author", author);
        dataModel.put("date", date);

        // 添加便捷方法标志
        dataModel.put("hasPrimaryKey", hasPrimaryKey());
        dataModel.put("hasForeignKeys", hasForeignKeys());
        dataModel.put("hasUniqueConstraints", hasUniqueConstraints());

        return dataModel;
    }

    /**
     * 检查是否有主键字段
     */
    private boolean hasPrimaryKey() {
        return fields.stream().anyMatch(FieldModel::isPrimaryKey);
    }

    /**
     * 检查是否有外键字段
     */
    private boolean hasForeignKeys() {
        return fields.stream().anyMatch(f -> f.getForeignKey() != null);
    }

    /**
     * 检查是否有唯一约束字段
     */
    private boolean hasUniqueConstraints() {
        return fields.stream().anyMatch(FieldModel::isUnique);
    }

    /**
     * 表名转Java类名（snake_case → PascalCase）
     * 同时处理复数形式转单数
     * 示例：users → User, blog_posts → BlogPost, categories → Category
     */
    private static String toJavaClassName(String tableName) {
        String[] parts = tableName.split("_");
        StringBuilder className = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                // 对最后一个部分处理复数转单数
                if (i == parts.length - 1) {
                    part = singularize(part);
                }
                className.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    className.append(part.substring(1));
                }
            }
        }
        return className.toString();
    }

    /**
     * 简单的复数转单数规则
     * 适用于常见的英语复数形式
     */
    private static String singularize(String plural) {
        // 特殊情况（不规则复数）
        if (plural.equals("people")) return "person";
        if (plural.equals("children")) return "child";
        if (plural.equals("men")) return "man";
        if (plural.equals("women")) return "woman";
        if (plural.equals("data")) return "datum";  // 虽然data常作单数使用

        // -ies → -y (categories → category, stories → story)
        if (plural.endsWith("ies") && plural.length() > 4) {
            return plural.substring(0, plural.length() - 3) + "y";
        }

        // -es → -e 或 -s (boxes → box, classes → class, addresses → address)
        if (plural.endsWith("ses") && plural.length() > 4) {
            return plural.substring(0, plural.length() - 2);
        }
        if (plural.endsWith("xes") && plural.length() > 4) {
            return plural.substring(0, plural.length() - 2);
        }
        if (plural.endsWith("ches") && plural.length() > 5) {
            return plural.substring(0, plural.length() - 2);
        }
        if (plural.endsWith("shes") && plural.length() > 5) {
            return plural.substring(0, plural.length() - 2);
        }

        // -s → "" (users → user, posts → post, products → product)
        if (plural.endsWith("s") && plural.length() > 1) {
            return plural.substring(0, plural.length() - 1);
        }

        // 已经是单数形式，直接返回
        return plural;
    }

    /**
     * 字段数据模型
     *
     * <p>封装单个字段的所有代码生成信息</p>
     */
    @Data
    @Builder
    public static class FieldModel {
        /**
         * 字段名（Java命名：驼峰）
         */
        private String name;

        /**
         * 原始字段名（数据库命名：下划线）
         */
        private String columnName;

        /**
         * Java类型（如：String, UUID, OffsetDateTime）
         */
        private String javaType;

        /**
         * PostgreSQL类型（如：VARCHAR(255), UUID, TIMESTAMPTZ）
         */
        private String postgresType;

        /**
         * 字段描述
         */
        private String description;

        /**
         * 是否主键
         */
        private boolean primaryKey;

        /**
         * 是否唯一约束
         */
        private boolean unique;

        /**
         * 是否允许NULL
         */
        private boolean nullable;

        /**
         * 默认值（SQL表达式）
         */
        private String defaultValue;

        /**
         * CHECK约束（SQL表达式）
         */
        private String checkConstraint;

        /**
         * 外键引用（格式：表名.字段名）
         */
        private String foreignKey;

        /**
         * 外键删除策略
         */
        private String onDelete;

        /**
         * 字段长度（仅VARCHAR）
         */
        private Integer length;

        /**
         * 数值精度（仅NUMERIC）
         */
        private Integer precision;

        /**
         * 数值小数位（仅NUMERIC）
         */
        private Integer scale;

        /**
         * 从Field schema创建FieldModel
         */
        public static FieldModel fromField(Field field) {
            return FieldModel.builder()
                    .name(toJavaFieldName(field.getName()))
                    .columnName(field.getName())
                    .javaType(mapPostgresToJavaType(field.getType()))
                    .postgresType(buildPostgresType(field))
                    .description(field.getDescription())
                    .primaryKey(field.isPrimaryKey())
                    .unique(field.isUnique())
                    .nullable(field.isNullable())
                    .defaultValue(field.getDefaultValue())
                    .checkConstraint(field.getCheckConstraint())
                    .foreignKey(field.getForeignKey())
                    .onDelete(field.getOnDelete())
                    .length(field.getLength())
                    .precision(field.getPrecision())
                    .scale(field.getScale())
                    .build();
        }

        /**
         * 数据库字段名转Java字段名（snake_case → camelCase）
         */
        private static String toJavaFieldName(String columnName) {
            String[] parts = columnName.split("_");
            StringBuilder fieldName = new StringBuilder(parts[0]);
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i];
                if (!part.isEmpty()) {
                    fieldName.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        fieldName.append(part.substring(1));
                    }
                }
            }
            return fieldName.toString();
        }

        /**
         * PostgreSQL类型映射到Java类型
         */
        private static String mapPostgresToJavaType(FieldType type) {
            return switch (type) {
                case UUID -> "java.util.UUID";
                case VARCHAR, TEXT -> "String";
                case INTEGER -> "Integer";
                case BIGINT -> "Long";
                case NUMERIC -> "java.math.BigDecimal";
                case REAL -> "Float";
                case BOOLEAN -> "Boolean";
                case TIMESTAMPTZ -> "java.time.OffsetDateTime";
                case DATE -> "java.time.LocalDate";
                case TIME -> "java.time.LocalTime";
                case JSON, JSONB -> "String";  // 或使用Map<String, Object>
                case TEXT_ARRAY -> "String[]";
                case INTEGER_ARRAY -> "Integer[]";
            };
        }

        /**
         * 构建完整的PostgreSQL类型字符串（包含长度、精度等）
         */
        private static String buildPostgresType(Field field) {
            String baseType = field.getType().toPostgreSQLType();

            // VARCHAR需要长度
            if (field.getType() == FieldType.VARCHAR && field.getLength() != null) {
                return baseType + "(" + field.getLength() + ")";
            }

            // NUMERIC需要精度和小数位
            if (field.getType() == FieldType.NUMERIC &&
                    field.getPrecision() != null && field.getScale() != null) {
                return baseType + "(" + field.getPrecision() + ", " + field.getScale() + ")";
            }

            return baseType;
        }
    }
}
