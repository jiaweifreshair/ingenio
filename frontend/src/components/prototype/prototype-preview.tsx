'use client';

import React, { useState, useRef, useEffect, useLayoutEffect, useMemo, useCallback } from 'react';
import { Loader2, CheckCircle2, Wrench } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { useLanguage } from '@/contexts/LanguageContext';

interface PrototypePreviewProps {
  previewUrl: string | null;
  isGenerating: boolean;
  elapsedTime: number;
  stage: string;
  iframeKey: number;
  className?: string;
  streamedCode?: string;
  /** 触发白屏兜底修复 */
  onSmartFix?: () => Promise<void> | void;
  /** 白屏修复中状态 */
  smartFixing?: boolean;
  /**
   * 预览运行时错误回调
   *
   * 是什么：沙箱运行时错误的回调函数。
   * 做什么：向上层传递错误，便于提示与自动修复。
   * 为什么：运行时异常可能导致白屏，需要外部处理。
   */
  onRuntimeError?: (error: Error) => void;
  /**
   * 预览加载失败回调
   *
   * 是什么：iframe 加载失败的回调函数。
   * 做什么：向上层传递加载失败原因与时机。
   * 为什么：便于记录失败并提示用户重试或修复。
   */
  onPreviewError?: (error: Error) => void;
}

export function PrototypePreview({
  previewUrl,
  isGenerating,
  elapsedTime,
  stage,
  iframeKey,
  className = '',
  streamedCode = '',
  onSmartFix,
  smartFixing = false,
  onRuntimeError,
  onPreviewError,
}: PrototypePreviewProps) {
  const { t } = useLanguage();
  /**
   * 预览加载超时阈值
   *
   * 是什么：等待 iframe 成功加载的最大时长。
   * 做什么：超过阈值后触发白屏兜底展示代码。
   * 为什么：避免预览空白时用户无反馈。
   */
  const PREVIEW_TIMEOUT_MS = 12_000;
  const [iframeLoaded, setIframeLoaded] = useState(false);
  const [iframeError, setIframeError] = useState<string | null>(null);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const codeScrollRef = useRef<HTMLDivElement>(null);
  const codeScrollRef2 = useRef<HTMLDivElement>(null);
  /**
   * 预览错误状态标记
   *
   * 是什么：记录当前 iframe 是否已触发错误回调。
   * 做什么：防止同一次错误事件触发多次回调。
   * 为什么：避免重复提示造成用户困扰。
   */
  const hasPreviewErrorRef = useRef(false);
  // 代码流底部锚点：用于流式输出时强制滚动到最新位置，避免遮罩停在顶部。
  const codeTailRef = useRef<HTMLSpanElement>(null);
  // 无预览时的代码流底部锚点：作用同上，确保滚动跟随输出。
  const codeTailRef2 = useRef<HTMLSpanElement>(null);

  // 自动滚动到底部的通用函数
  const scrollToBottom = useCallback((ref: React.RefObject<HTMLElement | null>) => {
    const target = ref.current;
    if (!target) {
      return;
    }

    // 使用 requestAnimationFrame 确保 DOM 更新后再滚动
    requestAnimationFrame(() => {
      target.scrollIntoView({ block: 'end' });
    });
  }, []);

  // Auto-scroll code view (有previewUrl时的遮罩层代码流)
  useLayoutEffect(() => {
    scrollToBottom(codeTailRef);
  }, [streamedCode, scrollToBottom]);

  // Auto-scroll code view (无previewUrl时的代码流)
  useLayoutEffect(() => {
    scrollToBottom(codeTailRef2);
  }, [streamedCode, scrollToBottom]);

  // Reset loaded state when key changes (reload)
  useEffect(() => {
    setIframeLoaded(false);
    setIframeError(null);
    hasPreviewErrorRef.current = false;
  }, [iframeKey, previewUrl]);

  /**
   * 统一处理 iframe 错误
   *
   * 是什么：预览加载失败的统一处理逻辑。
   * 做什么：更新状态并触发 onPreviewError 回调。
   * 为什么：确保错误提示与兜底展示一致。
   */
  const handleIframeError = useCallback(() => {
    if (hasPreviewErrorRef.current) {
      return;
    }
    hasPreviewErrorRef.current = true;
    setIframeLoaded(false);
    const errorMessage = t('ui.preview_load_failed');
    setIframeError(errorMessage);
    if (onPreviewError) {
      onPreviewError(new Error(errorMessage));
    }
  }, [onPreviewError, t]);

  /**
   * 预览加载超时兜底
   *
   * 是什么：当 iframe 长时间未加载完成时标记为失败。
   * 做什么：触发错误提示并展示代码兜底。
   * 为什么：减少白屏无反馈导致的阻塞。
   */
  useEffect(() => {
    if (!previewUrl || isGenerating || iframeLoaded) {
      return;
    }

    const timer = window.setTimeout(() => {
      const errorMessage = t('ui.preview_load_failed');
      setIframeError(errorMessage);
      if (onPreviewError) {
        onPreviewError(new Error(errorMessage));
      }
    }, PREVIEW_TIMEOUT_MS);

    return () => window.clearTimeout(timer);
  }, [previewUrl, isGenerating, iframeLoaded, t, onPreviewError]);

  /**
   * iframe 错误事件监听
   *
   * 是什么：监听 iframe 原生 error 事件。
   * 做什么：确保测试环境与真实浏览器一致触发回调。
   * 为什么：React 的 onError 在 iframe 上可能无法稳定触发。
   */
  useEffect(() => {
    const iframeElement = iframeRef.current;
    if (!iframeElement) {
      return;
    }
    iframeElement.addEventListener('error', handleIframeError);
    return () => {
      iframeElement.removeEventListener('error', handleIframeError);
    };
  }, [handleIframeError, iframeKey, previewUrl]);

  /**
   * 沙箱运行时错误监听
   *
   * 是什么：监听 sandbox 运行时错误消息。
   * 做什么：触发回调并启用白屏兜底展示。
   * 为什么：预览运行时异常需要及时反馈与修复。
   */
  useEffect(() => {
    /**
     * 运行时错误标准化
     *
     * 是什么：把沙箱错误 payload 转为 Error 实例。
     * 做什么：兼容字符串/对象/Error 等多种类型。
     * 为什么：确保上层统一处理白屏与运行时异常。
     */
    const normalizeSandboxError = (rawError: unknown): Error => {
      if (rawError instanceof Error) {
        return rawError;
      }
      if (rawError && typeof rawError === 'object' && 'message' in rawError) {
        const message = (rawError as { message?: unknown }).message;
        return new Error(typeof message === 'string' ? message : '预览运行时错误');
      }
      if (typeof rawError === 'string') {
        return new Error(rawError);
      }
      return new Error('预览运行时错误');
    };

    const handleMessage = (event: MessageEvent) => {
      if (event.data && event.data.type === 'SANDBOX_ERROR' && event.data.error) {
        const normalizedError = normalizeSandboxError(event.data.error);
        setIframeLoaded(false);
        setIframeError(t('ui.preview_load_failed'));
        if (onRuntimeError) {
          onRuntimeError(normalizedError);
        }
      }
    };

    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [onRuntimeError, t]);

  // 为避免浏览器缓存导致 iframe 停留在旧的错误页/空白页，刷新时增加 cache bust 参数
  const iframeSrc = useMemo(() => {
    if (!previewUrl) return null;
    if (iframeKey <= 0) return previewUrl;

    try {
      const url = new URL(previewUrl);
      url.searchParams.set('t', Date.now().toString());
      return url.toString();
    } catch {
      const hasQuery = previewUrl.includes('?');
      const separator = hasQuery ? '&' : '?';
      return `${previewUrl}${separator}t=${Date.now()}`;
    }
  }, [previewUrl, iframeKey]);

  const canSmartFix = !!previewUrl && !isGenerating && !!onSmartFix;

  return (
    <div className={`relative h-full w-full ${className}`}>
      {previewUrl ? (
        <>
          {canSmartFix && (
            <div className="absolute top-3 right-3 z-40">
              <Button
                size="sm"
                variant="secondary"
                onClick={onSmartFix}
                disabled={smartFixing}
                className="text-xs gap-1.5 bg-white/80 hover:bg-white"
              >
                <Wrench className="w-3.5 h-3.5" />
                {smartFixing ? t('ui.white_screen_fixing') : t('ui.white_screen_fix')}
              </Button>
            </div>
          )}
          <iframe
            key={iframeKey}
            ref={iframeRef}
            src={iframeSrc || previewUrl}
            className="w-full h-full border-0"
            title={t('ui.prototype_preview')}
            sandbox="allow-scripts allow-same-origin allow-forms allow-modals allow-popups"
            onLoad={() => {
              setIframeLoaded(true);
              setIframeError(null);
              hasPreviewErrorRef.current = false;
            }}
            onError={handleIframeError}
            allow="accelerometer; camera; encrypted-media; geolocation; gyroscope; microphone; midi"
            data-testid="prototype-preview-iframe"
          />

          {/* iframe 加载失败提示（提供新窗口打开作为兜底） */}
          {iframeError && !isGenerating && (
            <div className="absolute inset-0 z-30 bg-background/90 dark:bg-zinc-950/90 backdrop-blur-sm flex flex-col items-center justify-center p-6 text-center">
              <p className="text-sm text-muted-foreground mb-3">{iframeError}</p>
              {previewUrl && (
                <a
                  href={previewUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="text-sm text-primary underline underline-offset-4"
                >
                  {t('ui.open_preview_new_window')}
                </a>
              )}
              {streamedCode && (
                <div className="mt-4 w-full max-w-2xl bg-black/80 text-green-300 rounded-lg p-4 text-left">
                  <div className="text-xs text-green-200/80 mb-2">代码兜底预览</div>
                  <pre className="text-xs whitespace-pre-wrap break-all max-h-60 overflow-y-auto">
                    {streamedCode}
                  </pre>
                </div>
              )}
            </div>
          )}

          {/* 生成中遮罩 (带代码流) */}
          {isGenerating && (
            <div className="absolute inset-0 bg-black/80 backdrop-blur-md flex flex-col p-6 z-20">
              <div className="flex items-center justify-between mb-4 text-white">
                <div className="flex items-center gap-2">
                  <Loader2 className="w-5 h-5 animate-spin text-purple-400" />
                  <span className="font-medium">
                    {stage === 'sandbox' ? t('ui.creating_sandbox') : t('ui.ai_writing_code')}
                  </span>
                </div>
                <div className="text-sm opacity-70">
                  {t('ui.elapsed_time')} <span className="font-mono">{elapsedTime}</span>s
                </div>
              </div>
              
              {/* 代码流展示区 - 带滚动条和打字机效果 */}
              {/* 代码流展示区 - 带滚动条和打字机效果 */}
              <div
                ref={codeScrollRef}
                className="flex-1 overflow-y-scroll font-mono text-xs text-green-400 bg-black/50 p-4 rounded-lg border border-white/10 relative [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar-thumb]:bg-green-400/30 hover:[&::-webkit-scrollbar-thumb]:bg-green-400/80 [&::-webkit-scrollbar-thumb]:rounded-full"
                style={{ scrollbarWidth: 'thin', scrollbarColor: '#4ade80 transparent' }}
              >
                <pre className="whitespace-pre-wrap break-all pb-8">
                {streamedCode || '// ' + t('ui.generating_code')}
                  <span className="inline-block w-2 h-4 bg-green-400 ml-1 animate-pulse align-middle" />
                  <span ref={codeTailRef} className="block h-px w-full" aria-hidden="true" />
                </pre>

                {/* 底部渐变遮罩 */}
                <div className="absolute bottom-0 left-0 right-0 h-12 bg-gradient-to-t from-black/50 to-transparent pointer-events-none" />
              </div>
            </div>
          )}

          {/* 预览就绪标记 */}
          {stage === 'complete' && iframeLoaded && !iframeError && (
            <Badge className="absolute top-2 right-2 bg-green-100 text-green-700 border-green-200">
              <CheckCircle2 className="h-3 w-3 mr-1" />
              {t('ui.preview_ready')}
            </Badge>
          )}
        </>
      ) : (
        <div className="flex flex-col items-center justify-center h-full w-full bg-gray-50 dark:bg-gray-900">
           {isGenerating && streamedCode ? (
             <div className="w-full h-full flex flex-col p-6">
               <div className="flex items-center justify-between mb-4">
                 <div className="flex items-center gap-2">
                   <Loader2 className="w-5 h-5 animate-spin text-purple-600" />
                   <span className="font-medium text-gray-900 dark:text-gray-100">
                     {stage === 'sandbox' ? t('ui.creating_sandbox') : t('ui.ai_writing_code')}
                   </span>
                 </div>
                 <div className="text-sm text-muted-foreground">
                   {t('ui.elapsed_time')} <span className="font-mono">{elapsedTime}</span>s
                 </div>
               </div>
               
               <div
                 ref={codeScrollRef2}
                 className="flex-1 overflow-y-scroll font-mono text-xs bg-[#1e1e1e] text-[#d4d4d4] p-4 rounded-lg shadow-inner border border-gray-200 dark:border-gray-800 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar-thumb]:bg-purple-500/30 hover:[&::-webkit-scrollbar-thumb]:bg-purple-500/80 [&::-webkit-scrollbar-thumb]:rounded-full"
                 style={{ scrollbarWidth: 'thin', scrollbarColor: '#a855f7 transparent' }}
               >
                 <pre className="whitespace-pre-wrap break-all">
                   {streamedCode}
                   <span className="inline-block w-2 h-4 bg-purple-500 ml-1 animate-pulse align-middle" />
                   <span ref={codeTailRef2} className="block h-px w-full" aria-hidden="true" />
                 </pre>
               </div>
             </div>
           ) : (
             <div className="flex flex-col items-center justify-center">
               <Loader2 className="h-8 w-8 animate-spin text-purple-600 mb-4" />
               <p className="text-muted-foreground">
                 {stage === 'sandbox'
                   ? t('ui.creating_sandbox')
                   : stage === 'generating'
                     ? t('ui.ai_generating_code')
                     : t('ui.preparing_environment')}
               </p>
               <p className="text-xs text-muted-foreground mt-2">
                 {t('ui.elapsed_time')} {elapsedTime}s · {stage === 'sandbox' ? t('ui.usually_takes_short') : t('ui.usually_takes_long')}
               </p>
             </div>
           )}
        </div>
      )}
    </div>
  );
}
