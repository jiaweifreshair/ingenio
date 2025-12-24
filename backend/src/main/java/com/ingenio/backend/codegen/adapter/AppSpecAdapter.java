package com.ingenio.backend.codegen.adapter;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import com.ingenio.backend.dto.request.GenerateFullRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AppSpec适配器
 *
 * <p>将通用的Map<String, Object> AppSpec转换为强类型的List<Entity>模型</p>
 * <p>用于桥接ExecuteAgent输出和CodeGenerator输入</p>
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AppSpecAdapter {

    /**
     * 将AppSpec转换为实体列表
     *
     * @param appSpecJson AppSpec JSON Map
     * @return 实体列表
     */
    @SuppressWarnings("unchecked")
    public List<Entity> adapt(Map<String, Object> appSpecJson) {
        List<Entity> entities = new ArrayList<>();

        if (appSpecJson == null || !appSpecJson.containsKey("dataModels")) {
            log.warn("AppSpec不包含dataModels字段，返回空实体列表");
            return entities;
        }

        try {
            Object dataModelsObj = appSpecJson.get("dataModels");
            if (dataModelsObj instanceof List) {
                List<Map<String, Object>> dataModels = (List<Map<String, Object>>) dataModelsObj;
                for (Map<String, Object> modelMap : dataModels) {
                    entities.add(mapToEntity(modelMap));
                }
            }
        } catch (Exception e) {
            log.error("解析AppSpec dataModels失败", e);
        }

        return entities;
    }

    private Entity mapToEntity(Map<String, Object> map) {
        String name = (String) map.get("tableName"); // Assuming AI outputs 'tableName'
        if (name == null) name = (String) map.get("name");
        
        String description = (String) map.get("description");
        
        List<Field> fields = new ArrayList<>();
        if (map.containsKey("fields")) {
            List<Map<String, Object>> fieldsList = (List<Map<String, Object>>) map.get("fields");
            for (Map<String, Object> fieldMap : fieldsList) {
                fields.add(mapToField(fieldMap));
            }
        }

        return Entity.builder()
                .name(name)
                .description(description)
                .fields(fields)
                .timestamps(true) // Default to true
                .build();
    }

    private Field mapToField(Map<String, Object> map) {
        String name = (String) map.get("columnName");
        if (name == null) name = (String) map.get("name");
        
        String typeStr = (String) map.get("type"); // e.g., "VARCHAR", "UUID"
        FieldType type = mapType(typeStr);
        
        String description = (String) map.get("description");
        Boolean primaryKey = (Boolean) map.get("primaryKey");
        Boolean nullable = (Boolean) map.get("nullable");

        return Field.builder()
                .name(name)
                .type(type)
                .description(description)
                .primaryKey(Boolean.TRUE.equals(primaryKey))
                .nullable(nullable != null ? nullable : true)
                .build();
    }

    private FieldType mapType(String typeStr) {
        if (typeStr == null) return FieldType.VARCHAR;
        try {
            return FieldType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Fallback mapping
            if (typeStr.equalsIgnoreCase("STRING")) return FieldType.VARCHAR;
            if (typeStr.equalsIgnoreCase("INT")) return FieldType.INTEGER;
            if (typeStr.equalsIgnoreCase("LONG")) return FieldType.BIGINT;
            if (typeStr.equalsIgnoreCase("BOOL")) return FieldType.BOOLEAN;
            if (typeStr.equalsIgnoreCase("DATETIME")) return FieldType.TIMESTAMPTZ;
            return FieldType.VARCHAR;
        }
    }

    /**
     * 提取应用名称
     */
    public static String extractAppName(Map<String, Object> appSpecJson) {
        if (appSpecJson != null && appSpecJson.containsKey("appName")) {
            return (String) appSpecJson.get("appName");
        }
        return "GeneratedApp";
    }

    /**
     * 提取包名
     */
    public static String extractPackageName(Map<String, Object> appSpecJson, GenerateFullRequest request) {
        if (request != null && request.getPackageName() != null && !request.getPackageName().isEmpty()) {
            return request.getPackageName();
        }
        if (appSpecJson != null && appSpecJson.containsKey("packageName")) {
            return (String) appSpecJson.get("packageName");
        }
        String appName = extractAppName(appSpecJson);
        String cleanName = appName.toLowerCase().replaceAll("[^a-z0-9]", "");
        return "com.ingenio.generated." + cleanName;
    }
}
