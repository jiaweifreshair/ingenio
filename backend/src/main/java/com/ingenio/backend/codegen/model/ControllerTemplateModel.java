package com.ingenio.backend.codegen.model;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.util.NamingConverter;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller模板数据模型（V2.0 Phase 3.5）
 *
 * <p>包含生成Spring REST Controller所需的完整元数据</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>Controller基础信息配置</li>
 *   <li>RESTful端点定义（5个标准CRUD方法）</li>
 *   <li>Spring注解配置（@RestController、@RequestMapping等）</li>
 *   <li>Swagger/OpenAPI文档注解</li>
 *   <li>依赖注入Service配置</li>
 *   <li>统一响应格式Result包装</li>
 *   <li>Bean Validation校验配置</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 从Entity schema生成Controller模型
 * Entity entity = // ... from Phase 2
 * ControllerTemplateModel model = ControllerTemplateModel.fromEntity(entity);
 *
 * // 转换为Freemarker数据模型
 * Map<String, Object> dataModel = model.toMap();
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.5: Controller代码生成数据模型
 */
@Data
@Builder
public class ControllerTemplateModel {

    // ==================== Controller基础信息 ====================

    /**
     * Controller包名
     * 示例：com.ingenio.backend.controller
     */
    private String packageName;

    /**
     * Controller类名
     * 示例：UserController
     */
    private String className;

    /**
     * RESTful API基础路径
     * 示例：/api/v1/users
     */
    private String baseUrl;

    /**
     * 实体名称（首字母大写）
     * 示例：User
     */
    private String entityName;

    /**
     * Controller描述
     * 示例：用户管理Controller
     */
    private String description;

    // ==================== 依赖注入配置 ====================

    /**
     * Service接口名称
     * 示例：IUserService
     */
    private String serviceInterfaceName;

    /**
     * Service字段名（小写开头）
     * 示例：userService
     */
    private String serviceFieldName;

    // ==================== DTO名称配置 ====================

    /**
     * 创建请求DTO名称
     * 示例：UserCreateDTO
     */
    private String createDTOName;

    /**
     * 更新请求DTO名称
     * 示例：UserUpdateDTO
     */
    private String updateDTOName;

    /**
     * 响应DTO名称
     * 示例：UserResponseDTO
     */
    private String responseDTOName;

    /**
     * 响应DTO字段名（小写开头）
     * 示例：userResponse
     */
    private String responseDTOFieldName;

    // ==================== RESTful端点列表 ====================

    /**
     * Controller的所有RESTful端点
     * 包含：create、getById、update、delete、list
     */
    private List<ControllerEndpoint> endpoints;

    // ==================== Swagger/OpenAPI配置 ====================

    /**
     * API标签名称
     * 示例：用户管理
     */
    private String apiTagName;

    /**
     * API标签描述
     * 示例：提供用户CRUD、分页查询等接口
     */
    private String apiTagDescription;

    // ==================== 其他元数据 ====================

    /**
     * 主键类型（完整类名）
     * 示例：java.util.UUID 或 java.lang.Long
     */
    private String primaryKeyType;

    /**
     * 主键字段名
     * 示例：id
     */
    private String primaryKeyFieldName;

    /**
     * 作者
     */
    private String author;

    /**
     * 创建日期
     */
    private String date;

    // ==================== 工厂方法 ====================

    /**
     * 从Entity schema构建ControllerTemplateModel
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @return ControllerTemplateModel实例
     */
    public static ControllerTemplateModel fromEntity(Entity entity) {
        String tableName = entity.getName();
        // 将表名单数化后转PascalCase：users → user → User
        String singularTableName = NamingConverter.toSingular(tableName);
        String entityName = NamingConverter.toPascalCase(singularTableName);

        // 基础路径：/api/v1/实体名复数形式（小写）
        // 先单数化再复数化，确保得到正确的复数形式（避免users → userses）
        String baseUrl = "/api/v1/" + NamingConverter.toPlural(singularTableName).toLowerCase();

        // Service名称
        String serviceInterfaceName = "I" + entityName + "Service";
        String serviceFieldName = NamingConverter.toCamelCase(entityName) + "Service";

        // DTO名称
        String createDTOName = entityName + "CreateDTO";
        String updateDTOName = entityName + "UpdateDTO";
        String responseDTOName = entityName + "ResponseDTO";
        String responseDTOFieldName = NamingConverter.toCamelCase(entityName) + "Response";

        // Swagger API标签
        String apiTagName = entityName + "管理";
        String apiTagDescription = "提供" + entityName + "的CRUD、分页查询等接口";

        // 主键信息（通过遍历fields找到主键字段）
        var primaryKeyField = entity.getFields().stream()
                .filter(com.ingenio.backend.codegen.schema.Field::isPrimaryKey)
                .findFirst()
                .orElse(null);
        String primaryKeyType = primaryKeyField != null
                ? primaryKeyField.getType().toJavaType()
                : "java.util.UUID";
        String primaryKeyFieldName = primaryKeyField != null
                ? primaryKeyField.getName()
                : "id";

        // 构建5个标准CRUD端点
        List<ControllerEndpoint> endpoints = buildStandardCrudEndpoints(
                entityName,
                createDTOName,
                updateDTOName,
                responseDTOName,
                primaryKeyType,
                primaryKeyFieldName
        );

        return ControllerTemplateModel.builder()
                .packageName("com.ingenio.backend.controller")
                .className(entityName + "Controller")
                .baseUrl(baseUrl)
                .entityName(entityName)
                .description(entityName + "管理Controller")
                .serviceInterfaceName(serviceInterfaceName)
                .serviceFieldName(serviceFieldName)
                .createDTOName(createDTOName)
                .updateDTOName(updateDTOName)
                .responseDTOName(responseDTOName)
                .responseDTOFieldName(responseDTOFieldName)
                .endpoints(endpoints)
                .apiTagName(apiTagName)
                .apiTagDescription(apiTagDescription)
                .primaryKeyType(primaryKeyType)
                .primaryKeyFieldName(primaryKeyFieldName)
                .author("Ingenio Code Generator")
                .date(LocalDate.now().toString())
                .build();
    }

    /**
     * 构建5个标准CRUD端点
     *
     * @param entityName 实体名称
     * @param createDTOName 创建请求DTO名称
     * @param updateDTOName 更新请求DTO名称
     * @param responseDTOName 响应DTO名称
     * @param primaryKeyType 主键类型
     * @param primaryKeyFieldName 主键字段名
     * @return 端点列表
     */
    private static List<ControllerEndpoint> buildStandardCrudEndpoints(
            String entityName,
            String createDTOName,
            String updateDTOName,
            String responseDTOName,
            String primaryKeyType,
            String primaryKeyFieldName
    ) {
        List<ControllerEndpoint> endpoints = new ArrayList<>();

        // 1. create端点：POST /api/v1/{entity}
        endpoints.add(ControllerEndpoint.builder()
                .name("create")
                .httpMethod("POST")
                .path("")  // 基础路径
                .description("创建" + entityName)
                .parameters(List.of(
                        new EndpointParameter("request", createDTOName, "创建请求DTO", "body", true, true)
                ))
                .requestBodyType(createDTOName)
                .returnType("Result<" + responseDTOName + ">")
                .responseType(responseDTOName)
                .requireLogin(true)
                .operationSummary("创建" + entityName)
                .operationDescription("创建新的" + entityName + "实体")
                .build());

        // 2. getById端点：GET /api/v1/{entity}/{id}
        String simpleKeyType = primaryKeyType.contains(".")
                ? primaryKeyType.substring(primaryKeyType.lastIndexOf('.') + 1)
                : primaryKeyType;
        endpoints.add(ControllerEndpoint.builder()
                .name("getById")
                .httpMethod("GET")
                .path("/{" + primaryKeyFieldName + "}")
                .description("根据ID查询" + entityName)
                .parameters(List.of(
                        new EndpointParameter(primaryKeyFieldName, simpleKeyType, "主键ID", "path", true, false)
                ))
                .requestBodyType(null)
                .returnType("Result<" + responseDTOName + ">")
                .responseType(responseDTOName)
                .requireLogin(true)
                .operationSummary("查询" + entityName + "详情")
                .operationDescription("根据ID查询" + entityName + "详细信息")
                .build());

        // 3. update端点：PUT /api/v1/{entity}/{id}
        endpoints.add(ControllerEndpoint.builder()
                .name("update")
                .httpMethod("PUT")
                .path("/{" + primaryKeyFieldName + "}")
                .description("更新" + entityName)
                .parameters(List.of(
                        new EndpointParameter(primaryKeyFieldName, simpleKeyType, "主键ID", "path", true, false),
                        new EndpointParameter("request", updateDTOName, "更新请求DTO", "body", true, true)
                ))
                .requestBodyType(updateDTOName)
                .returnType("Result<" + responseDTOName + ">")
                .responseType(responseDTOName)
                .requireLogin(true)
                .operationSummary("更新" + entityName)
                .operationDescription("更新指定ID的" + entityName + "实体")
                .build());

        // 4. delete端点：DELETE /api/v1/{entity}/{id}
        endpoints.add(ControllerEndpoint.builder()
                .name("delete")
                .httpMethod("DELETE")
                .path("/{" + primaryKeyFieldName + "}")
                .description("删除" + entityName)
                .parameters(List.of(
                        new EndpointParameter(primaryKeyFieldName, simpleKeyType, "主键ID", "path", true, false)
                ))
                .requestBodyType(null)
                .returnType("Result<Void>")
                .responseType("Void")
                .requireLogin(true)
                .operationSummary("删除" + entityName)
                .operationDescription("删除指定ID的" + entityName + "实体（软删除）")
                .build());

        // 5. list端点：GET /api/v1/{entity}
        endpoints.add(ControllerEndpoint.builder()
                .name("list")
                .httpMethod("GET")
                .path("")
                .description("分页查询" + entityName + "列表")
                .parameters(List.of(
                        new EndpointParameter("current", "Long", "当前页码", "query", false, false),
                        new EndpointParameter("size", "Long", "每页数量", "query", false, false)
                ))
                .requestBodyType(null)
                .returnType("Result<PageResult<" + responseDTOName + ">>")
                .responseType("PageResult<" + responseDTOName + ">")
                .requireLogin(true)
                .operationSummary("分页查询" + entityName + "列表")
                .operationDescription("分页查询当前用户的" + entityName + "列表")
                .build());

        return endpoints;
    }

    /**
     * 转换为Freemarker数据模型
     *
     * @return Freemarker数据模型Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> dataModel = new HashMap<>();
        dataModel.put("packageName", packageName);
        dataModel.put("className", className);
        dataModel.put("baseUrl", baseUrl);
        dataModel.put("entityName", entityName);
        dataModel.put("description", description);
        dataModel.put("serviceInterfaceName", serviceInterfaceName);
        dataModel.put("serviceFieldName", serviceFieldName);
        dataModel.put("createDTOName", createDTOName);
        dataModel.put("updateDTOName", updateDTOName);
        dataModel.put("responseDTOName", responseDTOName);
        dataModel.put("responseDTOFieldName", responseDTOFieldName);
        dataModel.put("endpoints", endpoints);
        dataModel.put("apiTagName", apiTagName);
        dataModel.put("apiTagDescription", apiTagDescription);
        dataModel.put("primaryKeyType", primaryKeyType);
        dataModel.put("primaryKeyFieldName", primaryKeyFieldName);
        dataModel.put("author", author);
        dataModel.put("date", date);

        // 辅助判断：是否需要导入UUID
        dataModel.put("needsUUID", primaryKeyType != null && primaryKeyType.contains("UUID"));

        return dataModel;
    }

    // ==================== 内部类 ====================

    /**
     * Controller端点模型
     * 表示一个RESTful API端点（如POST /api/v1/users）
     */
    @Data
    @Builder
    public static class ControllerEndpoint {
        /**
         * 方法名
         * 示例：create、getById、update
         */
        private String name;

        /**
         * HTTP方法
         * 取值：GET、POST、PUT、DELETE
         */
        private String httpMethod;

        /**
         * 端点路径（相对于baseUrl）
         * 示例：""（空字符串表示基础路径）、"/{id}"
         */
        private String path;

        /**
         * 端点描述
         * 示例：创建用户
         */
        private String description;

        /**
         * 参数列表
         * 包含路径参数、查询参数、请求体参数
         */
        private List<EndpointParameter> parameters;

        /**
         * 请求体类型（仅POST/PUT有值）
         * 示例：UserCreateDTO、UserUpdateDTO
         */
        private String requestBodyType;

        /**
         * 返回类型（完整类型）
         * 示例：Result<UserResponseDTO>
         */
        private String returnType;

        /**
         * 响应类型（泛型内部类型）
         * 示例：UserResponseDTO、PageResult<UserResponseDTO>
         */
        private String responseType;

        /**
         * 是否需要登录
         * true表示需要@SaCheckLogin注解
         */
        private boolean requireLogin;

        /**
         * Swagger操作摘要
         * 示例：创建用户
         */
        private String operationSummary;

        /**
         * Swagger操作描述
         * 示例：创建新的用户实体
         */
        private String operationDescription;
    }

    /**
     * 端点参数模型
     * 表示一个端点的参数（路径参数、查询参数、请求体）
     */
    @Data
    public static class EndpointParameter {
        /**
         * 参数名
         * 示例：id、request、current
         */
        private String name;

        /**
         * 参数类型（简化类型）
         * 示例：UUID、UserCreateDTO、Long
         */
        private String type;

        /**
         * 参数描述
         * 示例：主键ID、创建请求DTO
         */
        private String description;

        /**
         * 参数位置
         * 取值：path、query、body
         */
        private String location;

        /**
         * 是否必需
         * true表示required，false表示optional
         */
        private boolean required;

        /**
         * 是否需要校验
         * true表示需要@Valid注解
         */
        private boolean needValidation;

        /**
         * 构造函数
         */
        public EndpointParameter(String name, String type, String description,
                                  String location, boolean required, boolean needValidation) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.location = location;
            this.required = required;
            this.needValidation = needValidation;
        }
    }
}
