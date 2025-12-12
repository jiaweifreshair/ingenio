/**
 * CompletedView - 生成完成视图组件
 *
 * 功能：
 * - 显示生成完成后的完整结果页面
 * - 包含进度展示、Agent结果、模块列表、代码下载、快速操作等
 * - 提供探索更多功能的入口
 *
 * Props:
 * - appSpecId: AppSpec ID
 * - agents: Agent执行状态列表
 * - modules: 功能模块列表
 * - generationStats: 生成统计数据
 * - taskStatus: 任务状态响应
 * - onDownload: 下载回调
 * - onShare: 分享回调
 * - onNavigate: 导航回调
 */
'use client';

import React from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Badge } from '@/components/ui/badge';
import { CheckCircle2 } from 'lucide-react';
import { AgentExecutionStatus } from '@/types/wizard';
import { TaskStatusResponse } from '@/lib/api/generate';
import { GenerationStats, type GenerationStats as GenerationStatsType } from '@/components/wizard/generation-stats';
import { QuickActionCards } from '@/components/wizard/quick-action-cards';
import { CodeDownloadPanel } from '@/components/wizard/code-download-panel';
import { AgentResultsCard } from './agent-results-card';
import { ModuleListCard, type Module } from './module-list-card';
import { ExploreMoreCard } from './explore-more-card';

/**
 * CompletedView Props接口
 */
export interface CompletedViewProps {
  /** AppSpec ID */
  appSpecId: string;
  /** Agent执行状态列表 */
  agents: AgentExecutionStatus[];
  /** 功能模块列表 */
  modules: Module[];
  /** 生成统计数据 */
  generationStats: GenerationStatsType | null;
  /** 任务状态响应 */
  taskStatus?: TaskStatusResponse;
  /** 下载回调 */
  onDownload: () => void;
  /** 分享回调 */
  onShare: () => void;
  /** 导航回调 */
  onNavigate: (path: string) => void;
}

/**
 * CompletedView组件
 */
export const CompletedView: React.FC<CompletedViewProps> = ({
  appSpecId,
  agents,
  modules,
  generationStats,
  taskStatus,
  onDownload,
  onShare,
  onNavigate,
}) => {
  return (
    <div className="min-h-screen bg-background">
      {/* 顶部标题栏 - E2E测试关键元素 */}
      <div className="border-b bg-background px-6 py-4 mb-8">
        <div className="flex items-center gap-4">
          <h1 className="text-xl font-semibold">AppSpec 生成向导</h1>
          <Badge variant="default" className="text-sm bg-green-500">
            生成完成
          </Badge>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-8">
        {/* 页面副标题 */}
        <div className="mb-8">
          <div className="flex items-center gap-4 mb-4">
            <h2 className="text-3xl font-bold">生成完成！</h2>
          </div>
          <p className="text-sm text-muted-foreground">ID: {appSpecId}</p>
        </div>

        {/* 总体进度 */}
        <Card className="mb-8">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium">总体进度</span>
              <span className="text-sm text-muted-foreground">100%</span>
            </div>
            <Progress value={100} className="h-2" />
            <div className="flex items-center gap-2 mt-2">
              <CheckCircle2 className="w-4 h-4 text-green-500" />
              <p className="text-sm text-green-600">所有Agent执行完成</p>
            </div>
          </CardContent>
        </Card>

        {/* 生成统计信息 */}
        {generationStats && (
          <GenerationStats stats={generationStats} className="mb-8" />
        )}

        {/* 快速操作卡片 */}
        <div className="mb-8">
          <h3 className="text-xl font-semibold mb-4">接下来做什么？</h3>
          <QuickActionCards
            appId={appSpecId}
            projectId={appSpecId}
            onDownload={onDownload}
            onShare={onShare}
          />
        </div>

        {/* Agent执行结果 */}
        <AgentResultsCard agents={agents} className="mb-8" />

        {/* 模块列表 */}
        <ModuleListCard modules={modules} className="mb-8" />

        {/* 代码下载面板 */}
        {taskStatus && (taskStatus.downloadUrl || taskStatus.codeSummary) && (
          <CodeDownloadPanel
            codeDownloadUrl={taskStatus.downloadUrl}
            codeSummary={taskStatus.codeSummary}
            generatedFileList={taskStatus.generatedFileList}
            className="mb-8"
          />
        )}

        {/* 探索更多功能 */}
        <ExploreMoreCard
          appSpecId={appSpecId}
          onNavigate={onNavigate}
          className="mb-8"
        />
      </div>
    </div>
  );
};

// 设置displayName便于调试
CompletedView.displayName = 'CompletedView';
