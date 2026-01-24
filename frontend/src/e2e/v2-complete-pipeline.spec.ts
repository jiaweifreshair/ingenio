/**
 * Blueprint 端到端（E2E）测试
 *
 * 覆盖范围（以“能跑通、能定位问题”为第一目标）：
 * 1) Plan 路由：`POST /api/v2/plan-routing/route`
 * 2) Blueprint Mode：`POST /api/v2/plan-routing/{appSpecId}/select-template`
 * 3) 设计确认：`POST /api/v2/plan-routing/{appSpecId}/confirm-design`
 * 4) UI 主链路：登录 → 生成 →（确认方案）→ 原型确认 → 确认设计 → 进入 Execute（G3 Console）
 *
 * 说明：
 * - 该链路依赖 AI 规划/原型生成与外部沙箱，耗时远超 10s 属于正常现象；
 * - 为减少误报，关键请求均显式提高 timeout；
 * - E2E 默认走真实后端，不使用 Mock。
 */

import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

/**
 * 后端基准地址（包含 /api context-path）
 * - 默认使用 127.0.0.1，避免 localhost 在部分环境优先解析到 IPv6(::1) 导致连接问题
 */
const BACKEND_API_BASE_URL =
  process.env.E2E_BACKEND_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  'http://127.0.0.1:8080/api';

/**
 * UI 登录账号（用于走完整 UI 链路）
 * - 如果你不希望把密码写死，请在运行时注入 `E2E_PASSWORD`
 */
const E2E_USERNAME = process.env.E2E_USERNAME || 'justin';
const E2E_PASSWORD = process.env.E2E_PASSWORD || 'qazOKM123';

const API_TIMEOUT_MS = Number(process.env.E2E_API_TIMEOUT_MS || 180_000);
const PRODUCTSHOT_TEMPLATE_ID = 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11';

function api(path: string) {
  return `${BACKEND_API_BASE_URL}${path}`;
}

async function ensureBackendHealthy(request: APIRequestContext) {
  const health = await request.get(api('/actuator/health'), { timeout: 10_000 });
  expect(health.ok()).toBeTruthy();
}

async function loginViaApi(request: APIRequestContext) {
  const resp = await request.post(api('/v1/auth/login'), {
    data: { usernameOrEmail: E2E_USERNAME, password: E2E_PASSWORD },
    timeout: 20_000,
  });
  expect(resp.ok()).toBeTruthy();
  const body = await resp.json();
  // 兼容两类响应包装：
  // 1) { code: "0000", message, data, ... }
  // 2) { code: 200, success: true, message: "success", data, ... }
  expect([200, '0000']).toContain(body.code);
  expect(body.data?.token).toBeTruthy();
  return { token: body.data.token as string };
}

async function loginViaUi(page: Page) {
  await page.goto('/login');
  await page.getByLabel('用户名或邮箱').fill(E2E_USERNAME);
  await page.getByLabel('密码').fill(E2E_PASSWORD);

  const loginRespPromise = page.waitForResponse((resp) => {
    return resp.request().method() === 'POST' && resp.url().includes('/v1/auth/login');
  });

  await page.getByRole('button', { name: '登录' }).click();
  const loginResp = await loginRespPromise;
  expect(loginResp.ok()).toBeTruthy();

  await expect.poll(
    () => page.evaluate(() => localStorage.getItem('auth_token')),
    { timeout: 10_000 }
  ).not.toBeNull();
}

async function routeClone(request: APIRequestContext, token: string) {
  const routeResp = await request.post(api('/v2/plan-routing/route'), {
    headers: { authorization: token },
    timeout: API_TIMEOUT_MS,
    data: {
      userRequirement: '仿照 airbnb.com 做一个民宿预订平台，需要支持房源列表、预订、支付功能',
    },
  });
  expect(routeResp.ok()).toBeTruthy();

  const routeBody = await routeResp.json();
  expect(routeBody.code).toBe('0000');

  const data = routeBody.data;
  expect(data).toBeTruthy();
  expect(data.intent).toBe('CLONE_EXISTING_WEBSITE');
  expect(data.branch).toBe('CLONE');
  expect(data.prototypeGenerated).toBe(true);
  expect(typeof data.prototypeUrl).toBe('string');
  expect(data.prototypeUrl).toMatch(/^https?:\/\//);
  expect(data.appSpecId).toBeTruthy();

  return { appSpecId: data.appSpecId as string, prototypeUrl: data.prototypeUrl as string };
}

test.describe('Blueprint E2E（API + UI）', () => {
  let token: string;

  test.beforeAll(async ({ request }) => {
    await ensureBackendHealthy(request);
    const login = await loginViaApi(request);
    token = login.token;
  });

  test('API：CLONE 路由返回原型（可确认设计）', async ({ request }) => {
    test.setTimeout(API_TIMEOUT_MS + 60_000);
    await routeClone(request, token);
  });

  test('API：选择模板后 Blueprint Mode 已加载（select-template）', async ({ request }) => {
    test.setTimeout(API_TIMEOUT_MS + 60_000);
    const { appSpecId } = await routeClone(request, token);

    const resp = await request.post(api(`/v2/plan-routing/${appSpecId}/select-template`), {
      headers: { authorization: token },
      timeout: 30_000,
      data: { templateId: PRODUCTSHOT_TEMPLATE_ID },
    });
    expect(resp.ok()).toBeTruthy();

    const body = await resp.json();
    expect(body.code).toBe('0000');
    expect(body.data?.metadata?.blueprintModeEnabled).toBe(true);
    expect(body.data?.nextAction).toContain('Blueprint');
  });

  test('API：未确认设计时，execute-code-generation 应返回“设计未确认”', async ({ request }) => {
    test.setTimeout(API_TIMEOUT_MS + 60_000);
    const { appSpecId } = await routeClone(request, token);

    const resp = await request.post(api(`/v2/plan-routing/${appSpecId}/execute-code-generation`), {
      headers: { authorization: token },
      timeout: 30_000,
      data: {},
    });
    expect(resp.ok()).toBeTruthy();

    const body = await resp.json();
    expect(body.code).toBe('1001');
    expect(body.message).toContain('设计未确认');
  });

  test('API：confirm-design 成功（数据库需补齐 blueprint_* 字段）', async ({ request }) => {
    test.setTimeout(API_TIMEOUT_MS + 60_000);
    const { appSpecId } = await routeClone(request, token);

    const resp = await request.post(api(`/v2/plan-routing/${appSpecId}/confirm-design`), {
      headers: { authorization: token },
      timeout: 30_000,
    });
    expect(resp.ok()).toBeTruthy();

    const body = await resp.json();
    expect(body.code).toBe('0000');
    expect(body.data?.message).toContain('设计确认成功');
  });

  test('API：DESIGN 路由返回 styleVariants（当前为单风格极速版）', async ({ request }) => {
    test.setTimeout(90_000);

    const routeResp = await request.post(api('/v2/plan-routing/route'), {
      headers: { authorization: token },
      timeout: 60_000,
      data: {
        userRequirement: '创建一个技术博客平台，支持Markdown编辑、代码高亮、评论功能',
      },
    });
    expect(routeResp.ok()).toBeTruthy();

    const routeBody = await routeResp.json();
    expect(routeBody.code).toBe('0000');

    const data = routeBody.data;
    expect(data.intent).toBe('DESIGN_FROM_SCRATCH');
    expect(data.branch).toBe('DESIGN');
    expect(Array.isArray(data.styleVariants)).toBe(true);
    expect(data.styleVariants.length).toBeGreaterThanOrEqual(1);
    expect(typeof data.selectedStyleId).toBe('string');
    expect(data.prototypeGenerated).toBe(false);
    expect(data.nextAction).toContain('设计风格');
  });

  test('UI：登录 → 生成 → 原型确认 → 确认设计 → 进入 Execute', async ({ page }) => {
    test.setTimeout(300_000);

    await loginViaUi(page);
    await page.goto('/');
    await expect(page.getByText('你的创意，')).toBeVisible();
    await expect(page.getByText('AI 实现')).toBeVisible();

    // 提交克隆需求，确保后端直接生成原型（无需等待前端 SSE 生成）
    const input = page.locator('textarea[placeholder*="在这里输入你想做什么"]');
    await input.fill('仿照 airbnb.com 做一个民宿预订平台，需要支持房源列表、预订、支付功能');
    await page.getByRole('button', { name: '生成' }).click();

    await expect(page.getByText('深度分析')).toBeVisible({ timeout: 60_000 });

    // 分析完成后可能出现 PlanDisplay，需要用户点击确认；也可能直接进入原型确认
    const planConfirmButton = page.getByRole('button', { name: '确认并生成原型' });
    const prototypePanel = page.locator('[data-testid="prototype-confirmation-panel"]');

    const reached = await Promise.race([
      planConfirmButton.waitFor({ state: 'visible', timeout: 180_000 }).then(() => 'plan' as const),
      prototypePanel.waitFor({ state: 'visible', timeout: 180_000 }).then(() => 'prototype' as const),
    ]);

    if (reached === 'plan') {
      await planConfirmButton.click();
      await expect(prototypePanel).toBeVisible({ timeout: 180_000 });
    }

    // 原型确认面板
    // PrototypeConfirmation 同时渲染了桌面/移动两套布局（其中一套会被 CSS 隐藏），这里显式只选取可见元素
    const refreshBtn = page.locator('[data-testid="refresh-preview-button"]:visible');
    const confirmBtn = page.locator('[data-testid="confirm-design-button"]:visible');

    await expect(refreshBtn).toBeVisible();
    await expect(confirmBtn).toBeVisible();

    // 预览可能存在“沙箱过期/地址失效”，先触发一次刷新（按钮可能短暂禁用，做容错）
    try {
      await expect(refreshBtn).toBeEnabled({ timeout: 60_000 });
      await refreshBtn.click();
    } catch {
      // ignore: clone 分支可能已可确认，无需刷新
    }

    await expect(confirmBtn).toBeEnabled({ timeout: 180_000 });

    // 监听 confirm-design 请求，确保后端真正完成设计确认（而不是仅 UI 切步）
    const confirmDesignRespPromise = page.waitForResponse((resp) => {
      return resp.request().method() === 'POST' && resp.url().includes('/v2/plan-routing/') && resp.url().includes('/confirm-design');
    });

    await confirmBtn.click();
    await page.getByRole('button', { name: '确认并生成' }).click();

    const confirmDesignResp = await confirmDesignRespPromise;
    expect(confirmDesignResp.ok()).toBeTruthy();
    const confirmDesignBody = await confirmDesignResp.json();
    expect(confirmDesignBody.code).toBe('0000');

    // 进入 Execute（G3 Console）
    await expect(page.getByTestId('g3-atoms-console')).toBeVisible({ timeout: 60_000 });
  });
});
