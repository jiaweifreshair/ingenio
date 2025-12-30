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

  it('兼容 type=content 的增量字段 content', () => {
    const s0 = getInitialOpenLovableAccumulationState();
    const s1 = applyOpenLovableSseMessage(s0, { type: 'content', content: 'ABC' });
    const s2 = applyOpenLovableSseMessage(s1, { type: 'content', content: 'DEF' });

    expect(s2.streamedText).toBe('ABCDEF');
    expect(getOpenLovableCodeForApply(s2)).toBe('ABCDEF');
  });

  it('complete.generatedCode 作为最终权威输出，并阻止后续增量污染', () => {
    const s0 = getInitialOpenLovableAccumulationState();
    const s1 = applyOpenLovableSseMessage(s0, { type: 'stream', text: 'PARTIAL' });
    const finalCode = '<file path="src/App.jsx">FINAL</file>';
    const s2 = applyOpenLovableSseMessage(s1, { type: 'complete', generatedCode: finalCode });
    const s3 = applyOpenLovableSseMessage(s2, { type: 'stream', text: 'EXTRA' });

    expect(s2.finalCode).toBe(finalCode);
    expect(getOpenLovableCodeForApply(s2)).toBe(finalCode);
    expect(s3.streamedText).toBe(finalCode);
    expect(getOpenLovableCodeForApply(s3)).toBe(finalCode);
  });

  it('complete.generatedCode 若不含 <file> 标签，不应覆盖既有流式代码', () => {
    const s0 = getInitialOpenLovableAccumulationState();
    const streamed = '<file path="src/App.jsx">STREAM</file>';
    const s1 = applyOpenLovableSseMessage(s0, { type: 'stream', text: streamed });
    const s2 = applyOpenLovableSseMessage(s1, { type: 'complete', generatedCode: '<thinking>DONE</thinking>' });

    expect(s2.finalCode).toBe(null);
    expect(getOpenLovableCodeForApply(s2)).toBe(streamed);
  });

  it('complete.text/content 含 file 标签时可作为最终输出（generatedCode 为空的兼容兜底）', () => {
    const s0 = getInitialOpenLovableAccumulationState();
    const finalCode = '<file path="src/main.jsx">MAIN</file>';
    const s1 = applyOpenLovableSseMessage(s0, { type: 'complete', generatedCode: '', text: finalCode });

    expect(s1.finalCode).toBe(finalCode);
    expect(getOpenLovableCodeForApply(s1)).toBe(finalCode);
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
