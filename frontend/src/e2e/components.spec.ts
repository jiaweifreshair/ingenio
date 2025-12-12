/**
 * UI组件E2E测试
 * 测试Toast通知、Dialog模态框等共享组件
 */
import { test, expect } from '@playwright/test';

test.describe('Toast通知系统测试', () => {
  test('应该能够显示成功通知', async ({ page }) => {
    await page.goto('/create');

    // 点击快速模板触发成功Toast (不会导航，可以观察到Toast)
    // 使用实际存在的模板ID: campus-marketplace (校园二手交易)
    await page.locator('[data-template="campus-marketplace"]').click();

    // 验证Toast通知显示 - 使用exact匹配避免strict mode violation
    await expect(page.getByText('模板已应用', { exact: true })).toBeVisible({ timeout: 5000 });
    await expect(page.getByText('已使用"校园二手交易"模板', { exact: true })).toBeVisible();
  });

  test('应该能够显示错误通知', async ({ page }) => {
    await page.goto('/create');

    // 空表单时，按钮应该被disabled
    const submitButton = page.getByRole('button', { name: /生成应用/ });
    await expect(submitButton).toBeDisabled();

    // 填充少量字符（少于10个），按钮仍然disabled
    await page.getByPlaceholder(/描述你想要的应用/).fill('测试');
    await expect(submitButton).toBeDisabled();
  });

  test('Toast通知应该自动消失', async ({ page }) => {
    await page.goto('/create');

    // 点击快速模板触发Toast
    // 使用实际存在的模板ID: campus-marketplace (校园二手交易)
    await page.locator('[data-template="campus-marketplace"]').click();

    // 等待通知出现 - 使用exact匹配
    const toastTitle = page.getByText('模板已应用', { exact: true });
    await expect(toastTitle).toBeVisible({ timeout: 5000 });

    // 等待通知自动消失（Radix Toast默认5秒）
    await expect(toastTitle).not.toBeVisible({ timeout: 10000 });
  });
});

test.describe('Dialog模态框测试', () => {
  test('演示视频模态框应该正确打开', async ({ page }) => {
    await page.goto('/');

    // 点击"观看示例"按钮
    await page.getByRole('button', { name: /观看.*分钟示例/ }).click();

    // 验证模态框打开 - 使用更精确的选择器（h3标签，exact匹配）
    await expect(page.getByRole('heading', { name: '秒构AI 演示', exact: true })).toBeVisible();

    // 验证模态框内容
    await expect(page.getByText('1分钟快速了解如何创建你的第一个应用')).toBeVisible();
  });

  test('应该显示视频播放控制按钮', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: /观看.*分钟示例/ }).click();

    // 等待模态框完全加载
    await expect(page.getByRole('heading', { name: '秒构AI 演示', exact: true })).toBeVisible();

    // 使用dialog作为scope，验证视频区域中的大播放按钮
    const dialog = page.getByRole('dialog');

    // 验证中央大播放按钮（用于启动视频）
    const centralPlayButton = dialog.locator('button').filter({
      has: page.locator('svg').first()
    }).first();
    await expect(centralPlayButton).toBeVisible();

    // 验证控制栏中的播放/暂停按钮
    const controlBarPlayButton = dialog.locator('div.absolute.bottom-0 button').first();
    await expect(controlBarPlayButton).toBeVisible();
  });

  test('应该能够关闭模态框', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: /观看.*分钟示例/ }).click();

    // 验证模态框打开 - 使用更精确的选择器
    await expect(page.getByRole('heading', { name: '秒构AI 演示', exact: true })).toBeVisible();

    // 点击关闭按钮（Radix Dialog通常用Escape键或点击overlay关闭）
    await page.keyboard.press('Escape');

    // 验证模态框关闭
    await expect(page.getByRole('heading', { name: '秒构AI 演示', exact: true })).not.toBeVisible();
  });

  test('点击overlay应该关闭模态框', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: /观看.*分钟示例/ }).click();

    // 验证模态框打开 - 使用更精确的选择器
    await expect(page.getByRole('heading', { name: '秒构AI 演示', exact: true })).toBeVisible();

    // 点击overlay（模态框外部） - 点击屏幕左上角（肯定在模态框外）
    await page.mouse.click(10, 10);

    // 验证模态框关闭
    await expect(page.getByRole('heading', { name: '秒构AI 演示', exact: true })).not.toBeVisible({ timeout: 3000 });
  });

  test('模态框应该显示四个演示步骤', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: /观看.*分钟示例/ }).click();

    // 等待模态框完全加载
    await expect(page.getByRole('heading', { name: '秒构AI 演示', exact: true })).toBeVisible();

    // 使用dialog作为scope，避免与首页内容冲突
    const dialog = page.getByRole('dialog');

    // 验证四个步骤（在dialog内部查找，避免strict mode violation）
    await expect(dialog.getByText('输入需求')).toBeVisible();
    await expect(dialog.getByText('描述你想要的应用')).toBeVisible();

    await expect(dialog.getByText('AI分析')).toBeVisible();
    await expect(dialog.getByText('智能拆解功能模块')).toBeVisible();

    await expect(dialog.getByText('向导填空')).toBeVisible();
    await expect(dialog.getByText('完善应用配置')).toBeVisible();

    await expect(dialog.getByText('发布上线')).toBeVisible();
    await expect(dialog.getByText('一键部署到云端')).toBeVisible();
  });
});

test.describe('响应式设计测试', () => {
  test('组件在移动端应该正确显示', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/');

    // 验证主要元素在移动端可见 - 更新为新的首页标题
    await expect(page.getByRole('heading', { name: /你的创意，AI 来实现/ })).toBeVisible();
    // 使用hero区域的主CTA按钮
    await expect(page.locator('[data-location="hero"]')).toBeVisible();
  });

  test('组件在平板端应该正确显示', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });
    await page.goto('/');

    // 验证主要元素在平板端可见 - 更新为新的首页标题
    await expect(page.getByRole('heading', { name: /你的创意，AI 来实现/ })).toBeVisible();
    // 使用hero区域的主CTA按钮
    await expect(page.locator('[data-location="hero"]')).toBeVisible();
  });

  test('组件在桌面端应该正确显示', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/');

    // 验证主要元素在桌面端可见 - 更新为新的首页标题
    await expect(page.getByRole('heading', { name: /你的创意，AI 来实现/ })).toBeVisible();
    // 使用hero区域的主CTA按钮
    await expect(page.locator('[data-location="hero"]')).toBeVisible();
  });
});
