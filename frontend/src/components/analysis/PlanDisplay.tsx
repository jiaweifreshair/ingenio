'use client';

import React, { useState, useRef, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Loader2, MessageSquare, CheckCircle2, Play, Brain, ChevronDown } from 'lucide-react';

interface PlanDisplayProps {
  planContent: string;
  onConfirm: () => void;
  onModify: (newRequirement: string) => void;
  isGenerating?: boolean;
  /** 推理内容（DeepSeek R1 等推理模型的思考过程） */
  reasoningContent?: string;
  /** 是否正在推理中 */
  isReasoning?: boolean;
}

export function PlanDisplay({
  planContent,
  onConfirm,
  onModify,
  isGenerating = false,
  reasoningContent,
  isReasoning
}: PlanDisplayProps) {
  const [modification, setModification] = useState('');
  const [isModifying, setIsModifying] = useState(false);
  const [isReasoningCollapsed, setIsReasoningCollapsed] = useState(false);
  const reasoningRef = useRef<HTMLDivElement>(null);

  // 自动滚动推理内容到底部
  useEffect(() => {
    if (reasoningRef.current && reasoningContent && !isReasoningCollapsed) {
      reasoningRef.current.scrollTop = reasoningRef.current.scrollHeight;
    }
  }, [reasoningContent, isReasoningCollapsed]);

  const handleModifySubmit = () => {
    if (!modification.trim()) return;
    setIsModifying(true);
    onModify(modification);
    // Note: Parent should handle the re-generation or update logic
    setModification('');
    setIsModifying(false); // Reset after submit (or keep loading if async)
  };

  return (
    <div className="flex flex-col h-full space-y-4 animate-in fade-in duration-500">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent flex items-center gap-2">
          <CheckCircle2 className="w-6 h-6 text-green-500" />
          Technical Blueprint
        </h2>
        <span className="text-sm text-muted-foreground px-3 py-1 bg-secondary rounded-full">
          Step 6/6 Completed
        </span>
      </div>

      <div className="flex-1 min-h-0 border rounded-xl bg-card/50 backdrop-blur-sm overflow-hidden flex flex-col">
        {/* 🧠 推理过程展示区（DeepSeek R1 等推理模型） */}
        {(isReasoning || reasoningContent) && (
          <div className="px-4 py-3 bg-gradient-to-r from-purple-50 to-indigo-50 dark:from-purple-950/30 dark:to-indigo-950/30 border-b border-purple-200 dark:border-purple-800">
            {/* 推理状态头部 */}
            <div
              className="flex items-center justify-between cursor-pointer"
              onClick={() => setIsReasoningCollapsed(!isReasoningCollapsed)}
            >
              <div className="flex items-center gap-2">
                {isReasoning ? (
                  <>
                    <div className="relative">
                      <Loader2 className="w-5 h-5 text-purple-500 animate-spin" />
                    </div>
                    <span className="text-sm font-medium text-purple-700 dark:text-purple-300">
                      <Brain className="w-4 h-4 inline mr-1" />
                      AI 正在深度思考...
                    </span>
                    <span className="text-xs text-purple-500 animate-pulse">
                      {reasoningContent ? `已思考 ${reasoningContent.length} 字` : '分析需求中'}
                    </span>
                  </>
                ) : (
                  <>
                    <CheckCircle2 className="w-5 h-5 text-purple-600" />
                    <span className="text-sm font-medium text-purple-700 dark:text-purple-300">
                      深度思考完成
                    </span>
                    <span className="text-xs text-purple-500">
                      共 {reasoningContent?.length || 0} 字
                    </span>
                  </>
                )}
              </div>
              <button className="text-purple-500 hover:text-purple-700 transition-colors">
                <ChevronDown
                  className={`w-5 h-5 transition-transform duration-200 ${isReasoningCollapsed ? '' : 'rotate-180'}`}
                />
              </button>
            </div>

            {/* 推理内容展示 */}
            {!isReasoningCollapsed && reasoningContent && (
              <div
                ref={reasoningRef}
                className="mt-3 bg-purple-950 border border-purple-700 rounded-lg p-4 max-h-48 overflow-y-auto"
              >
                <pre className="text-xs font-mono text-purple-300 whitespace-pre-wrap leading-relaxed">
                  {reasoningContent}
                  {isReasoning && (
                    <span className="inline-block w-2 h-3 bg-purple-400 animate-pulse ml-0.5" />
                  )}
                </pre>
              </div>
            )}

            {/* 推理中但无内容时的占位 */}
            {!isReasoningCollapsed && isReasoning && !reasoningContent && (
              <div className="mt-3 bg-purple-950 border border-purple-700 rounded-lg p-4">
                <div className="flex items-center gap-2 text-purple-400">
                  <div className="flex space-x-1">
                    <div className="w-2 h-2 bg-purple-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                    <div className="w-2 h-2 bg-purple-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                    <div className="w-2 h-2 bg-purple-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                  </div>
                  <span className="text-xs">正在启动深度推理引擎...</span>
                </div>
              </div>
            )}
          </div>
        )}

        <ScrollArea className="flex-1 p-6">
          <div className="prose prose-sm dark:prose-invert max-w-none">
            <ReactMarkdown
              components={{
                code({ inline, className, children, ...props }: { inline?: boolean; className?: string; children?: React.ReactNode }) {
                  const match = /language-(\w+)/.exec(className || '');
                  return !inline && match ? (
                    <SyntaxHighlighter
                      style={vscDarkPlus}
                      language={match[1]}
                      PreTag="div"
                      {...props}
                    >
                      {String(children).replace(/\n$/, '')}
                    </SyntaxHighlighter>
                  ) : (
                    <code className={className} {...props}>
                      {children}
                    </code>
                  );
                },
              }}
            >
              {planContent}
            </ReactMarkdown>
          </div>
        </ScrollArea>

        {/* Action Footer */}
        <div className="p-4 border-t bg-background/50 backdrop-blur-md space-y-4">
          
          {/* Chat/Modify Input */}
          <div className="space-y-2">
            <div className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <MessageSquare className="w-4 h-4" />
              <span>Adjust the plan?</span>
            </div>
            <div className="flex gap-2">
              <Textarea 
                placeholder="E.g., Change the database to MongoDB, or add a dark mode toggle..."
                value={modification}
                onChange={(e) => setModification(e.target.value)}
                className="min-h-[60px] resize-none"
              />
              <Button 
                variant="outline" 
                size="icon" 
                className="h-[60px] w-[60px]"
                onClick={handleModifySubmit}
                disabled={!modification.trim() || isGenerating}
              >
                {isModifying ? <Loader2 className="w-5 h-5 animate-spin" /> : <MessageSquare className="w-5 h-5" />}
              </Button>
            </div>
          </div>

          <div className="flex justify-end pt-2">
            <Button 
              size="lg" 
              className="w-full sm:w-auto bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white shadow-lg hover:shadow-xl transition-all duration-300"
              onClick={onConfirm}
              disabled={isGenerating}
            >
              {isGenerating ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  Generating Code...
                </>
              ) : (
                <>
                  <Play className="w-4 h-4 mr-2" />
                  Confirm & Generate Prototype
                </>
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
