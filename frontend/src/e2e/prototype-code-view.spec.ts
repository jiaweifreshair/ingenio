/**
 * E2E测试：验证原型生成的代码视图功能
 *
 * 测试场景：
 * 1. 访问首页
 * 2. 输入需求并生成原型
 * 3. 验证E2B沙箱预览加载
 * 4. 验证代码视图显示生成的文件
 */

import { test, expect } from '@playwright/test';

test.describe('原型生成代码视图测试', () => {
  test('应该正确显示生成的代码文件', async ({ page }) => {
    // 设置较长的超时时间（原型生成需要时间）
    test.setTimeout(120000);

    // 1. 访问首页
    await page.goto('http://localhost:3000');
    await expect(page).toHaveTitle(/Ingenio|秒构AI/);

    console.log('[E2E] ✅ 首页加载成功');

    // 2. 输入需求
    const requirementInput = page.getByPlaceholder(/在这里输入你想做什么/i);
    await requirementInput.fill('做一个简单的登录页面');

    console.log('[E2E] ✅ 已输入需求');

    // 3. 点击"生成"按钮
    const generateButton = page.getByRole('button', { name: /生成/i });
    await generateButton.click();

    console.log('[E2E] ✅ 已点击开始分析');

    // 4. 等待意图识别完成（最多30秒）
    await page.waitForSelector('text=/意图识别|设计风格/i', { timeout: 30000 });

    console.log('[E2E] ✅ 意图识别完成');

    // 5. 选择风格（选择第一个风格）
    const styleCard = page.locator('[data-testid="style-card"]').first();
    if (await styleCard.count() > 0) {
      await styleCard.click();
    } else {
      // 备用方案：查找任何风格按钮
      const anyStyleButton = page.getByRole('button', { name: /选择|现代/i }).first();
      await anyStyleButton.click();
    }

    console.log('[E2E] ✅ 已选择设计风格');

    // 6. 等待原型生成（显示"正在生成原型预览"）
    await page.waitForSelector('text=/正在生成原型|原型预览/i', { timeout: 10000 });

    console.log('[E2E] ✅ 开始生成原型');

    // 7. 等待沙箱URL出现（iframe加载）- 最多60秒
    // const _sandboxFrame = page.frameLocator('iframe[title="原型预览"]');
    await page.waitForSelector('iframe[title="原型预览"]', { timeout: 60000 });

    console.log('[E2E] ✅ E2B沙箱加载成功');

    // 8. 验证预览标签页显示
    const previewTab = page.getByRole('tab', { name: /预览/i });
    await expect(previewTab).toBeVisible();

    // 9. 点击"代码"标签页
    const codeTab = page.getByRole('tab', { name: /代码/i });
    await codeTab.click();

    console.log('[E2E] ✅ 已切换到代码视图');

    // 10. 验证文件树显示
    const fileTree = page.locator('text=/src|App\\.jsx|index\\.css/i').first();
    await expect(fileTree).toBeVisible({ timeout: 5000 });

    console.log('[E2E] ✅ 文件树显示成功');

    // 11. 验证至少有一个文件
    const fileItems = page.locator('[role="button"]', { hasText: /\\.jsx|\\.css|\\.js/ });
    const fileCount = await fileItems.count();
    expect(fileCount).toBeGreaterThan(0);

    console.log(`[E2E] ✅ 检测到 ${fileCount} 个文件`);

    // 12. 点击第一个文件
    await fileItems.first().click();

    console.log('[E2E] ✅ 已选择文件');

    // 13. 验证代码高亮显示
    const codeBlock = page.locator('code, pre').first();
    await expect(codeBlock).toBeVisible({ timeout: 3000 });

    console.log('[E2E] ✅ 代码高亮显示成功');

    // 14. 验证复制按钮存在
    const copyButton = page.getByRole('button', { name: /复制代码/i });
    await expect(copyButton).toBeVisible();

    console.log('[E2E] ✅ 复制按钮显示');

    // 15. 截图保存
    await page.screenshot({ path: '/tmp/prototype-code-view-success.png', fullPage: true });

    console.log('[E2E] ✅ 测试完成，截图已保存');
  });
});
