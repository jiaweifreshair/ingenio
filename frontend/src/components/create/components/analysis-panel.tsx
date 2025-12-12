/**
 * AnalysisPanel组件
 * 左右分屏布局：左侧显示需求，右侧显示分析进度或风格选择
 *
 * 功能：
 * - 左侧：需求文本展示（只读）+ AI模型信息 + 选中风格
 * - 右侧：根据阶段显示不同内容
 *   - analyzing阶段：显示AnalysisProgressPanel（实时分析进度）
 *   - style-selection阶段：显示StylePicker（7种设计风格）
 *
 * @author Ingenio Team
 * @since V2.0
 */

'use client';

import React from 'react';
import { AnalysisProgressPanel } from '@/components/analysis/AnalysisProgressPanel';
import { StylePicker } from '@/components/design/style-picker';
import { MODEL_CONFIGS, type UniaixModel } from '@/lib/api/uniaix';
import type { AnalysisProgressMessage } from '@/hooks/use-analysis-sse';
import type { PhaseType } from '@/types/requirement-form';

/**
 * AnalysisPanel组件Props
 */
export interface AnalysisPanelProps {
  /** 需求描述文本 */
  requirement: string;
  /** 选中的AI模型 */
  selectedModel: UniaixModel;
  /** 选中的设计风格 */
  selectedStyle: string | null;
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

  // ==================== 风格选择相关 ====================
  /** 风格选择回调 */
  onStyleSelected: (style: string) => void;
  /** 取消风格选择回调 */
  onStyleCancel: () => void;

  /** 自定义类名 */
  className?: string;
}

/**
 * AnalysisPanel组件
 * 左右分屏布局，展示分析过程和风格选择
 */
export function AnalysisPanel({
  requirement,
  selectedModel,
  selectedStyle,
  currentPhase,
  messages,
  isConnected,
  isCompleted,
  analysisError,
  onStyleSelected,
  onStyleCancel,
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
            {selectedStyle && (
              <p className="text-sm text-muted-foreground">
                <strong>选中风格：</strong>
                {selectedStyle}
              </p>
            )}
          </div>
        </div>

        {/* ==================== 右侧：根据阶段显示不同内容 ==================== */}
        <div className="rounded-2xl border border-border/50 bg-card/30 p-6 backdrop-blur-xl">
          {currentPhase === 'style-selection' ? (
            // 风格选择阶段：显示StylePicker
            <StylePicker
              userRequirement={requirement}
              appType="应用"
              targetPlatform="web"
              useAICustomization={false}
              onStyleSelected={onStyleSelected}
              onCancel={onStyleCancel}
            />
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
