/**
 * Sandbox心跳Hook
 *
 * 功能：
 * - 每60秒向后端发送心跳请求
 * - 保持Sandbox活跃状态，防止被提前终止
 * - 自动启动和停止心跳
 * - 提供心跳状态监控
 *
 * 使用场景：
 * - 原型预览页面
 * - 代码生成页面
 * - 任何需要长时间保持Sandbox运行的场景
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-12-10
 */

'use client';

import { useEffect, useRef } from 'react';

/**
 * Sandbox心跳Hook参数接口
 */
export interface UseSandboxHeartbeatOptions {
  /** Sandbox ID */
  sandboxId: string | null;
  /** 心跳间隔（毫秒），默认60000（60秒） */
  interval?: number;
  /** 是否启用心跳，默认true */
  enabled?: boolean;
  /** 心跳成功回调 */
  onHeartbeatSuccess?: () => void;
  /** 心跳失败回调 */
  onHeartbeatError?: (error: Error) => void;
}

/**
 * 发送Sandbox心跳请求
 */
async function sendHeartbeat(sandboxId: string): Promise<void> {
  try {
    const response = await fetch('/api/v1/openlovable/heartbeat', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ sandboxId }),
    });

    if (!response.ok) {
      throw new Error(`心跳请求失败: HTTP ${response.status}`);
    }

    const data = await response.json();

    if (!data.success) {
      throw new Error(data.error || '心跳请求失败');
    }

    console.log(`[Sandbox心跳] Sandbox ${sandboxId} 心跳成功`);
  } catch (error) {
    console.error(`[Sandbox心跳] Sandbox ${sandboxId} 心跳失败:`, error);
    throw error;
  }
}

/**
 * useSandboxHeartbeat - Sandbox心跳Hook
 *
 * 用法示例：
 * ```tsx
 * const { lastHeartbeatTime, isActive } = useSandboxHeartbeat({
 *   sandboxId: 'sandbox-123',
 *   interval: 60000,
 *   onHeartbeatSuccess: () => console.log('心跳成功'),
 *   onHeartbeatError: (error) => console.error('心跳失败', error),
 * });
 * ```
 *
 * @param options - 心跳配置选项
 * @returns 心跳状态
 */
export function useSandboxHeartbeat(options: UseSandboxHeartbeatOptions) {
  const {
    sandboxId,
    interval = 60000, // 默认60秒
    enabled = true,
    onHeartbeatSuccess,
    onHeartbeatError,
  } = options;

  const intervalIdRef = useRef<NodeJS.Timeout | null>(null);
  const lastHeartbeatTimeRef = useRef<number | null>(null);

  useEffect(() => {
    // 如果未启用或无sandboxId，则不启动心跳
    if (!enabled || !sandboxId) {
      return;
    }

    console.log(`[Sandbox心跳] 启动心跳监控: ${sandboxId}, 间隔: ${interval}ms`);

    // 立即发送第一次心跳
    (async () => {
      try {
        await sendHeartbeat(sandboxId);
        lastHeartbeatTimeRef.current = Date.now();
        onHeartbeatSuccess?.();
      } catch (error) {
        onHeartbeatError?.(error as Error);
      }
    })();

    // 设置定时心跳
    intervalIdRef.current = setInterval(async () => {
      try {
        await sendHeartbeat(sandboxId);
        lastHeartbeatTimeRef.current = Date.now();
        onHeartbeatSuccess?.();
      } catch (error) {
        onHeartbeatError?.(error as Error);
      }
    }, interval);

    // 清理函数
    return () => {
      if (intervalIdRef.current) {
        console.log(`[Sandbox心跳] 停止心跳监控: ${sandboxId}`);
        clearInterval(intervalIdRef.current);
        intervalIdRef.current = null;
      }
    };
  }, [sandboxId, interval, enabled, onHeartbeatSuccess, onHeartbeatError]);

  return {
    /** 最后一次心跳时间（时间戳） */
    lastHeartbeatTime: lastHeartbeatTimeRef.current,
    /** 心跳是否活跃 */
    isActive: enabled && !!sandboxId && !!intervalIdRef.current,
  };
}
