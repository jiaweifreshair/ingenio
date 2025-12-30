/**
 * 生成 traceId（用于后端 TraceIdFilter 注入 MDC）。
 *
 * 约定：
 * - 后端读取请求头 `X-Trace-Id`；
 * - 前端每次请求生成一个 traceId，便于定位单次请求的端到端链路；
 * - 在浏览器环境优先使用 `crypto.randomUUID()`，否则降级为时间戳 + 随机数。
 */
export function generateTraceId(): string {
  if (typeof globalThis.crypto !== 'undefined' && typeof globalThis.crypto.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `trace-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

