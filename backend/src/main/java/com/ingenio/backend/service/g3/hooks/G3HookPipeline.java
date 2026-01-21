package com.ingenio.backend.service.g3.hooks;

import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.config.G3HookProperties;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * G3 Hook 管线。
 *
 * <p>负责统一调度 Hook 处理器并提供模型/工具调用前后的统一入口。</p>
 */
@Service
public class G3HookPipeline {

    private static final Logger log = LoggerFactory.getLogger(G3HookPipeline.class);

    private final G3HookProperties hookProperties;
    private final List<G3HookHandler> hookHandlers;

    public G3HookPipeline(G3HookProperties hookProperties, List<G3HookHandler> hookHandlers) {
        this.hookProperties = hookProperties;
        this.hookHandlers = hookHandlers != null ? hookHandlers : List.of();
    }

    /**
     * 工具调用前 Hook。
     */
    public G3HookResult beforeTool(G3HookContext context) {
        if (!hookProperties.isEnabled()) {
            return G3HookResult.allow();
        }
        for (G3HookHandler handler : hookHandlers) {
            G3HookResult result = handler.beforeTool(context);
            if (result != null && result.isBlocked()) {
                log.warn("[G3Hook] 工具调用被阻断: {}", result.getReason());
                return result;
            }
        }
        return G3HookResult.allow();
    }

    /**
     * 工具调用后 Hook。
     */
    public void afterTool(G3HookContext context, G3HookResult result) {
        if (!hookProperties.isEnabled()) {
            return;
        }
        for (G3HookHandler handler : hookHandlers) {
            handler.afterTool(context, result);
        }
    }

    /**
     * 模型调用前 Hook。
     */
    public G3HookResult beforeModel(G3HookContext context) {
        if (!hookProperties.isEnabled()) {
            return G3HookResult.allow();
        }
        for (G3HookHandler handler : hookHandlers) {
            G3HookResult result = handler.beforeModel(context);
            if (result != null && result.isBlocked()) {
                log.warn("[G3Hook] 模型调用被阻断: {}", result.getReason());
                return result;
            }
        }
        return G3HookResult.allow();
    }

    /**
     * 模型调用后 Hook。
     */
    public void afterModel(G3HookContext context, G3HookResult result) {
        if (!hookProperties.isEnabled()) {
            return;
        }
        for (G3HookHandler handler : hookHandlers) {
            handler.afterModel(context, result);
        }
    }

    /**
     * 为 G3 任务包装 AIProvider，注入模型 Hook。
     *
     * @param provider    原始 AIProvider
     * @param job         G3 任务实体
     * @param logConsumer 日志回调
     * @return 支持 Hook 的 AIProvider
     */
    public AIProvider wrapProvider(AIProvider provider, G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        if (provider == null) {
            return null;
        }
        if (!hookProperties.isEnabled()) {
            return provider;
        }
        return new G3HookedAIProvider(provider, this, hookProperties, job, logConsumer);
    }

    public G3HookProperties getHookProperties() {
        return hookProperties;
    }
}
