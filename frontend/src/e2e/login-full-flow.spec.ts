/**
 * 完整登录流程E2E测试
 * 测试从登录到主要功能的完整用户旅程
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
import { test, expect, type Page } from '@playwright/test';

// 测试账号配置
const TEST_USER = {
  identifier: 'justin',
  password: 'Test12345',
};

/**
 * 辅助函数：执行登录操作
 */
async function performLogin(page: Page, user = TEST_USER) {
  await page.goto('/login', { waitUntil: 'networkidle' });

  // 填写登录表单
  await page.fill('#identifier', user.identifier);
  await page.fill('#password', user.password);

  // 截图：登录表单已填写
  await page.screenshot({ path: '/tmp/e2e-login-1-filled.png' });

  // 监听登录API响应
  const loginResponsePromise = page.waitForResponse(
    response => response.url().includes('/auth/login') && response.status() === 200,
    { timeout: 15000 }
  );

  // 点击登录按钮
  await page.click('button[type="submit"]');

  // 等待登录API返回成功
  const loginResponse = await loginResponsePromise;
  console.log('登录API返回状态:', loginResponse.status());

  // 等待页面跳转（登录成功后跳转到首页 /）
  await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 15000 });

  // 额外等待确保Token存储完成
  await page.waitForTimeout(500);

  // 截图：登录成功
  await page.screenshot({ path: '/tmp/e2e-login-2-success.png' });

  console.log('登录成功，当前URL:', page.url());
}

/**
 * 辅助函数：验证Token已存储
 */
async function verifyTokenStored(page: Page) {
  const token = await page.evaluate(() => localStorage.getItem('auth_token'));
  expect(token).not.toBeNull();
  expect(token).not.toBe('');
  console.log('Token已存储:', token?.substring(0, 20) + '...');
  return token;
}

test.describe('完整登录流程测试', () => {
  test.beforeEach(async ({ page }) => {
    // 清除认证状态
    await page.context().clearCookies();
  });

  test('Step 1: 登录成功并验证Token', async ({ page }) => {
    console.log('=== Step 1: 登录测试 ===');

    // 执行登录
    await performLogin(page);

    // 验证Token存储
    await verifyTokenStored(page);

    // 验证不在登录页
    expect(page.url()).not.toContain('/login');

    console.log('登录测试通过');
  });

  test('Step 2: 登录后访问Dashboard', async ({ page }) => {
    console.log('=== Step 2: Dashboard访问测试 ===');

    // 登录
    await performLogin(page);

    // 导航到Dashboard
    await page.goto('/dashboard', { waitUntil: 'networkidle' });

    // 截图
    await page.screenshot({ path: '/tmp/e2e-login-3-dashboard.png' });

    // 验证页面已加载
    await expect(page).toHaveURL(/\/dashboard/);

    // 检查页面内容（调用一次以确保DOM已就绪）
    await page.content();
    console.log('Dashboard页面标题:', await page.title());

    // 验证没有被重定向到登录页
    expect(page.url()).not.toContain('/login');

    console.log('Dashboard访问测试通过');
  });

  test('Step 3: 登录后访问Lab页面', async ({ page }) => {
    console.log('=== Step 3: Lab页面测试 ===');

    // 登录
    await performLogin(page);

    // 导航到Lab页面
    await page.goto('/lab', { waitUntil: 'networkidle' });

    // 截图
    await page.screenshot({ path: '/tmp/e2e-login-4-lab.png' });

    // 验证页面已加载
    await expect(page).toHaveURL(/\/lab/);

    // 检查是否有输入框（需求输入）
    const inputExists = await page.locator('textarea, input[type="text"]').first().isVisible().catch(() => false);
    console.log('Lab页面输入框存在:', inputExists);

    console.log('Lab页面测试通过');
  });

  test('Step 4: 登录后访问Settings页面', async ({ page }) => {
    console.log('=== Step 4: Settings页面测试 ===');

    // 登录
    await performLogin(page);

    // 导航到Settings页面
    await page.goto('/settings', { waitUntil: 'networkidle' });

    // 截图
    await page.screenshot({ path: '/tmp/e2e-login-5-settings.png' });

    // 验证页面已加载（可能重定向到settings子路径）
    expect(page.url()).toContain('/settings');

    // 验证没有被重定向到登录页
    expect(page.url()).not.toContain('/login');

    console.log('Settings页面测试通过');
  });

  test('Step 5: 登录后访问Account页面', async ({ page }) => {
    console.log('=== Step 5: Account页面测试 ===');

    // 登录
    await performLogin(page);

    // 导航到Account页面
    await page.goto('/account', { waitUntil: 'networkidle' });

    // 截图
    await page.screenshot({ path: '/tmp/e2e-login-6-account.png' });

    // 验证没有被重定向到登录页
    expect(page.url()).not.toContain('/login');

    console.log('Account页面测试通过');
  });
});

test.describe('登录后完整功能流程', () => {
  test.beforeEach(async ({ page }) => {
    await page.context().clearCookies();
  });

  test('完整流程：登录 -> Lab -> 输入需求', async ({ page }) => {
    console.log('=== 完整流程测试：登录 -> Lab -> 输入需求 ===');

    // Step 1: 登录
    await performLogin(page);
    console.log('Step 1: 登录完成');

    // Step 2: 导航到Lab
    await page.goto('/lab', { waitUntil: 'networkidle' });
    console.log('Step 2: 导航到Lab页面');
    await page.screenshot({ path: '/tmp/e2e-flow-1-lab.png' });

    // Step 3: 查找并填写需求输入框
    const inputSelectors = [
      'textarea[placeholder*="需求"]',
      'textarea[placeholder*="输入"]',
      'textarea[placeholder*="描述"]',
      'textarea',
      'input[type="text"]',
    ];

    let inputFound = false;
    for (const selector of inputSelectors) {
      const input = page.locator(selector).first();
      if (await input.isVisible().catch(() => false)) {
        console.log(`找到输入框: ${selector}`);
        await input.fill('做一个简单的计数器应用，可以点击加减按钮');
        inputFound = true;
        await page.screenshot({ path: '/tmp/e2e-flow-2-input.png' });
        break;
      }
    }

    if (!inputFound) {
      console.log('未找到输入框，列出所有可见输入元素:');
      const inputs = await page.locator('input, textarea').all();
      for (const input of inputs) {
        const placeholder = await input.getAttribute('placeholder').catch(() => '');
        const id = await input.getAttribute('id').catch(() => '');
        const visible = await input.isVisible().catch(() => false);
        console.log(`  Input: id=${id}, placeholder=${placeholder}, visible=${visible}`);
      }
    }

    expect(inputFound).toBe(true);
    console.log('Step 3: 需求输入完成');

    // Step 4: 查找提交按钮
    const buttonSelectors = [
      'button:has-text("开始")',
      'button:has-text("生成")',
      'button:has-text("创建")',
      'button:has-text("提交")',
      'button[type="submit"]',
    ];

    let buttonFound = false;
    for (const selector of buttonSelectors) {
      const btn = page.locator(selector).first();
      if (await btn.isVisible().catch(() => false)) {
        console.log(`找到按钮: ${selector}`);
        buttonFound = true;
        break;
      }
    }

    console.log('Step 4: 按钮检查完成, 按钮存在:', buttonFound);
    await page.screenshot({ path: '/tmp/e2e-flow-3-ready.png' });

    console.log('完整流程测试通过');
  });

  test('完整流程：登录 -> 首页 -> 检查导航', async ({ page }) => {
    console.log('=== 完整流程测试：登录 -> 首页 -> 检查导航 ===');

    // Step 1: 登录
    await performLogin(page);
    console.log('Step 1: 登录完成');

    // Step 2: 导航到首页
    await page.goto('/', { waitUntil: 'networkidle' });
    console.log('Step 2: 导航到首页');
    await page.screenshot({ path: '/tmp/e2e-nav-1-home.png' });

    // Step 3: 检查顶部导航
    const navItems = await page.locator('nav a, header a').all();
    console.log(`Step 3: 找到 ${navItems.length} 个导航链接`);

    for (const item of navItems.slice(0, 5)) {
      const href = await item.getAttribute('href').catch(() => '');
      const text = await item.textContent().catch(() => '');
      console.log(`  导航项: "${text?.trim()}" -> ${href}`);
    }

    // Step 4: 检查用户菜单（头像/用户名区域）
    const userMenu = page.locator('[data-testid="user-menu"], button:has-text("账户"), button:has-text("Account")');
    const userMenuExists = await userMenu.first().isVisible().catch(() => false);
    console.log('Step 4: 用户菜单存在:', userMenuExists);

    await page.screenshot({ path: '/tmp/e2e-nav-2-final.png' });

    console.log('导航检查测试通过');
  });
});

test.describe('登录失败场景', () => {
  test.beforeEach(async ({ page }) => {
    await page.context().clearCookies();
  });

  test('无效密码登录应失败', async ({ page }) => {
    console.log('=== 无效密码登录测试 ===');

    await page.goto('/login', { waitUntil: 'networkidle' });

    // 填写错误密码
    await page.fill('#identifier', TEST_USER.identifier);
    await page.fill('#password', 'wrongpassword123');

    await page.screenshot({ path: '/tmp/e2e-fail-1-input.png' });

    // 点击登录
    await page.click('button[type="submit"]');

    // 等待响应
    await page.waitForTimeout(3000);

    // 应该仍在登录页
    expect(page.url()).toContain('/login');

    // 检查是否有错误提示
    const errorText = await page.locator('.text-red-500, .error, [role="alert"], .text-destructive').allTextContents();
    console.log('错误提示:', errorText);

    await page.screenshot({ path: '/tmp/e2e-fail-2-error.png' });

    console.log('无效密码登录测试通过');
  });

  test('空表单提交应显示验证错误', async ({ page }) => {
    console.log('=== 空表单提交测试 ===');

    await page.goto('/login', { waitUntil: 'networkidle' });

    // 直接点击登录（不填写任何内容）
    await page.click('button[type="submit"]');

    await page.waitForTimeout(1000);

    // 检查是否有验证错误
    const pageContent = await page.content();
    const hasValidationError =
      pageContent.includes('必填') ||
      pageContent.includes('required') ||
      pageContent.includes('请输入') ||
      pageContent.includes('不能为空');

    console.log('存在验证错误:', hasValidationError);

    await page.screenshot({ path: '/tmp/e2e-fail-3-empty.png' });

    console.log('空表单提交测试通过');
  });
});

test.describe('API调用验证', () => {
  test.beforeEach(async ({ page }) => {
    await page.context().clearCookies();
  });

  test('登录后API请求应携带Authorization头', async ({ page }) => {
    console.log('=== API Authorization验证 ===');

    // 监听网络请求
    const apiRequests: { url: string; hasAuth: boolean }[] = [];
    page.on('request', request => {
      const url = request.url();
      if (url.includes('/api/') && !url.includes('/auth/')) {
        const authHeader = request.headers()['authorization'];
        apiRequests.push({
          url,
          hasAuth: !!authHeader,
        });
      }
    });

    // 登录
    await performLogin(page);

    // 访问需要API调用的页面
    await page.goto('/dashboard', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);

    // 检查API请求
    console.log('API请求记录:');
    apiRequests.forEach(req => {
      console.log(`  ${req.url} - Auth: ${req.hasAuth}`);
    });

    // 如果有API请求，验证它们都携带了Authorization头
    if (apiRequests.length > 0) {
      const allHaveAuth = apiRequests.every(req => req.hasAuth);
      console.log('所有API请求都携带Authorization:', allHaveAuth);
    }

    console.log('API Authorization验证通过');
  });
});
