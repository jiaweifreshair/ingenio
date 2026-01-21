package com.ingenio.backend.ai.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.ai.AIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI修复建议解析器
 */
@Component
public class AIRepairSuggestionParser {

    private static final Logger log = LoggerFactory.getLogger(AIRepairSuggestionParser.class);

    private final ObjectMapper objectMapper;

    public AIRepairSuggestionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RepairSuggestion parse(String aiResponseJson) throws AIProvider.AIException {
        if (aiResponseJson == null || aiResponseJson.isBlank()) {
            throw new AIProvider.AIException(
                    "AI响应为空，无法解析修复建议",
                    "dashscope");
        }

        try {
            String cleanJson = cleanMarkdown(aiResponseJson);
            log.debug("清理后的JSON: {}", cleanJson);

            JsonNode rootNode = objectMapper.readTree(cleanJson);

            String repairStrategy = rootNode.path("repairStrategy").asText();
            if (repairStrategy == null || repairStrategy.isBlank()) {
                throw new AIProvider.AIException(
                        "AI响应缺少repairStrategy字段",
                        "dashscope");
            }

            List<Map<String, Object>> suggestions = parseSuggestions(rootNode.path("suggestions"));
            List<String> affectedFiles = parseAffectedFiles(rootNode.path("affectedFiles"));
            String reasoning = rootNode.path("reasoning").asText();
            String estimatedImpact = rootNode.path("estimatedImpact").asText("medium");

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
            throw e;
        } catch (Exception e) {
            log.error("解析AI修复建议失败 - json: {}", aiResponseJson, e);
            throw new AIProvider.AIException(
                    "解析AI修复建议失败: " + e.getMessage(),
                    "dashscope",
                    e);
        }
    }

    private String cleanMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String cleaned = text.replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "");
        cleaned = cleaned.replaceAll("```\\s*$", "");
        cleaned = cleaned.trim();
        log.debug("Markdown清理: {} chars -> {} chars", text.length(), cleaned.length());
        return cleaned;
    }

    private List<Map<String, Object>> parseSuggestions(JsonNode suggestionsNode) {
        List<Map<String, Object>> suggestions = new ArrayList<>();

        if (suggestionsNode == null || !suggestionsNode.isArray()) {
            log.warn("AI响应缺少suggestions数组或格式不正确，返回空列表");
            return suggestions;
        }

        for (JsonNode suggestionNode : suggestionsNode) {
            Map<String, Object> suggestion = new HashMap<>();

            putIfExists(suggestion, "file", suggestionNode.path("file").asText());
            putIfExists(suggestion, "lineNumber", suggestionNode.path("lineNumber").asInt(0));
            putIfExists(suggestion, "errorType", suggestionNode.path("errorType").asText());
            putIfExists(suggestion, "originalCode", suggestionNode.path("originalCode").asText());
            putIfExists(suggestion, "fixedCode", suggestionNode.path("fixedCode").asText());
            putIfExists(suggestion, "explanation", suggestionNode.path("explanation").asText());
            putIfExists(suggestion, "confidence", suggestionNode.path("confidence").asDouble(0.8));
            putIfExists(suggestion, "priority", suggestionNode.path("priority").asText("medium"));

            putIfExists(suggestion, "testName", suggestionNode.path("testName").asText());
            putIfExists(suggestion, "component", suggestionNode.path("component").asText());
            putIfExists(suggestion, "violationType", suggestionNode.path("violationType").asText());
            putIfExists(suggestion, "businessRule", suggestionNode.path("businessRule").asText());

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

    private void putIfExists(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            if (value instanceof String) {
                String strValue = (String) value;
                if (!strValue.isBlank()) {
                    map.put(key, strValue);
                }
            } else if (value instanceof Integer) {
                Integer intValue = (Integer) value;
                if (intValue != 0) {
                    map.put(key, intValue);
                }
            } else {
                map.put(key, value);
            }
        }
    }

    public static class RepairSuggestion {
        private String repairStrategy;
        private List<Map<String, Object>> suggestions;
        private List<String> affectedFiles;
        private String reasoning;
        private String estimatedImpact;

        public RepairSuggestion() {
        }

        public RepairSuggestion(String repairStrategy, List<Map<String, Object>> suggestions,
                List<String> affectedFiles, String reasoning, String estimatedImpact) {
            this.repairStrategy = repairStrategy;
            this.suggestions = suggestions;
            this.affectedFiles = affectedFiles;
            this.reasoning = reasoning;
            this.estimatedImpact = estimatedImpact;
        }

        public static RepairSuggestionBuilder builder() {
            return new RepairSuggestionBuilder();
        }

        public static class RepairSuggestionBuilder {
            private String repairStrategy;
            private List<Map<String, Object>> suggestions;
            private List<String> affectedFiles;
            private String reasoning;
            private String estimatedImpact;

            public RepairSuggestionBuilder repairStrategy(String repairStrategy) {
                this.repairStrategy = repairStrategy;
                return this;
            }

            public RepairSuggestionBuilder suggestions(List<Map<String, Object>> suggestions) {
                this.suggestions = suggestions;
                return this;
            }

            public RepairSuggestionBuilder affectedFiles(List<String> affectedFiles) {
                this.affectedFiles = affectedFiles;
                return this;
            }

            public RepairSuggestionBuilder reasoning(String reasoning) {
                this.reasoning = reasoning;
                return this;
            }

            public RepairSuggestionBuilder estimatedImpact(String estimatedImpact) {
                this.estimatedImpact = estimatedImpact;
                return this;
            }

            public RepairSuggestion build() {
                return new RepairSuggestion(repairStrategy, suggestions, affectedFiles, reasoning, estimatedImpact);
            }
        }

        public String getRepairStrategy() {
            return repairStrategy;
        }

        public void setRepairStrategy(String repairStrategy) {
            this.repairStrategy = repairStrategy;
        }

        public List<Map<String, Object>> getSuggestions() {
            return suggestions;
        }

        public void setSuggestions(List<Map<String, Object>> suggestions) {
            this.suggestions = suggestions;
        }

        public List<String> getAffectedFiles() {
            return affectedFiles;
        }

        public void setAffectedFiles(List<String> affectedFiles) {
            this.affectedFiles = affectedFiles;
        }

        public String getReasoning() {
            return reasoning;
        }

        public void setReasoning(String reasoning) {
            this.reasoning = reasoning;
        }

        public String getEstimatedImpact() {
            return estimatedImpact;
        }

        public void setEstimatedImpact(String estimatedImpact) {
            this.estimatedImpact = estimatedImpact;
        }
    }
}
