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

  const cleanedUpRef = useRef(false);

  useEffect(() => {
    // 如果未启用或无sandboxId，则不设置清理
    if (!enabled || !sandboxId) {
      return;
    }

    // 重置清理标记（当sandboxId变化时）
    cleanedUpRef.current = false;

    console.log(`[Sandbox清理] 注册清理监听器: ${sandboxId}`);

    /**
     * 执行清理
     */
    const performCleanup = async () => {
      // 避免重复清理
      if (cleanedUpRef.current) {
        return;
      }

      cleanedUpRef.current = true;

      console.log(`[Sandbox清理] 开始清理Sandbox: ${sandboxId}`);
      onBeforeCleanup?.();

      const success = await cleanupSandbox(sandboxId);

      if (success) {
        onCleanupComplete?.();
      }
    };

    /**
     * beforeunload事件处理（页面卸载）
     */
    const handleBeforeUnload = () => {
      performCleanup();
    };

    /**
     * visibilitychange事件处理（页面隐藏）
     */
    const handleVisibilityChange = () => {
      if (cleanupOnHide && document.visibilityState === 'hidden') {
        console.log(`[Sandbox清理] 页面隐藏，触发清理`);
        performCleanup();
      }
    };

    // 注册事件监听器
    window.addEventListener('beforeunload', handleBeforeUnload);

    if (cleanupOnHide) {
      document.addEventListener('visibilitychange', handleVisibilityChange);
    }

    // 清理函数（组件卸载时）
    return () => {
      console.log(`[Sandbox清理] 组件卸载，执行清理: ${sandboxId}`);

      window.removeEventListener('beforeunload', handleBeforeUnload);

      if (cleanupOnHide) {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
      }

      // 组件卸载时同步清理
      performCleanup();
    };
  }, [sandboxId, cleanupOnHide, enabled, onBeforeCleanup, onCleanupComplete]);

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
