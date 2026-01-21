package com.ingenio.backend.service.g3.hooks;

import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.config.G3HookProperties;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * G3 Hook 包装的 AIProvider。
 *
 * <p>用于在模型调用前后触发 Hook 管线，实现审计与策略控制。</p>
 */
public class G3HookedAIProvider implements AIProvider {

    private final AIProvider delegate;
    private final G3HookPipeline hookPipeline;
    private final G3HookProperties hookProperties;
    private final UUID jobId;
    private final UUID tenantId;
    private final UUID userId;
    private final Consumer<G3LogEntry> logConsumer;

    public G3HookedAIProvider(
            AIProvider delegate,
            G3HookPipeline hookPipeline,
            G3HookProperties hookProperties,
            G3JobEntity job,
            Consumer<G3LogEntry> logConsumer) {
        this.delegate = delegate;
        this.hookPipeline = hookPipeline;
        this.hookProperties = hookProperties;
        this.jobId = job != null ? job.getId() : null;
        this.tenantId = job != null ? job.getTenantId() : null;
        this.userId = job != null ? job.getUserId() : null;
        this.logConsumer = logConsumer;
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    @Override
    public String getProviderDisplayName() {
        return delegate.getProviderDisplayName();
    }

    @Override
    public String getDefaultModel() {
        return delegate.getDefaultModel();
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public AIResponse generate(String prompt, AIRequest request) throws AIException {
        String modelName = request != null && request.model() != null
                ? request.model()
                : delegate.getDefaultModel();
        String promptPreview = truncate(prompt, hookProperties.getMaxPayloadChars());

        G3HookContext beforeContext = G3HookContext.builder()
                .eventType(G3HookEventType.BEFORE_MODEL)
                .jobId(jobId)
                .tenantId(tenantId)
                .userId(userId)
                .modelName(modelName)
                .promptPreview(promptPreview)
                .logConsumer(logConsumer)
                .metadata("provider", delegate.getProviderName())
                .build();

        G3HookResult decision = hookPipeline.beforeModel(beforeContext);
        if (decision.isBlocked()) {
            G3HookContext blockedContext = G3HookContext.builder()
                    .eventType(G3HookEventType.AFTER_MODEL)
                    .jobId(jobId)
                    .tenantId(tenantId)
                    .userId(userId)
                    .modelName(modelName)
                    .promptPreview(promptPreview)
                    .success(false)
                    .errorMessage(decision.getReason())
                    .logConsumer(logConsumer)
                    .metadata("provider", delegate.getProviderName())
                    .build();
            hookPipeline.afterModel(blockedContext, decision);
            throw new AIException("模型调用被Hook阻断: " + decision.getReason(), "g3-hook");
        }

        long start = System.currentTimeMillis();
        try {
            AIResponse response = delegate.generate(prompt, request);
            long duration = System.currentTimeMillis() - start;

            G3HookContext afterContext = G3HookContext.builder()
                    .eventType(G3HookEventType.AFTER_MODEL)
                    .jobId(jobId)
                    .tenantId(tenantId)
                    .userId(userId)
                    .modelName(response.model() != null ? response.model() : modelName)
                    .promptPreview(promptPreview)
                    .promptTokens(response.promptTokens())
                    .completionTokens(response.completionTokens())
                    .totalTokens(response.totalTokens())
                    .durationMs(duration)
                    .success(true)
                    .logConsumer(logConsumer)
                    .metadata("provider", response.provider())
                    .build();
            hookPipeline.afterModel(afterContext, decision);
            return response;
        } catch (AIException e) {
            long duration = System.currentTimeMillis() - start;
            G3HookContext errorContext = G3HookContext.builder()
                    .eventType(G3HookEventType.AFTER_MODEL)
                    .jobId(jobId)
                    .tenantId(tenantId)
                    .userId(userId)
                    .modelName(modelName)
                    .promptPreview(promptPreview)
                    .durationMs(duration)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .logConsumer(logConsumer)
                    .metadata("provider", delegate.getProviderName())
                    .build();
            hookPipeline.afterModel(errorContext, decision);
            throw e;
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (maxChars <= 0 || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...(已截断)";
    }
}
