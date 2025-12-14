/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest';
import { POST } from './route';

/**
 * 仅做最小闭环测试：
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
  process.env.BACKEND_API_URL = 'http://backend.test/api';
});

function makeRequest(body: any, auth?: string) {
  const headers = new Headers();
  if (auth) headers.set('Authorization', auth);
  return { json: async () => body, headers } as any;
}

describe('POST /api/v1/prototype/create-app-spec', () => {
  it('缺少 userRequirement 时应返回 400', async () => {
    const res = await POST(makeRequest({ sandboxUrl: 'http://sandbox' }));
    expect(res.status).toBe(400);
    const json = await res.json();
    expect(json.success).toBe(false);
  });

  it('应代理到后端并透传响应', async () => {
    const backendPayload = { success: true, data: { appSpecId: 'abc' } };

    global.fetch = vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => backendPayload,
      text: async () => JSON.stringify(backendPayload),
    })) as any;

    const res = await POST(
      makeRequest({
        userRequirement: '测试需求文本',
        sandboxUrl: 'http://sandbox',
        sandboxId: 'sid',
      }, 'Bearer token')
    );

    expect(global.fetch).toHaveBeenCalledTimes(1);
    expect((global.fetch as any).mock.calls[0][0]).toBe(
      'http://backend.test/api/v1/prototype/create-app-spec'
    );

    const json = await res.json();
    expect(json).toEqual(backendPayload);
  });
});

