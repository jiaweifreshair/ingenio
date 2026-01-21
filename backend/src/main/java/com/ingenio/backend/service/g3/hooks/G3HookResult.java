package com.ingenio.backend.service.g3.hooks;

import java.util.Collections;
import java.util.Map;

/**
 * G3 Hook 执行结果。
 *
 * <p>用于描述 Hook 对当前操作的决策与原因，方便审计与上层处理。</p>
 */
public class G3HookResult {

    /**
     * Hook 决策。
     */
    private final G3HookDecision decision;

    /**
     * 决策原因（可为空）。
     */
    private final String reason;

    /**
     * 额外元数据（可为空）。
     */
    private final Map<String, Object> metadata;

    public G3HookResult(G3HookDecision decision, String reason, Map<String, Object> metadata) {
        this.decision = decision;
        this.reason = reason;
        this.metadata = metadata != null ? metadata : Collections.emptyMap();
    }

    /**
     * 创建允许执行的结果。
     */
    public static G3HookResult allow() {
        return new G3HookResult(G3HookDecision.ALLOW, "ok", Collections.emptyMap());
    }

    /**
     * 创建阻断执行的结果。
     *
     * @param reason 阻断原因
     */
    public static G3HookResult block(String reason) {
        return new G3HookResult(G3HookDecision.BLOCK, reason, Collections.emptyMap());
    }

    public G3HookDecision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isBlocked() {
        return decision == G3HookDecision.BLOCK;
    }
}
