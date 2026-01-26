"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";
import { getApiBaseUrl } from "@/lib/api/base-url";
import { getBenchmarkHtmlRaw, listBenchmarks, type BenchmarkSummary } from "@/lib/api/benchmarks";
import { ExternalLink, Loader2, RefreshCw } from "lucide-react";
import { TopNav } from "@/components/layout/top-nav";

const TSX_PREVIEW_ROUTE: Record<string, string> = {
  index: "/examples",
  primary: "/examples/primary",
  middle: "/examples/middle",
  high: "/examples/high",
  vocational: "/examples/vocational",
};

/**
 * Benchmarks 对照页
 *
 * 目标：
 * - 从后端加载“挑战赛标杆 HTML”（/v1/benchmarks）
 * - 同时提供“TSX 示例实现”（/examples/**）的跳转入口，便于对照验证
 */
export default function BenchmarksPage() {
  const [items, setItems] = useState<BenchmarkSummary[]>([]);
  const [selectedId, setSelectedId] = useState<string>("");
  const [html, setHtml] = useState<string>("");
  const [loadingList, setLoadingList] = useState(false);
  const [loadingHtml, setLoadingHtml] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selected = useMemo(
    () => items.find((x) => x.id === selectedId) || null,
    [items, selectedId]
  );

  const apiBase = getApiBaseUrl(); // e.g. http://localhost:8080/api
  const backendRenderedUrl = selectedId ? `${apiBase}/v1/benchmarks/${encodeURIComponent(selectedId)}` : "";
  const backendRawUrl = selectedId ? `${apiBase}/v1/benchmarks/${encodeURIComponent(selectedId)}/raw` : "";
  const tsxPreviewUrl = TSX_PREVIEW_ROUTE[selectedId] || "/examples";

  async function refreshList() {
    setLoadingList(true);
    setError(null);
    try {
      const resp = await listBenchmarks();
      if (!resp.success || !resp.data) {
        throw new Error(resp.error || resp.message || "加载基准列表失败");
      }
      setItems(resp.data);
      if (!selectedId && resp.data.length > 0) {
        setSelectedId(resp.data[0].id);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载基准列表失败");
    } finally {
      setLoadingList(false);
    }
  }

  async function refreshHtml(id: string) {
    if (!id) return;
    setLoadingHtml(true);
    setError(null);
    try {
      const text = await getBenchmarkHtmlRaw(id);
      setHtml(text);
    } catch (e) {
      setError(e instanceof Error ? e.message : "加载基准HTML失败");
      setHtml("");
    } finally {
      setLoadingHtml(false);
    }
  }

  useEffect(() => {
    void refreshList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!selectedId) return;
    void refreshHtml(selectedId);
  }, [selectedId]);

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      <TopNav />
      <div className="container mx-auto py-10 space-y-6 max-w-7xl">
        <div className="flex items-center justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-slate-900 dark:text-slate-100">
              挑战赛标杆 Benchmarks（后端加载）
            </h1>
            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
              左侧选择基准页面，右侧查看 HTML 原文，并可跳转到 TSX 示例页对照渲染效果。
            </p>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <Button
              variant="outline"
              onClick={refreshList}
              disabled={loadingList}
              className="gap-2"
            >
              {loadingList ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
              刷新列表
            </Button>
            <Button asChild className="gap-2">
              <Link href="/examples">
                TSX 示例页
                <ExternalLink className="w-4 h-4" />
              </Link>
            </Button>
          </div>
        </div>

        {error && (
          <Card className="p-4 border-red-200 bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-200 dark:border-red-900">
            {error}
          </Card>
        )}

        <div className="grid grid-cols-12 gap-6">
          <div className="col-span-12 md:col-span-4">
            <Card className="p-4">
              <div className="flex items-center justify-between mb-3">
                <div className="text-sm font-semibold text-slate-800 dark:text-slate-200">基准列表</div>
                <div className="text-xs text-slate-500">
                  {items.length > 0 ? `${items.length} 项` : "暂无"}
                </div>
              </div>

              {items.length === 0 ? (
                <div className="text-sm text-slate-500">
                  未发现基准 HTML。请在后端配置 `ingenio.benchmarks.dir`（或放置 classpath:benchmarks/）。
                </div>
              ) : (
                <div className="flex flex-col gap-2">
                  {items.map((item) => (
                    <button
                      key={item.id}
                      onClick={() => setSelectedId(item.id)}
                      className={cn(
                        "text-left rounded-lg border px-3 py-2 transition-colors",
                        selectedId === item.id
                          ? "border-blue-500 bg-blue-50 dark:bg-blue-950/30"
                          : "border-slate-200 hover:bg-slate-50 dark:border-slate-800 dark:hover:bg-slate-900"
                      )}
                    >
                      <div className="text-sm font-medium text-slate-900 dark:text-slate-100 truncate">
                        {item.title}
                      </div>
                      <div className="text-xs text-slate-500 mt-1">
                        id: {item.id} · source: {item.source}
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </Card>
          </div>

          <div className="col-span-12 md:col-span-8 space-y-4">
            <Card className="p-4">
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-slate-800 dark:text-slate-200 truncate">
                    {selected ? selected.title : "未选择"}
                  </div>
                  {selectedId && (
                    <div className="text-xs text-slate-500 mt-1 font-mono truncate">
                      {backendRawUrl}
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <Button
                    variant="outline"
                    onClick={() => refreshHtml(selectedId)}
                    disabled={!selectedId || loadingHtml}
                    className="gap-2"
                  >
                    {loadingHtml ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                    刷新HTML
                  </Button>
                  {selectedId && (
                    <>
                      <Button asChild variant="outline" className="gap-2">
                        <a href={backendRenderedUrl} target="_blank" rel="noreferrer">
                          后端渲染
                          <ExternalLink className="w-4 h-4" />
                        </a>
                      </Button>
                      <Button asChild className="gap-2">
                        <Link href={tsxPreviewUrl}>
                          TSX 预览
                          <ExternalLink className="w-4 h-4" />
                        </Link>
                      </Button>
                    </>
                  )}
                </div>
              </div>
            </Card>

            <Card className="p-0 overflow-hidden">
              <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
                <div className="text-sm font-semibold text-slate-800 dark:text-slate-200">HTML 原文</div>
                <div className="text-xs text-slate-500">{html ? `${html.length} chars` : "—"}</div>
              </div>

              <ScrollArea className="h-[62vh] bg-[#0a0a0c]">
                <pre className="p-4 text-xs text-slate-100 whitespace-pre-wrap break-words font-mono">
                  {loadingHtml ? "加载中..." : html || (selectedId ? "暂无内容" : "请选择左侧基准页面")}
                </pre>
              </ScrollArea>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}

