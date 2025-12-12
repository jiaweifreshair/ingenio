import { test, expect } from '@playwright/test';
import * as path from 'path';
import * as fs from 'fs';

/**
 * 全面的页面截图验证测试
 * 测试所有主要页面的渲染和功能
 */

const screenshotDir = '/tmp/ingenio-screenshots';

// 确保截图目录存在
if (!fs.existsSync(screenshotDir)) {
  fs.mkdirSync(screenshotDir, { recursive: true });
}

test.describe('Ingenio前端页面全面验证', () => {
  test.setTimeout(120000); // 设置2分钟超时

  // 测试首页
  test('01-首页 (/)', async ({ page }) => {
    await page.goto('http://localhost:3000/');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲
    await page.screenshot({
      path: path.join(screenshotDir, '01-homepage.png'),
      fullPage: true
    });

    // 验证关键元素
    await expect(page.locator('text=秒构AI').first()).toBeVisible();
  });

  // 测试创建页面
  test('02-创建页面 (/create)', async ({ page }) => {
    await page.goto('http://localhost:3000/create');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲
    await page.screenshot({
      path: path.join(screenshotDir, '02-create-page.png'),
      fullPage: true
    });

    // 验证表单存在
    await expect(page.locator('textarea').first()).toBeVisible();
  });

  // 测试模板页面
  test('03-模板页面 (/templates)', async ({ page }) => {
    await page.goto('http://localhost:3000/templates');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲
    await page.waitForTimeout(2000); // 等待加载
    await page.screenshot({
      path: path.join(screenshotDir, '03-templates-page.png'),
      fullPage: true
    });
  });

  // 测试Dashboard页面
  test('04-Dashboard (/dashboard)', async ({ page }) => {
    await page.goto('http://localhost:3000/dashboard');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲
    await page.waitForTimeout(2000);
    await page.screenshot({
      path: path.join(screenshotDir, '04-dashboard-page.png'),
      fullPage: true
    });
  });

  // 测试账户页面
  test('05-账户页面 (/account)', async ({ page }) => {
    await page.goto('http://localhost:3000/account');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲
    await page.waitForTimeout(2000);
    await page.screenshot({
      path: path.join(screenshotDir, '05-account-page.png'),
      fullPage: true
    });
  });

  // 测试通知页面
  test('06-通知页面 (/notifications)', async ({ page }) => {
    await page.goto('http://localhost:3000/notifications');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲
    await page.waitForTimeout(2000);
    await page.screenshot({
      path: path.join(screenshotDir, '06-notifications-page.png'),
      fullPage: true
    });
  });

  // 测试AI能力选择页面
  test('07-AI能力选择 (/wizard/ai-capabilities)', async ({ page }) => {
    await page.goto('http://localhost:3000/wizard/ai-capabilities');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲
    await page.waitForTimeout(2000);
    await page.screenshot({
      path: path.join(screenshotDir, '07-ai-capabilities-page.png'),
      fullPage: true
    });
  });

  // 测试创建流程 - 输入需求
  test('08-创建流程-需求输入', async ({ page }) => {
    await page.goto('http://localhost:3000/create');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲

    const textarea = page.locator('textarea').first();
    await textarea.fill('我想创建一个电商平台，包含商品展示、购物车和订单管理功能');
    await page.waitForTimeout(1000);

    await page.screenshot({
      path: path.join(screenshotDir, '08-create-with-input.png'),
      fullPage: true
    });
  });

  // 测试响应式布局 - 移动端视图
  test('09-移动端视图-首页', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 }); // iPhone X尺寸
    await page.goto('http://localhost:3000/');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲
    await page.screenshot({
      path: path.join(screenshotDir, '09-mobile-homepage.png'),
      fullPage: true
    });
  });

  // 测试响应式布局 - 平板视图
  test('10-平板视图-首页', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 }); // iPad尺寸
    await page.goto('http://localhost:3000/');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲
    await page.screenshot({
      path: path.join(screenshotDir, '10-tablet-homepage.png'),
      fullPage: true
    });
  });

  // 测试暗色模式
  test('11-暗色模式-首页', async ({ page }) => {
    await page.goto('http://localhost:3000/');
    await page.waitForLoadState('domcontentloaded'); // 改用domcontentloaded，不等待网络空闲

    // 尝试切换到暗色模式
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.waitForTimeout(500);

    await page.screenshot({
      path: path.join(screenshotDir, '11-dark-mode-homepage.png'),
      fullPage: true
    });
  });

  // 生成验证报告
  test.afterAll(async () => {
    const files = fs.readdirSync(screenshotDir);
    console.log('\n=================================');
    console.log('📸 截图验证完成');
    console.log('=================================');
    console.log(`✅ 总共生成 ${files.length} 张截图`);
    console.log(`📁 截图保存位置: ${screenshotDir}`);
    console.log('=================================');
    console.log('截图清单:');
    files.forEach((file, index) => {
      console.log(`  ${index + 1}. ${file}`);
    });
    console.log('=================================\n');
  });
});
