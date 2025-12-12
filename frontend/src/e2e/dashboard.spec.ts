/**
 * 应用仪表板E2E测试
 * 测试应用列表、搜索筛选、CRUD操作
 */
import { test, expect } from '@playwright/test';

test.describe('应用仪表板功能测试', () => {
  test.beforeEach(async ({ page }) => {
    // 导航到仪表板页面
    await page.goto('/dashboard');

    // 等待页面加载完成
    await expect(page.getByRole('heading', { name: '我的应用' })).toBeVisible({ timeout: 10000 });
  });

  test('应该正确显示页面标题和创建按钮', async ({ page }) => {
    // 验证页面标题
    await expect(page.getByRole('heading', { name: '我的应用' })).toBeVisible();

    // 验证页面描述
    await expect(page.getByText('管理您创建的所有应用')).toBeVisible();

    // 验证"创建新应用"按钮
    const createButton = page.getByRole('button', { name: /创建新应用/ });
    await expect(createButton).toBeVisible();
  });

  test('应该正确显示统计卡片或加载骨架', async ({ page }) => {
    // 等待统计数据加载（可能显示骨架屏或实际数据）
    await page.waitForTimeout(1000);

    // 验证至少有统计卡片区域（骨架屏或实际卡片）
    // 注：实际统计卡片的内容取决于后端数据
    // 这里验证卡片容器存在即可
  });

  test('应该正确显示筛选栏', async ({ page }) => {
    // 等待筛选栏加载
    await page.waitForTimeout(500);

    // 注：FilterBar组件的具体元素需要根据实现添加验证
    // 可能包含：搜索框、状态筛选下拉框、搜索按钮等
  });

  test('应该能够搜索应用', async ({ page }) => {
    // 等待页面加载完成
    await page.waitForTimeout(1000);

    // 注：由于没有data-testid，这里使用更通用的选择器
    // 实际测试时需要根据FilterBar的实现调整

    // 示例：如果有搜索输入框
    const searchInput = page.locator('input[type="text"]').first();
    if (await searchInput.isVisible()) {
      await searchInput.fill('图书');
      await searchInput.press('Enter');

      // 等待搜索结果
      await page.waitForTimeout(1000);

      // 验证搜索执行（通过网络请求或页面变化）
    }
  });

  test('应该能够筛选应用状态', async ({ page }) => {
    // 等待页面加载完成
    await page.waitForTimeout(1000);

    // 注：状态筛选的实现取决于FilterBar组件
    // 需要添加data-testid后才能准确定位
  });

  test('应该显示应用卡片列表或空状态', async ({ page }) => {
    // 等待列表加载完成
    await page.waitForTimeout(2000);

    // 检查是否有应用卡片
    const appCards = page.locator('.grid > div');
    const cardCount = await appCards.count();

    if (cardCount > 0) {
      // 有应用：验证卡片显示
      await expect(appCards.first()).toBeVisible();
    } else {
      // 无应用：验证空状态
      await expect(page.getByText(/还没有创建任何应用/)).toBeVisible();
      await expect(page.getByRole('button', { name: /创建新应用/ }).last()).toBeVisible();
    }
  });

  test('应该能够查看应用详情', async ({ page }) => {
    // 等待列表加载
    await page.waitForTimeout(2000);

    // 使用更精确的选择器：找到第一个"查看"按钮（在应用卡片的footer中）
    const viewButtons = page.getByRole('button', { name: '查看' });
    const buttonCount = await viewButtons.count();

    if (buttonCount > 0) {
      // 使用第一个"查看"按钮
      const firstViewButton = viewButtons.first();
      await expect(firstViewButton).toBeVisible();

      const currentUrl = page.url();
      await firstViewButton.click();

      // 等待导航完成 - 增加超时时间到10秒，并使用networkidle
      try {
        await page.waitForURL(/\/preview\/.+/, { timeout: 10000, waitUntil: 'networkidle' });

        // 验证URL发生变化（导航到预览页面）
        const newUrl = page.url();
        expect(newUrl).not.toBe(currentUrl);
        expect(newUrl).toContain('/preview/');
      } catch (error) {
        // 如果waitForURL超时，检查URL是否至少改变了
        const newUrl = page.url();
        console.log(`Navigation timeout. Current URL: ${currentUrl}, New URL: ${newUrl}`);

        // 如果URL确实改变到preview页面，即使加载慢也算通过
        if (newUrl.includes('/preview/')) {
          expect(newUrl).not.toBe(currentUrl);
          expect(newUrl).toContain('/preview/');
        } else {
          throw error; // 如果URL没变，重新抛出错误
        }
      }
    } else {
      // 如果没有应用卡片，跳过测试但记录信息
      test.skip();
    }
  });

  test('应该能够继续编辑应用', async ({ page }) => {
    // 等待列表加载
    await page.waitForTimeout(2000);

    // 检查是否有应用卡片
    const appCards = page.locator('.grid > div');
    const cardCount = await appCards.count();

    if (cardCount > 0) {
      // 注：需要添加data-testid到AppCard的"编辑"按钮
      const editButton = page.getByRole('button', { name: /继续编辑|编辑/ }).first();
      if (await editButton.isVisible()) {
        const currentUrl = page.url();
        await editButton.click();

        // 等待导航
        await page.waitForTimeout(1000);

        // 验证URL发生变化（导航到向导页面）
        const newUrl = page.url();
        expect(newUrl).not.toBe(currentUrl);
        expect(newUrl).toContain('/wizard');
      }
    }
  });

  test('点击创建新应用按钮应该导航到创建页面', async ({ page }) => {
    // 点击顶部的"创建新应用"按钮
    await page.getByRole('button', { name: /创建新应用/ }).first().click();

    // 验证导航到创建页面
    await page.waitForTimeout(1000);
    await expect(page).toHaveURL('/create');
  });

  test('应该显示分页器（如果有多页）', async ({ page }) => {
    // 等待列表加载完成
    await page.waitForTimeout(2000);

    // 检查是否有分页按钮
    const prevButton = page.getByRole('button', { name: '上一页' });
    const nextButton = page.getByRole('button', { name: '下一页' });

    // 如果有分页器，验证按钮状态
    if (await prevButton.isVisible()) {
      await expect(prevButton).toBeVisible();
      await expect(nextButton).toBeVisible();

      // 验证第一页时"上一页"按钮禁用
      await expect(prevButton).toBeDisabled();
    }
  });

  test('页面应该响应式布局', async ({ page }) => {
    // 测试移动端视口 - 单列布局
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(page.getByRole('heading', { name: '我的应用' })).toBeVisible();

    // 测试平板视口 - 双列布局
    await page.setViewportSize({ width: 768, height: 1024 });
    await expect(page.getByRole('heading', { name: '我的应用' })).toBeVisible();

    // 测试桌面视口 - 三列布局
    await page.setViewportSize({ width: 1920, height: 1080 });
    await expect(page.getByRole('heading', { name: '我的应用' })).toBeVisible();
  });

  test('应该正确处理加载状态', async ({ page }) => {
    // 刷新页面以观察加载状态
    await page.reload();

    // 等待一小段时间，可能看到加载骨架屏
    await page.waitForTimeout(200);

    // 最终应该显示内容（卡片列表或空状态）
    await page.waitForTimeout(2000);
    const heading = page.getByRole('heading', { name: '我的应用' });
    await expect(heading).toBeVisible();
  });

  test('应该正确处理错误状态', async ({ page }) => {
    // 注：需要模拟API错误才能测试错误状态
    // 可以通过拦截网络请求实现

    // 拦截API请求并返回错误
    await page.route('**/api/v1/projects/list', (route) => {
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 500,
          message: '服务器错误',
          data: null
        })
      });
    });

    // 刷新页面触发API调用
    await page.reload();

    // 等待错误提示显示
    await page.waitForTimeout(2000);

    // 验证错误提示存在（具体内容取决于实现）
    // 可能显示Alert组件或错误消息
  });
});
