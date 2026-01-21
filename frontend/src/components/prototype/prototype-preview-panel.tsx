'use client';

import React, { useState, useRef, useEffect } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Eye, ArrowLeft, Check, Loader2, AlertCircle, Code2, Copy, CheckCircle, Sparkles, RefreshCw, Wrench } from 'lucide-react';
import { DesignStyle, getStyleDisplayInfo } from '@/types/design-style';
import type { IndustryTemplate } from '@/types/industry-template';
import { CodeFileTree, type FileNode } from './code-file-tree';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { useLanguage } from '@/contexts/LanguageContext';

// Phase 2: Sandbox生命周期管理
import { useSandboxHeartbeat } from '@/hooks/use-sandbox-heartbeat';
import { useSandboxCleanup } from '@/hooks/use-sandbox-cleanup';

/**
 * PrototypePreviewPanel组件属性接口
 */
export interface PrototypePreviewPanelProps {
  /** E2B沙箱预览URL */
  sandboxUrl: string | null;
  /** E2B Sandbox ID（用于心跳和清理） */
  sandboxId?: string | null;
  /** 生成失败的详细错误 */
  errorMessage?: string | null;
  /** 确认设计回调 */
  onConfirm: () => void;
  /** 返回上一步回调 */
  onBack: () => void;
  /** 重新生成回调（错误时使用） */
  onRetry?: () => void;
  /** 选中的模板（可选） */
  selectedTemplate?: IndustryTemplate | null;
  /** 选中的风格 */
  selectedStyle?: DesignStyle | null;
  /** 是否正在生成原型 */
  loading?: boolean;
  /** 代码是否正在生成中（沙箱已创建但代码未完成） */
  isGenerating?: boolean;
  /** 生成的文件列表（可选，用于代码显示） */
  files?: FileNode[];
  /** 流式传输中的代码（可选，用于实时显示） */
  streamedCode?: string;
  /** AI思考过程内容（可选） */
  thinking?: string;
  /** 刷新预览回调 */
  onRefresh?: () => Promise<void> | void;
  /** 已用时间（秒） */
  elapsedTime?: number;
  /** 自动修复回调 */
  onAutoFix?: () => Promise<void> | void;
}

/**
 * 获取代码语言类型
 */
function getLanguage(type: FileNode['type']): string {
  switch (type) {
    case 'react':
      return 'jsx';
    case 'typescript':
      return 'typescript';
    case 'javascript':
      return 'javascript';
    case 'css':
      return 'css';
    case 'json':
      return 'json';
    default:
      return 'text';
  }
}

/**
 * PrototypePreviewPanel - V2.0原型预览面板
 *
 * 功能：
 * - 展示AI生成的原型预览（iframe嵌入）
 * - 显示生成的代码文件树和语法高亮代码
 * - 支持预览和代码视图切换（标签页）
 * - 显示用户选择的模板和风格回顾
 * - 提供"确认设计"按钮触发后端代码生成
 * - 提供"返回"按钮重新选择风格
 * - 完整的加载状态和错误处理
 * - 完整的深色模式支持
 *
 * Phase 1增强版本：
 * - ✅ 代码文件树展示
 * - ✅ 语法高亮代码显示
 * - ✅ 复制代码功能
 *
 * Phase 2增强版本：
 * - ✅ Sandbox心跳机制（60秒）
 * - ✅ 自动清理机制（页面卸载）
 * - 暂不实现聊天式修改功能（Phase 10实现）
 *
 * @author Ingenio Team
 * @version 2.2.0
 * @since 2025-12-10
 */
export function PrototypePreviewPanel({
  sandboxUrl,
  sandboxId = null,
  errorMessage = null,
  onConfirm,
  onBack,
  onRetry,
  selectedTemplate,
  selectedStyle,
  loading = false,
  isGenerating = false,
  files = [],
  streamedCode = '',
  thinking = '',
  onRefresh,
  elapsedTime = 0,
  onAutoFix,
}: PrototypePreviewPanelProps): React.ReactElement {
  const { t } = useLanguage();
  // 获取选中风格的显示信息
  const selectedStyleInfo = selectedStyle ? getStyleDisplayInfo(selectedStyle) : null;

  // 选中的文件状态
  const [selectedFile, setSelectedFile] = useState<FileNode | null>(null);

  // 复制状态
  const [copied, setCopied] = useState(false);

  // 刷新状态
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [iframeKey, setIframeKey] = useState(0);

  // 自动修复状态
  const [isFixing, setIsFixing] = useState(false);

  // Phase 2: Sandbox心跳（60秒间隔）
  useSandboxHeartbeat({
    sandboxId,
    interval: 60000,
    enabled: !loading && !!sandboxId,
    onHeartbeatSuccess: () => {
      console.log('[原型预览] Sandbox心跳成功');
    },
    onHeartbeatError: (error) => {
      console.error('[原型预览] Sandbox心跳失败:', error);
    },
  });

  // Phase 2: Sandbox自动清理
  useSandboxCleanup({
    sandboxId,
    cleanupOnHide: false, // 仅在页面卸载时清理
    // 修复：在 loading/生成/刷新期间禁用清理，防止 apply 操作时 Sandbox 被意外杀死
    enabled: !!sandboxId && !loading && !isGenerating && !isRefreshing,
    onBeforeCleanup: () => {
      console.log('[原型预览] 准备清理Sandbox');
    },
    onCleanupComplete: () => {
      console.log('[原型预览] Sandbox清理完成');
    },
  });

  // 刷新预览
  const handleRefresh = async () => {
    if (isRefreshing) return;

    setIsRefreshing(true);
    try {
      if (onRefresh) {
        await onRefresh();
      }
      // 强制刷新iframe
      setIframeKey(prev => prev + 1);
    } catch (error) {
      console.error('刷新失败:', error);
    } finally {
      setIsRefreshing(false);
    }
  };

  // 自动修复
  const handleAutoFix = async () => {
    if (isFixing || !onAutoFix) return;

    setIsFixing(true);
    try {
      await onAutoFix();
      // 修复后自动刷新预览
      setIframeKey(prev => prev + 1);
    } catch (error) {
      console.error('自动修复失败:', error);
    } finally {
      setIsFixing(false);
    }
  };

  // 复制代码到剪贴板
  const handleCopyCode = async () => {
    if (!selectedFile) return;

    try {
      await navigator.clipboard.writeText(selectedFile.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy code:', err);
    }
  };

  // 自动选中第一个文件
  React.useEffect(() => {
    if (files.length > 0 && !selectedFile) {
      setSelectedFile(files[0]);
    }
  }, [files, selectedFile]);

  // 代码生成完成时自动刷新预览
  const prevIsGenerating = React.useRef(isGenerating);
  React.useEffect(() => {
    // 从生成中变为生成完成时，自动刷新预览
    if (prevIsGenerating.current && !isGenerating && sandboxUrl) {
      console.log('[原型预览] 代码生成完成，自动刷新预览');
      // 延迟1秒刷新，确保沙箱已完成热更新
      const timer = setTimeout(() => {
        setIframeKey(prev => prev + 1);
      }, 1000);
      return () => clearTimeout(timer);
    }
    prevIsGenerating.current = isGenerating;
  }, [isGenerating, sandboxUrl]);

  // 自动滚动思考过程到底部
  const thinkingRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (thinkingRef.current) {
      thinkingRef.current.scrollTop = thinkingRef.current.scrollHeight;
    }
  }, [thinking]);

  return (
    <div className="flex flex-col min-h-screen h-full">
      {/* 标题和选择回顾 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between mb-6 gap-4">
        <div className="flex items-center gap-3">
          <Eye className="h-6 w-6 text-blue-600 dark:text-blue-400" />
          <div>
            <h2 className="text-xl md:text-2xl font-bold text-gray-900 dark:text-gray-100">
              {t('ui.prototype_preview')}
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {loading
                ? t('ui.generating_prototype_preview')
                : t('ui.preview_design_confirm_hint')}
            </p>
          </div>
        </div>

        {/* 返回按钮 - 始终可点击，允许用户在超时时返回上一步 */}
        <Button
          onClick={onBack}
          variant="outline"
          className="w-full sm:w-auto"
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          {loading ? t('ui.cancel_generation') : t('ui.reselect_style')}
        </Button>
      </div>

      {/* 选择回顾卡片 */}
      <Card className="p-4 mb-6 border-2 bg-gradient-to-r from-blue-50 to-purple-50 dark:from-blue-900/10 dark:to-purple-900/10 border-blue-200 dark:border-blue-800">
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {t('ui.your_selection')}
          </span>

          {/* 模板徽章 */}
          {selectedTemplate && (
            <Badge className="bg-gradient-to-r from-green-600 to-teal-600 text-white border-0">
              📚 模板：{selectedTemplate.name}
            </Badge>
          )}

          {/* 风格徽章 */}
          {selectedStyleInfo && (
            <Badge className="bg-gradient-to-r from-purple-600 to-blue-600 text-white border-0">
              🎨 风格：{selectedStyleInfo.displayName} ({selectedStyleInfo.identifier})
            </Badge>
          )}

          {/* 无模板徽章 */}
          {!selectedTemplate && (
            <Badge variant="outline" className="border-gray-300 dark:border-gray-600">
              从零设计
            </Badge>
          )}
        </div>
      </Card>

      {/* 主要内容区域 - 原型预览或加载状态 */}
      <div className="flex-1 overflow-hidden mb-6 min-h-[600px] lg:min-h-[700px]">
        <Card className="h-full border-2 border-gray-200 dark:border-gray-700">
          {/* 加载状态 */}
          {loading && (
            <div className="flex flex-col items-center h-full bg-gray-50 dark:bg-gray-900 overflow-y-auto">
              <div className="w-full max-w-4xl flex flex-col items-center p-8 space-y-8 my-auto min-h-min">
                <div className="flex flex-col items-center text-center">
                  <Loader2 className="h-16 w-16 text-blue-600 dark:text-blue-400 animate-spin mb-6" />
                  <h3 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">
                    {t('ui.generating_prototype_preview')}
                  </h3>
                  <p className="text-sm text-gray-600 dark:text-gray-400 text-center max-w-md">
                    AI 正在根据您的选择生成可交互的应用原型，已用时 {elapsedTime} 秒...
                  </p>
                  <div className="mt-6 flex items-center gap-3">
                    <div className="h-2.5 w-2.5 rounded-full bg-blue-600 dark:bg-blue-400 animate-pulse"></div>
                    <div className="h-2.5 w-2.5 rounded-full bg-purple-600 dark:bg-purple-400 animate-pulse delay-75"></div>
                    <div className="h-2.5 w-2.5 rounded-full bg-green-600 dark:bg-green-400 animate-pulse delay-150"></div>
                  </div>
                </div>

                {/* AI思考过程 */}
                {thinking && (
                  <div className="w-full bg-gray-50 dark:bg-gray-800/50 rounded-lg p-6 text-left border border-gray-200 dark:border-gray-700 shadow-sm">
                    <div className="flex items-center gap-2 mb-4 text-purple-600 dark:text-purple-400 border-b border-gray-100 dark:border-gray-700/50 pb-2">
                      <Sparkles className="w-4 h-4" />
                      <span className="text-sm font-semibold">AI 深度思考中...</span>
                    </div>
                    <div
                      ref={thinkingRef}
                      className="text-xs text-gray-600 dark:text-gray-300 font-mono whitespace-pre-wrap leading-relaxed max-h-[500px] overflow-y-auto scroll-smooth pr-2"
                    >
                      {thinking}
                      <span className="inline-block w-1.5 h-3 bg-purple-600/50 dark:bg-purple-400/50 ml-1 animate-pulse" />
                    </div>
                  </div>
                )}

                {/* 流式代码预览 */}
                {streamedCode && (
                  <div className="w-full">
                    <div className="bg-gray-900 rounded-lg p-6 text-left shadow-lg border border-gray-700">
                      <div className="flex items-center justify-between mb-3">
                        <p className="text-xs text-gray-400">{t('ui.generating_code')}</p>
                        <Badge variant="outline" className="text-[10px] border-gray-700 text-gray-400">
                          Live Stream
                        </Badge>
                      </div>
                      <pre className="text-xs text-green-400 font-mono overflow-x-auto max-h-60 scrollbar-thin scrollbar-thumb-gray-700 scrollbar-track-transparent">
                        {streamedCode.slice(-1000)}
                      </pre>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Tabs: 预览 vs 代码 */}
          {!loading && sandboxUrl && (
            <Tabs defaultValue="preview" className="h-full flex flex-col">
              <div className="flex items-center justify-between px-4 mt-4">
                <TabsList>
                  <TabsTrigger value="preview" className="flex items-center gap-2">
                    <Eye className="h-4 w-4" />
                    预览
                  </TabsTrigger>
                  <TabsTrigger value="code" className="flex items-center gap-2">
                    <Code2 className="h-4 w-4" />
                    代码 {files.length > 0 && `(${files.length})`}
                  </TabsTrigger>
                </TabsList>

                {onRefresh && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleRefresh}
                    disabled={isRefreshing}
                    className="h-8 text-xs flex items-center gap-2"
                  >
                    <RefreshCw className={`h-3.5 w-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
                    {isRefreshing ? t('ui.refreshing') : t('ui.refresh_preview')}
                  </Button>
                )}
              </div>

              {/* 预览标签页 */}
              <TabsContent value="preview" className="flex-1 overflow-hidden m-4 mt-2 min-h-[500px]">
                <div className="w-full h-full min-h-[500px] border border-gray-300 dark:border-gray-600 rounded-lg overflow-hidden bg-white dark:bg-gray-800 relative group">
                  {/* 刷新中遮罩 */}
                  {isRefreshing && (
                    <div className="absolute inset-0 z-10 bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm flex flex-col items-center justify-center">
                      <Loader2 className="h-8 w-8 text-blue-600 animate-spin mb-2" />
                      <p className="text-sm font-medium">{t('ui.refreshing')}</p>
                    </div>
                  )}
                  {/* 代码生成中遮罩 - 当有沙箱URL但代码还在生成时显示 */}
                  {isGenerating && !isRefreshing && (
                    <div className="absolute inset-0 z-10 bg-white/90 dark:bg-gray-900/90 backdrop-blur-sm flex flex-col items-center justify-center">
                      <div className="flex flex-col items-center max-w-md text-center p-6">
                        <Loader2 className="h-12 w-12 text-blue-600 animate-spin mb-4" />
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">
                          {t('ui.code_generating')}
                        </h3>
                        <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
                          {t('ui.code_generating_desc')}
                        </p>
                        {/* 进度动画 */}
                        <div className="flex items-center gap-2">
                          <div className="h-2 w-2 rounded-full bg-blue-600 animate-pulse"></div>
                          <div className="h-2 w-2 rounded-full bg-purple-600 animate-pulse delay-75"></div>
                          <div className="h-2 w-2 rounded-full bg-green-600 animate-pulse delay-150"></div>
                        </div>
                        {/* 流式代码预览提示 */}
                        {streamedCode && (
                          <div className="mt-4 w-full bg-gray-800 rounded-lg p-3 text-left">
                            <p className="text-xs text-gray-400 mb-1">{t('ui.generating_code')}</p>
                            <pre className="text-xs text-green-400 font-mono overflow-x-auto max-h-20">
                              {streamedCode.slice(-200)}
                            </pre>
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                  <iframe
                    key={iframeKey}
                    src={iframeKey > 0 ? `${sandboxUrl}?t=${Date.now()}` : sandboxUrl}
                    className="w-full h-full min-h-[500px] border-0"
                    title={t('ui.prototype_preview')}
                    sandbox="allow-scripts allow-same-origin allow-forms allow-modals allow-popups allow-downloads"
                  />
                </div>
              </TabsContent>

              {/* 代码标签页 */}
              <TabsContent value="code" className="flex-1 overflow-hidden m-4 mt-2">
                <div className="flex h-full gap-4">
                  {/* 左侧：文件树 */}
                  <div className="w-72 flex-shrink-0 border-r border-gray-200 dark:border-gray-700 overflow-y-auto">
                    <CodeFileTree
                      files={files}
                      selectedPath={selectedFile?.path}
                      onFileSelect={setSelectedFile}
                    />
                  </div>

                  {/* 右侧：代码显示 */}
                  <div className="flex-1 flex flex-col overflow-hidden">
                    {selectedFile ? (
                      <>
                        {/* 文件头部 */}
                        <div className="flex items-center justify-between mb-2 pb-2 border-b border-gray-200 dark:border-gray-700">
                          <div className="flex items-center gap-2">
                            <Code2 className="h-4 w-4 text-gray-500" />
                            <span className="text-sm font-mono text-gray-700 dark:text-gray-300">
                              {selectedFile.path}
                            </span>
                          </div>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={handleCopyCode}
                            className="h-8"
                          >
                            {copied ? (
                              <>
                                <CheckCircle className="h-4 w-4 mr-2 text-green-600" />
                                已复制
                              </>
                            ) : (
                              <>
                                <Copy className="h-4 w-4 mr-2" />
                                复制代码
                              </>
                            )}
                          </Button>
                        </div>

                        {/* 代码高亮显示 */}
                        <div className="flex-1 overflow-auto rounded-lg border border-gray-200 dark:border-gray-700">
                          <SyntaxHighlighter
                            language={getLanguage(selectedFile.type)}
                            style={vscDarkPlus}
                            showLineNumbers
                            customStyle={{
                              margin: 0,
                              borderRadius: '0.5rem',
                              fontSize: '0.875rem',
                              height: '100%',
                            }}
                          >
                            {selectedFile.content}
                          </SyntaxHighlighter>
                        </div>
                      </>
                    ) : (
                      <div className="flex items-center justify-center h-full text-gray-500 dark:text-gray-400">
                        <p className="text-sm">请从左侧选择一个文件查看代码</p>
                      </div>
                    )}
                  </div>
                </div>
              </TabsContent>
            </Tabs>
          )}

          {/* 空状态（无URL且不在加载） */}
          {!loading && !sandboxUrl && (
            <div className="flex flex-col items-center justify-center h-full bg-gray-50 dark:bg-gray-900 p-8">
              <AlertCircle className="h-16 w-16 text-yellow-600 dark:text-yellow-400 mb-4" />
              <h3 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">
                {t('ui.prototype_generation_failed')}
              </h3>
              <p className="text-sm text-gray-600 dark:text-gray-400 text-center max-w-md mb-6">
                {errorMessage || t('ui.cannot_generate_prototype')}
              </p>
              {/* 操作按钮 */}
              <div className="flex gap-3">
                {onAutoFix && (
                  <Button
                    onClick={handleAutoFix}
                    disabled={isFixing}
                    variant="default"
                    size="lg"
                    className="bg-orange-600 hover:bg-orange-700 text-white"
                  >
                    {isFixing ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        修复中...
                      </>
                    ) : (
                      <>
                        <Wrench className="mr-2 h-4 w-4" />
                        自动修复代码
                      </>
                    )}
                  </Button>
                )}
                {onRetry && (
                  <Button
                    onClick={onRetry}
                    variant="default"
                    size="lg"
                    className="bg-primary hover:bg-primary/90"
                  >
                    <Loader2 className="mr-2 h-4 w-4" />
                    重新生成
                  </Button>
                )}
                <Button
                  onClick={onBack}
                  variant="outline"
                  size="lg"
                >
                  <ArrowLeft className="mr-2 h-4 w-4" />
                  {t('ui.go_back_reselect')}
                </Button>
              </div>
              <p className="text-xs text-gray-500 dark:text-gray-500 mt-6 text-center max-w-md">
                {t('ui.service_not_started_hint')}
              </p>
            </div>
          )}
        </Card>
      </div>

      {/* 确认警告提示 */}
      {!loading && sandboxUrl && (
        <Card className="p-4 mb-6 border-2 bg-green-50 dark:bg-green-900/10 border-green-200 dark:border-green-800">
          <div className="flex items-start gap-3">
            <AlertCircle className="h-5 w-5 text-green-600 dark:text-green-400 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <h4 className="text-sm font-semibold text-green-900 dark:text-green-100 mb-1">
                {t('ui.check_before_confirm')}
              </h4>
              <p className="text-sm text-green-800 dark:text-green-200 leading-relaxed">
                {t('ui.confirm_design_hint')}
                {t('ui.generation_time_hint')}
              </p>
            </div>
          </div>
        </Card>
      )}

      {/* 底部操作按钮 */}
      <div className="flex flex-col sm:flex-row gap-4 pt-4 border-t-2 border-gray-200 dark:border-gray-700">
        {/* 确认按钮 */}
        <Button
          onClick={onConfirm}
          disabled={loading || !sandboxUrl}
          className="flex-1 bg-gradient-to-r from-green-600 to-teal-600 hover:from-green-700 hover:to-teal-700 text-white text-lg py-6"
        >
          <Check className="h-5 w-5 mr-2" />
          {loading ? '生成中...' : '✅ 确认设计，开始生成后端代码'}
        </Button>
      </div>

      {/* 提示信息 */}
      {!loading && sandboxUrl && (
        <p className="text-xs text-center text-gray-500 dark:text-gray-400 mt-4">
          💡 提示：确认后将进入Execute Agent阶段，生成完整的Spring Boot后端 + PostgreSQL数据库 + 多端代码
        </p>
      )}
    </div>
  );
}
