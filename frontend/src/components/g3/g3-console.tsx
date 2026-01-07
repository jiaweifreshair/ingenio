"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { G3LogEntry as LocalG3LogEntry, G3Task } from "@/lib/g3/types";
import { createAndMonitorG3Job, getG3Artifacts, getG3JobStatus } from "@/lib/api/g3";
import { G3LogEntry as ApiG3LogEntry } from "@/types/g3";
import { G3ConsoleView } from "./g3-console-view";
import { toast } from "@/hooks/use-toast";
import { G3ResultDialog } from "./g3-result-dialog";
import type { G3JobStatusResponse } from "@/lib/api/g3";
import type { G3ArtifactSummary } from "@/types/g3";

interface G3ConsoleProps {
  initialRequirement?: string;
  autoStart?: boolean;
  onComplete?: (task: G3Task) => void;
  className?: string;
}

/**
 * 将后端 API 返回的日志格式转换为本地组件使用的格式
 */
function convertApiLogToLocal(apiLog: ApiG3LogEntry): LocalG3LogEntry {
  return {
    timestamp: new Date(apiLog.timestamp).getTime(),
    role: apiLog.role === 'EXECUTOR' ? 'SYSTEM' : apiLog.role,
    step: 'BACKEND',
    content: apiLog.message,
    level: apiLog.level.toUpperCase() as 'INFO' | 'WARN' | 'ERROR' | 'SUCCESS',
  };
}

export function G3Console({ initialRequirement, autoStart = false, onComplete, className }: G3ConsoleProps) {
  const [isRunning, setIsRunning] = useState(false);
  const [requirement, setRequirement] = useState(initialRequirement || "创建一个请假系统，需要审批流");
  const [logs, setLogs] = useState<LocalG3LogEntry[]>([]);
  const [activeRole, setActiveRole] = useState<'ARCHITECT' | 'PLAYER' | 'COACH' | null>(null);
  const [round, setRound] = useState(0);
  const [jobId, setJobId] = useState<string | null>(null);
  const cancelRef = useRef<(() => void) | null>(null);
  const jobIdRef = useRef<string | null>(null);
  const startInFlightRef = useRef(false);
  const autoStartOnceRef = useRef(false);
  const [finalStatus, setFinalStatus] = useState<'COMPLETED' | 'FAILED' | null>(null);

  // 结果展示：任务状态 + 产物列表
  const [jobInfo, setJobInfo] = useState<G3JobStatusResponse | null>(null);
  const [artifacts, setArtifacts] = useState<G3ArtifactSummary[]>([]);
  const [resultLoading, setResultLoading] = useState(false);
  const [resultError, setResultError] = useState<string | null>(null);

  // 处理日志条目
  const handleLogEntry = useCallback((apiLog: ApiG3LogEntry) => {
    const localLog = convertApiLogToLocal(apiLog);
    setLogs(prev => [...prev, localLog]);

    // 更新活跃角色
    if (localLog.role === 'ARCHITECT' || localLog.role === 'PLAYER' || localLog.role === 'COACH') {
      setActiveRole(localLog.role);
    }

    // 检测轮次变化
    if (localLog.content.includes("轮") || localLog.content.includes("Round")) {
      const match = localLog.content.match(/第\s*(\d+)\s*轮|Round\s*(\d+)/);
      if (match) {
        const roundNum = parseInt(match[1] || match[2]);
        setRound(roundNum);
      }
    }

    // 检测完成状态
    if (localLog.content.includes("任务完成") || localLog.content.includes("COMPLETED")) {
      setIsRunning(false);
      setActiveRole(null);
      setFinalStatus('COMPLETED');
      toast({ title: "G3 引擎执行成功", description: "代码已交付" });
    }

    // 检测失败状态
    if (
      localLog.content.includes("任务失败") ||
      localLog.content.includes("FAILED") ||
      localLog.content.includes("❌ G3任务失败")
    ) {
      setIsRunning(false);
      setActiveRole(null);
      setFinalStatus('FAILED');
      toast({ title: "G3 引擎执行失败", description: localLog.content, variant: "destructive" });
    }
  }, []);

  /**
   * 拉取任务“结果视图”（状态 + 产物列表）
   *
   * 说明：
   * - 由 SSE complete 事件触发（后端会在流结束时补发 complete）
   * - 也可在 UI 中手动刷新
   */
  const refreshResult = useCallback(async () => {
    const id = jobIdRef.current;
    if (!id) return;

    setResultLoading(true);
    setResultError(null);

    try {
      const [statusResp, artifactsResp] = await Promise.all([
        getG3JobStatus(id),
        getG3Artifacts(id),
      ]);

      if (statusResp.success && statusResp.data) {
        setJobInfo(statusResp.data);
      }

      if (artifactsResp.success && artifactsResp.data) {
        setArtifacts(artifactsResp.data as G3ArtifactSummary[]);
      }

      if (!statusResp.success || !artifactsResp.success) {
        const msg =
          statusResp.error ||
          statusResp.message ||
          artifactsResp.error ||
          artifactsResp.message ||
          "拉取结果失败";
        setResultError(msg);
      }
    } catch (e) {
      setResultError(e instanceof Error ? e.message : "拉取结果失败");
    } finally {
      setResultLoading(false);
    }
  }, []);

  const handleStart = useCallback(async () => {
    if (!requirement.trim()) return;
    if (startInFlightRef.current) return;
    if (isRunning) return;

    startInFlightRef.current = true;

    setIsRunning(true);
    setLogs([]);
    setRound(0);
    setActiveRole(null);
    setJobId(null);
    jobIdRef.current = null;
    setFinalStatus(null);
    setJobInfo(null);
    setArtifacts([]);
    setResultError(null);

    // 添加启动日志
    setLogs([{
      timestamp: Date.now(),
      role: 'SYSTEM',
      step: 'INIT',
      content: '🚀 G3 引擎启动，正在连接后端服务...',
      level: 'INFO',
    }]);

    try {
      // 若已有连接，先取消，避免重复订阅导致“提交两次任务/日志串线”
      if (cancelRef.current) {
        cancelRef.current();
        cancelRef.current = null;
      }

      const { cancel } = await createAndMonitorG3Job(requirement, {
        onSubmitted: (id) => {
          console.log('[G3Console] Job submitted:', id);
          setJobId(id);
          jobIdRef.current = id;
          setLogs(prev => [...prev, {
            timestamp: Date.now(),
            role: 'SYSTEM',
            step: 'INIT',
            content: `✅ 任务已提交，ID: ${id.substring(0, 8)}...`,
            level: 'SUCCESS',
          }, {
            timestamp: Date.now(),
            role: 'SYSTEM',
            step: 'SSE',
            content: '🌊 正在订阅实时日志流...',
            level: 'INFO',
          }]);
        },
        onOpen: (info) => {
          setLogs((prev) => [
            ...prev,
            {
              timestamp: Date.now(),
              role: "SYSTEM",
              step: "SSE",
              content: `🌊 SSE已连接 (HTTP ${info.status}${info.contentType ? `, ${info.contentType}` : ""})`,
              level: "INFO",
            },
          ]);
        },
        onSubmitError: (error) => {
          console.error('[G3Console] Submit error:', error);
          setLogs(prev => [...prev, {
            timestamp: Date.now(),
            role: 'SYSTEM',
            step: 'ERROR',
            content: `❌ 任务提交失败: ${error}`,
            level: 'ERROR',
          }]);
          setIsRunning(false);
          toast({ title: "G3 引擎启动失败", description: error, variant: "destructive" });
        },
        onLog: handleLogEntry,
        onComplete: () => {
          console.log('[G3Console] Job completed');
          setIsRunning(false);
          setActiveRole(null);
          refreshResult();
          // 构造 G3Task 对象供回调使用
          const task: G3Task = {
            id: jobIdRef.current || 'unknown',
            requirement,
            status: finalStatus === 'FAILED' ? 'FAILED' : 'COMPLETED',
            rounds: round,
            maxRounds: 3,
            artifacts: { codeFiles: {}, testFiles: {}, logs: [] },
          };
          onComplete?.(task);
        },
        onError: (error) => {
          console.error('[G3Console] SSE error:', error);
          setLogs(prev => [...prev, {
            timestamp: Date.now(),
            role: 'SYSTEM',
            step: 'ERROR',
            content: `⚠️ 连接错误: ${error}`,
            level: 'WARN',
          }]);
        },
        onClose: () => {
          console.log('[G3Console] SSE connection closed');
          if (isRunning) {
            setIsRunning(false);
          }
        },
      });

      cancelRef.current = cancel;
    } catch (e) {
      console.error('[G3Console] Unexpected error:', e);
      setIsRunning(false);
      toast({ title: "G3 引擎异常", description: String(e), variant: "destructive" });
    } finally {
      startInFlightRef.current = false;
    }
  }, [requirement, onComplete, handleLogEntry, refreshResult, round, isRunning, finalStatus]);

  // 清理：组件卸载时取消 SSE 连接
  useEffect(() => {
    return () => {
      if (cancelRef.current) {
        cancelRef.current();
      }
    };
  }, []);

  // Auto Start Effect
  useEffect(() => {
    // Next.js dev 模式下 React StrictMode 会导致 effect 执行两次，这里用 ref 防止重复启动
    if (autoStart && !autoStartOnceRef.current && !isRunning && logs.length === 0) {
      autoStartOnceRef.current = true;
      handleStart();
    }
  }, [autoStart, isRunning, logs.length, handleStart]);

  return (
    <G3ConsoleView
      logs={logs}
      activeRole={activeRole}
      round={round}
      isRunning={isRunning}
      requirement={requirement}
      onRequirementChange={setRequirement}
      onStart={handleStart}
      autoStart={autoStart}
      rightTopSlot={
        <G3ResultDialog
          jobId={jobId}
          jobInfo={jobInfo}
          artifacts={artifacts}
          isLoading={resultLoading}
          error={resultError}
          onRefresh={refreshResult}
        />
      }
      className={className}
    />
  );
}
