"use client";

import { useMemo, useState } from "react";
import type { G3ArtifactContent, G3ArtifactSummary, G3Contract } from "@/types/g3";
import { getG3ArtifactContent, getG3Contract } from "@/lib/api/g3";
import { cn } from "@/lib/utils";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Button } from "@/components/ui/button";
import { FileTree } from "./file-tree";
import { FileText, Loader2, RefreshCw, Copy, AlertTriangle, CheckCircle2, ClipboardCopy } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

/**
 * G3 右侧预览面板（Atoms 右栏）
 *
 * 说明：
 * - 复用现有后端产物接口，将“查看产物”从弹窗升级为常驻 IDE 视图。
 * - 契约（OpenAPI/Schema）作为同级 Tab 展示，为后续 Schema View/ER 图打基础。
 */
export function G3PreviewPanel({
  jobId,
  artifacts,
  isRunning,
  onRefreshArtifacts,
  className,
}: {
  jobId: string | null;
  artifacts: G3ArtifactSummary[];
  isRunning: boolean;
  onRefreshArtifacts: () => void;
  className?: string;
}) {
  const [keyword, setKeyword] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [contentCache, setContentCache] = useState<Record<string, G3ArtifactContent>>({});
  const [loadingId, setLoadingId] = useState<string | null>(null);
  const [contentError, setContentError] = useState<string | null>(null);

  const [contract, setContract] = useState<G3Contract | null>(null);
  const [contractLoading, setContractLoading] = useState(false);
  const [contractError, setContractError] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const { toast } = useToast();

  const selected = selectedId ? contentCache[selectedId] : null;

  const hasErrorsCount = useMemo(
    () => artifacts.filter((a) => a.hasErrors).length,
    [artifacts]
  );

  /** 复制任务ID到剪贴板 */
  const handleCopyJobId = async () => {
    if (!jobId) return;
    try {
      await navigator.clipboard.writeText(jobId);
      toast({
        title: "已复制",
        description: `任务ID: ${jobId.slice(0, 8)}...`,
      });
    } catch {
      toast({
        title: "复制失败",
        description: "请手动复制任务ID",
        variant: "destructive",
      });
    }
  };

  /** 带loading状态的刷新 */
  const handleRefresh = async () => {
    if (!jobId || isRunning || isRefreshing) return;
    setIsRefreshing(true);
    try {
      await onRefreshArtifacts();
    } finally {
      // 延迟关闭loading以确保动画可见
      setTimeout(() => setIsRefreshing(false), 500);
    }
  };

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

  async function refreshContract() {
    if (!jobId) return;
    setContractLoading(true);
    setContractError(null);
    try {
      const resp = await getG3Contract(jobId);
      if (!resp.success || !resp.data) {
        throw new Error(resp.error || resp.message || "获取契约失败");
      }
      setContract(resp.data as G3Contract);
    } catch (e) {
      setContractError(e instanceof Error ? e.message : "获取契约失败");
    } finally {
      setContractLoading(false);
    }
  }

  const headerBadges = (
    <div className="flex items-center gap-2 min-w-0">
      {/* 文件统计 - 更直观的展示 */}
      <div className="flex items-center gap-1.5 px-2.5 py-1 bg-white/[0.04] rounded-lg border border-white/[0.08]">
        {artifacts.length > 0 ? (
          <>
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
            <span className="text-xs text-white/70">
              已生成 <span className="font-medium text-white/90">{artifacts.length}</span> 个文件
            </span>
          </>
        ) : (
          <span className="text-xs text-white/50">暂无文件</span>
        )}
      </div>

      {/* 错误统计 - 仅有错误时显示 */}
      {hasErrorsCount > 0 && (
        <div className="flex items-center gap-1.5 px-2.5 py-1 bg-red-500/10 rounded-lg border border-red-500/20">
          <AlertTriangle className="w-3.5 h-3.5 text-red-400" />
          <span className="text-xs text-red-300">
            {hasErrorsCount} 个错误
          </span>
        </div>
      )}

      {/* 运行中状态指示 */}
      {isRunning && (
        <div className="flex items-center gap-1.5 px-2 py-1">
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-500"></span>
          </span>
          <span className="text-[11px] text-blue-400 font-medium">生成中...</span>
        </div>
      )}
    </div>
  );

  /** 可点击复制的任务ID */
  const jobIdChip = jobId && (
    <Button
      variant="ghost"
      size="sm"
      onClick={handleCopyJobId}
      className="h-7 px-2.5 bg-white/[0.04] hover:bg-white/[0.08] border border-white/[0.08] rounded-lg text-xs gap-1.5 group"
      title="点击复制完整任务ID"
    >
      <ClipboardCopy className="w-3 h-3 text-white/40 group-hover:text-white/60 transition-colors" />
      <span className="text-white/60 font-mono group-hover:text-white/80 transition-colors">
        {jobId.slice(0, 8)}...
      </span>
    </Button>
  );

  return (
    <div
      className={cn(
        "flex flex-col min-h-0 rounded-2xl bg-white/[0.02] border border-white/[0.06] overflow-hidden",
        className
      )}
    >
      <div className="px-4 py-3 border-b border-white/[0.06]">
        {/* 第一行：标题 + 任务ID + 刷新按钮 */}
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 min-w-0">
            <FileText className="w-4 h-4 text-blue-400" />
            <span className="text-sm font-semibold text-white/80">产物预览</span>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {jobIdChip}
            <Button
              variant="ghost"
              size="sm"
              onClick={handleRefresh}
              disabled={!jobId || isRunning || isRefreshing}
              className="h-7 px-3 bg-white/[0.04] hover:bg-white/[0.08] border border-white/[0.08] rounded-lg text-xs gap-1.5"
              title="刷新产物列表"
            >
              <RefreshCw className={cn("w-3.5 h-3.5", isRefreshing && "animate-spin")} />
              <span className="text-white/70">刷新</span>
            </Button>
          </div>
        </div>

        {/* 第二行：文件统计 + 错误统计 + 运行状态 */}
        <div className="mt-2">
          {headerBadges}
        </div>
      </div>

      <Tabs defaultValue="files" className="flex-1 min-h-0 flex flex-col">
        <div className="px-3 pt-3 shrink-0">
          <TabsList className="w-full bg-black/20 border-b border-white/[0.06] p-0.5 h-9 rounded-lg">
            <TabsTrigger
              value="files"
              className="flex-1 h-full rounded-md data-[state=active]:bg-white/[0.08] data-[state=active]:text-white data-[state=active]:shadow-none transition-all"
            >
              文件
            </TabsTrigger>
            <TabsTrigger
              value="contract"
              className="flex-1 h-full rounded-md data-[state=active]:bg-white/[0.08] data-[state=active]:text-white data-[state=active]:shadow-none transition-all"
              onClick={() => {
                if (!contract && jobId) void refreshContract();
              }}
            >
              契约
            </TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="files" className="flex-1 min-h-0 p-3 pt-3 overflow-hidden">
          <div
            className={cn(
              "flex gap-3 h-full min-h-0 transition-all duration-500 ease-in-out",
              isRunning ? "flex-col" : "flex-row"
            )}
          >
            {/* 文件树 */}
            <div
              className={cn(
                "flex flex-col min-h-0 rounded-xl border border-white/[0.08] bg-white/[0.02] overflow-hidden transition-all duration-500 ease-in-out",
                isRunning ? "w-full h-[35%]" : "w-[30%] h-full"
              )}
            >
              <div className="p-2 border-b border-white/[0.06] shrink-0">
                <Input
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="搜索文件..."
                  className="h-8 bg-white/[0.04] border-white/[0.10] text-white placeholder:text-white/30 rounded-lg text-xs"
                  disabled={!jobId}
                />
              </div>
              <div className="flex-1 min-h-0 overflow-hidden">
                <ScrollArea className="h-full">
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
                </ScrollArea>
              </div>
            </div>

            {/* 内容预览 */}
            <div
              className={cn(
                "flex flex-col min-h-0 rounded-xl border border-white/[0.08] bg-white/[0.02] overflow-hidden transition-all duration-500 ease-in-out",
                isRunning ? "w-full h-[65%]" : "w-[70%] h-full"
              )}
            >
              <div className="px-3 py-2 border-b border-white/[0.06] flex items-center justify-between gap-2 h-10 shrink-0">
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-semibold text-white/80 truncate">
                    {selected?.filePath || "请选择文件"}
                  </div>
                  {/* 省略即时错误显示，避免头部太高 */}
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-6 w-6 text-white/70 hover:text-white hover:bg-white/[0.06]"
                    disabled={!selected?.content}
                    onClick={async () => {
                      if (!selected?.content) return;
                      await navigator.clipboard.writeText(selected.content);
                    }}
                    title="复制"
                  >
                    <Copy className="w-3.5 h-3.5" />
                  </Button>
                  {loadingId && (
                    <Loader2 className="w-3.5 h-3.5 animate-spin text-white/60" />
                  )}
                </div>
              </div>

              {/* 错误提示条 (Optional, added below header if exists) */}
              {(selected?.compilerOutput || contentError) && (
                 <div className="px-3 py-1 bg-red-500/10 border-b border-red-500/10 text-[10px] text-red-200/90 truncate shrink-0">
                    {contentError || `编译错误：${selected?.compilerOutput}`}
                 </div>
              )}

              <ScrollArea className="flex-1 min-h-0 bg-[#0c0c0e]">
                <pre className="p-3 text-[11px] leading-relaxed text-white/80 font-mono whitespace-pre-wrap break-words">
                  {selected?.content || (jobId ? "暂无内容" : "请先启动任务")}
                </pre>
              </ScrollArea>
            </div>
          </div>
        </TabsContent>

        <TabsContent value="contract" className="flex-1 min-h-0 p-3 pt-3 flex flex-col overflow-hidden">
          <div className="flex items-center justify-between gap-2 mb-3 shrink-0">
            <div className="text-xs text-white/60">
              {contractError ? `加载失败：${contractError}` : "OpenAPI + Schema（后续可渲染 ER 图）"}
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => void refreshContract()}
              disabled={!jobId || contractLoading}
              className="text-white/70 hover:text-white hover:bg-white/[0.06]"
            >
              {contractLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
              <span className="ml-2 text-xs">刷新</span>
            </Button>
          </div>

          <div className="grid grid-cols-12 gap-3 flex-1 min-h-0">
            <div className="col-span-6 rounded-xl border border-white/[0.08] bg-white/[0.02] overflow-hidden flex flex-col min-h-0">
              <div className="px-3 py-2 border-b border-white/[0.06] text-xs font-semibold text-white/80 shrink-0">
                OpenAPI YAML
              </div>
              <ScrollArea className="flex-1 min-h-0">
                <pre className="p-3 text-[11px] leading-relaxed text-white/80 font-mono whitespace-pre-wrap break-words">
                  {contract?.openApiYaml || (jobId ? "暂无 OpenAPI" : "请先启动任务")}
                </pre>
              </ScrollArea>
            </div>
            <div className="col-span-6 rounded-xl border border-white/[0.08] bg-white/[0.02] overflow-hidden flex flex-col min-h-0">
              <div className="px-3 py-2 border-b border-white/[0.06] text-xs font-semibold text-white/80 shrink-0">
                DB Schema SQL
              </div>
              <ScrollArea className="flex-1 min-h-0">
                <pre className="p-3 text-[11px] leading-relaxed text-white/80 font-mono whitespace-pre-wrap break-words">
                  {contract?.dbSchemaSql || (jobId ? "暂无 Schema" : "请先启动任务")}
                </pre>
              </ScrollArea>
            </div>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}

