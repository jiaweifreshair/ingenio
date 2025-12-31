package com.ingenio.backend.service.blueprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.blueprint.BlueprintSpec;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Blueprint 合规性验证器
 *
 * 当前范围（V3）：
 * - 校验 Architect 生成的 PostgreSQL DDL 是否包含 Blueprint schema 中定义的表与核心字段
 *
 * 注意：
 * - 允许生成结果包含额外字段/索引/约束
 * - 不做过度严格的类型等价判断（避免 TIMESTAMP vs TIMESTAMPTZ 等误报）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlueprintValidator {

    private final ObjectMapper objectMapper;

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(
            "@TableName\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TABLE_FIELD_PATTERN = Pattern.compile(
            "@TableField\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TABLE_ID_PATTERN = Pattern.compile(
            "@TableId\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JAVA_FIELD_DECL_PATTERN = Pattern.compile(
            "(?m)^\\s*private\\s+[^;=]+\\s+(\\w+)\\s*(?:=|;)"
    );

    /**
     * 校验 DDL 是否符合 Blueprint schema
     *
     * @param dbSchemaSql   Architect 生成的 DDL SQL
     * @param blueprintSpec Blueprint 规范（JSONB Map）
     * @return 合规性结果
     */
    public BlueprintComplianceResult validateSchemaCompliance(String dbSchemaSql, Map<String, Object> blueprintSpec) {
        BlueprintSpec spec = parse(blueprintSpec);
        if (spec == null || spec.getSchema() == null || spec.getSchema().isEmpty()) {
            return BlueprintComplianceResult.passedResult();
        }

        if (dbSchemaSql == null || dbSchemaSql.isBlank()) {
            return BlueprintComplianceResult.failedResult(List.of("DDL 为空，无法进行 Blueprint 合规性校验"));
        }

        List<String> violations = new ArrayList<>();

        for (BlueprintSpec.SchemaTable table : spec.getSchema()) {
            if (table == null || isBlank(table.getTableName())) {
                continue;
            }

            String tableName = table.getTableName().trim();
            String createTableBody = extractCreateTableBody(dbSchemaSql, tableName);

            if (createTableBody == null) {
                violations.add("缺少必需表: " + tableName);
                continue;
            }

            Set<String> actualColumns = extractColumnNames(createTableBody);

            for (BlueprintSpec.SchemaColumn col : nullSafe(table.getColumns())) {
                if (col == null || isBlank(col.getName())) {
                    continue;
                }

                // 兼容复合主键占位写法：{"name":"PRIMARY KEY","type":"COMPOSITE",...}
                if ("PRIMARY KEY".equalsIgnoreCase(col.getName())) {
                    continue;
                }

                String requiredColumn = col.getName().trim();
                if (!actualColumns.contains(requiredColumn)) {
                    violations.add("表 " + tableName + " 缺少必需字段: " + requiredColumn);
                    continue;
                }

                // 仅对 UUID 做轻量类型校验（减少误报）
                if ("UUID".equalsIgnoreCase(safeUpper(col.getType()))
                        && !containsColumnType(createTableBody, requiredColumn, "uuid")) {
                    violations.add("表 " + tableName + " 字段类型不符合 UUID 约束: " + requiredColumn);
                }
            }
        }

        return violations.isEmpty()
                ? BlueprintComplianceResult.passedResult()
                : BlueprintComplianceResult.failedResult(violations);
    }

    /**
     * 校验后端代码产物是否符合 Blueprint schema（F4 最小版）
     *
     * 当前策略：
     * - 仅校验“表 → Entity”是否存在，以及 Entity 是否覆盖 Blueprint columns
     * - 不做过度严格的类型等价判断（避免 UUID/Instant/LocalDateTime 等映射差异引起误报）
     *
     * 约束来源：
     * - 通过 MyBatis-Plus 的 @TableName / @TableField / @TableId 来建立表与字段映射
     * - 若字段未显式标注 @TableField，则尝试用 snake_case → camelCase 推导字段名进行兜底匹配
     *
     * @param artifacts     后端生成的代码产物列表
     * @param blueprintSpec Blueprint 规范（JSONB Map）
     * @return 合规性结果
     */
    public BlueprintComplianceResult validateBackendArtifactsCompliance(
            List<G3ArtifactEntity> artifacts,
            Map<String, Object> blueprintSpec) {

        BlueprintSpec spec = parse(blueprintSpec);
        if (spec == null || spec.getSchema() == null || spec.getSchema().isEmpty()) {
            return BlueprintComplianceResult.passedResult();
        }

        if (artifacts == null || artifacts.isEmpty()) {
            return BlueprintComplianceResult.failedResult(List.of("代码产物为空，无法进行 Blueprint 合规性校验"));
        }

        Map<String, G3ArtifactEntity> entityByTable = indexEntitiesByTableName(artifacts);
        List<String> violations = new ArrayList<>();

        for (BlueprintSpec.SchemaTable table : spec.getSchema()) {
            if (table == null || isBlank(table.getTableName())) {
                continue;
            }

            String tableName = table.getTableName().trim();
            G3ArtifactEntity entityArtifact = entityByTable.get(tableName.toLowerCase(Locale.ROOT));

            if (entityArtifact == null) {
                violations.add("缺少 Entity 映射: " + tableName);
                continue;
            }

            EntityColumnIndex index = buildEntityColumnIndex(entityArtifact.getContent());

            for (BlueprintSpec.SchemaColumn col : nullSafe(table.getColumns())) {
                if (col == null || isBlank(col.getName())) {
                    continue;
                }

                // 兼容复合主键占位写法：{"name":"PRIMARY KEY","type":"COMPOSITE",...}
                if ("PRIMARY KEY".equalsIgnoreCase(col.getName())) {
                    continue;
                }

                String requiredColumn = col.getName().trim();
                if (!index.hasColumn(requiredColumn)) {
                    violations.add("表 " + tableName + " 缺少字段映射: " + requiredColumn
                            + "（Entity: " + entityArtifact.getFileName() + "）");
                }
            }
        }

        return violations.isEmpty()
                ? BlueprintComplianceResult.passedResult()
                : BlueprintComplianceResult.failedResult(violations);
    }

    private BlueprintSpec parse(Map<String, Object> blueprintSpec) {
        if (blueprintSpec == null || blueprintSpec.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.convertValue(blueprintSpec, BlueprintSpec.class);
        } catch (Exception e) {
            log.warn("BlueprintSpec 解析失败，已跳过合规性校验: {}", e.getMessage());
            return null;
        }
    }

    private String extractCreateTableBody(String sql, String tableName) {
        Pattern pattern = Pattern.compile(
                "(?is)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?"
                        + Pattern.quote(tableName)
                        + "\\s*\\((.*?)\\)\\s*;",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(sql);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private Map<String, G3ArtifactEntity> indexEntitiesByTableName(List<G3ArtifactEntity> artifacts) {
        Map<String, G3ArtifactEntity> map = new HashMap<>();
        for (G3ArtifactEntity artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            String content = artifact.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }

            String tableName = extractTableNameFromMybatisEntity(content);
            if (tableName == null || tableName.isBlank()) {
                continue;
            }

            // 若存在重复，以“最新版本”优先（generationRound 更大）
            String key = tableName.trim().toLowerCase(Locale.ROOT);
            G3ArtifactEntity existed = map.get(key);
            if (existed == null || safeInt(existed.getGenerationRound()) <= safeInt(artifact.getGenerationRound())) {
                map.put(key, artifact);
            }
        }
        return map;
    }

    private String extractTableNameFromMybatisEntity(String javaSource) {
        Matcher matcher = TABLE_NAME_PATTERN.matcher(javaSource);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private EntityColumnIndex buildEntityColumnIndex(String javaSource) {
        Set<String> explicitColumnsLower = new HashSet<>();
        Set<String> declaredFields = new HashSet<>();

        if (javaSource == null || javaSource.isBlank()) {
            return new EntityColumnIndex(explicitColumnsLower, declaredFields);
        }

        Matcher fieldMatcher = TABLE_FIELD_PATTERN.matcher(javaSource);
        while (fieldMatcher.find()) {
            String col = fieldMatcher.group(1);
            if (col != null && !col.isBlank()) {
                explicitColumnsLower.add(col.trim().toLowerCase(Locale.ROOT));
            }
        }

        Matcher idMatcher = TABLE_ID_PATTERN.matcher(javaSource);
        while (idMatcher.find()) {
            String col = idMatcher.group(1);
            if (col != null && !col.isBlank()) {
                explicitColumnsLower.add(col.trim().toLowerCase(Locale.ROOT));
            }
        }

        Matcher javaFieldMatcher = JAVA_FIELD_DECL_PATTERN.matcher(javaSource);
        while (javaFieldMatcher.find()) {
            String fieldName = javaFieldMatcher.group(1);
            if (fieldName != null && !fieldName.isBlank()) {
                declaredFields.add(fieldName.trim());
            }
        }

        return new EntityColumnIndex(explicitColumnsLower, declaredFields);
    }

    private record EntityColumnIndex(
            Set<String> explicitColumnsLower,
            Set<String> declaredFields
    ) {
        boolean hasColumn(String columnName) {
            if (columnName == null || columnName.isBlank()) {
                return false;
            }

            String normalized = columnName.trim();
            String normalizedLower = normalized.toLowerCase(Locale.ROOT);
            if (explicitColumnsLower.contains(normalizedLower)) {
                return true;
            }

            // 兜底：允许 snake_case → camelCase 映射（例如 created_at → createdAt）
            String camel = toLowerCamelCase(normalized);
            return camel != null && declaredFields.contains(camel);
        }
    }

    private static String toLowerCamelCase(String snakeOrKebab) {
        if (snakeOrKebab == null) {
            return null;
        }
        String v = snakeOrKebab.trim();
        if (v.isEmpty()) {
            return null;
        }

        // 若本身不包含分隔符，直接返回（可能已经是 camelCase）
        if (!v.contains("_") && !v.contains("-")) {
            return v;
        }

        String[] parts = v.split("[_\\-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (sb.length() == 0) {
                sb.append(p);
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1));
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private Set<String> extractColumnNames(String createTableBody) {
        Set<String> columns = new HashSet<>();
        if (createTableBody == null || createTableBody.isBlank()) {
            return columns;
        }

        for (String rawLine : createTableBody.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 去掉行尾逗号
            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1).trim();
            }

            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("CONSTRAINT")
                    || upper.startsWith("PRIMARY KEY")
                    || upper.startsWith("FOREIGN KEY")
                    || upper.startsWith("UNIQUE")
                    || upper.startsWith("CHECK")) {
                continue;
            }

            // 取第一个token作为列名（兼容引号）
            String[] parts = line.split("\\s+");
            if (parts.length == 0) {
                continue;
            }
            String colName = stripQuotes(parts[0]);
            if (!colName.isEmpty()) {
                columns.add(colName);
            }
        }

        return columns;
    }

    private boolean containsColumnType(String createTableBody, String columnName, String expectedTypeLower) {
        Pattern pattern = Pattern.compile(
                "(?is)\\b" + Pattern.quote(columnName) + "\\b\\s+" + Pattern.quote(expectedTypeLower) + "\\b",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        return pattern.matcher(createTableBody).find();
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "")
                .replace("`", "")
                .replace("'", "")
                .trim();
    }

    private static String safeUpper(String value) {
        return value != null ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
