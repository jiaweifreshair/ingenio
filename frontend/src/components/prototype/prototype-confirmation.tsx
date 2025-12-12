/**
 * PrototypeConfirmation - V2.0原型预览确认组件（深度融合版）
 *
 * 功能：
 * - 集成OpenLovable快速预览（5-10秒生成）
 * - 支持聊天式迭代修改
 * - 实时代码显示和预览切换
 * - 展示选择摘要（意图类型、选择的模板、设计风格）
 * - 全屏预览模式支持
 *
 * V2.0核心：用户必须确认设计后才能进入Execute阶段
 *
 * @author Ingenio Team
 * @version 2.0.1 (深度融合版)
 * @since 2025-11-16
 */
'use client';

import React, { useState, useRef, useEffect } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Loader2,
  ArrowLeft,
  CheckCircle2,
  ExternalLink,
  Maximize2,
  Minimize2,
  RefreshCw,
  AlertCircle,
  Eye,
  Sparkles,
  FileText,
  Palette,
  Code,
  Send,
  FileCode,
  Check,
} from 'lucide-react';

import type { PlanRoutingResult } from '@/lib/api/plan-routing';
import { ROUTING_BRANCH_DISPLAY } from '@/lib/api/plan-routing';
import type { Template } from '@/types/template';
import { getStyleDisplayInfo } from '@/types/design-style';
import { useOpenLovablePreview, type GeneratedFile } from '@/hooks/use-openlovable-preview';
import { cn } from '@/lib/utils';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { PrototypePreview } from './prototype-preview';
import { ConfirmationDialog } from './confirmation-dialog';

// ==================== 类型定义 ====================

/**
 * PrototypeConfirmation组件属性接口
 */
export interface PrototypeConfirmationProps {
  /** 路由结果（包含原型URL等信息） */
  routingResult: PlanRoutingResult;
  /** 用户需求（用于OpenLovable生成） */
  userRequirement?: string;
  /** 选择的模板（可选） */
  selectedTemplate?: Template | null;
  /** 确认设计回调 */
  onConfirm: () => void;
  /** 返回修改回调 */
  onBack: () => void;
  /** 是否正在加载 */
  loading?: boolean;
  /** 错误信息 */
  error?: string | null;
}

/**
 * 视图模式
 */
type ViewMode = 'preview' | 'code' | 'log';

/**
 * 获取语法高亮语言
 */
function getSyntaxLanguage(type: string): string {
  const langMap: Record<string, string> = {
    'javascript': 'jsx',
    'typescript': 'tsx',
    'css': 'css',
    'scss': 'scss',
    'html': 'html',
    'json': 'json',
    'markdown': 'markdown',
    'text': 'text',
  };
  return langMap[type] || 'jsx';
}

// ==================== 主组件 ====================

/**
 * PrototypeConfirmation - 原型预览确认组件（深度融合版）
 */
export function PrototypeConfirmation({
  routingResult,
  userRequirement = '',
  selectedTemplate,
  onConfirm,
  onBack,
  loading = false,
  error: externalError = null,
}: PrototypeConfirmationProps): React.ReactElement {
  // OpenLovable Hook
  const {
    stage,
    previewUrl,
    generatedFiles,
    currentFile,
    generationLog,
    elapsedTime,
    totalTime,
    error: openLovableError,
    isReloading,
    startGeneration,
    sendIterationMessage,
    reloadPreview,
  } = useOpenLovablePreview();

  // 状态管理
  const [viewMode, setViewMode] = useState<ViewMode>('preview');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [iterationInput, setIterationInput] = useState('');
  const [iframeKey, setIframeKey] = useState(0);
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);

  // Refs
  const logContainerRef = useRef<HTMLDivElement>(null);
  const hasStartedRef = useRef(false);

  // 获取路由分支显示信息
  const branchInfo = ROUTING_BRANCH_DISPLAY[routingResult.branch];

  // 获取选中风格的显示信息
  const selectedStyleInfo = routingResult.selectedStyleId
    ? getStyleDisplayInfo(routingResult.selectedStyleId as import('@/types/design-style').DesignStyle)
    : null;

  // 组合错误信息
  const error = externalError || openLovableError;

  // 使用OpenLovable预览URL或后端提供的URL
  const effectivePreviewUrl = previewUrl || routingResult.prototypeUrl || null;

  // 是否可以确认（原型已生成且无错误）
  const canConfirm = (stage === 'complete' || routingResult.prototypeGenerated) && !error;

  // 是否正在生成
  const isGenerating = stage === 'sandbox' || stage === 'generating';

  // ========== 副作用 ==========

  /**
   * 自动启动OpenLovable生成（如果后端没有提供prototypeUrl）
   */
  useEffect(() => {
    if (!hasStartedRef.current && !routingResult.prototypeUrl && userRequirement && stage === 'idle') {
      hasStartedRef.current = true;
      const styleHint = selectedStyleInfo?.displayName || '';
      startGeneration(userRequirement, styleHint);
    }
  }, [routingResult.prototypeUrl, userRequirement, stage, selectedStyleInfo, startGeneration]);

  /**
   * 自动滚动日志到底部
   */
  useEffect(() => {
    if (logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight;
    }
  }, [generationLog]);

  /**
   * 生成完成后刷新iframe
   */
  useEffect(() => {
    if (stage === 'complete' && previewUrl) {
      // 等待Vite热更新
      const timer = setTimeout(() => {
        setIframeKey(prev => prev + 1);
      }, 5000); // 增加延迟，确保Vite完成热更新
      return () => clearTimeout(timer);
    }
  }, [stage, previewUrl]);

  // ========== 事件处理 ==========

  /**
   * 发送迭代修改请求
   */
  const handleSendIteration = async () => {
    if (!iterationInput.trim()) return;

    const message = iterationInput.trim();
    setIterationInput('');
    await sendIterationMessage(message);

    // 迭代完成后刷新预览
    if (previewUrl) {
      setTimeout(() => {
        setIframeKey(prev => prev + 1);
      }, 2000);
    }
  };

  /**
   * 刷新预览
   */
  const handleRefresh = async () => {
    await reloadPreview();
    // 刷新iframe
    setTimeout(() => {
      setIframeKey(prev => prev + 1);
    }, 3000);
  };

  /**
   * 切换全屏模式
   */
  const toggleFullscreen = () => {
    setIsFullscreen(prev => !prev);
  };

  /**
   * 获取当前显示的文件
   */
  const displayFile: GeneratedFile | null = selectedFile
    ? generatedFiles.find(f => f.path === selectedFile) || currentFile
    : currentFile || (generatedFiles.length > 0 ? generatedFiles[0] : null);

  // ========== 渲染 ==========

  return (
    <div className={`flex flex-col ${isFullscreen ? 'fixed inset-0 z-50 bg-white dark:bg-gray-900 p-4' : 'h-full'}`} data-testid="prototype-confirmation-panel">
      <ConfirmationDialog
        isOpen={showConfirmDialog}
        onClose={() => setShowConfirmDialog(false)}
        onConfirm={onConfirm}
        loading={loading}
      />

      {/* 标题区域 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-4 gap-3">
        <div className="flex items-center gap-3">
          <Eye className="h-6 w-6 text-purple-600 dark:text-purple-400" />
          <div>
            <h2 className="text-xl md:text-2xl font-bold text-gray-900 dark:text-gray-100">
              确认设计方案
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {isGenerating
                ? `AI正在生成原型... ${elapsedTime}s`
                : totalTime
                  ? `生成完成 (${totalTime}s)，可进行迭代修改`
                  : '预览原型效果，确认后将进入全栈代码生成'}
            </p>
          </div>
        </div>

        {/* 控制按钮 */}
        <div className="flex items-center gap-2">
          {/* 状态标签 */}
          <Badge
            className={cn(
              'px-3 py-1',
              stage === 'complete' && 'bg-green-100 text-green-700',
              isGenerating && 'bg-purple-100 text-purple-700',
              stage === 'error' && 'bg-red-100 text-red-700',
              stage === 'idle' && 'bg-gray-100 text-gray-700',
            )}
          >
            {isGenerating && <Loader2 className="h-3 w-3 mr-1 animate-spin" />}
            {stage === 'complete' && <CheckCircle2 className="h-3 w-3 mr-1" />}
            {stage === 'error' && <AlertCircle className="h-3 w-3 mr-1" />}
            {stage === 'sandbox' && `创建沙箱 ${elapsedTime}s`}
            {stage === 'generating' && `生成中 ${elapsedTime}s`}
            {stage === 'complete' && `已完成 ${totalTime}s`}
            {stage === 'error' && '失败'}
            {stage === 'idle' && '等待中'}
          </Badge>

          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={loading || isReloading || !effectivePreviewUrl}
            data-testid="refresh-preview-button"
          >
            <RefreshCw className={cn('h-4 w-4 mr-1', isReloading && 'animate-spin')} />
            刷新
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={toggleFullscreen}
            disabled={!effectivePreviewUrl}
          >
            {isFullscreen ? (
              <>
                <Minimize2 className="h-4 w-4 mr-1" />
                退出
              </>
            ) : (
              <>
                <Maximize2 className="h-4 w-4 mr-1" />
                全屏
              </>
            )}
          </Button>
          {effectivePreviewUrl && (
            <Button variant="outline" size="sm" asChild>
              <a href={effectivePreviewUrl} target="_blank" rel="noopener noreferrer">
                <ExternalLink className="h-4 w-4 mr-1" />
                新窗口
              </a>
            </Button>
          )}
        </div>
      </div>

      {/* 错误提示 */}
      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* 主要内容区域 */}
      <div className="flex-1 flex flex-col lg:flex-row gap-4 min-h-0">
        {/* 左侧：预览/代码切换区域 - 增加宽度占比 (75%) */}
        <div className={`${isFullscreen ? 'flex-1' : 'lg:flex-[3]'} min-h-[400px]`}>
          <Card className="h-full flex flex-col border-2">
            {/* 视图切换标签 */}
            <div className="flex items-center justify-between p-3 border-b">
              <Tabs value={viewMode} onValueChange={(v) => setViewMode(v as ViewMode)}>
                <TabsList className="h-8">
                  <TabsTrigger value="preview" className="text-xs px-3">
                    <Eye className="w-3 h-3 mr-1" />
                    预览
                  </TabsTrigger>
                  <TabsTrigger value="code" className="text-xs px-3">
                    <Code className="w-3 h-3 mr-1" />
                    代码
                  </TabsTrigger>
                  <TabsTrigger value="log" className="text-xs px-3">
                    <FileText className="w-3 h-3 mr-1" />
                    日志
                  </TabsTrigger>
                </TabsList>
              </Tabs>

              {viewMode === 'code' && generatedFiles.length > 0 && (
                <span className="text-xs text-muted-foreground">
                  {generatedFiles.length} 个文件
                </span>
              )}
            </div>

            {/* 内容区 */}
            <div className="flex-1 p-0 overflow-hidden">
              {viewMode === 'preview' ? (
                // 预览模式
                <PrototypePreview
                  previewUrl={effectivePreviewUrl}
                  isGenerating={isGenerating}
                  elapsedTime={elapsedTime}
                  stage={stage}
                  iframeKey={iframeKey}
                />
              ) : viewMode === 'code' ? (
                // 代码模式
                <div className="h-full flex">
                  {/* 文件列表 */}
                  <div className="w-48 bg-gray-900 border-r border-gray-700 overflow-y-auto">
                    <div className="p-2 text-xs text-gray-400 font-medium border-b border-gray-700">
                      文件列表 ({generatedFiles.length})
                    </div>
                    {generatedFiles.map((file) => (
                      <button
                        key={file.path}
                        onClick={() => setSelectedFile(file.path)}
                        className={cn(
                          'w-full px-3 py-2 text-left text-xs font-mono flex items-center gap-2 hover:bg-gray-800 transition-colors',
                          selectedFile === file.path ? 'bg-gray-800 text-white' : 'text-gray-400'
                        )}
                      >
                        <FileCode className="w-3 h-3 flex-shrink-0" />
                        <span className="truncate">{file.path}</span>
                        {file.completed && <Check className="w-3 h-3 text-green-500 flex-shrink-0" />}
                      </button>
                    ))}
                    {currentFile && (
                      <button
                        onClick={() => setSelectedFile(currentFile.path)}
                        className={cn(
                          'w-full px-3 py-2 text-left text-xs font-mono flex items-center gap-2 hover:bg-gray-800 transition-colors',
                          selectedFile === currentFile.path ? 'bg-gray-800 text-white' : 'text-gray-400'
                        )}
                      >
                        <Loader2 className="w-3 h-3 animate-spin flex-shrink-0" />
                        <span className="truncate">{currentFile.path}</span>
                      </button>
                    )}
                    {generatedFiles.length === 0 && !currentFile && (
                      <div className="p-3 text-xs text-gray-500 text-center">
                        {isGenerating ? '等待代码生成...' : '暂无文件'}
                      </div>
                    )}
                  </div>

                  {/* 代码显示 */}
                  <div className="flex-1 overflow-hidden bg-gray-900">
                    {displayFile ? (
                      <div className="h-full flex flex-col">
                        <div className="px-4 py-2 bg-[#36322F] text-white flex items-center justify-between border-b border-gray-700">
                          <div className="flex items-center gap-2">
                            {!displayFile.completed && (
                              <Loader2 className="w-4 h-4 animate-spin text-orange-400" />
                            )}
                            {displayFile.completed && (
                              <Check className="w-4 h-4 text-green-500" />
                            )}
                            <span className="font-mono text-sm">{displayFile.path}</span>
                          </div>
                        </div>
                        <div className="flex-1 overflow-auto">
                          <SyntaxHighlighter
                            language={getSyntaxLanguage(displayFile.type)}
                            style={vscDarkPlus}
                            customStyle={{
                              margin: 0,
                              padding: '1rem',
                              fontSize: '0.75rem',
                              background: 'transparent',
                              minHeight: '100%',
                            }}
                            showLineNumbers={true}
                            wrapLongLines={true}
                          >
                            {displayFile.content || '// 等待代码生成...'}
                          </SyntaxHighlighter>
                        </div>
                      </div>
                    ) : (
                      <div className="flex items-center justify-center h-full text-gray-500">
                        <div className="text-center">
                          <Code className="w-8 h-8 mx-auto mb-2 opacity-50" />
                          <p className="text-sm">选择文件查看代码</p>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              ) : (
                // 日志模式
                <ScrollArea className="h-full p-4" ref={logContainerRef}>
                  <div className="space-y-1 font-mono text-xs">
                    {generationLog.length > 0 ? (
                      generationLog.map((log, index) => (
                        <div key={index} className="text-gray-600 dark:text-gray-400">
                          {log}
                        </div>
                      ))
                    ) : (
                      <div className="text-gray-400 text-center py-8">
                        暂无日志
                      </div>
                    )}
                  </div>
                </ScrollArea>
              )}
            </div>

            {/* 迭代输入框（生成完成后显示） */}
            {stage === 'complete' && (
              <div className="p-3 border-t bg-gray-50 dark:bg-gray-800/50">
                <div className="flex gap-2">
                  <Input
                    value={iterationInput}
                    onChange={(e) => setIterationInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        handleSendIteration();
                      }
                    }}
                    placeholder="输入修改需求，例如：把标题改成蓝色、增加一个按钮..."
                    className="flex-1"
                    disabled={loading}
                  />
                  <Button
                    onClick={handleSendIteration}
                    disabled={!iterationInput.trim() || loading}
                    size="sm"
                  >
                    <Send className="w-4 h-4" />
                  </Button>
                </div>
                <p className="text-xs text-muted-foreground mt-2">
                  按Enter发送迭代请求，AI将实时修改原型
                </p>
              </div>
            )}
          </Card>
        </div>

        {/* 右侧：选择摘要（全屏模式下隐藏） */}
        {!isFullscreen && (
          <div className="lg:flex-1 space-y-4">
            {/* 意图类型 */}
            <Card className="p-4 border-2">
              <div className="flex items-start gap-3">
                <Sparkles className="h-5 w-5 text-purple-600 flex-shrink-0 mt-0.5" />
                <div className="flex-1">
                  <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-1">
                    意图类型
                  </h4>
                  <div className="flex items-center gap-2">
                    <span className="text-2xl">{branchInfo.icon}</span>
                    <div>
                      <p className="font-medium">{branchInfo.name}</p>
                      <p className="text-xs text-muted-foreground">{branchInfo.description}</p>
                    </div>
                  </div>
                </div>
              </div>
            </Card>

            {/* 选择的模板 */}
            <Card className="p-4 border-2">
              <div className="flex items-start gap-3">
                <FileText className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
                <div className="flex-1">
                  <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-1">
                    选择的模板
                  </h4>
                  {selectedTemplate ? (
                    <div>
                      <p className="font-medium">{selectedTemplate.name}</p>
                      <p className="text-xs text-muted-foreground">{selectedTemplate.description}</p>
                    </div>
                  ) : (
                    <p className="text-muted-foreground text-sm">未选择模板（从零设计）</p>
                  )}
                </div>
              </div>
            </Card>

            {/* 设计风格 */}
            <Card className="p-4 border-2">
              <div className="flex items-start gap-3">
                <Palette className="h-5 w-5 text-pink-600 flex-shrink-0 mt-0.5" />
                <div className="flex-1">
                  <h4 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-1">
                    设计风格
                  </h4>
                  {selectedStyleInfo ? (
                    <div className="flex items-center gap-2">
                      <Badge className="bg-gradient-to-r from-purple-600 to-blue-600 text-white border-0">
                        {selectedStyleInfo.identifier}
                      </Badge>
                      <span className="font-medium">{selectedStyleInfo.displayName}</span>
                    </div>
                  ) : (
                    <p className="text-muted-foreground text-sm">
                      {routingResult.selectedStyleId || '未选择风格'}
                    </p>
                  )}
                </div>
              </div>
            </Card>

            {/* V2.0确认提示 - 保留作为辅助提示，详细信息在Dialog中 */}
            <Alert className="border-purple-200 bg-purple-50 dark:bg-purple-900/10">
              <CheckCircle2 className="h-4 w-4 text-purple-600" />
              <AlertDescription className="text-purple-800 dark:text-purple-200">
                <strong>V2.0设计确认机制：</strong>
                确认设计后，系统将基于此方案生成完整的全栈代码。
              </AlertDescription>
            </Alert>
          </div>
        )}
      </div>

      {/* 底部操作按钮 */}
      <div className="flex flex-col sm:flex-row gap-3 mt-4 pt-4 border-t-2 border-gray-200 dark:border-gray-700">
        <Button
          variant="outline"
          onClick={onBack}
          disabled={loading}
          className="w-full sm:flex-1"
          data-testid="back-to-style-button"
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回修改
        </Button>
        <Button
          onClick={() => setShowConfirmDialog(true)}
          disabled={loading || !canConfirm}
          className="w-full sm:flex-1 bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 text-white"
          data-testid="confirm-design-button"
        >
          {loading ? (
            <>
              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
              确认中...
            </>
          ) : (
            <>
              <CheckCircle2 className="h-4 w-4 mr-2" />
              确认设计，开始生成
            </>
          )}
        </Button>
      </div>

      {/* 底部提示 */}
      {!canConfirm && !loading && (
        <div className="mt-3 p-3 rounded-lg bg-amber-50 dark:bg-amber-900/10 border border-amber-200 dark:border-amber-800">
          <p className="text-xs text-amber-700 dark:text-amber-300 text-center">
            {isGenerating
              ? `正在生成原型... ${elapsedTime}s`
              : stage === 'error'
                ? '生成失败，请重试'
                : '请等待原型生成完成后再确认设计'}
          </p>
        </div>
      )}
    </div>
  );
}

export default PrototypeConfirmation;
