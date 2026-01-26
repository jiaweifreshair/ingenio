'use client';

import React, { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { Loader2, CheckCircle2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { useLanguage } from '@/contexts/LanguageContext';

interface PrototypePreviewProps {
  previewUrl: string | null;
  isGenerating: boolean;
  elapsedTime: number;
  stage: string;
  iframeKey: number;
  className?: string;
  streamedCode?: string;
}

export function PrototypePreview({
  previewUrl,
  isGenerating,
  elapsedTime,
  stage,
  iframeKey,
  className = '',
  streamedCode = '',
}: PrototypePreviewProps) {
  const { t } = useLanguage();
  const [iframeLoaded, setIframeLoaded] = useState(false);
  const [iframeError, setIframeError] = useState<string | null>(null);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const codeScrollRef = useRef<HTMLDivElement>(null);
  const codeScrollRef2 = useRef<HTMLDivElement>(null);

  // 自动滚动到底部的通用函数
  const scrollToBottom = useCallback((ref: React.RefObject<HTMLDivElement | null>) => {
    if (ref.current) {
      // 使用 requestAnimationFrame 确保 DOM 更新后再滚动
      requestAnimationFrame(() => {
        if (ref.current) {
          ref.current.scrollTop = ref.current.scrollHeight;
        }
      });
    }
  }, []);

  // Auto-scroll code view (有previewUrl时的遮罩层代码流)
  useEffect(() => {
    scrollToBottom(codeScrollRef);
  }, [streamedCode, scrollToBottom]);

  // Auto-scroll code view (无previewUrl时的代码流)
  useEffect(() => {
    scrollToBottom(codeScrollRef2);
  }, [streamedCode, scrollToBottom]);

  // Reset loaded state when key changes (reload)
  useEffect(() => {
    setIframeLoaded(false);
    setIframeError(null);
  }, [iframeKey]);

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

  return (
    <div className={`relative h-full w-full ${className}`}>
      {previewUrl ? (
        <>
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
            }}
            onError={() => {
              setIframeLoaded(false);
              setIframeError(t('ui.preview_load_failed'));
            }}
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
