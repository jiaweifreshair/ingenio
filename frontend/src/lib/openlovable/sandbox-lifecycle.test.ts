/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, expect, it, vi, afterEach } from 'vitest';
import {
  DEFAULT_SANDBOX_MAX_AGE_MS,
  ensureSandboxAvailable,
  ensureSandboxIdAvailable,
  type SandboxInfo,
} from './sandbox-lifecycle';

function mockJsonResponse(json: any, init?: { ok?: boolean; status?: number }) {
  return {
    ok: init?.ok ?? true,
    status: init?.status ?? 200,
    statusText: init?.status ? `HTTP ${init.status}` : 'OK',
    json: async () => json,
  } as any;
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('OpenLovable Sandbox Lifecycle', () => {
  it('无现有沙箱时应创建新沙箱', async () => {
    const now = 1_700_000_000_000;
    const apiBaseUrl = 'http://backend.test/api';

    global.fetch = vi.fn(async (url: any, options: any) => {
      expect(url).toBe(`${apiBaseUrl}/v1/openlovable/sandbox/create`);
      expect(options?.method).toBe('POST');
      return mockJsonResponse({
        success: true,
        data: { sandboxId: 'sb_new', url: 'https://demo.e2b.app', provider: 'e2b', message: 'ok' },
      });
    }) as any;

    const result = await ensureSandboxAvailable(apiBaseUrl, null, null, { now });

    expect(result.action).toBe('created');
    expect(result.sandbox.sandboxId).toBe('sb_new');
    expect(result.sandbox.createdAt).toBe(now);
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });

  it('现有沙箱未过期且状态可用时应复用并同步URL', async () => {
    const now = 1_700_000_000_000;
    const apiBaseUrl = 'http://backend.test/api';
    const current: SandboxInfo = {
      success: true,
      sandboxId: 'sb_exist',
      url: 'https://old.e2b.app',
      provider: 'e2b',
      message: '',
      createdAt: now - 60_000,
    };

    global.fetch = vi.fn(async (url: any, options: any) => {
      expect(options?.method).toBe('GET');
      expect(url).toBe(`${apiBaseUrl}/v1/openlovable/sandbox/status?sandboxId=sb_exist`);
      return mockJsonResponse({
        success: true,
        data: { status: 'running', url: 'https://new.e2b.app', provider: 'e2b' },
      });
    }) as any;

    const result = await ensureSandboxAvailable(apiBaseUrl, current, null, { now });

    expect(result.action).toBe('reused');
    expect(result.urlUpdated).toBe(true);
    expect(result.sandbox.url).toBe('https://new.e2b.app');
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });

  it('兼容 open-lovable-cn: 状态返回 sandboxData 时应同步 sandboxId/url', async () => {
    const now = 1_700_000_000_000;
    const apiBaseUrl = 'http://backend.test/api';
    const current: SandboxInfo = {
      success: true,
      sandboxId: 'sb_stale',
      url: 'https://5173-sb_stale.e2b.app',
      provider: 'e2b',
      message: '',
      createdAt: now - 60_000,
    };

    global.fetch = vi.fn(async (url: any, options: any) => {
      expect(options?.method).toBe('GET');
      expect(url).toBe(`${apiBaseUrl}/v1/openlovable/sandbox/status?sandboxId=sb_stale`);
      return mockJsonResponse({
        success: true,
        data: {
          success: true,
          active: true,
          healthy: true,
          sandboxData: { sandboxId: 'sb_actual', url: 'https://5173-sb_actual.e2b.app' },
          message: 'Sandbox is active and healthy',
        },
      });
    }) as any;

    const result = await ensureSandboxAvailable(apiBaseUrl, current, null, { now });

    expect(result.action).toBe('recreated');
    expect(result.reason).toBe('not_found');
    expect(result.sandbox.sandboxId).toBe('sb_actual');
    expect(result.sandbox.url).toBe('https://5173-sb_actual.e2b.app');
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });

  it('现有沙箱状态为不健康时应重建', async () => {
    const now = 1_700_000_000_000;
    const apiBaseUrl = 'http://backend.test/api';
    const current: SandboxInfo = {
      success: true,
      sandboxId: 'sb_unhealthy',
      url: 'https://5173-sb_unhealthy.e2b.app',
      provider: 'e2b',
      message: '',
      createdAt: now - 60_000,
    };

    global.fetch = vi.fn(async (url: any, options: any) => {
      if (String(url).includes('/v1/openlovable/sandbox/status')) {
        expect(options?.method).toBe('GET');
        return mockJsonResponse({
          success: true,
          data: {
            active: true,
            healthy: false,
            sandboxData: { sandboxId: 'sb_unhealthy', url: 'https://5173-sb_unhealthy.e2b.app' },
            message: 'Sandbox is active but unhealthy',
          },
        });
      }

      expect(url).toBe(`${apiBaseUrl}/v1/openlovable/sandbox/create`);
      expect(options?.method).toBe('POST');
      return mockJsonResponse({
        success: true,
        data: { sandboxId: 'sb_recovered', url: 'https://5173-sb_recovered.e2b.app', provider: 'e2b', message: 'ok' },
      });
    }) as any;

    const result = await ensureSandboxAvailable(apiBaseUrl, current, null, { now });

    expect(result.action).toBe('recreated');
    expect(result.reason).toBe('not_found');
    expect(result.sandbox.sandboxId).toBe('sb_recovered');
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it('现有沙箱状态为 not_found 时应重建', async () => {
    const now = 1_700_000_000_000;
    const apiBaseUrl = 'http://backend.test/api';
    const current: SandboxInfo = {
      success: true,
      sandboxId: 'sb_dead',
      url: 'https://dead.e2b.app',
      provider: 'e2b',
      message: '',
      createdAt: now - 60_000,
    };

    global.fetch = vi.fn(async (url: any, options: any) => {
      if (String(url).includes('/v1/openlovable/sandbox/status')) {
        expect(options?.method).toBe('GET');
        return mockJsonResponse({ success: true, data: { status: 'not_found' } });
      }

      expect(url).toBe(`${apiBaseUrl}/v1/openlovable/sandbox/create`);
      expect(options?.method).toBe('POST');
      return mockJsonResponse({
        success: true,
        data: { sandboxId: 'sb_new2', url: 'https://new2.e2b.app', provider: 'e2b', message: 'ok' },
      });
    }) as any;

    const result = await ensureSandboxAvailable(apiBaseUrl, current, null, { now });

    expect(result.action).toBe('recreated');
    expect(result.reason).toBe('not_found');
    expect(result.sandbox.sandboxId).toBe('sb_new2');
    expect(global.fetch).toHaveBeenCalledTimes(2);
  });

  it('现有沙箱超过最大复用时长时应直接重建（不查询状态）', async () => {
    const now = 1_700_000_000_000;
    const apiBaseUrl = 'http://backend.test/api';
    const current: SandboxInfo = {
      success: true,
      sandboxId: 'sb_old',
      url: 'https://old.e2b.app',
      provider: 'e2b',
      message: '',
      createdAt: now - DEFAULT_SANDBOX_MAX_AGE_MS - 1,
    };

    global.fetch = vi.fn(async (url: any, options: any) => {
      expect(url).toBe(`${apiBaseUrl}/v1/openlovable/sandbox/create`);
      expect(options?.method).toBe('POST');
      return mockJsonResponse({
        success: true,
        data: { sandboxId: 'sb_fresh', url: 'https://fresh.e2b.app', provider: 'e2b', message: 'ok' },
      });
    }) as any;

    const result = await ensureSandboxAvailable(apiBaseUrl, current, null, { now });

    expect(result.action).toBe('recreated');
    expect(result.reason).toBe('expired');
    expect(result.sandbox.sandboxId).toBe('sb_fresh');
    expect(global.fetch).toHaveBeenCalledTimes(1);
  });

  it('ensureSandboxIdAvailable: sandboxId 不可用时应创建新沙箱', async () => {
    const now = 1_700_000_000_000;
    const apiBaseUrl = 'http://backend.test/api';

    global.fetch = vi.fn(async (url: any, options: any) => {
      if (String(url).includes('/v1/openlovable/sandbox/status')) {
        expect(options?.method).toBe('GET');
        return mockJsonResponse({ success: true, data: { message: "Sandbox Not Found" } });
      }
      expect(url).toBe(`${apiBaseUrl}/v1/openlovable/sandbox/create`);
      expect(options?.method).toBe('POST');
      return mockJsonResponse({
        success: true,
        data: { sandboxId: 'sb_created', url: 'https://created.e2b.app', provider: 'e2b', message: 'ok' },
      });
    }) as any;

    const result = await ensureSandboxIdAvailable(apiBaseUrl, 'sb_missing', null, { now });

    expect(result.action).toBe('recreated');
    expect(result.sandbox.sandboxId).toBe('sb_created');
    expect(result.sandbox.createdAt).toBe(now);
  });
});
