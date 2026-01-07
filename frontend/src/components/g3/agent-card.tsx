"use client";

import { useMemo } from "react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { Loader2, Brain, Shield, Sword, Sparkles, CheckCircle2 } from "lucide-react";

/**
 * Agent 角色类型
 */
type AgentRole = "PLAYER" | "COACH" | "ARCHITECT";

/**
 * Agent 状态类型
 */
type AgentStatus = "IDLE" | "THINKING" | "WORKING" | "DONE" | "WAITING";

interface AgentCardProps {
  /** 角色类型 */
  role: AgentRole;
  /** 当前状态 */
  status: AgentStatus;
  /** 显示名称 */
  name: string;
  /** 角色描述 */
  description: string;
  /** 当前任务描述（可选） */
  currentTask?: string;
  /** 已完成任务数（可选） */
  completedTasks?: number;
  /** 容器类名 */
  className?: string;
}

/**
 * 获取状态对应的中文标签
 */
function getStatusLabel(status: AgentStatus): string {
  const labels: Record<AgentStatus, string> = {
    IDLE: "待命",
    THINKING: "思考中",
    WORKING: "执行中",
    DONE: "已完成",
    WAITING: "等待中",
  };
  return labels[status];
}

/**
 * 获取角色对应的图标
 */
function getRoleIcon(role: AgentRole) {
  const icons: Record<AgentRole, typeof Brain> = {
    PLAYER: Sword,
    COACH: Shield,
    ARCHITECT: Brain,
  };
  const Icon = icons[role];
  return <Icon className="w-4 h-4" />;
}

/**
 * 苹果风格角色颜色配置
 * 使用柔和的渐变和半透明效果
 */
const roleColors: Record<AgentRole, {
  gradient: string;
  iconBg: string;
  iconColor: string;
  accentColor: string;
  glowColor: string;
}> = {
  ARCHITECT: {
    gradient: "from-violet-500/10 via-purple-500/5 to-fuchsia-500/10",
    iconBg: "bg-gradient-to-br from-violet-500 to-purple-600",
    iconColor: "text-white",
    accentColor: "text-violet-400",
    glowColor: "shadow-violet-500/20",
  },
  PLAYER: {
    gradient: "from-blue-500/10 via-cyan-500/5 to-teal-500/10",
    iconBg: "bg-gradient-to-br from-blue-500 to-cyan-500",
    iconColor: "text-white",
    accentColor: "text-blue-400",
    glowColor: "shadow-blue-500/20",
  },
  COACH: {
    gradient: "from-rose-500/10 via-red-500/5 to-orange-500/10",
    iconBg: "bg-gradient-to-br from-rose-500 to-red-500",
    iconColor: "text-white",
    accentColor: "text-rose-400",
    glowColor: "shadow-rose-500/20",
  },
};

/**
 * 状态指示器颜色
 */
const statusIndicatorColors: Record<AgentStatus, string> = {
  IDLE: "bg-slate-400",
  THINKING: "bg-amber-400",
  WORKING: "bg-emerald-400",
  DONE: "bg-emerald-500",
  WAITING: "bg-slate-500",
};

/**
 * 思考中的动态提示语
 */
const thinkingPhrases = [
  "分析需求中...",
  "构思方案中...",
  "优化架构中...",
  "评估风险中...",
];

/**
 * 工作中的动态提示语
 */
const workingPhrases: Record<AgentRole, string[]> = {
  ARCHITECT: ["设计模块结构...", "规划API接口...", "定义数据模型..."],
  PLAYER: ["生成Controller...", "实现Service层...", "编写Repository..."],
  COACH: ["扫描安全漏洞...", "检测IDOR问题...", "验证输入校验..."],
};

/**
 * G3 Agent 卡片组件 - 苹果风格
 *
 * 设计特点：
 * - 毛玻璃效果（backdrop-blur）
 * - 柔和的渐变背景
 * - 大圆角设计
 * - 充足的内边距和留白
 * - 简洁优雅的排版
 */
export function AgentCard({
  role,
  status,
  name,
  description,
  currentTask,
  completedTasks = 0,
  className,
}: AgentCardProps) {
  const colors = roleColors[role];
  const isActive = status === "WORKING" || status === "THINKING";
  const isDone = status === "DONE";

  // 动态提示语
  const dynamicHint = useMemo(() => {
    if (currentTask) return currentTask;
    if (status === "THINKING") {
      return thinkingPhrases[Math.floor(Math.random() * thinkingPhrases.length)];
    }
    if (status === "WORKING") {
      const phrases = workingPhrases[role];
      return phrases[Math.floor(Math.random() * phrases.length)];
    }
    return null;
  }, [currentTask, status, role]);

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: "easeOut" }}
      className={className}
    >
      <div
        className={cn(
          // 基础样式：毛玻璃效果
          "relative overflow-hidden rounded-2xl transition-all duration-500",
          "backdrop-blur-xl bg-white/[0.03]",
          "border border-white/[0.08]",
          // 渐变背景
          `bg-gradient-to-br ${colors.gradient}`,
          // 激活状态
          isActive && [
            "border-white/[0.15]",
            `shadow-xl ${colors.glowColor}`,
            "scale-[1.02]",
          ],
          // 完成状态
          isDone && "border-emerald-500/20 bg-emerald-500/5",
          // 非激活状态降低透明度
          !isActive && !isDone && "opacity-70 hover:opacity-90"
        )}
      >
        {/* 激活时的微光效果 */}
        {isActive && (
          <motion.div
            className="absolute inset-0 bg-gradient-to-r from-transparent via-white/[0.03] to-transparent"
            animate={{ x: ["-100%", "200%"] }}
            transition={{ duration: 2.5, repeat: Infinity, ease: "linear" }}
          />
        )}

        {/* 内容区域 */}
        <div className="relative p-4">
          {/* 头部：图标 + 名称 + 状态 */}
          <div className="flex items-start gap-3">
            {/* 角色图标 */}
            <motion.div
              className={cn(
                "flex-shrink-0 w-10 h-10 rounded-xl flex items-center justify-center shadow-lg",
                colors.iconBg,
                colors.glowColor
              )}
              animate={isActive ? { scale: [1, 1.05, 1] } : {}}
              transition={{ duration: 2, repeat: Infinity }}
            >
              <span className={colors.iconColor}>{getRoleIcon(role)}</span>
            </motion.div>

            {/* 名称和描述 */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-2 mb-1">
                <h3 className="text-base font-semibold text-white/90 tracking-tight">
                  {name}
                </h3>
                {/* 状态指示器 */}
                <div className="flex items-center gap-2">
                  <span className="text-[11px] font-medium text-white/40">
                    {getStatusLabel(status)}
                  </span>
                  <motion.div
                    className={cn(
                      "w-2 h-2 rounded-full",
                      statusIndicatorColors[status]
                    )}
                    animate={isActive ? { opacity: [1, 0.4, 1] } : {}}
                    transition={{ duration: 1.5, repeat: Infinity }}
                  />
                </div>
              </div>
              <p className="text-[13px] text-white/40 leading-relaxed line-clamp-2">
                {description}
              </p>
            </div>
          </div>

          {/* 动态状态区域 */}
          <div className="mt-3 min-h-[20px]">
            {status === "THINKING" && (
              <motion.div
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex items-center gap-2"
              >
                <motion.div
                  animate={{ rotate: 360 }}
                  transition={{ duration: 2, repeat: Infinity, ease: "linear" }}
                >
                  <Sparkles className="w-3.5 h-3.5 text-amber-400" />
                </motion.div>
                <span className="text-[12px] text-amber-400/90 font-medium">
                  {dynamicHint}
                </span>
              </motion.div>
            )}

            {status === "WORKING" && (
              <motion.div
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex items-center gap-2"
              >
                <Loader2 className={cn("w-3.5 h-3.5 animate-spin", colors.accentColor)} />
                <span className={cn("text-[12px] font-medium font-mono", colors.accentColor)}>
                  {dynamicHint}
                </span>
              </motion.div>
            )}

            {status === "DONE" && (
              <motion.div
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                className="flex items-center gap-2"
              >
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                <span className="text-[12px] text-emerald-400 font-medium">
                  已完成 {completedTasks > 0 && `· ${completedTasks} 项任务`}
                </span>
              </motion.div>
            )}

            {status === "WAITING" && (
              <div className="flex items-center gap-2 text-white/30">
                <div className="flex gap-0.5">
                  {[0, 1, 2].map((i) => (
                    <motion.span
                      key={i}
                      className="w-1 h-1 rounded-full bg-current"
                      animate={{ opacity: [0.3, 1, 0.3] }}
                      transition={{
                        duration: 1.2,
                        repeat: Infinity,
                        delay: i * 0.2,
                      }}
                    />
                  ))}
                </div>
                <span className="text-[12px]">等待上游完成</span>
              </div>
            )}

            {status === "IDLE" && (
              <span className="text-[12px] text-white/20">就绪</span>
            )}
          </div>
        </div>
      </div>
    </motion.div>
  );
}
