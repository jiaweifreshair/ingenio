package com.ingenio.backend.codegen.model;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DTO模板数据模型（V2.0 Phase 3.3）
 *
 * <p>将Entity schema对象转换为DTO代码生成所需的数据格式</p>
 *
 * <p>支持三种DTO类型：</p>
 * <ul>
 *   <li>CreateDTO：创建请求DTO（排除id、createdAt、updatedAt、deletedAt）</li>
 *   <li>UpdateDTO：更新请求DTO（包含id，其他字段可选）</li>
 *   <li>ResponseDTO：响应DTO（包含所有字段）</li>
 * </ul>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>字段过滤：根据DTO类型过滤不需要的字段</li>
 *   <li>验证注解：为字段添加Bean Validation注解</li>
 *   <li>类型映射：PostgreSQL类型 → Java类型</li>
 *   <li>命名转换：snake_case → camelCase/PascalCase</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Entity entity = // ... from Phase 2
 * DTOTemplateModel createModel = DTOTemplateModel.fromEntity(entity, DTOType.CREATE);
 * DTOTemplateModel updateModel = DTOTemplateModel.fromEntity(entity, DTOType.UPDATE);
 * DTOTemplateModel responseModel = DTOTemplateModel.fromEntity(entity, DTOType.RESPONSE);
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.3: DTO代码生成模板
 */
@Data
@Builder
public class DTOTemplateModel {

    /**
     * DTO类型枚举
     */
    public enum DTOType {
        /**
         * 创建请求DTO（排除id、时间戳、软删除字段）
         */
        CREATE,

        /**
         * 更新请求DTO（包含id，其他字段可选）
         */
        UPDATE,

        /**
         * 响应DTO（包含所有字段）
         */
        RESPONSE
    }

    /**
     * 包名（默认：com.ingenio.backend.dto）
     */
    @Builder.Default
    private String packageName = "com.ingenio.backend.dto";

    /**
     * 类名（驼峰命名，首字母大写）
     * 示例：User + CREATE → UserCreateDTO
     */
    private String className;

    /**
     * 实体类名（不带DTO后缀）
     * 示例：User
     */
    private String entityName;

    /**
     * 类描述
     */
    private String description;

    /**
     * DTO类型
     */
    private DTOType dtoType;

    /**
     * 字段列表（已根据DTO类型过滤）
     */
    private List<DTOFieldModel> fields;

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
     * 从Entity schema对象创建DTO模板模型
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @param dtoType DTO类型
     * @return DTOTemplateModel实例
     */
    public static DTOTemplateModel fromEntity(Entity entity, DTOType dtoType) {
        // 转换表名为Java类名（users → User）
        String entityName = EntityTemplateModel.toJavaClassName(entity.getName());
        String className = buildClassName(entityName, dtoType);

        // 准备字段列表（包含时间戳字段）
        List<Field> allFields = new ArrayList<>(entity.getFields());

        // 如果Entity启用了timestamps，自动添加created_at和updated_at字段
        if (entity.isTimestamps()) {
            Field createdAtField = Field.builder()
                    .name("created_at")
                    .type(FieldType.TIMESTAMPTZ)
                    .nullable(false)
                    .description("创建时间")
                    .build();

            Field updatedAtField = Field.builder()
                    .name("updated_at")
                    .type(FieldType.TIMESTAMPTZ)
                    .nullable(false)
                    .description("更新时间")
                    .build();

            allFields.add(createdAtField);
            allFields.add(updatedAtField);
        }

        // 如果Entity启用了softDelete，自动添加deleted_at字段
        if (entity.isSoftDelete()) {
            Field deletedAtField = Field.builder()
                    .name("deleted_at")
                    .type(FieldType.TIMESTAMPTZ)
                    .nullable(true)
                    .description("删除时间")
                    .build();

            allFields.add(deletedAtField);
        }

        // 过滤字段（根据DTO类型）
        List<DTOFieldModel> fields = filterFieldsByDTOType(allFields, dtoType);

        return DTOTemplateModel.builder()
                .className(className)
                .entityName(entityName)
                .description(buildDescription(entity.getDescription(), dtoType))
                .dtoType(dtoType)
                .fields(fields)
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
        dataModel.put("entityName", entityName);
        dataModel.put("description", description);
        dataModel.put("dtoType", dtoType.name());
        dataModel.put("fields", fields);
        dataModel.put("author", author);
        dataModel.put("date", date);

        // 添加便捷方法标志
        dataModel.put("isCreateDTO", dtoType == DTOType.CREATE);
        dataModel.put("isUpdateDTO", dtoType == DTOType.UPDATE);
        dataModel.put("isResponseDTO", dtoType == DTOType.RESPONSE);

        return dataModel;
    }

    /**
     * 构建DTO类名
     * 示例：User + CREATE → UserCreateDTO
     */
    private static String buildClassName(String entityName, DTOType dtoType) {
        return switch (dtoType) {
            case CREATE -> entityName + "CreateDTO";
            case UPDATE -> entityName + "UpdateDTO";
            case RESPONSE -> entityName + "ResponseDTO";
        };
    }

    /**
     * 构建DTO类描述
     */
    private static String buildDescription(String entityDescription, DTOType dtoType) {
        String baseDesc = entityDescription != null ? entityDescription : "";
        return switch (dtoType) {
            case CREATE -> baseDesc + " - 创建请求DTO";
            case UPDATE -> baseDesc + " - 更新请求DTO";
            case RESPONSE -> baseDesc + " - 响应DTO";
        };
    }

    /**
     * 根据DTO类型过滤字段
     *
     * @param fields 原始字段列表
     * @param dtoType DTO类型
     * @return 过滤后的字段列表
     */
    private static List<DTOFieldModel> filterFieldsByDTOType(List<Field> fields, DTOType dtoType) {
        return fields.stream()
                .filter(field -> shouldIncludeField(field, dtoType))
                .map(field -> DTOFieldModel.fromField(field, dtoType))
                .collect(Collectors.toList());
    }

    /**
     * 判断字段是否应该包含在DTO中
     *
     * @param field 字段对象
     * @param dtoType DTO类型
     * @return true表示应该包含
     */
    private static boolean shouldIncludeField(Field field, DTOType dtoType) {
        String fieldName = field.getName();

        // 所有DTO类型都排除软删除字段
        if ("deleted_at".equals(fieldName)) {
            return false;
        }

        return switch (dtoType) {
            case CREATE -> {
                // CreateDTO排除：id、created_at、updated_at
                yield !field.isPrimaryKey()
                        && !"created_at".equals(fieldName)
                        && !"updated_at".equals(fieldName);
            }
            case UPDATE -> {
                // UpdateDTO排除：created_at、updated_at（保留id）
                yield !"created_at".equals(fieldName)
                        && !"updated_at".equals(fieldName);
            }
            case RESPONSE -> {
                // ResponseDTO包含所有字段
                yield true;
            }
        };
    }

    /**
     * DTO字段数据模型
     *
     * <p>封装单个DTO字段的所有代码生成信息</p>
     */
    @Data
    @Builder
    public static class DTOFieldModel {
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
         * 是否必填（用于生成@NotNull/@NotBlank注解）
         */
        private boolean required;

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
         * 验证注解列表（Bean Validation）
         */
        private List<String> validationAnnotations;

        /**
         * 从Field schema创建DTOFieldModel
         */
        public static DTOFieldModel fromField(Field field, DTOType dtoType) {
            String javaType = mapPostgresToJavaType(field.getType());
            // UpdateDTO: 只有主键字段required=true，其他字段required=false(可选)
            // CreateDTO: 非nullable字段required=true
            // ResponseDTO: 所有字段required=false(无需验证)
            boolean required = switch (dtoType) {
                case UPDATE -> field.isPrimaryKey() && !field.isNullable();
                case CREATE -> !field.isNullable();
                case RESPONSE -> false;
            };

            return DTOFieldModel.builder()
                    .name(toJavaFieldName(field.getName()))
                    .columnName(field.getName())
                    .javaType(javaType)
                    .description(field.getDescription())
                    .primaryKey(field.isPrimaryKey())
                    .unique(field.isUnique())
                    .nullable(field.isNullable())
                    .required(required)
                    .length(field.getLength())
                    .precision(field.getPrecision())
                    .scale(field.getScale())
                    .validationAnnotations(buildValidationAnnotations(field, javaType, required, dtoType))
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
                case JSON, JSONB -> "String";
                case TEXT_ARRAY -> "String[]";
                case INTEGER_ARRAY -> "Integer[]";
            };
        }

        /**
         * 构建Bean Validation注解列表
         *
         * @param field 字段对象
         * @param javaType Java类型
         * @param required 是否必填
         * @param dtoType DTO类型
         * @return 注解列表
         */
        private static List<String> buildValidationAnnotations(
                Field field, String javaType, boolean required, DTOType dtoType) {
            List<String> annotations = new ArrayList<>();

            // ResponseDTO不需要验证注解
            if (dtoType == DTOType.RESPONSE) {
                return annotations;
            }

            // 必填校验
            if (required) {
                if ("String".equals(javaType)) {
                    annotations.add("@NotBlank(message = \"" + field.getDescription() + "不能为空\")");
                } else {
                    annotations.add("@NotNull(message = \"" + field.getDescription() + "不能为空\")");
                }
            }

            // 字符串长度校验
            if ("String".equals(javaType) && field.getLength() != null) {
                annotations.add("@Size(max = " + field.getLength() +
                        ", message = \"" + field.getDescription() + "长度不能超过" + field.getLength() + "个字符\")");
            }

            // 数值范围校验
            if ("Integer".equals(javaType) || "Long".equals(javaType)) {
                annotations.add("@Min(value = 0, message = \"" + field.getDescription() + "不能为负数\")");
            }

            // BigDecimal精度校验
            if ("java.math.BigDecimal".equals(javaType) && field.getPrecision() != null) {
                int integerDigits = field.getPrecision() - (field.getScale() != null ? field.getScale() : 0);
                annotations.add("@Digits(integer = " + integerDigits +
                        ", fraction = " + (field.getScale() != null ? field.getScale() : 0) +
                        ", message = \"" + field.getDescription() + "格式不正确\")");
            }

            // 邮箱校验（通过字段名推断）
            if ("String".equals(javaType) && field.getName().toLowerCase().contains("email")) {
                annotations.add("@Email(message = \"邮箱格式不正确\")");
            }

            return annotations;
        }
    }

    /**
     * 表名转Java类名（复用EntityTemplateModel的逻辑）
     * 这里使用静态方法引用，避免重复实现
     */
    private static class EntityTemplateModel {
        static String toJavaClassName(String tableName) {
            String[] parts = tableName.split("_");
            StringBuilder className = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (!part.isEmpty()) {
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

        static String singularize(String plural) {
            if (plural.equals("people")) return "person";
            if (plural.equals("children")) return "child";
            if (plural.equals("men")) return "man";
            if (plural.equals("women")) return "woman";
            if (plural.equals("data")) return "datum";

            if (plural.endsWith("ies") && plural.length() > 4) {
                return plural.substring(0, plural.length() - 3) + "y";
            }
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
            if (plural.endsWith("s") && plural.length() > 1) {
                return plural.substring(0, plural.length() - 1);
            }
            return plural;
        }
    }
}
