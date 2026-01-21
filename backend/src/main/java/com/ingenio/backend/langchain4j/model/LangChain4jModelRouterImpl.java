package com.ingenio.backend.langchain4j.model;

import com.ingenio.backend.langchain4j.config.LangChain4jProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j 模型路由器默认实现。
 *
 * 是什么：基于配置的模型选择器。
 * 做什么：按任务类型与失败次数选择 provider/model。
 * 为什么：提供最小可用的路由能力，便于后续替换为复杂策略。
 */
public class LangChain4jModelRouterImpl implements LangChain4jModelRouter {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jModelRouterImpl.class);

    /**
     * LangChain4j 配置。
     *
     * 是什么：模型路由与提供商配置来源。
     * 做什么：提供 route 与 provider 的配置读取。
     * 为什么：保证路由逻辑可配置化。
     */
    private final LangChain4jProperties properties;

    /**
     * 构造函数。
     *
     * 是什么：模型路由器实现的初始化入口。
     * 做什么：注入 LangChain4j 配置。
     * 为什么：保证路由逻辑可读取外部配置。
     *
     * @param properties LangChain4j 配置
     */
    public LangChain4jModelRouterImpl(LangChain4jProperties properties) {
        this.properties = properties;
    }

    /**
     * 选择模型。
     *
     * 是什么：模型选择实现入口。
     * 做什么：按任务类型与尝试次数返回模型信息。
     * 为什么：保证统一路由逻辑与降级策略。
     *
     * @param taskType 任务类型
     * @param attempt 尝试次数
     * @param failureContext 失败上下文
     * @return 模型选择结果
     */
    @Override
    public ModelSelection select(TaskType taskType, int attempt, FailureContext failureContext) {
        List<String> candidates = resolveCandidates(taskType);
        if (candidates.isEmpty()) {
            log.warn("[LangChain4jRouter] 未配置任何可用提供商，返回 unknown");
            return new ModelSelection("unknown", "unknown");
        }

        int safeAttempt = Math.max(0, attempt);
        int index = Math.min(safeAttempt, candidates.size() - 1);
        String providerKey = candidates.get(index);

        LangChain4jProperties.Provider provider = resolveProvider(providerKey);
        if (provider == null) {
            log.warn("[LangChain4jRouter] provider 未配置: {}", providerKey);
            return new ModelSelection(providerKey, "unknown");
        }

        String model = provider.getModel();
        if (model == null || model.isBlank()) {
            model = "unknown";
        }

        log.debug("[LangChain4jRouter] 选择模型: taskType={}, provider={}, model={}, attempt={}",
                taskType, providerKey, model, safeAttempt);
        return new ModelSelection(providerKey, model);
    }

    /**
     * 解析任务对应的候选提供商列表。
     *
     * 是什么：路由候选列表解析方法。
     * 做什么：根据任务类型读取配置或回退默认列表。
     * 为什么：保证任务类型与模型优先级可配置。
     */
    private List<String> resolveCandidates(TaskType taskType) {
        if (properties == null || properties.getRouting() == null) {
            return fallbackProviders();
        }
        List<String> configured = switch (taskType) {
            case CODEGEN -> properties.getRouting().getCodegen();
            case REPAIR -> properties.getRouting().getRepair();
            case ANALYSIS -> properties.getRouting().getAnalysis();
        };
        if (configured == null || configured.isEmpty()) {
            return fallbackProviders();
        }
        return new ArrayList<>(configured);
    }

    /**
     * 获取兜底提供商列表。
     *
     * 是什么：默认的 provider key 列表。
     * 做什么：在未配置路由时提供可用候选。
     * 为什么：保证路由器不会因缺省配置而中断。
     */
    private List<String> fallbackProviders() {
        Map<String, LangChain4jProperties.Provider> providers =
                properties != null ? properties.getProviders() : null;
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }
        return providers.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * 解析提供商配置。
     *
     * 是什么：provider 配置读取方法。
     * 做什么：根据 key 获取 Provider 实例。
     * 为什么：集中处理配置读取便于扩展。
     */
    private LangChain4jProperties.Provider resolveProvider(String providerKey) {
        if (properties == null || properties.getProviders() == null) {
            return null;
        }
        return properties.getProviders().get(providerKey);
    }
}
