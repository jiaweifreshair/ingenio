package com.ingenio.backend.service.g3.hooks;

/**
 * G3 Hook 事件类型。
 *
 * <p>用于区分 Hook 触发的阶段，确保审计与策略可按场景处理。</p>
 */
public enum G3HookEventType {

    /**
     * 工具调用前。
     */
    BEFORE_TOOL,

    /**
     * 工具调用后。
     */
    AFTER_TOOL,

    /**
     * 模型调用前。
     */
    BEFORE_MODEL,

    /**
     * 模型调用后。
     */
    AFTER_MODEL
}
