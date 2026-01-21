"use client";

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Terminal, ShieldAlert, Cpu, Loader2, Play } from 'lucide-react';
import type { G3LogEntry } from '@/types/g3';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { createAndMonitorG3Job } from '@/lib/api/g3';

interface G3LogViewerProps {
  /** 需求描述（必填） */
  requirement: string;
  /** 行业模板 ID（可选，用于加载 Blueprint） */
  templateId?: string;
  /** 是否禁用启动按钮 */
  disabled?: boolean;
  /**
   * 当前任务ID回调（可选）
   *
   * 用途：
   * - 供 /lab 工具面板调用 Toolset / Shell
   */
  onJobIdChange?: (jobId: string | null) => void;
}

export function G3LogViewer({ requirement, templateId, disabled = false, onJobIdChange }: G3LogViewerProps) {
  const [logs, setLogs] = useState<G3LogEntry[]>([]);
  const [isRunning, setIsRunning] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [logFilter, setLogFilter] = useState('');
  // SSE 取消句柄（用于重启任务或卸载时关闭连接）
  const cancelRef = useRef<(() => void) | null>(null);

  const filteredLogs = useMemo(() => {
    const keyword = logFilter.trim().toLowerCase();
    if (!keyword) return logs;
    return logs.filter((log) => (
      log.message.toLowerCase().includes(keyword) ||
      log.role.toLowerCase().includes(keyword) ||
      log.level.toLowerCase().includes(keyword)
    ));
  }, [logs, logFilter]);

  // Auto-scroll logs
  useEffect(() => {
    if (scrollRef.current) {
      const scrollElement = scrollRef.current.querySelector('[data-radix-scroll-area-viewport]');
      if (scrollElement) {
        scrollElement.scrollTop = scrollElement.scrollHeight;
      }
    }
  }, [logs]);

  useEffect(() => {
    return () => {
      cancelRef.current?.();
      cancelRef.current = null;
    };
  }, []);

  const startGeneration = async () => {
    if (!requirement || requirement.trim().length < 10) return;
    setIsRunning(true);
    onJobIdChange?.(null);
    cancelRef.current?.();
    cancelRef.current = null;
    setLogs([{
      timestamp: new Date().toISOString(),
      role: 'SYSTEM',
      level: 'info',
      message: 'G3引擎启动，正在建立连接并准备生成任务...'
    }]);

    try {
      const { cancel } = await createAndMonitorG3Job(
        requirement,
        {
          onSubmitted: (id) => {
            onJobIdChange?.(id);
            setLogs(prev => ([...prev, {
              timestamp: new Date().toISOString(),
              role: 'SYSTEM',
              level: 'info',
              message: `任务已创建，jobId=${id}`,
            }]));
          },
          onSubmitError: (error) => {
            setLogs(prev => ([...prev, {
              timestamp: new Date().toISOString(),
              role: 'EXECUTOR',
              level: 'error',
              message: `任务创建失败: ${error}`,
            }]));
            setIsRunning(false);
          },
          onLog: (entry) => {
            if (entry.level === 'heartbeat') return;
            setLogs(prev => ([...prev, entry]));
          },
          onError: (error) => {
            setLogs(prev => ([...prev, {
              timestamp: new Date().toISOString(),
              role: 'EXECUTOR',
              level: 'warn',
              message: `日志流异常: ${error}`,
            }]));
          },
          onComplete: () => {
            setIsRunning(false);
          },
        },
        templateId ? { templateId } : undefined
      );
      cancelRef.current = cancel;
    } catch (error) {
      console.error('Stream error:', error);
      setLogs(prev => [...prev, {
        timestamp: new Date().toISOString(),
        role: 'EXECUTOR',
        level: 'error',
        message: `Connection failed: ${error}`
      }]);
      setIsRunning(false);
    }
  };

  const getRoleIcon = (role: string) => {
    switch (role) {
      case 'PLAYER': return <Cpu className="w-4 h-4 text-blue-400" />;
      case 'COACH': return <ShieldAlert className="w-4 h-4 text-red-400" />;
      case 'EXECUTOR': return <Terminal className="w-4 h-4 text-green-400" />;
      case 'ARCHITECT': return <Terminal className="w-4 h-4 text-indigo-400" />;
      default: return <Terminal className="w-4 h-4" />;
    }
  };

  const getRoleColor = (role: string) => {
    switch (role) {
      case 'PLAYER': return 'text-blue-400 border-blue-900/30 bg-blue-900/10';
      case 'COACH': return 'text-red-400 border-red-900/30 bg-red-900/10';
      case 'EXECUTOR': return 'text-green-400 border-green-900/30 bg-green-900/10';
      case 'ARCHITECT': return 'text-indigo-400 border-indigo-900/30 bg-indigo-900/10';
      default: return 'text-gray-400';
    }
  };

  return (
    <Card className="flex flex-col bg-slate-950 border-slate-800 text-slate-100 overflow-hidden shadow-2xl h-[650px]">
      <CardHeader className="bg-slate-900/50 border-b border-slate-800 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Cpu className="w-5 h-5 text-indigo-400" />
            <div>
              <CardTitle className="text-sm font-mono text-indigo-300">G3 日志监控</CardTitle>
              <CardDescription className="text-xs text-slate-500">
                <span className="flex items-center gap-1">
                  <span
                    className={`w-2 h-2 rounded-full ${isRunning ? 'bg-green-500 animate-pulse' : 'bg-slate-600'}`}
                  />
                  状态: {isRunning ? '执行中' : '空闲'}
                </span>
              </CardDescription>
            </div>
          </div>
          <div className="flex gap-2 items-center">
            <Input
              value={logFilter}
              onChange={(e) => setLogFilter(e.target.value)}
              placeholder="过滤日志..."
              className="h-8 w-40 text-xs bg-slate-900 border-slate-700 text-slate-100 placeholder:text-slate-500"
            />
            <Button
              size="sm"
              variant={isRunning ? 'secondary' : 'default'}
              onClick={startGeneration}
              disabled={isRunning || disabled || !requirement?.trim()}
              className="h-8 text-xs font-mono bg-indigo-600 hover:bg-indigo-500 text-white border-none disabled:opacity-50"
            >
              {isRunning ? <Loader2 className="w-3 h-3 mr-1 animate-spin" /> : <Play className="w-3 h-3 mr-1" />}
              {isRunning ? '执行中...' : '启动任务'}
            </Button>
          </div>
        </div>
      </CardHeader>

      <ScrollArea ref={scrollRef} className="flex-1 p-4 font-mono text-xs">
        {logs.length === 0 && !isRunning && (
          <div className="flex flex-col items-center justify-center h-full text-slate-600 space-y-2">
            <Terminal className="w-8 h-8 opacity-20" />
            <p>等待启动 G3 任务...</p>
            <div className="flex gap-2 text-[10px] text-slate-500">
              <Badge variant="outline" className="border-slate-700">后端: Java G3</Badge>
              <Badge variant="outline" className="border-slate-700">流式: SSE</Badge>
            </div>
          </div>
        )}

        <div className="space-y-3">
          {filteredLogs.map((log, i) => (
            <div key={i} className="flex gap-3 animate-in fade-in slide-in-from-left-2 duration-300">
              <div className="mt-0.5 opacity-70">{getRoleIcon(log.role)}</div>
              <div className="flex-1 space-y-1">
                <div className="flex items-center gap-2">
                  <span className={`px-1.5 py-0.5 rounded border text-[10px] font-bold ${getRoleColor(log.role)}`}>
                    {log.role}
                  </span>
                  <span className="text-slate-500 text-[10px]">
                    {log.timestamp?.includes('T')
                      ? log.timestamp.split('T')[1].replace('Z', '')
                      : log.timestamp}
                  </span>
                </div>
                <p
                  className={`text-sm leading-relaxed ${
                    log.level === 'error'
                      ? 'text-red-300'
                      : log.level === 'warn'
                        ? 'text-yellow-300'
                        : log.level === 'success'
                          ? 'text-green-300'
                          : 'text-slate-300'
                  }`}
                >
                  {log.message}
                </p>
              </div>
            </div>
          ))}
          {isRunning && <div className="h-4 w-2 bg-indigo-500/50 animate-pulse ml-9" />}
        </div>
      </ScrollArea>
    </Card>
  );
}

/**
 * G3LogViewerSimple - 仅展示日志列表（不发起请求）
 *
 * 用途：
 * - 在“确认设计/生成中”的弹窗里作为视觉覆盖层展示日志
 * - 避免在多个组件里重复实现日志渲染逻辑
 */
export function G3LogViewerSimple({ logs }: { logs: G3LogEntry[] }) {
  return (
    <div className="rounded-md border border-slate-800 bg-slate-950 text-slate-100 overflow-hidden">
      <ScrollArea className="h-[360px] p-3 font-mono text-xs">
        <div className="space-y-2">
          {logs.map((log, i) => (
            <div key={i} className="flex gap-2">
              <span
                className={`shrink-0 px-1.5 py-0.5 rounded border text-[10px] font-bold ${
                  log.role === 'PLAYER'
                    ? 'text-blue-400 border-blue-900/30 bg-blue-900/10'
                    : log.role === 'COACH'
                      ? 'text-red-400 border-red-900/30 bg-red-900/10'
                      : log.role === 'EXECUTOR'
                        ? 'text-green-400 border-green-900/30 bg-green-900/10'
                        : log.role === 'ARCHITECT'
                          ? 'text-indigo-400 border-indigo-900/30 bg-indigo-900/10'
                          : 'text-gray-400'
                }`}
              >
                {log.role}
              </span>
              <div className="min-w-0 flex-1">
                <div className="text-slate-500 text-[10px]">
                  {log.timestamp?.includes('T')
                    ? log.timestamp.split('T')[1].replace('Z', '')
                    : log.timestamp}
                </div>
                <div
                  className={`text-sm leading-relaxed ${
                    log.level === 'error'
                      ? 'text-red-300'
                      : log.level === 'warn'
                        ? 'text-yellow-300'
                        : log.level === 'success'
                          ? 'text-green-300'
                          : 'text-slate-300'
                  }`}
                >
                  {log.message}
                </div>
              </div>
            </div>
          ))}
          {logs.length === 0 && (
            <div className="text-slate-500 text-center py-6">暂无日志</div>
          )}
        </div>
      </ScrollArea>
    </div>
  );
}
