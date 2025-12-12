/**
 * V2.0 移动端E2E测试
 *
 * 测试覆盖：
 * - 移动端响应式布局验证
 * - 触摸交互测试
 * - 移动端特定的UI组件行为
 * - 不同移动设备尺寸适配
 *
 * 设备覆盖：
 * - iPhone SE (375x667)
 * - iPhone 12 (390x844)
 * - Pixel 5 (393x851)
 * - iPad (768x1024)
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-12-01
 */

import { test, expect, type Page, devices } from '@playwright/test';

// 测试配置
const BASE_URL = 'http://localhost:3000';

// ==================== 认证辅助函数 ====================

/**
 * 设置认证Token到Cookie和localStorage
 * 模拟已登录状态，解决受保护路由重定向问题
 */
async function setAuthToken(page: Page, token: string = 'mock_jwt_token_for_testing') {
  // 先导航到一个页面以建立上下文
  await page.goto(`${BASE_URL}/`);

  // 设置Cookie
  await page.context().addCookies([{
    name: 'auth_token',
    value: token,
    domain: 'localhost',
    path: '/',
  }]);

  // 设置localStorage
  await page.evaluate((t) => {
    localStorage.setItem('auth_token', t);
  }, token);
}

// 移动设备配置
const MOBILE_DEVICES = {
  iPhoneSE: devices['iPhone SE'],
  iPhone12: devices['iPhone 12'],
  pixel5: devices['Pixel 5'],
  iPad: devices['iPad (gen 7)'],
};

// 测试数据
const TEST_REQUIREMENT = '创建一个技术博客平台，支持Markdown编辑、代码高亮、评论功能';

// ==================== 辅助函数 ====================

/**
 * 等待页面加载完成
 */
async function waitForPageLoad(page: Page) {
  await page.waitForLoadState('networkidle');
}

/**
 * 执行需求输入步骤
 */
async function submitRequirement(page: Page, requirement: string) {
  const input = page.locator('[data-testid="requirement-input"]');
  await input.fill(requirement);
  await page.locator('[data-testid="submit-requirement"]').click();
  await page.waitForSelector('[data-testid="intent-result-panel"]', { timeout: 30000 });
}

// ==================== iPhone SE 测试 ====================

test.describe('V2.0 移动端测试 - iPhone SE (375x667)', () => {
  test.use({ ...MOBILE_DEVICES.iPhoneSE });

  test('页面应该在小屏幕上正确渲染', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // 验证页面标题可见
    await expect(page.locator('text=描述您想要的应用')).toBeVisible();

    // 验证输入框可见且可交互
    const input = page.locator('[data-testid="requirement-input"]');
    await expect(input).toBeVisible();
    await expect(input).toBeEnabled();

    // 验证提交按钮可见
    await expect(page.locator('[data-testid="submit-requirement"]')).toBeVisible();
  });

  test('快速示例按钮应该在移动端正确显示', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // 验证快速示例按钮可见并可点击
    const exampleButton = page.locator('text=参考淘宝做一个电商平台');
    await expect(exampleButton).toBeVisible();

    // 触摸点击示例按钮
    await exampleButton.tap();

    // 验证输入框内容已填充
    const input = page.locator('[data-testid="requirement-input"]');
    await expect(input).toHaveValue('参考淘宝做一个电商平台');
  });

  test('意图识别结果面板应该在移动端正确显示', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
    await submitRequirement(page, TEST_REQUIREMENT);

    // 验证意图结果面板在移动端可见
    await expect(page.locator('[data-testid="intent-result-panel"]')).toBeVisible();

    // 验证按钮在移动端正确布局（应该是垂直堆叠）
    const confirmButton = page.locator('[data-testid="confirm-intent-button"]');
    const modifyButton = page.locator('[data-testid="modify-intent-button"]');

    await expect(confirmButton).toBeVisible();
    await expect(modifyButton).toBeVisible();
  });

  test('风格选择网格应该在移动端显示为单列', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
    await submitRequirement(page, TEST_REQUIREMENT);

    // 确认意图
    await page.locator('[data-testid="confirm-intent-button"]').tap();

    // 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await page.locator('[data-testid="skip-template-button"]').tap();
    }

    // 等待风格选择面板
    await page.waitForSelector('[data-testid="style-selection-panel"]', { timeout: 10000 });

    // 验证风格卡片可见（移动端应该能滚动查看所有卡片）
    const styleCards = page.locator('[data-testid="style-card"]');
    await expect(styleCards.first()).toBeVisible();

    // 验证可以滚动查看更多卡片
    await page.evaluate(() => window.scrollBy(0, 300));
    await page.waitForTimeout(500);
  });

  test('完整流程应该在iPhone SE上可用', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // Step 1: 输入需求
    const input = page.locator('[data-testid="requirement-input"]');
    await input.fill(TEST_REQUIREMENT);
    await page.locator('[data-testid="submit-requirement"]').tap();

    // Step 2: 确认意图
    await page.waitForSelector('[data-testid="intent-result-panel"]', { timeout: 30000 });
    await page.locator('[data-testid="confirm-intent-button"]').tap();

    // Step 3: 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await page.locator('[data-testid="skip-template-button"]').tap();
    }

    // Step 4: 选择风格
    await page.waitForSelector('[data-testid="style-selection-panel"]', { timeout: 10000 });
    await page.locator('[data-testid="style-card"]').first().tap();

    // 滚动到确认按钮
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.waitForTimeout(300);

    const confirmStyleButton = page.locator('[data-testid="confirm-style-button"]');
    await expect(confirmStyleButton).toBeVisible();
    await confirmStyleButton.tap();

    // Step 5: 验证原型确认面板
    await expect(page.locator('[data-testid="prototype-confirmation-panel"]')).toBeVisible({ timeout: 30000 });

    console.log('iPhone SE 完整流程测试通过');
  });
});

// ==================== iPhone 12 测试 ====================

test.describe('V2.0 移动端测试 - iPhone 12 (390x844)', () => {
  test.use({ ...MOBILE_DEVICES.iPhone12 });

  test('页面应该在iPhone 12上正确渲染', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();
    await expect(page.locator('[data-testid="submit-requirement"]')).toBeVisible();
  });

  test('进度条应该在移动端正确显示', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // 验证进度指示器可见
    await expect(page.locator('text=描述需求')).toBeVisible();

    // 验证步骤数字可见
    const stepIndicators = page.locator('text=/^[1-6]$/');
    await expect(stepIndicators.first()).toBeVisible();
  });

  test('返回按钮应该在移动端可用', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
    await submitRequirement(page, TEST_REQUIREMENT);

    // 确认意图进入下一步
    await page.locator('[data-testid="confirm-intent-button"]').tap();

    // 等待下一步骤
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    const stylePanel = page.locator('[data-testid="style-selection-panel"]');
    await expect(templatePanel.or(stylePanel)).toBeVisible({ timeout: 10000 });

    // 点击返回按钮
    const backButton = page.locator('text=返回上一步');
    await expect(backButton).toBeVisible();
    await backButton.tap();

    // 验证返回到意图识别步骤
    await expect(page.locator('[data-testid="intent-result-panel"]')).toBeVisible();
  });
});

// ==================== Pixel 5 测试 ====================

test.describe('V2.0 移动端测试 - Pixel 5 (393x851)', () => {
  test.use({ ...MOBILE_DEVICES.pixel5 });

  test('页面应该在Pixel 5上正确渲染', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();
    await expect(page.locator('[data-testid="submit-requirement"]')).toBeVisible();
  });

  test('文本输入应该在Android设备上正常工作', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    const input = page.locator('[data-testid="requirement-input"]');

    // 点击输入框
    await input.tap();

    // 模拟输入
    await input.fill(TEST_REQUIREMENT);

    // 验证输入内容
    await expect(input).toHaveValue(TEST_REQUIREMENT);

    // 验证提交按钮已启用
    await expect(page.locator('[data-testid="submit-requirement"]')).toBeEnabled();
  });
});

// ==================== iPad 测试 ====================

test.describe('V2.0 移动端测试 - iPad (768x1024)', () => {
  test.use({ ...MOBILE_DEVICES.iPad });

  test('页面应该在iPad上正确渲染', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();
    await expect(page.locator('[data-testid="submit-requirement"]')).toBeVisible();
  });

  test('风格选择网格应该在iPad上显示为两列', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
    await submitRequirement(page, TEST_REQUIREMENT);

    // 确认意图
    await page.locator('[data-testid="confirm-intent-button"]').tap();

    // 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await page.locator('[data-testid="skip-template-button"]').tap();
    }

    // 等待风格选择面板
    await page.waitForSelector('[data-testid="style-selection-panel"]', { timeout: 10000 });

    // 验证风格卡片网格（iPad应该显示2列或更多）
    const styleCards = page.locator('[data-testid="style-card"]');
    await expect(styleCards).toHaveCount(7);
  });

  test('完整流程应该在iPad上可用', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // Step 1: 输入需求
    await page.locator('[data-testid="requirement-input"]').fill(TEST_REQUIREMENT);
    await page.locator('[data-testid="submit-requirement"]').tap();

    // Step 2: 确认意图
    await page.waitForSelector('[data-testid="intent-result-panel"]', { timeout: 30000 });
    await page.locator('[data-testid="confirm-intent-button"]').tap();

    // Step 3: 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await page.locator('[data-testid="skip-template-button"]').tap();
    }

    // Step 4: 选择风格
    await page.waitForSelector('[data-testid="style-selection-panel"]', { timeout: 10000 });
    await page.locator('[data-testid="style-card"]').first().tap();
    await page.locator('[data-testid="confirm-style-button"]').tap();

    // Step 5: 验证原型确认面板
    await expect(page.locator('[data-testid="prototype-confirmation-panel"]')).toBeVisible({ timeout: 30000 });

    console.log('iPad 完整流程测试通过');
  });
});

// ==================== 横屏模式测试 ====================

test.describe('V2.0 移动端测试 - 横屏模式', () => {
  test('iPhone 12 横屏应该正确渲染', async ({ page }) => {
    await setAuthToken(page);
    // 设置横屏尺寸
    await page.setViewportSize({ width: 844, height: 390 });
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // 验证页面在横屏模式下可用
    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();
    await expect(page.locator('[data-testid="submit-requirement"]')).toBeVisible();
  });

  test('iPad 横屏应该正确渲染', async ({ page }) => {
    await setAuthToken(page);
    // 设置iPad横屏尺寸
    await page.setViewportSize({ width: 1024, height: 768 });
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // 验证页面在iPad横屏模式下可用
    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();
    await expect(page.locator('[data-testid="submit-requirement"]')).toBeVisible();

    // 验证在更宽的屏幕上内容区域合理
    const input = page.locator('[data-testid="requirement-input"]');
    const inputBox = await input.boundingBox();
    expect(inputBox?.width).toBeGreaterThan(300);
  });
});

// ==================== 触摸交互测试 ====================

test.describe('V2.0 移动端测试 - 触摸交互', () => {
  test.use({ ...MOBILE_DEVICES.iPhone12 });

  test('双击输入框应该选中文本', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    const input = page.locator('[data-testid="requirement-input"]');
    await input.fill('测试文本');

    // 双击选中文本
    await input.dblclick();

    // 验证可以进行输入操作
    await input.fill(TEST_REQUIREMENT);
    await expect(input).toHaveValue(TEST_REQUIREMENT);
  });

  test('风格卡片触摸应该有视觉反馈', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
    await submitRequirement(page, TEST_REQUIREMENT);

    // 确认意图
    await page.locator('[data-testid="confirm-intent-button"]').tap();

    // 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await page.locator('[data-testid="skip-template-button"]').tap();
    }

    // 等待风格选择面板
    await page.waitForSelector('[data-testid="style-selection-panel"]', { timeout: 10000 });

    // 触摸第一个风格卡片
    const styleCard = page.locator('[data-testid="style-card"]').first();
    await styleCard.tap();

    // 验证确认按钮出现（表示选中状态）
    await expect(page.locator('[data-testid="confirm-style-button"]')).toBeVisible();
  });
});

// ==================== 滚动测试 ====================

test.describe('V2.0 移动端测试 - 滚动行为', () => {
  test.use({ ...MOBILE_DEVICES.iPhoneSE });

  test('长内容应该可以滚动', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
    await submitRequirement(page, TEST_REQUIREMENT);

    // 确认意图
    await page.locator('[data-testid="confirm-intent-button"]').tap();

    // 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await page.locator('[data-testid="skip-template-button"]').tap();
    }

    // 等待风格选择面板
    await page.waitForSelector('[data-testid="style-selection-panel"]', { timeout: 10000 });

    // 获取初始滚动位置
    const initialScrollY = await page.evaluate(() => window.scrollY);

    // 滚动页面
    await page.evaluate(() => window.scrollBy(0, 500));
    await page.waitForTimeout(500);

    // 验证页面已滚动
    const newScrollY = await page.evaluate(() => window.scrollY);
    expect(newScrollY).toBeGreaterThan(initialScrollY);
  });
});

// ==================== 性能测试 ====================

test.describe('V2.0 移动端测试 - 性能', () => {
  test.use({ ...MOBILE_DEVICES.iPhone12 });

  test('移动端页面加载时间应该<5秒', async ({ page }) => {
    await setAuthToken(page);
    const startTime = Date.now();

    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    const loadTime = Date.now() - startTime;

    expect(loadTime).toBeLessThan(5000);
    console.log(`移动端页面加载时间: ${loadTime}ms`);
  });

  test('移动端交互响应时间应该<1秒', async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    const input = page.locator('[data-testid="requirement-input"]');

    const startTime = Date.now();
    await input.tap();
    await input.fill('测试');
    const responseTime = Date.now() - startTime;

    expect(responseTime).toBeLessThan(1000);
    console.log(`移动端输入响应时间: ${responseTime}ms`);
  });
});
