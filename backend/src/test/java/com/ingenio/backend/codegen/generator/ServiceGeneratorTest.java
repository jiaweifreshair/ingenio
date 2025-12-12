package com.ingenio.backend.codegen.generator;

import com.ingenio.backend.codegen.model.ServiceTemplateModel.ServiceType;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import com.ingenio.backend.codegen.template.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ServiceGenerator测试类（V2.0 Phase 3.4）
 *
 * <p>测试Service代码生成器的完整功能</p>
 *
 * <p>测试覆盖范围：</p>
 * <ul>
 *   <li>Service接口生成</li>
 *   <li>ServiceImpl实现类生成</li>
 *   <li>标准CRUD方法生成</li>
 *   <li>事务注解验证</li>
 *   <li>依赖注入验证</li>
 *   <li>批量生成功能</li>
 *   <li>软删除支持</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.4: Service代码生成测试
 */
class ServiceGeneratorTest {

    private ServiceGenerator serviceGenerator;
    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        // 初始化TemplateEngine（使用默认配置）
        templateEngine = new TemplateEngine();
        serviceGenerator = new ServiceGenerator(templateEngine);
    }

    /**
     * 测试1: Service接口基础代码生成
     */
    @Test
    void testGenerateInterface_BasicEntity_ShouldGenerateValidInterfaceCode() {
        // Given: 一个简单的Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成Service接口
        String generatedCode = serviceGenerator.generateInterface(entity);

        // Then: 验证生成的代码包含关键元素
        assertThat(generatedCode)
                .as("生成的Service接口代码应包含接口声明")
                .contains("public interface IUserService");

        assertThat(generatedCode)
                .as("Service接口应包含create方法签名")
                .contains("UserResponseDTO create(UserCreateDTO createDTO)");

        assertThat(generatedCode)
                .as("Service接口应包含update方法签名")
                .contains("UserResponseDTO update(UserUpdateDTO updateDTO)");

        assertThat(generatedCode)
                .as("Service接口应包含delete方法签名")
                .contains("void delete(java.util.UUID id)");

        assertThat(generatedCode)
                .as("Service接口应包含getById方法签名")
                .contains("Optional<UserResponseDTO> getById(java.util.UUID id)");

        assertThat(generatedCode)
                .as("Service接口应包含list方法签名")
                .contains("IPage<UserResponseDTO> list(Integer page, Integer size)");

        assertThat(generatedCode)
                .as("Service接口应导入必要的类型")
                .contains("import com.ingenio.backend.dto.UserCreateDTO")
                .contains("import com.ingenio.backend.dto.UserUpdateDTO")
                .contains("import com.ingenio.backend.dto.UserResponseDTO")
                .contains("import com.baomidou.mybatisplus.core.metadata.IPage")
                .contains("import java.util.Optional")
                .contains("import java.util.UUID");
    }

    /**
     * 测试2: ServiceImpl实现类基础代码生成
     */
    @Test
    void testGenerateImplementation_BasicEntity_ShouldGenerateValidImplementationCode() {
        // Given: 一个简单的Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成ServiceImpl实现类
        String generatedCode = serviceGenerator.generateImplementation(entity);

        // Then: 验证生成的代码包含关键元素
        assertThat(generatedCode)
                .as("生成的ServiceImpl应包含类声明")
                .contains("public class UserServiceImpl implements IUserService");

        assertThat(generatedCode)
                .as("ServiceImpl应包含@Service注解")
                .contains("@Service");

        assertThat(generatedCode)
                .as("ServiceImpl应包含@Slf4j注解")
                .contains("@Slf4j");

        assertThat(generatedCode)
                .as("ServiceImpl应包含@RequiredArgsConstructor注解")
                .contains("@RequiredArgsConstructor");

        assertThat(generatedCode)
                .as("ServiceImpl应注入Mapper依赖")
                .contains("private final UserMapper userMapper");
    }

    /**
     * 测试3: Service方法包含@Transactional注解
     */
    @Test
    void testGenerateImplementation_CRUDMethods_ShouldIncludeTransactionalAnnotation() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成ServiceImpl实现类
        String generatedCode = serviceGenerator.generateImplementation(entity);

        // Then: 验证create、update、delete方法有@Transactional注解
        assertThat(generatedCode)
                .as("create方法应包含@Transactional注解")
                .contains("@Transactional(rollbackFor = Exception.class)");

        // 验证方法数量（create、update、delete有事务，getById、list无事务）
        int transactionalCount = generatedCode.split("@Transactional").length - 1;
        assertThat(transactionalCount)
                .as("应该有3个方法使用@Transactional注解（create/update/delete）")
                .isGreaterThanOrEqualTo(3)
                .as("不应超过5个@Transactional注解")
                .isLessThanOrEqualTo(5);
    }

    /**
     * 测试4: Service方法包含日志记录
     */
    @Test
    void testGenerateImplementation_AllMethods_ShouldIncludeLogging() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成ServiceImpl实现类
        String generatedCode = serviceGenerator.generateImplementation(entity);

        // Then: 验证所有方法包含日志记录
        assertThat(generatedCode)
                .as("所有方法应包含log.info日志记录")
                .contains("log.info(\"[UserServiceImpl]");

        // 验证日志数量（5个CRUD方法）
        int logCount = generatedCode.split("log\\.info\\(").length - 1;
        assertThat(logCount)
                .as("应该有5个日志记录（对应5个CRUD方法）")
                .isGreaterThanOrEqualTo(5);
    }

    /**
     * 测试5: 批量生成单个Entity的Service
     */
    @Test
    void testGenerateAll_SingleEntity_ShouldGenerateBothInterfaceAndImplementation() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 批量生成Service
        Map<String, String> result = serviceGenerator.generateAll(entity);

        // Then: 应该生成接口和实现类两个文件
        assertThat(result)
                .as("批量生成应返回2个Service类")
                .hasSize(2);

        assertThat(result)
                .as("应包含Service接口")
                .containsKey("IUserService");

        assertThat(result)
                .as("应包含ServiceImpl实现类")
                .containsKey("UserServiceImpl");

        // 验证接口代码
        String interfaceCode = result.get("IUserService");
        assertThat(interfaceCode)
                .as("接口代码应包含interface关键字")
                .contains("public interface IUserService");

        // 验证实现类代码
        String implCode = result.get("UserServiceImpl");
        assertThat(implCode)
                .as("实现类代码应包含class和implements")
                .contains("public class UserServiceImpl implements IUserService");
    }

    /**
     * 测试6: 批量生成多个Entity的Service
     */
    @Test
    void testGenerateAll_MultipleEntities_ShouldGenerateAllServices() {
        // Given: 两个Entity schema
        Entity userEntity = buildSimpleEntity();
        Entity orderEntity = buildOrderEntity();
        List<Entity> entities = List.of(userEntity, orderEntity);

        // When: 批量生成Service
        Map<String, String> result = serviceGenerator.generateAll(entities);

        // Then: 应该生成4个Service类（2个实体 × 2个类型）
        assertThat(result)
                .as("批量生成应返回4个Service类")
                .hasSize(4);

        assertThat(result.keySet())
                .as("应包含所有Service类名")
                .containsExactlyInAnyOrder(
                        "IUserService", "UserServiceImpl",
                        "IOrderService", "OrderServiceImpl"
                );
    }

    /**
     * 测试7: 根据类型生成Service（仅接口）
     */
    @Test
    void testGenerateByType_InterfaceOnly_ShouldGenerateOnlyInterfaces() {
        // Given: 两个Entity schema
        Entity userEntity = buildSimpleEntity();
        Entity orderEntity = buildOrderEntity();
        List<Entity> entities = List.of(userEntity, orderEntity);

        // When: 仅生成Service接口
        Map<String, String> result = serviceGenerator.generateByType(entities, ServiceType.INTERFACE);

        // Then: 应该只生成2个接口
        assertThat(result)
                .as("仅接口生成应返回2个Service接口")
                .hasSize(2);

        assertThat(result.keySet())
                .as("应只包含接口类名")
                .containsExactlyInAnyOrder("IUserService", "IOrderService");

        assertThat(result.keySet())
                .as("不应包含实现类")
                .doesNotContain("UserServiceImpl", "OrderServiceImpl");
    }

    /**
     * 测试8: 根据类型生成Service（仅实现类）
     */
    @Test
    void testGenerateByType_ImplementationOnly_ShouldGenerateOnlyImplementations() {
        // Given: 两个Entity schema
        Entity userEntity = buildSimpleEntity();
        Entity orderEntity = buildOrderEntity();
        List<Entity> entities = List.of(userEntity, orderEntity);

        // When: 仅生成ServiceImpl实现类
        Map<String, String> result = serviceGenerator.generateByType(entities, ServiceType.IMPLEMENTATION);

        // Then: 应该只生成2个实现类
        assertThat(result)
                .as("仅实现类生成应返回2个ServiceImpl")
                .hasSize(2);

        assertThat(result.keySet())
                .as("应只包含实现类名")
                .containsExactlyInAnyOrder("UserServiceImpl", "OrderServiceImpl");

        assertThat(result.keySet())
                .as("不应包含接口")
                .doesNotContain("IUserService", "IOrderService");
    }

    /**
     * 测试9: DTO转换方法生成
     */
    @Test
    void testGenerateImplementation_ShouldIncludeDTOConversionMethods() {
        // Given: 一个Entity schema
        Entity entity = buildSimpleEntity();

        // When: 生成ServiceImpl实现类
        String generatedCode = serviceGenerator.generateImplementation(entity);

        // Then: 应包含DTO转换方法
        assertThat(generatedCode)
                .as("应包含convertToEntity方法")
                .contains("private UserEntity convertToEntity(UserCreateDTO createDTO)");

        assertThat(generatedCode)
                .as("应包含updateEntityFields方法")
                .contains("private void updateEntityFields(UserEntity entity, UserUpdateDTO updateDTO)");

        assertThat(generatedCode)
                .as("应包含convertToResponseDTO方法")
                .contains("private UserResponseDTO convertToResponseDTO(UserEntity entity)");
    }

    /**
     * 测试10: 软删除支持
     */
    @Test
    void testGenerateImplementation_WithSoftDelete_ShouldGenerateCorrectDeleteMethod() {
        // Given: 一个启用软删除的Entity
        Entity entity = Entity.builder()
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
                                .build()
                ))
                .timestamps(true)
                .softDelete(true) // 启用软删除
                .build();

        // When: 生成ServiceImpl实现类
        String generatedCode = serviceGenerator.generateImplementation(entity);

        // Then: 应包含软删除相关注释
        assertThat(generatedCode)
                .as("应包含软删除说明")
                .contains("（软删除）");
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
