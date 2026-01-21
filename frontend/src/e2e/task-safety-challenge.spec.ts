/**
 * 安全与应急挑战赛基准 - 验收测试
 *
 * 目标：
 * - 将首页/演示用例从“情绪/压力类”切换为“安全与应急”方向；
 * - 以 /examples（TSX 示例实现）与 /benchmarks（后端加载标杆 HTML）作为最小验收闭环。
 *
 * 运行方式：
 * - E2E_TASK_SAFETY=1 pnpm e2e:chromium -- src/e2e/task-safety-challenge.spec.ts
 *
 * 说明：
 * - /benchmarks 依赖后端：http://127.0.0.1:8080/api（需 Docker Postgres/Redis/MinIO 就绪）
 * - 若后端未启动，本用例会自动 skip “Benchmarks 对照”部分，不影响前端示例页验收
 */

import { test, expect, type APIRequestContext } from "@playwright/test";

const ENABLED = process.env.E2E_TASK_SAFETY === "1";

const BACKEND_API_BASE_URL =
  process.env.E2E_BACKEND_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  "http://127.0.0.1:8080/api";

async function isBackendUp(request: APIRequestContext): Promise<boolean> {
  try {
    const resp = await request.get(`${BACKEND_API_BASE_URL}/actuator/health`, {
      timeout: 3000,
    });
    return resp.ok();
  } catch {
    return false;
  }
}

test.describe("安全与应急挑战赛基准 - 核心流程验收", () => {
  test.skip(!ENABLED, "需要设置 E2E_TASK_SAFETY=1");

  test("TSX 示例页可访问（/examples）", async ({ page }) => {
    await page.goto("/examples");
    await expect(page.getByText(/挑战赛标杆/i)).toBeVisible();

    // 进入小学组示例页
    await page.getByText("我的安全小卫士").click();
    await expect(page).toHaveURL(/\/examples\/primary/);
    await expect(page.getByText(/My Safety Guardian|我的安全小卫士/i)).toBeVisible();
  });

  test("Benchmarks 对照页可加载后端标杆 HTML（/benchmarks，可选）", async ({ page, request }) => {
    const backendUp = await isBackendUp(request);
    test.skip(!backendUp, "后端未启动，跳过 Benchmarks 对照（请先启动后端与Docker依赖）");

    await page.goto("/benchmarks");
    await expect(page.getByText("基准列表")).toBeVisible();

    // 选择 primary（小学组）
    await page.getByText(/id:\\s*primary/i).click();

    // 右侧代码区应出现 HTML title
    await expect(page.getByText(/<title>我的安全小卫士/)).toBeVisible();
  });
});
