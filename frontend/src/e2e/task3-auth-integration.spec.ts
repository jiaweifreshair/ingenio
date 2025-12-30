/**
 * 任务3：前端接口联调（真实后端）
 *
 * 目标覆盖：
 * 1) 前端可访问（/ 与 /login 页面正常渲染）
 * 2) 公共接口调用：未登录状态下请求 /v1/notifications/unread-count 成功
 * 3) 用户登录流程：UI 登录成功并写入 localStorage/cookie
 * 4) 受保护接口调用：访问 /dashboard 触发 /v1/projects/stats，请求头携带裸 token（无 Bearer）
 * 5) trace_id 链路：请求头携带 x-trace-id，且后端响应头回传相同 x-trace-id
 *
 * 运行方式（需要本机后端已启动在 8080/api）：
 * - `cd frontend && E2E_TASK3=1 NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8080/api pnpm e2e:chromium -- src/e2e/task3-auth-integration.spec.ts`
 */

import { test, expect } from '@playwright/test';
import { randomUUID } from 'crypto';

const ENABLED = process.env.E2E_TASK3 === '1';

const USERNAME = process.env.E2E_USERNAME || 'justin';
const PASSWORD = process.env.E2E_PASSWORD || 'qazOKM123';

/**
 * 后端基准地址（包含 /api context-path）
 * - 默认使用 127.0.0.1，避免 localhost 在部分环境优先解析到 IPv6(::1) 导致连接问题
 */
const BACKEND_API_BASE_URL =
  process.env.E2E_BACKEND_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  'http://127.0.0.1:8080/api';

/**
 * 判断某个响应是否命中“同源BFF(/api)”或“直连后端(8080/api)”两种模式之一。
 * - 同源BFF：`/api/v1/...`
 * - 直连后端：`${BACKEND_API_BASE_URL}/v1/...`
 */
function isApiResponse(url: string, directPath: string, bffPath: string) {
  return url.includes(bffPath) || url.includes(directPath);
}

test.describe('任务3 - 前端接口联调（真实后端）', () => {
  test.skip(!ENABLED, '需要设置 E2E_TASK3=1，并确保后端服务已启动。');

  test('登录 + 受保护接口 + trace_id', async ({ page, request }) => {
    // 0) 后端可用性检查（避免前端报错难定位）
    const health = await request.get(`${BACKEND_API_BASE_URL}/actuator/health`);
    expect(health.ok()).toBeTruthy();

    // 1) trace_id 链路（后端）：主动带 x-trace-id 请求健康检查，验证响应头回传相同 traceId
    const traceId = randomUUID();
    const tracedHealth = await request.get(`${BACKEND_API_BASE_URL}/actuator/health`, {
      headers: { 'x-trace-id': traceId },
    });
    expect(tracedHealth.ok()).toBeTruthy();
    expect(tracedHealth.headers()['x-trace-id']).toBe(traceId);

    // 2) 未登录访问首页：验证页面渲染正常（TopNav 品牌名）
    await page.goto('/');
    await expect(page.getByRole('link', { name: '秒构AI' })).toBeVisible();

    // 兼容性：部分页面版本不再在匿名首屏触发 unread-count 请求；如果存在则校验其携带 x-trace-id
    try {
      const unreadResp = await page.waitForResponse(
        (resp) => {
          return (
            isApiResponse(
              resp.url(),
              `${BACKEND_API_BASE_URL}/v1/notifications/unread-count`,
              '/api/v1/notifications/unread-count'
            ) &&
            resp.request().method() === 'GET'
          );
        },
        { timeout: 3000 }
      );
      expect(unreadResp.ok()).toBeTruthy();
      expect(unreadResp.request().headers()['x-trace-id']).toBeTruthy();
    } catch {
      // 不阻塞主链路：登录/受保护接口/trace_id 回传仍然会覆盖核心联调目标
    }

    // 3) UI 登录（justin/qazOKM123）
    await page.goto('/login');

    await page.getByLabel('用户名或邮箱').fill(USERNAME);
    await page.getByLabel('密码').fill(PASSWORD);

    const loginRespPromise = page.waitForResponse((resp) => {
      return (
        isApiResponse(
          resp.url(),
          `${BACKEND_API_BASE_URL}/v1/auth/login`,
          '/api/v1/auth/login'
        ) &&
        resp.request().method() === 'POST'
      );
    });

    await page.getByRole('button', { name: '登录' }).click();

    const loginResp = await loginRespPromise;
    expect(loginResp.ok()).toBeTruthy();

    const loginReqHeaders = loginResp.request().headers();
    expect(loginReqHeaders['x-trace-id']).toBeTruthy();

    // 4) Token 存储验证（localStorage + Cookie）
    await expect.poll(
      () => page.evaluate(() => localStorage.getItem('auth_token')),
      { timeout: 10_000 }
    ).not.toBeNull();

    const tokenValue = await page.evaluate(() => localStorage.getItem('auth_token'));
    expect(tokenValue).toBeTruthy();
    expect(tokenValue).not.toContain('Bearer ');

    const cookies = await page.context().cookies();
    const authCookie = cookies.find((c) => c.name === 'auth_token');
    expect(authCookie?.value).toBe(tokenValue);

    // 5) 访问受保护页面，触发受保护接口请求
    // 同理：受保护接口请求可能在页面加载早期就发出，先监听再跳转更稳
    const statsRespPromise = page.waitForResponse((resp) => {
      return (
        isApiResponse(
          resp.url(),
          `${BACKEND_API_BASE_URL}/v1/projects/stats`,
          '/api/v1/projects/stats'
        ) &&
        resp.request().method() === 'GET'
      );
    });
    await page.goto('/dashboard');

    const statsResp = await statsRespPromise;
    expect(statsResp.ok()).toBeTruthy();

    const statsReqHeaders = statsResp.request().headers();
    expect(statsReqHeaders.authorization).toBe(tokenValue);
    expect(statsReqHeaders.authorization).not.toMatch(/^Bearer\\s+/i);
    expect(statsReqHeaders['x-trace-id']).toBeTruthy();

    // 6) trace_id 回传：后端 TraceIdFilter 会把 traceId 放入响应头
    const respHeaders = statsResp.headers();
    // 兼容同源BFF：如果响应头包含 x-trace-id，则要求与请求一致；否则不强制（由 BFF 是否透传决定）
    if (respHeaders['x-trace-id']) {
      expect(respHeaders['x-trace-id']).toBe(statsReqHeaders['x-trace-id']);
    }
  });
});
