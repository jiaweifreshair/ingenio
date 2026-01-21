package com.ingenio.backend.langchain4j.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j 配置属性。
 *
 * 是什么：LangChain4j 的全局配置容器。
 * 做什么：承载模型路由与多提供商配置。
 * 为什么：统一配置入口便于灰度切换与成本控制。
 */
@Data
@ConfigurationProperties(prefix = "ingenio.langchain4j")
public class LangChain4jProperties {

    /**
     * 路由配置。
     *
     * 是什么：任务类型到模型列表的映射。
     * 做什么：指定 codegen/repair/analysis 的优先级。
     * 为什么：不同任务对模型能力的偏好不同。
     */
    private Routing routing = new Routing();

    /**
     * 提供商配置。
     *
     * 是什么：providerKey -> Provider。
     * 做什么：保存 apiKey/baseUrl/model 等参数。
     * 为什么：支持多模型多提供商切换。
     */
    private Map<String, Provider> providers = new HashMap<>();

    /**
     * 路由配置结构。
     *
     * 是什么：任务类型到模型列表的配置结构。
     * 做什么：承载 codegen/repair/analysis 的路由优先级。
     * 为什么：让不同任务可配置模型偏好。
     */
    @Data
    public static class Routing {
        /**
         * 代码生成模型优先级列表。
         *
         * 是什么：按优先级排序的提供商 key 列表。
         * 做什么：指导 codegen 任务的模型选择顺序。
         * 为什么：确保生成任务优先使用高质量模型。
         */
        private List<String> codegen;
        /**
         * 修复模型优先级列表。
         *
         * 是什么：按优先级排序的提供商 key 列表。
         * 做什么：指导 repair 任务的模型选择顺序。
         * 为什么：提高修复成功率与收敛速度。
         */
        private List<String> repair;
        /**
         * 分析模型优先级列表。
         *
         * 是什么：按优先级排序的提供商 key 列表。
         * 做什么：指导 analysis 任务的模型选择顺序。
         * 为什么：保证分析场景的稳定输出。
         */
        private List<String> analysis;
    }

    /**
     * 提供商配置结构。
     *
     * 是什么：模型提供商的配置容器。
     * 做什么：保存 API key/baseUrl/model 等信息。
     * 为什么：支持多提供商接入与切换。
     */
    @Data
    public static class Provider {
        /**
         * API Key。
         *
         * 是什么：调用提供商 API 的鉴权密钥。
         * 做什么：用于请求签名或鉴权。
         * 为什么：没有密钥无法访问模型服务。
         */
        private String apiKey;
        /**
         * Base URL。
         *
         * 是什么：模型服务的基础地址。
         * 做什么：指定 OpenAI 兼容接口的入口。
         * 为什么：便于切换服务商或代理网关。
         */
        private String baseUrl;
        /**
         * Chat 模型名称。
         *
         * 是什么：对话模型的标识。
         * 做什么：指导模型调用使用的具体版本。
         * 为什么：不同模型能力与成本差异明显。
         */
        private String model;
        /**
         * Embedding 模型名称。
         *
         * 是什么：向量化模型的标识。
         * 做什么：用于 RAG 向量检索的嵌入生成。
         * 为什么：嵌入质量影响检索命中率。
         */
        private String embeddingModel;
        /**
         * Embedding 备用模型名称。
         *
         * 是什么：Embedding 主模型失败时的降级模型。
         * 做什么：作为自动降级的备用选择。
         * 为什么：提高嵌入生成成功率。
         */
        private String embeddingFallbackModel;
        /**
         * Embedding 向量维度。
         *
         * 是什么：Embedding 向量的维度大小。
         * 做什么：用于 Redis Embedding Store 初始化维度。
         * 为什么：维度不匹配会导致向量检索失败。
         */
        private Integer embeddingDimension;
        /**
         * Embedding 备用维度。
         *
         * 是什么：备用 Embedding 模型的向量维度。
         * 做什么：在备用模型启用时指定维度。
         * 为什么：确保向量维度与存储一致。
         */
        private Integer embeddingFallbackDimension;
        /**
         * 采样温度。
         *
         * 是什么：生成随机性参数。
         * 做什么：控制模型输出的发散程度。
         * 为什么：低温度更稳定，适合代码生成。
         */
        private Double temperature;
        /**
         * 最大输出 Token。
         *
         * 是什么：模型输出最大 Token 数。
         * 做什么：限制生成结果长度。
         * 为什么：控制成本与响应时长。
         */
        private Integer maxTokens;
        /**
         * 超时秒数。
         *
         * 是什么：模型请求的超时秒数。
         * 做什么：控制单次请求的最大耗时。
         * 为什么：避免请求无限期挂起。
         */
        private Integer timeoutSeconds;
        /**
         * 最大重试次数。
         *
         * 是什么：模型请求重试次数。
         * 做什么：在网络波动时进行重试。
         * 为什么：提升调用稳定性。
         */
        private Integer maxRetries;
    }
}
