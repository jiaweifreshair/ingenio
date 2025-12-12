/**
 * Claude Code 补丁：禁用 context_management 特性
 *
 * 背景：
 * - context_management 是 Anthropic API 的 Beta 特性
 * - 未开通该特性的账号会返回 400 错误
 *
 * 功能：
 * - 拦截所有 fetch 请求
 * - 自动移除请求体中的 context_management 字段
 * - 自动过滤 betas 数组中的 "context-management-2025-06-27"
 *
 * 使用方法：
 * - 通过 NODE_OPTIONS 环境变量注入：NODE_OPTIONS="--require ./disable-context-management.js"
 * - 或使用 claude-multi.sh 包装脚本自动注入
 *
 * @author Ingenio Team
 * @since 2025-01-13
 */

const originalFetch = globalThis.fetch;

/**
 * 移除请求体中的 context_management 相关字段
 *
 * @param {RequestInit} init - fetch 请求的初始化选项
 * @returns {RequestInit} 处理后的请求选项
 */
function stripContextManagement(init) {
  // 如果没有请求体或请求体不是字符串，直接返回
  if (!init || typeof init.body !== "string") {
    return init;
  }

  // 尝试解析 JSON 请求体
  let parsed;
  try {
    parsed = JSON.parse(init.body);
  } catch {
    // 解析失败（可能不是 JSON），直接返回
    return init;
  }

  // 如果解析结果不是对象，直接返回
  if (!parsed || typeof parsed !== "object") {
    return init;
  }

  // 移除 context_management 字段
  if (parsed.context_management) {
    delete parsed.context_management;
    // console.debug('[Patch] 已移除 context_management 字段');
  }

  // 过滤 betas 数组中的 context-management 特性
  if (Array.isArray(parsed.betas)) {
    const originalLength = parsed.betas.length;
    parsed.betas = parsed.betas.filter(
      (beta) => beta !== "context-management-2025-06-27"
    );

    // if (parsed.betas.length < originalLength) {
    //   console.debug('[Patch] 已过滤 context-management beta 特性');
    // }
  }

  // 返回处理后的请求选项
  return {
    ...init,
    body: JSON.stringify(parsed),
  };
}

/**
 * 补丁后的 fetch 函数
 *
 * @param {RequestInfo | URL} resource - 请求的 URL
 * @param {RequestInit} init - 请求的初始化选项
 * @returns {Promise<Response>} fetch 响应
 */
globalThis.fetch = async function patchedFetch(resource, init) {
  // 处理请求选项（移除 context_management）
  const nextInit = stripContextManagement(init);

  // 调用原始 fetch
  return originalFetch.call(this, resource, nextInit);
};

// 调试信息（可选）
// console.log('[Patch] disable-context-management.js 已加载');
