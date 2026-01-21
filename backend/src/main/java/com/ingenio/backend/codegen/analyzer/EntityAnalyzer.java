package com.ingenio.backend.codegen.analyzer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.codegen.schema.*;
import com.ingenio.backend.codegen.schema.EntityRelationship.RelationType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 实体分析器
 *
 * <p>
 * 使用AI（Qwen-Max）从用户需求中提取数据库实体定义
 * </p>
 */
@Service
@RequiredArgsConstructor
public class EntityAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(EntityAnalyzer.class);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    /**
     * AI提示词：实体提取系统提示
     */
    private static final String ENTITY_EXTRACTION_SYSTEM_PROMPT = """
            你是一个数据库设计专家，负责从用户需求中提取实体和字段定义。

            你的任务：
            1. 识别所有核心业务实体（数据库表）
            2. 为每个实体提取字段（字段名、类型、约束）
            3. 推断实体间的关系（1:1、1:N、N:M）
            4. 遵循PostgreSQL和Supabase最佳实践

            命名规范：
            - 表名：小写复数形式，使用下划线分隔（如users, blog_posts, order_items）
            - 字段名：小写，使用下划线分隔（如user_id, created_at, total_amount）
            - 外键字段：关联表名单数形式 + _id（如author_id指向users表）

            字段类型映射：
            - 主键：UUID（默认uuid_generate_v4()）
            - 邮箱、用户名、短文本：VARCHAR(255)
            - 年龄、数量、计数：INTEGER
            - 价格、金额：NUMERIC(10, 2)
            - 状态、分类：VARCHAR(50)
            - 描述、内容：TEXT
            - 时间戳：TIMESTAMPTZ（默认NOW()）
            - 布尔标志：BOOLEAN（默认false）

            约束规则：
            - 主键：NOT NULL + PRIMARY KEY
            - 邮箱：UNIQUE + NOT NULL
            - 外键：NOT NULL + REFERENCES + ON DELETE CASCADE/SET NULL
            - 枚举状态：CHECK约束（如status IN ('draft', 'published', 'archived')）

            Supabase标准字段（每个表必须包含）：
            - id UUID PRIMARY KEY DEFAULT uuid_generate_v4()
            - created_at TIMESTAMPTZ DEFAULT NOW()
            - updated_at TIMESTAMPTZ DEFAULT NOW()
            - deleted_at TIMESTAMPTZ（如果启用软删除）

            输出格式：
            返回JSON格式，包含entities数组，每个实体包含：
            - name: 表名
            - description: 表描述
            - fields: 字段列表
            - rlsEnabled: 是否启用RLS（默认true）
            - softDelete: 是否软删除（默认false）
            - timestamps: 是否时间戳（默认true）

            示例输出：
            {
              "entities": [
                {
                  "name": "users",
                  "description": "用户表",
                  "rlsEnabled": true,
                  "softDelete": false,
                  "timestamps": true,
                  "fields": [
                    {
                      "name": "id",
                      "type": "UUID",
                      "primaryKey": true,
                      "nullable": false,
                      "defaultValue": "uuid_generate_v4()",
                      "description": "用户ID"
                    },
                    {
                      "name": "email",
                      "type": "VARCHAR",
                      "length": 255,
                      "unique": true,
                      "nullable": false,
                      "description": "用户邮箱"
                    },
                    {
                      "name": "username",
                      "type": "VARCHAR",
                      "length": 50,
                      "unique": true,
                      "nullable": false,
                      "description": "用户名"
                    }
                  ]
                }
              ],
              "relationships": [
                {
                  "fromEntity": "users",
                  "toEntity": "posts",
                  "type": "ONE_TO_MANY",
                  "foreignKeyField": "author_id"
                }
              ]
            }

            注意事项：
            1. 不要遗漏任何核心实体
            2. 外键字段必须定义foreignKey属性
            3. 枚举字段必须定义checkConstraint
            4. 确保JSON格式有效且完整
            5. 不要省略标准字段（id、created_at、updated_at）
            """;

    /**
     * 从用户需求中提取实体定义
     */
    public List<Entity> extractEntities(String userRequirement) {
        log.info("[EntityAnalyzer] 开始提取实体，用户需求: {}", userRequirement);

        try {
            // Step 1: 构建用户提示词
            String userPrompt = buildEntityExtractionUserPrompt(userRequirement);
            log.debug("[EntityAnalyzer] 用户提示词: {}", userPrompt);

            // Step 2: 调用Spring AI（Qwen-Max）- 动态构建ChatClient
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            String aiResponse = chatClient.prompt()
                    .system(ENTITY_EXTRACTION_SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();
            log.debug("[EntityAnalyzer] AI原始响应: {}", aiResponse);

            // Step 3: 解析AI响应
            List<Entity> entities = parseEntitiesFromAI(aiResponse);
            log.info("[EntityAnalyzer] 成功提取{}个实体: {}",
                    entities.size(),
                    entities.stream().map(Entity::getName).collect(Collectors.joining(", ")));

            // Step 4: 验证和完善实体定义
            entities.forEach(this::validateAndEnhanceEntity);

            return entities;

        } catch (Exception e) {
            log.error("[EntityAnalyzer] 提取实体失败", e);
            throw new RuntimeException("实体提取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 推断实体间关系
     */
    public List<EntityRelationship> inferRelationships(List<Entity> entities) {
        log.info("[EntityAnalyzer] 开始推断实体间关系，实体数量: {}", entities.size());

        List<EntityRelationship> relationships = new ArrayList<>();

        // 创建实体名称索引（快速查找）
        Map<String, Entity> entityMap = entities.stream()
                .collect(Collectors.toMap(Entity::getName, e -> e));

        // 遍历每个实体，检查外键字段
        for (Entity entity : entities) {
            for (Field field : entity.getFields()) {
                if (field.getForeignKey() != null && !field.getForeignKey().isEmpty()) {
                    // 解析外键引用：格式为 table_name.field_name
                    String[] parts = field.getForeignKey().split("\\.");
                    if (parts.length == 2) {
                        String referencedTable = parts[0];
                        String referencedField = parts[1];

                        // 推断关系类型
                        RelationType relationType = inferRelationType(field, entity);

                        relationships.add(EntityRelationship.builder()
                                .fromEntity(referencedTable)
                                .toEntity(entity.getName())
                                .type(relationType)
                                .foreignKeyField(field.getName())
                                .build());

                        log.debug("[EntityAnalyzer] 推断关系: {} ({}) {} (外键: {})",
                                referencedTable, relationType, entity.getName(), field.getName());
                    }
                }
            }
        }

        // 推断多对多关系（基于命名模式）
        relationships.addAll(inferManyToManyRelationships(entities, entityMap));

        log.info("[EntityAnalyzer] 成功推断{}个关系", relationships.size());
        relationships.forEach(r -> log.debug("[EntityAnalyzer] 关系: {}", r.getDescription()));

        return relationships;
    }

    /**
     * 映射语义类型到PostgreSQL类型
     */
    public FieldType mapFieldType(String semanticType) {
        String lower = semanticType.toLowerCase();

        // 邮箱、用户名、URL等
        if (lower.matches(".*(邮箱|email|用户名|username|url|链接|地址|phone|电话).*")) {
            return FieldType.VARCHAR;
        }

        // 年龄、数量、计数等
        if (lower.matches(".*(年龄|age|数量|count|次数|排序|sort|order).*")) {
            return FieldType.INTEGER;
        }

        // 价格、金额、评分等
        if (lower.matches(".*(价格|金额|price|amount|费用|cost|评分|score|rating).*")) {
            return FieldType.NUMERIC;
        }

        // 状态、分类、类型等
        if (lower.matches(".*(状态|status|分类|category|类型|type|角色|role).*")) {
            return FieldType.VARCHAR;
        }

        // 描述、内容、简介等
        if (lower.matches(".*(描述|description|内容|content|简介|bio|详情|detail).*")) {
            return FieldType.TEXT;
        }

        // 时间、日期等
        if (lower.matches(".*(时间|date|日期|timestamp|创建|更新|deleted).*")) {
            return FieldType.TIMESTAMPTZ;
        }

        // 布尔标志
        if (lower.matches(".*(是否|是|否|enabled|disabled|active|标志|flag).*")) {
            return FieldType.BOOLEAN;
        }

        // UUID
        if (lower.matches(".*(id|uuid|guid).*")) {
            return FieldType.UUID;
        }

        // 默认VARCHAR
        return FieldType.VARCHAR;
    }

    // ==========================================
    // 私有辅助方法
    // ==========================================

    /**
     * 构建实体提取的用户提示词
     */
    private String buildEntityExtractionUserPrompt(String userRequirement) {
        return String.format("""
                用户需求：%s

                请分析上述需求，识别所有核心业务实体，并为每个实体提取字段定义。

                要求：
                1. 输出JSON格式（确保valid JSON）
                2. 表名使用复数形式（users, posts, comments）
                3. 字段名使用下划线命名法（user_id, created_at）
                4. 每个表必须包含id、created_at、updated_at标准字段
                5. 外键字段必须定义foreignKey属性（格式：table.field）
                6. 枚举字段必须定义checkConstraint
                7. 识别实体间的关系（1:1、1:N、N:M）

                请务必返回完整、准确的JSON格式数据。
                """, userRequirement);
    }

    /**
     * 解析AI响应中的实体定义
     */
    private List<Entity> parseEntitiesFromAI(String aiResponse) throws Exception {
        // 提取JSON部分（AI可能返回```json...```格式）
        String jsonContent = extractJsonContent(aiResponse);

        // 解析JSON
        Map<String, Object> responseMap = objectMapper.readValue(jsonContent, new TypeReference<>() {
        });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entitiesData = (List<Map<String, Object>>) responseMap.get("entities");

        if (entitiesData == null || entitiesData.isEmpty()) {
            throw new RuntimeException("AI响应中未找到entities数组");
        }

        return entitiesData.stream()
                .map(this::parseEntity)
                .collect(Collectors.toList());
    }

    /**
     * 提取JSON内容（处理markdown代码块格式）
     */
    private String extractJsonContent(String aiResponse) {
        // 尝试提取```json...```中的内容
        Pattern pattern = Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(aiResponse);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 如果没有代码块，尝试提取第一个{...}
        pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        matcher = pattern.matcher(aiResponse);

        if (matcher.find()) {
            return matcher.group().trim();
        }

        // 否则直接返回原内容
        return aiResponse.trim();
    }

    /**
     * 解析单个实体
     */
    private Entity parseEntity(Map<String, Object> entityData) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fieldsData = (List<Map<String, Object>>) entityData.get("fields");

        List<Field> fields = fieldsData.stream()
                .map(this::parseField)
                .collect(Collectors.toList());

        return Entity.builder()
                .name((String) entityData.get("name"))
                .description((String) entityData.get("description"))
                .fields(fields)
                .rlsEnabled((Boolean) entityData.getOrDefault("rlsEnabled", true))
                .softDelete((Boolean) entityData.getOrDefault("softDelete", false))
                .timestamps((Boolean) entityData.getOrDefault("timestamps", true))
                .build();
    }

    /**
     * 解析单个字段
     */
    private Field parseField(Map<String, Object> fieldData) {
        String typeName = (String) fieldData.get("type");
        FieldType type = FieldType.valueOf(typeName);

        return Field.builder()
                .name((String) fieldData.get("name"))
                .type(type)
                .length(fieldData.containsKey("length") ? ((Number) fieldData.get("length")).intValue() : null)
                .precision(fieldData.containsKey("precision") ? ((Number) fieldData.get("precision")).intValue() : null)
                .scale(fieldData.containsKey("scale") ? ((Number) fieldData.get("scale")).intValue() : null)
                .primaryKey((Boolean) fieldData.getOrDefault("primaryKey", false))
                .unique((Boolean) fieldData.getOrDefault("unique", false))
                .nullable((Boolean) fieldData.getOrDefault("nullable", true))
                .defaultValue((String) fieldData.get("defaultValue"))
                .checkConstraint((String) fieldData.get("checkConstraint"))
                .foreignKey((String) fieldData.get("foreignKey"))
                .onDelete((String) fieldData.getOrDefault("onDelete", "RESTRICT"))
                .description((String) fieldData.get("description"))
                .indexed((Boolean) fieldData.getOrDefault("indexed", false))
                .build();
    }

    /**
     * 验证和完善实体定义
     */
    private void validateAndEnhanceEntity(Entity entity) {
        // 确保有主键
        boolean hasPrimaryKey = entity.getFields().stream().anyMatch(Field::isPrimaryKey);
        if (!hasPrimaryKey) {
            log.warn("[EntityAnalyzer] 实体{}缺少主键，自动添加id字段", entity.getName());
            entity.getFields().add(0, Field.builder()
                    .name("id")
                    .type(FieldType.UUID)
                    .primaryKey(true)
                    .nullable(false)
                    .defaultValue("uuid_generate_v4()")
                    .description("主键ID")
                    .build());
        }

        // 确保有时间戳字段
        if (entity.isTimestamps()) {
            boolean hasCreatedAt = entity.getFields().stream().anyMatch(f -> "created_at".equals(f.getName()));
            boolean hasUpdatedAt = entity.getFields().stream().anyMatch(f -> "updated_at".equals(f.getName()));

            if (!hasCreatedAt) {
                entity.getFields().add(Field.builder()
                        .name("created_at")
                        .type(FieldType.TIMESTAMPTZ)
                        .nullable(false)
                        .defaultValue("NOW()")
                        .description("创建时间")
                        .build());
            }

            if (!hasUpdatedAt) {
                entity.getFields().add(Field.builder()
                        .name("updated_at")
                        .type(FieldType.TIMESTAMPTZ)
                        .nullable(false)
                        .defaultValue("NOW()")
                        .description("更新时间")
                        .build());
            }
        }

        // 确保有软删除字段
        if (entity.isSoftDelete()) {
            boolean hasDeletedAt = entity.getFields().stream().anyMatch(f -> "deleted_at".equals(f.getName()));
            if (!hasDeletedAt) {
                entity.getFields().add(Field.builder()
                        .name("deleted_at")
                        .type(FieldType.TIMESTAMPTZ)
                        .nullable(true)
                        .description("删除时间（软删除）")
                        .build());
            }
        }
    }

    /**
     * 推断关系类型（基于字段约束）
     */
    private RelationType inferRelationType(Field field, Entity entity) {
        // 如果外键字段有UNIQUE约束，则为1:1关系
        if (field.isUnique()) {
            return RelationType.ONE_TO_ONE;
        }

        // 默认为1:N关系
        return RelationType.ONE_TO_MANY;
    }

    /**
     * 推断多对多关系（基于命名模式）
     */
    private List<EntityRelationship> inferManyToManyRelationships(
            List<Entity> entities,
            Map<String, Entity> entityMap) {

        List<EntityRelationship> relationships = new ArrayList<>();

        for (Entity entity : entities) {
            String tableName = entity.getName();

            // 识别中间表模式：table1_table2（如user_roles, post_tags）
            String[] parts = tableName.split("_");
            if (parts.length == 2) {
                String table1Plural = parts[0] + "s"; // 假设单数形式，转复数
                String table2Plural = parts[1] + "s";

                // 检查两个表是否存在
                if (entityMap.containsKey(table1Plural) && entityMap.containsKey(table2Plural)) {
                    // 推断为多对多关系
                    relationships.add(EntityRelationship.builder()
                            .fromEntity(table1Plural)
                            .toEntity(table2Plural)
                            .type(RelationType.MANY_TO_MANY)
                            .foreignKeyField(parts[0] + "_id")
                            .junctionTable(tableName)
                            .targetForeignKeyField(parts[1] + "_id")
                            .build());

                    log.debug("[EntityAnalyzer] 推断多对多关系: {} N:M {} (通过{})",
                            table1Plural, table2Plural, tableName);
                }
            }
        }

        return relationships;
    }
}
