/**
 * E2E：OpenLovable 快速预览页（/preview-quick）关键链路验证
 *
 * 验证目标：
 * 1) 先触发并完成代码生成（/v1/openlovable/generate/stream）
 * 2) 再创建沙箱（/v1/openlovable/sandbox/create）
 * 3) 最后将代码 apply 到沙箱（/v1/openlovable/apply）
 * 4) 页面代码视图/预览 iframe 有数据，避免“空生成/空页面”回归
 *
 * 为什么要测：
 * - 该页面采用“先生成后创建沙箱”的策略（sandboxId 传 'pending'），用例可锁住时序，防止未来改动倒序。
 */

import { test, expect } from '@playwright/test';

const USERNAME = process.env.E2E_USERNAME || 'justin';
const PASSWORD = process.env.E2E_PASSWORD || 'Test12345';

/**
 * 判断是否命中 OpenLovable 相关接口请求
 *
 * 是什么：对请求 URL 的轻量匹配规则。
 * 做什么：兼容“直连后端(8080/api)”与“同源BFF(/api)”两种部署方式。
 * 为什么：不同环境下 URL 前缀不同，但 path 片段保持一致。
 */
function includesOpenLovablePath(url: string, path: string): boolean {
  return url.includes(path) || url.includes(`/api${path}`);
}

test.describe('OpenLovable 快速预览：先生成后创建沙箱', () => {
  test('页面应按“生成 → 创建沙箱 → apply”顺序执行，并产出预览/代码', async ({ page, request }) => {
    test.setTimeout(600_000); // 10 分钟：包含 SSE 生成与沙箱创建

    // 0) 后端健康检查（避免前端报错难定位）
    const backendBase =
      process.env.E2E_BACKEND_API_BASE_URL ||
      process.env.NEXT_PUBLIC_API_BASE_URL ||
      'http://127.0.0.1:8080/api';
    const health = await request.get(`${backendBase}/actuator/health`, { timeout: 10_000 });
    expect(health.ok()).toBeTruthy();

    // 1) UI 登录（需要 token 才能访问 /v1/openlovable/*）
    await page.goto('/login');
    await page.getByLabel('用户名或邮箱').fill(USERNAME);
    await page.getByLabel('密码').fill(PASSWORD);
    await page.getByRole('button', { name: '登录' }).click();

    await expect.poll(
      () => page.evaluate(() => localStorage.getItem('auth_token')),
      { timeout: 10_000 }
    ).not.toBeNull();

    // 2) 监听关键请求顺序（以“生成结束时间”为锚点，校验后续请求不提前）
    let generateStreamFinishedAt: number | null = null;
    let sandboxCreateRequestedAt: number | null = null;
    let applyRequestedAt: number | null = null;

    page.on('request', (req) => {
      const url = req.url();
      if (req.method() === 'POST' && includesOpenLovablePath(url, '/v1/openlovable/sandbox/create')) {
        sandboxCreateRequestedAt = Date.now();
      }
      if (req.method() === 'POST' && includesOpenLovablePath(url, '/v1/openlovable/apply')) {
        applyRequestedAt = Date.now();
      }
    });

    const waitGenerateStreamResp = page.waitForResponse(
      (resp) => {
        return (
          resp.request().method() === 'POST' &&
          includesOpenLovablePath(resp.url(), '/v1/openlovable/generate/stream')
        );
      },
      { timeout: 180_000 }
    );
    const waitSandboxCreateReq = page.waitForRequest(
      (req) => {
        return (
          req.method() === 'POST' &&
          includesOpenLovablePath(req.url(), '/v1/openlovable/sandbox/create')
        );
      },
      { timeout: 300_000 }
    );
    const waitApplyReq = page.waitForRequest(
      (req) => {
        return req.method() === 'POST' && includesOpenLovablePath(req.url(), '/v1/openlovable/apply');
      },
      { timeout: 300_000 }
    );

    // 3) 进入快速预览页：页面会自动开始“先生成后创建沙箱”流程
    const requirement = '做一个简单的待办事项 Web 应用（支持新增、完成、删除）';
    await page.goto(`/preview-quick/${encodeURIComponent(requirement)}`);

    // 4) UI 侧应出现关键阶段日志（用于快速定位回归）
    await expect(page.getByText('AI正在生成代码', { exact: false })).toBeVisible({ timeout: 60_000 });

    const generateResp = await waitGenerateStreamResp;
    await generateResp.finished();
    generateStreamFinishedAt = Date.now();

    await waitSandboxCreateReq;
    await waitApplyReq;

    // 5) 时序断言：创建沙箱 / apply 不应早于 SSE 生成结束
    expect(generateStreamFinishedAt).not.toBeNull();
    expect(sandboxCreateRequestedAt).not.toBeNull();
    expect(applyRequestedAt).not.toBeNull();
    expect(sandboxCreateRequestedAt as number).toBeGreaterThan(generateStreamFinishedAt as number);
    expect(applyRequestedAt as number).toBeGreaterThan(generateStreamFinishedAt as number);

    // 6) 页面最终应进入“完成”态，并出现可用预览
    await expect(page.getByText('生成完成', { exact: false })).toBeVisible({ timeout: 300_000 });

    const previewFrame = page.locator('iframe[title="应用预览"]');
    await expect(previewFrame).toBeVisible({ timeout: 300_000 });
    await expect(previewFrame).toHaveAttribute('src', /.+/);

    // 7) 代码视图应有文件（避免“生成成功但无文件”）
    await page.getByRole('tab', { name: '代码' }).click();
    const fileListHeader = page.getByText(/文件列表\s*\(\d+\)/);
    await expect(fileListHeader).toBeVisible({ timeout: 30_000 });
    await expect(fileListHeader).not.toHaveText(/文件列表\s*\(0\)/);

    await page.screenshot({ path: '/tmp/e2e-openlovable-preview-quick-success.png', fullPage: true });
  });
});

