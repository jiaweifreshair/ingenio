"use client";

import { useMemo, useState, type ReactNode } from "react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { AgentCard } from "./agent-card";
import { LogStream } from "./log-stream";
import type { G3LogEntry } from "@/lib/g3/types";
import { Play, Loader2, Send, Activity, ChevronDown, Lock, Unlock, Maximize2, Minimize2 } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useLanguage } from "@/contexts/LanguageContext";

export interface G3AtomsConsoleViewProps {
  logs: G3LogEntry[];
  activeRole: "ARCHITECT" | "PLAYER" | "COACH" | null;
  isRunning: boolean;
  requirement: string;
  onRequirementChange?: (value: string) => void;
  onStart?: () => void;
  /** 头部右侧插槽（如结果按钮等） */
  headerRightSlot?: ReactNode;
  /** 右侧预览区插槽 */
  previewSlot?: ReactNode;
  className?: string;
}

/**
 * Atoms 风格三栏控制台（左：Command Center，中：Trace，右：Preview）。
 *
 * 说明：
 * - 当前版本仍以“日志/产物”为核心链路；后续将中栏从 Log 升级为结构化 AgentEvent。
 * - Direct Order 先做 UI 占位（需要 Agent-OS 才能真正路由到某个角色）。
 */
export function G3AtomsConsoleView({
  logs,
  activeRole,
  isRunning,
  requirement,
  onRequirementChange,
  onStart,
  headerRightSlot,
  previewSlot,
  className,
}: G3AtomsConsoleViewProps) {
  const { t } = useLanguage();
  const [focusRole, setFocusRole] = useState<"ARCHITECT" | "PLAYER" | "COACH" | null>(null);
  const [directOrder, setDirectOrder] = useState("");
  const [autoScroll, setAutoScroll] = useState(true);
  const [isPreviewExpanded, setIsPreviewExpanded] = useState(false);

  /** 角色筛选配置 */
  const roleOptions = [
    { value: null, label: "全部角色", color: "text-white/70" },
    { value: "ARCHITECT" as const, label: "架构师", color: "text-purple-400" },
    { value: "PLAYER" as const, label: "蓝队", color: "text-blue-400" },
    { value: "COACH" as const, label: "红队", color: "text-red-400" },
  ];

  /** 获取当前选中角色的显示信息 */
  const currentRoleOption = roleOptions.find(r => r.value === focusRole) || roleOptions[0];

  const filteredLogs = useMemo(() => {
    if (!focusRole) return logs;
    // 只聚焦某个角色 + SYSTEM，避免上下文断裂
    return logs.filter((l) => l.role === focusRole || l.role === "SYSTEM");
  }, [logs, focusRole]);

  // 动态布局配置
  const layout = useMemo(() => {
    // 展开模式：右侧全屏
    if (isPreviewExpanded) {
      return { left: "hidden", middle: "hidden", right: "col-span-12" };
    }
    // 标准模式
    return isRunning
      ? { left: "col-span-3", middle: "col-span-6", right: "col-span-3" } // 执行中：追踪优先
      : { left: "col-span-3", middle: "col-span-3", right: "col-span-6" }; // 闲置/完成：预览优先
  }, [isRunning, isPreviewExpanded]);

  return (
    <div
      className={cn(
        "flex flex-col w-full min-h-[calc(100vh-60px)]", // 增加高度，减少顶部留白
        "bg-[#0a0a0c]",
        "text-white/90",
        "p-4 md:p-6 rounded-3xl", // 稍微减小内边距
        "border border-white/[0.06]",
        "shadow-2xl shadow-black/50",
        "overflow-hidden",
        className
      )}
      data-testid="g3-atoms-console"
    >
      {/* Main Grid */}
      <div className="grid grid-cols-12 gap-6 flex-1 min-h-0 overflow-hidden">
        {/* Left: Command Center */}
        <div className={cn(layout.left, "flex flex-col min-h-0 transition-all duration-500 ease-in-out")}>
          <ScrollArea className="flex-1 min-h-0">
            <div className="flex flex-col gap-4 pr-2">
              <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/[0.06] group/cmd">
                {/* Header Slot (Promoted) */}
                {headerRightSlot && (
                  <div className="flex items-center justify-end mb-3">
                    {headerRightSlot}
                  </div>
                )}
                
                {/* Unified Command Bar */}
                <div className="relative">
                  {/* Glow Effect */}
                  <div className="absolute -inset-0.5 bg-gradient-to-r from-blue-500/20 to-purple-500/20 rounded-xl blur opacity-0 group-hover/cmd:opacity-100 transition-opacity duration-1000" />
                  
                  <div className="relative flex items-center bg-[#0F0F12] border border-white/[0.08] rounded-xl transition-all duration-300 focus-within:ring-1 focus-within:ring-blue-500/50 focus-within:border-blue-500/50 hover:border-white/[0.12]">
                    <Input
                      value={requirement}
                      onChange={(e) => onRequirementChange?.(e.target.value)}
                      disabled={isRunning || !onRequirementChange}
                      className="flex-1 h-11 bg-transparent border-0 text-white placeholder:text-white/20 font-mono text-xs focus-visible:ring-0 focus-visible:ring-offset-0 px-4"
                      placeholder="Input mission objective..."
                    />
                    
                    <div className="pr-1.5 pl-1">
                      <Button
                        onClick={onStart}
                        disabled={isRunning || !onStart || !requirement?.trim()}
                        size="sm"
                        className={cn(
                          "h-8 px-3 rounded-lg transition-all duration-300 font-medium text-xs",
                          isRunning
                            ? "bg-white/[0.05] text-white/40 cursor-not-allowed"
                            : "bg-blue-600 hover:bg-blue-500 text-white shadow-lg shadow-blue-500/20"
                        )}
                      >
                        {isRunning ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        ) : (
                          <div className="flex items-center gap-1.5">
                            <span>RUN</span>
                            <Play className="w-3 h-3 fill-current" />
                          </div>
                        )}
                      </Button>
                    </div>
                  </div>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/[0.06]">
                <div className="flex items-center justify-between mb-3">
                  <div className="text-xs font-semibold text-white/70">{t('ui.agent_selector')}</div>
                  <Button
                    size="sm"
                    variant="ghost"
                    className="h-7 px-2 text-white/60 hover:text-white hover:bg-white/[0.06]"
                    onClick={() => setFocusRole(null)}
                  >
                    全部
                  </Button>
                </div>

                <div className="flex flex-col gap-3">
                  <div onClick={() => setFocusRole("ARCHITECT")} className="cursor-pointer">
                    <AgentCard
                      role="ARCHITECT"
                      name="架构师"
                      description="拆解需求，输出契约/Schema 设计"
                      status={isRunning && activeRole === "ARCHITECT" ? "WORKING" : focusRole === "ARCHITECT" ? "DONE" : "IDLE"}
                    />
                  </div>
                  <div onClick={() => setFocusRole("PLAYER")} className="cursor-pointer">
                    <AgentCard
                      role="PLAYER"
                      name="蓝队"
                      description="按模板生成代码，实现业务逻辑"
                      status={isRunning && activeRole === "PLAYER" ? "WORKING" : focusRole === "PLAYER" ? "DONE" : "IDLE"}
                    />
                  </div>
                  <div onClick={() => setFocusRole("COACH")} className="cursor-pointer">
                    <AgentCard
                      role="COACH"
                      name="红队"
                      description="验证漏洞，推动自修复闭环"
                      status={isRunning && activeRole === "COACH" ? "WORKING" : focusRole === "COACH" ? "DONE" : "IDLE"}
                    />
                  </div>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/[0.06]">
                <div className="text-xs font-semibold text-white/70 mb-2">{t('ui.direct_order')}</div>
                <div className="text-[11px] text-white/40 mb-2">
                  需要 Agent-OS 支持角色定向路由，当前为 UI 占位。
                </div>
                <div className="flex items-center gap-2">
                  <Input
                    value={directOrder}
                    onChange={(e) => setDirectOrder(e.target.value)}
                    disabled
                    className="h-9 bg-white/[0.04] border-white/[0.08] text-white placeholder:text-white/30 rounded-xl font-mono text-xs"
                    placeholder="例如：只让架构师把 User 表加 vip_level"
                  />
                  <Button
                    size="sm"
                    disabled
                    className="h-9 px-3 bg-white/[0.06] hover:bg-white/[0.10] text-white border border-white/[0.10] rounded-xl"
                    title="Agent-OS 阶段开放"
                  >
                    <Send className="w-4 h-4" />
                  </Button>
                </div>
              </div>
            </div>
          </ScrollArea>
        </div>

        {/* Middle: Trace */}
        <div className={cn(layout.middle, "flex flex-col min-h-0 transition-all duration-500 ease-in-out")}>
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25 }}
            className="flex-1 min-h-0 flex flex-col rounded-2xl overflow-hidden border border-white/[0.06] bg-white/[0.02]"
          >
            {/* 优化后的实时追踪头部 */}
            <div className="px-4 py-3 border-b border-white/[0.06] shrink-0">
              {/* 第一行：标题 + 角色筛选 + 自动滚动控制 */}
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Activity className="w-4 h-4 text-emerald-400" />
                  <span className="text-sm font-semibold text-white/80">{t('ui.live_trace')}</span>
                </div>

                <div className="flex items-center gap-2">
                  {/* 角色筛选下拉框 */}
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-7 px-3 bg-white/[0.04] hover:bg-white/[0.08] border border-white/[0.08] rounded-lg text-xs gap-1.5"
                      >
                        <span className={cn("font-medium", currentRoleOption.color)}>
                          {currentRoleOption.label}
                        </span>
                        <ChevronDown className="w-3 h-3 text-white/50" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent
                      align="end"
                      className="bg-slate-900/95 backdrop-blur-sm border-white/[0.08] min-w-[120px]"
                    >
                      {roleOptions.map((option) => (
                        <DropdownMenuItem
                          key={option.value ?? 'all'}
                          onClick={() => setFocusRole(option.value)}
                          className={cn(
                            "text-xs cursor-pointer",
                            focusRole === option.value && "bg-white/[0.08]"
                          )}
                        >
                          <span className={option.color}>{option.label}</span>
                        </DropdownMenuItem>
                      ))}
                    </DropdownMenuContent>
                  </DropdownMenu>

                  {/* 自动滚动开关 */}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setAutoScroll(!autoScroll)}
                    className={cn(
                      "h-7 w-7 p-0 rounded-lg border",
                      autoScroll
                        ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/20"
                        : "bg-white/[0.04] border-white/[0.08] text-white/50 hover:bg-white/[0.08]"
                    )}
                    title={autoScroll ? "自动滚动已开启" : "自动滚动已暂停"}
                  >
                    {autoScroll ? <Lock className="w-3 h-3" /> : <Unlock className="w-3 h-3" />}
                  </Button>
                </div>
              </div>

              {/* 第二行：日志统计 + 实时状态指示 */}
              <div className="flex items-center justify-between mt-2">
                <div className="flex items-center gap-3 text-[11px] text-white/50">
                  <span>共 <span className="text-white/70 font-medium">{filteredLogs.length}</span> 条日志</span>
                  {focusRole && (
                    <span className="text-white/40">
                      (筛选自 {logs.length} 条)
                    </span>
                  )}
                </div>

                {isRunning && (
                  <div className="flex items-center gap-1.5">
                    <span className="relative flex h-2 w-2">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                    </span>
                    <span className="text-[11px] text-emerald-400 font-medium">实时</span>
                  </div>
                )}
              </div>
            </div>
            {/* 日志流区域 - 使用 flex-1 和 min-h-0 确保正确约束高度 */}
            <div className="flex-1 min-h-0 overflow-hidden">
              <LogStream
                logs={filteredLogs}
                className="h-full"
                showFilter
                autoCollapse
                autoScroll={autoScroll}
              />
            </div>
          </motion.div>
        </div>

        {/* Right: Preview */}
        <div className={cn(layout.right, "flex flex-col min-h-0 transition-all duration-500 ease-in-out relative group/right")}>
           {/* Maximize Toggle Button */}
           <Button
            variant="ghost"
            size="icon"
            onClick={() => setIsPreviewExpanded(!isPreviewExpanded)}
            className="absolute top-2 right-4 z-50 h-8 w-8 bg-black/20 hover:bg-black/60 text-white/40 hover:text-white backdrop-blur-sm rounded-lg opacity-0 group-hover/right:opacity-100 transition-opacity"
            title={isPreviewExpanded ? "还原" : "最大化"}
           >
             {isPreviewExpanded ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
           </Button>

          {previewSlot || (
            <div className="flex-1 min-h-0 rounded-2xl border border-white/[0.06] bg-white/[0.02] p-4 text-xs text-white/40">
              预览区未配置
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
