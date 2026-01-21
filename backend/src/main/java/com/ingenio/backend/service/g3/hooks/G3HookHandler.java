package com.ingenio.backend.service.g3.hooks;

/**
 * G3 Hook 处理器接口。
 *
 * <p>用于扩展自定义 Hook 逻辑，例如审计、策略阻断、指标上报等。</p>
 */
public interface G3HookHandler {

    /**
     * 工具调用前 Hook。
     *
     * @param context Hook 上下文
     * @return Hook 决策结果
     */
    default G3HookResult beforeTool(G3HookContext context) {
        return G3HookResult.allow();
    }

    /**
     * 工具调用后 Hook。
     *
     * @param context Hook 上下文
     * @param result  最终决策结果
     */
    default void afterTool(G3HookContext context, G3HookResult result) {
        // 默认不处理
    }

    /**
     * 模型调用前 Hook。
     *
     * @param context Hook 上下文
     * @return Hook 决策结果
     */
    default G3HookResult beforeModel(G3HookContext context) {
        return G3HookResult.allow();
    }

    /**
     * 模型调用后 Hook。
     *
     * @param context Hook 上下文
     * @param result  最终决策结果
     */
    default void afterModel(G3HookContext context, G3HookResult result) {
        // 默认不处理
    }
}
