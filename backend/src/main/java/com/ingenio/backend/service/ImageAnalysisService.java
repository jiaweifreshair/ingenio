package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 图像分析服务
 * 使用阿里云DashScope QianwenVL多模态大模型
 *
 * 功能：
 * 1. OCR文字识别
 * 2. UI元素检测
 * 3. 图像场景理解
 *
 * API文档: https://help.aliyun.com/zh/dashscope/developer-reference/qianwen-vl-api
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageAnalysisService {

    @Value("${DASHSCOPE_API_KEY:}")
    private String apiKey;

    private final ObjectMapper objectMapper;

    private static final String DASHSCOPE_VL_API = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String MODEL = "qwen-vl-plus"; // 使用qwen-vl-plus模型
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /**
     * 分析图像内容（OCR + UI元素检测 + 场景理解）
     *
     * @param imageUrl MinIO中的图像文件URL
     * @return 图像分析结果（JSON格式）
     */
    public ImageAnalysisResult analyze(String imageUrl) {
        log.info("开始图像分析: imageUrl={}", imageUrl);

        try {
            // 1. 调用QianwenVL API进行图像理解
            String analysisJson = callQianwenVL(imageUrl);

            // 2. 解析分析结果
            ImageAnalysisResult result = parseAnalysisResult(analysisJson);

            log.info("图像分析完成: imageUrl={}, ocrTextLength={}, uiElementsCount={}",
                    imageUrl,
                    result.getOcrText() != null ? result.getOcrText().length() : 0,
                    result.getUiElements() != null ? result.getUiElements().size() : 0);

            return result;

        } catch (Exception e) {
            log.error("图像分析失败: imageUrl={}", imageUrl, e);
            throw new RuntimeException("图像分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用QianwenVL API进行图像理解
     *
     * 使用多轮对话模式：
     * 1. 第一轮：识别图片中的所有文字（OCR）
     * 2. 第二轮：检测UI元素（按钮、输入框、图标等）
     * 3. 第三轮：理解图片场景和用途
     */
    private String callQianwenVL(String imageUrl) throws Exception {
        log.debug("调用QianwenVL API: imageUrl={}", imageUrl);

        // 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL);

        // 构造消息列表
        List<Map<String, Object>> messages = new ArrayList<>();

        // 用户消息：包含图片URL和分析指令
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();

        // 添加图片
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("image", imageUrl);
        content.add(imageContent);

        // 添加分析指令（综合三个任务）
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("text",
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
            "}"
        );
        content.add(textContent);

        userMessage.put("content", content);
        messages.add(userMessage);

        requestBody.put("messages", messages);

        // 发送请求
        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(DASHSCOPE_VL_API)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        // 执行请求
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new RuntimeException("QianwenVL API调用失败: " + response.code() + ", " + errorBody);
            }

            // 解析响应
            String responseBody = response.body().string();
            log.debug("QianwenVL API响应: {}", responseBody);

            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            // 提取生成的文本
            if (jsonResponse.has("output") &&
                jsonResponse.get("output").has("choices") &&
                jsonResponse.get("output").get("choices").isArray() &&
                jsonResponse.get("output").get("choices").size() > 0) {

                JsonNode firstChoice = jsonResponse.get("output").get("choices").get(0);
                if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                    String responseContent = firstChoice.get("message").get("content").asText();
                    return responseContent;
                }
            }

            throw new RuntimeException("QianwenVL API响应格式异常: " + responseBody);
        }
    }

    /**
     * 解析QianwenVL返回的分析结果
     *
     * 尝试解析JSON格式的响应，如果失败则使用纯文本模式
     */
    private ImageAnalysisResult parseAnalysisResult(String analysisJson) {
        ImageAnalysisResult result = new ImageAnalysisResult();

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
     * 检查API密钥是否配置
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("sk-placeholder");
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
