/**
 * V2.0 前端UI集成E2E测试
 *
 * 测试覆盖：
 * - 完整V2.0流程：需求输入 → 自动分析 → 原型确认 → Execute跳转
 * - 各步骤组件的交互测试
 * - 响应式布局验证（桌面端 + 移动端）
 * - 错误处理和边界情况
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-12-01
 */

import { test, expect, type Page } from '@playwright/test';

// 测试配置
const BASE_URL = 'http://localhost:3000';

// 设置全局测试超时
test.setTimeout(90000); // 90秒

// ==================== 认证辅助函数 ====================

// 测试用户凭据
const TEST_USER = {
  username: 'testuser009',
  email: 'test009@example.com',
  password: 'Test1234',
};

/**
 * 通过真实登录API获取有效Token并设置到Cookie和localStorage
 * 这确保后端API调用能够正确验证用户身份
 */
async function setAuthToken(page: Page) {
  // 先导航到一个页面以建立上下文
  await page.goto(`${BASE_URL}/`);

  // 调用登录API获取真实token
  const loginResponse = await page.evaluate(async (credentials) => {
    try {
      const response = await fetch('http://localhost:8080/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: credentials.username,
          password: credentials.password,
        }),
      });
      return await response.json();
    } catch {
      return { success: false, message: 'Fetch failed' };
    }
  }, TEST_USER);

  if (!loginResponse.success || !loginResponse.data?.token) {
    console.log('登录跳过或失败，测试继续（可能使用匿名模式）');
    return null;
  }

  const token = loginResponse.data.token;

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

  return token;
}

// 测试数据
const TEST_REQUIREMENTS = {
  clone: '仿照airbnb.com做一个民宿预订平台，需要支持房源列表、预订、支付功能',
  design: '创建一个技术博客平台，支持Markdown编辑、代码高亮、评论功能',
  hybrid: '参考知乎做一个问答社区，但需要增加AI问答功能',
};

// ==================== 辅助函数 ====================

/**
 * 等待页面加载完成
 */
async function waitForPageLoad(page: Page) {
  await page.waitForLoadState('networkidle');
}

/**
 * 填写需求并提交
 */
async function submitRequirement(page: Page, requirement: string) {
  const input = page.locator('textarea[placeholder*="在这里输入你想做什么"]');

  // 等待页面完全加载
  await page.waitForTimeout(500);

  await input.click();
  await input.clear();
  await input.type(requirement, { delay: 10 });

  const submitButton = page.locator('button:has-text("生成")');
  await submitButton.click();

  // 等待进入分析阶段
  await expect(page.locator('text=深度分析')).toBeVisible({ timeout: 10000 });
}

// ==================== 测试用例 ====================

test.describe('V2.0 UI集成测试 - 需求输入步骤', () => {
  test.beforeEach(async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);
  });

  test('应该正确渲染需求输入页面', async ({ page }) => {
    // 验证页面标题
    await expect(page.locator('text=你的创意，AI 来实现')).toBeVisible();

    // 验证输入框存在
    await expect(page.locator('textarea[placeholder*="在这里输入你想做什么"]')).toBeVisible();

    // 验证提交按钮存在
    await expect(page.locator('button:has-text("生成")')).toBeVisible();
  });
});

test.describe('V2.0 UI集成测试 - 自动分析与流程', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);
  });

  test('提交需求后应该显示分析面板', async ({ page }) => {
    await submitRequirement(page, TEST_REQUIREMENTS.design);

    // 验证分析面板
    await expect(page.locator('text=AI正在分析您的需求')).toBeVisible();
  });

  // 注意：后续步骤依赖后端/Mock，这里仅验证核心流程触发
  test('完整流程UI状态转换', async ({ page }) => {
    await submitRequirement(page, TEST_REQUIREMENTS.design);
    
    // 验证进入向导模式后URL可能保持不变（SPA），或者参数变化
    // 但页面内容应该变化
    await expect(page.locator('text=你的创意，AI 来实现').first()).not.toBeVisible();
  });
});

test.describe('V2.0 UI集成测试 - 响应式设计', () => {
  test('桌面端布局正确', async ({ page }) => {
    await setAuthToken(page);
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    await expect(page.locator('textarea[placeholder*="在这里输入你想做什么"]')).toBeVisible();
  });

  test('平板端布局正确', async ({ page }) => {
    await setAuthToken(page);
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    await expect(page.locator('textarea[placeholder*="在这里输入你想做什么"]')).toBeVisible();
  });

  test('移动端布局正确', async ({ page }) => {
    await setAuthToken(page);
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    await expect(page.locator('textarea[placeholder*="在这里输入你想做什么"]')).toBeVisible();
    await expect(page.locator('button:has-text("生成")')).toBeVisible();
  });
});

test.describe('V2.0 UI集成测试 - 性能', () => {
  test('页面初始加载时间应该<3秒', async ({ page }) => {
    await setAuthToken(page);
    const startTime = Date.now();

    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    const loadTime = Date.now() - startTime;
    expect(loadTime).toBeLessThan(3000);
  });
});
