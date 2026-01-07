"use client";

import { useState, useEffect, useMemo, type ReactNode } from "react";
import { motion } from "framer-motion";
import { G3LogEntry } from "@/lib/g3/types";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { LogStream } from "./log-stream";
import { AgentCard } from "./agent-card";
import { G3ProgressTracker, G3Phase } from "./g3-progress-tracker";
import {
  Play,
  ShieldCheck,
  Sword,
  Hammer,
  Loader2,
  Zap,
  Sparkles,
} from "lucide-react";
import { cn } from "@/lib/utils";

export interface G3ConsoleViewProps {
  /** 日志条目列表 */
  logs: G3LogEntry[];
  /** 当前活跃角色 */
  activeRole: "ARCHITECT" | "PLAYER" | "COACH" | null;
  /** 当前轮次 */
  round: number;
  /** 是否正在运行 */
  isRunning: boolean;
  /** 需求描述 */
  requirement: string;
  /** 需求变更回调 */
  onRequirementChange?: (value: string) => void;
  /** 启动回调 */
  onStart?: () => void;
  /** 是否自动启动模式 */
  autoStart?: boolean;
  /** 右侧日志区域顶部插槽（用于放置“结果/状态”等补充信息） */
  rightTopSlot?: ReactNode;
  /** 容器类名 */
  className?: string;
}

/**
 * 根据日志内容推断当前阶段
 */
function inferPhaseFromLogs(logs: G3LogEntry[]): G3Phase {
  if (logs.length === 0) return "INIT";

  // 从后往前查找最新的阶段标识
  for (let i = logs.length - 1; i >= 0; i--) {
    const content = logs[i].content.toLowerCase();

    if (
      content.includes("完成") ||
      content.includes("completed") ||
      content.includes("交付")
    ) {
      return "COMPLETED";
    }
    if (
      content.includes("安全") ||
      content.includes("测试") ||
      content.includes("红队") ||
      logs[i].role === "COACH"
    ) {
      return "TESTING";
    }
    if (
      content.includes("生成代码") ||
      content.includes("蓝队") ||
      content.includes("controller") ||
      content.includes("service") ||
      logs[i].role === "PLAYER"
    ) {
      return "CODING";
    }
    if (
      content.includes("schema") ||
      content.includes("数据库") ||
      content.includes("表结构")
    ) {
      return "SCHEMA";
    }
    if (
      content.includes("api") ||
      content.includes("契约") ||
      content.includes("接口")
    ) {
      return "CONTRACT";
    }
    if (
      content.includes("架构") ||
      content.includes("设计") ||
      logs[i].role === "ARCHITECT"
    ) {
      return "ARCHITECT";
    }
  }

  return "INIT";
}

/**
 * G3 控制台视图组件 - 苹果风格
 *
 * 设计特点：
 * - 宽松的布局和充足的留白
 * - 柔和的深色背景
 * - 毛玻璃效果和微妙渐变
 * - 清晰的视觉层次
 * - 优雅的动画过渡
 */
export function G3ConsoleView({
  logs,
  activeRole,
  round,
  isRunning,
  requirement,
  onRequirementChange,
  onStart,
  autoStart = false,
  rightTopSlot,
  className,
}: G3ConsoleViewProps) {
  // 开始时间
  const [startTime, setStartTime] = useState<number | undefined>();

  // 当开始运行时记录时间
  useEffect(() => {
    if (isRunning && !startTime) {
      setStartTime(Date.now());
    } else if (!isRunning) {
      setStartTime(undefined);
    }
  }, [isRunning, startTime]);

  // 推断当前阶段
  const currentPhase = useMemo(() => {
    if (!isRunning && logs.length === 0) return "INIT" as G3Phase;
    return inferPhaseFromLogs(logs);
  }, [logs, isRunning]);

  // Agent 状态计算
  const getAgentStatus = (role: "ARCHITECT" | "PLAYER" | "COACH") => {
    if (!isRunning) return "IDLE";
    if (activeRole === role) return "WORKING";

    // 根据阶段推断状态
    const phaseOrder = ["ARCHITECT", "CONTRACT", "SCHEMA", "CODING", "TESTING"];
    const rolePhaseMap: Record<string, string[]> = {
      ARCHITECT: ["ARCHITECT", "CONTRACT", "SCHEMA"],
      PLAYER: ["CODING"],
      COACH: ["TESTING"],
    };

    const currentPhaseIndex = phaseOrder.indexOf(currentPhase);
    const rolePhases = rolePhaseMap[role];

    // 如果角色的阶段已经过了，显示完成
    const roleLastPhaseIndex = Math.max(
      ...rolePhases.map((p) => phaseOrder.indexOf(p))
    );
    if (currentPhaseIndex > roleLastPhaseIndex) {
      return "DONE";
    }

    // 如果角色的阶段还没到，显示等待
    const roleFirstPhaseIndex = Math.min(
      ...rolePhases.map((p) => phaseOrder.indexOf(p))
    );
    if (currentPhaseIndex < roleFirstPhaseIndex) {
      return "WAITING";
    }

    return "IDLE";
  };

  return (
    <div
      className={cn(
        // 容器：苹果风格深色背景
        "flex flex-col w-full min-h-[calc(100vh-120px)]",
        "bg-[#0a0a0c]",
        "text-white/90",
        "p-6 md:p-8 rounded-3xl",
        "border border-white/[0.06]",
        "shadow-2xl shadow-black/50",
        "overflow-hidden",
        className
      )}
    >
      {/* ====== Header ====== */}
      <header className="flex items-center justify-between mb-6">
        {/* 左侧：Logo + 标题 */}
        <div className="flex items-center gap-4">
          <motion.div
            className="p-3 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl shadow-lg shadow-blue-500/25"
            animate={isRunning ? { scale: [1, 1.03, 1] } : {}}
            transition={{ duration: 2.5, repeat: Infinity }}
          >
            <Sword className="w-6 h-6 text-white" />
          </motion.div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white">
              G3 红蓝博弈控制台
            </h1>
            <div className="flex items-center gap-4 text-sm text-white/40 mt-1">
              <span className="flex items-center gap-1.5">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                安全优先
              </span>
              <span className="flex items-center gap-1.5">
                <Hammer className="w-3.5 h-3.5 text-amber-400" />
                自动修复
              </span>
              <span className="flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-violet-400" />
                AI驱动
              </span>
            </div>
          </div>
        </div>

        {/* 右侧：控制区域 */}
        <div className="flex items-center gap-4">
          {/* 输入框（非自动模式） */}
          {!autoStart && (
            <Input
              value={requirement}
              onChange={(e) => onRequirementChange?.(e.target.value)}
              disabled={isRunning || !onRequirementChange}
              className="w-80 h-11 bg-white/[0.04] border-white/[0.08] text-white placeholder:text-white/30 rounded-xl font-mono text-sm focus:ring-2 focus:ring-blue-500/30 focus:border-blue-500/50 transition-all"
              placeholder="输入任务目标..."
            />
          )}

          {/* 启动按钮（非自动模式） */}
          {!autoStart && onStart && (
            <Button
              onClick={onStart}
              disabled={isRunning}
              className="h-11 px-6 bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-400 hover:to-indigo-400 text-white font-semibold rounded-xl shadow-lg shadow-blue-500/25 hover:shadow-blue-500/40 transition-all hover:scale-[1.02] disabled:opacity-50 disabled:hover:scale-100"
            >
              {isRunning ? (
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              ) : (
                <Play className="w-4 h-4 mr-2" />
              )}
              {isRunning ? "执行中" : "启动引擎"}
            </Button>
          )}

          {/* 自主模式标识 */}
          {(autoStart || isRunning) && (
            <Badge className="h-9 px-4 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-full font-medium">
              <Zap className="w-3.5 h-3.5 mr-1.5" />
              自主模式
            </Badge>
          )}
        </div>
      </header>

      {/* ====== Progress Tracker ====== */}
      {(isRunning || logs.length > 0) && (
        <motion.div
          initial={{ opacity: 0, y: -10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4 }}
          className="mb-6"
        >
          <G3ProgressTracker
            currentPhase={currentPhase}
            isRunning={isRunning}
            startTime={startTime}
            round={round}
            maxRounds={3}
          />
        </motion.div>
      )}

      {/* ====== Main Content ====== */}
      <div className="grid grid-cols-12 gap-6 flex-1 min-h-0 overflow-hidden">
        {/* ====== Left: Agent Cards ====== */}
        <div className="col-span-4 flex flex-col gap-4 overflow-y-auto pr-2 scrollbar-thin scrollbar-thumb-white/10 scrollbar-track-transparent">
          <AgentCard
            role="ARCHITECT"
            name="架构师"
            description="解构需求，设计模块结构，规划API接口契约"
            status={getAgentStatus("ARCHITECT")}
          />
          <AgentCard
            role="PLAYER"
            name="蓝队"
            description="使用安全模板构建功能代码，实现业务逻辑"
            status={getAgentStatus("PLAYER")}
          />
          <AgentCard
            role="COACH"
            name="红队"
            description="检测 IDOR、SQL注入等安全漏洞，验证防护"
            status={getAgentStatus("COACH")}
          />

          {/* 轮次进度（运行时显示） */}
          {isRunning && round > 0 && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="mt-auto p-4 rounded-xl bg-white/[0.02] border border-white/[0.06] backdrop-blur-sm"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm text-white/50">博弈轮次</span>
                <span className="text-sm font-mono text-white/70">
                  {round} / 3
                </span>
              </div>
              <div className="flex gap-2">
                {[1, 2, 3].map((r) => (
                  <div
                    key={r}
                    className={cn(
                      "flex-1 h-2 rounded-full transition-all duration-500",
                      r <= round
                        ? "bg-gradient-to-r from-blue-500 to-indigo-500"
                        : "bg-white/[0.06]"
                    )}
                  />
                ))}
              </div>
            </motion.div>
          )}
        </div>

        {/* ====== Right: Log Stream ====== */}
        <div className="col-span-8 flex flex-col min-h-0">
          {rightTopSlot && <div className="shrink-0 mb-4">{rightTopSlot}</div>}
          <LogStream
            logs={logs}
            className="flex-1 rounded-2xl"
            showFilter
            autoCollapse
          />
        </div>
      </div>
    </div>
  );
}
