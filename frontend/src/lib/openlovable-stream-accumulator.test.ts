import { describe, expect, it } from 'vitest';

import {
  applyOpenLovableSseMessage,
  getInitialOpenLovableAccumulationState,
  getOpenLovableCodeForApply,
} from './openlovable-stream-accumulator';

describe('openlovable-stream-accumulator', () => {
  it('只拼接 type=stream 的增量文本，忽略 conversation', () => {
    const s0 = getInitialOpenLovableAccumulationState();
    const s1 = applyOpenLovableSseMessage(s0, { type: 'stream', text: '<file path="a">A' });
    const s2 = applyOpenLovableSseMessage(s1, { type: 'conversation', text: 'SHOULD_NOT_APPEND' });

    expect(s2.streamedText).toBe('<file path="a">A');
    expect(getOpenLovableCodeForApply(s2)).toBe('<file path="a">A');
  });

  it('complete.generatedCode 作为最终权威输出，并阻止后续增量污染', () => {
    const s0 = getInitialOpenLovableAccumulationState();
    const s1 = applyOpenLovableSseMessage(s0, { type: 'stream', text: 'PARTIAL' });
    const s2 = applyOpenLovableSseMessage(s1, { type: 'complete', generatedCode: 'FINAL' });
    const s3 = applyOpenLovableSseMessage(s2, { type: 'stream', text: 'EXTRA' });

    expect(s2.finalCode).toBe('FINAL');
    expect(getOpenLovableCodeForApply(s2)).toBe('FINAL');
    expect(s3.streamedText).toBe('FINAL');
    expect(getOpenLovableCodeForApply(s3)).toBe('FINAL');
  });

  it('stream 增量存在重叠时应去重合并，避免出现断裂拼接', () => {
    const s0 = getInitialOpenLovableAccumulationState();
    const s1 = applyOpenLovableSseMessage(s0, { type: 'stream', text: 'ABCDE' });
    const s2 = applyOpenLovableSseMessage(s1, { type: 'stream', text: 'CDEFG' });

    expect(s2.streamedText).toBe('ABCDEFG');
  });

  it('stream 以快照方式输出时应直接替换为最新快照', () => {
    const s0 = getInitialOpenLovableAccumulationState();
    const s1 = applyOpenLovableSseMessage(s0, { type: 'stream', text: 'ABC' });
    const s2 = applyOpenLovableSseMessage(s1, { type: 'stream', text: 'ABCDEF' });

    expect(s2.streamedText).toBe('ABCDEF');
  });
});
