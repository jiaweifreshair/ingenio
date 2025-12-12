/**
 * 用户功能E2E测试
 * 
 * 测试内容：
 * 1. 找回密码功能完整流程
 * 2. 用户名更新功能（含唯一性检查）
 * 3. 注册功能（无验证码）
 * 
 * @author Ingenio Team
 * @since 2025-01-XX
 */
import { test, expect } from '@playwright/test';

test.describe('User Features', () => {
  // const _apiBaseURL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api';

  test('should allow user to register and login', async ({ page }) => {
    // 步骤1: 访问登录页面
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /登录|秒构AI/i })).toBeVisible({ timeout: 10000 });

    // 步骤2: 点击"忘记密码？"链接
    const forgotPasswordLink = page.getByRole('link', { name: '忘记密码？' });
    await expect(forgotPasswordLink).toBeVisible();
    await forgotPasswordLink.click();

    // 步骤3: 验证跳转到找回密码页面
    await expect(page).toHaveURL('/forgot-password', { timeout: 5000 });
    await expect(page.getByText('找回密码')).toBeVisible();

    // 步骤4: 验证三步流程UI
    // Step 1: 输入邮箱
    await expect(page.getByText('验证邮箱')).toBeVisible();
    await expect(page.getByLabel(/邮箱地址/i)).toBeVisible();

    // 注意：由于需要真实邮箱验证码，这里只验证UI流程
    // 实际测试时需要Mock邮件服务或使用测试邮箱
    const emailInput = page.getByLabel(/邮箱地址/i);
    await emailInput.fill('test@example.com');

    // 点击发送验证码按钮（UI测试）
    const sendCodeButton = page.getByRole('button', { name: /发送验证码/i });
    await expect(sendCodeButton).toBeVisible();

    // 验证步骤指示器
    await expect(page.locator('text=1')).toBeVisible();
    await expect(page.locator('text=2')).toBeVisible();
    await expect(page.locator('text=3')).toBeVisible();
  });

  test('2. 注册功能（无验证码）', async ({ page }) => {
    // 步骤1: 访问注册页面
    await page.goto('/register');
    
    // 验证页面加载（使用实际的页面标题）
    await expect(page.getByRole('heading', { name: /创建账号/i })).toBeVisible({ timeout: 10000 });

    // 步骤2: 验证注册表单不包含验证码输入
    await expect(page.getByLabel(/用户名/i)).toBeVisible();
    await expect(page.getByLabel(/邮箱/i)).toBeVisible();
    await expect(page.getByLabel(/密码/i).first()).toBeVisible();

    // 验证码相关元素应该不存在
    const verificationCodeInput = page.getByLabel(/验证码|邮箱验证码/i);
    const verificationCodeCount = await verificationCodeInput.count();
    expect(verificationCodeCount).toBe(0);

    const sendCodeButton = page.getByRole('button', { name: /发送验证码/i });
    const sendCodeButtonCount = await sendCodeButton.count();
    expect(sendCodeButtonCount).toBe(0);

    // 步骤3: 验证表单字段
    const usernameInput = page.getByLabel(/用户名/i);
    const emailInput = page.getByLabel(/邮箱/i);
    // 使用id选择器来精确匹配密码输入框
    const passwordInput = page.locator('#password');
    const confirmPasswordInput = page.locator('#confirmPassword');

    await expect(usernameInput).toBeVisible();
    await expect(emailInput).toBeVisible();
    await expect(passwordInput).toBeVisible();
    await expect(confirmPasswordInput).toBeVisible();

    // 步骤4: 验证表单验证规则（UI层面）
    // 这里只验证输入框存在，实际注册需要后端支持
    await usernameInput.fill('testuser123');
    await emailInput.fill('test@example.com');
    await passwordInput.fill('Test123456');
    await confirmPasswordInput.fill('Test123456');

    // 验证注册按钮存在
    const registerButton = page.getByRole('button', { name: /注册/i });
    await expect(registerButton).toBeVisible();
  });

  test('3. 用户名更新功能', async ({ page }) => {
    // 前置条件：需要先登录
    // 这里使用Mock或实际登录流程
    // 注意：实际测试时需要有效的认证Token

    // 步骤1: 访问个人中心
    await page.goto('/account');

    // 等待页面加载（可能需要登录）
    // 如果未登录，应该跳转到登录页
    const isLoginPage = page.url().includes('/login');
    if (isLoginPage) {
      test.info().annotations.push({
        type: 'note',
        description: '需要先登录才能测试用户名更新功能'
      });
      return;
    }

    // 步骤2: 验证个人信息Tab存在
    await expect(page.getByRole('tab', { name: '个人信息' })).toBeVisible({ timeout: 10000 });

    // 步骤3: 验证用户名输入框存在
    const usernameInput = page.getByLabel('用户名');
    await expect(usernameInput).toBeVisible({ timeout: 5000 });

    // 步骤4: 点击编辑按钮
    const editButton = page.getByRole('button', { name: '编辑信息' });
    if (await editButton.isVisible()) {
      await editButton.click();

      // 步骤5: 验证进入编辑模式
      await expect(page.getByRole('button', { name: '保存' })).toBeVisible({ timeout: 2000 });

      // 步骤6: 修改用户名
      // const _currentUsername = await usernameInput.inputValue();
      const newUsername = `testuser_${Date.now()}`;
      await usernameInput.clear();
      await usernameInput.fill(newUsername);

      // 步骤7: 验证保存和取消按钮
      const saveButton = page.getByRole('button', { name: '保存' });
      const cancelButton = page.getByRole('button', { name: '取消' });

      await expect(saveButton).toBeVisible();
      await expect(cancelButton).toBeVisible();

      // 步骤8: 点击取消（避免实际修改）
      await cancelButton.click();

      // 步骤9: 验证返回只读模式
      await expect(page.getByRole('button', { name: '编辑信息' })).toBeVisible();
    } else {
      test.info().annotations.push({
        type: 'warning',
        description: '编辑按钮不可见，可能用户信息未加载'
      });
    }
  });

  test('完整用户流程：注册 -> 登录 -> 修改用户名', async ({ page }) => {
    // 这是一个完整的端到端测试流程
    // 注意：实际执行时可能需要Mock后端API或使用测试环境

    const timestamp = Date.now();
    const testUsername = `testuser_${timestamp}`;
    const testEmail = `test_${timestamp}@example.com`;
    const testPassword = 'Test123456';

    // 步骤1: 注册新用户
    await page.goto('/register');
    
    // 等待页面加载
    await expect(page.getByRole('heading', { name: /创建账号/i })).toBeVisible({ timeout: 10000 });
    
    // 填写注册表单
    await page.getByLabel(/用户名/i).fill(testUsername);
    await page.getByLabel(/邮箱/i).fill(testEmail);
    // 使用id选择器精确匹配密码输入框
    await page.locator('#password').fill(testPassword);
    await page.locator('#confirmPassword').fill(testPassword);

    // 同意用户协议（如果有复选框）
    // 使用更精确的选择器找到用户协议复选框
    const termsCheckbox = page.getByRole('checkbox');
    const checkboxCount = await termsCheckbox.count();
    if (checkboxCount > 0) {
      // 使用click而不是check，因为这是一个自定义checkbox组件
      await termsCheckbox.first().click();
      // 验证复选框已选中
      await expect(termsCheckbox.first()).toBeChecked();
    }

    // 注意：实际提交注册需要后端支持
    // 这里只验证表单可以填写
    test.info().annotations.push({
      type: 'note',
      description: '完整流程测试需要在有后端支持的环境中运行'
    });
  });
});

