'use client';

import React, { useState, useRef, useEffect } from 'react';
import { Loader2, CheckCircle2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';

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
  const [iframeLoaded, setIframeLoaded] = useState(false);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const codeScrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll code view
  useEffect(() => {
    if (codeScrollRef.current) {
      codeScrollRef.current.scrollTop = codeScrollRef.current.scrollHeight;
    }
  }, [streamedCode]);

  // Reset loaded state when key changes (reload)
  useEffect(() => {
    setIframeLoaded(false);
  }, [iframeKey]);

  return (
    <div className={`relative h-full w-full ${className}`}>
      {previewUrl ? (
        <>
          <iframe
            key={iframeKey}
            ref={iframeRef}
            src={previewUrl}
            className="w-full h-full border-0"
            title="原型预览"
            onLoad={() => setIframeLoaded(true)}
            allow="accelerometer; camera; encrypted-media; geolocation; gyroscope; microphone; midi"
            data-testid="prototype-preview-iframe"
          />

          {/* 生成中遮罩 (带代码流) */}
          {isGenerating && (
            <div className="absolute inset-0 bg-black/80 backdrop-blur-md flex flex-col p-6 z-20">
              <div className="flex items-center justify-between mb-4 text-white">
                <div className="flex items-center gap-2">
                  <Loader2 className="w-5 h-5 animate-spin text-purple-400" />
                  <span className="font-medium">AI正在编写代码...</span>
                </div>
                <div className="text-sm opacity-70">
                  已用时 <span className="font-mono">{elapsedTime}</span>s
                </div>
              </div>
              
              {/* 代码流展示区 */}
              <div 
                ref={codeScrollRef}
                className="flex-1 overflow-hidden font-mono text-xs text-green-400 bg-black/50 p-4 rounded-lg border border-white/10 relative"
              >
                <pre className="whitespace-pre-wrap break-all pb-8">
                  {streamedCode || '// 初始化代码生成环境...'}
                  <span className="inline-block w-2 h-4 bg-green-400 ml-1 animate-pulse align-middle" />
                </pre>
                
                {/* 底部渐变遮罩 */}
                <div className="absolute bottom-0 left-0 right-0 h-12 bg-gradient-to-t from-black/50 to-transparent pointer-events-none" />
              </div>
            </div>
          )}

          {/* 预览就绪标记 */}
          {stage === 'complete' && iframeLoaded && (
            <Badge className="absolute top-2 right-2 bg-green-100 text-green-700 border-green-200">
              <CheckCircle2 className="h-3 w-3 mr-1" />
              预览就绪
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
                   <span className="font-medium text-gray-900 dark:text-gray-100">AI正在编写代码...</span>
                 </div>
                 <div className="text-sm text-muted-foreground">
                   已用时 <span className="font-mono">{elapsedTime}</span>s
                 </div>
               </div>
               
               <div 
                 ref={codeScrollRef}
                 className="flex-1 overflow-y-auto font-mono text-xs bg-[#1e1e1e] text-[#d4d4d4] p-4 rounded-lg shadow-inner border border-gray-200 dark:border-gray-800"
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
                 {stage === 'sandbox' ? '正在创建沙箱...' : '准备生成环境...'}
               </p>
               <p className="text-xs text-muted-foreground mt-2">
                 已用时 {elapsedTime}s · 通常需要5-10秒
               </p>
             </div>
           )}
        </div>
      )}
    </div>
  );
}
