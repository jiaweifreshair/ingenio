/**
 * PrototypeConfirmation - V2.0原型预览确认组件（深度融合版 - 修复优化）
 *
 * 优化内容：
 * - 采用左右分栏布局（左侧交互/日志，右侧预览/代码）
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

import React, { useState, useEffect } from 'react';
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
} from 'lucide-react';

import type { PlanRoutingResult } from '@/lib/api/plan-routing';
import { updatePrototypeStatus } from '@/lib/api/plan-routing';
import type { Template } from '@/types/template';
import { useOpenLovablePreview } from '@/hooks/use-openlovable-preview';
import { cn } from '@/lib/utils';
import { PrototypePreview } from './prototype-preview';
import { ConfirmationDialog } from './confirmation-dialog';
import { InteractionPanel } from './interaction-panel';
import { RealtimeCodeViewer } from '@/components/code-generation/realtime-code-viewer';
import { ResizablePanels } from '@/components/ui/resizable-panels';
import { useToast } from '@/hooks/use-toast';
import { useSandboxHeartbeat } from '@/hooks/use-sandbox-heartbeat';
import { useSandboxCleanup } from '@/hooks/use-sandbox-cleanup';

// ==================== 类型定义 ====================

export interface PrototypeConfirmationProps {
  routingResult: PlanRoutingResult;
  userRequirement?: string;
  selectedTemplate?: Template | null;
  onConfirm: () => void;
  onBack: () => void;
  loading?: boolean;
  error?: string | null;
}

type ViewMode = 'preview' | 'code';

// ==================== 主组件 ====================

export function PrototypeConfirmation({
  routingResult,
  userRequirement = '',
  onConfirm,
  onBack,
  loading = false,
  error: externalError = null,
}: PrototypeConfirmationProps): React.ReactElement {
  const { toast } = useToast();
  
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
  } = useOpenLovablePreview();

  // 状态管理
  const [viewMode, setViewMode] = useState<ViewMode>('preview');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [iframeKey, setIframeKey] = useState(0);
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const hasStartedRef = React.useRef(false);

  // 组合错误信息
  const error = externalError || openLovableError;
  const effectivePreviewUrl = previewUrl || routingResult.prototypeUrl || null;
  const canConfirm = (stage === 'complete' || routingResult.prototypeGenerated) && !error;
  const isGenerating = stage === 'sandbox' || stage === 'generating';
  const currentSandboxId = sandboxInfo?.sandboxId || null;

  // Phase 2: Sandbox生命周期管理（保活 + 卸载清理）
  useSandboxHeartbeat({
    sandboxId: currentSandboxId,
    interval: 60000,
    enabled: !!currentSandboxId && !loading,
    onHeartbeatError: (heartbeatError) => {
      console.warn('[PrototypeConfirmation] Sandbox心跳失败:', heartbeatError);
    },
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

  // ========== 事件处理 ==========

  const handleSendIteration = async (message: string) => {
    await sendIterationMessage(message);
    if (previewUrl) {
      setTimeout(() => setIframeKey(prev => prev + 1), 2000);
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

  // ========== 渲染子组件 ==========

  // 左侧面板：交互与日志
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
          initialRequirement={userRequirement}
        />
      </div>
    </div>
  );

  // 右侧面板：预览与代码
  const RightPanel = (
    <div className="h-full flex flex-col bg-muted/10">
      {/* 工具栏 */}
      <div className="flex-none h-14 border-b bg-background/80 backdrop-blur-sm px-4 flex items-center justify-between sticky top-0 z-10">
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
            disabled={loading || isReloading || !effectivePreviewUrl}
            title="刷新预览"
          >
            <RefreshCw className={cn('h-4 w-4', isReloading && 'animate-spin')} />
          </Button>

          {effectivePreviewUrl && (
            <Button variant="ghost" size="icon" onClick={handleCopyLink} title="复制链接">
              <ExternalLink className="h-4 w-4" />
            </Button>
          )}

          <Button variant="ghost" size="icon" onClick={() => setIsFullscreen(!isFullscreen)} title={isFullscreen ? "退出全屏" : "全屏预览"}>
            {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </Button>

          <div className="w-px h-4 bg-border mx-1" />

          <Button
            onClick={() => setShowConfirmDialog(true)}
            disabled={loading || !canConfirm}
            className="bg-gradient-to-r from-purple-600 to-blue-600 text-white shadow-sm hover:opacity-90 transition-opacity"
            size="sm"
          >
            {loading ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <CheckCircle2 className="h-4 w-4 mr-2" />}
            确认设计
          </Button>
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
             <div className="flex-1 relative">
                <PrototypePreview
                  previewUrl={effectivePreviewUrl}
                  isGenerating={isGenerating}
                  elapsedTime={elapsedTime}
                  stage={stage}
                  iframeKey={iframeKey}
                  streamedCode={streamedCode}
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
      "bg-background w-full overflow-hidden transition-all duration-300",
      isFullscreen ? "fixed inset-0 z-50" : "h-[calc(100vh-100px)] border rounded-xl shadow-lg"
    )} data-testid="prototype-confirmation-panel">
      
      <ConfirmationDialog
        isOpen={showConfirmDialog}
        onClose={() => setShowConfirmDialog(false)}
        onConfirm={onConfirm}
        loading={loading}
      />

      {/* 响应式布局：桌面端使用 ResizablePanels，移动端使用 Tabs */}
      <div className="hidden lg:block h-full w-full">
        <ResizablePanels
          defaultLeftWidth={30}
          minLeftWidth={20}
          maxLeftWidth={50}
          leftPanel={LeftPanel}
          rightPanel={RightPanel}
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
             <Button size="sm" onClick={() => setShowConfirmDialog(true)} disabled={!canConfirm}>
               确认
             </Button>
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
