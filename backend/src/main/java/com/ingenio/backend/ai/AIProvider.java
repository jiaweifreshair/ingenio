package com.ingenio.backend.ai;

import java.util.List;
import java.util.Map;

/**
 * AI提供商抽象接口
 *
 * 定义统一的AI模型调用接口，支持多AI提供商（七牛云、阿里云DashScope等）。
 *
 * 设计原则：
 * - 接口隔离：只定义必需的文本生成方法
 * - 提供商无关：调用方不需要知道底层使用哪个AI提供商
 * - 可扩展：轻松添加新的AI提供商实现
 *
 * 支持的AI提供商：
 * - 七牛云Qiniu（OpenAI兼容接口）
 * - 阿里云DashScope（通义千问）
 * - 未来：OpenAI、百度文心一言等
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @see QiniuCloudAIProvider
 * @see DashScopeAIProvider
 */
public interface AIProvider {

    /**
     * 获取提供商名称
     *
     * @return 提供商名称，例如："qiniu"、"dashscope"
     */
    String getProviderName();

    /**
     * 获取提供商显示名称
     *
     * @return 提供商显示名称，例如："七牛云Qiniu"、"阿里云DashScope"
     */
    String getProviderDisplayName();

    /**
     * 获取默认模型名称
     *
     * @return 模型名称，例如："Qwen/Qwen2.5-72B-Instruct"、"qwen-max"
     */
    String getDefaultModel();

    /**
     * 检查提供商是否可用（API Key已配置且有效）
     *
     * @return true表示可用，false表示不可用
     */
    boolean isAvailable();

    /**
     * 生成文本（同步调用）
     *
     * 核心方法：根据提示词生成文本，支持自定义参数。
     *
     * @param prompt 提示词（支持多行文本）
     * @param request 请求参数配置
     * @return AI生成的响应
     * @throws AIException 当API调用失败时抛出
     */
    AIResponse generate(String prompt, AIRequest request) throws AIException;

    /**
     * 快速生成文本（使用默认参数）
     *
     * 便捷方法：使用默认配置（temperature=0.7, maxTokens=4096）生成文本。
     *
     * @param prompt 提示词
     * @return AI生成的响应
     * @throws AIException 当API调用失败时抛出
     */
    default AIResponse generate(String prompt) throws AIException {
        return generate(prompt, AIRequest.builder().build());
    }

    /**
     * 对话消息对象
     *
     * 是什么：描述一条对话消息的角色与内容。
     * 做什么：支持多模态消息（文本/图片/视频/文件）序列化到OpenAI兼容格式。
     * 为什么：Gemini 3.0 Pro Preview 需要使用结构化 messages 传入多模态内容。
     */
    record ChatMessage(
            String role,     // 消息角色（system/user/assistant/tool）
            Object content   // 消息内容（字符串或多模态内容数组）
    ) {}

    /**
     * AI请求参数
     *
     * 使用Builder模式构建，支持链式调用。
     *
     * @param messages        是什么：多模态对话消息列表；做什么：承载结构化messages输入；为什么：Gemini 3.0 通过messages表达多模态内容
     * @param reasoningEffort 是什么：思考强度标识；做什么：控制模型思考程度；为什么：七牛云Gemini仅允许low/high
     * @param extraParams     是什么：扩展参数透传容器；做什么：附加safety_settings等字段；为什么：保持OpenAPI兼容与演进空间
     */
    record AIRequest(
            String model,          // 模型名称（null则使用默认模型）
            Double temperature,    // 温度参数 [0.0, 2.0]，控制随机性
            Integer maxTokens,     // 最大生成token数
            Double topP,           // 核采样参数 [0.0, 1.0]
            String stopSequence,   // 停止序列（可选）
            List<ChatMessage> messages,  // 多模态对话消息列表（优先于prompt）
            String reasoningEffort,      // 思考强度（Gemini仅支持 low/high）
            Map<String, Object> extraParams // 扩展参数（safety_settings/response_format等）
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private Double temperature = 0.7;      // 默认温度
            private Integer maxTokens = 4096;      // 默认最大token数
            private Double topP = 1.0;             // 默认不使用核采样
            private String stopSequence;
            private List<ChatMessage> messages;
            private String reasoningEffort;
            private Map<String, Object> extraParams;

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder temperature(Double temperature) {
                this.temperature = temperature;
                return this;
            }

            public Builder maxTokens(Integer maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public Builder topP(Double topP) {
                this.topP = topP;
                return this;
            }

            public Builder stopSequence(String stopSequence) {
                this.stopSequence = stopSequence;
                return this;
            }

            /**
             * 设置多模态对话消息列表。
             *
             * 是什么：用于替代prompt的完整messages数组。
             * 做什么：支持文本/图片/视频/文件等内容组合输入。
             * 为什么：Gemini 3.0 Pro Preview 通过messages承载多模态请求。
             */
            public Builder messages(List<ChatMessage> messages) {
                this.messages = messages;
                return this;
            }

            /**
             * 设置思考强度。
             *
             * 是什么：Gemini思考模式的强度标识。
             * 做什么：控制模型思考投入（low/high）。
             * 为什么：七牛云Gemini 3.0 Pro Preview仅支持该参数控制思考程度。
             */
            public Builder reasoningEffort(String reasoningEffort) {
                this.reasoningEffort = reasoningEffort;
                return this;
            }

            /**
             * 设置扩展参数。
             *
             * 是什么：额外透传的请求参数集合。
             * 做什么：传递safety_settings、response_format等未收敛字段。
             * 为什么：保持对七牛云OpenAPI扩展字段的兼容性。
             */
            public Builder extraParams(Map<String, Object> extraParams) {
                this.extraParams = extraParams;
                return this;
            }

            public AIRequest build() {
                return new AIRequest(
                        model,
                        temperature,
                        maxTokens,
                        topP,
                        stopSequence,
                        messages,
                        reasoningEffort,
                        extraParams
                );
            }
        }
    }

    /**
     * AI响应结果
     *
     * 封装AI生成的文本和元数据。
     */
    record AIResponse(
            String content,           // 生成的文本内容
            String model,             // 实际使用的模型名称
            int promptTokens,         // 输入token数
            int completionTokens,     // 输出token数
            int totalTokens,          // 总token数
            long durationMs,          // 请求耗时（毫秒）
            String provider,          // 提供商名称
            String rawResponse        // 原始JSON响应（用于调试）
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String content;
            private String model;
            private int promptTokens;
            private int completionTokens;
            private int totalTokens;
            private long durationMs;
            private String provider;
            private String rawResponse;

            public Builder content(String content) {
                this.content = content;
                return this;
            }

            public Builder model(String model) {
                this.model = model;
                return this;
            }

            public Builder promptTokens(int promptTokens) {
                this.promptTokens = promptTokens;
                return this;
            }

            public Builder completionTokens(int completionTokens) {
                this.completionTokens = completionTokens;
                return this;
            }

            public Builder totalTokens(int totalTokens) {
                this.totalTokens = totalTokens;
                return this;
            }

            public Builder durationMs(long durationMs) {
                this.durationMs = durationMs;
                return this;
            }

            public Builder provider(String provider) {
                this.provider = provider;
                return this;
            }

            public Builder rawResponse(String rawResponse) {
                this.rawResponse = rawResponse;
                return this;
            }

            public AIResponse build() {
                return new AIResponse(
                        content,
                        model,
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        durationMs,
                        provider,
                        rawResponse
                );
            }
        }
    }

    /**
     * AI异常
     *
     * 封装AI API调用过程中的所有异常。
     */
    class AIException extends RuntimeException {
        private final String provider;
        private final String errorCode;
        private final Integer httpStatus;

        public AIException(String message, String provider) {
            super(message);
            this.provider = provider;
            this.errorCode = null;
            this.httpStatus = null;
        }

        public AIException(String message, String provider, Throwable cause) {
            super(message, cause);
            this.provider = provider;
            this.errorCode = null;
            this.httpStatus = null;
        }

        public AIException(String message, String provider, String errorCode, Integer httpStatus) {
            super(message);
            this.provider = provider;
            this.errorCode = errorCode;
            this.httpStatus = httpStatus;
        }

        public String getProvider() {
            return provider;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public Integer getHttpStatus() {
            return httpStatus;
        }

        @Override
        public String toString() {
            return String.format("AIException[provider=%s, errorCode=%s, httpStatus=%d, message=%s]",
                    provider, errorCode, httpStatus, getMessage());
        }
    }
}
