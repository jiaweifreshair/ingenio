/**
 * SSE（Server-Sent Events）事件块解析工具
 *
 * 是什么：
 * - 将浏览器 fetch/readableStream 拿到的 SSE 文本流按“事件块”拆分，并从事件块中提取 data 负载。
 *
 * 做什么：
 * - 兼容标准 SSE 格式（event/id/retry + 多行 data）
 * - 支持 data 为 JSON（常见：`data: {"type":"status",...}`）
 * - 支持 data 为纯文本（少数上游会直接输出代码片段）
 *
 * 为什么：
 * - 线上出现“生成完成但代码为空”：多数是因为上游 SSE 的 data 不是单行 JSON（多行 data / 非 JSON）
 *   或字段使用 delta 等变体，导致前端解析丢失代码增量。
 */

export type ParsedSsePayload =
  | { kind: 'json'; value: unknown }
  | { kind: 'text'; value: string };

/**
 * 拆分 SSE Buffer（按空行分隔事件）
 *
 * 注意：
 * - 标准分隔为 `\n\n`，但不同实现可能使用 `\r\n\r\n`。
 */
export function splitSseBuffer(buffer: string): { events: string[]; remainder: string } {
  const parts = buffer.split(/\n\n|\r\n\r\n/);
  const remainder = parts.pop() || '';
  return { events: parts, remainder };
}

/**
 * 从一个 SSE 事件块中提取 data 行（去掉 `data:` 前缀）
 */
function extractSseDataLines(eventBlock: string): string[] {
  const lines = eventBlock.split(/\n|\r\n/);
  const dataLines: string[] = [];

  for (const line of lines) {
    if (!line.trim()) continue;
    if (line.startsWith(':')) continue; // comment
    if (!line.startsWith('data:')) continue;
    dataLines.push(line.replace(/^data:\s?/, ''));
  }

  return dataLines;
}

function tryParseJson(text: string): unknown | null {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/**
 * 解析单个 SSE 事件块，返回一个或多个 payload
 *
 * 规则：
 * - 若合并后的 data（多行 data 以 `\n` 连接）可解析为 JSON，则返回单条 json payload
 * - 否则尝试逐行解析（适配“一个事件块内包含多条 JSON”的非标准实现）
 * - 若仍无法解析 JSON，则返回一条 text payload（保留换行）
 */
export function parseSseEvent(eventBlock: string): ParsedSsePayload[] {
  const dataLines = extractSseDataLines(eventBlock);
  if (dataLines.length === 0) return [];

  const combined = dataLines.join('\n');
  const combinedTrimmed = combined.trim();
  if (!combinedTrimmed) return [];

  const combinedJson = tryParseJson(combinedTrimmed);
  if (combinedJson !== null) return [{ kind: 'json', value: combinedJson }];

  // 逐行兜底：常见于“同一个事件块写了多条 data: {...}”
  const parts: ParsedSsePayload[] = [];
  let hasAnyJson = false;
  for (const line of dataLines) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const parsed = tryParseJson(trimmed);
    if (parsed !== null) {
      hasAnyJson = true;
      parts.push({ kind: 'json', value: parsed });
    } else {
      parts.push({ kind: 'text', value: trimmed });
    }
  }

  if (hasAnyJson) return parts;
  return [{ kind: 'text', value: combined }];
}

