"use client";

import { useEffect, useRef, useState, useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { cn } from "@/lib/utils";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Badge } from "@/components/ui/badge";
import { G3LogEntry } from "@/lib/g3/types";
import {
  Terminal,
  ChevronRight,
  ChevronDown,
  Filter,
  Brain,
  Shield,
  Sword,
  Cpu,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Info,
} from "lucide-react";

/**
 * 角色类型
 */
type LogRole = "PLAYER" | "COACH" | "ARCHITECT" | "SYSTEM";

interface LogStreamProps {
  /** 日志条目列表 */
  logs: G3LogEntry[];
  /** 容器类名 */
  className?: string;
  /** 是否显示过滤器 */
  showFilter?: boolean;
  /** 是否自动折叠详细信息 */
  autoCollapse?: boolean;
}

/**
 * 获取角色对应的中文标签
 */
function getRoleLabel(role: string): string {
  const labels: Record<string, string> = {
    PLAYER: "蓝队",
    COACH: "红队",
    ARCHITECT: "架构师",
    SYSTEM: "系统",
  };
  return labels[role] || role;
}

/**
 * 获取角色图标
 */
function getRoleIcon(role: string) {
  const icons: Record<string, typeof Brain> = {
    PLAYER: Sword,
    COACH: Shield,
    ARCHITECT: Brain,
    SYSTEM: Cpu,
  };
  const Icon = icons[role] || Cpu;
  return <Icon className="w-3 h-3" />;
}

/**
 * 获取日志级别图标
 */
function getLevelIcon(level: string) {
  switch (level) {
    case "SUCCESS":
      return <CheckCircle2 className="w-3 h-3 text-emerald-400" />;
    case "ERROR":
      return <XCircle className="w-3 h-3 text-red-400" />;
    case "WARN":
      return <AlertTriangle className="w-3 h-3 text-yellow-400" />;
    default:
      return <Info className="w-3 h-3 text-slate-400" />;
  }
}

/**
 * 角色颜色配置
 */
const roleColors: Record<string, string> = {
  PLAYER: "text-blue-400 bg-blue-500/10 border-blue-500/30",
  COACH: "text-red-400 bg-red-500/10 border-red-500/30",
  ARCHITECT: "text-purple-400 bg-purple-500/10 border-purple-500/30",
  SYSTEM: "text-green-400 bg-green-500/10 border-green-500/30",
};

/**
 * 判断日志是否为关键节点（需要高亮）
 */
function isKeyMilestone(content: string): boolean {
  const keywords = [
    "开始",
    "完成",
    "成功",
    "失败",
    "错误",
    "启动",
    "结束",
    "生成",
    "第.*轮",
    "Round",
    "COMPLETED",
    "FAILED",
    "契约",
    "Schema",
    "API",
  ];
  return keywords.some((kw) => new RegExp(kw, "i").test(content));
}

/**
 * 判断日志是否为详细信息（可折叠）
 */
function isDetailLog(content: string): boolean {
  const detailPatterns = [
    /^[\s]*[-*]/,  // 列表项
    /^\s+/,        // 缩进内容
    /^DEBUG/i,     // 调试信息
    /field|column|table|index/i,  // 数据库细节
  ];
  return detailPatterns.some((p) => p.test(content));
}

/**
 * 判断日志是否涉及AI调用/模型推理
 *
 * 说明：用于在执行过程中高亮“调用AI”的关键片段，便于定位慢点/失败点。
 */
function isAiCallLog(content: string): boolean {
  const patterns = [
    /\bai\b/i,
    /\bllm\b/i,
    /openai|anthropic|gemini|qwen|deepseek|claude/i,
    /prompt|token|completion|model/i,
    /大模型|模型|推理|调用AI|调用.*模型|生成提示词/i,
  ];
  return patterns.some((p) => p.test(content));
}

/**
 * 日志条目组件
 */
function LogEntry({
  log,
  isCollapsed,
  onToggle,
  showCollapse,
}: {
  log: G3LogEntry;
  isCollapsed: boolean;
  onToggle: () => void;
  showCollapse: boolean;
}) {
  const isMilestone = isKeyMilestone(log.content);
  const isAiCall = isAiCallLog(log.content);
  const roleColor = roleColors[log.role] || roleColors.SYSTEM;

  return (
    <motion.div
      initial={{ opacity: 0, x: -10 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.2 }}
      className={cn(
        "group flex gap-2 py-1.5 px-2 rounded-lg transition-colors",
        isMilestone
          ? "bg-slate-800/50 border-l-2 border-current"
          : "hover:bg-slate-800/30"
      )}
    >
      {/* 时间戳 */}
      <span className="text-slate-600 shrink-0 tabular-nums text-[11px] pt-0.5">
        {new Date(log.timestamp).toLocaleTimeString([], {
          hour12: false,
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        })}
      </span>

      {/* 角色标签 */}
      <Badge
        variant="outline"
        className={cn(
          "shrink-0 px-1.5 py-0 h-5 text-[10px] font-medium gap-1",
          roleColor
        )}
      >
        {getRoleIcon(log.role)}
        {getRoleLabel(log.role)}
      </Badge>

      {/* 内容区域 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-start gap-2">
          {/* 级别图标 */}
          <span className="shrink-0 pt-0.5">{getLevelIcon(log.level)}</span>

          {/* AI调用标识 */}
          {isAiCall && (
            <Badge
              variant="outline"
              className="shrink-0 px-1 py-0 h-4 text-[10px] bg-violet-500/10 border-violet-500/30 text-violet-300 gap-1"
              title="检测到AI调用相关日志"
            >
              <Brain className="w-3 h-3" />
              AI
            </Badge>
          )}

          {/* 日志内容 */}
          <span
            className={cn(
              "text-xs leading-relaxed break-all",
              log.level === "ERROR"
                ? "text-red-400"
                : log.level === "SUCCESS"
                ? "text-emerald-400 font-medium"
                : log.level === "WARN"
                ? "text-yellow-400"
                : "text-slate-300",
              isAiCall && log.level === "INFO" && "text-violet-200",
              isMilestone && "font-medium"
            )}
          >
            {log.content}
          </span>

          {/* 折叠按钮 */}
          {showCollapse && (
            <button
              onClick={onToggle}
              className="shrink-0 p-0.5 text-slate-500 hover:text-slate-300 transition-colors opacity-0 group-hover:opacity-100"
            >
              {isCollapsed ? (
                <ChevronRight className="w-3 h-3" />
              ) : (
                <ChevronDown className="w-3 h-3" />
              )}
            </button>
          )}
        </div>
      </div>
    </motion.div>
  );
}

/**
 * G3 日志流组件
 *
 * 优化版特性：
 * - 角色过滤器
 * - 关键节点高亮
 * - 详细信息折叠
 * - 平滑滚动和动画
 */
export function LogStream({
  logs,
  className,
  showFilter = true,
  autoCollapse = true,
}: LogStreamProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [activeFilters, setActiveFilters] = useState<Set<LogRole>>(
    new Set(["PLAYER", "COACH", "ARCHITECT", "SYSTEM"])
  );
  const [collapsedItems, setCollapsedItems] = useState<Set<number>>(new Set());
  const [showFilterPanel, setShowFilterPanel] = useState(false);
  const [aiOnly, setAiOnly] = useState(false);

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
  }, [logs]);

  // 过滤后的日志
  const filteredLogs = useMemo(() => {
    return logs.filter((log) => {
      if (!activeFilters.has(log.role as LogRole)) return false;
      if (aiOnly) return isAiCallLog(log.content);
      return true;
    });
  }, [logs, activeFilters, aiOnly]);

  const aiLogCount = useMemo(() => {
    return logs.filter((log) => isAiCallLog(log.content)).length;
  }, [logs]);

  // 切换过滤器
  const toggleFilter = (role: LogRole) => {
    setActiveFilters((prev) => {
      const next = new Set(prev);
      if (next.has(role)) {
        next.delete(role);
      } else {
        next.add(role);
      }
      return next;
    });
  };

  // 切换折叠
  const toggleCollapse = (index: number) => {
    setCollapsedItems((prev) => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  };

  // 角色统计
  const roleStats = useMemo(() => {
    const stats: Record<LogRole, number> = {
      PLAYER: 0,
      COACH: 0,
      ARCHITECT: 0,
      SYSTEM: 0,
    };
    logs.forEach((log) => {
      if (log.role in stats) {
        stats[log.role as LogRole]++;
      }
    });
    return stats;
  }, [logs]);

  return (
    <div
      className={cn(
        "flex flex-col h-full bg-slate-950/90 rounded-xl border border-slate-800/80 font-mono text-xs overflow-hidden",
        className
      )}
    >
      {/* 头部工具栏 */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-slate-800/80 bg-slate-900/70">
        <div className="flex items-center gap-2">
          <ChevronRight className="w-4 h-4 text-green-400" />
          <Terminal className="w-4 h-4 text-slate-400" />
          <span className="text-slate-400 font-semibold tracking-wide">
            执行日志
          </span>
          <Badge
            variant="outline"
            className="text-[10px] px-1.5 py-0 h-4 bg-slate-800/50 border-slate-700 text-slate-400"
          >
            {filteredLogs.length} 条
          </Badge>
          {aiLogCount > 0 && (
            <Badge
              variant="outline"
              className="text-[10px] px-1.5 py-0 h-4 bg-violet-500/10 border-violet-500/30 text-violet-300"
              title="检测到AI调用相关日志数量"
            >
              AI {aiLogCount}
            </Badge>
          )}
        </div>

        {/* 过滤器按钮 */}
        {showFilter && (
          <div className="flex items-center gap-2">
            <button
              onClick={() => setAiOnly((v) => !v)}
              className={cn(
                "flex items-center gap-1.5 px-2 py-1 rounded text-xs transition-colors",
                aiOnly
                  ? "bg-violet-500/20 text-violet-200"
                  : "text-slate-400 hover:text-slate-200 hover:bg-slate-800"
              )}
              title="仅显示AI调用相关日志"
            >
              <Brain className="w-3 h-3" />
              AI
            </button>
            <button
              onClick={() => setShowFilterPanel(!showFilterPanel)}
              className={cn(
                "flex items-center gap-1.5 px-2 py-1 rounded text-xs transition-colors",
                showFilterPanel
                  ? "bg-slate-700 text-slate-200"
                  : "text-slate-400 hover:text-slate-200 hover:bg-slate-800"
              )}
            >
              <Filter className="w-3 h-3" />
              筛选
            </button>
          </div>
        )}
      </div>

      {/* 过滤器面板 */}
      <AnimatePresence>
        {showFilterPanel && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden border-b border-slate-800/60"
          >
            <div className="flex items-center gap-2 px-3 py-2 bg-slate-900/50">
              {(["ARCHITECT", "PLAYER", "COACH", "SYSTEM"] as LogRole[]).map(
                (role) => (
                  <button
                    key={role}
                    onClick={() => toggleFilter(role)}
                    className={cn(
                      "flex items-center gap-1.5 px-2 py-1 rounded text-[11px] transition-all",
                      activeFilters.has(role)
                        ? roleColors[role]
                        : "bg-slate-800/30 text-slate-600 border border-slate-700/50"
                    )}
                  >
                    {getRoleIcon(role)}
                    {getRoleLabel(role)}
                    <span className="opacity-60">({roleStats[role]})</span>
                  </button>
                )
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* 日志列表 */}
      <ScrollArea className="flex-1 px-2 py-2" ref={scrollRef}>
        <div className="space-y-0.5">
          <AnimatePresence mode="popLayout">
            {filteredLogs.map((log, i) => {
              const isDetail = autoCollapse && isDetailLog(log.content);
              const isCollapsed = collapsedItems.has(i);

              // 如果是详细日志且被折叠，不显示
              if (isDetail && isCollapsed) {
                return null;
              }

              return (
                <LogEntry
                  key={`${log.timestamp}-${i}`}
                  log={log}
                  isCollapsed={isCollapsed}
                  onToggle={() => toggleCollapse(i)}
                  showCollapse={isDetail}
                />
              );
            })}
          </AnimatePresence>

          {/* 空状态 */}
          {filteredLogs.length === 0 && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="flex flex-col items-center justify-center py-12 text-slate-600"
            >
              <Terminal className="w-10 h-10 mb-3 opacity-30" />
              <span className="text-sm">
                {logs.length === 0 ? "等待 G3 引擎启动..." : "没有匹配的日志"}
              </span>
              {logs.length === 0 && (
                <span className="text-xs mt-1 text-slate-700">
                  输入需求并点击启动按钮开始
                </span>
              )}
            </motion.div>
          )}

          {/* 输入光标动画 */}
          {logs.length > 0 && filteredLogs.length > 0 && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: [0.3, 1, 0.3] }}
              transition={{ duration: 1, repeat: Infinity }}
              className="flex items-center gap-2 px-2 py-1 text-slate-600"
            >
              <span className="text-green-400">{">"}</span>
              <span className="w-2 h-4 bg-green-400/50 animate-pulse" />
            </motion.div>
          )}
        </div>
      </ScrollArea>
    </div>
  );
}
