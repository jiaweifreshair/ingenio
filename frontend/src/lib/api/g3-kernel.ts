/**
 * G3 Kernel 运行资产 API 客户端
 *
 * 目标：
 * - 将 templates/prompts/capabilities 从后端统一加载，避免前端硬编码导致版本漂移
 * - 为后续 MetaGPT/Agent-OS 接入提供稳定的 Kernel 资产入口
 */

import { get } from "./client";

export interface G3KernelIndex {
  templates: string[];
  prompts: string[];
  capabilities: string;
}

export async function getG3KernelIndex() {
  return get<G3KernelIndex>("/v1/g3/kernel/index");
}

export async function getG3KernelTemplate(name: string) {
  // 注意：后端返回 text/plain，这里沿用 get() 走 JSON 会失败，因此直接 fetch
  const base = (await import("./base-url")).getApiBaseUrl();
  const token = (await import("@/lib/auth/token")).getToken();
  const url = `${base}/v1/g3/kernel/templates/${encodeURIComponent(name)}`;

  const headers: Record<string, string> = {
    Accept: "text/plain",
  };
  if (token) headers["Authorization"] = token;

  const resp = await fetch(url, { headers, credentials: "include" });
  if (!resp.ok) throw new Error(`获取模板失败: HTTP ${resp.status}`);
  return resp.text();
}

export async function getG3KernelPrompt(name: string) {
  const base = (await import("./base-url")).getApiBaseUrl();
  const token = (await import("@/lib/auth/token")).getToken();
  const url = `${base}/v1/g3/kernel/prompts/${encodeURIComponent(name)}`;

  const headers: Record<string, string> = {
    Accept: "text/plain",
  };
  if (token) headers["Authorization"] = token;

  const resp = await fetch(url, { headers, credentials: "include" });
  if (!resp.ok) throw new Error(`获取提示词失败: HTTP ${resp.status}`);
  return resp.text();
}

export async function getG3KernelCapabilities() {
  const base = (await import("./base-url")).getApiBaseUrl();
  const token = (await import("@/lib/auth/token")).getToken();
  const url = `${base}/v1/g3/kernel/capabilities`;

  const headers: Record<string, string> = {
    Accept: "text/plain",
  };
  if (token) headers["Authorization"] = token;

  const resp = await fetch(url, { headers, credentials: "include" });
  if (!resp.ok) throw new Error(`获取能力表失败: HTTP ${resp.status}`);
  return resp.text();
}

