package com.ingenio.backend.codegen.model;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service模板数据模型（V2.0 Phase 3.4）
 *
 * <p>将Entity schema对象转换为Service代码生成所需的数据格式</p>
 *
 * <p>支持两种Service类型：</p>
 * <ul>
 *   <li>ServiceInterface：Service接口（IXxxService）</li>
 *   <li>ServiceImpl：Service实现类（XxxServiceImpl）</li>
 * </ul>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>方法定义：标准CRUD方法（create、update、delete、getById、list）</li>
 *   <li>依赖注入：Mapper依赖、其他Service依赖</li>
 *   <li>DTO转换：Entity ↔ DTO转换逻辑</li>
 *   <li>事务管理：@Transactional注解配置</li>
 *   <li>分页支持：IPage<ResponseDTO>分页查询</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Entity entity = // ... from Phase 2
 * ServiceTemplateModel interfaceModel = ServiceTemplateModel.forInterface(entity);
 * ServiceTemplateModel implModel = ServiceTemplateModel.forImplementation(entity);
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.4: Service代码生成模板
 */
@Data
@Builder
public class ServiceTemplateModel {

    /**
     * Service类型枚举
     */
    public enum ServiceType {
        /**
         * Service接口（IXxxService）
         */
        INTERFACE,

        /**
         * Service实现类（XxxServiceImpl）
         */
        IMPLEMENTATION
    }

    /**
     * 包名（默认：com.ingenio.backend.service）
     */
    @Builder.Default
    private String packageName = "com.ingenio.backend.service";

    /**
     * 接口类名（以I开头，驼峰命名）
     * 示例：IUserService
     */
    private String interfaceName;

    /**
     * 实现类名（以Impl结尾，驼峰命名）
     * 示例：UserServiceImpl
     */
    private String implementationName;

    /**
     * 实体类名（不带Service后缀）
     * 示例：User
     */
    private String entityName;

    /**
     * Mapper类名
     * 示例：UserMapper
     */
    private String mapperName;

    /**
     * CreateDTO类名
     * 示例：UserCreateDTO
     */
    private String createDTOName;

    /**
     * UpdateDTO类名
     * 示例：UserUpdateDTO
     */
    private String updateDTOName;

    /**
     * ResponseDTO类名
     * 示例：UserResponseDTO
     */
    private String responseDTOName;

    /**
     * 类描述
     */
    private String description;

    /**
     * Service类型
     */
    private ServiceType serviceType;

    /**
     * 方法列表（CRUD方法定义）
     */
    private List<ServiceMethodModel> methods;

    /**
     * 主键字段类型（用于方法参数）
     */
    private String primaryKeyType;

    /**
     * 主键字段名（用于查询条件）
     */
    private String primaryKeyFieldName;

    /**
     * 是否启用软删除
     */
    private boolean softDelete;

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
     * 为Service接口创建模板模型
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @return ServiceTemplateModel实例
     */
    public static ServiceTemplateModel forInterface(Entity entity) {
        String entityName = toJavaClassName(entity.getName());
        String interfaceName = "I" + entityName + "Service";

        // 查找主键字段
        Field primaryKeyField = entity.getFields().stream()
                .filter(Field::isPrimaryKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Entity必须有主键字段: " + entity.getName()));

        String primaryKeyType = mapPostgresToJavaType(primaryKeyField.getType());
        String primaryKeyFieldName = toCamelCase(primaryKeyField.getName());

        // 生成标准CRUD方法
        List<ServiceMethodModel> methods = buildCrudMethods(entityName, primaryKeyType, primaryKeyFieldName, false);

        return ServiceTemplateModel.builder()
                .packageName("com.ingenio.backend.service")
                .interfaceName(interfaceName)
                .entityName(entityName)
                .mapperName(entityName + "Mapper")
                .createDTOName(entityName + "CreateDTO")
                .updateDTOName(entityName + "UpdateDTO")
                .responseDTOName(entityName + "ResponseDTO")
                .description(buildDescription(entity.getDescription(), ServiceType.INTERFACE))
                .serviceType(ServiceType.INTERFACE)
                .methods(methods)
                .primaryKeyType(primaryKeyType)
                .primaryKeyFieldName(primaryKeyFieldName)
                .softDelete(entity.isSoftDelete())
                .build();
    }

    /**
     * 为Service实现类创建模板模型
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @return ServiceTemplateModel实例
     */
    public static ServiceTemplateModel forImplementation(Entity entity) {
        String entityName = toJavaClassName(entity.getName());
        String implementationName = entityName + "ServiceImpl";
        String interfaceName = "I" + entityName + "Service";

        // 查找主键字段
        Field primaryKeyField = entity.getFields().stream()
                .filter(Field::isPrimaryKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Entity必须有主键字段: " + entity.getName()));

        String primaryKeyType = mapPostgresToJavaType(primaryKeyField.getType());
        String primaryKeyFieldName = toCamelCase(primaryKeyField.getName());

        // 生成标准CRUD方法（带实现细节）
        List<ServiceMethodModel> methods = buildCrudMethods(entityName, primaryKeyType, primaryKeyFieldName, true);

        return ServiceTemplateModel.builder()
                .packageName("com.ingenio.backend.service.impl")
                .interfaceName(interfaceName)
                .implementationName(implementationName)
                .entityName(entityName)
                .mapperName(entityName + "Mapper")
                .createDTOName(entityName + "CreateDTO")
                .updateDTOName(entityName + "UpdateDTO")
                .responseDTOName(entityName + "ResponseDTO")
                .description(buildDescription(entity.getDescription(), ServiceType.IMPLEMENTATION))
                .serviceType(ServiceType.IMPLEMENTATION)
                .methods(methods)
                .primaryKeyType(primaryKeyType)
                .primaryKeyFieldName(primaryKeyFieldName)
                .softDelete(entity.isSoftDelete())
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
        dataModel.put("interfaceName", interfaceName);
        dataModel.put("implementationName", implementationName);
        dataModel.put("entityName", entityName);
        dataModel.put("mapperName", mapperName);
        dataModel.put("createDTOName", createDTOName);
        dataModel.put("updateDTOName", updateDTOName);
        dataModel.put("responseDTOName", responseDTOName);
        dataModel.put("description", description);
        dataModel.put("serviceType", serviceType.name());
        dataModel.put("methods", methods);
        dataModel.put("primaryKeyType", primaryKeyType);
        dataModel.put("primaryKeyFieldName", primaryKeyFieldName);
        dataModel.put("softDelete", softDelete);
        dataModel.put("author", author);
        dataModel.put("date", date);

        // 添加便捷方法标志
        dataModel.put("isInterface", serviceType == ServiceType.INTERFACE);
        dataModel.put("isImplementation", serviceType == ServiceType.IMPLEMENTATION);

        return dataModel;
    }

    /**
     * 构建标准CRUD方法列表
     *
     * @param entityName 实体名称
     * @param primaryKeyType 主键类型
     * @param primaryKeyFieldName 主键字段名
     * @param withImplementation 是否包含实现细节
     * @return 方法列表
     */
    private static List<ServiceMethodModel> buildCrudMethods(
            String entityName,
            String primaryKeyType,
            String primaryKeyFieldName,
            boolean withImplementation) {

        List<ServiceMethodModel> methods = new ArrayList<>();

        // 1. create方法
        methods.add(ServiceMethodModel.builder()
                .name("create")
                .returnType(entityName + "ResponseDTO")
                .parameters(List.of(
                        new MethodParameter(entityName + "CreateDTO", "createDTO", "创建请求DTO")
                ))
                .description("创建" + entityName + "实体")
                .transactional(true)
                .withImplementation(withImplementation)
                .implementationSteps(withImplementation ? List.of(
                        "// 1. DTO转Entity",
                        entityName + "Entity entity = convertToEntity(createDTO);",
                        "",
                        "// 2. 保存到数据库",
                        "mapper.insert(entity);",
                        "log.info(\"创建" + entityName + "成功: {}={}\", \"" + primaryKeyFieldName + "\", entity.get" + capitalize(primaryKeyFieldName) + "());",
                        "",
                        "// 3. Entity转ResponseDTO",
                        "return convertToResponseDTO(entity);"
                ) : null)
                .build());

        // 2. update方法
        methods.add(ServiceMethodModel.builder()
                .name("update")
                .returnType(entityName + "ResponseDTO")
                .parameters(List.of(
                        new MethodParameter(entityName + "UpdateDTO", "updateDTO", "更新请求DTO")
                ))
                .description("更新" + entityName + "实体")
                .transactional(true)
                .withImplementation(withImplementation)
                .implementationSteps(withImplementation ? List.of(
                        "// 1. 查询现有实体",
                        entityName + "Entity entity = mapper.selectById(updateDTO.get" + capitalize(primaryKeyFieldName) + "());",
                        "if (entity == null) {",
                        "    throw new RuntimeException(\"" + entityName + "不存在: \" + updateDTO.get" + capitalize(primaryKeyFieldName) + "());",
                        "}",
                        "",
                        "// 2. 更新字段（仅更新非null字段）",
                        "updateEntityFields(entity, updateDTO);",
                        "",
                        "// 3. 保存到数据库",
                        "mapper.updateById(entity);",
                        "log.info(\"更新" + entityName + "成功: {}={}\", \"" + primaryKeyFieldName + "\", entity.get" + capitalize(primaryKeyFieldName) + "());",
                        "",
                        "// 4. Entity转ResponseDTO",
                        "return convertToResponseDTO(entity);"
                ) : null)
                .build());

        // 3. delete方法
        methods.add(ServiceMethodModel.builder()
                .name("delete")
                .returnType("void")
                .parameters(List.of(
                        new MethodParameter(primaryKeyType, primaryKeyFieldName, "主键ID")
                ))
                .description("删除" + entityName + "实体")
                .transactional(true)
                .withImplementation(withImplementation)
                .implementationSteps(withImplementation ? List.of(
                        "// 软删除或物理删除",
                        "int deleted = mapper.deleteById(" + primaryKeyFieldName + ");",
                        "if (deleted == 0) {",
                        "    throw new RuntimeException(\"" + entityName + "不存在或已删除: \" + " + primaryKeyFieldName + ");",
                        "}",
                        "log.info(\"删除" + entityName + "成功: {}={}\", \"" + primaryKeyFieldName + "\", " + primaryKeyFieldName + ");"
                ) : null)
                .build());

        // 4. getById方法
        methods.add(ServiceMethodModel.builder()
                .name("getById")
                .returnType("Optional<" + entityName + "ResponseDTO>")
                .parameters(List.of(
                        new MethodParameter(primaryKeyType, primaryKeyFieldName, "主键ID")
                ))
                .description("根据ID查询" + entityName + "实体")
                .transactional(false)
                .withImplementation(withImplementation)
                .implementationSteps(withImplementation ? List.of(
                        entityName + "Entity entity = mapper.selectById(" + primaryKeyFieldName + ");",
                        "return Optional.ofNullable(entity).map(this::convertToResponseDTO);"
                ) : null)
                .build());

        // 5. list方法（分页查询）
        methods.add(ServiceMethodModel.builder()
                .name("list")
                .returnType("IPage<" + entityName + "ResponseDTO>")
                .parameters(List.of(
                        new MethodParameter("Integer", "page", "页码（从1开始）"),
                        new MethodParameter("Integer", "size", "每页大小")
                ))
                .description("分页查询" + entityName + "列表")
                .transactional(false)
                .withImplementation(withImplementation)
                .implementationSteps(withImplementation ? List.of(
                        "// 1. 构建分页对象",
                        "Page<" + entityName + "Entity> pageRequest = new Page<>(page, size);",
                        "",
                        "// 2. 执行分页查询",
                        "IPage<" + entityName + "Entity> entityPage = mapper.selectPage(pageRequest, null);",
                        "",
                        "// 3. Entity转ResponseDTO",
                        "return entityPage.convert(this::convertToResponseDTO);"
                ) : null)
                .build());

        return methods;
    }

    /**
     * 构建Service类描述
     */
    private static String buildDescription(String entityDescription, ServiceType serviceType) {
        String baseDesc = entityDescription != null ? entityDescription : "";
        return switch (serviceType) {
            case INTERFACE -> baseDesc + " - Service接口";
            case IMPLEMENTATION -> baseDesc + " - Service实现类";
        };
    }

    /**
     * 表名转Java类名（复用EntityTemplateModel的逻辑）
     */
    private static String toJavaClassName(String tableName) {
        String[] parts = tableName.split("_");
        StringBuilder className = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                // 最后一部分单数化
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
     * 单数化（简单规则）
     */
    private static String singularize(String plural) {
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

    /**
     * snake_case转camelCase
     */
    private static String toCamelCase(String snakeCase) {
        String[] parts = snakeCase.split("_");
        StringBuilder camelCase = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                camelCase.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    camelCase.append(part.substring(1));
                }
            }
        }
        return camelCase.toString();
    }

    /**
     * 首字母大写
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * PostgreSQL类型映射到Java类型
     */
    private static String mapPostgresToJavaType(com.ingenio.backend.codegen.schema.FieldType type) {
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
     * Service方法数据模型
     */
    @Data
    @Builder
    public static class ServiceMethodModel {
        /**
         * 方法名
         */
        private String name;

        /**
         * 返回类型
         */
        private String returnType;

        /**
         * 参数列表
         */
        private List<MethodParameter> parameters;

        /**
         * 方法描述
         */
        private String description;

        /**
         * 是否需要事务
         */
        private boolean transactional;

        /**
         * 是否包含实现（Interface为false，ServiceImpl为true）
         */
        private boolean withImplementation;

        /**
         * 实现代码行（仅ServiceImpl需要）
         */
        private List<String> implementationSteps;
    }

    /**
     * 方法参数数据模型
     */
    @Data
    @Builder
    public static class MethodParameter {
        /**
         * 参数类型
         */
        private String type;

        /**
         * 参数名
         */
        private String name;

        /**
         * 参数描述
         */
        private String description;

        public MethodParameter(String type, String name, String description) {
            this.type = type;
            this.name = name;
            this.description = description;
        }
    }
}
