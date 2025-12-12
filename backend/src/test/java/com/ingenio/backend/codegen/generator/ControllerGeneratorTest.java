package com.ingenio.backend.codegen.generator;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import com.ingenio.backend.codegen.template.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ControllerGenerator测试类（V2.0 Phase 3.5）
 *
 * <p>测试Controller代码生成器的完整功能</p>
 *
 * <p>测试覆盖范围：</p>
 * <ul>
 *   <li>单个Controller生成</li>
 *   <li>批量Controller生成</li>
 *   <li>5个标准CRUD端点生成</li>
 *   <li>Spring MVC注解验证</li>
 *   <li>Sa-Token认证注解验证</li>
 *   <li>Swagger/OpenAPI注解验证</li>
 *   <li>依赖注入验证</li>
 *   <li>统一响应格式Result验证</li>
 *   <li>参数校验注解验证</li>
 *   <li>错误处理验证</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 3.5: Controller代码生成测试
 */
class ControllerGeneratorTest {

    private ControllerGenerator controllerGenerator;
    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        // 初始化TemplateEngine（使用默认配置）
        templateEngine = new TemplateEngine();
        controllerGenerator = new ControllerGenerator(templateEngine);
    }

    /**
     * 测试1: Controller基础代码生成
     */
    @Test
    void testGenerate_BasicEntity_ShouldGenerateValidControllerCode() {
        // Given: 一个简单的Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证生成的代码包含关键元素
        assertThat(generatedCode)
                .as("生成的Controller代码应包含类声明")
                .contains("public class UserController");

        assertThat(generatedCode)
                .as("Controller应包含@RestController注解")
                .contains("@RestController");

        assertThat(generatedCode)
                .as("Controller应包含@RequestMapping注解")
                .contains("@RequestMapping(\"/api/v1/users\")");

        assertThat(generatedCode)
                .as("Controller应包含@RequiredArgsConstructor注解")
                .contains("@RequiredArgsConstructor");

        assertThat(generatedCode)
                .as("Controller应包含@Slf4j注解")
                .contains("@Slf4j");

        assertThat(generatedCode)
                .as("Controller应包含@Tag注解")
                .contains("@Tag(name = \"User管理\"");

        assertThat(generatedCode)
                .as("Controller应注入Service依赖")
                .contains("private final IUserService userService");
    }

    /**
     * 测试2: 5个标准CRUD端点生成
     */
    @Test
    void testGenerate_BasicEntity_ShouldGenerateAllCrudEndpoints() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证包含5个标准CRUD端点

        // 1. create端点：POST
        assertThat(generatedCode)
                .as("应包含create端点")
                .contains("@PostMapping")
                .contains("public Result<UserResponseDTO> create(")
                .contains("@Valid @RequestBody UserCreateDTO request");

        // 2. getById端点：GET /{id}
        assertThat(generatedCode)
                .as("应包含getById端点")
                .contains("@GetMapping(\"/{id}\")")
                .contains("public Result<UserResponseDTO> getById(")
                .contains("@PathVariable");

        // 3. update端点：PUT /{id}
        assertThat(generatedCode)
                .as("应包含update端点")
                .contains("@PutMapping(\"/{id}\")")
                .contains("public Result<UserResponseDTO> update(")
                .contains("@Valid @RequestBody UserUpdateDTO request");

        // 4. delete端点：DELETE /{id}
        assertThat(generatedCode)
                .as("应包含delete端点")
                .contains("@DeleteMapping(\"/{id}\")")
                .contains("public Result<Void> delete(");

        // 5. list端点：GET
        assertThat(generatedCode)
                .as("应包含list端点")
                .contains("public Result<PageResult<UserResponseDTO>> list(")
                .contains("@RequestParam(required = false) Long current")
                .contains("@RequestParam(required = false) Long size");
    }

    /**
     * 测试3: Sa-Token认证注解
     */
    @Test
    void testGenerate_AllEndpoints_ShouldIncludeSaCheckLoginAnnotation() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证所有端点都有@SaCheckLogin注解
        // 计算@SaCheckLogin出现次数（应该是5个端点都有）
        int saCheckLoginCount = generatedCode.split("@SaCheckLogin").length - 1;
        assertThat(saCheckLoginCount)
                .as("所有5个CRUD端点都应该有@SaCheckLogin注解")
                .isGreaterThanOrEqualTo(5);
    }

    /**
     * 测试4: Swagger/OpenAPI注解
     */
    @Test
    void testGenerate_AllEndpoints_ShouldIncludeSwaggerAnnotations() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证包含Swagger注解
        assertThat(generatedCode)
                .as("应包含@Operation注解")
                .contains("@Operation(summary");

        assertThat(generatedCode)
                .as("应包含@Parameter注解")
                .contains("@Parameter(description");

        // 计算@Operation出现次数（应该是5个端点都有）
        int operationCount = generatedCode.split("@Operation").length - 1;
        assertThat(operationCount)
                .as("所有5个CRUD端点都应该有@Operation注解")
                .isGreaterThanOrEqualTo(5);
    }

    /**
     * 测试5: Bean Validation注解
     */
    @Test
    void testGenerate_CreateAndUpdateEndpoints_ShouldIncludeValidAnnotation() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证create和update端点有@Valid注解
        // @Valid应该出现在create和update的@RequestBody参数前
        int validCount = generatedCode.split("@Valid @RequestBody").length - 1;
        assertThat(validCount)
                .as("create和update端点应该有@Valid注解（至少2个）")
                .isGreaterThanOrEqualTo(2);
    }

    /**
     * 测试6: 统一响应格式Result包装
     */
    @Test
    void testGenerate_AllEndpoints_ShouldReturnResultWrapper() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证所有端点都返回Result包装
        assertThat(generatedCode)
                .as("create端点应返回Result<ResponseDTO>")
                .contains("public Result<UserResponseDTO> create(");

        assertThat(generatedCode)
                .as("getById端点应返回Result<ResponseDTO>")
                .contains("public Result<UserResponseDTO> getById(");

        assertThat(generatedCode)
                .as("update端点应返回Result<ResponseDTO>")
                .contains("public Result<UserResponseDTO> update(");

        assertThat(generatedCode)
                .as("delete端点应返回Result<Void>")
                .contains("public Result<Void> delete(");

        assertThat(generatedCode)
                .as("list端点应返回Result<PageResult<>>")
                .contains("public Result<PageResult<UserResponseDTO>> list(");
    }

    /**
     * 测试7: Service方法调用
     */
    @Test
    void testGenerate_AllEndpoints_ShouldCallServiceMethods() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证Controller调用Service方法
        assertThat(generatedCode)
                .as("create端点应调用Service.create()")
                .contains("userService.create(request)");

        assertThat(generatedCode)
                .as("getById端点应调用Service.getById()")
                .contains("userService.getById(");

        assertThat(generatedCode)
                .as("update端点应调用Service.update()")
                .contains("userService.update(request)");

        assertThat(generatedCode)
                .as("delete端点应调用Service.delete()")
                .contains("userService.delete(");

        assertThat(generatedCode)
                .as("list端点应调用Service.list()")
                .contains("userService.list(");
    }

    /**
     * 测试8: 日志记录
     */
    @Test
    void testGenerate_AllEndpoints_ShouldIncludeLogging() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证所有端点包含日志记录
        assertThat(generatedCode)
                .as("所有端点应包含log.info日志记录")
                .contains("log.info(\"[UserController.");

        // 验证日志数量（5个CRUD端点）
        int logCount = generatedCode.split("log\\.info\\(").length - 1;
        assertThat(logCount)
                .as("应该有5个日志记录（对应5个CRUD方法）")
                .isGreaterThanOrEqualTo(5);
    }

    /**
     * 测试9: 单个Entity生成（Map格式）
     */
    @Test
    void testGenerateAll_SingleEntity_ShouldGenerateControllerAsMap() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 批量生成Controller（单个Entity）
        Map<String, String> result = controllerGenerator.generateAll(entity);

        // Then: 应该生成1个Controller
        assertThat(result)
                .as("单个Entity批量生成应返回1个Controller")
                .hasSize(1);

        assertThat(result)
                .as("应包含UserController")
                .containsKey("UserController");

        // 验证Controller代码
        String controllerCode = result.get("UserController");
        assertThat(controllerCode)
                .as("Controller代码应包含类声明")
                .contains("public class UserController");
    }

    /**
     * 测试10: 批量生成多个Entity的Controller
     */
    @Test
    void testGenerateAll_MultipleEntities_ShouldGenerateAllControllers() {
        // Given: 两个Entity schema
        Entity userEntity = buildSimpleEntity();
        Entity orderEntity = buildOrderEntity();
        List<Entity> entities = List.of(userEntity, orderEntity);

        // When: 批量生成Controller
        Map<String, String> result = controllerGenerator.generateAll(entities);

        // Then: 应该生成2个Controller
        assertThat(result)
                .as("批量生成应返回2个Controller")
                .hasSize(2);

        assertThat(result.keySet())
                .as("应包含所有Controller类名")
                .containsExactlyInAnyOrder("UserController", "OrderController");

        // 验证每个Controller的代码
        assertThat(result.get("UserController"))
                .as("UserController代码应包含类声明")
                .contains("public class UserController");

        assertThat(result.get("OrderController"))
                .as("OrderController代码应包含类声明")
                .contains("public class OrderController");
    }

    /**
     * 测试11: 导入语句验证
     */
    @Test
    void testGenerate_BasicEntity_ShouldIncludeAllNecessaryImports() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证包含所有必要的导入语句
        assertThat(generatedCode)
                .as("应导入DTO类型")
                .contains("import com.ingenio.backend.dto.UserCreateDTO")
                .contains("import com.ingenio.backend.dto.UserUpdateDTO")
                .contains("import com.ingenio.backend.dto.UserResponseDTO");

        assertThat(generatedCode)
                .as("应导入Service接口")
                .contains("import com.ingenio.backend.service.IUserService");

        assertThat(generatedCode)
                .as("应导入统一响应格式")
                .contains("import com.ingenio.backend.common.Result")
                .contains("import com.ingenio.backend.common.PageResult");

        assertThat(generatedCode)
                .as("应导入Spring MVC注解")
                .contains("import org.springframework.web.bind.annotation");

        assertThat(generatedCode)
                .as("应导入Bean Validation")
                .contains("import jakarta.validation.Valid");

        assertThat(generatedCode)
                .as("应导入Lombok注解")
                .contains("import lombok.RequiredArgsConstructor")
                .contains("import lombok.extern.slf4j.Slf4j");

        assertThat(generatedCode)
                .as("应导入Sa-Token认证")
                .contains("import cn.dev33.satoken.annotation.SaCheckLogin");

        assertThat(generatedCode)
                .as("应导入Swagger注解")
                .contains("import io.swagger.v3.oas.annotations");
    }

    /**
     * 测试12: UUID主键类型导入
     */
    @Test
    void testGenerate_UuidPrimaryKey_ShouldImportUuid() {
        // Given: 一个使用UUID主键的Entity
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 应该导入UUID
        assertThat(generatedCode)
                .as("使用UUID主键应导入java.util.UUID")
                .contains("import java.util.UUID");
    }

    /**
     * 测试13: RESTful路径验证
     */
    @Test
    void testGenerate_BasicEntity_ShouldGenerateCorrectRestfulPaths() {
        // Given: users表的Entity
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证RESTful路径
        assertThat(generatedCode)
                .as("基础路径应该是复数形式 /api/v1/users")
                .contains("@RequestMapping(\"/api/v1/users\")");

        assertThat(generatedCode)
                .as("getById和update、delete端点应使用/{id}路径")
                .contains("@GetMapping(\"/{id}\")")
                .contains("@PutMapping(\"/{id}\")")
                .contains("@DeleteMapping(\"/{id}\")");
    }

    /**
     * 测试14: 参数校验 - null Entity
     */
    @Test
    void testGenerate_NullEntity_ShouldThrowException() {
        // When & Then: 传入null应抛出异常
        assertThatThrownBy(() -> controllerGenerator.generate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Entity不能为null");
    }

    /**
     * 测试15: 参数校验 - Entity缺少name
     */
    @Test
    void testGenerate_EntityWithoutName_ShouldThrowException() {
        // Given: 一个没有name的Entity
        Entity entity = Entity.builder()
                .name(null)
                .fields(List.of(
                        Field.builder()
                                .name("id")
                                .type(FieldType.UUID)
                                .primaryKey(true)
                                .build()
                ))
                .build();

        // When & Then: 应抛出异常
        assertThatThrownBy(() -> controllerGenerator.generate(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Entity name不能为空");
    }

    /**
     * 测试16: 参数校验 - Entity缺少fields
     */
    @Test
    void testGenerate_EntityWithoutFields_ShouldThrowException() {
        // Given: 一个没有fields的Entity
        Entity entity = Entity.builder()
                .name("users")
                .fields(null)
                .build();

        // When & Then: 应抛出异常
        assertThatThrownBy(() -> controllerGenerator.generate(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Entity fields不能为空");
    }

    /**
     * 测试17: 错误处理 - RuntimeException包装
     */
    @Test
    void testGenerate_GetByIdEndpoint_ShouldThrowRuntimeExceptionWhenNotFound() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: getById端点应包含orElseThrow错误处理
        assertThat(generatedCode)
                .as("getById端点应包含orElseThrow错误处理")
                .contains(".orElseThrow(() -> new RuntimeException(\"User不存在");
    }

    /**
     * 测试18: PageResult转换验证
     */
    @Test
    void testGenerate_ListEndpoint_ShouldConvertToPageResult() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: list端点应包含PageResult转换逻辑
        assertThat(generatedCode)
                .as("list端点应调用Service.list()并转换为PageResult")
                .contains("IPage<UserResponseDTO> page = userService.list(")
                .contains("PageResult<UserResponseDTO> pageResult = PageResult.<UserResponseDTO>builder()")
                .contains(".records(page.getRecords())")
                .contains(".total(page.getTotal())")
                .contains(".current(page.getCurrent())")
                .contains(".size(page.getSize())")
                .contains(".pages(page.getPages())")
                .contains(".build()");
    }

    /**
     * 测试19: 分页参数默认值
     */
    @Test
    void testGenerate_ListEndpoint_ShouldProvideDefaultPaginationValues() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: list端点应提供分页默认值
        assertThat(generatedCode)
                .as("list端点应提供分页默认值（current默认1，size默认10）")
                .contains("current != null ? current : 1L")
                .contains("size != null ? size : 10L");
    }

    /**
     * 测试20: 代码格式和JavaDoc注释
     */
    @Test
    void testGenerate_BasicEntity_ShouldGenerateWellFormattedCode() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Controller
        String generatedCode = controllerGenerator.generate(entity);

        // Then: 验证代码格式
        assertThat(generatedCode)
                .as("应包含JavaDoc注释")
                .contains("/**")
                .contains(" * ");

        assertThat(generatedCode)
                .as("应包含package声明")
                .contains("package com.ingenio.backend.controller");

        assertThat(generatedCode)
                .as("应包含@author注释")
                .contains("@author");

        assertThat(generatedCode)
                .as("应包含@since注释")
                .contains("@since");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建简单的User实体
     */
    private Entity buildSimpleEntity() {
        return Entity.builder()
                .name("users")
                .description("用户表")
                .fields(List.of(
                        Field.builder()
                                .name("id")
                                .type(FieldType.UUID)
                                .primaryKey(true)
                                .nullable(false)
                                .description("主键ID")
                                .build(),
                        Field.builder()
                                .name("username")
                                .type(FieldType.VARCHAR)
                                .length(50)
                                .unique(true)
                                .nullable(false)
                                .description("用户名")
                                .build(),
                        Field.builder()
                                .name("email")
                                .type(FieldType.VARCHAR)
                                .length(100)
                                .unique(true)
                                .nullable(false)
                                .description("邮箱")
                                .build()
                ))
                .timestamps(true)
                .softDelete(false)
                .build();
    }

    /**
     * 构建Order实体
     */
    private Entity buildOrderEntity() {
        return Entity.builder()
                .name("orders")
                .description("订单表")
                .fields(List.of(
                        Field.builder()
                                .name("id")
                                .type(FieldType.UUID)
                                .primaryKey(true)
                                .nullable(false)
                                .description("主键ID")
                                .build(),
                        Field.builder()
                                .name("order_number")
                                .type(FieldType.VARCHAR)
                                .length(50)
                                .unique(true)
                                .nullable(false)
                                .description("订单号")
                                .build(),
                        Field.builder()
                                .name("user_id")
                                .type(FieldType.UUID)
                                .nullable(false)
                                .description("用户ID")
                                .build()
                ))
                .timestamps(true)
                .softDelete(false)
                .build();
    }
}
