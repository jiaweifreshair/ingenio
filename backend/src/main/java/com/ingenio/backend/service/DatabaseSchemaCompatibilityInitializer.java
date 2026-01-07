package com.ingenio.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 数据库表结构兼容性初始化器
 *
 * 目的：
 * - 解决“实体字段已新增，但本地/开发数据库尚未迁移”的漂移问题
 * - 避免 Blueprint 相关字段缺失导致 MyBatis-Plus selectById 直接报 500（如 app_specs.blueprint_id 不存在）
 *
 * 设计原则：
 * - 只做“向后兼容”的最小补齐：ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS
 * - 失败不阻断启动：开发环境可自修复，生产环境仍应以标准迁移为准
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class DatabaseSchemaCompatibilityInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        // 仅做兼容性补齐：避免因字段缺失导致关键链路不可用
        ensureAppSpecsBlueprintColumns();
        ensureIndustryTemplatesBlueprintColumns();
        ensureG3JobsBlueprintColumns();
    }

    /**
     * 为 app_specs 表补齐 Blueprint 相关字段（以及必要的类型升级）。
     *
     * 当前代码中 AppSpecEntity 映射了：
     * - blueprint_id(UUID)
     * - blueprint_spec(JSONB)
     * - blueprint_mode_enabled(BOOLEAN)
     *
     * 若数据库缺列，会导致任何 selectById 失败，从而阻塞：
     * - /v2/plan-routing/{id}/confirm-design
     * - /v2/plan-routing/{id}/update-prototype
     */
    private void ensureAppSpecsBlueprintColumns() {
        log.info("[SchemaCompat] 检查 app_specs Blueprint 字段...");

        executeQuietly("ALTER TABLE IF EXISTS app_specs ADD COLUMN IF NOT EXISTS blueprint_id UUID");
        executeQuietly("ALTER TABLE IF EXISTS app_specs ADD COLUMN IF NOT EXISTS blueprint_spec JSONB");
        executeQuietly("ALTER TABLE IF EXISTS app_specs ADD COLUMN IF NOT EXISTS blueprint_mode_enabled BOOLEAN DEFAULT FALSE");
        executeQuietly("""
            CREATE INDEX IF NOT EXISTS idx_app_specs_blueprint_mode_enabled
            ON app_specs(blueprint_mode_enabled)
            WHERE blueprint_mode_enabled = TRUE
            """);

        // 兼容历史：早期迁移把 blueprint_id 定义为 VARCHAR(64)，这里按需升级为 UUID
        Optional<String> udtName = getColumnUdtName("app_specs", "blueprint_id");
        if (udtName.isEmpty()) {
            log.info("[SchemaCompat] app_specs.blueprint_id 不存在（已通过 ADD COLUMN 补齐或表不存在）");
            return;
        }

        if (!"uuid".equalsIgnoreCase(udtName.get())) {
            log.warn("[SchemaCompat] 检测到 app_specs.blueprint_id 类型为 {}，尝试升级为 UUID", udtName.get());
            executeQuietly("""
                ALTER TABLE app_specs
                ALTER COLUMN blueprint_id TYPE UUID
                USING (
                  CASE
                    WHEN blueprint_id IS NULL THEN NULL
                    WHEN BTRIM(blueprint_id::text) = '' THEN NULL
                    WHEN blueprint_id::text ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                      THEN blueprint_id::uuid
                    WHEN blueprint_id::text = 'campus-marketplace' THEN '28f12c4d-db84-4583-ba26-96426a415c68'::uuid
                    WHEN blueprint_id::text = 'personal-blog' THEN 'f46a9ce0-e682-4a4d-922b-bfd8ba70807d'::uuid
                    ELSE NULL
                  END
                )
                """);
        }
    }

    /**
     * 为 industry_templates 表补齐 Blueprint 规范字段。
     *
     * Blueprint Mode 会从 IndustryTemplate.blueprint_spec 读取规范并写入 AppSpec/G3 Job。
     */
    private void ensureIndustryTemplatesBlueprintColumns() {
        log.info("[SchemaCompat] 检查 industry_templates Blueprint 字段...");
        executeQuietly("ALTER TABLE IF EXISTS industry_templates ADD COLUMN IF NOT EXISTS blueprint_spec JSONB");
    }

    /**
     * 为 g3_jobs 表补齐 Blueprint 相关字段（避免创建/查询任务时报错）。
     */
    private void ensureG3JobsBlueprintColumns() {
        log.info("[SchemaCompat] 检查 g3_jobs Blueprint 字段...");

        executeQuietly("ALTER TABLE IF EXISTS g3_jobs ADD COLUMN IF NOT EXISTS matched_template_id UUID");
        executeQuietly("ALTER TABLE IF EXISTS g3_jobs ADD COLUMN IF NOT EXISTS blueprint_spec JSONB");
        executeQuietly("ALTER TABLE IF EXISTS g3_jobs ADD COLUMN IF NOT EXISTS blueprint_mode_enabled BOOLEAN DEFAULT FALSE");
        executeQuietly("ALTER TABLE IF EXISTS g3_jobs ADD COLUMN IF NOT EXISTS template_context TEXT");
        executeQuietly("CREATE INDEX IF NOT EXISTS idx_g3_jobs_matched_template_id ON g3_jobs(matched_template_id)");
        executeQuietly("""
            CREATE INDEX IF NOT EXISTS idx_g3_jobs_blueprint_mode_enabled
            ON g3_jobs(blueprint_mode_enabled)
            WHERE blueprint_mode_enabled = TRUE
            """);
    }

    /**
     * 获取某个列在 information_schema 中的 udt_name（如 uuid / varchar / text）。
     */
    private Optional<String> getColumnUdtName(String tableName, String columnName) {
        try {
            List<String> rows = jdbcTemplate.queryForList(
                    """
                        SELECT udt_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                          AND column_name = ?
                        """,
                    String.class,
                    tableName,
                    columnName
            );
            return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
        } catch (Exception e) {
            log.warn("[SchemaCompat] 查询列类型失败: {}.{} ({})", tableName, columnName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 执行 SQL（失败仅记录日志，不阻断启动）。
     */
    private void executeQuietly(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("[SchemaCompat] 执行失败: {} ({})", compactSql(sql), e.getMessage());
        }
    }

    private String compactSql(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
    }
}
