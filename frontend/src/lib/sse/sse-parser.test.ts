import { describe, expect, it } from 'vitest';

import { parseSseEvent, splitSseBuffer } from './sse-parser';

describe('sse-parser', () => {
  it('splitSseBuffer：应按空行拆分事件并保留 remainder', () => {
    const input = 'data: {"a":1}\n\n' +
      'event: msg\ndata: {"b":2}\n\n' +
      'data: {"c":3}';

    const { events, remainder } = splitSseBuffer(input);
    expect(events.length).toBe(2);
    expect(events[0]).toContain('"a":1');
    expect(events[1]).toContain('"b":2');
    expect(remainder).toContain('"c":3');
  });

  it('parseSseEvent：应忽略 event 行并解析 JSON data', () => {
    const eventBlock = 'event: message\n' +
      'data: {"type":"status","message":"Initializing AI..."}\n';

    const parsed = parseSseEvent(eventBlock);
    expect(parsed).toEqual([
      { kind: 'json', value: { type: 'status', message: 'Initializing AI...' } }
    ]);
  });

  it('parseSseEvent：同一事件块内多条 JSON data 时应逐条输出', () => {
    const eventBlock =
      'data: {"type":"status","message":"A"}\n' +
      'data: {"type":"status","message":"B"}\n';

    const parsed = parseSseEvent(eventBlock);
    expect(parsed).toEqual([
      { kind: 'json', value: { type: 'status', message: 'A' } },
      { kind: 'json', value: { type: 'status', message: 'B' } },
    ]);
  });

  it('parseSseEvent：data 为纯文本时应输出 text payload', () => {
    const eventBlock = 'data: <file path="a">A</file>\n';
    const parsed = parseSseEvent(eventBlock);
    expect(parsed).toEqual([{ kind: 'text', value: '<file path="a">A</file>' }]);
  });
});

