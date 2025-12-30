"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { G3LogEntry as LocalG3LogEntry, G3Task } from "@/lib/g3/types";
import { createAndMonitorG3Job } from "@/lib/api/g3";
import { G3LogEntry as ApiG3LogEntry } from "@/types/g3";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import { LogStream } from "./log-stream";
import { AgentCard } from "./agent-card";
import { Play, ShieldCheck, Sword, Hammer, Loader2 } from "lucide-react";
import { toast } from "@/hooks/use-toast";

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
  console.log('[DEBUG] G3Console Rendering, autoStart:', autoStart);
  const [isRunning, setIsRunning] = useState(false);
  const [requirement, setRequirement] = useState(initialRequirement || "创建一个请假系统，需要审批流");
  const [logs, setLogs] = useState<LocalG3LogEntry[]>([]);
  const [activeRole, setActiveRole] = useState<'ARCHITECT' | 'PLAYER' | 'COACH' | null>(null);
  const [round, setRound] = useState(0);
  const [jobId, setJobId] = useState<string | null>(null);
  const cancelRef = useRef<(() => void) | null>(null);

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
      toast({ title: "G3 引擎执行成功", description: "代码已交付" });
    }
  }, []);

  const handleStart = useCallback(async () => {
    console.log('[DEBUG] handleStart called - using backend API');
    if (!requirement.trim()) return;

    setIsRunning(true);
    setLogs([]);
    setRound(0);
    setActiveRole(null);
    setJobId(null);

    // 添加启动日志
    setLogs([{
      timestamp: Date.now(),
      role: 'SYSTEM',
      step: 'INIT',
      content: '🚀 G3 引擎启动，正在连接后端服务...',
      level: 'INFO',
    }]);

    try {
      const { cancel } = await createAndMonitorG3Job(requirement, {
        onSubmitted: (id) => {
          console.log('[G3Console] Job submitted:', id);
          setJobId(id);
          setLogs(prev => [...prev, {
            timestamp: Date.now(),
            role: 'SYSTEM',
            step: 'INIT',
            content: `✅ 任务已提交，ID: ${id.substring(0, 8)}...`,
            level: 'SUCCESS',
          }]);
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
          // 构造 G3Task 对象供回调使用
          const task: G3Task = {
            id: jobId || 'unknown',
            requirement,
            status: 'COMPLETED',
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
    }
  }, [requirement, onComplete, handleLogEntry, jobId, round, isRunning]);

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
    if (autoStart && !isRunning && logs.length === 0) {
      handleStart();
    }
  }, [autoStart, isRunning, logs.length, handleStart]);

  return (
    <div className={`flex flex-col h-[600px] w-full bg-slate-950 text-slate-200 p-6 rounded-xl border border-slate-800 shadow-2xl overflow-hidden ${className}`}>
      
      {/* Header / Control Panel */}
      <div className="flex items-center gap-4 mb-6">
        <div className="flex items-center gap-2 mr-auto">
          <div className="p-2 bg-blue-600 rounded-lg">
            <Sword className="w-5 h-5 text-white" />
          </div>
          <div>
            <h2 className="text-xl font-bold tracking-tight text-white">G3 Battle Console</h2>
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <span className="flex items-center gap-1"><ShieldCheck className="w-3 h-3" /> Security-First</span>
              <span>•</span>
              <span className="flex items-center gap-1"><Hammer className="w-3 h-3" /> Auto-Fix</span>
            </div>
          </div>
        </div>

        {/* In embedded mode, input is read-only or hidden */}
        {!autoStart && (
          <div className="flex-1 max-w-lg">
              <Input 
                  value={requirement}
                  onChange={e => setRequirement(e.target.value)}
                  disabled={isRunning}
                  className="bg-slate-900 border-slate-700 text-slate-200 placeholder:text-slate-600 font-mono text-sm"
                  placeholder="Enter mission objective..."
              />
          </div>
        )}

        {/* Start Button (Hidden in auto-start mode) */}
        {!autoStart && (
          <Button 
              onClick={handleStart} 
              disabled={isRunning}
              className="bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-bold shadow-lg shadow-blue-900/20"
          >
              {isRunning ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Play className="w-4 h-4 mr-2" />}
              {isRunning ? "ENGAGED" : "START ENGINE"}
          </Button>
        )}
        
        {autoStart && isRunning && (
           <Badge variant="outline" className="bg-blue-900/20 text-blue-400 border-blue-500/30 animate-pulse">
              AUTONOMOUS MODE
           </Badge>
        )}
      </div>

      {/* Main Battle Field */}
      <div className="grid grid-cols-12 gap-6 flex-1 min-h-0">
        
        {/* Left: Agents Status */}
        <div className="col-span-3 flex flex-col gap-4">
            <AgentCard 
                role="ARCHITECT"
                name="Architect"
                description="Deconstructs requirements into specs."
                status={activeRole === 'ARCHITECT' ? 'WORKING' : 'IDLE'}
            />
            <AgentCard 
                role="PLAYER"
                name="Blue Team"
                description="Builds features using secure templates."
                status={activeRole === 'PLAYER' ? 'WORKING' : 'IDLE'}
            />
            <AgentCard 
                role="COACH"
                name="Red Team"
                description="Attacks code with IDOR & Injection."
                status={activeRole === 'COACH' ? 'WORKING' : 'IDLE'}
            />
        </div>

        {/* Center: Battle Log */}
        <div className="col-span-9 flex flex-col gap-4">
            {/* Status Bar */}
            <div className="flex items-center justify-between px-4 py-2 bg-slate-900/50 rounded-lg border border-slate-800">
                <div className="flex items-center gap-4">
                    <span className="text-xs uppercase text-slate-500 font-bold">Current Phase</span>
                    <Badge variant="outline" className="bg-slate-800 border-slate-700 text-slate-300">
                        {round > 0 ? `ROUND ${round}` : "STANDBY"}
                    </Badge>
                </div>
                <div className="flex items-center gap-2 text-xs text-slate-500">
                    <div className={`w-2 h-2 rounded-full ${isRunning ? "bg-green-500 animate-pulse" : "bg-slate-600"}`} />
                    System Status: {isRunning ? "ONLINE" : "READY"}
                </div>
            </div>

            {/* Terminal */}
            <LogStream logs={logs} className="flex-1" />
        </div>

      </div>
    </div>
  );
}