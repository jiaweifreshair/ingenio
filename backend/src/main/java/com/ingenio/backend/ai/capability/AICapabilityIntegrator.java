package com.ingenio.backend.ai.capability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * AI能力自动集成服务
 *
 * <p>根据用户需求自动识别并集成所需的AI能力：</p>
 * <ul>
 *   <li>NLP - 自然语言处理（文本分类、情感分析、实体识别）</li>
 *   <li>Vision - 计算机视觉（图像分类、OCR、人脸识别）</li>
 *   <li>Speech - 语音处理（语音识别、语音合成）</li>
 *   <li>Recommendation - 推荐系统（协同过滤、内容推荐）</li>
 *   <li>Translation - 机器翻译（多语言翻译）</li>
 *   <li>Chat - 对话AI（智能客服、问答系统）</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-20 V2.0 AI能力集成
 */
@Slf4j
@Component
public class AICapabilityIntegrator {

    /**
     * AI能力类型枚举
     */
    public enum AICapability {
        /** 自然语言处理 */
        NLP("NLP", "自然语言处理", "文本分类、情感分析、实体识别、关键词提取"),
        /** 计算机视觉 */
        VISION("Vision", "计算机视觉", "图像分类、目标检测、OCR、人脸识别"),
        /** 语音处理 */
        SPEECH("Speech", "语音处理", "语音识别ASR、语音合成TTS"),
        /** 推荐系统 */
        RECOMMENDATION("Recommendation", "推荐系统", "协同过滤、内容推荐、个性化排序"),
        /** 机器翻译 */
        TRANSLATION("Translation", "机器翻译", "多语言翻译、实时翻译"),
        /** 对话AI */
        CHAT("Chat", "对话AI", "智能客服、问答系统、多轮对话"),
        /** 代码生成 */
        CODE_GENERATION("CodeGeneration", "代码生成", "代码补全、代码解释、代码重构"),
        /** 内容生成 */
        CONTENT_GENERATION("ContentGeneration", "内容生成", "文章生成、摘要生成、标题生成");

        private final String code;
        private final String displayName;
        private final String description;

        AICapability(String code, String displayName, String description) {
            this.code = code;
            this.displayName = displayName;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * AI能力检测结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AICapabilityDetectionResult {
        /** 检测到的AI能力列表 */
        private List<DetectedCapability> detectedCapabilities;
        /** 总置信度 */
        private double overallConfidence;
        /** 推荐的集成方案 */
        private IntegrationPlan integrationPlan;
        /** 分析耗时(ms) */
        private long durationMs;
    }

    /**
     * 检测到的单个AI能力
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedCapability {
        /** AI能力类型 */
        private AICapability capability;
        /** 置信度(0-1) */
        private double confidence;
        /** 匹配的关键词 */
        private List<String> matchedKeywords;
        /** 推荐的API提供商 */
        private String recommendedProvider;
        /** 预估Token消耗/请求 */
        private int estimatedTokensPerRequest;
        /** 预估成本(元/1000请求) */
        private double estimatedCostPer1000;
    }

    /**
     * 集成方案
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntegrationPlan {
        /** 需要添加的依赖 */
        private List<String> dependencies;
        /** 需要配置的环境变量 */
        private List<String> envVariables;
        /** 生成的配置代码 */
        private String configCode;
        /** 生成的服务代码 */
        private Map<String, String> serviceCodes;
        /** 预估总成本(元/月) */
        private double estimatedMonthlyCost;
    }

    // ==================== 关键词定义 ====================

    /** NLP关键词 */
    private static final Set<String> NLP_KEYWORDS = Set.of(
        "文本分析", "情感分析", "文本分类", "实体识别", "关键词提取",
        "分词", "词性标注", "命名实体", "文本摘要", "文本相似度",
        "sentiment", "nlp", "text analysis", "entity recognition", "keyword extraction"
    );

    /** Vision关键词 */
    private static final Set<String> VISION_KEYWORDS = Set.of(
        "图像识别", "图片分类", "目标检测", "人脸识别", "OCR",
        "图像分析", "物体识别", "人脸检测", "证件识别", "车牌识别",
        "image", "vision", "ocr", "face detection", "object detection"
    );

    /** Speech关键词 */
    private static final Set<String> SPEECH_KEYWORDS = Set.of(
        "语音识别", "语音合成", "语音转文字", "文字转语音", "语音助手",
        "ASR", "TTS", "语音交互", "语音输入",
        "speech", "voice", "asr", "tts", "speech recognition"
    );

    /** Recommendation关键词 */
    private static final Set<String> RECOMMENDATION_KEYWORDS = Set.of(
        "推荐系统", "个性化推荐", "智能推荐", "协同过滤", "猜你喜欢",
        "商品推荐", "内容推荐", "相关推荐", "为你推荐",
        "recommendation", "personalized", "collaborative filtering"
    );

    /** Translation关键词 */
    private static final Set<String> TRANSLATION_KEYWORDS = Set.of(
        "翻译", "机器翻译", "多语言", "中英翻译", "实时翻译",
        "translation", "translate", "multilingual", "i18n"
    );

    /** Chat关键词 */
    private static final Set<String> CHAT_KEYWORDS = Set.of(
        "智能客服", "聊天机器人", "对话系统", "问答系统", "智能助手",
        "在线客服", "自动回复", "FAQ", "多轮对话",
        "chatbot", "chat", "qa", "customer service", "assistant"
    );

    /** Code Generation关键词 */
    private static final Set<String> CODE_GEN_KEYWORDS = Set.of(
        "代码生成", "代码补全", "智能编程", "代码助手", "自动编码",
        "code generation", "code completion", "copilot"
    );

    /** Content Generation关键词 */
    private static final Set<String> CONTENT_GEN_KEYWORDS = Set.of(
        "文章生成", "内容生成", "AI写作", "自动写作", "文案生成",
        "标题生成", "摘要生成", "AI创作",
        "content generation", "ai writing", "copywriting"
    );

    /**
     * 分析需求并检测所需的AI能力
     *
     * @param requirement 用户需求描述
     * @param features 功能列表
     * @return AI能力检测结果
     */
    public AICapabilityDetectionResult analyzeAndDetect(String requirement, List<String> features) {
        log.info("[AICapabilityIntegrator] 开始AI能力检测...");
        long startTime = System.currentTimeMillis();

        // 合并所有文本
        String fullText = buildFullText(requirement, features);
        String lowerText = fullText.toLowerCase();

        // 检测各种AI能力
        List<DetectedCapability> detectedCapabilities = new ArrayList<>();

        // NLP检测
        DetectedCapability nlpCapability = detectCapability(lowerText, AICapability.NLP, NLP_KEYWORDS);
        if (nlpCapability != null) detectedCapabilities.add(nlpCapability);

        // Vision检测
        DetectedCapability visionCapability = detectCapability(lowerText, AICapability.VISION, VISION_KEYWORDS);
        if (visionCapability != null) detectedCapabilities.add(visionCapability);

        // Speech检测
        DetectedCapability speechCapability = detectCapability(lowerText, AICapability.SPEECH, SPEECH_KEYWORDS);
        if (speechCapability != null) detectedCapabilities.add(speechCapability);

        // Recommendation检测
        DetectedCapability recCapability = detectCapability(lowerText, AICapability.RECOMMENDATION, RECOMMENDATION_KEYWORDS);
        if (recCapability != null) detectedCapabilities.add(recCapability);

        // Translation检测
        DetectedCapability transCapability = detectCapability(lowerText, AICapability.TRANSLATION, TRANSLATION_KEYWORDS);
        if (transCapability != null) detectedCapabilities.add(transCapability);

        // Chat检测
        DetectedCapability chatCapability = detectCapability(lowerText, AICapability.CHAT, CHAT_KEYWORDS);
        if (chatCapability != null) detectedCapabilities.add(chatCapability);

        // Code Generation检测
        DetectedCapability codeGenCapability = detectCapability(lowerText, AICapability.CODE_GENERATION, CODE_GEN_KEYWORDS);
        if (codeGenCapability != null) detectedCapabilities.add(codeGenCapability);

        // Content Generation检测
        DetectedCapability contentGenCapability = detectCapability(lowerText, AICapability.CONTENT_GENERATION, CONTENT_GEN_KEYWORDS);
        if (contentGenCapability != null) detectedCapabilities.add(contentGenCapability);

        // 计算总体置信度
        double overallConfidence = calculateOverallConfidence(detectedCapabilities);

        // 生成集成方案
        IntegrationPlan integrationPlan = generateIntegrationPlan(detectedCapabilities);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("[AICapabilityIntegrator] 检测完成: 检测到{}个AI能力, 耗时{}ms",
            detectedCapabilities.size(), durationMs);

        return AICapabilityDetectionResult.builder()
            .detectedCapabilities(detectedCapabilities)
            .overallConfidence(overallConfidence)
            .integrationPlan(integrationPlan)
            .durationMs(durationMs)
            .build();
    }

    /**
     * 检测单个AI能力
     */
    private DetectedCapability detectCapability(String text, AICapability capability, Set<String> keywords) {
        List<String> matchedKeywords = new ArrayList<>();

        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                matchedKeywords.add(keyword);
            }
        }

        if (matchedKeywords.isEmpty()) {
            return null;
        }

        // 计算置信度
        double confidence = Math.min(1.0, matchedKeywords.size() * 0.25);

        return DetectedCapability.builder()
            .capability(capability)
            .confidence(confidence)
            .matchedKeywords(matchedKeywords)
            .recommendedProvider(getRecommendedProvider(capability))
            .estimatedTokensPerRequest(getEstimatedTokens(capability))
            .estimatedCostPer1000(getEstimatedCost(capability))
            .build();
    }

    /**
     * 获取推荐的API提供商
     */
    private String getRecommendedProvider(AICapability capability) {
        return switch (capability) {
            case NLP -> "阿里云NLP";
            case VISION -> "阿里云视觉智能";
            case SPEECH -> "阿里云语音AI";
            case RECOMMENDATION -> "阿里云PAI";
            case TRANSLATION -> "阿里云翻译";
            case CHAT -> "通义千问";
            case CODE_GENERATION -> "通义灵码";
            case CONTENT_GENERATION -> "通义千问";
        };
    }

    /**
     * 获取预估Token消耗
     */
    private int getEstimatedTokens(AICapability capability) {
        return switch (capability) {
            case NLP -> 500;
            case VISION -> 1000;
            case SPEECH -> 200;
            case RECOMMENDATION -> 100;
            case TRANSLATION -> 300;
            case CHAT -> 2000;
            case CODE_GENERATION -> 3000;
            case CONTENT_GENERATION -> 4000;
        };
    }

    /**
     * 获取预估成本（元/1000请求）
     */
    private double getEstimatedCost(AICapability capability) {
        return switch (capability) {
            case NLP -> 2.0;
            case VISION -> 5.0;
            case SPEECH -> 3.0;
            case RECOMMENDATION -> 1.0;
            case TRANSLATION -> 2.0;
            case CHAT -> 10.0;
            case CODE_GENERATION -> 15.0;
            case CONTENT_GENERATION -> 20.0;
        };
    }

    /**
     * 计算总体置信度
     */
    private double calculateOverallConfidence(List<DetectedCapability> capabilities) {
        if (capabilities.isEmpty()) return 0.0;

        double sum = capabilities.stream()
            .mapToDouble(DetectedCapability::getConfidence)
            .sum();

        return Math.min(1.0, sum / capabilities.size());
    }

    /**
     * 生成集成方案
     */
    private IntegrationPlan generateIntegrationPlan(List<DetectedCapability> capabilities) {
        List<String> dependencies = new ArrayList<>();
        List<String> envVariables = new ArrayList<>();
        Map<String, String> serviceCodes = new LinkedHashMap<>();
        double estimatedMonthlyCost = 0.0;

        for (DetectedCapability cap : capabilities) {
            // 添加依赖
            dependencies.add(getDependencyForCapability(cap.getCapability()));

            // 添加环境变量
            envVariables.add(getEnvVariableForCapability(cap.getCapability()));

            // 生成服务代码
            serviceCodes.put(cap.getCapability().getCode() + "Service.java",
                generateServiceCode(cap.getCapability()));

            // 计算成本
            estimatedMonthlyCost += cap.getEstimatedCostPer1000() * 10; // 假设10K请求/月
        }

        // 生成配置代码
        String configCode = generateConfigCode(capabilities);

        return IntegrationPlan.builder()
            .dependencies(dependencies)
            .envVariables(envVariables)
            .configCode(configCode)
            .serviceCodes(serviceCodes)
            .estimatedMonthlyCost(estimatedMonthlyCost)
            .build();
    }

    /**
     * 获取能力对应的依赖
     */
    private String getDependencyForCapability(AICapability capability) {
        return switch (capability) {
            case NLP, VISION, SPEECH, RECOMMENDATION, TRANSLATION ->
                "<dependency><groupId>com.aliyun</groupId><artifactId>alibabacloud-" +
                capability.getCode().toLowerCase() + "</artifactId><version>2.0.0</version></dependency>";
            case CHAT, CODE_GENERATION, CONTENT_GENERATION ->
                "<dependency><groupId>com.alibaba.cloud.ai</groupId><artifactId>spring-ai-alibaba-starter</artifactId><version>1.0.0-M6</version></dependency>";
        };
    }

    /**
     * 获取能力对应的环境变量
     */
    private String getEnvVariableForCapability(AICapability capability) {
        return switch (capability) {
            case NLP -> "ALIYUN_NLP_ACCESS_KEY";
            case VISION -> "ALIYUN_VISION_ACCESS_KEY";
            case SPEECH -> "ALIYUN_SPEECH_ACCESS_KEY";
            case RECOMMENDATION -> "ALIYUN_PAI_ACCESS_KEY";
            case TRANSLATION -> "ALIYUN_TRANSLATION_ACCESS_KEY";
            case CHAT, CODE_GENERATION, CONTENT_GENERATION -> "DASHSCOPE_API_KEY";
        };
    }

    /**
     * 生成服务代码
     */
    private String generateServiceCode(AICapability capability) {
        String serviceName = capability.getCode() + "Service";

        return """
            package com.app.service.ai;

            import org.springframework.stereotype.Service;
            import lombok.extern.slf4j.Slf4j;

            /**
             * %s
             * 自动生成的AI能力服务
             */
            @Slf4j
            @Service
            public class %s {

                /**
                 * 执行%s操作
                 */
                public Object process(Object input) {
                    log.info("[%s] 开始处理: {}", input);
                    // TODO: 实现具体的AI调用逻辑
                    return null;
                }
            }
            """.formatted(capability.getDescription(), serviceName,
                capability.getDisplayName(), serviceName);
    }

    /**
     * 生成配置代码
     */
    private String generateConfigCode(List<DetectedCapability> capabilities) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AI能力配置\n");
        sb.append("ai:\n");

        for (DetectedCapability cap : capabilities) {
            sb.append("  ").append(cap.getCapability().getCode().toLowerCase()).append(":\n");
            sb.append("    enabled: true\n");
            sb.append("    provider: ").append(cap.getRecommendedProvider()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建完整分析文本
     */
    private String buildFullText(String requirement, List<String> features) {
        StringBuilder sb = new StringBuilder();
        if (requirement != null) {
            sb.append(requirement).append(" ");
        }
        if (features != null) {
            sb.append(String.join(" ", features));
        }
        return sb.toString();
    }
}
