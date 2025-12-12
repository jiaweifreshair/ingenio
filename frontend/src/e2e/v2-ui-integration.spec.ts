/**
 * V2.0 前端UI集成E2E测试
 *
 * 测试覆盖：
 * - 完整V2.0流程：需求输入 → 意图识别 → 模板选择 → 风格选择 → 原型确认 → Execute跳转
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
const API_BASE_URL = 'http://localhost:8080';

// AI API超时配置（意图识别可能需要较长时间）
const AI_API_TIMEOUT = 60000; // 60秒

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
    const response = await fetch('http://localhost:8080/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        usernameOrEmail: credentials.username,
        password: credentials.password,
      }),
    });
    return response.json();
  }, TEST_USER);

  if (!loginResponse.success || !loginResponse.data?.token) {
    console.error('登录失败:', loginResponse);
    throw new Error(`登录失败: ${loginResponse.message}`);
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
 * 使用点击快速示例按钮或type模拟键盘输入，确保React状态正确更新
 */
async function submitRequirement(page: Page, requirement: string) {
  const input = page.locator('[data-testid="requirement-input"]');

  // 等待页面完全加载
  await page.waitForTimeout(500);

  // 优先使用快速示例按钮（如果匹配的话）
  const exampleButtons = [
    '参考淘宝做一个电商平台',
    '设计一个在线教育系统',
    '仿照知乎做一个问答社区',
  ];

  let usedExampleButton = false;
  for (const example of exampleButtons) {
    if (requirement.includes(example) || example.includes(requirement.substring(0, 10))) {
      const exampleButton = page.locator(`button:has-text("${example}")`);
      if (await exampleButton.isVisible()) {
        await exampleButton.click();
        usedExampleButton = true;
        break;
      }
    }
  }

  // 如果没有匹配的快速示例，使用type输入
  if (!usedExampleButton) {
    await input.click();
    await input.clear();
    await input.type(requirement, { delay: 10 });
  }

  // 等待React状态更新
  await page.waitForTimeout(500);

  const submitButton = page.locator('[data-testid="submit-requirement"]');
  // 等待按钮启用
  await expect(submitButton).toBeEnabled({ timeout: 5000 });
  await submitButton.click();

  // 等待意图识别结果加载（AI API可能需要较长时间）
  await page.waitForSelector('[data-testid="intent-result-panel"]', { timeout: AI_API_TIMEOUT });
}

/**
 * 确认意图并继续
 */
async function confirmIntent(page: Page) {
  const confirmButton = page.locator('[data-testid="confirm-intent-button"]');
  await confirmButton.click();
}

/**
 * 跳过模板选择
 */
async function skipTemplateSelection(page: Page) {
  const skipButton = page.locator('[data-testid="skip-template-button"]');
  if (await skipButton.isVisible()) {
    await skipButton.click();
  }
}

/**
 * 选择设计风格
 * @param waitForPrototype 是否等待原型确认面板出现（原型生成API可能需要很长时间）
 */
async function selectStyle(page: Page, styleIndex: number = 0, waitForPrototype: boolean = true) {
  // 等待风格选择面板加载
  await page.waitForSelector('[data-testid="style-selection-panel"]', { timeout: 10000 });

  // 选择指定索引的风格卡片
  const styleCards = page.locator('[data-testid="style-card"]');
  await styleCards.nth(styleIndex).click();

  // 点击确认风格按钮
  const confirmStyleButton = page.locator('[data-testid="confirm-style-button"]');
  await confirmStyleButton.click();

  // 等待原型确认面板出现（原型生成API可能需要较长时间）
  if (waitForPrototype) {
    await page.waitForSelector('[data-testid="prototype-confirmation-panel"]', { timeout: AI_API_TIMEOUT });
  }
}

// ==================== 测试用例 ====================

test.describe('V2.0 UI集成测试 - 需求输入步骤', () => {
  test.beforeEach(async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
  });

  test('应该正确渲染需求输入页面', async ({ page }) => {
    // 验证页面标题
    await expect(page.locator('text=描述您想要的应用')).toBeVisible();

    // 验证输入框存在
    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();

    // 验证提交按钮存在且初始禁用
    const submitButton = page.locator('[data-testid="submit-requirement"]');
    await expect(submitButton).toBeVisible();
    await expect(submitButton).toBeDisabled();
  });

  test('输入少于10个字符时提交按钮应该禁用', async ({ page }) => {
    const input = page.locator('[data-testid="requirement-input"]');

    // 等待页面完全hydration完成
    await page.waitForTimeout(500);

    // 使用type模拟真实键盘输入（短文本）
    await input.click();
    await input.type('短需求', { delay: 50 });

    await page.waitForTimeout(300);
    const submitButton = page.locator('[data-testid="submit-requirement"]');
    await expect(submitButton).toBeDisabled();
  });

  test('输入超过10个字符时提交按钮应该启用', async ({ page }) => {
    const input = page.locator('[data-testid="requirement-input"]');

    // 等待页面完全hydration完成
    await page.waitForTimeout(1000);

    // 方法1：点击快速示例按钮（最可靠的方式）
    const exampleButton = page.locator('button:has-text("参考淘宝做一个电商平台")');
    if (await exampleButton.isVisible()) {
      await exampleButton.click();
      await page.waitForTimeout(500);
    } else {
      // 方法2：使用type模拟真实键盘输入（比fill更接近用户行为）
      await input.click();
      await input.type('这是一个超过十个字符的需求描述', { delay: 50 });
    }

    const submitButton = page.locator('[data-testid="submit-requirement"]');
    await expect(submitButton).toBeEnabled({ timeout: 5000 });
  });

  test('点击快速示例应该填充输入框', async ({ page }) => {
    // 等待快速示例按钮加载
    const exampleButton = page.locator('button:has-text("参考淘宝做一个电商平台")');
    await expect(exampleButton).toBeVisible({ timeout: 10000 });

    // 点击快速示例按钮
    await exampleButton.click();

    // 等待React状态更新
    await page.waitForTimeout(500);

    // 等待输入框值更新
    const input = page.locator('[data-testid="requirement-input"]');
    await expect(input).toHaveValue('参考淘宝做一个电商平台', { timeout: 5000 });

    // 提交按钮应该启用
    const submitButton = page.locator('[data-testid="submit-requirement"]');
    await expect(submitButton).toBeEnabled({ timeout: 5000 });
  });
});

test.describe('V2.0 UI集成测试 - 意图识别步骤', () => {
  test.beforeEach(async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
  });

  test('提交需求后应该显示意图识别结果', async ({ page }) => {
    await submitRequirement(page, TEST_REQUIREMENTS.design);

    // 验证意图结果面板显示
    await expect(page.locator('[data-testid="intent-result-panel"]')).toBeVisible();

    // 验证置信度徽章显示
    await expect(page.locator('[data-testid="confidence-badge"]')).toBeVisible();

    // 验证确认和修改按钮显示
    await expect(page.locator('[data-testid="confirm-intent-button"]')).toBeVisible();
    await expect(page.locator('[data-testid="modify-intent-button"]')).toBeVisible();
  });

  test('点击修改意图应该返回需求输入步骤', async ({ page }) => {
    await submitRequirement(page, TEST_REQUIREMENTS.design);

    // 点击修改意图按钮
    await page.locator('[data-testid="modify-intent-button"]').click();

    // 验证返回需求输入步骤
    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();
  });

  test('确认意图后应该进入下一步', async ({ page }) => {
    await submitRequirement(page, TEST_REQUIREMENTS.design);
    await confirmIntent(page);

    // 应该进入模板选择或风格选择步骤
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    const stylePanel = page.locator('[data-testid="style-selection-panel"]');

    // 等待其中一个面板显示
    await expect(templatePanel.or(stylePanel)).toBeVisible({ timeout: 10000 });
  });
});

test.describe('V2.0 UI集成测试 - 模板选择步骤', () => {
  test('跳过模板选择应该进入风格选择步骤', async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
    await submitRequirement(page, TEST_REQUIREMENTS.design);
    await confirmIntent(page);

    // 如果显示模板选择面板，跳过它
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await skipTemplateSelection(page);
    }

    // 验证进入风格选择步骤
    await expect(page.locator('[data-testid="style-selection-panel"]')).toBeVisible({ timeout: 10000 });
  });
});

test.describe('V2.0 UI集成测试 - 风格选择步骤', () => {
  test.beforeEach(async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
    await submitRequirement(page, TEST_REQUIREMENTS.design);
    await confirmIntent(page);

    // 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await skipTemplateSelection(page);
    }
  });

  test('应该显示7种设计风格卡片', async ({ page }) => {
    await expect(page.locator('[data-testid="style-selection-panel"]')).toBeVisible();

    // 验证有7个风格卡片
    const styleCards = page.locator('[data-testid="style-card"]');
    await expect(styleCards).toHaveCount(7);
  });

  test('点击风格卡片应该选中并显示确认按钮', async ({ page }) => {
    const styleCards = page.locator('[data-testid="style-card"]');

    // 点击第一个风格卡片
    await styleCards.first().click();

    // 验证确认按钮显示
    await expect(page.locator('[data-testid="confirm-style-button"]')).toBeVisible();
  });

  // 注意：原型生成依赖OpenLovable API，响应时间不稳定（可能超过60秒）
  // 此测试在CI环境中可能因超时而失败，生产环境需要监控API性能
  test.skip('确认风格后应该进入原型确认步骤', async ({ page }) => {
    // selectStyle 函数已经等待 prototype-confirmation-panel 出现
    await selectStyle(page, 0);

    // 验证进入原型确认步骤（验证 selectStyle 已经完成）
    await expect(page.locator('[data-testid="prototype-confirmation-panel"]')).toBeVisible();
  });
});

// 注意：原型确认步骤的所有测试都依赖OpenLovable原型生成API
// 该API响应时间不稳定（可能超过60秒），在CI环境中可能导致测试超时
// 这些测试应该在API性能优化后启用
test.describe.skip('V2.0 UI集成测试 - 原型确认步骤', () => {
  test.beforeEach(async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);
    await submitRequirement(page, TEST_REQUIREMENTS.design);
    await confirmIntent(page);

    // 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await skipTemplateSelection(page);
    }

    await selectStyle(page, 0);
  });

  test('应该显示原型确认面板', async ({ page }) => {
    await expect(page.locator('[data-testid="prototype-confirmation-panel"]')).toBeVisible();

    // 验证刷新按钮存在
    await expect(page.locator('[data-testid="refresh-preview-button"]')).toBeVisible();

    // 验证返回按钮存在
    await expect(page.locator('[data-testid="back-to-style-button"]')).toBeVisible();

    // 验证确认设计按钮存在
    await expect(page.locator('[data-testid="confirm-design-button"]')).toBeVisible();
  });

  test('点击返回按钮应该返回风格选择步骤', async ({ page }) => {
    await page.locator('[data-testid="back-to-style-button"]').click();

    // 验证返回风格选择步骤
    await expect(page.locator('[data-testid="style-selection-panel"]')).toBeVisible();
  });

  test('原型预览iframe应该加载（如果有URL）', async ({ page }) => {
    // 等待iframe出现（如果原型URL存在）
    const iframe = page.locator('[data-testid="prototype-preview-iframe"]');

    // iframe可能存在也可能不存在（取决于后端是否返回原型URL）
    const isIframeVisible = await iframe.isVisible({ timeout: 5000 }).catch(() => false);

    if (isIframeVisible) {
      await expect(iframe).toHaveAttribute('src');
    }
  });
});

test.describe('V2.0 UI集成测试 - 完整流程', () => {
  // 注意：完整流程测试依赖OpenLovable原型生成API，响应时间不稳定
  // 此测试在CI环境中可能因超时而失败
  test.skip('DESIGN路径完整流程测试', async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // Step 1: 输入需求
    await submitRequirement(page, TEST_REQUIREMENTS.design);

    // Step 2: 确认意图
    await expect(page.locator('[data-testid="intent-result-panel"]')).toBeVisible();
    await confirmIntent(page);

    // Step 3: 跳过模板选择（如果有）
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    if (await templatePanel.isVisible({ timeout: 5000 }).catch(() => false)) {
      await skipTemplateSelection(page);
    }

    // Step 4: 选择风格
    await expect(page.locator('[data-testid="style-selection-panel"]')).toBeVisible();
    await selectStyle(page, 0);

    // Step 5: 原型确认
    await expect(page.locator('[data-testid="prototype-confirmation-panel"]')).toBeVisible();

    // 验证所有关键元素
    await expect(page.locator('[data-testid="confirm-design-button"]')).toBeVisible();

    console.log('DESIGN路径完整流程测试通过');
  });

  test('进度条应该正确更新', async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // Step 1: 需求输入步骤 - 使用更精确的选择器（只匹配第一个可见的）
    await expect(page.locator('text=描述需求').first()).toBeVisible();

    // Step 2: 提交后进入意图识别
    await submitRequirement(page, TEST_REQUIREMENTS.design);
    await expect(page.locator('text=意图识别').first()).toBeVisible();

    // Step 3: 确认意图后进入下一步
    await confirmIntent(page);

    // 验证进度更新
    const templatePanel = page.locator('[data-testid="template-selection-panel"]');
    const stylePanel = page.locator('[data-testid="style-selection-panel"]');

    await expect(templatePanel.or(stylePanel)).toBeVisible({ timeout: 10000 });
  });
});

test.describe('V2.0 UI集成测试 - 错误处理', () => {
  test('网络错误应该显示错误提示', async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);

    // 模拟网络错误
    await page.route(`${API_BASE_URL}/api/v2/**`, (route) => {
      route.abort('failed');
    });

    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    const input = page.locator('[data-testid="requirement-input"]');
    await input.fill(TEST_REQUIREMENTS.design);

    const submitButton = page.locator('[data-testid="submit-requirement"]');
    await submitButton.click();

    // 验证错误提示显示
    await expect(page.locator('text=失败').or(page.locator('text=错误'))).toBeVisible({ timeout: 10000 });
  });
});

test.describe('V2.0 UI集成测试 - 响应式设计', () => {
  test('桌面端布局正确', async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // 验证桌面端布局
    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();
  });

  test('平板端布局正确', async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // 验证平板端布局
    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();
  });

  test('移动端布局正确', async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    // 验证移动端布局
    await expect(page.locator('[data-testid="requirement-input"]')).toBeVisible();

    // 验证提交按钮可见
    await expect(page.locator('[data-testid="submit-requirement"]')).toBeVisible();
  });
});

test.describe('V2.0 UI集成测试 - 性能', () => {
  test('页面初始加载时间应该<3秒', async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);

    const startTime = Date.now();

    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    const loadTime = Date.now() - startTime;

    expect(loadTime).toBeLessThan(3000);
    console.log(`页面加载时间: ${loadTime}ms`);
  });

  // 注意：AI API响应时间受模型和网络影响较大，实际生产中可能需要30-60秒
  // 此测试将目标设为60秒，用于监控严重的性能退化
  test('需求提交后响应时间应该<60秒', async ({ page }) => {
    // 设置认证状态，避免重定向到登录页
    await setAuthToken(page);
    await page.goto(`${BASE_URL}/create-v2`);
    await waitForPageLoad(page);

    const input = page.locator('[data-testid="requirement-input"]');
    await input.fill(TEST_REQUIREMENTS.design);

    const submitButton = page.locator('[data-testid="submit-requirement"]');

    const startTime = Date.now();
    await submitButton.click();

    // 等待意图结果面板显示（AI API可能需要较长时间）
    await page.waitForSelector('[data-testid="intent-result-panel"]', { timeout: AI_API_TIMEOUT });

    const responseTime = Date.now() - startTime;

    expect(responseTime).toBeLessThan(60000);
    console.log(`需求分析响应时间: ${responseTime}ms`);
  });
});
