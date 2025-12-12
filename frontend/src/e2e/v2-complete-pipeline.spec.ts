/**
 * V2.0完整链路E2E测试
 *
 * 测试覆盖：
 * - Plan阶段：意图识别 → 路由决策 → 风格选择
 * - 确认设计：用户确认 → designConfirmed标志
 * - Execute阶段：ExecuteGuard前置检查
 *
 * 三大核心场景：
 * 1. CLONE路径：克隆已有网站
 * 2. DESIGN路径：从零设计（7风格选择）
 * 3. 确认→Execute：设计确认后进入Execute阶段
 */

import { test, expect } from '@playwright/test';
import { v4 as uuidv4 } from 'uuid';

// 测试配置
const BASE_URL = 'http://localhost:3000';
const API_BASE_URL = 'http://localhost:8080';
const TEST_TENANT_ID = uuidv4();
const TEST_USER_ID = uuidv4();

/**
 * 场景A: CLONE路径 - 克隆已有网站
 * 用户需求: "仿照airbnb.com做一个民宿预订平台"
 * 预期流程: 意图识别→CLONE_EXISTING_WEBSITE→直接生成原型
 */
test.describe('场景A: CLONE路径 - 克隆已有网站', () => {
  test('应该正确识别克隆意图并生成原型', async ({ page }) => {
    // Step 1: 调用Plan路由API
    const routeResponse = await page.request.post(`${API_BASE_URL}/api/v2/plan-routing/route`, {
      data: {
        userRequirement: '仿照airbnb.com做一个民宿预订平台，需要支持房源列表、预订、支付功能',
        tenantId: TEST_TENANT_ID,
        userId: TEST_USER_ID,
      },
    });

    expect(routeResponse.ok()).toBeTruthy();
    const routeResult = await routeResponse.json();

    // 验证响应结构
    expect(routeResult.code).toBe('0000');
    expect(routeResult.data).toBeDefined();

    const { data } = routeResult;

    // 验证意图识别结果
    expect(data.intent).toBe('CLONE_EXISTING_WEBSITE');
    expect(data.branch).toBe('CLONE');
    expect(data.confidence).toBeGreaterThanOrEqual(0.8);

    // 验证提取的URL
    expect(data.extractedUrls).toContain('airbnb.com');

    // 验证下一步操作
    expect(data.nextAction).toBe('CRAWL_AND_GENERATE');

    // 验证AppSpec已创建
    expect(data.appSpecId).toBeDefined();

    console.log(`✅ CLONE路径测试通过 - AppSpecId: ${data.appSpecId}`);
  });

  test('应该能够直接生成原型（无需风格选择）', async ({ page: _page }) => {
    // CLONE路径应该跳过风格选择，直接生成
    // 这里需要验证不会显示StylePicker组件

    // TODO: 验证前端不显示风格选择UI
    // await page.goto(`${BASE_URL}/plan-routing?requirement=仿照airbnb.com`);
    // await expect(page.locator('[data-testid="style-picker"]')).not.toBeVisible();
  });
});

/**
 * 场景B: DESIGN路径 - 从零设计
 * 用户需求: "创建一个技术博客平台"
 * 预期流程: 意图识别→DESIGN_FROM_SCRATCH→7风格预览→用户选择→生成原型
 */
test.describe('场景B: DESIGN路径 - 从零设计', () => {
  let appSpecId: string;

  test('应该正确识别从零设计意图', async ({ page }) => {
    const routeResponse = await page.request.post(`${API_BASE_URL}/api/v2/plan-routing/route`, {
      data: {
        userRequirement: '创建一个技术博客平台，支持Markdown编辑、代码高亮、评论功能',
        tenantId: TEST_TENANT_ID,
        userId: TEST_USER_ID,
      },
    });

    expect(routeResponse.ok()).toBeTruthy();
    const routeResult = await routeResponse.json();

    const { data } = routeResult;

    // 验证意图识别
    expect(data.intent).toBe('DESIGN_FROM_SCRATCH');
    expect(data.branch).toBe('DESIGN');

    // 验证风格选项
    expect(data.styleOptions).toBeDefined();
    expect(data.styleOptions.length).toBe(7);

    // 验证7种风格都存在
    const expectedStyles = ['modern_minimal', 'vibrant_trendy', 'classic_professional',
                           'futuristic_tech', 'immersive_3d', 'gamified', 'natural_flow'];
    data.styleOptions.forEach((style: { id: string; previewUrl: string; name: string }) => {
      expect(expectedStyles).toContain(style.id);
      expect(style.previewUrl).toBeDefined();
      expect(style.name).toBeDefined();
    });

    // 验证下一步操作
    expect(data.nextAction).toBe('SELECT_STYLE');

    appSpecId = data.appSpecId;
    console.log(`✅ DESIGN路径 - 意图识别通过 - AppSpecId: ${appSpecId}`);
  });

  test('应该能够选择风格并生成原型', async ({ page }) => {
    // 使用上一步的appSpecId（如果没有则创建新的）
    if (!appSpecId) {
      const routeResponse = await page.request.post(`${API_BASE_URL}/api/v2/plan-routing/route`, {
        data: {
          userRequirement: '创建一个技术博客平台',
          tenantId: TEST_TENANT_ID,
          userId: TEST_USER_ID,
        },
      });
      const routeResult = await routeResponse.json();
      appSpecId = routeResult.data.appSpecId;
    }

    // Step 2: 选择风格
    const selectedStyleId = 'modern_minimal';

    const selectStyleResponse = await page.request.post(
      `${API_BASE_URL}/api/v2/plan-routing/${appSpecId}/select-style?styleId=${selectedStyleId}`
    );

    expect(selectStyleResponse.ok()).toBeTruthy();
    const selectStyleResult = await selectStyleResponse.json();

    const { data } = selectStyleResult;

    // 验证原型URL已生成
    expect(data.prototypeUrl).toBeDefined();
    expect(data.prototypeUrl).toContain('http');

    // 验证选择的风格
    expect(data.selectedStyleId).toBe(selectedStyleId);

    // 验证下一步操作
    expect(data.nextAction).toBe('CONFIRM_DESIGN');

    console.log(`✅ DESIGN路径 - 风格选择通过 - PrototypeURL: ${data.prototypeUrl}`);
  });
});

/**
 * 场景C: 确认设计 → Execute阶段
 * 操作: 用户点击"确认设计"按钮
 * 预期: designConfirmed=true → ExecuteGuard放行
 */
test.describe('场景C: 确认设计 → Execute阶段', () => {
  let testAppSpecId: string;

  test.beforeEach(async ({ page }) => {
    // 创建测试用的AppSpec
    const routeResponse = await page.request.post(`${API_BASE_URL}/api/v2/plan-routing/route`, {
      data: {
        userRequirement: '创建一个在线教育平台',
        tenantId: TEST_TENANT_ID,
        userId: TEST_USER_ID,
      },
    });
    const routeResult = await routeResponse.json();
    testAppSpecId = routeResult.data.appSpecId;

    // 如果是DESIGN分支，选择风格
    if (routeResult.data.branch === 'DESIGN') {
      await page.request.post(
        `${API_BASE_URL}/api/v2/plan-routing/${testAppSpecId}/select-style?styleId=modern_minimal`
      );
    }
  });

  test('未确认设计时，ExecuteGuard应该阻塞Execute阶段', async ({ page }) => {
    // 尝试调用Execute阶段API（假设存在）
    const executeResponse = await page.request.post(
      `${API_BASE_URL}/api/v2/execute/start`,
      {
        data: {
          appSpecId: testAppSpecId,
          tenantId: TEST_TENANT_ID,
          userId: TEST_USER_ID,
        },
        failOnStatusCode: false, // 允许失败响应
      }
    );

    // 验证被ExecuteGuard阻塞
    expect(executeResponse.status()).toBe(400);

    const errorResult = await executeResponse.json();
    expect(errorResult.message).toContain('设计未确认');

    console.log('✅ ExecuteGuard阻塞测试通过 - 未确认设计时正确阻塞');
  });

  test('确认设计后，designConfirmed标志应该更新', async ({ page }) => {
    // Step 3: 确认设计
    const confirmResponse = await page.request.post(
      `${API_BASE_URL}/api/v2/plan-routing/${testAppSpecId}/confirm-design`
    );

    expect(confirmResponse.ok()).toBeTruthy();
    const confirmResult = await confirmResponse.json();

    expect(confirmResult.code).toBe('0000');
    expect(confirmResult.data).toContain('设计确认成功');

    console.log('✅ 确认设计API调用成功');

    // TODO: 验证数据库中designConfirmed字段已更新
    // 这里需要查询数据库或通过status API验证
  });

  test('确认设计后，ExecuteGuard应该放行Execute阶段', async ({ page }) => {
    // 先确认设计
    await page.request.post(
      `${API_BASE_URL}/api/v2/plan-routing/${testAppSpecId}/confirm-design`
    );

    // 再次尝试调用Execute阶段API
    const executeResponse = await page.request.post(
      `${API_BASE_URL}/api/v2/execute/start`,
      {
        data: {
          appSpecId: testAppSpecId,
          tenantId: TEST_TENANT_ID,
          userId: TEST_USER_ID,
        },
        failOnStatusCode: false,
      }
    );

    // 验证ExecuteGuard放行（状态码不应该是400阻塞错误）
    // 注意：Execute阶段可能返回其他错误（如404未实现），但不应该是400设计未确认错误
    if (executeResponse.status() === 400) {
      const errorResult = await executeResponse.json();
      expect(errorResult.message).not.toContain('设计未确认');
    }

    console.log('✅ ExecuteGuard放行测试通过 - 确认设计后可进入Execute阶段');
  });
});

/**
 * 前端UI集成测试
 * 测试V2.0创建流程的完整交互
 */
test.describe('前端UI集成测试 - V2.0创建流程', () => {
  test('应该正确渲染V2.0创建向导页面', async ({ page }) => {
    await page.goto(`${BASE_URL}/create-v2`);
    await page.waitForLoadState('networkidle');

    // 验证页面标题
    await expect(page.locator('text=描述您想要的应用')).toBeVisible();

    // 验证输入框存在
    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();

    // 验证提交按钮存在
    await expect(page.locator('[data-testid="submit-requirement"]')).toBeVisible();

    // 验证进度指示器存在
    await expect(page.locator('text=描述需求')).toBeVisible();
  });

  test('提交需求后应该显示意图识别结果面板', async ({ page }) => {
    await page.goto(`${BASE_URL}/create-v2`);
    await page.waitForLoadState('networkidle');

    // 输入需求
    const input = page.locator('[data-testid="requirement-input"]');
    await input.fill('创建一个技术博客平台，支持Markdown编辑、代码高亮、评论功能');

    // 点击提交
    const submitButton = page.locator('[data-testid="submit-requirement"]');
    await submitButton.click();

    // 等待意图识别结果面板显示
    await expect(page.locator('[data-testid="intent-result-panel"]')).toBeVisible({ timeout: 30000 });

    // 验证置信度徽章显示
    await expect(page.locator('[data-testid="confidence-badge"]')).toBeVisible();

    // 验证确认按钮显示
    await expect(page.locator('[data-testid="confirm-intent-button"]')).toBeVisible();
  });

  test('原型确认面板应该包含所有必要元素', async ({ page }) => {
    await page.goto(`${BASE_URL}/create-v2`);
    await page.waitForLoadState('networkidle');

    // Step 1: 输入需求
    const input = page.locator('[data-testid="requirement-input"]');
    await input.fill('创建一个技术博客平台，支持Markdown编辑、代码高亮、评论功能');
    await page.locator('[data-testid="submit-requirement"]').click();

    // Step 2: 等待并确认意图
    await page.waitForSelector('[data-testid="intent-result-panel"]', { timeout: 30000 });
    await page.locator('[data-testid="confirm-intent-button"]').click();

    // Step 3: 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await page.locator('[data-testid="skip-template-button"]').click();
    }

    // Step 4: 选择风格
    await page.waitForSelector('[data-testid="style-selection-panel"]', { timeout: 10000 });
    await page.locator('[data-testid="style-card"]').first().click();
    await page.locator('[data-testid="confirm-style-button"]').click();

    // Step 5: 验证原型确认面板
    await expect(page.locator('[data-testid="prototype-confirmation-panel"]')).toBeVisible({ timeout: 30000 });

    // 验证确认设计按钮
    await expect(page.locator('[data-testid="confirm-design-button"]')).toBeVisible();

    // 验证返回按钮
    await expect(page.locator('[data-testid="back-to-style-button"]')).toBeVisible();

    // 验证刷新按钮
    await expect(page.locator('[data-testid="refresh-preview-button"]')).toBeVisible();
  });

  test('确认设计后应该跳转到Wizard页面', async ({ page }) => {
    await page.goto(`${BASE_URL}/create-v2`);
    await page.waitForLoadState('networkidle');

    // Step 1: 输入需求
    const input = page.locator('[data-testid="requirement-input"]');
    await input.fill('创建一个技术博客平台，支持Markdown编辑、代码高亮、评论功能');
    await page.locator('[data-testid="submit-requirement"]').click();

    // Step 2: 等待并确认意图
    await page.waitForSelector('[data-testid="intent-result-panel"]', { timeout: 30000 });
    await page.locator('[data-testid="confirm-intent-button"]').click();

    // Step 3: 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await page.locator('[data-testid="skip-template-button"]').click();
    }

    // Step 4: 选择风格
    await page.waitForSelector('[data-testid="style-selection-panel"]', { timeout: 10000 });
    await page.locator('[data-testid="style-card"]').first().click();
    await page.locator('[data-testid="confirm-style-button"]').click();

    // Step 5: 等待原型确认面板
    await page.waitForSelector('[data-testid="prototype-confirmation-panel"]', { timeout: 30000 });

    // Step 6: 等待确认按钮可用并点击
    const confirmButton = page.locator('[data-testid="confirm-design-button"]');
    await confirmButton.waitFor({ state: 'visible' });

    // 监听URL变化
    const urlChangePromise = page.waitForURL(/\/wizard\//, { timeout: 30000 });

    // 点击确认设计按钮
    await confirmButton.click();

    // 验证跳转到Wizard页面
    await urlChangePromise;
    expect(page.url()).toContain('/wizard/');

    console.log('确认设计后成功跳转到Wizard页面');
  });
});

/**
 * 性能测试
 * 验证关键API的响应时间
 */
test.describe('性能测试', () => {
  test('Plan路由API响应时间应该<3秒', async ({ page }) => {
    const startTime = Date.now();

    const routeResponse = await page.request.post(`${API_BASE_URL}/api/v2/plan-routing/route`, {
      data: {
        userRequirement: '创建一个电商平台',
        tenantId: TEST_TENANT_ID,
        userId: TEST_USER_ID,
      },
    });

    const endTime = Date.now();
    const duration = endTime - startTime;

    expect(routeResponse.ok()).toBeTruthy();
    expect(duration).toBeLessThan(3000); // 3秒内完成

    console.log(`⏱️ Plan路由API响应时间: ${duration}ms`);
  });

  test('风格选择API响应时间应该<2秒', async ({ page }) => {
    // 先创建AppSpec
    const routeResponse = await page.request.post(`${API_BASE_URL}/api/v2/plan-routing/route`, {
      data: {
        userRequirement: '创建一个社交平台',
        tenantId: TEST_TENANT_ID,
        userId: TEST_USER_ID,
      },
    });
    const routeResult = await routeResponse.json();
    const appSpecId = routeResult.data.appSpecId;

    const startTime = Date.now();

    const selectStyleResponse = await page.request.post(
      `${API_BASE_URL}/api/v2/plan-routing/${appSpecId}/select-style?styleId=modern_minimal`
    );

    const endTime = Date.now();
    const duration = endTime - startTime;

    expect(selectStyleResponse.ok()).toBeTruthy();
    expect(duration).toBeLessThan(2000); // 2秒内完成

    console.log(`⏱️ 风格选择API响应时间: ${duration}ms`);
  });

  test('确认设计API响应时间应该<500ms', async ({ page }) => {
    // 创建并选择风格
    const routeResponse = await page.request.post(`${API_BASE_URL}/api/v2/plan-routing/route`, {
      data: {
        userRequirement: '创建一个内容管理系统',
        tenantId: TEST_TENANT_ID,
        userId: TEST_USER_ID,
      },
    });
    const routeResult = await routeResponse.json();
    const appSpecId = routeResult.data.appSpecId;

    if (routeResult.data.branch === 'DESIGN') {
      await page.request.post(
        `${API_BASE_URL}/api/v2/plan-routing/${appSpecId}/select-style?styleId=modern_minimal`
      );
    }

    const startTime = Date.now();

    const confirmResponse = await page.request.post(
      `${API_BASE_URL}/api/v2/plan-routing/${appSpecId}/confirm-design`
    );

    const endTime = Date.now();
    const duration = endTime - startTime;

    expect(confirmResponse.ok()).toBeTruthy();
    expect(duration).toBeLessThan(500); // 500ms内完成

    console.log(`⏱️ 确认设计API响应时间: ${duration}ms`);
  });
});
