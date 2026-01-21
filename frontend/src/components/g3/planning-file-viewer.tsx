"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import ReactMarkdown from "react-markdown";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vscDarkPlus } from "react-syntax-highlighter/dist/esm/styles/prism";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Loader2, RefreshCw, Save, Copy, Pencil, X } from "lucide-react";
import { cn } from "@/lib/utils";
import type { G3PlanningFileType, G3PlanningFilesMap } from "@/types/g3-planning";
import { getG3PlanningFilesMap, updateG3PlanningFile } from "@/lib/api/g3-planning";

const FILE_LABELS: Record<G3PlanningFileType, string> = {
  task_plan: "task_plan.md",
  notes: "notes.md",
  context: "context.md",
};

/**
 * 规划文件查看器（task_plan/notes/context）
 *
 * 特性：
 * - 默认只读 Markdown 渲染（带代码高亮）
 * - 支持用户手动编辑并保存（updatedBy=user）
 * - 支持在任务运行中自动刷新（可按需开启）
 */
export function G3PlanningFileViewer({
  jobId,
  enabled,
  isRunning,
  className,
}: {
  jobId: string | null;
  /** 是否启用拉取（用于 Dialog 打开时才拉取，减少无效请求） */
  enabled: boolean;
  isRunning: boolean;
  className?: string;
}) {
  const [active, setActive] = useState<G3PlanningFileType>("task_plan");
  const [files, setFiles] = useState<G3PlanningFilesMap | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [isEditing, setIsEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [saving, setSaving] = useState(false);

  const activeContent = useMemo(() => {
    if (!files) return "";
    return files[active]?.content || "";
  }, [files, active]);

  const refresh = useCallback(async () => {
    if (!enabled) return;
    if (!jobId) return;
    setLoading(true);
    setError(null);
    try {
      const resp = await getG3PlanningFilesMap(jobId);
      if (!resp.success || !resp.data) {
        throw new Error(resp.error || resp.message || "拉取规划文件失败");
      }
      setFiles(resp.data);
    } catch (e) {
      setError(e instanceof Error ? e.message : "拉取规划文件失败");
    } finally {
      setLoading(false);
    }
  }, [enabled, jobId]);

  useEffect(() => {
    if (!enabled) return;
    refresh();
  }, [enabled, refresh]);

  useEffect(() => {
    if (!enabled) return;
    if (!isRunning) return;
    const timer = setInterval(refresh, 3000);
    return () => clearInterval(timer);
  }, [enabled, isRunning, refresh]);

  useEffect(() => {
    if (isEditing) return;
    setDraft(activeContent);
  }, [activeContent, isEditing]);

  async function handleSave() {
    if (!jobId) return;
    setSaving(true);
    setError(null);
    try {
      const resp = await updateG3PlanningFile(jobId, active, draft);
      if (!resp.success || !resp.data) {
        throw new Error(resp.error || resp.message || "保存失败");
      }
      setFiles((prev) => ({ ...(prev || {}), [active]: resp.data }));
      setIsEditing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  }

  function handleCopy() {
    const text = isEditing ? draft : activeContent;
    if (!text) return;
    void navigator.clipboard?.writeText(text);
  }

  return (
    <div className={cn("rounded-xl border border-white/[0.08] bg-white/[0.02] overflow-hidden", className)}>
      <div className="px-4 py-3 border-b border-white/[0.06] flex items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-white/80">规划文件</span>
            {loading && (
              <Badge variant="outline" className="border-white/[0.12] text-white/60">
                <Loader2 className="w-3.5 h-3.5 mr-1 animate-spin" />
                拉取中
              </Badge>
            )}
          </div>
          {error && <div className="mt-1 text-xs text-red-200/80 truncate">错误：{error}</div>}
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <Button
            variant="ghost"
            size="sm"
            onClick={handleCopy}
            disabled={!enabled || (!activeContent && !draft)}
            className="text-white/70 hover:text-white hover:bg-white/[0.06]"
            title="复制内容"
          >
            <Copy className="w-4 h-4" />
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={refresh}
            disabled={!enabled || !jobId || loading}
            className="text-white/70 hover:text-white hover:bg-white/[0.06]"
            title="刷新"
          >
            <RefreshCw className="w-4 h-4" />
          </Button>

          {!isEditing ? (
            <Button
              size="sm"
              onClick={() => setIsEditing(true)}
              disabled={!enabled || !jobId}
              className="bg-white/[0.06] hover:bg-white/[0.10] text-white border border-white/[0.10]"
              title="编辑并保存（updatedBy=user）"
            >
              <Pencil className="w-4 h-4 mr-2" />
              编辑
            </Button>
          ) : (
            <div className="flex items-center gap-2">
              <Button
                size="sm"
                onClick={handleSave}
                disabled={!enabled || !jobId || saving}
                className="bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-100 border border-emerald-500/30"
              >
                {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                保存
              </Button>
              <Button
                size="sm"
                variant="ghost"
                onClick={() => {
                  setIsEditing(false);
                  setDraft(activeContent);
                }}
                className="text-white/70 hover:text-white hover:bg-white/[0.06]"
                title="取消编辑"
              >
                <X className="w-4 h-4" />
              </Button>
            </div>
          )}
        </div>
      </div>

      <div className="px-4 pt-3">
        <Tabs
          value={active}
          onValueChange={(v) => {
            setActive(v as G3PlanningFileType);
            setIsEditing(false);
          }}
        >
          <TabsList className="bg-white/[0.03] border border-white/[0.06]">
            <TabsTrigger value="task_plan" className="data-[state=active]:bg-white/[0.06] data-[state=active]:text-white text-white/60">
              {FILE_LABELS.task_plan}
            </TabsTrigger>
            <TabsTrigger value="notes" className="data-[state=active]:bg-white/[0.06] data-[state=active]:text-white text-white/60">
              {FILE_LABELS.notes}
            </TabsTrigger>
            <TabsTrigger value="context" className="data-[state=active]:bg-white/[0.06] data-[state=active]:text-white text-white/60">
              {FILE_LABELS.context}
            </TabsTrigger>
          </TabsList>

          <TabsContent value={active} className="mt-3">
            <ScrollArea className="h-[52vh] rounded-xl border border-white/[0.08] bg-[#0a0a0c]">
              <div className="p-4">
                {isEditing ? (
                  <Textarea
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    className="min-h-[48vh] bg-white/[0.03] border-white/[0.10] text-white placeholder:text-white/30 font-mono text-xs"
                  />
                ) : (
                  <div className="prose prose-sm dark:prose-invert max-w-none">
                    <ReactMarkdown
                      components={{
                        code({ inline, className, children, ...props }: { inline?: boolean; className?: string; children?: React.ReactNode }) {
                          const match = /language-(\w+)/.exec(className || "");
                          return !inline && match ? (
                            <SyntaxHighlighter style={vscDarkPlus} language={match[1]} PreTag="div" {...props}>
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
                      {activeContent || "*暂无内容*"}
                    </ReactMarkdown>
                  </div>
                )}
              </div>
            </ScrollArea>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}

