'use client';

import React from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { CheckCircle2, AlertTriangle, ExternalLink, Sparkles, ArrowRight, Edit2, Globe, Search, X } from 'lucide-react';
import { Input } from '@/components/ui/input';
import {
  IntentClassificationResult,
  INTENT_DISPLAY_MAP,
  getConfidenceLevel,
  getConfidenceLevelColor,
} from '@/types/intent';
import { MotionWrapper } from '@/components/ui/motion-wrapper';
import { cn } from '@/lib/utils';

/**
 * IntentResultPanel组件属性接口
 */
export interface IntentResultPanelProps {
  /** 意图识别结果 */
  result: IntentClassificationResult;
  /** 确认意图回调 */
  onConfirm: () => void;
  /** 修改意图回调 */
  onModify: () => void;
  /** 关键词变更回调 */
  onKeywordsChange?: (keywords: string[]) => void;
  /** 是否正在加载 */
  loading?: boolean;
  /** 下一步操作按钮文本 */
  nextActionText?: string;
}

/**
 * IntentResultPanel - 意图识别结果展示面板
 *
 * 功能：
 * - 展示AI识别的用户意图类型（克隆/设计/混合）
 * - 可视化置信度分数
 * - 展示提取的关键词和参考URL
 * - 显示定制化需求和警告信息
 * - 提供确认或修改操作
 *
 * @author Ingenio Team
 * @version 2.1.0 (Redesigned)
 * @since 2025-12-03
 */
export function IntentResultPanel({
  result,
  onConfirm,
  onModify,
  onKeywordsChange,
  loading = false,
  nextActionText = '确认方案',
}: IntentResultPanelProps): React.ReactElement {
  // 获取意图显示信息
  const intentInfo = INTENT_DISPLAY_MAP[result.intent];

  // 获取置信度等级
  const confidenceLevel = getConfidenceLevel(result.confidence);
  const confidenceLevelColor = getConfidenceLevelColor(confidenceLevel);

  // 判断是否高置信度（≥0.7）
  const isHighConfidence = result.confidence >= 0.7;

  return (
    <MotionWrapper className="flex flex-col h-full max-w-4xl mx-auto" data-testid="intent-result-panel">
      {/* 顶部状态栏：置信度与警告 */}
      <div className="flex items-center justify-between mb-8 px-2">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Sparkles className="w-4 h-4 text-purple-500" />
          <span>AI 智能分析完成</span>
        </div>
        <div className="flex items-center gap-3">
          {!isHighConfidence && (
             <div className="flex items-center gap-1.5 text-amber-600 bg-amber-50 dark:bg-amber-900/20 px-3 py-1 rounded-full text-xs font-medium border border-amber-200 dark:border-amber-800">
               <AlertTriangle className="w-3 h-3" />
               <span>建议复核</span>
             </div>
          )}
          <div className={cn("flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border bg-background/50 backdrop-blur-sm", confidenceLevelColor)}>
            <div className={cn("w-1.5 h-1.5 rounded-full bg-current")} />
            <span>置信度 {(result.confidence * 100).toFixed(0)}%</span>
          </div>
        </div>
      </div>

      {/* 核心意图展示卡片 */}
      <div className="relative mb-8 group">
        <div className={cn(
          "absolute inset-0 bg-gradient-to-r rounded-2xl blur-xl opacity-20 dark:opacity-10 transition-opacity duration-500 group-hover:opacity-30",
          intentInfo.colorClass
        )} />
        <Card className="relative border-0 ring-1 ring-border/50 shadow-xl bg-background/80 backdrop-blur-xl overflow-hidden">
          <div className="p-8 md:p-10 text-center space-y-6">
            {/* Icon */}
            <div className="relative inline-block">
               <div className={cn(
                 "w-20 h-20 md:w-24 md:h-24 rounded-3xl flex items-center justify-center text-5xl md:text-6xl shadow-lg bg-gradient-to-br text-white mb-2 transform transition-transform duration-500 group-hover:scale-110 group-hover:rotate-3",
                 intentInfo.colorClass
               )}>
                 {intentInfo.icon}
               </div>
            </div>
            
            {/* Text */}
            <div className="space-y-3">
              <h2 className="text-2xl md:text-3xl font-bold tracking-tight text-foreground">
                {intentInfo.displayName}
              </h2>
              <p className="text-lg text-muted-foreground max-w-xl mx-auto leading-relaxed">
                {intentInfo.description}
              </p>
            </div>

            {/* Next Action Preview */}
            {result.suggestedNextAction && (
              <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-secondary/50 text-sm text-secondary-foreground border border-border/50">
                <ArrowRight className="w-4 h-4 text-primary" />
                <span>下一步：{result.suggestedNextAction}</span>
              </div>
            )}
          </div>
        </Card>
      </div>

      {/* 详情网格区域 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-8">
        {/* 左侧：分析与推理 */}
        <Card className="p-5 border-0 ring-1 ring-border/50 bg-background/50 backdrop-blur-sm space-y-4">
          <div className="flex items-center gap-2 pb-2 border-b border-border/50">
            <Search className="w-4 h-4 text-blue-500" />
            <h3 className="font-medium text-sm">分析推理</h3>
          </div>
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground leading-relaxed">
              {result.reasoning}
            </p>
            {result.customizationRequirement && (
              <div className="p-3 rounded-lg bg-amber-50 dark:bg-amber-900/10 border border-amber-100 dark:border-amber-900/50">
                <span className="text-xs font-medium text-amber-600 dark:text-amber-400 block mb-1">定制需求</span>
                <p className="text-xs text-amber-800 dark:text-amber-300">{result.customizationRequirement}</p>
              </div>
            )}
          </div>
        </Card>

        {/* 右侧：上下文信息 */}
        <Card className="p-5 border-0 ring-1 ring-border/50 bg-background/50 backdrop-blur-sm space-y-4 flex flex-col">
          <div className="flex items-center gap-2 pb-2 border-b border-border/50">
            <Globe className="w-4 h-4 text-green-500" />
            <h3 className="font-medium text-sm">关键信息提取</h3>
          </div>
          
          <div className="flex-1 space-y-4">
            {/* URLs */}
            {result.referenceUrls && result.referenceUrls.length > 0 ? (
              <div className="space-y-2">
                <span className="text-xs text-muted-foreground font-medium">参考链接</span>
                <div className="space-y-1.5">
                  {result.referenceUrls.slice(0, 2).map((url, i) => (
                    <a key={i} href={url} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 text-xs text-blue-600 dark:text-blue-400 hover:underline truncate bg-blue-50 dark:bg-blue-900/20 p-2 rounded">
                      <ExternalLink className="w-3 h-3 flex-shrink-0" />
                      <span className="truncate">{url}</span>
                    </a>
                  ))}
                  {result.referenceUrls.length > 2 && (
                    <p className="text-xs text-muted-foreground pl-2">+ {result.referenceUrls.length - 2} 更多链接</p>
                  )}
                </div>
              </div>
            ) : (
              <div className="text-xs text-muted-foreground italic p-2">未检测到参考链接</div>
            )}

            {/* Keywords */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs text-muted-foreground font-medium">关键词 (可编辑)</span>
                {onKeywordsChange && (
                  <span className="text-[10px] text-muted-foreground/60">点击 × 移除，输入回车添加</span>
                )}
              </div>
              
              <div className="flex flex-wrap gap-2">
                {result.extractedKeywords && result.extractedKeywords.map((k, i) => (
                  <Badge 
                    key={`${k}-${i}`} 
                    variant="secondary" 
                    className={cn(
                      "text-xs font-normal bg-secondary/50 hover:bg-secondary transition-colors pl-2.5",
                      onKeywordsChange && "pr-1 gap-1"
                    )}
                  >
                    {k}
                    {onKeywordsChange && (
                      <button
                        onClick={() => {
                          const newKeywords = [...(result.extractedKeywords || [])];
                          newKeywords.splice(i, 1);
                          onKeywordsChange(newKeywords);
                        }}
                        className="rounded-full p-0.5 hover:bg-background/50 text-muted-foreground hover:text-foreground transition-colors"
                      >
                        <X className="w-3 h-3" />
                        <span className="sr-only">移除 {k}</span>
                      </button>
                    )}
                  </Badge>
                ))}
                
                {onKeywordsChange && (
                  <div className="relative flex items-center">
                    <Input
                      className="h-6 w-24 text-xs px-2 py-0 rounded-full border-dashed border-muted-foreground/30 hover:border-muted-foreground/60 bg-transparent focus-visible:w-32 transition-all"
                      placeholder="+ 添加关键词"
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          const val = (e.target as HTMLInputElement).value.trim();
                          if (val && !result.extractedKeywords?.includes(val)) {
                            onKeywordsChange([...(result.extractedKeywords || []), val]);
                            (e.target as HTMLInputElement).value = '';
                          }
                        }
                      }}
                    />
                  </div>
                )}
              </div>
            </div>
          </div>
        </Card>
      </div>

      {/* 底部操作区 */}
      <div className="flex items-center gap-4 pt-4">
        <Button
          variant="ghost"
          size="lg"
          onClick={onModify}
          disabled={loading}
          className="text-muted-foreground hover:text-foreground"
        >
          <Edit2 className="w-4 h-4 mr-2" />
          修改意图
        </Button>
        <Button
          size="lg"
          onClick={onConfirm}
          disabled={loading}
          className={cn(
            "flex-1 shadow-lg transition-all duration-300 hover:scale-[1.02]",
            "bg-gradient-to-r from-primary to-primary/90 hover:to-primary text-primary-foreground",
            isHighConfidence ? "shadow-primary/25" : "shadow-muted/25"
          )}
        >
          {loading ? (
            <>
              <Sparkles className="w-4 h-4 mr-2 animate-spin" />
              处理中...
            </>
          ) : (
            <>
              <CheckCircle2 className="w-5 h-5 mr-2" />
              {nextActionText}
            </>
          )}
        </Button>
      </div>
    </MotionWrapper>
  );
}
