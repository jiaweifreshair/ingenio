/**
 * OpenLovable SSE 响应文本组装器
 *
 * 背景与问题：
 * - open-lovable-cn 的 SSE 会同时发送多种事件（如 stream / conversation / complete）
 * - 其中 `conversation.text` 往往是“对话可读性/聚合片段”，并非严格的增量 delta
 * - 如果把所有带 `text` 的事件都直接拼接，会导致重复、截断拼接污染，最终写入沙箱的代码出现语法错误
 *
 * 处理策略：
 * 1) 只从“增量事件”追加文本（delta）
 *    - `type=stream` 的 `text`
 *    - `type=content` 的 `content`（OpenLovable 上游常见格式）
 * 2) 一旦收到 `type=complete` 且包含 `generatedCode`，以其作为最终权威输出（覆盖流式拼接结果）
 */

export interface OpenLovableSseMessage {
  type?: string;
  text?: string;
  content?: string;
  generatedCode?: string;
  message?: string;
  error?: string;
  name?: string;
  args?: unknown;
}

export interface OpenLovableAccumulationState {
  /** 流式拼接得到的文本（仅来自“增量事件”） */
  streamedText: string;
  /** complete 事件给出的最终代码（存在时优先使用） */
  finalCode: string | null;
}

export function getInitialOpenLovableAccumulationState(): OpenLovableAccumulationState {
  return { streamedText: '', finalCode: null };
}

/**
 * 合并流式 `delta` 到已累积文本（尽量避免重复/重试造成的“代码拼接污染”）
 *
 * 说明：
 * - 上游在“截断续写/重试”场景下可能会带少量重叠内容
 * - 直接 `prev + delta` 会把重叠部分拼接两次，出现类似 `transitionconst` 这类断裂
 */
function mergeStreamDelta(prevText: string, delta: string): string {
  if (!delta) return prevText;
  if (!prevText) return delta;

  // delta 已经完整包含了 prev（快照式输出），直接使用 delta
  if (delta.startsWith(prevText)) return delta;
  // delta 完全被 prev 覆盖（重复发送），直接忽略
  if (prevText.endsWith(delta)) return prevText;
  if (delta.length >= 64 && prevText.includes(delta)) return prevText;

  // 尝试消除 suffix/prefix 重叠
  const maxOverlap = Math.min(prevText.length, delta.length, 4096);
  for (let overlap = maxOverlap; overlap > 0; overlap -= 1) {
    if (prevText.slice(-overlap) === delta.slice(0, overlap)) {
      return prevText + delta.slice(overlap);
    }
  }

  return prevText + delta;
}

/**
 * 应用单条 SSE 消息到累积状态
 */
export function applyOpenLovableSseMessage(
  prev: OpenLovableAccumulationState,
  message: OpenLovableSseMessage
): OpenLovableAccumulationState {
  // 已经拿到最终代码后，忽略后续增量，避免重复污染
  if (prev.finalCode) {
    return prev;
  }

  const isLikelyFileTagCode = (text: string) => text.includes('<file') && text.includes('</file>');

  if (message.type === 'complete') {
    if (
      typeof message.generatedCode === 'string' &&
      message.generatedCode.trim() &&
      isLikelyFileTagCode(message.generatedCode)
    ) {
      return { streamedText: message.generatedCode, finalCode: message.generatedCode };
    }

    // 兼容：个别上游版本会把最终代码放在 text/content，而 generatedCode 为空
    const completeText =
      typeof message.text === 'string' && message.text.trim()
        ? message.text
        : typeof message.content === 'string' && message.content.trim()
          ? message.content
          : '';

    if (completeText && isLikelyFileTagCode(completeText)) {
      return { streamedText: completeText, finalCode: completeText };
    }
  }

  /**
   * 仅追加“增量事件”的文本，避免 conversation 等事件重复/截断导致污染。
   *
   * 兼容说明：
   * - OpenLovable 上游在不同版本中可能使用：
   *   - type=stream, 字段 text
   *   - type=content, 字段 content
   * - 这里统一视为“流式增量”，走同一套去重合并逻辑。
   */
  if (message.type === 'stream' || message.type === 'content') {
    const delta =
      typeof message.text === 'string' && message.text
        ? message.text
        : typeof message.content === 'string' && message.content
          ? message.content
          : '';

    if (delta) {
      return { streamedText: mergeStreamDelta(prev.streamedText, delta), finalCode: null };
    }
  }

  return prev;
}

/**
 * 获取最终用于 apply 的代码文本
 */
export function getOpenLovableCodeForApply(state: OpenLovableAccumulationState): string {
  return state.finalCode ?? state.streamedText;
}
