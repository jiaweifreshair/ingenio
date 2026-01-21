package com.ingenio.backend.service.g3.hooks;

import com.ingenio.backend.config.G3HookProperties;
import com.ingenio.backend.entity.g3.G3LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * G3 Hook 审计处理器。
 *
 * <p>用于记录工具/模型调用的关键事件，形成可追踪的审计日志。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class G3AuditHookHandler implements G3HookHandler {

    private static final Logger log = LoggerFactory.getLogger(G3AuditHookHandler.class);

    private final G3HookProperties hookProperties;

    public G3AuditHookHandler(G3HookProperties hookProperties) {
        this.hookProperties = hookProperties;
    }

    @Override
    public G3HookResult beforeTool(G3HookContext context) {
        if (!hookProperties.isAuditLogEnabled()) {
            return G3HookResult.allow();
        }
        String message = String.format("Hook-Tool-开始: tool=%s, input=%s",
                safe(context.getToolName()), safe(context.getToolInput()));
        emitAuditLog(context, message, G3LogEntry.Level.INFO.getValue());
        return G3HookResult.allow();
    }

    @Override
    public void afterTool(G3HookContext context, G3HookResult result) {
        if (!hookProperties.isAuditLogEnabled()) {
            return;
        }
        String message = String.format("Hook-Tool-结束: tool=%s, success=%s, exitCode=%s, decision=%s, reason=%s",
                safe(context.getToolName()),
                safe(context.getSuccess()),
                safe(context.getExitCode()),
                result.getDecision(),
                safe(result.getReason()));
        String level = result.isBlocked() ? G3LogEntry.Level.WARN.getValue() : G3LogEntry.Level.INFO.getValue();
        emitAuditLog(context, message, level);
    }

    @Override
    public G3HookResult beforeModel(G3HookContext context) {
        if (!hookProperties.isAuditLogEnabled()) {
            return G3HookResult.allow();
        }
        String message = String.format("Hook-Model-开始: provider=%s, model=%s, promptPreview=%s",
                safe(readMetadata(context, "provider")),
                safe(context.getModelName()),
                safe(context.getPromptPreview()));
        emitAuditLog(context, message, G3LogEntry.Level.INFO.getValue());
        return G3HookResult.allow();
    }

    @Override
    public void afterModel(G3HookContext context, G3HookResult result) {
        if (!hookProperties.isAuditLogEnabled()) {
            return;
        }
        String message = String.format(
                "Hook-Model-结束: provider=%s, model=%s, success=%s, tokens=%s, durationMs=%s, decision=%s, reason=%s",
                safe(readMetadata(context, "provider")),
                safe(context.getModelName()),
                safe(context.getSuccess()),
                safe(context.getTotalTokens()),
                safe(context.getDurationMs()),
                result.getDecision(),
                safe(result.getReason()));
        String level = result.isBlocked() ? G3LogEntry.Level.WARN.getValue() : G3LogEntry.Level.INFO.getValue();
        emitAuditLog(context, message, level);
    }

    private void emitAuditLog(G3HookContext context, String message, String level) {
        String sanitized = truncate(maskSensitive(message), hookProperties.getMaxPayloadChars());
        log.info("[G3HookAudit] {}", sanitized);
        if (hookProperties.isEmitJobLog() && context.getLogConsumer() != null) {
            context.getLogConsumer().accept(new G3LogEntry(
                    java.time.Instant.now().toString(),
                    G3LogEntry.Role.SYSTEM.getValue(),
                    sanitized,
                    level
            ));
        }
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String readMetadata(G3HookContext context, String key) {
        if (context == null || context.getMetadata() == null || key == null) {
            return "-";
        }
        Object value = context.getMetadata().get(key);
        return value == null ? "-" : String.valueOf(value);
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

    private String maskSensitive(String text) {
        if (text == null) {
            return "";
        }
        String masked = text;
        masked = masked.replaceAll("(?i)(authorization\\s*:\\s*)([^\\n\\r]+)", "$1***");
        masked = masked.replaceAll("(?i)(bearer\\s+)([A-Za-z0-9._-]+)", "$1***");
        masked = masked.replaceAll("sk-[A-Za-z0-9]{16,}", "sk-***");
        masked = masked.replaceAll("(?i)(password|secret|token)\\s*[:=]\\s*([^\\s]+)", "$1=***");
        return masked;
    }
}
