package com.ingenio.backend.service.blueprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.blueprint.BlueprintSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Blueprint Prompt 构建器
 *
 * 职责：
 * - 将 blueprintSpec（JSONB）转化为可读、可执行的“强约束提示词”
 * - 统一约束注入方式，避免各 Agent 自行拼接导致口径漂移
 *
 * 设计原则：
 * - 强约束：表名/字段名不可擅自变更
 * - 可扩展：允许在不改动 Agent 逻辑的情况下增强约束表达
 * - 现实主义：允许额外字段（如 created_at/updated_at），但必须包含 Blueprint 定义的核心字段
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlueprintPromptBuilder {

    private final ObjectMapper objectMapper;

    /**
     * 为 ArchitectAgent 构建 Blueprint 约束提示词
     *
     * @param blueprintSpec Blueprint 规范（JSONB Map）
     * @return 约束提示词（为空表示不启用）
     */
    public String buildArchitectConstraint(Map<String, Object> blueprintSpec) {
        BlueprintSpec spec = parse(blueprintSpec);
        if (spec == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## Blueprint 约束（必须严格遵守）\n");
        sb.append("- Blueprint 模式已启用：你必须以 Blueprint 为最高优先级约束来源。\n");
        sb.append("- 禁止修改 Blueprint 中定义的 tableName 与核心字段名。\n");
        sb.append("- 允许补充非核心字段（如 created_at/updated_at、索引、约束、触发器），但不得删除 Blueprint 核心字段。\n");
        sb.append("- 主键统一使用 UUID，默认值使用 gen_random_uuid()。\n");
        sb.append("- 外键字段类型必须与引用主键一致（UUID）。\n");

        if (spec.getConstraints() != null) {
            BlueprintSpec.Constraints c = spec.getConstraints();
            sb.append("\n### 技术栈约束\n");
            if (notBlank(c.getTechStack())) {
                sb.append("- techStack: ").append(c.getTechStack()).append("\n");
            }
            if (notBlank(c.getDatabase())) {
                sb.append("- database: ").append(c.getDatabase()).append("\n");
            }
            if (notBlank(c.getAuth())) {
                sb.append("- auth: ").append(c.getAuth()).append("\n");
            }
            if (notBlank(c.getApiStyle())) {
                sb.append("- apiStyle: ").append(c.getApiStyle()).append("\n");
            }
        }

        sb.append("\n### 强制表结构（Schema）\n");
        sb.append("以下表结构必须包含在 PostgreSQL DDL 中（允许额外字段，但不可缺失/改名）：\n");
        for (BlueprintSpec.SchemaTable table : nullSafe(spec.getSchema())) {
            if (!notBlank(table.getTableName())) {
                continue;
            }
            sb.append("- ").append(table.getTableName());
            if (notBlank(table.getComment())) {
                sb.append("（").append(table.getComment()).append("）");
            }
            sb.append("\n");
            for (BlueprintSpec.SchemaColumn col : nullSafe(table.getColumns())) {
                if (!notBlank(col.getName())) {
                    continue;
                }
                // 兼容复合主键占位写法：{"name":"PRIMARY KEY","type":"COMPOSITE",...}
                if ("PRIMARY KEY".equalsIgnoreCase(col.getName())) {
                    continue;
                }
                sb.append("  - ").append(col.getName());
                if (notBlank(col.getType())) {
                    sb.append(": ").append(col.getType());
                }
                if (notBlank(col.getConstraints())) {
                    sb.append(" ").append(col.getConstraints());
                }
                if (notBlank(col.getComment())) {
                    sb.append("  // ").append(col.getComment());
                }
                sb.append("\n");
            }
        }

        if (spec.getFeatures() != null && !spec.getFeatures().isEmpty()) {
            sb.append("\n### 核心功能（Features）\n");
            sb.append("API 契约与数据库设计需要覆盖以下核心能力：\n");
            for (String feature : spec.getFeatures()) {
                if (notBlank(feature)) {
                    sb.append("- ").append(feature).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 为 BackendCoderAgent 生成 Entity 约束
     *
     * @param blueprintSpec Blueprint 规范（JSONB Map）
     * @return 约束提示词
     */
    public String buildEntityConstraint(Map<String, Object> blueprintSpec) {
        BlueprintSpec spec = parse(blueprintSpec);
        if (spec == null || spec.getSchema() == null || spec.getSchema().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## Blueprint 实体约束（必须遵守）\n");
        sb.append("- Entity/Mapper/Service/Controller 必须覆盖 Blueprint schema 中的所有表与字段。\n");
        sb.append("- 主键字段使用 UUID，并使用项目内约定的 UUIDv8TypeHandler。\n");
        sb.append("- 允许增加非核心字段，但不得删除/改名 Blueprint 字段。\n");
        sb.append("\n### 必须覆盖的表与字段\n");

        for (BlueprintSpec.SchemaTable table : nullSafe(spec.getSchema())) {
            if (!notBlank(table.getTableName())) {
                continue;
            }
            sb.append("- ").append(table.getTableName()).append(": ");
            boolean first = true;
            for (BlueprintSpec.SchemaColumn col : nullSafe(table.getColumns())) {
                if (!notBlank(col.getName()) || "PRIMARY KEY".equalsIgnoreCase(col.getName())) {
                    continue;
                }
                if (!first) {
                    sb.append(", ");
                }
                sb.append(col.getName());
                first = false;
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 为 BackendCoderAgent 生成 Service/Controller 约束
     *
     * @param blueprintSpec Blueprint 规范（JSONB Map）
     * @return 约束提示词
     */
    public String buildServiceConstraint(Map<String, Object> blueprintSpec) {
        BlueprintSpec spec = parse(blueprintSpec);
        if (spec == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## Blueprint 业务约束（必须遵守）\n");
        sb.append("- Service/Controller 生成必须围绕 Blueprint 的核心功能，禁止偏离业务边界。\n");
        sb.append("- API 风格遵循 RESTful，响应结构必须与系统统一格式一致（{code,msg,data}）。\n");

        if (spec.getConstraints() != null && notBlank(spec.getConstraints().getAuth())) {
            sb.append("- 认证方式: ").append(spec.getConstraints().getAuth()).append("\n");
        }

        if (spec.getFeatures() != null && !spec.getFeatures().isEmpty()) {
            sb.append("\n### 必须覆盖的功能\n");
            for (String feature : spec.getFeatures()) {
                if (notBlank(feature)) {
                    sb.append("- ").append(feature).append("\n");
                }
            }
        }

        return sb.toString();
    }

    private BlueprintSpec parse(Map<String, Object> blueprintSpec) {
        if (blueprintSpec == null || blueprintSpec.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.convertValue(blueprintSpec, BlueprintSpec.class);
        } catch (Exception e) {
            log.warn("BlueprintSpec 解析失败，已降级为不注入约束: {}", e.getMessage());
            return null;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}

