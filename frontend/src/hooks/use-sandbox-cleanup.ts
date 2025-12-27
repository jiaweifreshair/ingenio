/**
 * Sandbox自动清理Hook
 *
 * 功能：
 * - 页面卸载时自动清理Sandbox资源
 * - 防止E2B资源泄漏和不必要的计费
 * - 使用sendBeacon API确保请求可靠发送
 * - 支持页面隐藏时清理（可选）
 *
 * 使用场景：
 * - 原型预览页面
 * - 代码生成页面
 * - 任何创建Sandbox的场景
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-12-10
 */

'use client';

import { useEffect, useRef } from 'react';

/**
 * Sandbox清理Hook参数接口
 */
export interface UseSandboxCleanupOptions {
  /** Sandbox ID */
  sandboxId: string | null;
  /** 是否在页面隐藏时清理，默认false */
  cleanupOnHide?: boolean;
  /** 是否启用清理，默认true */
  enabled?: boolean;
  /** 清理前回调 */
  onBeforeCleanup?: () => void;
  /** 清理完成回调 */
  onCleanupComplete?: () => void;
}

/**
 * 发送Sandbox清理请求
 *
 * 优先使用sendBeacon API（可靠性最高），
 * 如果不支持则fallback到fetch with keepalive
 */
async function cleanupSandbox(sandboxId: string): Promise<boolean> {
  const cleanupUrl = '/api/v1/openlovable/cleanup';
  const payload = JSON.stringify({ sandboxId });

  // 方案1: sendBeacon API（最可靠）
  if (typeof navigator !== 'undefined' && navigator.sendBeacon) {
    const blob = new Blob([payload], { type: 'application/json' });
    const success = navigator.sendBeacon(cleanupUrl, blob);

    if (success) {
      console.log(`[Sandbox清理] 使用sendBeacon清理Sandbox: ${sandboxId}`);
      return true;
    }

    console.warn(`[Sandbox清理] sendBeacon失败，尝试fetch fallback`);
  }

  // 方案2: fetch with keepalive（fallback）
  try {
    await fetch(cleanupUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: payload,
      keepalive: true, // 确保请求在页面卸载后仍能完成
    });

    console.log(`[Sandbox清理] 使用fetch清理Sandbox: ${sandboxId}`);
    return true;
  } catch (error) {
    console.error(`[Sandbox清理] 清理失败:`, error);
    return false;
  }
}

/**
 * useSandboxCleanup - Sandbox自动清理Hook
 *
 * 用法示例：
 * ```tsx
 * useSandboxCleanup({
 *   sandboxId: 'sandbox-123',
 *   cleanupOnHide: false,
 *   onBeforeCleanup: () => console.log('准备清理'),
 *   onCleanupComplete: () => console.log('清理完成'),
 * });
 * ```
 *
 * @param options - 清理配置选项
 */
export function useSandboxCleanup(options: UseSandboxCleanupOptions) {
  const {
    sandboxId,
    cleanupOnHide = false,
    enabled = true,
    onBeforeCleanup,
    onCleanupComplete,
  } = options;

  // 是否已清理
  const cleanedUpRef = useRef(false);
  // 是否正在卸载（用于区分真正的组件卸载和 effect 依赖变化）
  const isUnmountingRef = useRef(false);
  // 当前有效的 sandboxId（用于卸载时清理）
  const currentSandboxIdRef = useRef<string | null>(null);
  // 回调函数的 ref（避免 effect 依赖变化）
  const onBeforeCleanupRef = useRef(onBeforeCleanup);
  const onCleanupCompleteRef = useRef(onCleanupComplete);

  // 同步更新回调 ref
  onBeforeCleanupRef.current = onBeforeCleanup;
  onCleanupCompleteRef.current = onCleanupComplete;

  /**
   * 执行清理（提取到 hook 外层，避免每次 effect 都重新创建）
   */
  const performCleanup = async (targetSandboxId: string | null) => {
    // 避免重复清理或无效 sandboxId
    if (cleanedUpRef.current || !targetSandboxId) {
      return;
    }

    cleanedUpRef.current = true;

    console.log(`[Sandbox清理] 开始清理Sandbox: ${targetSandboxId}`);
    onBeforeCleanupRef.current?.();

    const success = await cleanupSandbox(targetSandboxId);

    if (success) {
      onCleanupCompleteRef.current?.();
    }
  };

  // 专用于组件卸载的 effect（空依赖数组，仅在卸载时执行清理）
  useEffect(() => {
    return () => {
      // 标记组件正在卸载
      isUnmountingRef.current = true;

      // 组件卸载时执行清理
      if (currentSandboxIdRef.current && !cleanedUpRef.current) {
        console.log(`[Sandbox清理] 组件卸载，执行清理: ${currentSandboxIdRef.current}`);
        performCleanup(currentSandboxIdRef.current);
      }
    };
  }, []);

  // 主 effect：注册事件监听器
  useEffect(() => {
    // 如果未启用或无sandboxId，则不设置清理
    if (!enabled || !sandboxId) {
      return;
    }

    // 更新当前 sandboxId ref
    currentSandboxIdRef.current = sandboxId;
    // 重置清理标记（当sandboxId变化时）
    cleanedUpRef.current = false;

    console.log(`[Sandbox清理] 注册清理监听器: ${sandboxId}`);

    /**
     * beforeunload事件处理（页面卸载/刷新）
     */
    const handleBeforeUnload = () => {
      performCleanup(sandboxId);
    };

    /**
     * visibilitychange事件处理（页面隐藏）
     */
    const handleVisibilityChange = () => {
      if (cleanupOnHide && document.visibilityState === 'hidden') {
        console.log(`[Sandbox清理] 页面隐藏，触发清理`);
        performCleanup(sandboxId);
      }
    };

    // 注册事件监听器
    window.addEventListener('beforeunload', handleBeforeUnload);

    if (cleanupOnHide) {
      document.addEventListener('visibilitychange', handleVisibilityChange);
    }

    // 清理函数（仅移除事件监听器，不执行 sandbox 清理）
    // 真正的 sandbox 清理只在组件卸载时执行（由上面的空依赖 effect 处理）
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);

      if (cleanupOnHide) {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
      }

      // 重要：这里不调用 performCleanup()
      // 因为这个清理函数会在 enabled 变化时触发（如 isGenerating 从 true 变 false）
      // 真正的组件卸载清理由空依赖的 effect 处理
      console.log(`[Sandbox清理] effect 重新运行，移除事件监听器（不清理 sandbox）`);
    };
  }, [sandboxId, cleanupOnHide, enabled]);

  return {
    /** 是否已清理 */
    isCleanedUp: cleanedUpRef.current,
    /** 手动触发清理 */
    manualCleanup: async () => {
      if (sandboxId && !cleanedUpRef.current) {
        await cleanupSandbox(sandboxId);
        cleanedUpRef.current = true;
      }
    },
  };
}
