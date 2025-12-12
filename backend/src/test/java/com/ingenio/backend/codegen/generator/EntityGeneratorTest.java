package com.ingenio.backend.codegen.generator;

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
 * EntityGenerator单元测试
 *
 * <p>测试Entity代码生成的各种场景</p>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.2: Entity代码生成模板测试
 */
class EntityGeneratorTest {

    private EntityGenerator entityGenerator;
    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new TemplateEngine();
        entityGenerator = new EntityGenerator(templateEngine);
    }

    /**
     * 测试：生成基本Entity类（UUID主键+基本字段）
     */
    @Test
    void shouldGenerateBasicEntityWithUUIDPrimaryKey() {
        // Given: 准备最小Entity定义
        Field idField = Field.builder()
                .name("id")
                .type(FieldType.UUID)
                .primaryKey(true)
                .nullable(false)
                .defaultValue("uuid_generate_v4()")
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

        Entity entity = Entity.builder()
                .name("users")
                .description("用户表")
                .fields(List.of(idField, emailField))
                .timestamps(true)  // 启用created_at, updated_at
                .build();

        // When: 生成Entity代码
        String generatedCode = entityGenerator.generate(entity);

        // Then: 验证生成的代码
        assertThat(generatedCode).isNotNull();

        // 验证：包名和类名
        assertThat(generatedCode).contains("package com.ingenio.backend.entity;");
        assertThat(generatedCode).contains("public class User implements Serializable");

        // 验证：JPA注解
        assertThat(generatedCode).contains("@Entity");
        assertThat(generatedCode).contains("@Table(name = \"users\")");

        // 验证：Lombok注解
        assertThat(generatedCode).contains("@Data");
        assertThat(generatedCode).contains("@Builder");
        assertThat(generatedCode).contains("@NoArgsConstructor");
        assertThat(generatedCode).contains("@AllArgsConstructor");

        // 验证：主键字段
        assertThat(generatedCode).contains("@Id");
        assertThat(generatedCode).contains("@GeneratedValue(strategy = GenerationType.UUID)");
        assertThat(generatedCode).contains("private java.util.UUID id;");

        // 验证：普通字段
        assertThat(generatedCode).contains("private String email;");
        assertThat(generatedCode).contains("unique = true");

        // 验证：时间戳字段
        assertThat(generatedCode).contains("private OffsetDateTime createdAt;");
        assertThat(generatedCode).contains("private OffsetDateTime updatedAt;");
        assertThat(generatedCode).contains("@PrePersist");
        assertThat(generatedCode).contains("@PreUpdate");

        // 验证：导入语句
        assertThat(generatedCode).contains("import java.util.UUID;");
        assertThat(generatedCode).contains("import java.time.OffsetDateTime;");
    }

    /**
     * 测试：生成启用软删除的Entity
     */
    @Test
    void shouldGenerateEntityWithSoftDelete() {
        // Given: 启用软删除的Entity
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

        Entity entity = Entity.builder()
                .name("posts")
                .description("文章表")
                .fields(List.of(idField, titleField))
                .softDelete(true)  // 启用软删除
                .timestamps(true)
                .build();

        // When: 生成代码
        String generatedCode = entityGenerator.generate(entity);

        // Then: 验证包含软删除字段
        assertThat(generatedCode).contains("public class Post implements Serializable");
        assertThat(generatedCode).contains("private OffsetDateTime deletedAt;");
        assertThat(generatedCode).contains("软删除标志");
    }

    /**
     * 测试：生成包含外键关系的Entity
     */
    @Test
    void shouldGenerateEntityWithForeignKey() {
        // Given: 包含外键的Entity（comments表引用posts表）
        Field idField = Field.builder()
                .name("id")
                .type(FieldType.UUID)
                .primaryKey(true)
                .nullable(false)
                .description("评论ID")
                .build();

        Field postIdField = Field.builder()
                .name("post_id")
                .type(FieldType.UUID)
                .nullable(false)
                .foreignKey("posts.id")  // 外键引用
                .onDelete("CASCADE")
                .description("文章ID（外键）")
                .build();

        Field contentField = Field.builder()
                .name("content")
                .type(FieldType.TEXT)
                .nullable(false)
                .description("评论内容")
                .build();

        Entity entity = Entity.builder()
                .name("comments")
                .description("评论表")
                .fields(List.of(idField, postIdField, contentField))
                .timestamps(true)
                .build();

        // When: 生成代码
        String generatedCode = entityGenerator.generate(entity);

        // Then: 验证外键字段
        assertThat(generatedCode).contains("public class Comment implements Serializable");
        assertThat(generatedCode).contains("private java.util.UUID postId;");
        assertThat(generatedCode).contains("文章ID（外键）");
        assertThat(generatedCode).contains("外键引用：posts.id");
    }

    /**
     * 测试：生成包含多种字段类型的Entity
     */
    @Test
    void shouldGenerateEntityWithVariousFieldTypes() {
        // Given: 包含多种字段类型的Entity
        Field idField = Field.builder()
                .name("id")
                .type(FieldType.BIGINT)
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

        Field stockField = Field.builder()
                .name("stock")
                .type(FieldType.INTEGER)
                .nullable(false)
                .description("库存数量")
                .build();

        Field activeField = Field.builder()
                .name("active")
                .type(FieldType.BOOLEAN)
                .nullable(false)
                .defaultValue("true")
                .description("是否上架")
                .build();

        Field metadataField = Field.builder()
                .name("metadata")
                .type(FieldType.JSONB)
                .description("产品元数据")
                .build();

        Field tagsField = Field.builder()
                .name("tags")
                .type(FieldType.TEXT_ARRAY)
                .description("产品标签")
                .build();

        Entity entity = Entity.builder()
                .name("products")
                .description("产品表")
                .fields(List.of(idField, nameField, priceField, stockField,
                        activeField, metadataField, tagsField))
                .timestamps(true)
                .build();

        // When: 生成代码
        String generatedCode = entityGenerator.generate(entity);

        // Then: 验证所有字段类型
        assertThat(generatedCode).contains("public class Product implements Serializable");

        // 验证：BIGINT主键
        assertThat(generatedCode).contains("@GeneratedValue(strategy = GenerationType.IDENTITY)");
        assertThat(generatedCode).contains("private Long id;");

        // 验证：VARCHAR
        assertThat(generatedCode).contains("private String name;");

        // 验证：NUMERIC
        assertThat(generatedCode).contains("import java.math.BigDecimal;");
        assertThat(generatedCode).contains("private BigDecimal price;");
        assertThat(generatedCode).contains("precision = 10");
        assertThat(generatedCode).contains("scale = 2");

        // 验证：INTEGER
        assertThat(generatedCode).contains("private Integer stock;");

        // 验证：BOOLEAN
        assertThat(generatedCode).contains("private Boolean active;");

        // 验证：JSONB
        assertThat(generatedCode).contains("private String metadata;");

        // 验证：TEXT[]
        assertThat(generatedCode).contains("private String[] tags;");
    }

    /**
     * 测试：批量生成多个Entity
     */
    @Test
    void shouldGenerateMultipleEntities() {
        // Given: 准备2个Entity
        Entity usersEntity = Entity.builder()
                .name("users")
                .description("用户表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                        Field.builder().name("email").type(FieldType.VARCHAR).length(255).build()
                ))
                .build();

        Entity postsEntity = Entity.builder()
                .name("posts")
                .description("文章表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                        Field.builder().name("title").type(FieldType.VARCHAR).length(200).build()
                ))
                .build();

        List<Entity> entities = List.of(usersEntity, postsEntity);

        // When: 批量生成
        Map<String, String> result = entityGenerator.generateAll(entities);

        // Then: 验证结果
        assertThat(result).hasSize(2);
        assertThat(result).containsKeys("User", "Post");
        assertThat(result.get("User")).contains("public class User");
        assertThat(result.get("Post")).contains("public class Post");
    }

    /**
     * 测试：字段名转换（snake_case → camelCase）
     */
    @Test
    void shouldConvertFieldNamesCorrectly() {
        // Given: 包含下划线命名字段的Entity
        Field createdByField = Field.builder()
                .name("created_by_user_id")  // snake_case
                .type(FieldType.UUID)
                .description("创建用户ID")
                .build();

        Entity entity = Entity.builder()
                .name("audit_logs")
                .description("审计日志表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                        createdByField
                ))
                .build();

        // When: 生成代码
        String generatedCode = entityGenerator.generate(entity);

        // Then: 验证字段名转换为驼峰
        assertThat(generatedCode).contains("public class AuditLog");  // audit_logs → AuditLog
        assertThat(generatedCode).contains("private java.util.UUID createdByUserId;");  // created_by_user_id → createdByUserId
        assertThat(generatedCode).contains("@Column(name = \"created_by_user_id\")");  // 保留原始数据库字段名
    }

    /**
     * 测试：不启用时间戳的Entity
     */
    @Test
    void shouldGenerateEntityWithoutTimestamps() {
        // Given: 不启用时间戳
        Entity entity = Entity.builder()
                .name("config")
                .description("配置表")
                .fields(List.of(
                        Field.builder().name("id").type(FieldType.UUID).primaryKey(true).build(),
                        Field.builder().name("key").type(FieldType.VARCHAR).length(100).build()
                ))
                .timestamps(false)  // 不启用时间戳
                .build();

        // When: 生成代码
        String generatedCode = entityGenerator.generate(entity);

        // Then: 验证不包含时间戳字段
        assertThat(generatedCode).doesNotContain("createdAt");
        assertThat(generatedCode).doesNotContain("updatedAt");
        assertThat(generatedCode).doesNotContain("@PrePersist");
        assertThat(generatedCode).doesNotContain("@PreUpdate");
    }
}
