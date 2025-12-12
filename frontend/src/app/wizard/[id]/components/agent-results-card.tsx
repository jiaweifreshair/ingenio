/**
 * AgentResultsCard - Agent执行结果卡片组件
 *
 * 功能：
 * - 显示所有Agent的执行结果
 * - 显示Agent状态、耗时、Token使用情况
 * - 支持空状态展示
 *
 * Props:
 * - agents: Agent状态列表
 */
'use client';

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Activity, CheckCircle2 } from 'lucide-react';
import { AgentExecutionStatus } from '@/types/wizard';

/**
 * AgentResultsCard Props接口
 */
export interface AgentResultsCardProps {
  /** Agent状态列表 */
  agents: AgentExecutionStatus[];
  /** 自定义类名 */
  className?: string;
}

/**
 * AgentResultsCard组件
 */
export const AgentResultsCard: React.FC<AgentResultsCardProps> = ({
  agents,
  className,
}) => {
  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Activity className="w-5 h-5 text-primary" />
          Agent执行结果
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {agents.length === 0 ? (
          <div className="text-center text-muted-foreground py-4">
            暂无Agent执行记录
          </div>
        ) : (
          agents.map((agent) => (
            <div
              key={agent.id}
              className="flex items-center justify-between p-3 bg-muted rounded-lg"
            >
              <div className="flex items-center gap-3">
                <CheckCircle2 className="w-5 h-5 text-green-500" />
                <div>
                  <p className="font-medium">{agent.name}</p>
                  <p className="text-sm text-muted-foreground">
                    {agent.duration
                      ? `耗时: ${(agent.duration / 1000).toFixed(1)}s`
                      : '已完成'}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <Badge variant="default" className="bg-green-500">
                  已完成
                </Badge>
                {agent.metrics?.tokenUsage.total &&
                  agent.metrics.tokenUsage.total > 0 && (
                    <span className="text-xs text-muted-foreground">
                      {agent.metrics.tokenUsage.total} tokens
                    </span>
                  )}
              </div>
            </div>
          ))
        )}
      </CardContent>
    </Card>
  );
};

// 设置displayName便于调试
AgentResultsCard.displayName = 'AgentResultsCard';
