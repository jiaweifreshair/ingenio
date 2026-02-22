/**
 * PrototypeConfirmation - V2.0原型预览确认组件（深度融合版 - 修复优化）
 *
 * 优化内容：
 * - 采用左右分栏布局（左侧预览/代码，右侧交互/历史）
 * - 集成 InteractionPanel 组件（聊天式交互）
 * - 集成 RealtimeCodeViewer 组件（代码查看）
 * - 集成 ResizablePanels 组件（可拖拽调整）
 * - 优化移动端适配
 *
 * @author Ingenio Team
 * @version 2.0.2
 * @since 2025-12-15
 */
'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import {
  Loader2,
  ArrowLeft,
  CheckCircle2,
  ExternalLink,
  Maximize2,
  Minimize2,
  RefreshCw,
  Eye,
  Code,
  LayoutTemplate,
  Copy,
} from 'lucide-react';

import type { PlanRoutingResult } from '@/lib/api/plan-routing';
import { updatePrototypeStatus } from '@/lib/api/plan-routing';
import type { Template } from '@/types/template';
import type { G3LogEntry } from '@/types/g3';
import { isValidUrl, type SandboxInfo } from '@/lib/openlovable/sandbox-lifecycle';
import { useOpenLovablePreview } from '@/hooks/use-openlovable-preview';
import { cn } from '@/lib/utils';
import { PrototypePreview } from './prototype-preview';
import { ConfirmationDialog } from './confirmation-dialog';
import { InteractionPanel, type ChatHistoryItem } from './interaction-panel';
import { RealtimeCodeViewer } from '@/components/code-generation/realtime-code-viewer';
import { ResizablePanels } from '@/components/ui/resizable-panels';
import { useToast } from '@/hooks/use-toast';
import { useSandboxHeartbeat } from '@/hooks/use-sandbox-heartbeat';
import { preserveSandboxCleanup, useSandboxCleanup } from '@/hooks/use-sandbox-cleanup';
import { PaywallGuard } from '@/components/billing/paywall-guard';
import { useLanguage } from '@/contexts/LanguageContext';
import { smartFixPrototypeSandbox } from '@/lib/api/prototype';

// ==================== 类型定义 ====================

export interface PrototypeConfirmationProps {
  routingResult: PlanRoutingResult;
  userRequirement?: string;
  selectedTemplate?: Template | null;
  onConfirm: () => void;
  onBack: () => void;
  loading?: boolean;
  error?: string | null;
  g3Logs?: G3LogEntry[];
  /**
   * 聊天历史记录（可选）
   *
   * 用途：
   * - 与外层向导共享历史记录，保证跨页面一致性
   */
  chatHistory?: ChatHistoryItem[];
  /**
   * 聊天历史更新回调（可选）
   *
   * 用途：
   * - 将原型阶段的修改写回到外层状态
   */
  onChatHistoryChange?: React.Dispatch<React.SetStateAction<ChatHistoryItem[]>>;
  /**
   * 当前正在处理的历史记录ID（可选）
   *
   * 用途：
   * - 让外层统一展示“处理中”状态
   */
  activeHistoryId?: string | null;
  /**
   * 更新当前处理中的历史记录ID（可选）
   *
   * 用途：
   * - 与外层状态保持同步
   */
  onActiveHistoryIdChange?: React.Dispatch<React.SetStateAction<string | null>>;
  /**
   * 原型确认阶段的 Chat 修改回调（可选）
   *
   * 用途：
   * - 用户在原型确认前通过聊天不断补充/修正需求
   * - 将修改同步到外层向导上下文与后端 AppSpec，保证后续 G3 生成读取到最新需求
   */
  onChatModify?: (message: string) => void | Promise<void>;
}

type ViewMode = 'preview' | 'code';

/**
 * 生成历史记录ID（避免与后端ID冲突，确保前端可追踪）
 */
const buildHistoryId = (prefix: 'requirement' | 'iteration') =>
  `history-${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

// ==================== 主组件 ====================

export function PrototypeConfirmation({
  routingResult,
  userRequirement = '',
  onConfirm,
  onBack,
  loading = false,
  error: externalError = null,
  g3Logs = [],
  chatHistory,
  onChatHistoryChange,
  activeHistoryId,
  onActiveHistoryIdChange,
  onChatModify,
}: PrototypeConfirmationProps): React.ReactElement {
  const { t } = useLanguage();
  const { toast } = useToast();
  
  // CLONE 分支兼容：后端可能直接返回 prototypeUrl，但前端仍需要 sandboxId 才能执行“刷新预览/心跳保活”等操作
  const initialSandboxInfo = useMemo<SandboxInfo | null>(() => {
    if (!routingResult.prototypeUrl) return null;
    try {
      const url = new URL(routingResult.prototypeUrl);
      const hostname = url.hostname || '';

      // 兼容 e2b URL：`https://5173-<sandboxId>.e2b.app` / `https://<sandboxId>.e2b.app`
      const firstLabel = hostname.split('.')[0] || '';
      const candidate = (firstLabel.split('-').pop() || '').trim();
      if (!candidate || !/^[a-z0-9]+$/i.test(candidate)) return null;

      return {
        success: true,
        sandboxId: candidate,
        url: routingResult.prototypeUrl,
        provider: 'e2b',
        message: '',
        createdAt: Date.now(),
      };
    } catch {
      return null;
    }
  }, [routingResult.prototypeUrl]);

  // OpenLovable Hook
  const {
    stage,
    sandboxInfo,  // V2.0新增：用于同步原型状态到后端
    previewUrl,
    generatedFiles,
    currentFile,
    streamedCode,
    generationLog,
    elapsedTime,
    totalTime,
    error: openLovableError,
    isReloading,
    startGeneration,
    sendIterationMessage,
    reloadPreview,
  } = useOpenLovablePreview(initialSandboxInfo);

  // 状态管理
  const [viewMode, setViewMode] = useState<ViewMode>('preview');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [iframeKey, setIframeKey] = useState(0);
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const [isSmartFixing, setIsSmartFixing] = useState(false);
  const hasStartedRef = React.useRef(false);
  const historySeededRef = React.useRef(false);
  const [internalChatHistory, setInternalChatHistory] = useState<ChatHistoryItem[]>([]);
  const [internalActiveHistoryId, setInternalActiveHistoryId] = useState<string | null>(null);
  const effectiveChatHistory = chatHistory ?? internalChatHistory;
  const setChatHistory = onChatHistoryChange ?? setInternalChatHistory;
  const effectiveActiveHistoryId = typeof activeHistoryId === 'undefined'
    ? internalActiveHistoryId
    : activeHistoryId;
  const setActiveHistoryId = onActiveHistoryIdChange ?? setInternalActiveHistoryId;

  // 组合错误信息
  const error = externalError || openLovableError;
  const effectivePreviewUrl =
    previewUrl ||
    (isValidUrl(routingResult.prototypeUrl) ? (routingResult.prototypeUrl ?? null) : null);
  const invalidPreviewUrl =
    (sandboxInfo?.url && !isValidUrl(sandboxInfo.url) ? sandboxInfo.url : null) ||
    (routingResult.prototypeUrl && !isValidUrl(routingResult.prototypeUrl) ? routingResult.prototypeUrl : null);
  const canConfirm =
    !error &&
    ((stage === 'complete' && !!effectivePreviewUrl) ||
      (routingResult.prototypeGenerated && isValidUrl(routingResult.prototypeUrl)));
  const isGenerating = stage === 'sandbox' || stage === 'generating';
  const currentSandboxId = sandboxInfo?.sandboxId || null;

  /**
   * 自动自愈最小间隔（毫秒）
   * 用途：避免心跳失败时重复触发刷新导致抖动与资源浪费
   */
  const AUTO_RECOVER_COOLDOWN_MS = 60_000;

  /**
   * 自动自愈节流器
   * 作用：记录是否正在自愈与上次触发时间，防止并发重启
   */
  const autoRecoverRef = React.useRef({ inFlight: false, lastAt: 0 });

  /**
   * 确认设计前的沙箱保留处理
   *
   * 是什么：在确认设计跳转前标记当前沙箱可暂时保留。
   * 做什么：避免组件卸载时自动清理，确保下载链路可用。
   * 为什么：下载前端代码依赖沙箱内容，过早清理会导致下载失败。
   */
  const handleConfirmDesign = React.useCallback(async () => {
    if (currentSandboxId) {
      preserveSandboxCleanup(currentSandboxId);
    }

    // 设计分支兜底：在确认设计前确保原型状态已写回后端（否则 confirmDesign 可能失败）
    if (
      routingResult.appSpecId &&
      !routingResult.prototypeGenerated &&
      stage === 'complete' &&
      sandboxInfo?.sandboxId &&
      sandboxInfo?.url &&
      isValidUrl(sandboxInfo.url)
    ) {
      try {
        await updatePrototypeStatus(routingResult.appSpecId.toString(), {
          sandboxId: sandboxInfo.sandboxId,
          previewUrl: sandboxInfo.url,
          provider: sandboxInfo.provider || 'e2b',
        });
      } catch (e) {
        const msg = e instanceof Error ? e.message : '原型状态同步失败';
        toast({
          title: '无法确认设计',
          description: msg,
          variant: 'destructive',
        });
        return;
      }
    }

    await onConfirm();
  }, [
    currentSandboxId,
    onConfirm,
    routingResult.appSpecId,
    routingResult.prototypeGenerated,
    sandboxInfo,
    stage,
    toast,
  ]);

  /**
   * 心跳异常处理
   * 目标：在沙箱被回收/失活时触发自愈刷新，减少人工干预
   */
  const handleHeartbeatError = React.useCallback(async (heartbeatError: Error) => {
    console.warn('[PrototypeConfirmation] Sandbox心跳失败:', heartbeatError);

    // 后端不可用时不触发自愈，避免无效重试
    if (heartbeatError.message.includes('Failed to fetch')) {
      return;
    }

    if (!currentSandboxId || isGenerating || isReloading) {
      return;
    }

    const now = Date.now();
    if (autoRecoverRef.current.inFlight || now - autoRecoverRef.current.lastAt < AUTO_RECOVER_COOLDOWN_MS) {
      return;
    }

    autoRecoverRef.current.inFlight = true;
    autoRecoverRef.current.lastAt = now;

    toast({
      title: '预览自愈中',
      description: '检测到沙箱异常，正在尝试重启预览',
    });

    try {
      await reloadPreview();
      setTimeout(() => setIframeKey(prev => prev + 1), 3000);
    } catch (refreshError) {
      console.warn('[PrototypeConfirmation] 预览自愈失败:', refreshError);
    } finally {
      autoRecoverRef.current.inFlight = false;
    }
  }, [AUTO_RECOVER_COOLDOWN_MS, currentSandboxId, isGenerating, isReloading, reloadPreview, toast]);

  // Phase 2: Sandbox生命周期管理（保活 + 卸载清理）
  useSandboxHeartbeat({
    sandboxId: currentSandboxId,
    interval: 60000,
    enabled: !!currentSandboxId && !loading,
    onHeartbeatError: handleHeartbeatError,
  });

  useSandboxCleanup({
    sandboxId: currentSandboxId,
    cleanupOnHide: false,
    // 修复：在生成期间禁用清理，防止 apply 操作时 Sandbox 被意外杀死
    // 同时在 reloading 期间也禁用，避免刷新时的竞态条件
    enabled: !!currentSandboxId && !isGenerating && !isReloading,
    onBeforeCleanup: () => {
      console.log('[PrototypeConfirmation] 准备清理Sandbox:', currentSandboxId);
    },
    onCleanupComplete: () => {
      console.log('[PrototypeConfirmation] Sandbox清理完成:', currentSandboxId);
    },
  });

  // ========== 副作用 ==========

  // 自动启动生成
  useEffect(() => {
    if (!hasStartedRef.current && !routingResult.prototypeUrl && userRequirement && stage === 'idle') {
      hasStartedRef.current = true;
      startGeneration(userRequirement, {
        appSpecId: routingResult.appSpecId,
        styleId: routingResult.selectedStyleId
      });
    }
  }, [routingResult.prototypeUrl, userRequirement, stage, startGeneration, routingResult.appSpecId, routingResult.selectedStyleId]);

  // 生成完成后刷新iframe
  useEffect(() => {
    if (stage === 'complete' && previewUrl) {
      const timer = setTimeout(() => {
        setIframeKey(prev => prev + 1);
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [stage, previewUrl]);

  // V2.0新增：生成完成后同步更新后端AppSpec的原型状态
  // 解决问题：前端生成预览后，需要更新后端的frontendPrototype字段，否则confirmDesign会检查失败
  const hasUpdatedPrototypeRef = React.useRef(false);
  useEffect(() => {
    async function syncPrototypeStatus() {
      // 条件：生成完成、有sandboxInfo、有appSpecId、未曾更新过
      if (
        stage === 'complete' &&
        sandboxInfo?.sandboxId &&
        sandboxInfo?.url &&
        isValidUrl(sandboxInfo.url) &&
        routingResult.appSpecId &&
        !hasUpdatedPrototypeRef.current
      ) {
        hasUpdatedPrototypeRef.current = true;
        try {
          console.log('[PrototypeConfirmation] 同步原型状态到后端...');
          await updatePrototypeStatus(routingResult.appSpecId.toString(), {
            sandboxId: sandboxInfo.sandboxId,
            previewUrl: sandboxInfo.url,
            provider: sandboxInfo.provider || 'e2b',
          });
          console.log('[PrototypeConfirmation] ✅ 原型状态同步成功');
        } catch (err) {
          console.error('[PrototypeConfirmation] ❌ 原型状态同步失败:', err);
          // 不阻塞用户流程，仅记录错误
          toast({
            title: '状态同步失败',
            description: '预览已生成，但状态同步失败。如果确认设计失败，请刷新页面重试。',
            variant: 'default',
          });
        }
      }
    }

    syncPrototypeStatus();
  }, [stage, sandboxInfo, routingResult.appSpecId, toast]);

  // 自动切换到代码视图（如果生成中且没有预览URL）
  useEffect(() => {
    if (isGenerating && !effectivePreviewUrl && viewMode !== 'code') {
      // 保持在预览模式，因为Preview组件有遮罩显示代码流
    }
  }, [isGenerating, effectivePreviewUrl, viewMode]);

  // 初始化基础需求到历史记录（仅首次）
  useEffect(() => {
    if (historySeededRef.current) return;
    const trimmedRequirement = userRequirement?.trim();
    if (!trimmedRequirement) return;
    setChatHistory((prev) => {
      if (prev.length > 0) return prev;
      return [{
        id: buildHistoryId('requirement'),
        content: trimmedRequirement,
        timestamp: Date.now(),
        kind: 'requirement',
      }];
    });
    historySeededRef.current = true;
  }, [userRequirement, setChatHistory]);

  // ========== 事件处理 ==========

  const handleSendIteration = async (message: string) => {
    const historyId = buildHistoryId('iteration');
    setChatHistory(prev => [
      ...prev,
      {
        id: historyId,
        content: message,
        timestamp: Date.now(),
        kind: 'iteration',
      },
    ]);
    setActiveHistoryId(historyId);
    try {
      await onChatModify?.(message);
    } catch (e) {
      console.warn('[PrototypeConfirmation] onChatModify 处理失败:', e);
    }
    try {
      await sendIterationMessage(message);
      // 迭代成功后刷新预览
      if (previewUrl) {
        setTimeout(() => setIframeKey(prev => prev + 1), 2000);
      }
    } catch (e) {
      const errorMsg = e instanceof Error ? e.message : '修改失败';
      toast({
        title: '修改失败',
        description: errorMsg,
        variant: 'destructive',
      });
    } finally {
      setActiveHistoryId(null);
    }
  };

  const handleRefresh = async () => {
    await reloadPreview();
    setTimeout(() => setIframeKey(prev => prev + 1), 3000);
  };

  const handleCopyLink = () => {
    if (effectivePreviewUrl) {
      navigator.clipboard.writeText(effectivePreviewUrl);
      toast({ title: '链接已复制', description: '可以在浏览器中直接打开预览' });
    }
  };

  /**
   * 在新窗口打开预览链接（用于 iframe 受限/白屏时兜底）
   */
  const handleOpenPreview = () => {
    if (!effectivePreviewUrl) return;
    window.open(effectivePreviewUrl, '_blank', 'noopener,noreferrer');
  };

  /**
   * 预览加载失败回调
   *
   * 是什么：iframe 加载失败或超时的提示入口。
   * 做什么：通知用户当前预览不可用并给出错误信息。
   * 为什么：避免用户误以为系统卡顿，提供明确反馈。
   */
  const handlePreviewError = React.useCallback((error: Error) => {
    toast({
      title: '预览加载失败',
      description: error.message,
      variant: 'destructive',
    });
  }, [toast]);

  /**
   * 预览运行时错误回调
   *
   * 是什么：沙箱运行时错误的反馈入口。
   * 做什么：提示运行时异常并引导用户触发修复。
   * 为什么：运行时错误常导致白屏，需要快速可见的告警。
   */
  const handleRuntimeError = React.useCallback((error: Error) => {
    toast({
      title: '预览运行时错误',
      description: error.message,
      variant: 'destructive',
    });
  }, [toast]);

  /**
   * 白屏兜底修复
   *
   * 是什么：调用后端智能修复接口检查入口挂载并生成兜底 App。
   * 做什么：在预览白屏时允许用户主动触发修复并刷新 iframe。
   * 为什么：部分白屏不会触发 onError，需要提供可操作的恢复路径。
   */
  const handleSmartFix = React.useCallback(async () => {
    if (!currentSandboxId) {
      toast({
        title: '无法修复',
        description: '缺少沙箱ID，无法执行白屏修复',
        variant: 'destructive',
      });
      return;
    }
    if (isSmartFixing) return;

    setIsSmartFixing(true);
    try {
      const result = await smartFixPrototypeSandbox(currentSandboxId);
      const touchedCount = result.filesCreated.length + result.filesUpdated.length;
      toast({
        title: result.fixed ? '白屏已修复' : '已执行白屏修复',
        description: result.message || `已处理 ${touchedCount} 个文件`,
      });
      await reloadPreview();
      setTimeout(() => setIframeKey((prev) => prev + 1), 1500);
    } catch (error) {
      toast({
        title: '白屏修复失败',
        description: error instanceof Error ? error.message : '智能修复失败',
        variant: 'destructive',
      });
    } finally {
      setIsSmartFixing(false);
    }
  }, [currentSandboxId, isSmartFixing, reloadPreview, toast]);

  // ========== 渲染子组件 ==========

  // 右侧面板：交互与历史
  const LeftPanel = (
    <div className="h-full flex flex-col">
      <div className="flex-none p-4 border-b bg-background flex items-center justify-between">
        <div className="flex items-center gap-2">
           <LayoutTemplate className="w-5 h-5 text-primary" />
           <span className="font-semibold">设计助手</span>
        </div>
        <Button variant="ghost" size="sm" onClick={onBack} disabled={loading}>
          <ArrowLeft className="w-4 h-4 mr-1" />
          返回
        </Button>
      </div>
      <div className="flex-1 min-h-0">
        <InteractionPanel
          logs={generationLog}
          onSendMessage={handleSendIteration}
          isGenerating={isGenerating}
          historyItems={effectiveChatHistory}
          activeHistoryId={effectiveActiveHistoryId}
        />
      </div>
    </div>
  );

  // 左侧面板：预览与代码
  const RightPanel = (
    <div className="h-full flex flex-col bg-muted/10">
      {/* 工具栏 */}
      <div className="flex-none h-14 border-b bg-background/80 backdrop-blur-sm px-4 flex items-center justify-between relative z-[60]">
        <Tabs value={viewMode} onValueChange={(v) => setViewMode(v as ViewMode)} className="w-auto">
          <TabsList className="h-9">
            <TabsTrigger value="preview" className="text-xs px-3">
              <Eye className="w-3.5 h-3.5 mr-1.5" />
              界面预览
            </TabsTrigger>
            <TabsTrigger value="code" className="text-xs px-3">
              <Code className="w-3.5 h-3.5 mr-1.5" />
              生成代码
              {generatedFiles.length > 0 && (
                <Badge variant="secondary" className="ml-1.5 h-4 px-1 text-[10px] min-w-[1.25rem]">
                  {generatedFiles.length}
                </Badge>
              )}
            </TabsTrigger>
          </TabsList>
        </Tabs>

        <div className="flex items-center gap-2">
          {/* 状态指示器 */}
          <div className="hidden sm:flex items-center mr-2">
             {isGenerating ? (
               <Badge variant="outline" className="text-purple-600 border-purple-200 bg-purple-50 gap-1.5 animate-pulse">
                 <Loader2 className="w-3 h-3 animate-spin" />
                 生成中 {elapsedTime}s
               </Badge>
             ) : stage === 'complete' ? (
               <Badge variant="outline" className="text-green-600 border-green-200 bg-green-50 gap-1.5">
                 <CheckCircle2 className="w-3 h-3" />
                 已完成 {totalTime}s
               </Badge>
             ) : null}
          </div>

          <Button
            variant="ghost"
            size="icon"
            onClick={handleRefresh}
            disabled={loading || isReloading || isGenerating || !currentSandboxId}
            title={t('ui.refresh_preview')}
            data-testid="refresh-preview-button"
          >
            <RefreshCw className={cn('h-4 w-4', isReloading && 'animate-spin')} />
          </Button>

          {effectivePreviewUrl && (
            <>
              <Button variant="ghost" size="icon" onClick={handleCopyLink} title={t('ui.copy_link')}>
                <Copy className="h-4 w-4" />
              </Button>
              <Button variant="ghost" size="icon" onClick={handleOpenPreview} title={t('ui.open_new_window')}>
                <ExternalLink className="h-4 w-4" />
              </Button>
            </>
          )}

          <Button variant="ghost" size="icon" onClick={() => setIsFullscreen(!isFullscreen)} title={isFullscreen ? "退出全屏" : "全屏预览"}>
            {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </Button>

          <div className="w-px h-4 bg-border mx-1" />

          <PaywallGuard requiredCredits={1}>
            <Button
              onClick={() => setShowConfirmDialog(true)}
              disabled={loading || !canConfirm}
              className="bg-gradient-to-r from-purple-600 to-blue-600 text-white shadow-sm hover:opacity-90 transition-opacity"
              size="sm"
              data-testid="confirm-design-button"
            >
              {loading ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <CheckCircle2 className="h-4 w-4 mr-2" />}
              确认设计
            </Button>
          </PaywallGuard>
        </div>
      </div>

      {/* 内容区域 */}
      <div className="flex-1 overflow-hidden relative">
        {viewMode === 'preview' ? (
          <div className="h-full w-full bg-white dark:bg-zinc-950 flex flex-col">
             {/* 浏览器模拟地址栏 */}
             {effectivePreviewUrl && (
               <div className="h-8 bg-muted/30 border-b flex items-center px-4 gap-2">
                 <div className="flex gap-1.5">
                   <div className="w-2.5 h-2.5 rounded-full bg-red-400/80" />
                   <div className="w-2.5 h-2.5 rounded-full bg-yellow-400/80" />
                   <div className="w-2.5 h-2.5 rounded-full bg-green-400/80" />
                 </div>
                 <div className="flex-1 max-w-lg mx-auto bg-background rounded-md h-5 px-3 text-[10px] text-muted-foreground flex items-center justify-center font-mono border shadow-sm truncate">
                    {effectivePreviewUrl}
                 </div>
               </div>
             )}
             {invalidPreviewUrl && (
               <div className="border-b bg-amber-50 text-amber-900 px-4 py-2 text-xs flex items-center justify-between gap-3">
                 <div className="flex-1 min-w-0">
                   <span className="font-medium">预览地址无效：</span>
                   <span className="font-mono truncate inline-block align-bottom max-w-[65%]">{invalidPreviewUrl}</span>
                   <span className="ml-2 text-amber-800/80">（可能处于 Mock sandbox 环境，可尝试刷新或切换真实沙箱）</span>
                 </div>
                 <Button
                   size="sm"
                   variant="outline"
                   className="h-7 text-[11px] border-amber-300 bg-amber-50 hover:bg-amber-100"
                   onClick={handleRefresh}
                   disabled={loading || isReloading || isGenerating || !currentSandboxId}
                 >
                   刷新预览
                 </Button>
               </div>
             )}
             <div className="flex-1 relative">
                <PrototypePreview
                  previewUrl={effectivePreviewUrl}
                  isGenerating={isGenerating}
                  elapsedTime={elapsedTime}
                  stage={stage}
                  iframeKey={iframeKey}
                  streamedCode={streamedCode}
                  onSmartFix={currentSandboxId ? handleSmartFix : undefined}
                  smartFixing={isSmartFixing}
                  onPreviewError={handlePreviewError}
                  onRuntimeError={handleRuntimeError}
                />
             </div>
          </div>
        ) : (
          <div className="h-full w-full bg-[#1e1e1e]">
            <RealtimeCodeViewer
              files={generatedFiles}
              currentFile={currentFile?.path}
              isGenerating={isGenerating}
              className="h-full border-0 rounded-none"
              autoScroll={true}
            />
          </div>
        )}
      </div>
    </div>
  );

  // ========== 主渲染结构 ==========

  return (
    <div className={cn(
      "bg-background w-full overflow-hidden transition-all duration-300 relative",
      isFullscreen ? "fixed inset-0 z-50" : "h-[calc(100vh-100px)] border rounded-xl shadow-lg z-[55]"
    )} data-testid="prototype-confirmation-panel">
      
      <ConfirmationDialog
        isOpen={showConfirmDialog}
        onClose={() => setShowConfirmDialog(false)}
        onConfirm={handleConfirmDesign}
        loading={loading}
        g3Logs={g3Logs}
      />

      {/* 响应式布局：桌面端使用 ResizablePanels，移动端使用 Tabs */}
      {/* 布局：左侧为界面预览&代码，右侧为设计助手 */}
      <div className="hidden lg:block h-full w-full">
        <ResizablePanels
          defaultLeftWidth={65}
          minLeftWidth={50}
          maxLeftWidth={80}
          leftPanel={RightPanel}
          rightPanel={LeftPanel}
          dividerClassName="w-1 hover:w-1 bg-border/50 hover:bg-purple-500/50 transition-all"
        />
      </div>

      <div className="lg:hidden h-full flex flex-col">
        {/* 移动端简单Tab切换 */}
        <Tabs defaultValue="preview" className="flex-1 flex flex-col">
          <div className="flex-none p-2 border-b flex items-center justify-between">
             <TabsList>
               <TabsTrigger value="chat">设计</TabsTrigger>
               <TabsTrigger value="preview">预览</TabsTrigger>
             </TabsList>
             <PaywallGuard requiredCredits={1}>
               <Button size="sm" onClick={() => setShowConfirmDialog(true)} disabled={!canConfirm}>
                 确认
               </Button>
             </PaywallGuard>
          </div>
          <div className="flex-1 overflow-hidden">
             <TabsContent value="chat" className="h-full m-0 data-[state=inactive]:hidden">
               {LeftPanel}
             </TabsContent>
             <TabsContent value="preview" className="h-full m-0 data-[state=inactive]:hidden">
               {RightPanel}
             </TabsContent>
          </div>
        </Tabs>
      </div>
    </div>
  );
}
