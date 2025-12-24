/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';

/**
 * 最小闭环测试：
 * - 参数缺失时返回 400
 * - 代理成功时透传后端响应
 */

const ORIGINAL_BACKEND_API_URL = process.env.BACKEND_API_URL;

afterEach(() => {
  if (ORIGINAL_BACKEND_API_URL === undefined) {
    delete process.env.BACKEND_API_URL;
  } else {
    process.env.BACKEND_API_URL = ORIGINAL_BACKEND_API_URL;
  }
  vi.restoreAllMocks();
});

beforeEach(() => {
  vi.resetModules();
  process.env.BACKEND_API_URL = 'http://backend.test/api';
});

function makeRequest(body: any) {
  return { json: async () => body } as any;
}

describe('POST /api/v1/openlovable/heartbeat', () => {
  it('缺少 sandboxId 时应返回 400', async () => {
    const { POST } = await import('./route');
    const res = await POST(makeRequest({}));
    expect(res.status).toBe(400);
    const json = await res.json();
    expect(json.success).toBe(false);
  });

  it('应代理到后端并透传响应', async () => {
    const { POST } = await import('./route');
    const backendPayload = { success: true, data: { ok: true } };

    global.fetch = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => backendPayload,
      text: async () => JSON.stringify(backendPayload),
    })) as any;

    const res = await POST(makeRequest({ sandboxId: 'sb_123' }));

    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect((global.fetch as any).mock.calls[0][0]).toBe(
      'http://backend.test/api/v1/openlovable/heartbeat'
    );

    const json = await res.json();
    expect(json.success).toBe(true);
    expect(json.data).toEqual(backendPayload);
  });
});
