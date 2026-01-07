"use client";

import { useEffect, useState, useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { cn } from "@/lib/utils";
import { Progress } from "@/components/ui/progress";
import {
  Brain,
  FileCode,
  Database,
  Shield,
  Sparkles,
  CheckCircle2,
  Clock,
  Zap,
} from "lucide-react";

/**
 * G3 执行阶段定义
 */
export type G3Phase =
  | "INIT"           // 初始化
  | "ARCHITECT"      // 架构设计
  | "CONTRACT"       // API契约生成
  | "SCHEMA"         // 数据库Schema
  | "CODING"         // 代码生成
  | "TESTING"        // 安全测试
  | "COMPLETED"      // 完成
  | "ERROR";         // 错误

/**
 * 阶段配置
 */
interface PhaseConfig {
  /** 阶段标签 */
  label: string;
  /** 阶段图标 */
  icon: React.ReactNode;
  /** 阶段颜色 */
  color: string;
  /** 预估耗时（秒） */
  estimatedDuration: number;
  /** 阶段描述 */
  description: string;
}

/**
 * 阶段配置映射
 */
const phaseConfigs: Record<G3Phase, PhaseConfig> = {
  INIT: {
    label: "初始化",
    icon: <Zap className="w-4 h-4" />,
    color: "text-slate-400",
    estimatedDuration: 5,
    description: "连接G3引擎...",
  },
  ARCHITECT: {
    label: "架构设计",
    icon: <Brain className="w-4 h-4" />,
    color: "text-purple-400",
    estimatedDuration: 20,
    description: "AI架构师正在分析需求...",
  },
  CONTRACT: {
    label: "API契约",
    icon: <FileCode className="w-4 h-4" />,
    color: "text-blue-400",
    estimatedDuration: 15,
    description: "生成OpenAPI规范...",
  },
  SCHEMA: {
    label: "数据库Schema",
    icon: <Database className="w-4 h-4" />,
    color: "text-amber-400",
    estimatedDuration: 10,
    description: "设计数据库结构...",
  },
  CODING: {
    label: "代码生成",
    icon: <Sparkles className="w-4 h-4" />,
    color: "text-green-400",
    estimatedDuration: 30,
    description: "蓝队正在生成代码...",
  },
  TESTING: {
    label: "安全测试",
    icon: <Shield className="w-4 h-4" />,
    color: "text-red-400",
    estimatedDuration: 15,
    description: "红队检测安全漏洞...",
  },
  COMPLETED: {
    label: "完成",
    icon: <CheckCircle2 className="w-4 h-4" />,
    color: "text-emerald-400",
    estimatedDuration: 0,
    description: "代码已交付!",
  },
  ERROR: {
    label: "错误",
    icon: <Shield className="w-4 h-4" />,
    color: "text-red-500",
    estimatedDuration: 0,
    description: "执行过程中出现错误",
  },
};

/**
 * 阶段顺序（用于进度计算）
 */
const phaseOrder: G3Phase[] = [
  "INIT",
  "ARCHITECT",
  "CONTRACT",
  "SCHEMA",
  "CODING",
  "TESTING",
  "COMPLETED",
];

/**
 * 等待提示语列表
 */
const waitingTips = [
  "AI正在分析您的需求，这通常需要60-90秒...",
  "架构师正在设计最佳方案，请稍候...",
  "正在生成类型安全的代码...",
  "红蓝对抗中，确保代码质量...",
  "好饭不怕晚，优质代码值得等待...",
  "正在进行安全漏洞扫描...",
  "优化数据库查询性能中...",
  "生成完整的单元测试覆盖...",
];

interface G3ProgressTrackerProps {
  /** 当前阶段 */
  currentPhase: G3Phase;
  /** 是否正在运行 */
  isRunning: boolean;
  /** 开始时间 */
  startTime?: number;
  /** 当前轮次 */
  round?: number;
  /** 最大轮次 */
  maxRounds?: number;
  /** 容器类名 */
  className?: string;
}

/**
 * G3 进度追踪器组件
 *
 * 功能：
 * - 显示当前执行阶段
 * - 显示整体进度条
 * - 显示预估剩余时间
 * - 轮播等待提示语缓解焦躁
 */
export function G3ProgressTracker({
  currentPhase,
  isRunning,
  startTime,
  round = 0,
  maxRounds = 3,
  className,
}: G3ProgressTrackerProps) {
  // 已耗时（秒）
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  // 当前提示语索引
  const [tipIndex, setTipIndex] = useState(0);

  // 计时器
  useEffect(() => {
    if (!isRunning || !startTime) {
      setElapsedSeconds(0);
      return;
    }

    const interval = setInterval(() => {
      setElapsedSeconds(Math.floor((Date.now() - startTime) / 1000));
    }, 1000);

    return () => clearInterval(interval);
  }, [isRunning, startTime]);

  // 提示语轮播
  useEffect(() => {
    if (!isRunning) return;

    const interval = setInterval(() => {
      setTipIndex((prev) => (prev + 1) % waitingTips.length);
    }, 5000);

    return () => clearInterval(interval);
  }, [isRunning]);

  // 计算进度百分比
  const progressPercent = useMemo(() => {
    const currentIndex = phaseOrder.indexOf(currentPhase);
    if (currentIndex === -1) return 0;
    if (currentPhase === "COMPLETED") return 100;
    if (currentPhase === "ERROR") return 0;

    // 基础进度（基于阶段）
    const baseProgress = (currentIndex / (phaseOrder.length - 1)) * 100;

    // 阶段内进度（基于时间）
    const config = phaseConfigs[currentPhase];
    const phaseElapsed = Math.min(elapsedSeconds, config.estimatedDuration);
    const phaseProgress =
      config.estimatedDuration > 0
        ? (phaseElapsed / config.estimatedDuration) * (100 / (phaseOrder.length - 1))
        : 0;

    return Math.min(baseProgress + phaseProgress * 0.5, 99);
  }, [currentPhase, elapsedSeconds]);

  // 预估剩余时间
  const estimatedRemaining = useMemo(() => {
    const currentIndex = phaseOrder.indexOf(currentPhase);
    if (currentIndex === -1 || currentPhase === "COMPLETED") return 0;

    let remaining = 0;
    for (let i = currentIndex; i < phaseOrder.length - 1; i++) {
      remaining += phaseConfigs[phaseOrder[i]].estimatedDuration;
    }
    // 减去已经过的时间
    remaining -= elapsedSeconds;
    return Math.max(0, remaining);
  }, [currentPhase, elapsedSeconds]);

  // 格式化时间
  const formatTime = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    if (mins > 0) {
      return `${mins}分${secs}秒`;
    }
    return `${secs}秒`;
  };

  const config = phaseConfigs[currentPhase];

  return (
    <div
      className={cn(
        "bg-slate-900/60 rounded-xl border border-slate-800/80 backdrop-blur-sm overflow-hidden",
        className
      )}
    >
      {/* 进度头部 */}
      <div className="px-4 py-3 border-b border-slate-800/60">
        <div className="flex items-center justify-between">
          {/* 当前阶段 */}
          <div className="flex items-center gap-3">
            <motion.div
              className={cn(
                "p-2 rounded-lg",
                isRunning ? "bg-slate-800/80" : "bg-slate-900/50"
              )}
              animate={isRunning ? { scale: [1, 1.1, 1] } : {}}
              transition={{ duration: 1.5, repeat: Infinity }}
            >
              <span className={config.color}>{config.icon}</span>
            </motion.div>
            <div>
              <div className="flex items-center gap-2">
                <span className={cn("font-semibold", config.color)}>
                  {config.label}
                </span>
                {round > 0 && (
                  <span className="text-xs text-slate-500">
                    (第 {round}/{maxRounds} 轮)
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500 mt-0.5">
                {config.description}
              </p>
            </div>
          </div>

          {/* 时间信息 */}
          {isRunning && (
            <div className="text-right">
              <div className="flex items-center gap-1.5 text-sm text-slate-400">
                <Clock className="w-3.5 h-3.5" />
                <span>已用时 {formatTime(elapsedSeconds)}</span>
              </div>
              {estimatedRemaining > 0 && (
                <p className="text-xs text-slate-500 mt-0.5">
                  预计还需 {formatTime(estimatedRemaining)}
                </p>
              )}
            </div>
          )}
        </div>
      </div>

      {/* 进度条 */}
      <div className="px-4 py-3">
        <div className="flex items-center gap-3 mb-2">
          <Progress
            value={progressPercent}
            className="flex-1 h-2"
            gradient
            animated
          />
          <span className="text-xs font-mono text-slate-400 w-10 text-right">
            {Math.round(progressPercent)}%
          </span>
        </div>

        {/* 阶段指示器 */}
        <div className="flex justify-between mt-3">
          {phaseOrder.slice(0, -1).map((phase, index) => {
            const currentIndex = phaseOrder.indexOf(currentPhase);
            const isCompleted = index < currentIndex;
            const isCurrent = phase === currentPhase;
            const phaseConfig = phaseConfigs[phase];

            return (
              <div
                key={phase}
                className="flex flex-col items-center gap-1"
              >
                <motion.div
                  className={cn(
                    "w-6 h-6 rounded-full flex items-center justify-center text-[10px]",
                    isCompleted
                      ? "bg-emerald-500/20 text-emerald-400"
                      : isCurrent
                      ? "bg-slate-700 border-2 border-current " + phaseConfig.color
                      : "bg-slate-800/50 text-slate-600"
                  )}
                  animate={
                    isCurrent
                      ? { scale: [1, 1.1, 1], opacity: [0.8, 1, 0.8] }
                      : {}
                  }
                  transition={{ duration: 1.5, repeat: Infinity }}
                >
                  {isCompleted ? (
                    <CheckCircle2 className="w-3.5 h-3.5" />
                  ) : (
                    <span className={phaseConfig.color}>
                      {phaseConfig.icon}
                    </span>
                  )}
                </motion.div>
                <span
                  className={cn(
                    "text-[10px] whitespace-nowrap",
                    isCurrent ? phaseConfig.color : "text-slate-600"
                  )}
                >
                  {phaseConfig.label}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* 等待提示语轮播 */}
      {isRunning && (
        <div className="px-4 py-2.5 bg-slate-800/30 border-t border-slate-800/60">
          <AnimatePresence mode="wait">
            <motion.p
              key={tipIndex}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              transition={{ duration: 0.3 }}
              className="text-xs text-slate-400 text-center"
            >
              {waitingTips[tipIndex]}
            </motion.p>
          </AnimatePresence>
        </div>
      )}
    </div>
  );
}
