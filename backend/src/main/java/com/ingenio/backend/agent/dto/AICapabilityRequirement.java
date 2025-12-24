package com.ingenio.backend.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI能力需求
 * 用于识别应用是否需要AI能力，以及需要哪些AI能力
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AICapabilityRequirement {

    /**
     * 是否需要AI能力
     */
    @JsonProperty("needsAI")
    private Boolean needsAI;

    /**
     * 需要的AI能力列表
     */
    @JsonProperty("capabilities")
    private List<AICapability> capabilities;

    /**
     * AI复杂度
     */
    @JsonProperty("complexity")
    private AIComplexity complexity;

    /**
     * 推荐的实现方式
     */
    @JsonProperty("recommendedApproach")
    private AIApproach recommendedApproach;

    /**
     * 推理过程（为什么需要这些AI能力）
     */
    @JsonProperty("reasoning")
    private String reasoning;

    /**
     * 置信度（0.0-1.0）
     */
    @JsonProperty("confidence")
    private Double confidence;

    /**
     * AI能力详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AICapability {
        /**
         * AI能力类型
         */
        @JsonProperty("type")
        private AICapabilityType type;

        /**
         * 能力描述
         */
        @JsonProperty("description")
        private String description;

        /**
         * 使用场景
         */
        @JsonProperty("useCase")
        private String useCase;

        /**
         * 预估Token消耗（每次请求）
         */
        @JsonProperty("estimatedTokens")
        private Integer estimatedTokens;

        /**
         * API端点路径
         */
        @JsonProperty("apiEndpoint")
        private String apiEndpoint;

        /**
         * 需要的配置项
         */
        @JsonProperty("requiredConfigs")
        private List<String> requiredConfigs;
    }

    /**
     * AI能力类型枚举（扩展到19种）
     *
     * 基础11种：
     * - CHATBOT: 聊天机器人
     * - QA_SYSTEM: 问答系统
     * - RAG: 检索增强生成
     * - SUMMARIZATION: 文本摘要
     * - IMAGE_RECOGNITION: 图片识别
     * - SPEECH_TO_TEXT: 语音识别
     * - TEXT_TO_SPEECH: 语音合成
     * - CONTENT_GENERATION: 内容生成
     * - SENTIMENT_ANALYSIS: 情感分析
     * - TRANSLATION: 智能翻译
     * - CODE_COMPLETION: 代码补全
     *
     * 新增8种（2025-11-11）：
     * - VIDEO_ANALYSIS: 视频分析
     * - KNOWLEDGE_GRAPH: 知识图谱
     * - OCR_DOCUMENT: 智能文档识别
     * - REALTIME_STREAM: 实时流分析
     * - HYPER_PERSONALIZATION: 超个性化引擎
     * - PREDICTIVE_ANALYTICS: 预测分析
     * - MULTIMODAL_GENERATION: 多模态生成
     * - ANOMALY_DETECTION: 异常检测
     */
    public enum AICapabilityType {
        // ==================== 基础11种 ====================

        /**
         * 聊天机器人（智能客服）
         */
        CHATBOT("聊天机器人", "智能对话系统，支持多轮对话"),

        /**
         * 问答系统
         */
        QA_SYSTEM("问答系统", "基于知识库的问答"),

        /**
         * RAG检索增强生成
         */
        RAG("知识库检索", "检索增强生成，结合文档检索和AI生成"),

        /**
         * 文本摘要
         */
        SUMMARIZATION("文本摘要", "自动生成文本摘要"),

        /**
         * 图片识别
         */
        IMAGE_RECOGNITION("图片识别", "识别图片内容、物体、场景"),

        /**
         * 语音识别（STT）
         */
        SPEECH_TO_TEXT("语音识别", "语音转文字"),

        /**
         * 语音合成（TTS）
         */
        TEXT_TO_SPEECH("语音合成", "文字转语音"),

        /**
         * 内容生成
         */
        CONTENT_GENERATION("内容生成", "生成文章、广告文案、创意内容"),

        /**
         * 情感分析
         */
        SENTIMENT_ANALYSIS("情感分析", "分析文本情感倾向"),

        /**
         * 智能翻译
         */
        TRANSLATION("智能翻译", "多语言翻译"),

        /**
         * 代码补全/生成
         */
        CODE_COMPLETION("代码补全", "智能代码补全和生成"),

        /**
         * 智能推荐
         */
        RECOMMENDATION("智能推荐", "基于AI的个性化推荐系统，支持内容推荐、商品推荐、学习资源推荐等场景"),

        // ==================== 新增8种（2025-11-11）====================

        /**
         * 视频分析
         * 复杂度：MEDIUM
         * 技术实现：Qwen-VL-Max（视觉语言模型）
         * 应用场景：短视频应用、内容审核、智能监控、视频编辑、电商应用
         * 预估成本：$200-500/月
         */
        VIDEO_ANALYSIS("视频分析", "基于AI的视频内容理解和分析，支持物体检测、场景识别、动作分析、视频摘要生成"),

        /**
         * 知识图谱
         * 复杂度：COMPLEX
         * 技术实现：Qwen-Max（实体关系提取）
         * 应用场景：企业知识库、学习应用、内容平台、电商应用、法律科技
         * 预估成本：$300-800/月
         */
        KNOWLEDGE_GRAPH("知识图谱", "从非结构化文本中自动提取实体、关系和属性，构建知识图谱，支持实体链接、关系推理、语义搜索"),

        /**
         * 智能文档识别
         * 复杂度：SIMPLE
         * 技术实现：Qwen-VL-Max + OCR API
         * 应用场景：金融应用、财务管理、办公应用、教育应用、医疗健康
         * 预估成本：$100-300/月
         */
        OCR_DOCUMENT("智能文档识别", "结合OCR和AI理解的智能文档识别系统，支持身份证、发票、合同、表格等多种文档类型的识别和结构化"),

        /**
         * 实时流分析
         * 复杂度：COMPLEX
         * 技术实现：Google Gemini 2.0 Multimodal Live API
         * 应用场景：视频会议、直播平台、智能家居、在线教育、AR/VR应用
         * 预估成本：$500-1500/月
         */
        REALTIME_STREAM("实时流分析", "实时处理音视频流数据，支持低延迟的双向流式交互，可用于实时字幕生成、语音命令识别、视频内容审核、直播互动"),

        /**
         * 超个性化引擎
         * 复杂度：MEDIUM
         * 技术实现：Qwen-Max（用户画像生成 + 推荐理由）
         * 应用场景：内容平台、电商应用、音乐/影视、新闻资讯、学习应用
         * 预估成本：$200-600/月
         */
        HYPER_PERSONALIZATION("超个性化引擎", "基于用户行为、偏好、上下文的超个性化推荐和内容生成系统，提供精准的个性化体验，提升用户参与度和留存率"),

        /**
         * 预测分析
         * 复杂度：MEDIUM
         * 技术实现：Qwen-Max（时间序列分析 + 预测建模）
         * 应用场景：订阅应用、电商应用、金融应用、内容平台、人力资源
         * 预估成本：$300-800/月
         */
        PREDICTIVE_ANALYTICS("预测分析", "基于历史数据和机器学习模型的预测分析系统，支持时间序列预测、用户流失预测、收入预测、风险预估等功能"),

        /**
         * 多模态生成
         * 复杂度：COMPLEX
         * 技术实现：通义万相（Wanx）
         * 应用场景：社交应用、营销工具、教育应用、电商应用、游戏应用
         * 预估成本：$500-2000/月
         */
        MULTIMODAL_GENERATION("多模态生成", "跨模态AI内容生成系统，支持文生图、图生文、文生视频、图生视频等多种生成方式，适用于创意设计、营销内容生成"),

        /**
         * 异常检测
         * 复杂度：MEDIUM
         * 技术实现：Qwen-Max（异常模式识别）
         * 应用场景：金融安全、网络安全、质量监控、运维监控、用户行为
         * 预估成本：$200-600/月
         */
        ANOMALY_DETECTION("异常检测", "基于机器学习的异常检测系统，自动识别数据中的异常模式、异常行为、欺诈行为等，支持实时监控和批量分析");

        private final String displayName;
        private final String description;

        AICapabilityType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * AI复杂度枚举
     */
    public enum AIComplexity {
        /**
         * 简单：单一AI能力，直接调用API
         */
        SIMPLE("简单", "单一AI能力，直接调用API即可"),

        /**
         * 中等：多个AI能力组合，需要工作流编排
         */
        MEDIUM("中等", "多个AI能力组合，需要工作流编排"),

        /**
         * 复杂：复杂的AI系统，需要自定义Agent和Memory
         */
        COMPLEX("复杂", "复杂的AI系统，需要自定义Agent和Memory");

        private final String displayName;
        private final String description;

        AIComplexity(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * AI实现方式枚举
     */
    public enum AIApproach {
        /**
         * 无AI需求
         */
        @JsonProperty("NONE")
        @com.fasterxml.jackson.annotation.JsonAlias({"None", "none", "null"})
        NONE("无", "不需要AI能力"),

        /**
         * 直接调用AI API（适用于简单场景）
         */
        DIRECT_API("直接调用AI API", "适用于简单场景，生成直接调用七牛云/阿里云API的代码"),

        /**
         * Dify工作流编排（适用于中等复杂度）
         */
        DIFY_WORKFLOW("Dify工作流编排", "适用于中等复杂度，使用Dify可视化编排AI workflow"),

        /**
         * LangChain自定义（适用于高级定制）
         */
        LANGCHAIN("LangChain自定义", "适用于高级定制，使用LangChain4j构建复杂Agent系统");

        private final String displayName;
        private final String description;

        AIApproach(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }
}
