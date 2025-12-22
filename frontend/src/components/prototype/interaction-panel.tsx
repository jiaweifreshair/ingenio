'use client';

import React, { useRef, useEffect } from 'react';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Button } from '@/components/ui/button';
import { Send, Loader2, Sparkles } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';

export interface InteractionPanelProps {
  logs: string[];
  onSendMessage: (message: string) => void;
  isGenerating: boolean;
  className?: string;
  initialRequirement?: string;
}

export function InteractionPanel({
  logs,
  onSendMessage,
  isGenerating,
  className,
  initialRequirement
}: InteractionPanelProps) {
  const [input, setInput] = React.useState('');
  const endRef = useRef<HTMLDivElement>(null);

  // 自动滚动
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs]);

  const handleSubmit = (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!input.trim() || isGenerating) return;
    onSendMessage(input.trim());
    setInput('');
  };

  // 处理按键（Enter发送，Shift+Enter换行）
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  return (
    <div className={cn("flex flex-col h-full bg-background border-r", className)}>
      {/* Header */}
      <div className="p-4 border-b flex items-center justify-between bg-muted/20">
        <div className="flex items-center gap-2">
           <Sparkles className="w-4 h-4 text-purple-600" />
           <span className="font-medium text-sm">AI 思考过程</span>
        </div>
        {isGenerating && (
          <Badge variant="secondary" className="text-xs animate-pulse bg-purple-100 text-purple-700">
            执行中
          </Badge>
        )}
      </div>

      {/* Content Area (Logs & Chat) */}
      <ScrollArea className="flex-1 p-4">
        <div className="space-y-6">
          {/* 初始需求 */}
          {initialRequirement && (
            <div className="flex gap-3">
              <Avatar className="w-8 h-8 border">
                <AvatarFallback className="bg-muted text-xs">U</AvatarFallback>
              </Avatar>
              <div className="space-y-1">
                <div className="text-xs font-medium text-muted-foreground">User</div>
                <div className="text-sm bg-muted/30 p-3 rounded-lg rounded-tl-none break-words whitespace-pre-wrap">
                  {initialRequirement}
                </div>
              </div>
            </div>
          )}

          {/* 系统日志流 (模拟成对话气泡) */}
          <div className="flex gap-3">
            <Avatar className="w-8 h-8 border bg-purple-50">
               <AvatarImage src="/bot-avatar.png" />
               <AvatarFallback className="bg-purple-100 text-purple-700 text-xs">AI</AvatarFallback>
            </Avatar>
            <div className="space-y-1 flex-1 min-w-0">
               <div className="text-xs font-medium text-muted-foreground">Ingenio AI</div>
               <div className="space-y-2">
                 {logs.map((log, index) => {
                   // 简单解析日志类型
                   const timestamp = log.match(/^\[(.*?)\]/)?.[1] || '';
                   const content = log.replace(/^\[(.*?)\]\s*/, '');
                   const isError = content.includes('❌') || content.includes('Error');
                   const isSuccess = content.includes('✅') || content.includes('🎉') || content.includes('🎯');
                   const isProcess = !isError && !isSuccess;

                   return (
                     <div 
                        key={index} 
                        className={cn(
                          "text-xs px-3 py-2 rounded-md font-mono border break-all",
                          isError && "bg-red-50 text-red-700 border-red-100",
                          isSuccess && "bg-green-50 text-green-700 border-green-100",
                          isProcess && "bg-background text-muted-foreground border-border/50"
                        )}
                      >
                       {timestamp && <span className="opacity-50 mr-2">{timestamp}</span>}
                       {content}
                     </div>
                   );
                 })}
                 {isGenerating && (
                   <div className="flex items-center gap-2 text-xs text-muted-foreground px-2">
                     <Loader2 className="w-3 h-3 animate-spin" />
                     <span>正在思考下一步...</span>
                   </div>
                 )}
               </div>
            </div>
          </div>
          
          <div ref={endRef} />
        </div>
      </ScrollArea>

      {/* Input Area */}
      <div className="p-4 border-t bg-background">
        <div className="relative">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入修改建议 (例如: 把按钮改成蓝色)..."
            className="w-full min-h-[80px] p-3 pr-12 text-sm bg-muted/30 rounded-lg border focus:outline-none focus:ring-2 focus:ring-purple-500/20 resize-none"
            disabled={isGenerating}
          />
          <Button 
            size="icon" 
            className="absolute right-2 bottom-2 h-8 w-8 rounded-full"
            onClick={() => handleSubmit()}
            disabled={!input.trim() || isGenerating}
          >
            {isGenerating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
          </Button>
        </div>
        <div className="text-[10px] text-muted-foreground mt-2 text-center">
          Enter 发送，Shift + Enter 换行
        </div>
      </div>
    </div>
  );
}
