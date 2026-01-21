'use client';

import React, { useRef, useEffect } from 'react';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Button } from '@/components/ui/button';
import { Send, Loader2, Sparkles, History, PanelLeft } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import { useLanguage } from '@/contexts/LanguageContext';
import { formatDistanceToNow } from 'date-fns';
import { zhCN } from 'date-fns/locale';

/**
 * ChatHistoryItem - 对话历史条目
 *
 * 用途：
 * - 统一承载“基础需求 + 迭代修改”的展示与索引
 * - 支撑左侧历史记录列表和右侧对话气泡渲染
 */
export interface ChatHistoryItem {
  /** 本地唯一ID：用于定位与高亮 */
  id: string;
  /** 内容文本：原始需求或用户修改 */
  content: string;
  /** 时间戳：用于历史列表展示相对时间 */
  timestamp: number;
  /** 条目类型：区分基础需求与迭代修改 */
  kind: 'requirement' | 'iteration';
}

export interface InteractionPanelProps {
  /** 历史记录列表：每条记录对应一次用户需求/迭代输入 */
  historyItems: ChatHistoryItem[];
  logs: string[];
  onSendMessage: (message: string) => void;
  isGenerating: boolean;
  /** 当前正在处理的历史记录ID（可选，用于显示处理中状态） */
  activeHistoryId?: string | null;
  className?: string;
}

/**
 * 日志渲染片段
 *
 * 用途：
 * - 兼容模型输出的 <think> 片段拆分展示
 */
interface LogRenderPart {
  type: 'text' | 'think';
  content: string;
}

/**
 * 拆分日志中的 <think> 标签内容
 *
 * 用途：
 * - 避免思考过程与正文混排影响可读性
 * - 对齐 DeepVCode 的思考段落解析思路
 */
const splitThinkBlocks = (text: string): LogRenderPart[] => {
  const parts: LogRenderPart[] = [];
  const thinkRegex = /<think>([\s\S]*?)(?:<\/think>|$)/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  thinkRegex.lastIndex = 0;

  while ((match = thinkRegex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      const beforeText = text.substring(lastIndex, match.index);
      if (beforeText) {
        parts.push({ type: 'text', content: beforeText });
      }
    }
    parts.push({ type: 'think', content: match[1] });
    lastIndex = thinkRegex.lastIndex;
  }

  if (lastIndex < text.length) {
    const afterText = text.substring(lastIndex);
    if (afterText) {
      parts.push({ type: 'text', content: afterText });
    }
  }

  if (parts.length === 0) {
    parts.push({ type: 'text', content: text });
  }

  return parts;
};

export function InteractionPanel({
  historyItems,
  logs,
  onSendMessage,
  isGenerating,
  activeHistoryId,
  className
}: InteractionPanelProps) {
  const { t } = useLanguage();
  const [input, setInput] = React.useState('');
  const [showHistory, setShowHistory] = React.useState(false);
  const endRef = useRef<HTMLDivElement>(null);

  // 自动滚动
  useEffect(() => {
    const target = endRef.current;
    if (target && typeof target.scrollIntoView === 'function') {
      target.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, historyItems]);

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
    <div className={cn("flex h-full bg-background lg:border-r flex-col lg:flex-row", className)}>
      {/* 左侧历史记录 */}
      {/* 左侧历史记录 */}
      {showHistory && (
        <div className="w-full lg:w-64 border-b lg:border-b-0 lg:border-r bg-muted/10 flex flex-col shrink-0">
          <div className="p-4 border-b flex items-center justify-between bg-muted/20">
            <div className="flex items-center gap-2">
              <History className="w-4 h-4 text-purple-600" />
              <span className="font-medium text-sm">历史记录</span>
            </div>
            <span className="text-xs bg-muted px-2 py-0.5 rounded-full text-muted-foreground">
              {historyItems.length}
            </span>
          </div>
          <ScrollArea className="flex-1">
            <div className="p-3 space-y-3">
              {historyItems.length === 0 ? (
                <div className="text-center py-6 text-muted-foreground text-xs">
                  暂无历史记录
                </div>
              ) : (
                historyItems.map((item) => {
                  const isActive = activeHistoryId && item.id === activeHistoryId;
                  const timeText = formatDistanceToNow(new Date(item.timestamp), { addSuffix: true, locale: zhCN });
                  return (
                    <div
                      key={item.id}
                      className={cn(
                        "rounded-lg border p-3 space-y-2 transition-colors",
                        isActive
                          ? "border-purple-400/60 bg-purple-50/60"
                          : "border-border/60 bg-background hover:bg-accent/40"
                      )}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
                          {item.kind === 'requirement' ? '基础需求' : '修改记录'}
                        </span>
                        {isActive ? (
                          <Badge variant="secondary" className="text-[10px] bg-purple-100 text-purple-700">
                            处理中
                          </Badge>
                        ) : (
                          <span className="text-[10px] text-muted-foreground">{timeText}</span>
                        )}
                      </div>
                      <div className="text-xs text-foreground/90 line-clamp-3 whitespace-pre-wrap">
                        {item.content}
                      </div>
                      {!isActive && (
                        <div className="text-[10px] text-muted-foreground">{timeText}</div>
                      )}
                    </div>
                  );
                })
              )}
            </div>
          </ScrollArea>
        </div>
      )}

      {/* 右侧对话与日志 */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Header */}
        <div className="p-4 border-b flex items-center justify-between bg-muted/20">
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              className={cn("h-6 w-6 -ml-2 mr-1", showHistory && "bg-muted")}
              onClick={() => setShowHistory(!showHistory)}
              title={showHistory ? "收起历史记录" : "展开历史记录"}
            >
              <PanelLeft className="w-4 h-4 text-muted-foreground" />
            </Button>
            <Sparkles className="w-4 h-4 text-purple-600" />
            <span className="font-medium text-sm">{t('ui.ai_thinking')}</span>
          </div>
          {isGenerating && (
            <Badge variant="secondary" className="text-xs animate-pulse bg-purple-100 text-purple-700">
              {t('ui.executing')}
            </Badge>
          )}
        </div>

        {/* Content Area (Chat & Logs) */}
        <ScrollArea className="flex-1 p-4">
          <div className="space-y-6">
            {historyItems.length === 0 && (
              <div className="text-xs text-muted-foreground text-center py-6">
                请输入修改建议以开始对话
              </div>
            )}

            {historyItems.map((item) => (
              <div key={item.id} className="flex gap-3">
                <Avatar className="w-8 h-8 border">
                  <AvatarFallback className="bg-muted text-xs">U</AvatarFallback>
                </Avatar>
                <div className="space-y-1">
                  <div className="text-xs font-medium text-muted-foreground">
                    {item.kind === 'requirement' ? '需求说明' : t('ui.user')}
                  </div>
                  <div className="text-sm bg-muted/30 p-3 rounded-lg rounded-tl-none break-words whitespace-pre-wrap">
                    {item.content}
                  </div>
                </div>
              </div>
            ))}

            {/* 系统日志流 (模拟成对话气泡) */}
            <div className="flex gap-3">
              <Avatar className="w-8 h-8 border bg-gradient-to-br from-purple-500 to-indigo-600">
                <AvatarFallback className="bg-transparent text-white text-xs font-semibold">AI</AvatarFallback>
              </Avatar>
              <div className="space-y-1 flex-1 min-w-0">
                <div className="text-xs font-medium text-muted-foreground">{t('ui.ai_assistant')}</div>
                <div className="space-y-2">
                  {logs.length === 0 && (
                    <div className="text-xs text-muted-foreground px-2">暂无执行日志</div>
                  )}
                  {logs.map((log, index) => {
                    // 简单解析日志类型
                    const timestamp = log.match(/^\[(.*?)\]/)?.[1] || '';
                    const content = log.replace(/^\[(.*?)\]\s*/, '');
                    const isError = content.includes('❌') || content.includes('Error');
                    const isSuccess = content.includes('✅') || content.includes('🎉') || content.includes('🎯');
                    const isProcess = !isError && !isSuccess;
                    const contentParts = splitThinkBlocks(content);

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
                        <div className="space-y-2 whitespace-pre-wrap">
                          {contentParts.map((part, partIndex) => {
                            if (part.type === 'think') {
                              return (
                                <details
                                  key={`${index}-think-${partIndex}`}
                                  className="rounded-md border border-purple-200/60 bg-purple-50/70 px-2 py-1"
                                >
                                  <summary className="cursor-pointer text-[11px] text-purple-700">
                                    思考过程（点击展开）
                                  </summary>
                                  <pre className="mt-1 text-[11px] leading-relaxed text-purple-800 whitespace-pre-wrap">
                                    {part.content}
                                  </pre>
                                </details>
                              );
                            }
                            return (
                              <span key={`${index}-text-${partIndex}`}>
                                {part.content}
                              </span>
                            );
                          })}
                        </div>
                      </div>
                    );
                  })}
                  {isGenerating && (
                    <div className="flex items-center gap-2 text-xs text-muted-foreground px-2">
                      <Loader2 className="w-3 h-3 animate-spin" />
                      <span>{t('ui.thinking_next')}</span>
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
              placeholder={t('ui.input_suggestion')}
              className="w-full min-h-[80px] p-3 pr-12 text-sm bg-muted/30 rounded-lg border focus:outline-none focus:ring-2 focus:ring-purple-500/20 resize-none"
              disabled={isGenerating}
            />
          <Button
            size="icon"
            className="absolute right-2 bottom-2 h-8 w-8 rounded-full"
            onClick={() => handleSubmit()}
            disabled={!input.trim() || isGenerating}
            aria-label="发送消息"
          >
              {isGenerating ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
            </Button>
          </div>
          <div className="text-[10px] text-muted-foreground mt-2 text-center">
            {t('ui.enter_send')}
          </div>
        </div>
      </div>
    </div>
  );
}
