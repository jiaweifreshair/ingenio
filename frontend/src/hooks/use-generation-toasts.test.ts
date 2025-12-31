/**
 * useGenerationToasts Hook集成测试
 * 秒构AI生成任务Toast通知Hook测试
 *
 * 测试目标：src/hooks/use-generation-toasts.ts
 * 测试策略：
 * - 集成测试完整的Toast通知流程
 * - 测试用户可见的通知行为
 * - 测试不同事件类型的通知触发
 * - 测试配置选项对通知行为的影响
 *
 * Phase 3.3 - 核心Hooks集成测试
 * 创建时间：2025-11-14
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { useGenerationToasts } from "./use-generation-toasts";
import type { TaskStatusMessage, AgentStatusMessage, ErrorMessage } from "@/lib/websocket/generation-websocket";
import { AgentState } from "@/types/wizard";

// Mock use-toast hook
const mockToast = vi.fn();
vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({
    toast: mockToast,
  }),
}));

describe("useGenerationToasts Hook集成测试 - use-generation-toasts.ts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("用户流程：WebSocket连接状态通知", () => {
    it("应该在连接建立时显示通知", async () => {
      const { rerender } = renderHook(({ isConnected }) => useGenerationToasts(isConnected), {
        initialProps: { isConnected: false },
      });

      // 用户WebSocket连接成功
      rerender({ isConnected: true });

      await waitFor(() => {
        expect(mockToast).toHaveBeenCalledWith(
          expect.objectContaining({
            title: "✅ WebSocket已连接",
            description: "实时进度推送已启动",
            duration: 3000,
          })
        );
      });
    });

    it("应该在连接断开时显示警告通知", async () => {
      const { rerender } = renderHook(({ isConnected }) => useGenerationToasts(isConnected), {
        initialProps: { isConnected: true },
      });

      // 用户WebSocket连接断开
      rerender({ isConnected: false });

      await waitFor(() => {
        expect(mockToast).toHaveBeenCalledWith(
          expect.objectContaining({
            title: "⚠️ WebSocket连接断开",
            description: "正在尝试重新连接...",
            variant: "destructive",
          })
        );
      });
    });

    it("应该支持禁用连接状态通知", async () => {
      const { rerender } = renderHook(
        ({ isConnected }) =>
          useGenerationToasts(isConnected, {
            showConnectionNotifications: false,
          }),
        {
          initialProps: { isConnected: false },
        }
      );

      // 连接状态变化
      rerender({ isConnected: true });

      // 等待一段时间
      await new Promise((resolve) => setTimeout(resolve, 100));

      // 不应该显示通知
      expect(mockToast).not.toHaveBeenCalled();
    });

    it("应该忽略首次加载时的连接状态（避免误触发）", () => {
      renderHook(() => useGenerationToasts(true));

      // 首次加载不应该触发通知
      expect(mockToast).not.toHaveBeenCalled();
    });
  });

  describe("用户流程：任务状态变化通知", () => {
    it("应该在任务完成时显示成功通知", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const completedMessage: TaskStatusMessage = {
        type: "taskStatus",
        taskId: "task-123",
        currentAgent: "PlanAgent",
        status: "COMPLETED",
        progress: 100,
        timestamp: Date.now(),
      };

      act(() => {
        result.current.showTaskStatusNotification(completedMessage);
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "🎉 应用生成完成",
          description: expect.stringContaining("预览"),
          duration: 5000,
        })
      );
    });

    it("应该在任务失败时显示错误通知", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const failedMessage: TaskStatusMessage = {
        type: "taskStatus",
        taskId: "task-123",
        currentAgent: "PlanAgent",
        status: "FAILED",
        progress: 50,
        timestamp: Date.now(),
      };

      act(() => {
        result.current.showTaskStatusNotification(failedMessage);
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "❌ 应用生成失败",
          description: "生成过程中出现错误",
          variant: "destructive",
        })
      );
    });

    it("应该忽略进行中的任务状态（避免过多通知）", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const runningMessage: TaskStatusMessage = {
        type: "taskStatus",
        taskId: "task-123",
        currentAgent: "PlanAgent",
        status: "RUNNING",
        progress: 50,
        timestamp: Date.now(),
      };

      act(() => {
        result.current.showTaskStatusNotification(runningMessage);
      });

      // 不应该显示通知（只通知完成/失败）
      expect(mockToast).not.toHaveBeenCalled();
    });
  });

  describe("用户流程：Agent状态变化通知", () => {
    it("应该在Agent启动时显示通知（如果启用）", () => {
      const { result } = renderHook(() =>
        useGenerationToasts(true, {
          showAgentStartNotifications: true,
        })
      );

      const agentStartMessage: AgentStatusMessage = {
        type: "agentStatus",
        agentInfo: {
          agentType: "PlanAgent",
          status: AgentState.RUNNING,
          message: "正在规划应用架构",
          progress: 10,
          timestamp: Date.now(),
        },
      };

      act(() => {
        result.current.showAgentStatusNotification(agentStartMessage);
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "🚀 PlanAgent启动",
          description: "正在执行任务...",
          duration: 2000,
        })
      );
    });

    it("应该在Agent完成时显示成功通知", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const agentCompleteMessage: AgentStatusMessage = {
        type: "agentStatus",
        agentInfo: {
          agentType: "ExecuteAgent",
          status: AgentState.COMPLETED,
          message: "代码生成完成",
          progress: 100,
          timestamp: Date.now(),
        },
      };

      act(() => {
        result.current.showAgentStatusNotification(agentCompleteMessage);
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: expect.stringContaining("ExecuteAgent完成"),
          description: "任务执行成功",
          duration: 3000,
        })
      );
    });

    it("应该在Agent失败时显示错误通知", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const agentFailedMessage: AgentStatusMessage = {
        type: "agentStatus",
        agentInfo: {
          agentType: "ValidateAgent",
          status: AgentState.FAILED,
          message: "验证失败：缺少必需的配置文件",
          progress: 75,
          timestamp: Date.now(),
        },
      };

      act(() => {
        result.current.showAgentStatusNotification(agentFailedMessage);
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "❌ ValidateAgent失败",
          description: "验证失败：缺少必需的配置文件",
          variant: "destructive",
        })
      );
    });

    it("应该默认禁用Agent启动通知（避免过于频繁）", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const agentStartMessage: AgentStatusMessage = {
        type: "agentStatus",
        agentInfo: {
          agentType: "PlanAgent",
          status: AgentState.RUNNING,
          message: "正在规划",
          progress: 10,
          timestamp: Date.now(),
        },
      };

      act(() => {
        result.current.showAgentStatusNotification(agentStartMessage);
      });

      // 默认不显示启动通知
      expect(mockToast).not.toHaveBeenCalled();
    });
  });

  describe("用户流程：错误通知", () => {
    it("应该显示错误通知", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const errorMessage: ErrorMessage = {
        type: "error",
        error: "WebSocket连接超时，请检查网络",
        timestamp: Date.now(),
      };

      act(() => {
        result.current.showErrorNotification(errorMessage);
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "❌ 错误",
          description: "WebSocket连接超时，请检查网络",
          variant: "destructive",
        })
      );
    });

    it("应该处理无错误信息的情况", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const errorMessage: ErrorMessage = {
        type: "error",
        error: "",
        timestamp: Date.now(),
      };

      act(() => {
        result.current.showErrorNotification(errorMessage);
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "❌ 错误",
          description: "发生未知错误",
          variant: "destructive",
        })
      );
    });

    it("应该支持禁用错误通知", () => {
      const { result } = renderHook(() =>
        useGenerationToasts(true, {
          showErrorNotifications: false,
        })
      );

      const errorMessage: ErrorMessage = {
        type: "error",
        error: "测试错误",
        timestamp: Date.now(),
      };

      act(() => {
        result.current.showErrorNotification(errorMessage);
      });

      expect(mockToast).not.toHaveBeenCalled();
    });
  });

  describe("配置选项：通知控制", () => {
    it("应该支持全局禁用所有通知", async () => {
      const { result, rerender } = renderHook(
        ({ isConnected }) =>
          useGenerationToasts(isConnected, {
            enabled: false,
          }),
        {
          initialProps: { isConnected: false },
        }
      );

      // 连接状态变化
      rerender({ isConnected: true });

      // 显示任务完成通知
      const completedMessage: TaskStatusMessage = {
        type: "taskStatus",
        taskId: "task-123",
        currentAgent: "PlanAgent",
        status: "COMPLETED",
        progress: 100,
        timestamp: Date.now(),
      };

      act(() => {
        result.current.showTaskStatusNotification(completedMessage);
      });

      // 等待一段时间
      await new Promise((resolve) => setTimeout(resolve, 100));

      // 不应该显示任何通知
      expect(mockToast).not.toHaveBeenCalled();
    });

    it("应该支持单独控制Agent完成通知", () => {
      const { result } = renderHook(() =>
        useGenerationToasts(true, {
          showAgentCompleteNotifications: false,
        })
      );

      const agentCompleteMessage: AgentStatusMessage = {
        type: "agentStatus",
        agentInfo: {
          agentType: "PlanAgent",
          status: AgentState.COMPLETED,
          message: "完成",
          progress: 100,
          timestamp: Date.now(),
        },
      };

      act(() => {
        result.current.showAgentStatusNotification(agentCompleteMessage);
      });

      // 不应该显示Agent完成通知
      expect(mockToast).not.toHaveBeenCalled();
    });
  });

  describe("直接调用方法：手动触发通知", () => {
    it("应该支持手动显示连接成功通知", () => {
      const { result } = renderHook(() => useGenerationToasts(false));

      act(() => {
        result.current.showConnectedNotification();
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "✅ WebSocket已连接",
        })
      );
    });

    it("应该支持手动显示断开连接通知", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      act(() => {
        result.current.showDisconnectedNotification();
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "⚠️ WebSocket连接断开",
        })
      );
    });

    it("应该支持手动显示Agent启动通知", () => {
      const { result } = renderHook(() =>
        useGenerationToasts(true, {
          showAgentStartNotifications: true,
        })
      );

      act(() => {
        result.current.showAgentStartNotification("CustomAgent");
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "🚀 CustomAgent启动",
        })
      );
    });

    it("应该支持手动显示Agent完成通知（带耗时）", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      act(() => {
        result.current.showAgentCompleteNotification("DataAgent", 5400); // 5.4秒
      });

      // 实现将耗时放在title中，如 "✅ DataAgent完成 (耗时 5.4s)"
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "✅ DataAgent完成 (耗时 5.4s)",
        })
      );
    });

    it("应该支持手动显示Agent失败通知", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      act(() => {
        result.current.showAgentFailedNotification("TestAgent", "测试失败原因");
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "❌ TestAgent失败",
          description: "测试失败原因",
        })
      );
    });
  });

  describe("边界情况和健壮性", () => {
    it("应该处理缺少agentInfo的Agent状态消息", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const incompleteMessage: AgentStatusMessage = {
        type: "agentStatus",
        timestamp: Date.now(),
      } as unknown as AgentStatusMessage;

      // 不应该抛出错误
      expect(() => {
        act(() => {
          result.current.showAgentStatusNotification(incompleteMessage);
        });
      }).not.toThrow();
    });

    it("应该处理缺少message的错误通知", () => {
      const { result } = renderHook(() => useGenerationToasts(true));

      const emptyError: ErrorMessage = {
        type: "error",
        error: "",
        timestamp: Date.now(),
      } as unknown as ErrorMessage;

      act(() => {
        result.current.showErrorNotification(emptyError);
      });

      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          description: "发生未知错误",
        })
      );
    });

    it("应该支持连续多次状态变化", async () => {
      const { rerender } = renderHook(({ isConnected }) => useGenerationToasts(isConnected), {
        initialProps: { isConnected: false },
      });

      // 连接
      rerender({ isConnected: true });

      await waitFor(() => {
        expect(mockToast).toHaveBeenCalledTimes(1);
      });

      // 断开
      rerender({ isConnected: false });

      await waitFor(() => {
        expect(mockToast).toHaveBeenCalledTimes(2);
      });

      // 再次连接
      rerender({ isConnected: true });

      await waitFor(() => {
        expect(mockToast).toHaveBeenCalledTimes(3);
      });
    });
  });
});
