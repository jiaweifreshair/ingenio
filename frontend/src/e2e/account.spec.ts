/**
 * 个人中心E2E测试
 * 测试个人信息管理、应用管理、API密钥和安全设置
 */
import { test, expect } from '@playwright/test';

test.describe('个人中心功能测试', () => {
  test.beforeEach(async ({ page }) => {
    // Mock用户信息API - 使用Playwright的route.fulfill()功能
    await page.route('**/api/v1/user/profile', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          success: true,
          message: '获取用户信息成功',
          data: {
            id: 'test-user-123',
            username: '测试用户',
            email: 'test@example.com',
            phone: '13800138000',
            avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=test',
            createdAt: '2024-01-01T00:00:00Z',
            updatedAt: '2024-01-01T00:00:00Z'
          },
          timestamp: Date.now()
        })
      });
    });

    // 导航到个人中心页面
    await page.goto('/account');

    // 等待页面加载完成
    await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible({ timeout: 10000 });

    // 等待个人信息Tab加载完成（通过等待头像卡片出现）
    await expect(page.getByRole('heading', { name: '头像' })).toBeVisible({ timeout: 10000 });
  });

  test('应该正确显示个人信息', async ({ page }) => {
    // 验证页面标题
    await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible();

    // 验证页面描述
    await expect(page.getByText('管理您的个人信息、应用和安全设置')).toBeVisible();

    // 验证Tab导航存在
    await expect(page.getByRole('tab', { name: '个人信息' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '我的应用' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'API密钥' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '安全设置' })).toBeVisible();

    // 验证默认显示个人信息Tab的卡片标题（Mock API返回数据，应该正常显示）
    await expect(page.getByRole('heading', { name: '头像' })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('heading', { name: '个人信息', exact: true })).toBeVisible({ timeout: 5000 });
    await expect(page.getByRole('heading', { name: '密码' })).toBeVisible({ timeout: 5000 });
  });

  test('应该能够编辑个人信息', async ({ page }) => {
    // 等待个人信息卡片标题出现
    await expect(page.getByRole('heading', { name: '个人信息', exact: true })).toBeVisible({ timeout: 5000 });

    // 点击"编辑信息"按钮
    await page.getByRole('button', { name: '编辑信息' }).click();

    // 验证进入编辑模式
    await expect(page.getByRole('button', { name: '保存' })).toBeVisible();
    await expect(page.getByRole('button', { name: '取消' })).toBeVisible();

    // 修改用户名
    const usernameInput = page.getByLabel('用户名');
    await usernameInput.clear();
    await usernameInput.fill('新用户名测试');

    // 修改手机号
    const phoneInput = page.getByLabel('手机号');
    await phoneInput.clear();
    await phoneInput.fill('13900139000');

    // 点击取消按钮（避免实际API调用）
    await page.getByRole('button', { name: '取消' }).click();

    // 验证返回只读模式
    await expect(page.getByRole('button', { name: '编辑信息' })).toBeVisible();
  });

  test('应该能够上传头像', async ({ page }) => {
    // 等待头像卡片标题出现
    await expect(page.getByRole('heading', { name: '头像' })).toBeVisible({ timeout: 5000 });

    // 验证头像上传说明
    await expect(page.getByText('支持JPG、PNG格式，文件大小不超过2MB')).toBeVisible();

    // 验证头像上传元素存在和属性正确
    const avatarUpload = page.locator('#avatar-upload');
    await expect(avatarUpload).toBeAttached();
    await expect(avatarUpload).toHaveAttribute('accept', 'image/*');
  });

  test('应该能够打开修改密码对话框', async ({ page }) => {
    // 等待密码卡片标题出现
    await expect(page.getByRole('heading', { name: '密码' })).toBeVisible({ timeout: 5000 });

    // 点击"修改密码"按钮
    await page.getByRole('button', { name: '修改密码' }).click();

    // 验证对话框打开
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('heading', { name: '修改密码' })).toBeVisible();

    // 验证密码输入框
    await expect(page.getByLabel('当前密码')).toBeVisible();
    await expect(page.getByLabel('新密码', { exact: true })).toBeVisible();
    await expect(page.getByLabel('确认新密码')).toBeVisible();

    // 验证密码规则提示
    await expect(page.getByText('密码长度至少8位，建议包含字母、数字和特殊字符')).toBeVisible();

    // 关闭对话框
    await page.getByRole('button', { name: '取消' }).last().click();

    // 验证对话框关闭
    await expect(page.getByRole('dialog')).not.toBeVisible();
  });

  test('应该能够切换到我的应用Tab', async ({ page }) => {
    // 点击"我的应用"Tab
    await page.getByRole('tab', { name: '我的应用' }).click();

    // 等待应用列表加载
    await page.waitForTimeout(500);

    // 验证切换成功（通过URL或内容变化）
    // 注：具体验证内容取决于AppsSection的实现
  });

  test('应该能够切换到API密钥Tab', async ({ page }) => {
    // 点击"API密钥"Tab
    await page.getByRole('tab', { name: 'API密钥' }).click();

    // 等待API密钥列表加载
    await page.waitForTimeout(500);

    // 验证切换成功
    // 注：具体验证内容取决于ApiKeysSection的实现
  });

  test('应该能够切换到安全设置Tab', async ({ page }) => {
    // 点击"安全设置"Tab
    await page.getByRole('tab', { name: '安全设置' }).click();

    // 等待安全设置加载
    await page.waitForTimeout(500);

    // 验证切换成功
    // 注：具体验证内容取决于SecuritySection的实现
  });

  test('页面应该响应式布局', async ({ page }) => {
    // 测试移动端视口
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible();

    // 验证Tab导航在移动端可见（grid布局）
    await expect(page.getByRole('tab', { name: '个人信息' })).toBeVisible();

    // 测试平板视口
    await page.setViewportSize({ width: 768, height: 1024 });
    await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible();

    // 测试桌面视口
    await page.setViewportSize({ width: 1920, height: 1080 });
    await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible();
  });
});
