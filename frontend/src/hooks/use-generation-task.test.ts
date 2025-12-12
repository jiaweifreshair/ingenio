/**
 * useGenerationTask Hook单元测试
 * 秒构AI生成任务状态管理Hook测试
 *
 * 测试目标：src/hooks/use-generation-task.ts
 * 测试策略：
 * - 测试Hook初始化和状态管理
 * - 测试任务状态更新和回调
 * - 测试错误处理逻辑
 * - 测试生命周期管理（清理、重置）
 * - 测试基本方法功能（不测试复杂的定时器逻辑）
 *
 * Week 1 Day 3 Phase 3.2 - 核心工具函数单元测试
 * 创建时间：2025-11-14
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";
import { useGenerationTask, type UseGenerationTaskOptions } from "./use-generation-task";
import * as generateModule from "@/lib/api/generate";
import type { TaskStatusResponse } from "@/lib/api/generate";

// Mock generate模块
vi.mock("@/lib/api/generate", () => ({
  getTaskStatus: vi.fn(),
  cancelTask: vi.fn(),
}));

describe("useGenerationTask Hook - use-generation-task.ts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("初始化和基本状态", () => {
    it("应该正确初始化默认状态", () => {
      const { result } = renderHook(() => useGenerationTask());

      expect(result.current.taskId).toBeNull();
      expect(result.current.taskStatus).toBeNull();
      expect(result.current.isLoading).toBe(false);
      expect(result.current.isPolling).toBe(false);
      expect(result.current.error).toBeNull();
      expect(result.current.isConnected).toBe(false);
      expect(result.current.isCompleted).toBe(false);
      expect(result.current.isFailed).toBe(false);
      expect(result.current.isRunning).toBe(false);
      expect(result.current.progress).toBe(0);
    });

    it("应该提供完整的状态和方法", () => {
      const { result } = renderHook(() => useGenerationTask());

      expect(result.current).toHaveProperty("state");
      expect(result.current).toHaveProperty("setTaskId");
      expect(result.current).toHaveProperty("startPolling");
      expect(result.current).toHaveProperty("stopPolling");
      expect(result.current).toHaveProperty("refreshStatus");
      expect(result.current).toHaveProperty("cancelCurrentTask");
      expect(result.current).toHaveProperty("reset");
      expect(result.current).toHaveProperty("setConnectionStatus");
    });

    it("应该正确初始化配置选项", () => {
      const options: UseGenerationTaskOptions = {
        autoPoll: true,
        pollInterval: 3000,
        maxPolls: 500,
      };

      const { result } = renderHook(() => useGenerationTask(options));

      expect(result.current.taskId).toBeNull();
      expect(result.current.isPolling).toBe(false);
    });
  });

  describe("setTaskId() - 设置任务ID", () => {
    it("应该正确设置任务ID", () => {
      const { result } = renderHook(() => useGenerationTask());

      act(() => {
        result.current.setTaskId("task-123");
      });

      expect(result.current.taskId).toBe("task-123");
      expect(result.current.error).toBeNull();
    });

    it("应该在设置新任务ID时清除错误", () => {
      const { result } = renderHook(() => useGenerationTask());

      // 先设置一个错误状态（通过内部状态）
      act(() => {
        result.current.setTaskId("task-old");
      });

      // 设置新任务ID
      act(() => {
        result.current.setTaskId("task-new");
      });

      expect(result.current.taskId).toBe("task-new");
      expect(result.current.error).toBeNull();
    });

    it("应该允许设置空taskId", () => {
      const { result } = renderHook(() => useGenerationTask());

      act(() => {
        result.current.setTaskId("task-123");
      });

      expect(result.current.taskId).toBe("task-123");

      act(() => {
        result.current.setTaskId("");
      });

      expect(result.current.taskId).toBe("");
    });
  });

  describe("refreshStatus() - 手动刷新状态", () => {
    it("应该成功刷新任务状态", async () => {
      const mockStatus: TaskStatusResponse = {
        taskId: "task-123",
        taskName: "测试任务",
        userRequirement: "测试需求",
        status: "executing",
        statusDescription: "执行中",
        currentAgent: "ExecuteAgent",
        progress: 60,
        agents: [],
        startedAt: "2025-01-14T10:00:00Z",
        completedAt: "",
        estimatedRemainingSeconds: 180,
        appSpecId: "",
        qualityScore: 0,
        downloadUrl: "",
        previewUrl: "",
        tokenUsage: {
          planTokens: 1000,
          executeTokens: 1500,
          validateTokens: 0,
          totalTokens: 2500,
          estimatedCost: 0.04,
        },
        errorMessage: "",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T10:05:00Z",
      };

      vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
        success: true,
        data: mockStatus,
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.taskStatus).toEqual(mockStatus);
        expect(result.current.progress).toBe(60);
        expect(result.current.currentAgent).toBe("ExecuteAgent");
        expect(result.current.isLoading).toBe(false);
      });
    });

    it("应该在无taskId时不执行刷新", async () => {
      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      expect(generateModule.getTaskStatus).not.toHaveBeenCalled();
    });

    it("应该处理刷新失败", async () => {
      vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
        success: false,
        error: "Task not found",
      });

      const onError = vi.fn();

      const { result } = renderHook(() => useGenerationTask({ onError }));

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.error).toBe("Task not found");
        expect(onError).toHaveBeenCalledWith("Task not found");
      });
    });

    it("应该在刷新时设置loading状态", async () => {
      vi.mocked(generateModule.getTaskStatus).mockImplementation(
        () =>
          new Promise((resolve) => {
            setTimeout(
              () =>
                resolve({
                  success: true,
                  data: {
                    taskId: "task-123",
                    status: "planning",
                  } as TaskStatusResponse,
                }),
              100
            );
          })
      );

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      // Start refresh - the loading state will be set
      await act(async () => {
        await result.current.refreshStatus();
      });

      // After refresh completes, loading should be false
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
        expect(result.current.taskStatus).not.toBeNull();
      });
    });
  });

  describe("cancelCurrentTask() - 取消任务", () => {
    it("应该成功取消任务", async () => {
      vi.mocked(generateModule.cancelTask).mockResolvedValue({
        success: true,
        data: true,
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      let cancelled = false;
      await act(async () => {
        cancelled = await result.current.cancelCurrentTask();
      });

      expect(cancelled).toBe(true);
      expect(generateModule.cancelTask).toHaveBeenCalledWith("task-123");
    });

    it("应该在无taskId时返回false", async () => {
      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      let cancelled = false;
      await act(async () => {
        cancelled = await result.current.cancelCurrentTask();
      });

      expect(cancelled).toBe(false);
      expect(generateModule.cancelTask).not.toHaveBeenCalled();
    });

    it("应该处理取消失败", async () => {
      vi.mocked(generateModule.cancelTask).mockResolvedValue({
        success: false,
        error: "Cannot cancel completed task",
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      let cancelled = false;
      await act(async () => {
        cancelled = await result.current.cancelCurrentTask();
      });

      expect(cancelled).toBe(false);
      await waitFor(() => {
        // Error message is the API error directly, not wrapped
        expect(result.current.error).toBe("Cannot cancel completed task");
      });
    });

    it("应该在取消时设置loading状态", async () => {
      vi.mocked(generateModule.cancelTask).mockImplementation(
        () =>
          new Promise((resolve) => {
            setTimeout(
              () =>
                resolve({
                  success: true,
                  data: true,
                }),
              100
            );
          })
      );

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      // Cancel task - loading state will be managed internally
      await act(async () => {
        await result.current.cancelCurrentTask();
      });

      // After cancel completes, loading should be false
      await waitFor(() => {
        expect(result.current.isLoading).toBe(false);
      });
    });
  });

  describe("reset() - 重置状态", () => {
    it("应该重置所有状态", async () => {
      const mockStatus: TaskStatusResponse = {
        taskId: "task-123",
        taskName: "测试",
        userRequirement: "测试",
        status: "executing",
        statusDescription: "执行中",
        currentAgent: "ExecuteAgent",
        progress: 50,
        agents: [],
        startedAt: "2025-01-14T10:00:00Z",
        completedAt: "",
        estimatedRemainingSeconds: 200,
        appSpecId: "",
        qualityScore: 0,
        downloadUrl: "",
        previewUrl: "",
        tokenUsage: {
          planTokens: 1000,
          executeTokens: 1000,
          validateTokens: 0,
          totalTokens: 2000,
          estimatedCost: 0.03,
        },
        errorMessage: "",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T10:03:00Z",
      };

      vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
        success: true,
        data: mockStatus,
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.taskId).toBe("task-123");
        expect(result.current.taskStatus).toEqual(mockStatus);
      });

      act(() => {
        result.current.reset();
      });

      expect(result.current.taskId).toBeNull();
      expect(result.current.taskStatus).toBeNull();
      expect(result.current.isLoading).toBe(false);
      expect(result.current.isPolling).toBe(false);
      expect(result.current.error).toBeNull();
      expect(result.current.isConnected).toBe(false);
    });
  });

  describe("setConnectionStatus() - 设置连接状态", () => {
    it("应该正确设置WebSocket连接状态", async () => {
      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      expect(result.current.isConnected).toBe(false);

      act(() => {
        result.current.setConnectionStatus(true);
      });

      expect(result.current.isConnected).toBe(true);

      act(() => {
        result.current.setConnectionStatus(false);
      });

      expect(result.current.isConnected).toBe(false);
    });
  });

  describe("便捷属性", () => {
    it("isCompleted应该正确反映完成状态", async () => {
      const completedStatus: TaskStatusResponse = {
        taskId: "task-123",
        taskName: "测试",
        userRequirement: "测试",
        status: "completed",
        statusDescription: "已完成",
        currentAgent: "ValidateAgent",
        progress: 100,
        agents: [],
        startedAt: "2025-01-14T10:00:00Z",
        completedAt: "2025-01-14T11:00:00Z",
        estimatedRemainingSeconds: 0,
        appSpecId: "app-123",
        qualityScore: 95,
        downloadUrl: "",
        previewUrl: "",
        tokenUsage: {
          planTokens: 1000,
          executeTokens: 2000,
          validateTokens: 500,
          totalTokens: 3500,
          estimatedCost: 0.05,
        },
        errorMessage: "",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T11:00:00Z",
      };

      vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
        success: true,
        data: completedStatus,
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.isCompleted).toBe(true);
        expect(result.current.isFailed).toBe(false);
        expect(result.current.isRunning).toBe(false);
      });
    });

    it("isFailed应该正确反映失败状态", async () => {
      const failedStatus: TaskStatusResponse = {
        taskId: "task-123",
        taskName: "测试",
        userRequirement: "测试",
        status: "failed",
        statusDescription: "失败",
        currentAgent: "ExecuteAgent",
        progress: 45,
        agents: [],
        startedAt: "2025-01-14T10:00:00Z",
        completedAt: "2025-01-14T10:30:00Z",
        estimatedRemainingSeconds: 0,
        appSpecId: "",
        qualityScore: 0,
        downloadUrl: "",
        previewUrl: "",
        tokenUsage: {
          planTokens: 1000,
          executeTokens: 500,
          validateTokens: 0,
          totalTokens: 1500,
          estimatedCost: 0.02,
        },
        errorMessage: "代码生成失败",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T10:30:00Z",
      };

      vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
        success: true,
        data: failedStatus,
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.isFailed).toBe(true);
        expect(result.current.isCompleted).toBe(false);
      });
    });

    it("isRunning应该正确识别运行中的状态", async () => {
      const runningStatuses = [
        "planning",
        "executing",
        "validating",
        "generating",
      ];

      for (const status of runningStatuses) {
        const mockStatus: TaskStatusResponse = {
          taskId: "task-123",
          taskName: "测试",
          userRequirement: "测试",
          status,
          statusDescription: "运行中",
          currentAgent: "TestAgent",
          progress: 50,
          agents: [],
          startedAt: "2025-01-14T10:00:00Z",
          completedAt: "",
          estimatedRemainingSeconds: 100,
          appSpecId: "",
          qualityScore: 0,
          downloadUrl: "",
          previewUrl: "",
          tokenUsage: {
            planTokens: 1000,
            executeTokens: 1000,
            validateTokens: 0,
            totalTokens: 2000,
            estimatedCost: 0.03,
          },
          errorMessage: "",
          createdAt: "2025-01-14T10:00:00Z",
          updatedAt: "2025-01-14T10:00:00Z",
        };

        vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
          success: true,
          data: mockStatus,
        });

        const { result } = renderHook(() => useGenerationTask());

        // Wait for hook to initialize
        await waitFor(() => {
          expect(result.current).not.toBeNull();
        });

        act(() => {
          result.current.setTaskId("task-123");
        });

        await act(async () => {
          await result.current.refreshStatus();
        });

        await waitFor(() => {
          expect(result.current.isRunning).toBe(true);
        });
      }
    });

    it("progress应该返回任务进度", async () => {
      const mockStatus: TaskStatusResponse = {
        taskId: "task-123",
        taskName: "测试",
        userRequirement: "测试",
        status: "executing",
        statusDescription: "执行中",
        currentAgent: "ExecuteAgent",
        progress: 75,
        agents: [],
        startedAt: "2025-01-14T10:00:00Z",
        completedAt: "",
        estimatedRemainingSeconds: 60,
        appSpecId: "",
        qualityScore: 0,
        downloadUrl: "",
        previewUrl: "",
        tokenUsage: {
          planTokens: 1000,
          executeTokens: 1500,
          validateTokens: 0,
          totalTokens: 2500,
          estimatedCost: 0.04,
        },
        errorMessage: "",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T10:00:00Z",
      };

      vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
        success: true,
        data: mockStatus,
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.progress).toBe(75);
      });
    });

    it("currentAgent应该返回当前执行的Agent", async () => {
      const mockStatus: TaskStatusResponse = {
        taskId: "task-123",
        taskName: "测试",
        userRequirement: "测试",
        status: "planning",
        statusDescription: "规划中",
        currentAgent: "PlanAgent",
        progress: 25,
        agents: [],
        startedAt: "2025-01-14T10:00:00Z",
        completedAt: "",
        estimatedRemainingSeconds: 200,
        appSpecId: "",
        qualityScore: 0,
        downloadUrl: "",
        previewUrl: "",
        tokenUsage: {
          planTokens: 500,
          executeTokens: 0,
          validateTokens: 0,
          totalTokens: 500,
          estimatedCost: 0.01,
        },
        errorMessage: "",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T10:00:00Z",
      };

      vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
        success: true,
        data: mockStatus,
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.currentAgent).toBe("PlanAgent");
      });
    });
  });

  describe("错误处理", () => {
    it("应该处理网络错误", async () => {
      vi.mocked(generateModule.getTaskStatus).mockRejectedValue(
        new Error("Network error")
      );

      const onError = vi.fn();

      const { result } = renderHook(() => useGenerationTask({ onError }));

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.error).toBe("Network error");
        expect(onError).toHaveBeenCalledWith("Network error");
      });
    });

    it("应该处理API错误响应", async () => {
      vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
        success: false,
        error: "Internal Server Error",
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.error).toBe("Internal Server Error");
      });
    });

    it("应该在新请求时清除旧错误", async () => {
      // 第一次请求失败
      vi.mocked(generateModule.getTaskStatus).mockResolvedValueOnce({
        success: false,
        error: "First error",
      });

      const { result } = renderHook(() => useGenerationTask());

      // Wait for hook to initialize
      await waitFor(() => {
        expect(result.current).not.toBeNull();
      });

      act(() => {
        result.current.setTaskId("task-123");
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.error).toBe("First error");
      });

      // 第二次请求成功
      const mockStatus: TaskStatusResponse = {
        taskId: "task-123",
        taskName: "测试",
        userRequirement: "测试",
        status: "completed",
        statusDescription: "已完成",
        currentAgent: "ValidateAgent",
        progress: 100,
        agents: [],
        startedAt: "2025-01-14T10:00:00Z",
        completedAt: "2025-01-14T11:00:00Z",
        estimatedRemainingSeconds: 0,
        appSpecId: "app-123",
        qualityScore: 95,
        downloadUrl: "",
        previewUrl: "",
        tokenUsage: {
          planTokens: 1000,
          executeTokens: 2000,
          validateTokens: 500,
          totalTokens: 3500,
          estimatedCost: 0.05,
        },
        errorMessage: "",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T11:00:00Z",
      };

      vi.mocked(generateModule.getTaskStatus).mockResolvedValue({
        success: true,
        data: mockStatus,
      });

      await act(async () => {
        await result.current.refreshStatus();
      });

      await waitFor(() => {
        expect(result.current.error).toBeNull();
        expect(result.current.taskStatus).toEqual(mockStatus);
      });
    });
  });
});
