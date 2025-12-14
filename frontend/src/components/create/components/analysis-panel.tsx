/**
 * AnalysisPanel组件
 * 左右分屏布局：左侧显示需求，右侧显示分析进度
 *
 * 功能：
 * - 左侧：需求文本展示（只读）+ AI模型信息
 * - 右侧：显示实时分析进度
 *   - analyzing阶段：显示AnalysisProgressPanel（实时分析进度）
 *   - style-selection阶段：显示"正在生成原型..."（自动流转中间状态）
 *
 * V2.0简化版：风格选择已自动化，不再显示StylePicker
 *
 * @author Ingenio Team
 * @since V2.0
 */

'use client';

import React from 'react';
import { AnalysisProgressPanel } from '@/components/analysis/AnalysisProgressPanel';
import { Loader2 } from 'lucide-react';
import { MODEL_CONFIGS, type UniaixModel } from '@/lib/api/uniaix';
import type { AnalysisProgressMessage } from '@/hooks/use-analysis-sse';
import type { PhaseType } from '@/types/requirement-form';

/**
 * AnalysisPanel组件Props
 * V2.0简化版：移除风格选择相关属性
 */
export interface AnalysisPanelProps {
  /** 需求描述文本 */
  requirement: string;
  /** 选中的AI模型 */
  selectedModel: UniaixModel;
  /** 当前阶段 */
  currentPhase: PhaseType;

  // ==================== 分析相关 ====================
  /** 分析消息列表 */
  messages: AnalysisProgressMessage[];
  /** SSE连接状态 */
  isConnected: boolean;
  /** 分析是否完成 */
  isCompleted: boolean;
  /** 分析错误信息 */
  analysisError: string | null;

  /** 自定义类名 */
  className?: string;
}

/**
 * AnalysisPanel组件
 * 左右分屏布局，展示分析过程
 * V2.0简化版：风格选择已自动化
 */
export function AnalysisPanel({
  requirement,
  selectedModel,
  currentPhase,
  messages,
  isConnected,
  isCompleted,
  analysisError,
  className,
}: AnalysisPanelProps): React.ReactElement {
  // 获取AI模型名称
  const modelName = MODEL_CONFIGS.find((m) => m.id === selectedModel)?.name || selectedModel;

  return (
    <div className={className}>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* ==================== 左侧：需求展示 ==================== */}
        <div className="space-y-4">
          {/* 需求文本（只读） */}
          <div className="rounded-2xl border border-border/50 bg-card/30 p-6 backdrop-blur-xl">
            <h3 className="text-lg font-semibold mb-4">你的需求</h3>
            <div className="text-sm text-muted-foreground whitespace-pre-wrap">
              {requirement}
            </div>
          </div>

          {/* 配置信息 */}
          <div className="rounded-2xl border border-border/50 bg-card/30 p-4 backdrop-blur-xl space-y-2">
            <p className="text-sm text-muted-foreground">
              <strong>AI模型：</strong>
              {modelName}
            </p>
          </div>
        </div>

        {/* ==================== 右侧：根据阶段显示不同内容 ==================== */}
        <div className="rounded-2xl border border-border/50 bg-card/30 p-6 backdrop-blur-xl">
          {currentPhase === 'style-selection' ? (
            // style-selection阶段：显示原型生成中的加载状态
            <div className="flex flex-col items-center justify-center h-full min-h-[300px] space-y-4">
              <Loader2 className="h-12 w-12 animate-spin text-primary" />
              <div className="text-center space-y-2">
                <h3 className="text-lg font-semibold">正在生成原型...</h3>
                <p className="text-sm text-muted-foreground">
                  AI正在根据您的需求生成可交互原型，请稍候
                </p>
              </div>
            </div>
          ) : (
            // 分析阶段：显示分析进度面板
            <AnalysisProgressPanel
              messages={messages}
              isConnected={isConnected}
              isCompleted={isCompleted}
              error={analysisError}
            />
          )}
        </div>
      </div>
    </div>
  );
}
