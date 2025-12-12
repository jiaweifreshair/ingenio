package com.ingenio.backend.codegen.generator;

import com.ingenio.backend.codegen.model.DTOTemplateModel.DTOType;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import com.ingenio.backend.codegen.template.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * DTOGenerator单元测试
 *
 * <p>测试DTO代码生成的各种场景</p>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.3: DTO代码生成模板测试
 */
class DTOGeneratorTest {

    private DTOGenerator dtoGenerator;
    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new TemplateEngine();
        dtoGenerator = new DTOGenerator(templateEngine);
    }

    /**
     * 测试：生成基本CreateDTO（UUID主键+基本字段）
     */
    @Test
    void shouldGenerateBasicCreateDTO() {
        // Given: 准备最小Entity定义
        Field idField = Field.builder()
                .name("id")
                .type(FieldType.UUID)
                .primaryKey(true)
                .nullable(false)
                .description("用户ID")
                .build();

        Field emailField = Field.builder()
                .name("email")
                .type(FieldType.VARCHAR)
                .length(255)
                .unique(true)
                .nullable(false)
                .description("用户邮箱")
                .build();

        Field nameField = Field.builder()
                .name("name")
                .type(FieldType.VARCHAR)
                .length(100)
                .nullable(false)
                .description("用户名称")
                .build();

        Entity entity = Entity.builder()
                .name("users")
                .description("用户表")
                .fields(List.of(idField, emailField, nameField))
                .timestamps(true)
                .build();

        // When: 生成CreateDTO代码
        String generatedCode = dtoGenerator.generate(entity, DTOType.CREATE);

        // Then: 验证生成的代码
        assertThat(generatedCode).isNotNull();

        // 验证：包名和类名
        assertThat(generatedCode).contains("package com.ingenio.backend.dto;");
        assertThat(generatedCode).contains("public class UserCreateDTO");

        // 验证：Lombok注解
        assertThat(generatedCode).contains("@Data");

        // 验证：不包含id字段（CreateDTO排除主键）
        assertThat(generatedCode).doesNotContain("private java.util.UUID id;");
        assertThat(generatedCode).doesNotContain("private UUID id;");

        // 验证：包含业务字段
        assertThat(generatedCode).contains("private String email;");
        assertThat(generatedCode).contains("private String name;");

        // 验证：Bean Validation注解
        assertThat(generatedCode).contains("@NotBlank(message = \"用户邮箱不能为空\")");
        assertThat(generatedCode).contains("@Email(message = \"邮箱格式不正确\")");
        assertThat(generatedCode).contains("@Size(max = 255");

        // 验证：不包含时间戳字段（CreateDTO排除created_at, updated_at）
        assertThat(generatedCode).doesNotContain("createdAt");
        assertThat(generatedCode).doesNotContain("updatedAt");

        // 验证：导入语句
        assertThat(generatedCode).contains("import jakarta.validation.constraints.NotBlank;");
        assertThat(generatedCode).contains("import jakarta.validation.constraints.Email;");
    }

    /**
     * 测试：生成基本UpdateDTO（包含id，其他字段可选）
     */
    @Test
    void shouldGenerateBasicUpdateDTO() {
        // Given: 准备Entity定义
        Field idField = Field.builder()
                .name("id")
                .type(FieldType.UUID)
                .primaryKey(true)
                .nullable(false)
                .description("文章ID")
                .build();

        Field titleField = Field.builder()
                .name("title")
                .type(FieldType.VARCHAR)
                .length(200)
                .nullable(false)
                .description("文章标题")
                .build();

        Field contentField = Field.builder()
                .name("content")
                .type(FieldType.TEXT)
                .nullable(false)
                .description("文章内容")
                .build();

        Entity entity = Entity.builder()
                .name("posts")
                .description("文章表")
                .fields(List.of(idField, titleField, contentField))
                .timestamps(true)
                .build();

        // When: 生成UpdateDTO代码
        String generatedCode = dtoGenerator.generate(entity, DTOType.UPDATE);

        // Then: 验证生成的代码
        assertThat(generatedCode).contains("public class PostUpdateDTO");

        // 验证：包含id字段（UpdateDTO保留主键）
        assertThat(generatedCode).contains("private java.util.UUID id;");

        // 验证：id字段有@NotNull验证
        assertThat(generatedCode).contains("@NotNull(message = \"文章ID不能为空\")");

        // 验证：其他字段没有@NotNull/@NotBlank（UpdateDTO字段可选）
        assertThat(generatedCode).contains("private String title;");
        assertThat(generatedCode).contains("private String content;");
        // title和content之前不应有@NotBlank注解
        assertThat(generatedCode).doesNotContain("@NotBlank(message = \"文章标题不能为空\")");

        // 验证：不包含时间戳字段
        assertThat(generatedCode).doesNotContain("createdAt");
        assertThat(generatedCode).doesNotContain("updatedAt");

        // 验证：包含字段可选说明
        assertThat(generatedCode).contains("除id外，其他字段均为可选");
    }

    /**
     * 测试：生成基本ResponseDTO（包含所有字段，无验证注解）
     */
    @Test
    void shouldGenerateBasicResponseDTO() {
        // Given: 准备Entity定义
        Field idField = Field.builder()
                .name("id")
                .type(FieldType.UUID)
                .primaryKey(true)
                .nullable(false)
                .description("产品ID")
                .build();

        Field nameField = Field.builder()
                .name("name")
                .type(FieldType.VARCHAR)
                .length(100)
                .nullable(false)
                .description("产品名称")
                .build();

        Field priceField = Field.builder()
                .name("price")
                .type(FieldType.NUMERIC)
                .precision(10)
                .scale(2)
                .nullable(false)
                .description("产品价格")
                .build();

        Entity entity = Entity.builder()
                .name("products")
                .description("产品表")
                .fields(List.of(idField, nameField, priceField))
                .timestamps(true)
                .build();

        // When: 生成ResponseDTO代码
        String generatedCode = dtoGenerator.generate(entity, DTOType.RESPONSE);

        // Then: 验证生成的代码
        assertThat(generatedCode).contains("public class ProductResponseDTO");

        // 验证：包含所有字段（包括id和时间戳）
        assertThat(generatedCode).contains("private java.util.UUID id;");
        assertThat(generatedCode).contains("private String name;");
        assertThat(generatedCode).contains("private BigDecimal price;");
        assertThat(generatedCode).contains("private OffsetDateTime createdAt;");
        assertThat(generatedCode).contains("private OffsetDateTime updatedAt;");

        // 验证：不包含验证注解（ResponseDTO不需要验证）
        assertThat(generatedCode).doesNotContain("@NotNull");
        assertThat(generatedCode).doesNotContain("@NotBlank");
        assertThat(generatedCode).doesNotContain("@Size");
        assertThat(generatedCode).doesNotContain("@Email");

        // 验证：不包含Bean Validation相关import
        assertThat(generatedCode).doesNotContain("import jakarta.validation");

        // 验证：导入必要的类型
        assertThat(generatedCode).contains("import java.math.BigDecimal;");
        assertThat(generatedCode).contains("import java.time.OffsetDateTime;");
    }

    /**
     * 测试：生成包含软删除字段的DTO（所有类型都应排除deleted_at）
     */
    @Test
    void shouldExcludeDeletedAtInAllDTOTypes() {
        // Given: 启用软删除的Entity
        Field idField = Field.builder()
                .name("id")
                .type(FieldType.UUID)
                .primaryKey(true)
                .nullable(false)
                .description("任务ID")
                .build();

        Field titleField = Field.builder()
                .name("title")
                .type(FieldType.VARCHAR)
                .length(200)
                .nullable(false)
                .description("任务标题")
                .build();

        Entity entity = Entity.builder()
                .name("tasks")
                .description("任务表")
                .fields(List.of(idField, titleField))
                .softDelete(true)  // 启用软删除
                .timestamps(true)
                .build();

        // When: 生成三种DTO
        String createDTO = dtoGenerator.generate(entity, DTOType.CREATE);
        String updateDTO = dtoGenerator.generate(entity, DTOType.UPDATE);
        String responseDTO = dtoGenerator.generate(entity, DTOType.RESPONSE);

        // Then: 验证所有DTO都不包含deleted_at字段
        assertThat(createDTO).doesNotContain("deletedAt");
        assertThat(updateDTO).doesNotContain("deletedAt");
        assertThat(responseDTO).doesNotContain("deletedAt");
    }

    /**
     * 测试：生成包含多种字段类型的DTO
     */
    @Test
    void shouldGenerateDTOWithVariousFieldTypes() {
        // Given: 包含多种字段类型的Entity
        Field idField = Field.builder()
                .name("id")
                .type(FieldType.BIGINT)
                .primaryKey(true)
                .nullable(false)
                .description("订单ID")
                .build();

        Field amountField = Field.builder()
                .name("amount")
                .type(FieldType.NUMERIC)
                .precision(10)
                .scale(2)
                .nullable(false)
                .description("订单金额")
                .build();

        Field statusField = Field.builder()
                .name("status")
                .type(FieldType.VARCHAR)
                .length(50)
                .nullable(false)
                .description("订单状态")
                .build();

        Field paidField = Field.builder()
                .name("paid")
                .type(FieldType.BOOLEAN)
                .nullable(false)
                .description("是否已支付")
                .build();

        Field orderDateField = Field.builder()
                .name("order_date")
                .type(FieldType.TIMESTAMPTZ)
                .nullable(false)
                .description("下单时间")
                .build();

        Entity entity = Entity.builder()
                .name("orders")
                .description("订单表")
                .fields(List.of(idField, amountField, statusField, paidField, orderDateField))
                .timestamps(true)
                .build();

        // When: 生成CreateDTO
        String generatedCode = dtoGenerator.generate(entity, DTOType.CREATE);

        // Then: 验证所有字段类型
        assertThat(generatedCode).contains("public class OrderCreateDTO");

        // 验证：NUMERIC → BigDecimal
        assertThat(generatedCode).contains("private BigDecimal amount;");
        assertThat(generatedCode).contains("import java.math.BigDecimal;");
        assertThat(generatedCode).contains("@Digits(integer = 8, fraction = 2");

        // 验证：VARCHAR → String
        assertThat(generatedCode).contains("private String status;");
        assertThat(generatedCode).contains("@Size(max = 50");

        // 验证：BOOLEAN → Boolean
        assertThat(generatedCode).contains("private Boolean paid;");

        // 验证：TIMESTAMPTZ → OffsetDateTime
        assertThat(generatedCode).contains("private OffsetDateTime orderDate;");
        assertThat(generatedCode).contains("import java.time.OffsetDateTime;");
    }

    /**
     * 测试：批量生成单个Entity的所有DTO类型
     */
    @Test
    void shouldGenerateAllDTOTypesForEntity() {
        // Given: 准备Entity
        Entity entity = Entity.builder()
                .name("categories")
                .description("分类表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).nullable(false).description("分类ID").build(),
                        Field.builder().name("name").type(FieldType.VARCHAR).length(100).nullable(false).description("分类名称").build()
                ))
                .build();

        // When: 批量生成
        Map<String, String> result = dtoGenerator.generateAll(entity);

        // Then: 验证结果
        assertThat(result).hasSize(3);
        assertThat(result).containsKeys("CategoryCreateDTO", "CategoryUpdateDTO", "CategoryResponseDTO");
        assertThat(result.get("CategoryCreateDTO")).contains("public class CategoryCreateDTO");
        assertThat(result.get("CategoryUpdateDTO")).contains("public class CategoryUpdateDTO");
        assertThat(result.get("CategoryResponseDTO")).contains("public class CategoryResponseDTO");
    }

    /**
     * 测试：批量生成多个Entity的所有DTO
     */
    @Test
    void shouldGenerateAllDTOsForMultipleEntities() {
        // Given: 准备2个Entity
        Entity usersEntity = Entity.builder()
                .name("users")
                .description("用户表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).nullable(false).description("用户ID").build(),
                        Field.builder().name("email").type(FieldType.VARCHAR).length(255).nullable(false).description("邮箱").build()
                ))
                .build();

        Entity postsEntity = Entity.builder()
                .name("posts")
                .description("文章表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).nullable(false).description("文章ID").build(),
                        Field.builder().name("title").type(FieldType.VARCHAR).length(200).nullable(false).description("标题").build()
                ))
                .build();

        List<Entity> entities = List.of(usersEntity, postsEntity);

        // When: 批量生成
        Map<String, String> result = dtoGenerator.generateAll(entities);

        // Then: 验证结果（2个Entity × 3种DTO = 6个类）
        assertThat(result).hasSize(6);
        assertThat(result).containsKeys(
                "UserCreateDTO", "UserUpdateDTO", "UserResponseDTO",
                "PostCreateDTO", "PostUpdateDTO", "PostResponseDTO"
        );
    }

    /**
     * 测试：根据类型批量生成DTO
     */
    @Test
    void shouldGenerateDTOsByType() {
        // Given: 准备2个Entity
        Entity entity1 = Entity.builder()
                .name("tags")
                .description("标签表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).nullable(false).description("标签ID").build(),
                        Field.builder().name("name").type(FieldType.VARCHAR).length(50).nullable(false).description("标签名称").build()
                ))
                .build();

        Entity entity2 = Entity.builder()
                .name("comments")
                .description("评论表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).nullable(false).description("评论ID").build(),
                        Field.builder().name("content").type(FieldType.TEXT).nullable(false).description("评论内容").build()
                ))
                .build();

        List<Entity> entities = List.of(entity1, entity2);

        // When: 只生成CreateDTO类型
        Map<String, String> result = dtoGenerator.generateByType(entities, DTOType.CREATE);

        // Then: 验证只生成了CreateDTO
        assertThat(result).hasSize(2);
        assertThat(result).containsKeys("TagCreateDTO", "CommentCreateDTO");
        assertThat(result).doesNotContainKeys("TagUpdateDTO", "TagResponseDTO");
    }

    /**
     * 测试：字段名转换（snake_case → camelCase）
     */
    @Test
    void shouldConvertFieldNamesCorrectly() {
        // Given: 包含下划线命名字段的Entity
        Field userIdField = Field.builder()
                .name("user_id")
                .type(FieldType.UUID)
                .nullable(false)
                .description("用户ID")
                .build();

        Field createdByField = Field.builder()
                .name("created_by_admin")
                .type(FieldType.BOOLEAN)
                .nullable(false)
                .description("是否由管理员创建")
                .build();

        Entity entity = Entity.builder()
                .name("audit_logs")
                .description("审计日志表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).nullable(false).description("日志ID").build(),
                        userIdField,
                        createdByField
                ))
                .build();

        // When: 生成CreateDTO
        String generatedCode = dtoGenerator.generate(entity, DTOType.CREATE);

        // Then: 验证字段名转换为驼峰
        assertThat(generatedCode).contains("public class AuditLogCreateDTO");
        assertThat(generatedCode).contains("private java.util.UUID userId;");
        assertThat(generatedCode).contains("private Boolean createdByAdmin;");
    }

    /**
     * 测试：邮箱字段自动添加@Email验证
     */
    @Test
    void shouldAddEmailValidationAutomatically() {
        // Given: 包含email字段的Entity
        Field emailField = Field.builder()
                .name("email")
                .type(FieldType.VARCHAR)
                .length(255)
                .nullable(false)
                .description("邮箱地址")
                .build();

        Field contactEmailField = Field.builder()
                .name("contact_email")
                .type(FieldType.VARCHAR)
                .length(255)
                .nullable(true)
                .description("联系邮箱")
                .build();

        Entity entity = Entity.builder()
                .name("users")
                .description("用户表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).nullable(false).description("用户ID").build(),
                        emailField,
                        contactEmailField
                ))
                .build();

        // When: 生成CreateDTO
        String generatedCode = dtoGenerator.generate(entity, DTOType.CREATE);

        // Then: 验证email字段自动添加@Email验证
        assertThat(generatedCode).contains("@Email(message = \"邮箱格式不正确\")");
        assertThat(generatedCode).contains("private String email;");
        assertThat(generatedCode).contains("private String contactEmail;");
    }
}
