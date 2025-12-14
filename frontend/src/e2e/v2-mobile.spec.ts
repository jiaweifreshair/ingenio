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
 * 执行需求输入步骤 (适配新首页HeroBanner)
 */
async function submitRequirement(page: Page, requirement: string) {
  const input = page.locator('textarea[placeholder*="在这里输入你想做什么"]');
  await input.fill(requirement);
  
  // 移动端点击生成按钮
  const submitBtn = page.locator('button:has-text("生成")');
  await submitBtn.tap();
  
  // 等待向导出现
  await page.waitForSelector('text=深度分析', { timeout: 30000 });
}

// ==================== iPhone SE 测试 ====================

test.describe('V2.0 移动端测试 - iPhone SE (375x667)', () => {
  test.use({ ...MOBILE_DEVICES.iPhoneSE });

  test('页面应该在小屏幕上正确渲染', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    // 验证页面标题可见
    await expect(page.locator('text=你的创意，AI 来实现')).toBeVisible();

    // 验证输入框可见且可交互
    const input = page.locator('textarea[placeholder*="在这里输入你想做什么"]');
    await expect(input).toBeVisible();
    await expect(input).toBeEnabled();

    // 验证提交按钮可见
    await expect(page.locator('button:has-text("生成")')).toBeVisible();
  });

  test('完整流程应该在iPhone SE上可用', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    // Step 1: 输入需求并提交
    await submitRequirement(page, TEST_REQUIREMENT);

    // Step 2: 验证进入分析阶段
    await expect(page.locator('text=AI正在分析您的需求')).toBeVisible();

    // Step 3: 等待自动跳转到原型确认 (由于分析是模拟的或依赖后端，这可能需要时间)
    // 注意：如果是真实环境，可能需要mock SSE或等待较长时间
    // 这里我们主要验证进入了向导模式
    
    // 验证进度条可见
    await expect(page.locator('.bg-purple-100')).toBeVisible(); // 步骤图标背景
  });
});

// ==================== iPhone 12 测试 ====================

test.describe('V2.0 移动端测试 - iPhone 12 (390x844)', () => {
  test.use({ ...MOBILE_DEVICES.iPhone12 });

  test('页面应该在iPhone 12上正确渲染', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    await expect(page.locator('textarea[placeholder*="在这里输入你想做什么"]')).toBeVisible();
  });

  test('触摸交互应该正常工作', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    const input = page.locator('textarea[placeholder*="在这里输入你想做什么"]');
    
    // 触摸聚焦
    await input.tap();
    
    // 模拟输入
    await input.fill('测试触摸输入');
    
    await expect(input).toHaveValue('测试触摸输入');
  });
});

// ==================== Pixel 5 测试 ====================

test.describe('V2.0 移动端测试 - Pixel 5 (393x851)', () => {
  test.use({ ...MOBILE_DEVICES.pixel5 });

  test('页面应该在Pixel 5上正确渲染', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    await expect(page.locator('textarea[placeholder*="在这里输入你想做什么"]')).toBeVisible();
  });
});

// ==================== iPad 测试 ====================

test.describe('V2.0 移动端测试 - iPad (768x1024)', () => {
  test.use({ ...MOBILE_DEVICES.iPad });

  test('页面应该在iPad上正确渲染', async ({ page }) => {
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    await expect(page.locator('textarea[placeholder*="在这里输入你想做什么"]')).toBeVisible();
  });

  test('iPad横屏适配', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 768 });
    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    const input = page.locator('textarea[placeholder*="在这里输入你想做什么"]');
    await expect(input).toBeVisible();
    
    // 验证宽度自适应（比手机宽）
    const box = await input.boundingBox();
    expect(box?.width).toBeGreaterThan(400);
  });
});

// ==================== 性能测试 ====================

test.describe('V2.0 移动端测试 - 性能', () => {
  test.use({ ...MOBILE_DEVICES.iPhone12 });

  test('移动端页面加载时间应该<5秒', async ({ page }) => {
    const startTime = Date.now();

    await page.goto(`${BASE_URL}/`);
    await waitForPageLoad(page);

    const loadTime = Date.now() - startTime;

    expect(loadTime).toBeLessThan(5000);
    console.log(`移动端页面加载时间: ${loadTime}ms`);
  });
});
