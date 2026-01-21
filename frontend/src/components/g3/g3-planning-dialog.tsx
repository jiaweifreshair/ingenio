"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Loader2, RefreshCw, Files } from "lucide-react";
import { cn } from "@/lib/utils";
import { getG3PlanningFile } from "@/lib/api/g3-planning";
import { parseTaskPlanSummary } from "@/lib/g3/planning";
import { G3PlanningFileViewer } from "@/components/g3/planning-file-viewer";
import { G3TaskDependencyGraph } from "@/components/g3/task-dependency-graph";

/**
 * G3 规划文件入口（卡片 + Dialog）
 *
 * 目的：
 * - 将后端三文件规划（task_plan/notes/context）对接到“G3 控制台”页面
 * - 在不打断日志流阅读的情况下，提供随时可查看的“外部记忆”
 */
export function G3PlanningDialog({
  jobId,
  isRunning,
  className,
}: {
  jobId: string | null;
  isRunning: boolean;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [taskPlanContent, setTaskPlanContent] = useState<string>("");

  const summary = useMemo(() => parseTaskPlanSummary(taskPlanContent), [taskPlanContent]);

  const refreshSummary = useCallback(async () => {
    if (!jobId) return;
    setLoading(true);
    setError(null);
    try {
      const resp = await getG3PlanningFile(jobId, "task_plan");
      if (!resp.success || !resp.data) {
        throw new Error(resp.error || resp.message || "拉取 task_plan 失败");
      }
      setTaskPlanContent(resp.data.content || "");
    } catch (e) {
      setError(e instanceof Error ? e.message : "拉取 task_plan 失败");
    } finally {
      setLoading(false);
    }
  }, [jobId]);

  useEffect(() => {
    if (!jobId) return;
    void refreshSummary();
  }, [jobId, refreshSummary]);

  useEffect(() => {
    if (!jobId) return;
    if (!isRunning) return;
    const timer = setInterval(refreshSummary, 5000);
    return () => clearInterval(timer);
  }, [jobId, isRunning, refreshSummary]);

  return (
    <div className={cn("flex items-center justify-between gap-3 px-4 py-3 rounded-2xl bg-white/[0.03] border border-white/[0.06]", className)}>
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <Files className="w-4 h-4 text-white/50" />
          <span className="text-sm font-semibold text-white/80">规划</span>

          {summary.progressPercent != null && (
            <Badge variant="outline" className="border-white/[0.12] text-white/60">
              {summary.progressPercent}%
            </Badge>
          )}
          {summary.currentPhase && (
            <Badge variant="outline" className="border-white/[0.12] text-white/60 max-w-[180px] truncate">
              {summary.currentPhase}
            </Badge>
          )}
        </div>
        {error && <div className="mt-1 text-xs text-red-200/80 truncate">拉取失败：{error}</div>}
        {!error && summary.statusText && (
          <div className="mt-1 text-xs text-white/45 truncate">状态：{summary.statusText}</div>
        )}
      </div>

      <div className="flex items-center gap-2 shrink-0">
        <Button
          variant="ghost"
          size="sm"
          onClick={refreshSummary}
          disabled={!jobId || loading}
          className="text-white/70 hover:text-white hover:bg-white/[0.06]"
          title="刷新摘要"
        >
          {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
        </Button>

        <Dialog
          open={open}
          onOpenChange={(v) => {
            setOpen(v);
            if (v) {
              void refreshSummary();
            }
          }}
        >
          <DialogTrigger asChild>
            <Button
              size="sm"
              disabled={!jobId}
              className="bg-white/[0.06] hover:bg-white/[0.10] text-white border border-white/[0.10]"
            >
              查看规划
            </Button>
          </DialogTrigger>

          <DialogContent className="max-w-6xl bg-[#0a0a0c] text-white border border-white/[0.10]">
            <DialogHeader>
              <DialogTitle className="flex items-center justify-between gap-3">
                <span className="flex items-center gap-2">
                  <Files className="w-5 h-5 text-white/60" />
                  规划文件（Manus 三文件模式）
                </span>
                <div className="flex items-center gap-2">
                  {summary.progressPercent != null && (
                    <Badge variant="outline" className="border-white/[0.12] text-white/60">
                      {summary.progressPercent}%
                    </Badge>
                  )}
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={refreshSummary}
                    disabled={!jobId || loading}
                    className="text-white/70 hover:text-white hover:bg-white/[0.06]"
                  >
                    {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                    <span className="ml-2 text-xs">刷新</span>
                  </Button>
                </div>
              </DialogTitle>
            </DialogHeader>

            <div className="grid grid-cols-12 gap-4 h-[72vh] min-h-0">
              <div className="col-span-8 min-h-0">
                <G3PlanningFileViewer
                  jobId={jobId}
                  enabled={open}
                  isRunning={isRunning}
                  className="h-full"
                />
              </div>

              <div className="col-span-4 flex flex-col gap-4 min-h-0">
                <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] p-4">
                  <div className="text-sm font-semibold text-white/80">摘要</div>
                  <div className="mt-3 space-y-2 text-xs text-white/60">
                    <div className="truncate">
                      当前阶段：{summary.currentPhase || "—"}
                    </div>
                    <div>进度：{summary.progressPercent != null ? `${summary.progressPercent}%` : "—"}</div>
                    <div className="truncate">状态：{summary.statusText || "—"}</div>
                    <div>已完成阶段：{summary.completedPhases.length > 0 ? summary.completedPhases.join(", ") : "—"}</div>
                  </div>
                </div>

                <G3TaskDependencyGraph completedPhases={summary.completedPhases} />
              </div>
            </div>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  );
}

