package com.ingenio.backend.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI提供商工厂类
 *
 * 职责：
 * - 根据配置选择和创建AI提供商实例
 * - 实施智能降级策略（优先使用Qiniu，失败时降级到DashScope）
 * - 管理提供商的生命周期和可用性
 *
 * 配置方式：
 * 1. 环境变量：AI_PROVIDER=qiniu 或 AI_PROVIDER=dashscope
 * 2. 自动选择：如果未配置，自动选择第一个可用的提供商
 *
 * 优先级策略：
 * 1. 如果配置了AI_PROVIDER，优先使用配置的提供商
 * 2. 如果配置的提供商不可用，警告并尝试降级到其他提供商
 * 3. 如果未配置AI_PROVIDER，按优先级自动选择：
 * - Qiniu Cloud（七牛云）- 国内访问快，价格优惠
 * - DashScope（阿里云）- 生态完整，企业级稳定
 * 4. 如果所有提供商都不可用，抛出异常
 *
 * 使用示例：
 * 
 * <pre>
 * AIProvider provider = aiProviderFactory.getProvider();
 * AIResponse response = provider.generate("生成一个登录界面");
 * </pre>
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Component
public class AIProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(AIProviderFactory.class);

    /**
     * 配置的提供商名称（从环境变量AI_PROVIDER读取）
     * 可选值：qiniu, dashscope
     * 如果未配置，则自动选择第一个可用的提供商
     */
    @Value("${AI_PROVIDER:}")
    private String configuredProvider;

    /**
     * ECA Gateway AI 提供商（Gemini）
     */
    private final EcaGatewayAIProvider ecaGatewayAIProvider;

    /**
     * 七牛云AI提供商
     */
    private final QiniuCloudAIProvider qiniuCloudAIProvider;

    /**
     * 阿里云DashScope提供商
     */
    private final DashScopeAIProvider dashScopeAIProvider;

    /**
     * 所有注册的提供商列表（按优先级排序）
     */
    private final List<AIProvider> allProviders;

    public AIProviderFactory(
            EcaGatewayAIProvider ecaGatewayAIProvider,
            QiniuCloudAIProvider qiniuCloudAIProvider,
            DashScopeAIProvider dashScopeAIProvider) {

        this.ecaGatewayAIProvider = ecaGatewayAIProvider;
        this.qiniuCloudAIProvider = qiniuCloudAIProvider;
        this.dashScopeAIProvider = dashScopeAIProvider;

        // 注册提供商列表（按优先级排序）
        this.allProviders = List.of(
                ecaGatewayAIProvider, // 优先级1：ECA Gateway（Gemini，用户指定）
                qiniuCloudAIProvider, // 优先级2：七牛云（国内访问快）
                dashScopeAIProvider // 优先级3：阿里云DashScope（备用）
        );

        log.info("AI提供商工厂初始化完成，已注册 {} 个提供商", allProviders.size());
    }

    /**
     * 获取AI提供商实例
     *
     * 选择策略：
     * 1. 如果配置了AI_PROVIDER环境变量，优先使用配置的提供商
     * 2. 如果配置的提供商不可用，警告并降级到其他可用提供商
     * 3. 如果未配置AI_PROVIDER，自动选择第一个可用的提供商
     * 4. 如果所有提供商都不可用，抛出异常
     *
     * @return 可用的AI提供商实例
     * @throws AIProvider.AIException 如果没有可用的提供商
     */
    public AIProvider getProvider() throws AIProvider.AIException {
        // 情况1：配置了特定的提供商
        if (configuredProvider != null && !configuredProvider.isBlank()) {
            AIProvider provider = getProviderByName(configuredProvider);

            if (provider != null && provider.isAvailable()) {
                log.debug("使用配置的AI提供商: {}", provider.getProviderDisplayName());
                return provider;
            }

            // 配置的提供商不可用，警告并尝试降级
            log.warn("配置的AI提供商 {} 不可用，尝试降级到其他提供商", configuredProvider);
            return getFallbackProvider();
        }

        // 情况2：未配置提供商，自动选择第一个可用的
        log.debug("未配置AI_PROVIDER，自动选择可用提供商");
        return getFirstAvailableProvider();
    }

    /**
     * 根据提供商名称获取实例
     *
     * @param providerName 提供商名称（qiniu, dashscope）
     * @return 对应的提供商实例，如果未找到返回null
     */
    public AIProvider getProviderByName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return null;
        }

        return allProviders.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取第一个可用的提供商
     *
     * @return 第一个可用的提供商实例
     * @throws AIProvider.AIException 如果没有可用的提供商
     */
    private AIProvider getFirstAvailableProvider() throws AIProvider.AIException {
        for (AIProvider provider : allProviders) {
            if (provider.isAvailable()) {
                log.info("自动选择AI提供商: {} ({})",
                        provider.getProviderDisplayName(),
                        provider.getProviderName());
                return provider;
            }
        }

        // 没有任何可用的提供商
        throw new AIProvider.AIException(
                "没有可用的AI提供商。请配置以下环境变量之一：\n" +
                        "  - QINIU_CLOUD_API_KEY 或 DEEPSEEK_API_KEY（七牛云）\n" +
                        "  - DASHSCOPE_API_KEY（阿里云）",
                "factory");
    }

    /**
     * 获取降级提供商（跳过配置的提供商）
     *
     * @return 可用的降级提供商
     * @throws AIProvider.AIException 如果没有可用的降级提供商
     */
    private AIProvider getFallbackProvider() throws AIProvider.AIException {
        for (AIProvider provider : allProviders) {
            // 跳过配置的提供商（已经验证不可用）
            if (provider.getProviderName().equalsIgnoreCase(configuredProvider)) {
                continue;
            }

            if (provider.isAvailable()) {
                log.info("降级使用AI提供商: {} ({})",
                        provider.getProviderDisplayName(),
                        provider.getProviderName());
                return provider;
            }
        }

        // 没有任何可用的降级提供商
        throw new AIProvider.AIException(
                "配置的AI提供商 " + configuredProvider + " 不可用，且没有可用的降级提供商。\n" +
                        "请检查API Key配置：\n" +
                        "  - QINIU_CLOUD_API_KEY 或 DEEPSEEK_API_KEY（七牛云）\n" +
                        "  - DASHSCOPE_API_KEY（阿里云）",
                "factory");
    }

    /**
     * 获取所有注册的提供商列表
     *
     * @return 提供商列表（只读）
     */
    public List<AIProvider> getAllProviders() {
        return List.copyOf(allProviders);
    }

    /**
     * 检查是否有任何可用的提供商
     *
     * @return true表示至少有一个提供商可用，false表示没有可用的提供商
     */
    public boolean hasAvailableProvider() {
        return allProviders.stream().anyMatch(AIProvider::isAvailable);
    }

    /**
     * 获取七牛云提供商实例
     *
     * @return 七牛云提供商
     */
    public QiniuCloudAIProvider getQiniuCloudProvider() {
        return qiniuCloudAIProvider;
    }

    /**
     * 获取阿里云DashScope提供商实例
     *
     * @return 阿里云DashScope提供商
     */
    public DashScopeAIProvider getDashScopeProvider() {
        return dashScopeAIProvider;
    }

    /**
     * 打印所有提供商的可用性状态（用于调试）
     */
    public void printProviderStatus() {
        log.info("========== AI提供商状态 ==========");
        log.info("配置的提供商: {}", configuredProvider.isBlank() ? "未配置（自动选择）" : configuredProvider);

        for (AIProvider provider : allProviders) {
            String status = provider.isAvailable() ? "✅ 可用" : "❌ 不可用";
            log.info("  - {} ({}): {} - 默认模型: {}",
                    provider.getProviderDisplayName(),
                    provider.getProviderName(),
                    status,
                    provider.getDefaultModel());
        }
        log.info("===================================");
    }
}
