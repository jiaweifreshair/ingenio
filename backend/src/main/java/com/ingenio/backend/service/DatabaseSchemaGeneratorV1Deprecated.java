package com.ingenio.backend.service;

import java.time.Instant;
import com.ingenio.backend.entity.GeneratedSchemaEntity;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.StructuredRequirementEntity;
import com.ingenio.backend.mapper.GeneratedSchemaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据库Schema生成器 (V1.0 已废弃)
 *
 * <p>根据结构化需求生成数据库表结构和DDL SQL脚本</p>
 *
 * <p><strong>⚠️ 此类已废弃，请使用V2.0新版本：</strong></p>
 * <pre>
 * com.ingenio.backend.codegen.generator.DatabaseSchemaGenerator
 * </pre>
 *
 * @deprecated 请使用V2.0版本的DatabaseSchemaGenerator（com.ingenio.backend.codegen.generator.DatabaseSchemaGenerator），
 *             V2.0版本集成了EntityAnalyzer和SupabaseSchemaBuilder，提供更完善的功能。
 * @see com.ingenio.backend.codegen.generator.DatabaseSchemaGenerator
 */
@Deprecated(since = "2025-11-18", forRemoval = true)
@Slf4j
@Service("databaseSchemaGeneratorV1")
@RequiredArgsConstructor
public class DatabaseSchemaGeneratorV1Deprecated {

    private final GeneratedSchemaMapper schemaMapper;

    /**
     * 生成数据库Schema
     *
     * @param requirement 结构化需求
     * @param task        生成任务
     * @return 生成的Schema
     */
    public GeneratedSchemaEntity generate(StructuredRequirementEntity requirement, GenerationTaskEntity task) {
        log.info("开始生成Schema: taskId={}, requirementId={}",
                task.getId(), requirement.getId());

        try {
            // 1. 提取实体列表
            List<Map<String, Object>> entities = extractEntities(requirement);

            // 2. 生成DDL SQL脚本
            String ddlSql = generateDDL(entities);

            // 3. 创建Schema实体
            GeneratedSchemaEntity entity = new GeneratedSchemaEntity();
            entity.setId(UUID.randomUUID());
            entity.setTenantId(task.getTenantId());
            entity.setUserId(task.getUserId());
            entity.setTaskId(task.getId());
            entity.setSchemaName(generateSchemaName(task));
            entity.setDescription("AI生成的数据库Schema");

            Map<String, Object> tablesMap = new HashMap<>();
            tablesMap.put("tables", entities);
            entity.setTables(tablesMap);
            entity.setDdlSql(ddlSql);
            entity.setVersion(1);
            entity.setStatus("draft");
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            // 4. 保存到数据库
            schemaMapper.insert(entity);

            log.info("Schema生成完成: taskId={}, schemaId={}, tablesCount={}",
                    task.getId(), entity.getId(), entities.size());

            return entity;

        } catch (Exception e) {
            log.error("Schema生成失败: taskId={}", task.getId(), e);
            throw new RuntimeException("Schema生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取实体列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractEntities(StructuredRequirementEntity requirement) {
        Map<String, Object> entitiesMap = requirement.getEntities();
        if (entitiesMap != null && entitiesMap.containsKey("items")) {
            return (List<Map<String, Object>>) entitiesMap.get("items");
        }
        return new ArrayList<>();
    }

    /**
     * 生成DDL SQL脚本
     */
    private String generateDDL(List<Map<String, Object>> entities) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("-- Auto-generated DDL by Ingenio NL2Backend\n");
        ddl.append("-- Generated at: ").append(Instant.now()).append("\n\n");

        for (Map<String, Object> entity : entities) {
            ddl.append(generateTableDDL(entity));
            ddl.append("\n\n");
        }

        return ddl.toString();
    }

    /**
     * 生成单个表的DDL
     */
    @SuppressWarnings("unchecked")
    private String generateTableDDL(Map<String, Object> entity) {
        String tableName = (String) entity.get("tableName");
        String description = (String) entity.get("description");
        List<Map<String, Object>> attributes = (List<Map<String, Object>>) entity.get("attributes");

        StringBuilder ddl = new StringBuilder();
        ddl.append("-- ").append(description != null ? description : tableName).append("\n");
        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");

        // 添加字段定义
        for (int i = 0; i < attributes.size(); i++) {
            Map<String, Object> attr = attributes.get(i);
            ddl.append("    ").append(generateColumnDDL(attr));
            if (i < attributes.size() - 1) {
                ddl.append(",");
            }
            ddl.append("\n");
        }

        ddl.append(");\n");

        // 添加注释
        if (description != null) {
            ddl.append("COMMENT ON TABLE ").append(tableName)
               .append(" IS '").append(description).append("';\n");
        }

        return ddl.toString();
    }

    /**
     * 生成字段定义DDL
     */
    private String generateColumnDDL(Map<String, Object> attr) {
        String name = (String) attr.get("name");
        String type = (String) attr.get("type");
        Boolean nullable = (Boolean) attr.getOrDefault("nullable", true);
        Boolean primaryKey = (Boolean) attr.getOrDefault("primaryKey", false);
        String description = (String) attr.get("description");

        StringBuilder ddl = new StringBuilder();
        ddl.append(name).append(" ").append(type);

        if (primaryKey) {
            ddl.append(" PRIMARY KEY");
        }

        if (!nullable && !primaryKey) {
            ddl.append(" NOT NULL");
        }

        return ddl.toString();
    }

    /**
     * 生成Schema名称
     */
    private String generateSchemaName(GenerationTaskEntity task) {
        return "schema_" + task.getId().toString().substring(0, 8);
    }
}
