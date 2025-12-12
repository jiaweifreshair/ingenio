/**
 * Agent执行时间线组件
 * 垂直时间轴展示PlanAgent → ExecuteAgent → ValidateAgent的执行流程
 */
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { AgentExecutionStatus, AgentState, AgentType } from '@/types/wizard';
import { CheckCircle2, Circle, XCircle, Loader2, PauseCircle } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';

/**
 * Agent时间线组件Props
 */
interface AgentTimelineProps {
  /** Agent状态列表 */
  agents: AgentExecutionStatus[];
  /** 选中的Agent ID */
  selectedAgentId?: string;
  /** Agent点击回调 */
  onAgentClick?: (agent: AgentExecutionStatus) => void;
  /** 是否显示详细信息 */
  showDetails?: boolean;
  /** 类名 */
  className?: string;
}

/**
 * 获取Agent状态图标
 */
const getAgentStatusIcon = (status: AgentState) => {
  const iconClass = "h-6 w-6";

  switch (status) {
    case AgentState.PENDING:
      return <Circle className={cn(iconClass, "text-muted-foreground")} />;
    case AgentState.RUNNING:
      return <Loader2 className={cn(iconClass, "text-primary animate-spin")} />;
    case AgentState.COMPLETED:
      return <CheckCircle2 className={cn(iconClass, "text-green-500")} />;
    case AgentState.FAILED:
      return <XCircle className={cn(iconClass, "text-destructive")} />;
    case AgentState.PAUSED:
      return <PauseCircle className={cn(iconClass, "text-yellow-500")} />;
    default:
      return <Circle className={cn(iconClass, "text-muted-foreground")} />;
  }
};

/**
 * 获取Agent状态颜色
 */
const getAgentStatusColor = (status: AgentState): string => {
  switch (status) {
    case AgentState.PENDING:
      return 'bg-muted';
    case AgentState.RUNNING:
      return 'bg-primary';
    case AgentState.COMPLETED:
      return 'bg-green-500';
    case AgentState.FAILED:
      return 'bg-destructive';
    case AgentState.PAUSED:
      return 'bg-yellow-500';
    default:
      return 'bg-muted';
  }
};

/**
 * 获取Agent显示名称
 */
const getAgentDisplayName = (type: AgentType): string => {
  switch (type) {
    case AgentType.PLAN:
      return '需求分析';
    case AgentType.EXECUTE:
      return 'AppSpec生成';
    case AgentType.VALIDATE:
      return '质量验证';
    default:
      return type;
  }
};

/**
 * 获取Agent描述
 */
const getAgentDescription = (type: AgentType): string => {
  switch (type) {
    case AgentType.PLAN:
      return '分析用户需求，制定系统架构和模块划分方案';
    case AgentType.EXECUTE:
      return '根据规划结果生成完整的AppSpec应用规范';
    case AgentType.VALIDATE:
      return '校验生成结果的完整性、一致性和质量评分';
    default:
      return '';
  }
};

/**
 * 格式化耗时
 */
const formatDuration = (ms: number): string => {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
};

/**
 * Agent时间线项组件
 */
const AgentTimelineItem: React.FC<{
  agent: AgentExecutionStatus;
  isLast: boolean;
  isSelected: boolean;
  showDetails: boolean;
  onClick?: () => void;
}> = ({ agent, isLast, isSelected, showDetails, onClick }) => {
  const statusColor = getAgentStatusColor(agent.status);
  const isActive = agent.status === AgentState.RUNNING;

  return (
    <div className="relative flex gap-4">
      {/* 时间线连接线 */}
      {!isLast && (
        <div
          className={cn(
            "absolute left-3 top-12 w-0.5 h-full -ml-px",
            isActive ? statusColor : "bg-border"
          )}
        />
      )}

      {/* 状态图标 */}
      <div className={cn(
        "relative z-10 flex items-center justify-center w-6 h-6 rounded-full border-2 bg-background",
        isActive && "ring-2 ring-primary/20"
      )}>
        {getAgentStatusIcon(agent.status)}
      </div>

      {/* Agent卡片 */}
      <Card
        className={cn(
          "flex-1 mb-6 cursor-pointer transition-all hover:shadow-md",
          isSelected && "ring-2 ring-primary",
          isActive && "border-primary"
        )}
        onClick={onClick}
      >
        <CardHeader className="pb-3">
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <CardTitle className="text-base font-semibold flex items-center gap-2">
                {getAgentDisplayName(agent.type)}
                <Badge variant={
                  agent.status === AgentState.COMPLETED ? 'default' :
                  agent.status === AgentState.FAILED ? 'destructive' :
                  agent.status === AgentState.RUNNING ? 'default' :
                  'secondary'
                }>
                  {agent.status === AgentState.RUNNING && '执行中'}
                  {agent.status === AgentState.COMPLETED && '已完成'}
                  {agent.status === AgentState.FAILED && '失败'}
                  {agent.status === AgentState.PENDING && '待执行'}
                  {agent.status === AgentState.PAUSED && '已暂停'}
                </Badge>
              </CardTitle>
              <p className="text-sm text-muted-foreground mt-1">
                {getAgentDescription(agent.type)}
              </p>
            </div>

            {agent.duration && (
              <div className="text-sm text-muted-foreground ml-4">
                {formatDuration(agent.duration)}
              </div>
            )}
          </div>
        </CardHeader>

        <CardContent className="space-y-3">
          {/* 进度条 */}
          {(agent.status === AgentState.RUNNING || agent.progress > 0) && (
            <div className="space-y-1">
              <div className="flex items-center justify-between text-sm">
                <span className="text-muted-foreground">进度</span>
                <span className="font-medium">{agent.progress}%</span>
              </div>
              <Progress value={agent.progress} className="h-2" />
            </div>
          )}

          {/* 当前任务 */}
          {agent.currentTask && agent.status === AgentState.RUNNING && (
            <div className="text-sm">
              <span className="text-muted-foreground">当前任务：</span>
              <span className="ml-2">{agent.currentTask}</span>
            </div>
          )}

          {/* 消息 */}
          {agent.message && (
            <div className="text-sm text-muted-foreground">
              {agent.message}
            </div>
          )}

          {/* 错误信息 */}
          {agent.error && (
            <div className="text-sm text-destructive bg-destructive/10 p-3 rounded-md">
              <p className="font-medium mb-1">错误: {agent.error.code}</p>
              <p>{agent.error.message}</p>
            </div>
          )}

          {/* 详细信息 */}
          {showDetails && agent.status === AgentState.COMPLETED && agent.metrics && (
            <div className="grid grid-cols-2 gap-2 text-sm pt-2 border-t">
              <div>
                <span className="text-muted-foreground">Token使用:</span>
                <span className="ml-2 font-medium">
                  {agent.metrics.tokenUsage.totalTokens}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground">API调用:</span>
                <span className="ml-2 font-medium">
                  {agent.metrics.apiCalls}
                </span>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

/**
 * Agent时间线组件
 */
export const AgentTimeline: React.FC<AgentTimelineProps> = ({
  agents,
  selectedAgentId,
  onAgentClick,
  showDetails = false,
  className,
}) => {
  return (
    <div className={cn("py-4", className)}>
      {agents.map((agent, index) => (
        <AgentTimelineItem
          key={agent.id}
          agent={agent}
          isLast={index === agents.length - 1}
          isSelected={selectedAgentId === agent.id}
          showDetails={showDetails}
          onClick={() => onAgentClick?.(agent)}
        />
      ))}
    </div>
  );
};
