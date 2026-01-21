package com.ingenio.backend.langchain4j.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.service.blueprint.BlueprintComplianceResult;
import com.ingenio.backend.service.blueprint.BlueprintValidator;
import dev.langchain4j.agent.tool.Tool;

import java.util.Map;

/**
 * Blueprint 校验工具封装。
 *
 * 是什么：BlueprintValidator 的 LangChain4j 工具适配器。
 * 做什么：校验 Schema 是否符合 Blueprint 约束。
 * 为什么：将约束校验能力纳入工具编排。
 */
public class BlueprintValidatorTool {

    /**
     * Blueprint 校验器。
     *
     * 是什么：现有 Blueprint 校验组件。
     * 做什么：执行 Schema 合规性校验。
     * 为什么：复用既有约束逻辑。
     */
    private final BlueprintValidator blueprintValidator;

    /**
     * JSON 序列化器。
     *
     * 是什么：ObjectMapper 实例。
     * 做什么：解析 Blueprint JSON 并序列化结果。
     * 为什么：支持工具输入输出标准化。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数。
     *
     * 是什么：Blueprint 校验工具初始化入口。
     * 做什么：注入校验器与 JSON 解析器。
     * 为什么：保证工具可解析输入并输出结果。
     *
     * @param blueprintValidator Blueprint 校验器
     * @param objectMapper JSON 解析器
     */
    public BlueprintValidatorTool(BlueprintValidator blueprintValidator, ObjectMapper objectMapper) {
        this.blueprintValidator = blueprintValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验 Schema 是否符合 Blueprint。
     *
     * 是什么：Blueprint 合规性校验入口。
     * 做什么：解析 Blueprint JSON 并执行校验。
     * 为什么：确保生成的 Schema 满足约束要求。
     *
     * @param schemaSql      Schema SQL
     * @param blueprintJson  Blueprint JSON
     * @return 校验结果 JSON
     */
    @Tool("校验数据库 Schema 是否满足 Blueprint 约束")
    public String validateSchema(String schemaSql, String blueprintJson) {
        Map<String, Object> blueprint = parseBlueprint(blueprintJson);
        BlueprintComplianceResult result = blueprintValidator.validateSchemaCompliance(schemaSql, blueprint);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"passed\":false,\"message\":\"Blueprint 校验结果序列化失败\"}";
        }
    }

    /**
     * 解析 Blueprint JSON。
     *
     * 是什么：Blueprint 解析方法。
     * 做什么：将 JSON 转换为 Map 结构。
     * 为什么：便于校验器消费 Blueprint 约束。
     *
     * @param raw JSON 字符串
     * @return Blueprint Map
     */
    private Map<String, Object> parseBlueprint(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
