"use client";

import React, { useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vscDarkPlus } from "react-syntax-highlighter/dist/esm/styles/prism";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Loader2,
  MessageSquare,
  CheckCircle2,
  Play,
  Brain,
  ChevronDown,
} from "lucide-react";

interface PlanDisplayProps {
  planContent: string;
  onConfirm: () => void;
  /** @deprecated 左侧输入框已移除，修改建议通过右侧对话框提交 */
  onModify?: (newRequirement: string) => void;
  isGenerating?: boolean;
  /** 推理内容（DeepSeek R1 等推理模型的思考过程） */
  reasoningContent?: string;
  /** 是否正在推理中 */
  isReasoning?: boolean;
}

export function PlanDisplay({
  planContent,
  onConfirm,
  isGenerating = false,
  reasoningContent,
  isReasoning,
}: PlanDisplayProps) {
  const [isReasoningCollapsed, setIsReasoningCollapsed] = useState(false);
  const reasoningRef = useRef<HTMLDivElement>(null);

  // 自动滚动推理内容到底部 - 已禁用，允许用户自由滚动
  // useEffect(() => {
  //   if (reasoningRef.current && reasoningContent && !isReasoningCollapsed) {
  //     reasoningRef.current.scrollTop = reasoningRef.current.scrollHeight;
  //   }
  // }, [reasoningContent, isReasoningCollapsed]);

  return (
    <div className="flex h-full flex-col space-y-4 duration-500 animate-in fade-in">
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-2 bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-2xl font-bold text-transparent">
          <CheckCircle2 className="h-6 w-6 text-green-500" />
          首席架构师的实施蓝图
        </h2>
        <span className="rounded-full bg-secondary px-3 py-1 text-sm text-muted-foreground">
          步骤 6/6 已完成
        </span>
      </div>

      <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border bg-card/50 backdrop-blur-sm">
        {/* 🧠 推理过程展示区（DeepSeek R1 等推理模型） */}
        {(isReasoning || reasoningContent) && (
          <div className="border-b border-purple-200 bg-gradient-to-r from-purple-50 to-indigo-50 px-4 py-3 dark:border-purple-800 dark:from-purple-950/30 dark:to-indigo-950/30">
            {/* 推理状态头部 */}
            <div
              className="flex cursor-pointer items-center justify-between"
              onClick={() => setIsReasoningCollapsed(!isReasoningCollapsed)}
            >
              <div className="flex items-center gap-2">
                {isReasoning ? (
                  <>
                    <div className="relative">
                      <Loader2 className="h-5 w-5 animate-spin text-purple-500" />
                    </div>
                    <span className="text-sm font-medium text-purple-700 dark:text-purple-300">
                      <Brain className="mr-1 inline h-4 w-4" />
                      首席架构师正在深度思考...
                    </span>
                    <span className="animate-pulse text-xs text-purple-500">
                      {reasoningContent
                        ? `已思考 ${reasoningContent.length} 字`
                        : "分析需求中"}
                    </span>
                  </>
                ) : (
                  <>
                    <CheckCircle2 className="h-5 w-5 text-purple-600" />
                    <span className="text-sm font-medium text-purple-700 dark:text-purple-300">
                      深度思考完成
                    </span>
                    <span className="text-xs text-purple-500">
                      共 {reasoningContent?.length || 0} 字
                    </span>
                  </>
                )}
              </div>
              <button className="text-purple-500 transition-colors hover:text-purple-700">
                <ChevronDown
                  className={`h-5 w-5 transition-transform duration-200 ${isReasoningCollapsed ? "" : "rotate-180"}`}
                />
              </button>
            </div>

            {/* 推理内容展示 */}
            {!isReasoningCollapsed && reasoningContent && (
              <div
                ref={reasoningRef}
                className="mt-3 max-h-48 overflow-y-auto rounded-lg border border-purple-700 bg-purple-950 p-4"
              >
                <pre className="whitespace-pre-wrap font-mono text-xs leading-relaxed text-purple-300">
                  {reasoningContent}
                  {isReasoning && (
                    <span className="ml-0.5 inline-block h-3 w-2 animate-pulse bg-purple-400" />
                  )}
                </pre>
              </div>
            )}

            {/* 推理中但无内容时的占位 */}
            {!isReasoningCollapsed && isReasoning && !reasoningContent && (
              <div className="mt-3 rounded-lg border border-purple-700 bg-purple-950 p-4">
                <div className="flex items-center gap-2 text-purple-400">
                  <div className="flex space-x-1">
                    <div
                      className="h-2 w-2 animate-bounce rounded-full bg-purple-400"
                      style={{ animationDelay: "0ms" }}
                    />
                    <div
                      className="h-2 w-2 animate-bounce rounded-full bg-purple-400"
                      style={{ animationDelay: "150ms" }}
                    />
                    <div
                      className="h-2 w-2 animate-bounce rounded-full bg-purple-400"
                      style={{ animationDelay: "300ms" }}
                    />
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
                code({
                  inline,
                  className,
                  children,
                  ...props
                }: {
                  inline?: boolean;
                  className?: string;
                  children?: React.ReactNode;
                }) {
                  const match = /language-(\w+)/.exec(className || "");
                  return !inline && match ? (
                    <SyntaxHighlighter
                      style={vscDarkPlus}
                      language={match[1]}
                      PreTag="div"
                      {...props}
                    >
                      {String(children).replace(/\n$/, "")}
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

        {/* 操作区 */}
        <div className="space-y-4 border-t bg-background/50 p-4 backdrop-blur-md">
          {/* 修改建议提示（移除输入框，引导用户使用右侧对话框） */}
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <MessageSquare className="h-4 w-4" />
            <span>如需调整方案，请使用右侧对话框提交修改建议</span>
          </div>

          <div className="flex justify-end">
            <Button
              size="lg"
              className="w-full bg-gradient-to-r from-blue-600 to-purple-600 text-white shadow-lg transition-all duration-300 hover:from-blue-700 hover:to-purple-700 hover:shadow-xl sm:w-auto"
              onClick={onConfirm}
              disabled={isGenerating}
            >
              {isGenerating ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  正在生成代码...
                </>
              ) : (
                <>
                  <Play className="mr-2 h-4 w-4" />
                  确认并生成原型
                </>
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
