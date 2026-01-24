/**
 * 执行面板组件
 * 右侧面板，展示Agent执行过程、日志流和性能指标
 */
'use client';

import React, { useState, useMemo } from 'react';
import { cn } from '@/lib/utils';
import { AgentExecutionStatus, AgentState, AgentType } from '@/types/wizard';
import { G3ConsoleView } from '@/components/g3/g3-console-view';
import { G3LogEntry } from '@/lib/g3/types';
import { Switch } from '@/components/ui/switch';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Separator } from '@/components/ui/separator';
import {
  Activity,
  Clock,
  Zap,
  AlertCircle,
  CheckCircle2,
  Loader2,
  Terminal,
  BarChart3
} from 'lucide-react';
import { AgentTimeline } from './agent-timeline';
import { GenerationStages } from './generation-stages';
import { RoleTaskCards, mapAgentToRoleTask, type RoleTask } from './role-task-cards';
import { WebSocketStatus } from './websocket-status';
import { useGenerationToasts } from '@/hooks/use-generation-toasts';

/**
 * 执行面板Props
 */
interface ExecutionPanelProps {
  /** Agent状态列表 */
  agents: AgentExecutionStatus[];
  /** 当前生成步骤 */
  currentStep: string;
  /** 总体进度 */
  progress: number;
  /** 是否已连接WebSocket */
  isConnected: boolean;
  /** 是否正在连接WebSocket */
  isConnecting?: boolean;
  /** WebSocket重连尝试次数 */
  connectionAttempts?: number;
  /** 错误信息 */
  error?: string;
  /** WebSocket重连回调 */
  onReconnect?: () => void;
  /** Agent点击回调 */
  onAgentClick?: (agent: AgentExecutionStatus) => void;
  /** 类名 */
  className?: string;
}

/**
 * 获取步骤显示名称
 */
const getStepDisplayName = (step: string): string => {
  switch (step) {
    case 'idle':
      return '待开始';
    case 'planning':
      return '需求分析中';
    case 'executing':
      return '应用生成中';
    case 'validating':
      return '质量验证中';
    case 'completed':
      return '生成完成';
    case 'failed':
      return '生成失败';
    default:
      return step;
  }
};

/**
 * 映射currentStep到阶段ID
 * 用于GenerationStages组件
 */
const getCurrentStageId = (step: string): string | undefined => {
  switch (step) {
    case 'planning':
      return 'analysis'; // 需求分析阶段
    case 'schema_generation':
      return 'schema'; // 数据库设计阶段
    case 'executing':
    case 'code_generation':
      return 'code'; // 代码生成阶段
    case 'validating':
      return 'validation'; // 测试验证阶段
    case 'completed':
      return 'preview'; // 预览发布阶段
    default:
      return undefined;
  }
};

/**
 * 获取步骤状态颜色
 */
const getStepStatusColor = (step: string): string => {
  switch (step) {
    case 'planning':
    case 'executing':
    case 'validating':
      return 'text-blue-600 bg-blue-50 border-blue-200';
    case 'completed':
      return 'text-green-600 bg-green-50 border-green-200';
    case 'failed':
      return 'text-red-600 bg-red-50 border-red-200';
    default:
      return 'text-gray-600 bg-gray-50 border-gray-200';
  }
};

/**
 * 计算总体统计信息
 */
const calculateStats = (agents: AgentExecutionStatus[]) => {
  const completed = agents.filter(agent => agent.status === AgentState.COMPLETED).length;
  const running = agents.filter(agent => agent.status === AgentState.RUNNING).length;
  const failed = agents.filter(agent => agent.status === AgentState.FAILED).length;
  const totalTokens = agents.reduce((sum, agent) =>
    sum + (agent.metrics?.tokenUsage.total || 0), 0
  );
  const totalCost = agents.reduce((sum, agent) =>
    sum + (agent.metrics?.tokenUsage.estimatedCost || 0), 0
  );

  return { completed, running, failed, totalTokens, totalCost };
};

/**
 * 将Agents映射为角色任务
 */
const mapAgentsToRoleTasks = (agents: AgentExecutionStatus[]): RoleTask[] => {
  const roleTasks: RoleTask[] = [];

  agents.forEach((agent) => {
    const roleTask = mapAgentToRoleTask(
      agent.type,
      agent.status,
      agent.progress,
      undefined, // currentTask会从agent.type推断
      agent.duration
    );

    if (roleTask) {
      roleTasks.push(roleTask);
    }
  });

  // 如果没有Agent数据，返回默认的4个角色（待开始状态）
  if (roleTasks.length === 0) {
    const defaultRoles: RoleTask[] = [
      {
        role: 'product_manager',
        roleName: '产品经理',
        currentTask: '分析用户需求，规划应用功能',
        status: 'idle',
        progress: 0,
        icon: <Activity className="h-5 w-5" />,
        colorTheme: {
          bg: 'from-purple-500 to-pink-500',
          text: 'text-purple-700',
          border: 'border-purple-500',
          icon: 'bg-gradient-to-br from-purple-500 to-pink-500',
        },
      },
      {
        role: 'architect',
        roleName: '架构师',
        currentTask: '设计数据库架构和系统模块',
        status: 'idle',
        progress: 0,
        icon: <Activity className="h-5 w-5" />,
        colorTheme: {
          bg: 'from-blue-500 to-cyan-500',
          text: 'text-blue-700',
          border: 'border-blue-500',
          icon: 'bg-gradient-to-br from-blue-500 to-cyan-500',
        },
      },
      {
        role: 'engineer',
        roleName: '工程师',
        currentTask: '生成Kotlin Multiplatform代码',
        status: 'idle',
        progress: 0,
        icon: <Activity className="h-5 w-5" />,
        colorTheme: {
          bg: 'from-green-500 to-emerald-500',
          text: 'text-green-700',
          border: 'border-green-500',
          icon: 'bg-gradient-to-br from-green-500 to-emerald-500',
        },
      },
      {
        role: 'tester',
        roleName: '测试工程师',
        currentTask: '编译、测试和性能验证',
        status: 'idle',
        progress: 0,
        icon: <Activity className="h-5 w-5" />,
        colorTheme: {
          bg: 'from-orange-500 to-amber-500',
          text: 'text-orange-700',
          border: 'border-orange-500',
          icon: 'bg-gradient-to-br from-orange-500 to-amber-500',
        },
      },
    ];
    return defaultRoles;
  }

  return roleTasks;
};

/**
 * 执行面板组件
 */
export const ExecutionPanel: React.FC<ExecutionPanelProps> = ({
  agents,
  currentStep,
  progress,
  isConnected,
  isConnecting = false,
  connectionAttempts = 0,
  error,
  onReconnect,
  onAgentClick,
  className,
}) => {
  const [activeTab, setActiveTab] = useState('timeline');
  const [selectedAgent, setSelectedAgent] = useState<AgentExecutionStatus>();
  const [viewMode, setViewMode] = useState<'standard' | 'g3'>('g3');

  // G3 Logic Mapping
  const activeRole = useMemo(() => {
    const runningAgent = agents.find(a => a.status === AgentState.RUNNING);
    if (!runningAgent) return null;
    
    switch(runningAgent.type) {
        case AgentType.PLAN: return 'ARCHITECT';
        case AgentType.EXECUTE: return 'PLAYER';
        case AgentType.VALIDATE: return 'COACH';
        default: return null;
    }
  }, [agents]);

  const apiLogs = useMemo<G3LogEntry[]>(() => {
    // Synthesize logs from agents
    const logs: G3LogEntry[] = [];
    
    // Add initial system log
    logs.push({
        timestamp: Date.now(),
        role: 'SYSTEM',
        step: 'INIT',
        content: isConnected ? '✅ G3 Engine Connected' : '⏳ Waiting for connection...',
        level: isConnected ? 'SUCCESS' : 'INFO'
    });

    agents.forEach(agent => {
        if (agent.status === AgentState.RUNNING || agent.status === AgentState.COMPLETED) {
            let role: 'ARCHITECT' | 'PLAYER' | 'COACH' | 'SYSTEM' = 'SYSTEM';
            if (agent.type === AgentType.PLAN) role = 'ARCHITECT';
            if (agent.type === AgentType.EXECUTE) role = 'PLAYER';
            if (agent.type === AgentType.VALIDATE) role = 'COACH';

            if (agent.message) {
                 logs.push({
                    timestamp: agent.duration ? Date.now() - agent.duration : Date.now(), // Approximate
                    role: role as G3LogEntry['role'],
                    step: 'PROCESS',
                    content: agent.message,
                    level: 'INFO'
                });
            }
        }
    });

    return logs;
  }, [agents, isConnected]);

  const stats = useMemo(() => calculateStats(agents), [agents]);
  const hasRunningAgent = agents.some(agent => agent.status === AgentState.RUNNING);

  // 映射Agents到角色任务
  const roleTasks = useMemo(() => mapAgentsToRoleTasks(agents), [agents]);

  // Phase 5.2: Toast通知集成
  useGenerationToasts(isConnected, {
    enabled: true,
    showConnectionNotifications: true,
    showAgentStartNotifications: false, // 避免过于频繁
    showAgentCompleteNotifications: true,
    showErrorNotifications: true,
  });

  const handleAgentClick = (agent: AgentExecutionStatus) => {
    setSelectedAgent(agent);
    onAgentClick?.(agent);
  };

  return (
    <div className={cn("h-full flex flex-col", className)}>
      {/* Phase 5.1: WebSocket连接状态增强 */}
      <div className="border-b bg-background p-4">
        <WebSocketStatus
          isConnected={isConnected}
          isConnecting={isConnecting}
          error={error || null}
          connectionAttempts={connectionAttempts}
          onReconnect={onReconnect}
          showDetails={true}
          className="mb-3"
        />

        {/* 总体进度 */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="font-medium">总体进度</span>
            <span>{progress}%</span>
          </div>
          <Progress value={progress} className="h-2" />
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">
              当前步骤：{getStepDisplayName(currentStep)}
            </span>
            <Badge
              variant="outline"
              className={cn("text-xs", getStepStatusColor(currentStep))}
            >
              {hasRunningAgent && <Loader2 className="h-3 w-3 mr-1 animate-spin" />}
              {getStepDisplayName(currentStep)}
            </Badge>
          </div>
        </div>
      </div>

      {/* View Mode Toggle */}
      <div className="px-4 py-2 bg-muted/20 border-b flex items-center justify-end gap-2">
         <Label htmlFor="view-mode" className="text-xs text-muted-foreground mr-2">可视化模式</Label>
         <div className="flex items-center space-x-2">
            <span className={cn("text-xs font-medium transition-colors", viewMode === 'standard' ? "text-primary" : "text-muted-foreground")}>标准</span>
            <Switch 
                id="view-mode" 
                checked={viewMode === 'g3'} 
                onCheckedChange={(checked) => setViewMode(checked ? 'g3' : 'standard')}
            />
            <span className={cn("text-xs font-medium transition-colors", viewMode === 'g3' ? "text-primary" : "text-muted-foreground")}>G3引擎</span>
         </div>
      </div>

      {viewMode === 'g3' ? (
        <div className="flex-1 p-4 bg-background overflow-hidden flex flex-col">
            <G3ConsoleView 
                logs={apiLogs}
                activeRole={activeRole}
                round={1} // Mock round for wizard flow
                isRunning={stats.running > 0}
                requirement=""
                autoStart={true}
                className="flex-1 h-full shadow-none border-0"
            />
        </div>
      ) : (
      <>
      {/* 错误提示 */}
      {error && (
        <div className="p-4">
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        </div>
      )}

      {/* 5阶段可视化 - 码上飞风格 */}
      <div className="border-b bg-background/50 px-4 py-3">
        <GenerationStages
          currentStageId={getCurrentStageId(currentStep)}
          overallProgress={progress}
          vertical={false}
        />
      </div>

      {/* 角色任务卡片 - 码上飞风格 */}
      <div className="border-b bg-background/50 px-4 py-3">
        <RoleTaskCards
          tasks={roleTasks}
          vertical={false}
        />
      </div>

      {/* 内容区域 - 移除固定高度限制，让日志区域充分利用可用空间 */}
      <div className="flex-1 min-h-0 overflow-hidden">
        <Tabs value={activeTab} onValueChange={setActiveTab} className="h-full flex flex-col">
          {/* 标签页导航 */}
          <div className="border-b bg-background px-4 py-2">
            <TabsList className="grid w-full grid-cols-3">
              <TabsTrigger value="timeline" className="text-xs">
                <Activity className="h-4 w-4 mr-2" />
                执行流程
              </TabsTrigger>
              <TabsTrigger value="logs" className="text-xs">
                <Terminal className="h-4 w-4 mr-2" />
                执行日志
              </TabsTrigger>
              <TabsTrigger value="metrics" className="text-xs">
                <BarChart3 className="h-4 w-4 mr-2" />
                性能指标
              </TabsTrigger>
            </TabsList>
          </div>

          {/* 标签页内容 - 增加最小高度确保内容可见 */}
          <div className="flex-1 overflow-hidden min-h-[500px]">
            <TabsContent value="timeline" className="h-full m-0">
              <ScrollArea className="h-full">
                <div className="p-4">
                  <AgentTimeline
                    agents={agents}
                    selectedAgentId={selectedAgent?.id}
                    onAgentClick={handleAgentClick}
                    showDetails={true}
                  />
                </div>
              </ScrollArea>
            </TabsContent>

            <TabsContent value="logs" className="h-full m-0">
              {/* 修复：不使用ref绑定到ScrollArea，直接使用简单滚动 */}
              <ScrollArea className="h-full">
                <div className="p-4">
                  <Card>
                    <CardHeader className="pb-3">
                      <CardTitle className="text-base flex items-center gap-2">
                        <Terminal className="h-5 w-5" />
                        执行日志
                      </CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-3">
                    {/* Agent消息日志 */}
                    {agents.map(agent => (
                      <div key={agent.id} className="space-y-2">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-sm">{agent.name}</span>
                          <Badge variant={
                            agent.status === AgentState.COMPLETED ? 'default' :
                            agent.status === AgentState.FAILED ? 'destructive' :
                            agent.status === AgentState.RUNNING ? 'default' :
                            'secondary'
                          }>
                            {agent.status}
                          </Badge>
                          {agent.duration && (
                            <span className="text-xs text-muted-foreground">
                              <Clock className="h-3 w-3 inline mr-1" />
                              {(agent.duration / 1000).toFixed(1)}s
                            </span>
                          )}
                        </div>

                        {agent.message && (
                          <div className="text-sm text-muted-foreground pl-4 border-l-2 border-gray-200">
                            {agent.message}
                          </div>
                        )}

                        {agent.currentTask && agent.status === AgentState.RUNNING && (
                          <div className="text-sm text-blue-600 pl-4 border-l-2 border-blue-200 animate-pulse">
                            → {agent.currentTask}
                          </div>
                        )}

                        {agent.error && (
                          <div className="text-sm text-red-600 bg-red-50 p-2 rounded pl-4 border-l-2 border-red-400">
                            <strong>错误:</strong> {agent.error.message}
                          </div>
                        )}
                      </div>
                    ))}

                    {/* 性能统计 */}
                    {(stats.totalTokens > 0 || stats.totalCost > 0) && (
                      <>
                        <Separator />
                        <div className="grid grid-cols-2 gap-4 text-sm">
                          <div className="flex items-center gap-2">
                            <Zap className="h-4 w-4 text-yellow-500" />
                            <span>Token使用:</span>
                            <span className="font-medium">{stats.totalTokens}</span>
                          </div>
                          <div className="flex items-center gap-2">
                            <Clock className="h-4 w-4 text-blue-500" />
                            <span>预估成本:</span>
                            <span className="font-medium">${stats.totalCost.toFixed(4)}</span>
                          </div>
                        </div>
                      </>
                    )}
                    </CardContent>
                  </Card>
                </div>
              </ScrollArea>
            </TabsContent>

            <TabsContent value="metrics" className="h-full m-0">
              <ScrollArea className="h-full">
                <div className="p-4 space-y-4">
                  {/* 统计概览 */}
                  <Card>
                    <CardHeader className="pb-3">
                      <CardTitle className="text-base">执行概览</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="grid grid-cols-3 gap-4 text-center">
                        <div className="flex flex-col items-center">
                          <CheckCircle2 className="h-8 w-8 text-green-500 mb-1" />
                          <span className="text-2xl font-bold">{stats.completed}</span>
                          <span className="text-xs text-muted-foreground">已完成</span>
                        </div>
                        <div className="flex flex-col items-center">
                          <Loader2 className="h-8 w-8 text-blue-500 mb-1 animate-spin" />
                          <span className="text-2xl font-bold">{stats.running}</span>
                          <span className="text-xs text-muted-foreground">执行中</span>
                        </div>
                        <div className="flex flex-col items-center">
                          <AlertCircle className="h-8 w-8 text-red-500 mb-1" />
                          <span className="text-2xl font-bold">{stats.failed}</span>
                          <span className="text-xs text-muted-foreground">失败</span>
                        </div>
                      </div>
                    </CardContent>
                  </Card>

                  {/* Agent详情 */}
                  {selectedAgent && (
                    <Card>
                      <CardHeader className="pb-3">
                        <CardTitle className="text-base">{selectedAgent.name} - 详细信息</CardTitle>
                      </CardHeader>
                      <CardContent className="space-y-4">
                        <div className="grid grid-cols-2 gap-4 text-sm">
                          <div>
                            <span className="text-muted-foreground">状态:</span>
                            <div className="font-medium">{selectedAgent.status}</div>
                          </div>
                          <div>
                            <span className="text-muted-foreground">进度:</span>
                            <div className="font-medium">{selectedAgent.progress}%</div>
                          </div>
                          {selectedAgent.duration && (
                            <div>
                              <span className="text-muted-foreground">耗时:</span>
                              <div className="font-medium">{(selectedAgent.duration / 1000).toFixed(1)}s</div>
                            </div>
                          )}
                          {selectedAgent.startTime && (
                            <div>
                              <span className="text-muted-foreground">开始时间:</span>
                              <div className="font-medium">
                                {new Date(selectedAgent.startTime).toLocaleTimeString()}
                              </div>
                            </div>
                          )}
                        </div>

                        {selectedAgent.metrics && (
                          <>
                            <Separator />
                            <div className="space-y-2">
                              <h4 className="font-medium">性能指标</h4>
                              <div className="grid grid-cols-2 gap-2 text-sm">
                                <div>
                                  <span className="text-muted-foreground">Token输入:</span>
                                  <div className="font-medium">{selectedAgent.metrics.tokenUsage.inputTokens}</div>
                                </div>
                                <div>
                                  <span className="text-muted-foreground">Token输出:</span>
                                  <div className="font-medium">{selectedAgent.metrics.tokenUsage.outputTokens}</div>
                                </div>
                                <div>
                                  <span className="text-muted-foreground">API调用:</span>
                                  <div className="font-medium">{selectedAgent.metrics.apiCalls}</div>
                                </div>
                                <div>
                                  <span className="text-muted-foreground">成功率:</span>
                                  <div className="font-medium">{selectedAgent.metrics.successRate}%</div>
                                </div>
                              </div>
                            </div>
                          </>
                        )}

                        {selectedAgent.error && (
                          <>
                            <Separator />
                            <div className="space-y-2">
                              <h4 className="font-medium text-red-600">错误信息</h4>
                              <div className="text-sm text-red-600 bg-red-50 p-3 rounded">
                                <div><strong>错误代码:</strong> {selectedAgent.error.code}</div>
                                <div><strong>错误信息:</strong> {selectedAgent.error.message}</div>
                              </div>
                            </div>
                          </>
                        )}
                      </CardContent>
                    </Card>
                  )}

                  {/* 性能对比 */}
                  <Card>
                    <CardHeader className="pb-3">
                      <CardTitle className="text-base">Token使用分布</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="space-y-3">
                        {agents.filter(agent => agent.metrics?.tokenUsage.total).map(agent => {
                          const tokenTotal = agent.metrics?.tokenUsage.total || 0;
                          const percentage = stats.totalTokens > 0
                            ? (tokenTotal / stats.totalTokens) * 100
                            : 0;

                          return (
                            <div key={agent.id} className="space-y-1">
                              <div className="flex justify-between text-sm">
                                <span>{agent.name}</span>
                                <span>{tokenTotal} tokens</span>
                              </div>
                              <Progress
                                value={percentage}
                                className="h-2"
                              />
                            </div>
                          );
                        })}
                      </div>
                    </CardContent>
                  </Card>
                </div>
              </ScrollArea>
            </TabsContent>
          </div>
        </Tabs>
      </div>
      </>
      )}
    </div>
  );
};
