package com.ingenio.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * G3 Hook 管线配置。
 *
 * <p>用于控制 Hook 是否启用以及审计日志输出的范围与长度，避免泄露敏感信息。</p>
 */
@Configuration
@ConfigurationProperties(prefix = "ingenio.g3.hooks")
public class G3HookProperties {

    /**
     * 是否启用 Hook 管线。
     */
    private boolean enabled = true;

    /**
     * 是否启用 Hook 审计日志。
     */
    private boolean auditLogEnabled = true;

    /**
     * 是否将审计日志写入任务日志（SSE 可见）。
     */
    private boolean emitJobLog = true;

    /**
     * 审计日志中允许输出的最大负载长度（字符）。
     */
    private int maxPayloadChars = 400;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAuditLogEnabled() {
        return auditLogEnabled;
    }

    public void setAuditLogEnabled(boolean auditLogEnabled) {
        this.auditLogEnabled = auditLogEnabled;
    }

    public boolean isEmitJobLog() {
        return emitJobLog;
    }

    public void setEmitJobLog(boolean emitJobLog) {
        this.emitJobLog = emitJobLog;
    }

    public int getMaxPayloadChars() {
        return maxPayloadChars;
    }

    public void setMaxPayloadChars(int maxPayloadChars) {
        this.maxPayloadChars = maxPayloadChars;
    }
}
