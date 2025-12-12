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
}

export function PrototypePreview({
  previewUrl,
  isGenerating,
  elapsedTime,
  stage,
  iframeKey,
  className = '',
}: PrototypePreviewProps) {
  const [iframeLoaded, setIframeLoaded] = useState(false);
  const iframeRef = useRef<HTMLIFrameElement>(null);

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

          {/* 生成中遮罩 */}
          {isGenerating && (
            <div className="absolute inset-0 bg-black/30 backdrop-blur-sm flex flex-col items-center justify-center">
              <Loader2 className="w-12 h-12 animate-spin text-white mb-3" />
              <p className="text-white font-medium">AI正在生成代码...</p>
              <p className="text-white/70 text-sm mt-1">
                已用时 <span className="font-mono">{elapsedTime}</span>s
              </p>
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
        <div className="flex flex-col items-center justify-center h-full">
          <Loader2 className="h-8 w-8 animate-spin text-purple-600 mb-4" />
          <p className="text-muted-foreground">
            {stage === 'sandbox' ? '正在创建沙箱...' : '原型生成中，请稍候...'}
          </p>
          <p className="text-xs text-muted-foreground mt-2">
            已用时 {elapsedTime}s · 通常需要5-10秒
          </p>
        </div>
      )}
    </div>
  );
}
