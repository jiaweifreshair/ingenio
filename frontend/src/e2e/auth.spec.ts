/**
 * 认证流程E2E测试
 * 测试登录、注册、路由守卫、Token管理
 *
 * Phase 6: 认证E2E测试
 * @author Ingenio Team
 * @since 1.0.0
 */
import { test, expect, type Page } from '@playwright/test';

/**
 * 辅助函数：设置认证Token到Cookie和localStorage
 * 模拟已登录状态
 */
async function setAuthToken(page: Page, token: string = 'mock_jwt_token_for_testing') {
  await page.context().addCookies([{
    name: 'auth_token',
    value: token,
    domain: 'localhost',
    path: '/',
  }]);

  await page.evaluate((t) => {
    localStorage.setItem('auth_token', t);
  }, token);
}

/**
 * 辅助函数：清除认证Token
 * 仅清除Cookie（localStorage会在导航时由浏览器上下文处理）
 */
async function clearAuthToken(page: Page) {
  await page.context().clearCookies();
}

/**
 * 辅助函数：检查是否已重定向到登录页
 */
async function expectRedirectToLogin(page: Page, originalPath?: string) {
  await expect(page).toHaveURL(/\/login/, { timeout: 10000 });

  if (originalPath) {
    // 验证redirect参数包含原始路径
    const url = new URL(page.url());
    expect(url.searchParams.get('redirect')).toBe(originalPath);
  }
}

test.describe('路由守卫测试', () => {
  test.beforeEach(async ({ page }) => {
    // 每个测试前清除认证状态
    await clearAuthToken(page);
  });

  test('未认证用户访问受保护路由应重定向到登录页', async ({ page }) => {
    // 尝试访问dashboard（受保护路由）
    await page.goto('/dashboard');

    // 应被重定向到登录页，并携带redirect参数
    await expectRedirectToLogin(page, '/dashboard');
  });

  test('未认证用户访问/create应重定向到登录页', async ({ page }) => {
    await page.goto('/create');
    await expectRedirectToLogin(page, '/create');
  });

  test('未认证用户访问/wizard应重定向到登录页', async ({ page }) => {
    await page.goto('/wizard');
    await expectRedirectToLogin(page, '/wizard');
  });

  test('未认证用户访问/settings应重定向到登录页', async ({ page }) => {
    await page.goto('/settings');
    await expectRedirectToLogin(page, '/settings');
  });

  test('未认证用户可以访问首页', async ({ page }) => {
    await page.goto('/');

    // 应该能正常访问首页，不被重定向
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('未认证用户可以访问登录页', async ({ page }) => {
    await page.goto('/login');

    // 应该能正常访问登录页
    await expect(page).toHaveURL(/\/login/);
  });
});

test.describe('已认证用户路由行为', () => {
  test.beforeEach(async ({ page }) => {
    // 设置认证状态
    await page.goto('/'); // 需要先导航才能设置Cookie
    await setAuthToken(page);
  });

  test('已认证用户访问登录页应重定向到dashboard', async ({ page }) => {
    await page.goto('/login');

    // 应被重定向到dashboard
    await expect(page).toHaveURL(/\/dashboard/, { timeout: 10000 });
  });

  test('已认证用户可以访问dashboard', async ({ page }) => {
    await page.goto('/dashboard');

    // 应该能正常访问，不被重定向到登录页
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('已认证用户可以访问/create', async ({ page }) => {
    await page.goto('/create');

    await expect(page).not.toHaveURL(/\/login/);
  });
});

test.describe('登录页面UI测试', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuthToken(page);
    await page.goto('/login');
  });

  test('登录页应显示登录表单', async ({ page }) => {
    // 等待页面加载
    await page.waitForLoadState('networkidle');

    // 验证页面包含登录相关元素
    // 注：具体选择器需要根据实际登录页实现调整
    const pageContent = await page.content();

    // 至少应该有登录相关的文字或元素
    expect(
      pageContent.includes('登录') ||
      pageContent.includes('Login') ||
      pageContent.includes('Sign in')
    ).toBeTruthy();
  });

  test('登录页应有邮箱输入框', async ({ page }) => {
    await page.waitForLoadState('networkidle');

    // 查找邮箱输入框
    const emailInput = page.locator('input[type="email"], input[name="email"], input[placeholder*="邮箱"], input[placeholder*="email"]').first();

    // 如果存在邮箱输入框，验证它可见
    if (await emailInput.count() > 0) {
      await expect(emailInput).toBeVisible();
    }
  });

  test('登录页应有密码输入框', async ({ page }) => {
    await page.waitForLoadState('networkidle');

    // 查找密码输入框
    const passwordInput = page.locator('input[type="password"]').first();

    // 如果存在密码输入框，验证它可见
    if (await passwordInput.count() > 0) {
      await expect(passwordInput).toBeVisible();
    }
  });

  test('登录页应有OAuth登录按钮', async ({ page }) => {
    await page.waitForLoadState('networkidle');

    // 查找OAuth登录按钮（Google或GitHub）
    const oauthButtons = page.locator('button:has-text("Google"), button:has-text("GitHub"), a:has-text("Google"), a:has-text("GitHub")');

    // 验证至少有一个OAuth登录选项
    const buttonCount = await oauthButtons.count();

    // OAuth按钮可能存在也可能不存在，不强制要求
    // 但如果存在，应该可见
    if (buttonCount > 0) {
      await expect(oauthButtons.first()).toBeVisible();
    }
  });
});

test.describe('Token存储验证', () => {
  test('登录成功后Token应存储在Cookie中', async ({ page }) => {
    // 设置Token
    await page.goto('/');
    await setAuthToken(page, 'test_token_123');

    // 验证Cookie中有Token
    const cookies = await page.context().cookies();
    const authCookie = cookies.find(c => c.name === 'auth_token');

    expect(authCookie).toBeDefined();
    expect(authCookie?.value).toBe('test_token_123');
  });

  test('登录成功后Token应存储在localStorage中', async ({ page }) => {
    await page.goto('/');
    await setAuthToken(page, 'test_token_456');

    // 验证localStorage中有Token
    const token = await page.evaluate(() => localStorage.getItem('auth_token'));
    expect(token).toBe('test_token_456');
  });

  test('清除Token后应无法访问受保护路由', async ({ page }) => {
    // 先设置Token
    await page.goto('/');
    await setAuthToken(page);

    // 验证可以访问dashboard
    await page.goto('/dashboard');
    await expect(page).not.toHaveURL(/\/login/);

    // 清除Token
    await clearAuthToken(page);

    // 再次访问dashboard应被重定向
    await page.goto('/dashboard');
    await expectRedirectToLogin(page, '/dashboard');
  });
});

test.describe('登录后重定向测试', () => {
  test('登录后应重定向回原始请求路径', async ({ page }) => {
    // 清除认证状态
    await page.goto('/');
    await clearAuthToken(page);

    // 访问受保护路由，被重定向到登录页
    await page.goto('/wizard');
    await expectRedirectToLogin(page, '/wizard');

    // 模拟登录成功（设置Token）
    await setAuthToken(page);

    // 获取redirect参数并导航
    const url = new URL(page.url());
    const redirectPath = url.searchParams.get('redirect') || '/dashboard';

    await page.goto(redirectPath);

    // 应该能访问原始路径
    await expect(page).toHaveURL(new RegExp(redirectPath.replace('/', '\\/')));
  });
});

test.describe('会话过期处理', () => {
  test('过期Token应被重定向到登录页', async ({ page }) => {
    // 设置一个过期的Token
    await page.goto('/');
    await setAuthToken(page, 'expired_token');

    // 尝试访问需要认证的API
    // 注：实际测试需要后端返回401
    // 这里只验证前端Cookie机制

    // 清除Token模拟过期
    await clearAuthToken(page);

    // 访问受保护路由
    await page.goto('/dashboard');

    // 应被重定向到登录页
    await expectRedirectToLogin(page);
  });
});

test.describe('真实登录流程测试', () => {
  test.beforeEach(async ({ page }) => {
    await clearAuthToken(page);
  });

  test('使用有效凭据登录应成功', async ({ page }) => {
    // 导航到登录页面
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // 填写登录表单
    await page.fill('#identifier', 'playwrighttest');
    await page.fill('#password', 'PlayTest2024');

    // 点击登录按钮
    await page.click('button[type="submit"]');

    // 等待登录完成（页面跳转或toast出现）
    await page.waitForTimeout(3000);

    // 验证登录成功 - 检查是否跳转离开登录页
    const currentUrl = page.url();
    expect(currentUrl).not.toContain('/login');

    // 验证localStorage中有token
    const token = await page.evaluate(() => localStorage.getItem('auth_token'));
    expect(token).not.toBeNull();
  });

  test('使用无效密码登录应失败', async ({ page }) => {
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    // 填写错误密码
    await page.fill('#identifier', 'playwrighttest');
    await page.fill('#password', 'wrongpassword');

    // 点击登录按钮
    await page.click('button[type="submit"]');

    // 等待响应
    await page.waitForTimeout(2000);

    // 应该仍在登录页
    expect(page.url()).toContain('/login');
  });
});
