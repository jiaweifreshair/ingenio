"use client";

import { useMemo, useState } from "react";
import type { G3JobStatusResponse } from "@/lib/api/g3";
import { getG3ArtifactContent } from "@/lib/api/g3";
import type { G3ArtifactContent, G3ArtifactSummary } from "@/types/g3";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { CheckCircle2, FileText, Loader2, RefreshCw, XCircle } from "lucide-react";
import { FileTree } from "./file-tree";

/**
 * G3 结果展示对话框
 *
 * 目的：
 * - 在日志流之外，提供“可点击的产物列表 + 内容查看”，解决“没有输出结果展示”的体验问题。
 * - 以 jobId 为锚点拉取后端产物（含 pom.xml 等脚手架文件），便于快速定位编译失败原因。
 */
export function G3ResultDialog({
  jobId,
  jobInfo,
  artifacts,
  isLoading,
  error,
  onRefresh,
}: {
  /** 任务ID（为空时禁用入口） */
  jobId: string | null;
  /** 任务状态信息 */
  jobInfo: G3JobStatusResponse | null;
  /** 产物列表（摘要） */
  artifacts: G3ArtifactSummary[];
  /** 是否正在拉取结果 */
  isLoading: boolean;
  /** 拉取结果错误 */
  error: string | null;
  /** 刷新回调 */
  onRefresh: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [contentCache, setContentCache] = useState<Record<string, G3ArtifactContent>>({});
  const [loadingId, setLoadingId] = useState<string | null>(null);
  const [contentError, setContentError] = useState<string | null>(null);

  const selected = selectedId ? contentCache[selectedId] : null;

  const statusBadge = useMemo(() => {
    const status = jobInfo?.status;
    if (status === "COMPLETED") {
      return (
        <Badge className="bg-emerald-500/10 text-emerald-300 border border-emerald-500/20">
          <CheckCircle2 className="w-3.5 h-3.5 mr-1" />
          已完成
        </Badge>
      );
    }
    if (status === "FAILED") {
      return (
        <Badge className="bg-red-500/10 text-red-300 border border-red-500/20">
          <XCircle className="w-3.5 h-3.5 mr-1" />
          失败
        </Badge>
      );
    }
    return (
      <Badge variant="outline" className="border-white/[0.12] text-white/60">
        {status || "未知状态"}
      </Badge>
    );
  }, [jobInfo?.status]);

  async function ensureContent(artifactId: string) {
    if (!jobId) return;
    if (contentCache[artifactId]) return;

    setLoadingId(artifactId);
    setContentError(null);
    try {
      const resp = await getG3ArtifactContent(jobId, artifactId);
      if (!resp.success || !resp.data) {
        throw new Error(resp.error || resp.message || "获取产物内容失败");
      }
      setContentCache((prev) => ({ ...prev, [artifactId]: resp.data as G3ArtifactContent }));
    } catch (e) {
      setContentError(e instanceof Error ? e.message : "获取产物内容失败");
    } finally {
      setLoadingId(null);
    }
  }

  return (
    <div className="flex items-center justify-between gap-3 px-4 py-3 rounded-2xl bg-white/[0.03] border border-white/[0.06]">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <FileText className="w-4 h-4 text-white/50" />
          <span className="text-sm font-semibold text-white/80">结果</span>
          {statusBadge}
          {!!jobId && (
            <Badge variant="outline" className="border-white/[0.12] text-white/50">
              {jobId.slice(0, 8)}...
            </Badge>
          )}
          <Badge variant="outline" className="border-white/[0.12] text-white/50">
            {artifacts.length} 个文件
          </Badge>
        </div>
        {jobInfo?.lastError && (
          <div className="mt-1 text-xs text-red-200/80 truncate">
            失败原因：{jobInfo.lastError}
          </div>
        )}
        {error && (
          <div className="mt-1 text-xs text-red-200/80 truncate">拉取失败：{error}</div>
        )}
      </div>

      <div className="flex items-center gap-2 shrink-0">
        <Button
          variant="ghost"
          size="sm"
          onClick={onRefresh}
          disabled={!jobId || isLoading}
          className="text-white/70 hover:text-white hover:bg-white/[0.06]"
          title="刷新结果"
        >
          {isLoading ? (
            <Loader2 className="w-4 h-4 animate-spin" />
          ) : (
            <RefreshCw className="w-4 h-4" />
          )}
        </Button>

        <Dialog
          open={open}
          onOpenChange={(v) => {
            setOpen(v);
            if (!v) {
              setKeyword("");
              setSelectedId(null);
              setContentError(null);
              setLoadingId(null);
            }
          }}
        >
          <DialogTrigger asChild>
            <Button
              size="sm"
              disabled={!jobId}
              className="bg-white/[0.06] hover:bg-white/[0.10] text-white border border-white/[0.10]"
            >
              查看产物
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-5xl bg-[#0a0a0c] text-white border border-white/[0.10]">
            <DialogHeader>
              <DialogTitle className="flex items-center justify-between gap-3">
                <span className="flex items-center gap-2">
                  <FileText className="w-5 h-5 text-white/60" />
                  生成产物
                </span>
                <div className="flex items-center gap-2">
                  {statusBadge}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={onRefresh}
                    disabled={!jobId || isLoading}
                    className="text-white/70 hover:text-white hover:bg-white/[0.06]"
                  >
                    {isLoading ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <RefreshCw className="w-4 h-4" />
                    )}
                    <span className="ml-2 text-xs">刷新</span>
                  </Button>
                </div>
              </DialogTitle>
            </DialogHeader>

            <div className="grid grid-cols-12 gap-4 h-[70vh] min-h-0">
              {/* 左侧：IDE风格文件树 */}
              <div className="col-span-4 flex flex-col min-h-0">
                <Input
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="搜索文件路径..."
                  className="h-10 bg-white/[0.04] border-white/[0.10] text-white placeholder:text-white/30 rounded-xl"
                />
                <div className="flex-1 mt-3 rounded-xl border border-white/[0.08] bg-white/[0.02] overflow-hidden">
                  <FileTree
                    files={artifacts.map((a) => ({
                      id: a.id,
                      filePath: a.filePath,
                      hasErrors: a.hasErrors,
                      generatedBy: a.generatedBy,
                      round: a.round,
                    }))}
                    selectedId={selectedId}
                    onSelect={async (id) => {
                      setSelectedId(id);
                      await ensureContent(id);
                    }}
                    keyword={keyword}
                    className="h-full"
                  />
                </div>
              </div>

              {/* 右侧：内容预览 */}
              <div className="col-span-8 flex flex-col min-h-0 rounded-xl border border-white/[0.08] bg-white/[0.02] overflow-hidden">
                <div className="px-4 py-3 border-b border-white/[0.06] flex items-center justify-between">
                  <div className="min-w-0">
                    <div className="text-sm font-semibold text-white/80 truncate">
                      {selected?.filePath || "请选择文件"}
                    </div>
                    {selected?.hasErrors && selected.compilerOutput && (
                      <div className="mt-1 text-xs text-red-200/80 truncate">
                        已标记错误（可在下方查看输出）
                      </div>
                    )}
                  </div>
                  {loadingId && (
                    <Badge variant="outline" className="border-white/[0.12] text-white/60">
                      <Loader2 className="w-3.5 h-3.5 mr-1 animate-spin" />
                      加载中
                    </Badge>
                  )}
                </div>

                <ScrollArea className="flex-1">
                  <div className="p-4 space-y-4">
                    {contentError && (
                      <div className="text-xs text-red-200/80">获取内容失败：{contentError}</div>
                    )}

                    {selected?.hasErrors && selected.compilerOutput && (
                      <div className="rounded-lg border border-red-500/20 bg-red-500/5 p-3">
                        <div className="text-xs text-red-200/80 mb-2">编译输出（摘要）</div>
                        <pre className="text-[11px] text-red-100/80 font-mono whitespace-pre-wrap break-words">
                          {selected.compilerOutput}
                        </pre>
                      </div>
                    )}

                    {selected?.content ? (
                      <pre className="text-[11px] text-white/80 font-mono whitespace-pre-wrap break-words">
                        {selected.content}
                      </pre>
                    ) : (
                      <div className="py-16 text-center text-xs text-white/40">
                        {selectedId ? "内容为空或加载失败" : "从左侧选择一个文件查看内容"}
                      </div>
                    )}
                  </div>
                </ScrollArea>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  );
}

