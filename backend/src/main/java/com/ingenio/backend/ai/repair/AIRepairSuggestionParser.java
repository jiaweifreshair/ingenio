package com.ingenio.backend.ai.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.ai.AIProvider;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI修复建议解析器
 *
 * 功能：
 * 1. 解析AI返回的JSON格式修复建议
 * 2. 处理JSON解析异常和格式错误
 * 3. 提取修复策略、建议列表、受影响文件等
 * 4. 清理Markdown标记（防止AI返回```json...```格式）
 *
 * 设计原则：
 * - 容错性强：优雅处理AI返回格式不规范的情况
 * - 类型安全：提供强类型的RepairSuggestion对象
 * - 可扩展性：支持未来新增字段
 * - 日志完整：详细记录解析过程和异常
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AIRepairSuggestionParser {

    /**
     * JSON序列化工具
     */
    private final ObjectMapper objectMapper;

    /**
     * 解析AI修复建议JSON
     *
     * @param aiResponseJson AI返回的JSON字符串
     * @return 解析后的修复建议对象
     * @throws AIProvider.AIException 当解析失败时抛出
     */
    public RepairSuggestion parse(String aiResponseJson) throws AIProvider.AIException {
        if (aiResponseJson == null || aiResponseJson.isBlank()) {
            throw new AIProvider.AIException(
                    "AI响应为空，无法解析修复建议",
                    "dashscope"
            );
        }

        try {
            // 1. 清理Markdown标记（防止AI返回```json...```格式）
            String cleanJson = cleanMarkdown(aiResponseJson);

            log.debug("清理后的JSON: {}", cleanJson);

            // 2. 解析JSON
            JsonNode rootNode = objectMapper.readTree(cleanJson);

            // 3. 提取修复策略（必需字段）
            String repairStrategy = rootNode.path("repairStrategy").asText();
            if (repairStrategy == null || repairStrategy.isBlank()) {
                throw new AIProvider.AIException(
                        "AI响应缺少repairStrategy字段",
                        "dashscope"
                );
            }

            // 4. 提取建议列表（核心字段）
            List<Map<String, Object>> suggestions = parseSuggestions(rootNode.path("suggestions"));

            // 5. 提取受影响文件列表
            List<String> affectedFiles = parseAffectedFiles(rootNode.path("affectedFiles"));

            // 6. 提取修复思路
            String reasoning = rootNode.path("reasoning").asText();

            // 7. 提取影响程度（可选字段）
            String estimatedImpact = rootNode.path("estimatedImpact").asText("medium");

            // 8. 构建RepairSuggestion对象
            RepairSuggestion suggestion = RepairSuggestion.builder()
                    .repairStrategy(repairStrategy)
                    .suggestions(suggestions)
                    .affectedFiles(affectedFiles)
                    .reasoning(reasoning)
                    .estimatedImpact(estimatedImpact)
                    .build();

            log.info("AI修复建议解析成功 - strategy: {}, suggestionCount: {}, affectedFileCount: {}",
                    repairStrategy, suggestions.size(), affectedFiles.size());

            return suggestion;

        } catch (AIProvider.AIException e) {
            // 重新抛出AIException
            throw e;
        } catch (Exception e) {
            log.error("解析AI修复建议失败 - json: {}", aiResponseJson, e);
            throw new AIProvider.AIException(
                    "解析AI修复建议失败: " + e.getMessage(),
                    "dashscope",
                    e
            );
        }
    }

    /**
     * 清理Markdown标记
     *
     * 处理以下格式：
     * - ```json\n{...}\n```
     * - ```\n{...}\n```
     * - {... }（正常JSON）
     *
     * @param text 原始文本
     * @return 清理后的JSON字符串
     */
    private String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        // 移除开头的```json或```
        String cleaned = text.replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "");

        // 移除结尾的```
        cleaned = cleaned.replaceAll("```\\s*$", "");

        // 去除首尾空白
        cleaned = cleaned.trim();

        log.debug("Markdown清理: {} chars -> {} chars", text.length(), cleaned.length());

        return cleaned;
    }

    /**
     * 解析建议列表
     *
     * @param suggestionsNode JSON节点
     * @return 建议列表
     */
    private List<Map<String, Object>> parseSuggestions(JsonNode suggestionsNode) {
        List<Map<String, Object>> suggestions = new ArrayList<>();

        if (suggestionsNode == null || !suggestionsNode.isArray()) {
            log.warn("AI响应缺少suggestions数组或格式不正确，返回空列表");
            return suggestions;
        }

        for (JsonNode suggestionNode : suggestionsNode) {
            Map<String, Object> suggestion = new HashMap<>();

            // 提取各个字段（使用容错性强的方法）
            putIfExists(suggestion, "file", suggestionNode.path("file").asText());
            putIfExists(suggestion, "lineNumber", suggestionNode.path("lineNumber").asInt(0));
            putIfExists(suggestion, "errorType", suggestionNode.path("errorType").asText());
            putIfExists(suggestion, "originalCode", suggestionNode.path("originalCode").asText());
            putIfExists(suggestion, "fixedCode", suggestionNode.path("fixedCode").asText());
            putIfExists(suggestion, "explanation", suggestionNode.path("explanation").asText());
            putIfExists(suggestion, "confidence", suggestionNode.path("confidence").asDouble(0.8));
            putIfExists(suggestion, "priority", suggestionNode.path("priority").asText("medium"));

            // 编译错误特有字段
            putIfExists(suggestion, "testName", suggestionNode.path("testName").asText());
            putIfExists(suggestion, "component", suggestionNode.path("component").asText());
            putIfExists(suggestion, "violationType", suggestionNode.path("violationType").asText());
            putIfExists(suggestion, "businessRule", suggestionNode.path("businessRule").asText());

            // 依赖管理特有字段
            putIfExists(suggestion, "packageManager", suggestionNode.path("packageManager").asText());
            putIfExists(suggestion, "action", suggestionNode.path("action").asText());
            putIfExists(suggestion, "packageName", suggestionNode.path("packageName").asText());
            putIfExists(suggestion, "version", suggestionNode.path("version").asText());
            putIfExists(suggestion, "command", suggestionNode.path("command").asText());
            putIfExists(suggestion, "reasoning", suggestionNode.path("reasoning").asText());

            suggestions.add(suggestion);
        }

        return suggestions;
    }

    /**
     * 解析受影响文件列表
     *
     * @param affectedFilesNode JSON节点
     * @return 文件路径列表
     */
    private List<String> parseAffectedFiles(JsonNode affectedFilesNode) {
        List<String> affectedFiles = new ArrayList<>();

        if (affectedFilesNode == null || !affectedFilesNode.isArray()) {
            log.warn("AI响应缺少affectedFiles数组或格式不正确，返回空列表");
            return affectedFiles;
        }

        for (JsonNode fileNode : affectedFilesNode) {
            String filePath = fileNode.asText();
            if (filePath != null && !filePath.isBlank()) {
                affectedFiles.add(filePath);
            }
        }

        return affectedFiles;
    }

    /**
     * 辅助方法：仅当值非空时放入Map
     *
     * @param map 目标Map
     * @param key 键
     * @param value 值
     */
    private void putIfExists(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            // 对于字符串，检查是否为空
            if (value instanceof String) {
                String strValue = (String) value;
                if (!strValue.isBlank()) {
                    map.put(key, strValue);
                }
            }
            // 对于数字，检查是否为默认值
            else if (value instanceof Integer) {
                Integer intValue = (Integer) value;
                if (intValue != 0) {
                    map.put(key, intValue);
                }
            }
            // 其他类型直接放入
            else {
                map.put(key, value);
            }
        }
    }

    /**
     * 修复建议对象（强类型）
     */
    @Data
    @Builder
    public static class RepairSuggestion {
        /**
         * 修复策略
         * 可能的值：
         * - type_inference（类型推断）
         * - import_fix（导入修复）
         * - syntax_fix（语法修复）
         * - interface_implementation（接口实现）
         * - api_path_fix（API路径修复）
         * - mock_data_fix（Mock数据修复）
         * - assertion_fix（断言修复）
         * - timing_fix（时序修复）
         * - validation_add（添加验证）
         * - constraint_relax（放宽约束）
         * - state_machine_fix（状态机修复）
         * - permission_add（添加权限）
         * - dependency_install（依赖安装）
         * - version_upgrade（版本升级）
         * - version_downgrade（版本降级）
         * - config_add（添加配置）
         * - generic_fix（通用修复）
         */
        private String repairStrategy;

        /**
         * 修复建议列表（每个建议对应一个具体的修改）
         */
        private List<Map<String, Object>> suggestions;

        /**
         * 受影响的文件列表
         */
        private List<String> affectedFiles;

        /**
         * 修复思路（AI的推理过程）
         */
        private String reasoning;

        /**
         * 预估影响程度
         * 可能的值：low（低）、medium（中）、high（高）
         */
        private String estimatedImpact;
    }
}
