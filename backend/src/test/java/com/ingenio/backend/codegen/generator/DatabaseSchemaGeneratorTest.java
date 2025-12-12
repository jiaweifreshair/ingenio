package com.ingenio.backend.codegen.generator;

import com.ingenio.backend.codegen.analyzer.EntityAnalyzer;
import com.ingenio.backend.codegen.builder.SupabaseSchemaBuilder;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.EntityRelationship;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DatabaseSchemaGenerator集成测试
 *
 * <p>测试数据库Schema生成器的端到端功能</p>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 2.4: 测试和文档
 */
@ExtendWith(MockitoExtension.class)
class DatabaseSchemaGeneratorTest {

    @Mock
    private EntityAnalyzer entityAnalyzer;

    @Mock
    private SupabaseSchemaBuilder schemaBuilder;

    private DatabaseSchemaGenerator generator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        generator = new DatabaseSchemaGenerator(entityAnalyzer, schemaBuilder);
        // 使用反射设置迁移目录为临时目录
        ReflectionTestUtils.setField(generator, "migrationDir", tempDir.toString());
    }

    /**
     * 测试：成功生成完整的数据库Schema
     *
     * <p>验证端到端流程：提取实体 → 推断关系 → 生成SQL → 写入文件</p>
     */
    @Test
    void shouldGenerateCompleteDatabaseSchemaSuccessfully() throws IOException {
        // Given: 准备测试数据
        String userRequirement = "我需要一个博客系统，包括用户、文章、评论功能";

        Entity userEntity = Entity.builder()
                .name("users")
                .description("用户表")
                .fields(Collections.emptyList())
                .build();

        Entity postEntity = Entity.builder()
                .name("posts")
                .description("文章表")
                .fields(Collections.emptyList())
                .build();

        List<Entity> entities = List.of(userEntity, postEntity);

        EntityRelationship relationship = EntityRelationship.builder()
                .fromEntity("users")
                .toEntity("posts")
                .type(EntityRelationship.RelationType.ONE_TO_MANY)
                .foreignKeyField("author_id")
                .build();

        List<EntityRelationship> relationships = List.of(relationship);

        String migrationSQL = "CREATE TABLE users (id UUID PRIMARY KEY);";
        String rollbackSQL = "DROP TABLE IF EXISTS users;";

        // When: Mock依赖行为
        when(entityAnalyzer.extractEntities(userRequirement)).thenReturn(entities);
        when(entityAnalyzer.inferRelationships(entities)).thenReturn(relationships);
        when(schemaBuilder.generateMigrationScript(entities, relationships)).thenReturn(migrationSQL);
        when(schemaBuilder.generateRollbackScript(entities, relationships)).thenReturn(rollbackSQL);

        // Then: 执行生成
        DatabaseSchemaGenerator.DatabaseSchemaResult result = generator.generate(userRequirement);

        // 验证：调用链正确
        verify(entityAnalyzer, times(1)).extractEntities(userRequirement);
        verify(entityAnalyzer, times(1)).inferRelationships(entities);
        verify(schemaBuilder, times(1)).generateMigrationScript(entities, relationships);
        verify(schemaBuilder, times(1)).generateRollbackScript(entities, relationships);

        // 验证：结果对象正确
        assertThat(result).isNotNull();
        assertThat(result.getUserRequirement()).isEqualTo(userRequirement);
        assertThat(result.getEntities()).hasSize(2);
        assertThat(result.getRelationships()).hasSize(1);
        assertThat(result.getMigrationSQL()).isEqualTo(migrationSQL);
        assertThat(result.getRollbackSQL()).isEqualTo(rollbackSQL);
        assertThat(result.getElapsedTimeMs()).isGreaterThan(0);

        // 验证：迁移文件已写入
        assertThat(result.getMigrationFilePath()).contains("create_tables.sql");
        Path migrationFile = Path.of(result.getMigrationFilePath());
        assertThat(migrationFile).exists();
        assertThat(Files.readString(migrationFile)).isEqualTo(migrationSQL);

        // 验证：回滚文件已写入
        assertThat(result.getRollbackFilePath()).contains("rollback.sql");
        Path rollbackFile = Path.of(result.getRollbackFilePath());
        assertThat(rollbackFile).exists();
        assertThat(Files.readString(rollbackFile)).isEqualTo(rollbackSQL);
    }

    /**
     * 测试：当EntityAnalyzer未提取到实体时抛出异常
     */
    @Test
    void shouldThrowExceptionWhenNoEntitiesExtracted() {
        // Given: Mock返回空实体列表
        String userRequirement = "无效需求";
        when(entityAnalyzer.extractEntities(userRequirement)).thenReturn(Collections.emptyList());

        // When & Then: 验证抛出异常
        assertThatThrownBy(() -> generator.generate(userRequirement))
                .isInstanceOf(DatabaseSchemaGenerator.SchemaGenerationException.class)
                .hasMessageContaining("未能从需求中提取到任何实体定义");

        // 验证：不会继续调用后续方法
        verify(entityAnalyzer, times(1)).extractEntities(userRequirement);
        verify(entityAnalyzer, never()).inferRelationships(any());
        verify(schemaBuilder, never()).generateMigrationScript(any(), any());
    }

    /**
     * 测试：当EntityAnalyzer抛出异常时正确传播
     */
    @Test
    void shouldPropagateExceptionFromEntityAnalyzer() {
        // Given: Mock抛出异常
        String userRequirement = "测试需求";
        RuntimeException exception = new RuntimeException("EntityAnalyzer失败");
        when(entityAnalyzer.extractEntities(userRequirement)).thenThrow(exception);

        // When & Then: 验证异常被包装并抛出
        assertThatThrownBy(() -> generator.generate(userRequirement))
                .isInstanceOf(DatabaseSchemaGenerator.SchemaGenerationException.class)
                .hasMessageContaining("Schema生成失败")
                .hasCause(exception);
    }

    /**
     * 测试：DatabaseSchemaResult辅助方法
     */
    @Test
    void shouldProvideCorrectHelperMethodsInResult() {
        // Given: 创建测试结果对象
        Entity entity = Entity.builder()
                .name("test_table")
                .description("测试表")
                .fields(Collections.emptyList())
                .build();

        DatabaseSchemaGenerator.DatabaseSchemaResult result =
                DatabaseSchemaGenerator.DatabaseSchemaResult.builder()
                        .userRequirement("测试")
                        .entities(List.of(entity))
                        .relationships(Collections.emptyList())
                        .migrationSQL("CREATE TABLE test_table (\n  id UUID PRIMARY KEY\n);")
                        .rollbackSQL("DROP TABLE test_table;")
                        .migrationFilePath("/tmp/V123__create_tables.sql")
                        .rollbackFilePath("/tmp/V123__rollback.sql")
                        .elapsedTimeMs(2500L)
                        .build();

        // Then: 验证辅助方法
        assertThat(result.getEntityCount()).isEqualTo(1);
        assertThat(result.getRelationshipCount()).isEqualTo(0);
        assertThat(result.getMigrationSQLLineCount()).isEqualTo(3);
        assertThat(result.getFormattedElapsedTime()).isEqualTo("2.5秒");

        // 验证摘要输出
        String summary = result.getSummary();
        assertThat(summary).contains("实体数量: 1");
        assertThat(summary).contains("关系数量: 0");
        assertThat(summary).contains("迁移SQL行数: 3");
        assertThat(summary).contains("总耗时: 2.5秒");
    }

    /**
     * 测试：格式化耗时（不同时间范围）
     */
    @Test
    void shouldFormatElapsedTimeCorrectly() {
        // 测试毫秒级
        DatabaseSchemaGenerator.DatabaseSchemaResult result1 =
                DatabaseSchemaGenerator.DatabaseSchemaResult.builder()
                        .elapsedTimeMs(500L)
                        .build();
        assertThat(result1.getFormattedElapsedTime()).isEqualTo("500ms");

        // 测试秒级
        DatabaseSchemaGenerator.DatabaseSchemaResult result2 =
                DatabaseSchemaGenerator.DatabaseSchemaResult.builder()
                        .elapsedTimeMs(3500L)
                        .build();
        assertThat(result2.getFormattedElapsedTime()).isEqualTo("3.5秒");

        // 测试分钟级
        DatabaseSchemaGenerator.DatabaseSchemaResult result3 =
                DatabaseSchemaGenerator.DatabaseSchemaResult.builder()
                        .elapsedTimeMs(125000L) // 2分5秒
                        .build();
        assertThat(result3.getFormattedElapsedTime()).isEqualTo("2分5秒");
    }

    /**
     * 测试：文件命名格式正确
     */
    @Test
    void shouldGenerateCorrectFileNamesWithTimestamp() throws IOException {
        // Given: 准备最小测试数据
        String userRequirement = "测试";
        Entity entity = Entity.builder()
                .name("test_table")
                .description("测试表")
                .fields(Collections.emptyList())
                .build();

        when(entityAnalyzer.extractEntities(any())).thenReturn(List.of(entity));
        when(entityAnalyzer.inferRelationships(any())).thenReturn(Collections.emptyList());
        when(schemaBuilder.generateMigrationScript(any(), any())).thenReturn("SQL");
        when(schemaBuilder.generateRollbackScript(any(), any())).thenReturn("SQL");

        // When: 生成Schema
        DatabaseSchemaGenerator.DatabaseSchemaResult result = generator.generate(userRequirement);

        // Then: 验证文件名格式
        String migrationFileName = Path.of(result.getMigrationFilePath()).getFileName().toString();
        assertThat(migrationFileName).matches("V\\d{14}__create_tables\\.sql");

        String rollbackFileName = Path.of(result.getRollbackFilePath()).getFileName().toString();
        assertThat(rollbackFileName).matches("V\\d{14}__rollback\\.sql");

        // 验证时间戳相同（同一次生成）
        String migrationTimestamp = migrationFileName.substring(1, 15);
        String rollbackTimestamp = rollbackFileName.substring(1, 15);
        assertThat(migrationTimestamp).isEqualTo(rollbackTimestamp);
    }
}
