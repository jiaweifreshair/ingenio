"use client";

/**
 * G3 日志可视化组件
 *
 * Terminal 风格的日志展示组件，实时展示"红蓝博弈"过程。
 * 支持两种模式：
 * 1. 受控模式：外部传入 logs 数据
 * 2. 自主模式：内置 SSE 连接，自动获取日志
 *
 * @module components/generation/G3LogViewer
 * @author Ingenio Team
 * @since Phase 1 - G3 Engine MVP
 */

import { useState, useEffect, useRef, useCallback } from "react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Play,
  Square,
  Terminal,
  User,
  Shield,
  Cpu,
  Code2,
  CheckCircle2,
  XCircle,
  AlertTriangle,
} from "lucide-react";
import type { G3LogEntry } from "@/types/g3";
import type { G3Event } from "@/lib/lab/g3-engine";

/**
 * 显示层事件类型（用于UI渲染）
 * 将 G3Event 转换为扁平结构便于组件使用
 */
type DisplayEvent = {
  type: 'LOG' | 'ARTIFACT' | 'ERROR' | 'COMPLETE';
  role?: 'PLAYER' | 'COACH' | 'EXECUTOR';
  level?: 'info' | 'warn' | 'error' | 'success';
  message?: string;
  timestamp?: string;
  code?: string;
  filename?: string;
};

/**
 * 将 G3Event 转换为 DisplayEvent
 */
function toDisplayEvent(event: G3Event): DisplayEvent {
  if (event.type === 'LOG') {
    return {
      type: 'LOG',
      role: event.data.role as DisplayEvent['role'],
      level: event.data.level as DisplayEvent['level'],
      message: event.data.message,
      timestamp: event.data.timestamp,
    };
  }
  // ARTIFACT type
  return {
    type: 'ARTIFACT',
    code: event.data.code,
    filename: event.data.filename,
  };
}

/**
 * 受控模式属性（外部传入日志）
 */
interface ControlledProps {
  /** 日志列表 */
  logs: G3LogEntry[];
  /** 自定义样式类名 */
  className?: string;
}

/**
 * 自主模式属性（内置 SSE 连接）
 */
interface AutonomousProps {
  /** 自定义样式类名 */
  className?: string;
  /** 默认需求（可选） */
  defaultRequirement?: string;
  /** 是否自动开始 */
  autoStart?: boolean;
  /** 完成回调 */
  onComplete?: (code: string | null) => void;
}

/**
 * 日志条目显示组件（简化版，用于受控模式）
 */
function SimpleLogEntry({ log }: { log: G3LogEntry }) {
  const getRoleStyle = (role: G3LogEntry["role"]) => {
    switch (role) {
      case "PLAYER":
        return "text-blue-400 font-bold";
      case "COACH":
        return "text-red-400 font-bold";
      case "EXECUTOR":
        return "text-green-400 font-bold";
      default:
        return "text-gray-400";
    }
  };

  const getRoleIcon = (role: G3LogEntry["role"]) => {
    switch (role) {
      case "PLAYER":
        return "🔵";
      case "COACH":
        return "🔴";
      case "EXECUTOR":
        return "⚖️";
      default:
        return "⚪";
    }
  };

  return (
    <div className="mb-2 animate-in fade-in slide-in-from-bottom-2 duration-300">
      <span className="opacity-40 mr-2 text-xs">[{log.timestamp}]</span>
      <span className={`${getRoleStyle(log.role)} mr-2`}>
        {getRoleIcon(log.role)} [{log.role}]
      </span>
      <span
        className={
          log.level === "error"
            ? "text-red-300"
            : log.level === "warn"
              ? "text-yellow-200"
              : "text-gray-200"
        }
      >
        {log.message}
      </span>
    </div>
  );
}

/**
 * G3 日志查看器（受控模式）
 *
 * 简单的日志展示组件，由外部传入日志数据
 */
export function G3LogViewer({ logs, className }: ControlledProps) {
  const endRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [logs]);

  return (
    <div
      className={cn(
        "bg-gray-900 rounded-lg p-4 font-mono text-sm h-96 overflow-y-auto border border-gray-700 shadow-inner",
        className
      )}
    >
      {logs.length === 0 && (
        <div className="text-gray-500 italic text-center mt-32">
          Waiting for G3 Engine...
        </div>
      )}

      {logs.map((log, index) => (
        <SimpleLogEntry key={index} log={log} />
      ))}
      <div ref={endRef} />
    </div>
  );
}

/**
 * 日志条目显示组件（增强版，用于自主模式）
 */
function EnhancedLogEntry({ event }: { event: DisplayEvent }) {
  const roleConfig = {
    PLAYER: {
      color: "text-blue-400",
      bgColor: "bg-blue-500/10",
      icon: User,
      label: "PLAYER",
    },
    COACH: {
      color: "text-red-400",
      bgColor: "bg-red-500/10",
      icon: Shield,
      label: "COACH",
    },
    EXECUTOR: {
      color: "text-green-400",
      bgColor: "bg-green-500/10",
      icon: Cpu,
      label: "EXECUTOR",
    },
  };

  const role = event.role || 'EXECUTOR';
  const config = roleConfig[role];
  const Icon = config.icon;

  const levelColors = {
    info: "text-slate-300",
    warn: "text-yellow-400",
    error: "text-red-400",
    success: "text-green-400",
  };

  const levelIcons: Record<string, typeof AlertTriangle | null> = {
    info: null,
    warn: AlertTriangle,
    error: XCircle,
    success: CheckCircle2,
  };

  const level = event.level || 'info';
  const LevelIcon = levelIcons[level];
  const messageColor = levelColors[level] || "text-slate-300";

  const time = event.timestamp
    ? new Date(event.timestamp).toLocaleTimeString("zh-CN", {
        hour12: false,
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      })
    : "--:--:--";

  return (
    <div
      className={cn(
        "flex items-start gap-3 py-2 px-3 rounded-md transition-all duration-300",
        "animate-in fade-in slide-in-from-left-2",
        config.bgColor
      )}
    >
      <span className="text-xs text-slate-500 font-mono shrink-0 pt-0.5">
        {time}
      </span>

      <div className={cn("flex items-center gap-1 shrink-0", config.color)}>
        <Icon className="w-4 h-4" />
        <span className="text-xs font-bold uppercase">{config.label}</span>
      </div>

      <div className={cn("flex-1 text-sm font-mono break-all", messageColor)}>
        {LevelIcon && (
          <LevelIcon className="w-4 h-4 inline mr-1 align-text-bottom" />
        )}
        {event.message}
      </div>
    </div>
  );
}

/**
 * 代码展示组件
 */
function CodeBlock({ code, filename }: { code: string; filename?: string }) {
  return (
    <div className="mt-4 rounded-lg border border-green-500/30 bg-black/50 overflow-hidden">
      <div className="flex items-center gap-2 px-4 py-2 bg-green-500/10 border-b border-green-500/30">
        <Code2 className="w-4 h-4 text-green-400" />
        <span className="text-sm font-mono text-green-400">
          {filename || "generated.ts"}
        </span>
        <span className="ml-auto text-xs text-green-500/60">Generated Code</span>
      </div>

      <ScrollArea className="max-h-96">
        <pre className="p-4 text-sm font-mono text-green-300 overflow-x-auto">
          <code>{code}</code>
        </pre>
      </ScrollArea>
    </div>
  );
}

/**
 * G3 日志查看器（自主模式）
 *
 * 完整的日志展示组件，内置 SSE 连接
 */
export function G3LogViewerAutonomous({
  className,
  defaultRequirement = "创建一个用户管理系统，包含增删改查功能",
  autoStart = false,
  onComplete,
}: AutonomousProps) {
  const [requirement, setRequirement] = useState(defaultRequirement);
  const [events, setEvents] = useState<DisplayEvent[]>([]);
  const [isRunning, setIsRunning] = useState(false);
  const [generatedCode, setGeneratedCode] = useState<string | null>(null);
  const [codeFilename, setCodeFilename] = useState<string>("generated.ts");

  const scrollRef = useRef<HTMLDivElement>(null);
  const abortControllerRef = useRef<AbortController | null>(null);

  // 自动滚动到底部
  useEffect(() => {
    if (scrollRef.current) {
      const scrollContainer = scrollRef.current.querySelector(
        "[data-radix-scroll-area-viewport]"
      );
      if (scrollContainer) {
        scrollContainer.scrollTop = scrollContainer.scrollHeight;
      }
    }
  }, [events]);

  /**
   * 启动 G3 引擎
   */
  const handleStart = useCallback(async () => {
    if (!requirement.trim() || isRunning) return;

    setEvents([]);
    setGeneratedCode(null);
    setIsRunning(true);

    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    try {
      const response = await fetch("/api/lab/g3-poc", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ requirement }),
        signal: abortController.signal,
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new Error("No response body");
      }

      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split("\n\n");
        buffer = lines.pop() || "";

        for (const chunk of lines) {
          if (!chunk.trim()) continue;

          const eventMatch = chunk.match(/event:\s*(\w+)/);
          const dataMatch = chunk.match(/data:\s*(.+)/s);

          if (dataMatch) {
            try {
              const rawEvent = JSON.parse(dataMatch[1]) as G3Event;

              if (rawEvent.type === "LOG") {
                const displayEvent = toDisplayEvent(rawEvent);
                setEvents((prev) => [...prev, displayEvent]);

                // Check for completion (success message from EXECUTOR)
                if (rawEvent.data.level === 'success' && rawEvent.data.role === 'EXECUTOR') {
                  // Will receive ARTIFACT next, wait for it
                }
              }

              if (rawEvent.type === "ARTIFACT") {
                const displayEvent = toDisplayEvent(rawEvent);
                if (displayEvent.code) {
                  setGeneratedCode(displayEvent.code);
                  if (displayEvent.filename) {
                    setCodeFilename(displayEvent.filename);
                  }
                  // Mark as complete if code is valid
                  if (rawEvent.data.isValid) {
                    setIsRunning(false);
                    onComplete?.(displayEvent.code);
                  }
                }
              }
            } catch {
              // 忽略解析错误
            }
          }

          if (eventMatch?.[1] === "end") {
            setIsRunning(false);
          }
        }
      }
    } catch (error) {
      if (error instanceof Error && error.name === "AbortError") {
        return;
      }

      const errorEvent: DisplayEvent = {
        type: "ERROR",
        role: "EXECUTOR",
        message: `连接错误: ${error instanceof Error ? error.message : "Unknown error"}`,
        level: "error",
        timestamp: new Date().toISOString(),
      };
      setEvents((prev) => [...prev, errorEvent]);
    } finally {
      setIsRunning(false);
      abortControllerRef.current = null;
    }
  }, [requirement, isRunning, generatedCode, onComplete]);

  /**
   * 停止 G3 引擎
   */
  const handleStop = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsRunning(false);
  }, []);

  // 自动开始
  useEffect(() => {
    if (autoStart && !isRunning && events.length === 0) {
      handleStart();
    }
  }, [autoStart, isRunning, events.length, handleStart]);

  return (
    <div
      className={cn(
        "flex flex-col h-[700px] w-full bg-slate-950 text-slate-200",
        "rounded-xl border border-slate-800 shadow-2xl overflow-hidden",
        className
      )}
    >
      {/* Header */}
      <div className="flex items-center gap-4 px-6 py-4 border-b border-slate-800 bg-slate-900/50">
        <div className="flex items-center gap-2">
          <Terminal className="w-5 h-5 text-green-400" />
          <h2 className="text-lg font-bold text-white">G3 Engine PoC</h2>
        </div>

        <div className="flex-1">
          <input
            type="text"
            value={requirement}
            onChange={(e) => setRequirement(e.target.value)}
            disabled={isRunning}
            placeholder="输入需求描述..."
            className={cn(
              "w-full px-4 py-2 rounded-lg",
              "bg-slate-800 border border-slate-700",
              "text-sm font-mono text-slate-200",
              "placeholder:text-slate-500",
              "focus:outline-none focus:ring-2 focus:ring-green-500/50",
              "disabled:opacity-50"
            )}
          />
        </div>

        <Button
          onClick={isRunning ? handleStop : handleStart}
          disabled={!requirement.trim()}
          variant={isRunning ? "destructive" : "default"}
          className={cn(
            isRunning
              ? "bg-red-600 hover:bg-red-500"
              : "bg-green-600 hover:bg-green-500",
            "font-bold"
          )}
        >
          {isRunning ? (
            <>
              <Square className="w-4 h-4 mr-2" />
              STOP
            </>
          ) : (
            <>
              <Play className="w-4 h-4 mr-2" />
              START
            </>
          )}
        </Button>
      </div>

      {/* Log Stream */}
      <ScrollArea className="flex-1 p-4" ref={scrollRef}>
        <div className="space-y-1">
          {events.map((event, index) => (
            <EnhancedLogEntry key={index} event={event} />
          ))}

          {events.length === 0 && !isRunning && (
            <div className="text-center text-slate-500 py-12">
              <Terminal className="w-12 h-12 mx-auto mb-4 opacity-30" />
              <p>点击 START 启动 G3 引擎</p>
              <p className="text-xs mt-2 text-slate-600">
                观察 Player / Coach / Executor 的红蓝博弈过程
              </p>
            </div>
          )}

          {isRunning && events.length > 0 && (
            <div className="flex items-center gap-2 text-green-400 text-sm animate-pulse">
              <div className="w-2 h-2 rounded-full bg-green-400" />
              Processing...
            </div>
          )}
        </div>

        {generatedCode && (
          <CodeBlock code={generatedCode} filename={codeFilename} />
        )}
      </ScrollArea>

      {/* Footer Status */}
      <div className="flex items-center justify-between px-6 py-3 border-t border-slate-800 bg-slate-900/50 text-xs text-slate-500">
        <span>
          Events: {events.length} | Status:{" "}
          <span className={isRunning ? "text-green-400" : "text-slate-400"}>
            {isRunning ? "RUNNING" : "IDLE"}
          </span>
        </span>
        <span>G3 Engine PoC v1.0 | Phase 1 MVP</span>
      </div>
    </div>
  );
}

export default G3LogViewer;
