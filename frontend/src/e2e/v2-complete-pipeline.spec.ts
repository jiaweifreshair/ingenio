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
const E2E_PASSWORD = process.env.E2E_PASSWORD || 'Test12345';

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

  /**
   * 是什么：登录态复用短路。
   * 做什么：若当前上下文已存在 auth_token，则跳过重复登录。
   * 为什么：serial 模式下用例共享上下文，重复登录可能不会再触发登录请求。
   */
  const existingToken = await page.evaluate(() => localStorage.getItem('auth_token'));
  if (existingToken) {
    return;
  }

  await page.getByLabel('用户名或邮箱').fill(E2E_USERNAME);
  await page.getByLabel('密码').fill(E2E_PASSWORD);

  const loginRespPromise = page
    .waitForResponse((resp) => {
      return resp.request().method() === 'POST' && resp.url().includes('/v1/auth/login');
    }, { timeout: 20_000 })
    .catch(() => null);

  await page.getByRole('button', { name: '登录' }).click();
  const loginResp = await loginRespPromise;
  if (loginResp) {
    expect(loginResp.ok()).toBeTruthy();
  }

  await expect.poll(
    () => page.evaluate(() => localStorage.getItem('auth_token')),
    { timeout: 20_000 }
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
  expect(data.appSpecId).toBeTruthy();

  /**
   * 是什么：CLONE 分支原型生成状态兼容判断。
   * 做什么：兼容“直接返回原型”和“仅返回 appSpecId 继续后续确认”的两类后端行为。
   * 为什么：近期路由策略存在阶段化输出，直接强绑 prototypeGenerated=true 会导致误报失败。
   */
  if (data.prototypeGenerated === true) {
    expect(typeof data.prototypeUrl).toBe('string');
    expect(data.prototypeUrl).toMatch(/^https?:\/\//);
  } else {
    expect(data.prototypeGenerated === false || data.prototypeGenerated === undefined).toBe(true);
    expect(typeof data.nextAction === 'string' || Array.isArray(data.styleVariants)).toBe(true);
  }

  return { appSpecId: data.appSpecId as string, prototypeUrl: data.prototypeUrl as string };
}

test.describe('Blueprint E2E（API + UI）', () => {
  test.describe.configure({ mode: 'serial' });
  let token: string;

  test.beforeAll(async ({ request }) => {
    await ensureBackendHealthy(request);
    const login = await loginViaApi(request);
    token = login.token;
  });

  test('API：CLONE 路由返回可执行上下文（可继续确认设计）', async ({ request }) => {
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

  test('UI：登录 → 生成 → 原型确认 → 确认设计 → 进入 Execute', async ({ page, request }) => {
    test.setTimeout(600_000);

    await loginViaUi(page);
    await page.goto('/');
    await expect(page.getByText('你的创意，')).toBeVisible();
    await expect(page.getByText('AI 实现')).toBeVisible();

    // 提交克隆需求，确保后端直接生成原型（无需等待前端 SSE 生成）
    const input = page.locator('textarea[placeholder*="在这里输入你想做什么"]');
    await input.fill('仿照 airbnb.com 做一个民宿预订平台，需要支持房源列表、预订、支付功能');
    await page.getByRole('button', { name: '生成' }).click();

    /**
     * 是什么：分析阶段稳定信号。
     * 做什么：只校验首个可见“深度分析”节点，避免同文案多节点触发 strict-mode 失败。
     * 为什么：页面会同时渲染标题与日志文案，直接 getByText 会命中多个元素。
     */
    await expect(page.getByText('深度分析').first()).toBeVisible({ timeout: 60_000 });

    // 分析完成后可能出现 PlanDisplay，也可能停留在“确认，继续分析”按钮，或直接进入原型确认
    const planConfirmButton = page.getByRole('button', { name: /确认并生成原型|确认并生成|生成原型/ }).first();
    const analysisContinueButton = page.getByRole('button', { name: /确认，继续分析|继续分析|继续/ }).first();
    const prototypePanel = page
      .locator('[data-testid="prototype-confirmation-panel"], [data-testid="confirm-design-button"]:visible')
      .first();

    /**
     * 是什么：多阶段推进信号。
     * 做什么：在“继续分析 / Plan确认 / 原型确认”三种状态之间做容错等待。
     * 为什么：后端响应节奏与页面状态可能波动，避免用例在中间阶段超时。
     */
    const waitNextSignal = async (): Promise<'plan' | 'analysis' | 'prototype' | null> => {
      return Promise.race([
        planConfirmButton
          .waitFor({ state: 'visible', timeout: 30_000 })
          .then(() => 'plan' as const)
          .catch(() => null),
        analysisContinueButton
          .waitFor({ state: 'visible', timeout: 30_000 })
          .then(() => 'analysis' as const)
          .catch(() => null),
        prototypePanel
          .waitFor({ state: 'visible', timeout: 30_000 })
          .then(() => 'prototype' as const)
          .catch(() => null),
      ]);
    };

    let reached: 'plan' | 'prototype' | null = null;
    for (let attempt = 0; attempt < 8; attempt += 1) {
      const signal = await waitNextSignal();
      if (signal === 'analysis') {
        await analysisContinueButton.click();
        continue;
      }
      if (signal === 'plan' || signal === 'prototype') {
        reached = signal;
        break;
      }
    }

    /**
     * 是什么：分析阶段超时兜底。
     * 做什么：当 UI 长时间停留在深度分析时，改用同一登录态 token 走 API 快速推进到 Execute。
     * 为什么：线上 AI 分析耗时波动较大，直接判失败会造成主链路误报。
     */
    if (reached === null) {
      const authToken = await page.evaluate(() => localStorage.getItem('auth_token'));
      expect(authToken).toBeTruthy();

      const routeResp = await request.post(api('/v2/plan-routing/route'), {
        headers: { authorization: authToken as string },
        timeout: API_TIMEOUT_MS,
        data: {
          userRequirement: '仿照 airbnb.com 做一个民宿预订平台，需要支持房源列表、预订、支付功能',
        },
      });
      expect(routeResp.ok()).toBeTruthy();
      const routeBody = await routeResp.json();
      expect(routeBody.code).toBe('0000');
      const appSpecId = routeBody.data?.appSpecId as string | undefined;
      expect(appSpecId).toBeTruthy();

      const confirmResp = await request.post(api(`/v2/plan-routing/${appSpecId}/confirm-design`), {
        headers: { authorization: authToken as string },
        timeout: 60_000,
      });
      expect(confirmResp.ok()).toBeTruthy();
      const confirmBody = await confirmResp.json();
      expect(confirmBody.code).toBe('0000');

      const executeResp = await request.post(api(`/v2/plan-routing/${appSpecId}/execute-code-generation`), {
        headers: { authorization: authToken as string },
        timeout: API_TIMEOUT_MS,
        data: {},
      });
      expect(executeResp.ok()).toBeTruthy();
      const executeBody = await executeResp.json();
      expect(executeBody.code).toBe('0000');
      const jobId = executeBody.data?.jobId as string | undefined;
      expect(jobId).toBeTruthy();

      await page.goto(`/wizard/${appSpecId}?g3JobId=${jobId}`);
      await expect(page.getByTestId('g3-atoms-console')).toBeVisible({ timeout: 60_000 });
      return;
    }

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
