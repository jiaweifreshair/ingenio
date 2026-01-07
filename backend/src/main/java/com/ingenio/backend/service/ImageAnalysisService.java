package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.ai.JeecgBootMultiModalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 图像分析服务
 * 通过JeecgBoot网关调用七牛云多模态AI服务
 *
 * 功能：
 * 1. OCR文字识别
 * 2. UI元素检测
 * 3. 图像场景理解
 *
 * 架构说明：Ingenio -> JeecgBoot -> 七牛云AI
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnalysisService {

    private final JeecgBootMultiModalClient jeecgBootClient;
    private final ObjectMapper objectMapper;

    /**
     * 默认图像分析提示词
     * 要求AI返回结构化的JSON格式分析结果
     */
    private static final String DEFAULT_ANALYSIS_PROMPT =
            "请详细分析这张图片，提供以下信息：\n" +
            "1. OCR识别：提取图片中的所有文字内容\n" +
            "2. UI元素检测：识别按钮、输入框、图标、导航栏等UI组件\n" +
            "3. 场景理解：描述图片的用途、场景和整体设计风格\n\n" +
            "请以JSON格式返回，包含以下字段：\n" +
            "{\n" +
            "  \"ocr_text\": \"识别的所有文字\",\n" +
            "  \"ui_elements\": [\n" +
            "    {\"type\": \"button\", \"text\": \"按钮文字\", \"position\": \"位置描述\"},\n" +
            "    {\"type\": \"input\", \"placeholder\": \"输入框提示\", \"position\": \"位置描述\"}\n" +
            "  ],\n" +
            "  \"scene_description\": \"场景描述\",\n" +
            "  \"design_style\": \"设计风格\",\n" +
            "  \"purpose\": \"用途说明\"\n" +
            "}";

    /**
     * 分析图像内容（OCR + UI元素检测 + 场景理解）
     *
     * 通过JeecgBoot网关调用七牛云Vision API进行图像分析。
     * 不提供降级方案，JeecgBoot服务不可用时直接抛出异常。
     *
     * @param imageUrl MinIO中的图像文件URL
     * @return 图像分析结果（JSON格式）
     * @throws RuntimeException 如果JeecgBoot服务不可用或分析失败
     */
    public ImageAnalysisResult analyze(String imageUrl) {
        log.info("开始图像分析: imageUrl={}", imageUrl);

        try {
            // 调用JeecgBoot Vision API进行图像理解
            JeecgBootMultiModalClient.VisionAnalysisResult visionResult =
                    jeecgBootClient.analyzeImage(imageUrl, DEFAULT_ANALYSIS_PROMPT);

            if (!visionResult.isSuccess()) {
                throw new RuntimeException("JeecgBoot Vision API调用失败: " + visionResult.getErrorMessage());
            }

            // 解析分析结果
            ImageAnalysisResult result = parseAnalysisResult(visionResult.getContent());

            log.info("图像分析完成: imageUrl={}, ocrTextLength={}, uiElementsCount={}, durationMs={}",
                    imageUrl,
                    result.getOcrText() != null ? result.getOcrText().length() : 0,
                    result.getUiElements() != null ? result.getUiElements().size() : 0,
                    visionResult.getDurationMs());

            return result;

        } catch (Exception e) {
            log.error("图像分析失败: imageUrl={}", imageUrl, e);
            throw new RuntimeException("图像分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用自定义提示词分析图像
     *
     * @param imageUrl MinIO中的图像文件URL
     * @param customPrompt 自定义分析提示词
     * @return 原始分析内容（字符串）
     * @throws RuntimeException 如果分析失败
     */
    public String analyzeWithCustomPrompt(String imageUrl, String customPrompt) {
        log.info("使用自定义提示词分析图像: imageUrl={}", imageUrl);

        try {
            JeecgBootMultiModalClient.VisionAnalysisResult visionResult =
                    jeecgBootClient.analyzeImage(imageUrl, customPrompt);

            if (!visionResult.isSuccess()) {
                throw new RuntimeException("JeecgBoot Vision API调用失败: " + visionResult.getErrorMessage());
            }

            return visionResult.getContent();

        } catch (Exception e) {
            log.error("自定义图像分析失败: imageUrl={}", imageUrl, e);
            throw new RuntimeException("自定义图像分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析AI返回的分析结果
     *
     * 尝试解析JSON格式的响应，如果失败则使用纯文本模式
     */
    private ImageAnalysisResult parseAnalysisResult(String analysisJson) {
        ImageAnalysisResult result = new ImageAnalysisResult();

        if (analysisJson == null || analysisJson.isEmpty()) {
            log.warn("分析结果为空");
            return result;
        }

        try {
            // 尝试从Markdown代码块中提取JSON
            String jsonContent = analysisJson;
            if (analysisJson.contains("```json")) {
                int start = analysisJson.indexOf("```json") + 7;
                int end = analysisJson.lastIndexOf("```");
                if (end > start) {
                    jsonContent = analysisJson.substring(start, end).trim();
                }
            } else if (analysisJson.contains("```")) {
                int start = analysisJson.indexOf("```") + 3;
                int end = analysisJson.lastIndexOf("```");
                if (end > start) {
                    jsonContent = analysisJson.substring(start, end).trim();
                }
            }

            // 解析JSON
            JsonNode json = objectMapper.readTree(jsonContent);

            // 提取OCR文本
            if (json.has("ocr_text")) {
                result.setOcrText(json.get("ocr_text").asText());
            }

            // 提取UI元素
            if (json.has("ui_elements") && json.get("ui_elements").isArray()) {
                List<UIElement> uiElements = new ArrayList<>();
                for (JsonNode element : json.get("ui_elements")) {
                    UIElement uiElement = new UIElement();
                    if (element.has("type")) {
                        uiElement.setType(element.get("type").asText());
                    }
                    if (element.has("text")) {
                        uiElement.setText(element.get("text").asText());
                    }
                    if (element.has("placeholder")) {
                        uiElement.setPlaceholder(element.get("placeholder").asText());
                    }
                    if (element.has("position")) {
                        uiElement.setPosition(element.get("position").asText());
                    }
                    uiElements.add(uiElement);
                }
                result.setUiElements(uiElements);
            }

            // 提取场景描述
            if (json.has("scene_description")) {
                result.setSceneDescription(json.get("scene_description").asText());
            }

            // 提取设计风格
            if (json.has("design_style")) {
                result.setDesignStyle(json.get("design_style").asText());
            }

            // 提取用途
            if (json.has("purpose")) {
                result.setPurpose(json.get("purpose").asText());
            }

        } catch (Exception e) {
            log.warn("解析JSON格式失败，使用纯文本模式: {}", e.getMessage());
            // 如果JSON解析失败，将整个响应作为场景描述
            result.setSceneDescription(analysisJson);
            result.setOcrText(""); // 空字符串表示未识别到文字
            result.setUiElements(new ArrayList<>());
        }

        return result;
    }

    /**
     * 检查服务是否可用
     *
     * 检查JeecgBoot多模态服务是否可用
     *
     * @return true如果服务可用
     */
    public boolean isConfigured() {
        return jeecgBootClient.isAvailable();
    }

    /**
     * 图像分析结果
     */
    public static class ImageAnalysisResult {
        private String ocrText;                    // OCR识别的文字
        private List<UIElement> uiElements;         // UI元素列表
        private String sceneDescription;            // 场景描述
        private String designStyle;                 // 设计风格
        private String purpose;                     // 用途

        public ImageAnalysisResult() {
            this.uiElements = new ArrayList<>();
        }

        // Getters and Setters
        public String getOcrText() {
            return ocrText;
        }

        public void setOcrText(String ocrText) {
            this.ocrText = ocrText;
        }

        public List<UIElement> getUiElements() {
            return uiElements;
        }

        public void setUiElements(List<UIElement> uiElements) {
            this.uiElements = uiElements;
        }

        public String getSceneDescription() {
            return sceneDescription;
        }

        public void setSceneDescription(String sceneDescription) {
            this.sceneDescription = sceneDescription;
        }

        public String getDesignStyle() {
            return designStyle;
        }

        public void setDesignStyle(String designStyle) {
            this.designStyle = designStyle;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }

        /**
         * 转换为Map对象（用于存储到数据库的JSONB字段）
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("ocr_text", ocrText);
            map.put("scene_description", sceneDescription);
            map.put("design_style", designStyle);
            map.put("purpose", purpose);

            if (uiElements != null && !uiElements.isEmpty()) {
                List<Map<String, Object>> elementsMap = new ArrayList<>();
                for (UIElement element : uiElements) {
                    Map<String, Object> elementMap = new HashMap<>();
                    elementMap.put("type", element.getType());
                    elementMap.put("text", element.getText());
                    elementMap.put("placeholder", element.getPlaceholder());
                    elementMap.put("position", element.getPosition());
                    elementsMap.add(elementMap);
                }
                map.put("ui_elements", elementsMap);
            }

            return map;
        }

        /**
         * 转换为JSON字符串（用于日志和调试）
         */
        public String toJson() {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.writeValueAsString(toMap());
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }
    }

    /**
     * UI元素
     */
    public static class UIElement {
        private String type;           // 元素类型：button, input, icon, image, text, etc.
        private String text;           // 元素文字
        private String placeholder;    // 输入框提示
        private String position;       // 位置描述

        // Getters and Setters
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getPlaceholder() {
            return placeholder;
        }

        public void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }
    }
}
