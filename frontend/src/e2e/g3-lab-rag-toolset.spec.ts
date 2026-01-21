import { test, expect, type APIRequestContext } from "@playwright/test";

/**
 * G3 RAG + Toolset + Lab 流程验收
 *
 * 运行方式：
 * - E2E_G3_FULL=1 pnpm e2e:chromium -- src/e2e/g3-lab-rag-toolset.spec.ts
 */

const ENABLE_G3_FULL = process.env.E2E_G3_FULL === "1";
const ENABLE_ENTRY_FULL = process.env.E2E_ENTRY_FULL === "1";

const BACKEND_API_BASE_URL =
  process.env.E2E_BACKEND_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  "http://127.0.0.1:8080/api";

async function ensureBackendHealthy(request: APIRequestContext) {
  const resp = await request.get(`${BACKEND_API_BASE_URL}/actuator/health`, {
    timeout: 10_000,
  });
  expect(resp.ok()).toBeTruthy();
}

test.describe("G3 RAG + Toolset + Lab 流程验收", () => {
  test.skip(!ENABLE_G3_FULL, "需要设置 E2E_G3_FULL=1 才执行");

  test.beforeAll(async ({ request }) => {
    await ensureBackendHealthy(request);
  });

  test("API：Repo Index & Search 可用", async ({ request }) => {
    const indexResp = await request.post(
      `${BACKEND_API_BASE_URL}/v1/g3/knowledge/repo/index`,
      {
        timeout: 120_000,
        data: {},
      }
    );
    expect(indexResp.ok()).toBeTruthy();
    const indexBody = await indexResp.json();
    expect(indexBody.code).toBe(200);

    const searchResp = await request.get(
      `${BACKEND_API_BASE_URL}/v1/g3/knowledge/repo/search?q=G3OrchestratorService`,
      {
        timeout: 20_000,
      }
    );
    expect(searchResp.ok()).toBeTruthy();
    const searchBody = await searchResp.json();
    expect(searchBody.code).toBe(200);
    expect(Array.isArray(searchBody.data)).toBe(true);
  });

  test("API：Toolset 搜索可用", async ({ request }) => {
    const resp = await request.get(
      `${BACKEND_API_BASE_URL}/v1/g3/tools/search?q=G3OrchestratorService`,
      {
        timeout: 20_000,
      }
    );
    expect(resp.ok()).toBeTruthy();
    const body = await resp.json();
    expect(body.code).toBe(200);
    expect(body.data).toBeTruthy();
    expect(Array.isArray(body.data.matches)).toBe(true);
  });

  test("API：AppSpec 需求修改可透传到 G3 上下文", async ({ request }) => {
    test.setTimeout(240_000);

    // 1) 创建 AppSpec（PlanRouting）
    const routeResp = await request.post(`${BACKEND_API_BASE_URL}/v2/plan-routing/route`, {
      timeout: 30_000,
      data: {
        userRequirement: "创建一个事故管理系统，包含上报/审核/整改闭环",
      },
    });
    expect(routeResp.ok()).toBeTruthy();
    const routeBody = await routeResp.json();
    // /v2/** 使用后端 Result<T>，成功码为字符串（默认 "0000"）
    expect(routeBody.code === 200 || routeBody.code === "0000").toBeTruthy();
    const appSpecId = routeBody.data?.appSpecId as string;
    expect(appSpecId).toBeTruthy();

    // 2) 原型确认前追加/修改需求（模拟 Chat）
    const updatedRequirement =
      "创建一个事故管理系统，包含上报/审核/整改闭环\n\n---\n【需求变更】\n数据库改为 MongoDB，并增加超时提醒";
    const updateResp = await request.post(
      `${BACKEND_API_BASE_URL}/v2/plan-routing/${appSpecId}/update-requirement`,
      {
        timeout: 30_000,
        data: { userRequirement: updatedRequirement },
      }
    );
    expect(updateResp.ok()).toBeTruthy();
    const updateBody = await updateResp.json();
    expect(updateBody.code === 200 || updateBody.code === "0000").toBeTruthy();

    // 3) 提交 G3 任务（仅传 appSpecId，requirement 为空，验证后端会回退读取 AppSpec 最新需求）
    const submitResp = await request.post(`${BACKEND_API_BASE_URL}/v1/g3/jobs`, {
      timeout: 30_000,
      data: {
        requirement: "",
        appSpecId,
        maxRounds: 0,
      },
    });
    expect(submitResp.ok()).toBeTruthy();
    const submitBody = await submitResp.json();
    expect(submitBody.code).toBe(200);
    const jobId = submitBody.data?.jobId as string;
    expect(jobId).toBeTruthy();

    // 4) 读取 task_plan.md（初始化阶段即可读到需求，适合校验“需求透传”）
    const ctxResp = await request.get(
      `${BACKEND_API_BASE_URL}/v1/g3/jobs/${jobId}/planning/task_plan/content`,
      {
        timeout: 30_000,
      }
    );
    expect(ctxResp.ok()).toBeTruthy();
    const ctxBody = await ctxResp.json();
    // /v1/g3/jobs/**/planning/** 使用 ApiResponse<T>（success/data/errorCode）
    expect(ctxBody.success).toBe(true);
    expect(String(ctxBody.data)).toContain("MongoDB");
  });

  test("UI：/lab 启动 G3 任务并出现日志", async ({ page }) => {
    test.setTimeout(240_000);
    await page.goto("/lab");

    const startButton = page.getByRole("button", { name: "启动任务" });
    await expect(startButton).toBeVisible();
    await startButton.click();

    await expect(page.getByText("G3引擎启动")).toBeVisible({
      timeout: 180_000,
    });
  });
});

test.describe("入口全流程验收", () => {
  test.skip(!ENABLE_ENTRY_FULL, "需要设置 E2E_ENTRY_FULL=1 才执行");

  test("入口：首页 -> 启动向导 -> /lab 日志出现", async ({ page }) => {
    test.setTimeout(240_000);
    await page.goto("/");

    const requirementInput = page.getByTestId("hero-requirement-input");
    await expect(requirementInput).toBeVisible();
    await requirementInput.fill("做一个可追踪任务进度的企业后台系统");

    const generateButton = page.getByTestId("hero-generate-button");
    await expect(generateButton).toBeVisible();
    await generateButton.click();

    await expect(page.getByTestId("smart-wizard")).toBeVisible({ timeout: 30_000 });

    await page.goto("/lab");
    const startButton = page.getByRole("button", { name: "启动任务" });
    await expect(startButton).toBeVisible();
    await startButton.click();

    await expect(page.getByText("G3引擎启动")).toBeVisible({
      timeout: 180_000,
    });
  });
});
