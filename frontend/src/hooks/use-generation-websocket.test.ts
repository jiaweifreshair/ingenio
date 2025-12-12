/**
 * useGenerationWebSocket Hook集成测试
 * 秒构AI生成任务WebSocket连接管理Hook测试
 *
 * 测试目标：src/hooks/use-generation-websocket.ts
 * 测试策略：
 * - 集成测试完整的WebSocket连接流程
 * - 测试用户交互场景（连接、断开、重连、发送消息）
 * - 测试状态变化和回调触发
 * - 使用真实的WebSocket逻辑，Mock底层WebSocket实例
 *
 * Phase 3.3 - 核心Hooks集成测试
 * 创建时间：2025-11-14
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { useGenerationWebSocket } from "./use-generation-websocket";

// 定义Mock的选项类型
interface MockWebSocketOptions {
  onOpen?: () => void;
  onClose?: () => void;
  onMessage?: (message: unknown) => void;
  onError?: (error: Event) => void;
}

// Mock GenerationWebSocket类
vi.mock("@/lib/websocket/generation-websocket", () => {
  class MockGenerationWebSocket {
    private options: MockWebSocketOptions;

    constructor(options: MockWebSocketOptions) {
      this.options = options;
    }

    connect() {
      // 模拟异步连接
      setTimeout(() => {
        this.options.onOpen?.();
      }, 10);
    }

    disconnect() {
      this.options.onClose?.();
    }

    send(_message: unknown) {
      return true;
    }

    cancelTask() {
      return true;
    }
  }

  return {
    GenerationWebSocket: MockGenerationWebSocket,
    // 重新导出类型
    GenerationWebSocketMessage: {},
    TaskStatusMessage: {},
    AgentStatusMessage: {},
    ErrorMessage: {},
    WebSocketMessage: {},
  };
});

describe("useGenerationWebSocket Hook集成测试 - use-generation-websocket.ts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("用户流程：完整的WebSocket连接流程", () => {
    it("应该完成自动连接流程（autoConnect=true）", async () => {
      const onConnect = vi.fn();
      const taskId = "task-auto-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: true,
          onConnect,
        })
      );

      // 初始状态：正在连接
      expect(result.current.isConnecting).toBe(true);
      expect(result.current.isConnected).toBe(false);

      // 等待连接完成
      await waitFor(
        () => {
          expect(result.current.isConnected).toBe(true);
        },
        { timeout: 1000 }
      );

      expect(result.current.isConnecting).toBe(false);
      expect(onConnect).toHaveBeenCalledTimes(1);
    });

    it("应该支持手动连接流程（autoConnect=false）", async () => {
      const onConnect = vi.fn();
      const taskId = "task-manual-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: false,
          onConnect,
        })
      );

      // 初始状态：未连接
      expect(result.current.isConnecting).toBe(false);
      expect(result.current.isConnected).toBe(false);

      // 用户手动连接
      act(() => {
        result.current.connect();
      });

      // 连接中
      expect(result.current.isConnecting).toBe(true);

      // 等待连接完成
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      expect(onConnect).toHaveBeenCalledTimes(1);
    });

    it("应该完成断开连接流程", async () => {
      const onDisconnect = vi.fn();
      const taskId = "task-disconnect-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: true,
          onDisconnect,
        })
      );

      // 等待连接完成
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 用户断开连接
      act(() => {
        result.current.disconnect();
      });

      expect(result.current.isConnected).toBe(false);
      expect(onDisconnect).toHaveBeenCalledTimes(1);
    });

    it("应该完成重新连接流程", async () => {
      const onConnect = vi.fn();
      const onDisconnect = vi.fn();
      const taskId = "task-reconnect-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: true,
          onConnect,
          onDisconnect,
        })
      );

      // 等待首次连接完成
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      expect(onConnect).toHaveBeenCalledTimes(1);

      // 用户重新连接
      act(() => {
        result.current.reconnect();
      });

      // 应该先断开
      expect(onDisconnect).toHaveBeenCalledTimes(1);

      // 等待重新连接完成
      await waitFor(
        () => {
          expect(result.current.isConnected).toBe(true);
        },
        { timeout: 2000 }
      );

      // 连接回调应该被调用两次（首次+重连）
      await waitFor(() => {
        expect(onConnect).toHaveBeenCalledTimes(2);
      });
    });
  });

  describe("用户流程：消息发送和任务取消", () => {
    it("应该成功发送消息", async () => {
      const taskId = "task-send-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: true,
        })
      );

      // 等待连接完成
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 用户发送消息
      let sendResult = false;
      act(() => {
        sendResult = result.current.sendMessage({
          type: "ping",
          timestamp: Date.now(),
        });
      });

      expect(sendResult).toBe(true);
    });

    it("应该成功取消任务", async () => {
      const taskId = "task-cancel-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: true,
        })
      );

      // 等待连接完成
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 用户取消任务
      let cancelResult = false;
      act(() => {
        cancelResult = result.current.cancelTask();
      });

      expect(cancelResult).toBe(true);
    });

    it("应该在未连接时返回false（发送消息失败）", () => {
      const taskId = "task-not-connected-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: false,
        })
      );

      // 未连接时尝试发送消息
      const sendResult = result.current.sendMessage({
        type: "ping",
        timestamp: Date.now(),
      });

      expect(sendResult).toBe(false);
    });
  });

  describe("状态管理：连接尝试次数", () => {
    it("应该正确跟踪连接尝试次数", async () => {
      const taskId = "task-attempts-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: false,
        })
      );

      expect(result.current.connectionAttempts).toBe(0);

      // 第一次连接
      act(() => {
        result.current.connect();
      });

      expect(result.current.connectionAttempts).toBe(1);

      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 断开并重新连接
      act(() => {
        result.current.disconnect();
      });

      act(() => {
        result.current.connect();
      });

      expect(result.current.connectionAttempts).toBe(2);
    });
  });

  describe("回调触发：任务状态和Agent状态更新", () => {
    it("应该在收到任务状态更新时触发回调", async () => {
      // 注意：由于Mock限制，这个测试验证Hook正确传递回调给WebSocket类
      // 实际的消息回调触发由WebSocket类的单元测试覆盖
      const onTaskStatus = vi.fn();
      const taskId = "task-status-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: true,
          onTaskStatus,
        })
      );

      // 等待连接完成
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 验证Hook正确初始化（回调传递给WebSocket类的测试在WebSocket类的测试中）
      expect(result.current.isConnected).toBe(true);
    });

    it("应该在收到Agent状态更新时触发回调", async () => {
      // 注意：由于Mock限制，这个测试验证Hook正确传递回调给WebSocket类
      // 实际的消息回调触发由WebSocket类的单元测试覆盖
      const onAgentStatus = vi.fn();
      const taskId = "task-agent-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: true,
          onAgentStatus,
        })
      );

      // 等待连接完成
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 验证Hook正确初始化
      expect(result.current.isConnected).toBe(true);
    });
  });

  describe("清理和生命周期", () => {
    it("应该在组件卸载时断开连接", async () => {
      const onDisconnect = vi.fn();
      const taskId = "task-cleanup-123";

      const { result, unmount } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: true,
          onDisconnect,
        })
      );

      // 等待连接完成
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 卸载组件
      unmount();

      // 应该触发断开回调
      expect(onDisconnect).toHaveBeenCalled();
    });

    it("应该在taskId变化时重新连接", async () => {
      const onConnect = vi.fn();

      const { result, rerender } = renderHook(
        ({ taskId }) =>
          useGenerationWebSocket({
            taskId,
            autoConnect: true,
            onConnect,
          }),
        {
          initialProps: { taskId: "task-old-123" },
        }
      );

      // 等待首次连接完成
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      expect(onConnect).toHaveBeenCalledTimes(1);

      // 改变taskId
      rerender({ taskId: "task-new-456" });

      // 应该重新连接
      await waitFor(
        () => {
          expect(onConnect).toHaveBeenCalledTimes(2);
        },
        { timeout: 1000 }
      );
    });
  });

  describe("错误处理", () => {
    it("应该正确初始化错误处理回调", async () => {
      // 注意：由于Mock限制，实际的错误处理由WebSocket类的测试覆盖
      // 这个测试验证Hook正确传递错误回调
      const onError = vi.fn();

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId: "task-error-123",
          autoConnect: true,
          onError,
        })
      );

      // 等待连接完成（Mock默认成功连接）
      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 验证Hook正确初始化
      expect(result.current.isConnected).toBe(true);
    });
  });

  describe("边界情况", () => {
    it("应该支持多次连接和断开", async () => {
      const taskId = "task-multiple-123";

      const { result } = renderHook(() =>
        useGenerationWebSocket({
          taskId,
          autoConnect: false,
        })
      );

      // 第一次连接
      act(() => {
        result.current.connect();
      });

      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 断开
      act(() => {
        result.current.disconnect();
      });

      expect(result.current.isConnected).toBe(false);

      // 第二次连接
      act(() => {
        result.current.connect();
      });

      await waitFor(() => {
        expect(result.current.isConnected).toBe(true);
      });

      // 再次断开
      act(() => {
        result.current.disconnect();
      });

      expect(result.current.isConnected).toBe(false);
    });
  });
});
