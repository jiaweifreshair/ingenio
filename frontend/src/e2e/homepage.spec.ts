/**
 * 首页E2E测试
 * 测试Hero区域、CTA按钮、案例卡片等核心功能
 */
import { test, expect } from '@playwright/test';

test.describe('首页功能测试', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('应该正确显示页面标题和描述', async ({ page }) => {
    // 验证主标题 - 更新为新的首页标题
    await expect(page.getByRole('heading', { name: /你的创意，AI 来实现/ })).toBeVisible();
    await expect(page.getByText('让每个想法都长成应用')).toBeVisible();

    // 验证副标题
    await expect(page.getByText(/为校园而生/)).toBeVisible();
  });

  test('「免费开始」按钮应该导航到创建页面', async ({ page }) => {
    // 点击hero区域的"免费开始"按钮
    await page.locator('[data-location="hero"]').click();

    // 验证导航到创建页面
    await expect(page).toHaveURL('/create');

    // 验证创建页面已加载
    await expect(page.getByRole('heading', { name: /创建新应用/ })).toBeVisible({ timeout: 10000 });
  });

  test('「观看 1 分钟示例」按钮应该打开演示模态框', async ({ page }) => {
    // 点击"观看示例"按钮
    await page.getByRole('button', { name: /观看.*分钟示例/ }).click();

    // 验证模态框打开 - 使用exact匹配避免strict mode violation
    await expect(page.getByRole('heading', { name: '秒构AI 演示', exact: true })).toBeVisible();

    // 验证演示步骤显示
    await expect(page.getByText('输入需求')).toBeVisible();
    await expect(page.getByText('AI分析')).toBeVisible();
    await expect(page.getByText('向导填空')).toBeVisible();
    await expect(page.getByText('发布上线')).toBeVisible();
  });

  test('案例卡片应该正确显示', async ({ page }) => {
    // 滚动到案例区域
    await page.locator('#usecases').scrollIntoViewIfNeeded();

    // 验证标题
    await expect(page.getByRole('heading', { name: '校园案例' })).toBeVisible();

    // 验证三个案例卡片
    await expect(page.getByText('报名签到')).toBeVisible();
    await expect(page.getByText('问卷表单')).toBeVisible();
    await expect(page.getByText('社团小店')).toBeVisible();
  });

  test('点击案例卡片应该导航到预览页面', async ({ page }) => {
    // 滚动到案例区域
    await page.locator('#usecases').scrollIntoViewIfNeeded();

    // 点击"问卷表单"卡片
    await page.getByText('问卷表单').click();

    // 验证导航到预览页面
    await expect(page).toHaveURL('/preview/demo-survey');

    // 验证预览页面已加载 - 使用heading角色更精确
    await expect(page.getByRole('heading', { name: '应用预览' })).toBeVisible({ timeout: 10000 });
  });

  test('页面应该响应式布局', async ({ page }) => {
    // 测试移动端视口
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(page.getByRole('heading', { name: /你的创意，AI 来实现/ })).toBeVisible();

    // 测试平板视口
    await page.setViewportSize({ width: 768, height: 1024 });
    await expect(page.getByRole('heading', { name: /你的创意，AI 来实现/ })).toBeVisible();

    // 测试桌面视口
    await page.setViewportSize({ width: 1920, height: 1080 });
    await expect(page.getByRole('heading', { name: /你的创意，AI 来实现/ })).toBeVisible();
  });
});
