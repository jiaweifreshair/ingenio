package com.ingenio.backend.codegen.generator;

import com.ingenio.backend.codegen.analyzer.EntityAnalyzer;
import com.ingenio.backend.codegen.builder.SupabaseSchemaBuilder;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.EntityRelationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 数据库Schema生成器（V2.0核心组件）
 *
 * <p>
 * 整合EntityAnalyzer和SupabaseSchemaBuilder，提供端到端的数据库Schema生成能力。
 * </p>
 *
 * <p>
 * 核心流程：
 * </p>
 * 
 * <pre>
 * 用户需求文本
 *     ↓
 * EntityAnalyzer提取实体定义
 *     ↓
 * EntityAnalyzer推断实体关系
 *     ↓
 * SupabaseSchemaBuilder生成Supabase兼容的PostgreSQL DDL
 *     ↓
 * 写入迁移文件到磁盘
 * </pre>
 *
 * <p>
 * 使用示例：
 * </p>
 * 
 * <pre>{@code
 * @Autowired
 * private DatabaseSchemaGenerator schemaGenerator;
 *
 * public void generateSchema() {
 *     String userRequirement = "我需要一个博客系统，包括用户、文章、评论、标签功能";
 *
 *     DatabaseSchemaResult result = schemaGenerator.generate(userRequirement);
 *
 *     System.out.println("生成的实体数量: " + result.getEntities().size());
 *     System.out.println("推断的关系数量: " + result.getRelationships().size());
 *     System.out.println("迁移文件路径: " + result.getMigrationFilePath());
 *     System.out.println("回滚文件路径: " + result.getRollbackFilePath());
 * }
 * }</pre>
 *
 * <p>
 * 输出文件格式：
 * </p>
 * <ul>
 * <li>迁移文件：migrations/V{timestamp}__create_tables.sql</li>
 * <li>回滚文件：migrations/V{timestamp}__rollback.sql</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 2.3: 数据库Schema生成器整合
 */
@Service
public class DatabaseSchemaGenerator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaGenerator.class);

    /**
     * 实体分析器（依赖注入）
     * 负责从用户需求文本中提取实体定义和推断实体关系
     */
    private final EntityAnalyzer entityAnalyzer;

    /**
     * Supabase Schema构建器（依赖注入）
     * 负责将实体定义转换为Supabase兼容的PostgreSQL DDL语句
     */
    private final SupabaseSchemaBuilder schemaBuilder;

    public DatabaseSchemaGenerator(EntityAnalyzer entityAnalyzer, SupabaseSchemaBuilder schemaBuilder) {
        this.entityAnalyzer = entityAnalyzer;
        this.schemaBuilder = schemaBuilder;
    }

    /**
     * 迁移文件输出目录
     * 可通过配置文件覆盖：ingenio.codegen.migration-dir
     * 默认值：src/main/resources/db/migrations
     */
    @org.springframework.beans.factory.annotation.Value("${ingenio.codegen.migration-dir:src/main/resources/db/migrations}")
    private String migrationDir;

    /**
     * 生成数据库Schema（核心方法）
     *
     * <p>
     * 完整的端到端流程：
     * </p>
     * <ol>
     * <li>调用EntityAnalyzer提取实体定义</li>
     * <li>调用EntityAnalyzer推断实体间关系</li>
     * <li>调用SupabaseSchemaBuilder生成迁移SQL</li>
     * <li>调用SupabaseSchemaBuilder生成回滚SQL</li>
     * <li>将SQL写入磁盘文件</li>
     * <li>返回生成结果</li>
     * </ol>
     *
     * @param userRequirement 用户需求描述（自然语言）
     * @return DatabaseSchemaResult 生成结果，包含实体、关系、文件路径等信息
     * @throws SchemaGenerationException 当生成过程中发生错误时抛出
     */
    public DatabaseSchemaResult generate(String userRequirement) {
        log.info("[DatabaseSchemaGenerator] ========== 开始生成数据库Schema ==========");
        log.info("[DatabaseSchemaGenerator] 用户需求: {}", userRequirement);

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: 提取实体定义
            log.info("[DatabaseSchemaGenerator] Step 1/5: 提取实体定义...");
            List<Entity> entities = entityAnalyzer.extractEntities(userRequirement);
            log.info("[DatabaseSchemaGenerator] ✅ 成功提取 {} 个实体", entities.size());

            if (entities.isEmpty()) {
                log.warn("[DatabaseSchemaGenerator] ⚠️ 未提取到任何实体，生成终止");
                throw new SchemaGenerationException("未能从需求中提取到任何实体定义");
            }

            // Step 2: 推断实体关系
            log.info("[DatabaseSchemaGenerator] Step 2/5: 推断实体关系...");
            List<EntityRelationship> relationships = entityAnalyzer.inferRelationships(entities);
            log.info("[DatabaseSchemaGenerator] ✅ 成功推断 {} 个实体关系", relationships.size());

            // Step 3: 生成迁移SQL脚本
            log.info("[DatabaseSchemaGenerator] Step 3/5: 生成迁移SQL脚本...");
            String migrationSQL = schemaBuilder.generateMigrationScript(entities, relationships);
            log.info("[DatabaseSchemaGenerator] ✅ 迁移脚本生成完成，长度: {} 字符", migrationSQL.length());

            // Step 4: 生成回滚SQL脚本
            log.info("[DatabaseSchemaGenerator] Step 4/5: 生成回滚SQL脚本...");
            String rollbackSQL = schemaBuilder.generateRollbackScript(entities, relationships);
            log.info("[DatabaseSchemaGenerator] ✅ 回滚脚本生成完成，长度: {} 字符", rollbackSQL.length());

            // Step 5: 写入磁盘文件
            log.info("[DatabaseSchemaGenerator] Step 5/5: 写入迁移文件到磁盘...");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            Path migrationFile = writeMigrationFile(timestamp, migrationSQL);
            Path rollbackFile = writeRollbackFile(timestamp, rollbackSQL);
            log.info("[DatabaseSchemaGenerator] ✅ 迁移文件已写入: {}", migrationFile.toAbsolutePath());
            log.info("[DatabaseSchemaGenerator] ✅ 回滚文件已写入: {}", rollbackFile.toAbsolutePath());

            // 计算总耗时
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("[DatabaseSchemaGenerator] ========== 数据库Schema生成完成 ==========");
            log.info("[DatabaseSchemaGenerator] 总耗时: {} ms", elapsedTime);

            // 返回生成结果
            return DatabaseSchemaResult.builder()
                    .userRequirement(userRequirement)
                    .entities(entities)
                    .relationships(relationships)
                    .migrationSQL(migrationSQL)
                    .rollbackSQL(rollbackSQL)
                    .migrationFilePath(migrationFile.toString())
                    .rollbackFilePath(rollbackFile.toString())
                    .elapsedTimeMs(elapsedTime)
                    .build();

        } catch (IOException e) {
            log.error("[DatabaseSchemaGenerator] ❌ 写入迁移文件失败: {}", e.getMessage(), e);
            throw new SchemaGenerationException("写入迁移文件失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[DatabaseSchemaGenerator] ❌ Schema生成过程中发生错误: {}", e.getMessage(), e);
            throw new SchemaGenerationException("Schema生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 写入迁移SQL脚本到磁盘
     *
     * <p>
     * 文件命名规则：V{timestamp}__create_tables.sql
     * </p>
     * <p>
     * 示例：V20251117143025__create_tables.sql
     * </p>
     *
     * @param timestamp    时间戳（格式：yyyyMMddHHmmss）
     * @param migrationSQL 迁移SQL脚本内容
     * @return Path 迁移文件路径
     * @throws IOException 当文件写入失败时抛出
     */
    private Path writeMigrationFile(String timestamp, String migrationSQL) throws IOException {
        // 创建迁移目录（如果不存在）
        Path migrationDirPath = Paths.get(migrationDir);
        if (!Files.exists(migrationDirPath)) {
            log.info("[DatabaseSchemaGenerator] 创建迁移目录: {}", migrationDirPath.toAbsolutePath());
            Files.createDirectories(migrationDirPath);
        }

        // 生成迁移文件路径
        String fileName = String.format("V%s__create_tables.sql", timestamp);
        Path filePath = migrationDirPath.resolve(fileName);

        // 写入文件
        Files.writeString(
                filePath,
                migrationSQL,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        log.info("[DatabaseSchemaGenerator] 迁移文件已写入: {} ({} bytes)",
                fileName, Files.size(filePath));

        return filePath;
    }

    /**
     * 写入回滚SQL脚本到磁盘
     *
     * <p>
     * 文件命名规则：V{timestamp}__rollback.sql
     * </p>
     * <p>
     * 示例：V20251117143025__rollback.sql
     * </p>
     *
     * @param timestamp   时间戳（格式：yyyyMMddHHmmss）
     * @param rollbackSQL 回滚SQL脚本内容
     * @return Path 回滚文件路径
     * @throws IOException 当文件写入失败时抛出
     */
    private Path writeRollbackFile(String timestamp, String rollbackSQL) throws IOException {
        // 创建迁移目录（如果不存在）
        Path migrationDirPath = Paths.get(migrationDir);
        if (!Files.exists(migrationDirPath)) {
            Files.createDirectories(migrationDirPath);
        }

        // 生成回滚文件路径
        String fileName = String.format("V%s__rollback.sql", timestamp);
        Path filePath = migrationDirPath.resolve(fileName);

        // 写入文件
        Files.writeString(
                filePath,
                rollbackSQL,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        log.info("[DatabaseSchemaGenerator] 回滚文件已写入: {} ({} bytes)",
                fileName, Files.size(filePath));

        return filePath;
    }

    /**
     * 数据库Schema生成结果
     *
     * <p>
     * 封装生成过程的所有输入、输出和元数据
     * </p>
     */
    @lombok.Data
    @lombok.Builder
    public static class DatabaseSchemaResult {
        /**
         * 用户需求描述（输入）
         */
        private String userRequirement;

        /**
         * 提取的实体列表
         */
        private List<Entity> entities;

        /**
         * 推断的实体关系列表
         */
        private List<EntityRelationship> relationships;

        /**
         * 生成的迁移SQL脚本
         */
        private String migrationSQL;

        /**
         * 生成的回滚SQL脚本
         */
        private String rollbackSQL;

        /**
         * 迁移文件路径（绝对路径）
         */
        private String migrationFilePath;

        /**
         * 回滚文件路径（绝对路径）
         */
        private String rollbackFilePath;

        /**
         * 总耗时（毫秒）
         */
        private long elapsedTimeMs;

        /**
         * 获取实体数量
         */
        public int getEntityCount() {
            return entities != null ? entities.size() : 0;
        }

        /**
         * 获取关系数量
         */
        public int getRelationshipCount() {
            return relationships != null ? relationships.size() : 0;
        }

        /**
         * 获取迁移SQL行数
         */
        public int getMigrationSQLLineCount() {
            return migrationSQL != null ? migrationSQL.split("\n").length : 0;
        }

        /**
         * 获取格式化的耗时描述
         *
         * @return 耗时描述（如："2.5秒"）
         */
        public String getFormattedElapsedTime() {
            if (elapsedTimeMs < 1000) {
                return elapsedTimeMs + "ms";
            } else if (elapsedTimeMs < 60000) {
                return String.format("%.1f秒", elapsedTimeMs / 1000.0);
            } else {
                long minutes = elapsedTimeMs / 60000;
                long seconds = (elapsedTimeMs % 60000) / 1000;
                return String.format("%d分%d秒", minutes, seconds);
            }
        }

        /**
         * 打印生成摘要（用于日志）
         *
         * @return 生成摘要文本
         */
        public String getSummary() {
            return String.format(
                    "数据库Schema生成摘要:\n" +
                            "  - 实体数量: %d\n" +
                            "  - 关系数量: %d\n" +
                            "  - 迁移SQL行数: %d\n" +
                            "  - 迁移文件: %s\n" +
                            "  - 回滚文件: %s\n" +
                            "  - 总耗时: %s",
                    getEntityCount(),
                    getRelationshipCount(),
                    getMigrationSQLLineCount(),
                    migrationFilePath,
                    rollbackFilePath,
                    getFormattedElapsedTime());
        }
    }

    /**
     * Schema生成异常
     *
     * <p>
     * 当Schema生成过程中发生错误时抛出
     * </p>
     */
    public static class SchemaGenerationException extends RuntimeException {
        public SchemaGenerationException(String message) {
            super(message);
        }

        public SchemaGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
