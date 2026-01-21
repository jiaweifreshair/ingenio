/**
 * Benchmarks API（挑战赛标杆页面）
 *
 * 说明：
 * - 列表接口为 JSON（走统一 client.ts）
 * - HTML 原文接口为 text/plain（需要直接 fetch，避免 client.ts 将非 JSON 视为错误）
 */

import { get } from "./client";

export interface BenchmarkSummary {
  id: string;
  title: string;
  source: "filesystem" | "classpath" | string;
}

export async function listBenchmarks() {
  return get<BenchmarkSummary[]>("/v1/benchmarks");
}

export async function getBenchmarkHtmlRaw(id: string) {
  const base = (await import("./base-url")).getApiBaseUrl();
  const token = (await import("@/lib/auth/token")).getToken();
  const url = `${base}/v1/benchmarks/${encodeURIComponent(id)}/raw`;

  const headers: Record<string, string> = {
    Accept: "text/plain",
  };
  if (token) headers["Authorization"] = token;

  const resp = await fetch(url, { headers, credentials: "include" });
  if (!resp.ok) throw new Error(`获取基准HTML失败: HTTP ${resp.status}`);
  return resp.text();
}

