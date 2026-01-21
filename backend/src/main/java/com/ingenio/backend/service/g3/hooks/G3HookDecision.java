package com.ingenio.backend.service.g3.hooks;

/**
 * G3 Hook 决策枚举。
 *
 * <p>用于表明当前 Hook 是否允许继续执行。</p>
 */
public enum G3HookDecision {

    /**
     * 允许继续执行。
     */
    ALLOW,

    /**
     * 阻断执行。
     */
    BLOCK
}
