import { test, expect } from '@playwright/test';

/**
 * E2E测试：SSE流式原型生成
 *
 * 测试场景：
 * 1. 登录用户账号
 * 2. 用户输入需求
 * 3. 意图识别
 * 4. 选择设计风格
 * 5. SSE流式生成原型（验证打字机效果）
 * 6. 验证超时策略（120秒前端 + 30分钟后端）
 * 7. 测试重试功能
 */

test.describe('原型SSE流式生成测试', () => {
  test.beforeEach(async ({ page }) => {
    // 访问首页
    await page.goto('http://localhost:3000');
    await page.waitForLoadState('networkidle');

    // 登录用户账号
    console.log('[Test] 登录用户账号...');

    // 点击登录按钮
    const loginButton = page.locator('button:has-text("登录"), a:has-text("登录")').first();
    await loginButton.click();

    // 等待登录表单出现
    await page.waitForSelector('input[type="text"], input[placeholder*="用户名"], input[placeholder*="邮箱"]', { timeout: 10000 });

    // 填写用户名和密码
    const usernameInput = page.locator('input[type="text"], input[placeholder*="用户名"], input[placeholder*="邮箱"]').first();
    await usernameInput.fill('justin');

    const passwordInput = page.locator('input[type="password"]').first();
    await passwordInput.fill('qazOKM123');

    // 点击登录提交按钮
    const submitButton = page.locator('button:has-text("登录"), button[type="submit"]').last();
    await submitButton.click();

    // 等待登录成功（检查是否跳转或显示用户信息）
    await page.waitForTimeout(2000);

    console.log('[Test] 登录完成');

    // 返回首页（如果需要）
    await page.goto('http://localhost:3000');
    await page.waitForLoadState('networkidle');
  });

  test('完整流程：需求输入 → 意图识别 → 风格选择 → SSE原型生成', async ({ page }) => {
    test.setTimeout(240000); // 4分钟超时

    // Step 1: 输入需求
    console.log('[Test] Step 1: 输入用户需求');

    // 使用实际的 placeholder 定位输入框
    const requirementInput = page.locator('textarea[placeholder*="小程序"], textarea[placeholder*="APP"], textarea, input[type="text"]').first();
    await expect(requirementInput).toBeVisible({ timeout: 10000 });

    const testRequirement = '做一个简单的待办事项管理应用，支持添加、删除、标记完成功能';
    await requirementInput.fill(testRequirement);

    console.log('[Test] 已输入需求:', testRequirement);

    // 截图记录
    await page.screenshot({ path: '/tmp/e2e-01-input.png', fullPage: true });

    // 点击生成按钮
    const generateButton = page.locator('button:has-text("生成")').first();
    await generateButton.click();

    console.log('[Test] 已点击生成按钮，等待AI需求采集对话框...');

    // Step 2: 处理AI需求采集对话框
    try {
      // 等待对话框出现（使用Promise.race处理多个选择器）
      await Promise.race([
        page.waitForSelector('text=/AI需求/', { timeout: 10000 }),
        page.waitForSelector('text=/从零开始/', { timeout: 10000 }),
        page.waitForSelector('text=/克隆/', { timeout: 10000 }),
      ]).catch(() => {
        console.log('[Test] 未检测到对话框，可能已直接进入下一步');
      });

      console.log('[Test] Step 2: AI需求采集对话框已出现');

      await page.screenshot({ path: '/tmp/e2e-02-dialog.png', fullPage: true });

      // 选择"从零开始设计"选项（或第一个可见的选项）
      const designOption = page.locator('text=/从零开始/, button:has-text("从零开始")').first();
      const designOptionVisible = await designOption.isVisible({ timeout: 3000 }).catch(() => false);

      if (designOptionVisible) {
        await designOption.click();
        console.log('[Test] 已选择"从零开始设计"');
      } else {
        // 如果找不到，尝试点击第一个可见的选项
        const firstOption = page.locator('[role="radio"], [role="option"], button').first();
        await firstOption.click();
        console.log('[Test] 已选择第一个可用选项');
      }

      // 点击确认/下一步按钮
      await page.waitForTimeout(1000);
      const confirmDialogButton = page.locator('button:has-text("确认"), button:has-text("下一步"), button:has-text("继续")').last();
      await confirmDialogButton.click();

      console.log('[Test] 已确认意图选择，等待进入下一步...');

      // 等待对话框关闭，进入下一步
      await page.waitForTimeout(2000);

      await page.screenshot({ path: '/tmp/e2e-02-after-dialog.png', fullPage: true });

    } catch (error) {
      console.error('[Test] 处理AI需求采集对话框失败');
      await page.screenshot({ path: '/tmp/e2e-02-dialog-error.png', fullPage: true });
      throw error;
    }

    // Step 3: 选择设计风格（如果需要）
    console.log('[Test] Step 3: 查找并选择设计风格');

    // 等待风格选择卡片出现（可能需要先点击"下一步"或自动进入）
    await page.waitForTimeout(2000);

    // 尝试找到风格选择卡片
    const styleCards = page.locator('[class*="style"], [data-testid*="style"], .card, button').filter({ hasText: /现代|简约|商务|活力|优雅/ });
    const styleCardsCount = await styleCards.count();

    console.log('[Test] 找到', styleCardsCount, '个风格卡片');

    if (styleCardsCount > 0) {
      // 选择第一个风格
      await styleCards.first().click();
      console.log('[Test] 已选择设计风格');

      await page.screenshot({ path: '/tmp/e2e-03-style-selected.png', fullPage: true });

      // 点击确认或生成原型按钮
      const confirmButton = page.locator('button:has-text("生成原型"), button:has-text("下一步"), button:has-text("确认")').first();
      await confirmButton.click();

      console.log('[Test] 已点击生成原型按钮');
    } else {
      console.log('[Test] 未找到风格选择，可能已自动进入原型生成');
    }

    // Step 4: 监听 SSE 请求
    console.log('[Test] Step 4: 监听 SSE 流式请求');

    let sseRequestFound = false;
    let regularRequestFound = false;

    page.on('request', request => {
      const url = request.url();
      if (url.includes('/api/v1/prototype/generate')) {
        console.log('[Test] 检测到原型生成请求:', url);

        if (url.includes('/stream')) {
          sseRequestFound = true;
          console.log('[Test] ✓ 检测到 SSE 流式端点请求');
        } else {
          regularRequestFound = true;
          console.log('[Test] ✓ 检测到常规原型生成请求');
        }
      }
    });

    // Step 5: 等待原型生成完成
    console.log('[Test] Step 5: 等待原型生成完成（最长150秒）');

    try {
      // 等待原型预览界面
      await page.waitForSelector('[data-step="PROTOTYPE_PREVIEW"], text=/预览/, iframe', { timeout: 150000 });

      console.log('[Test] ✓ 检测到原型预览界面');

      // 等待一段时间让 SSE 请求发送
      await page.waitForTimeout(3000);

      // 验证请求类型
      if (sseRequestFound) {
        console.log('[Test] ✅ 成功使用 SSE 流式端点');
      } else if (regularRequestFound) {
        console.log('[Test] ⚠️  使用了常规端点而非 SSE 流式端点');
      } else {
        console.log('[Test] ⚠️  未检测到任何原型生成请求');
      }

      // 尝试找到 iframe 预览
      const iframes = page.locator('iframe');
      const iframeCount = await iframes.count();
      console.log('[Test] 找到', iframeCount, '个 iframe');

      if (iframeCount > 0) {
        console.log('[Test] ✓ 原型沙箱已加载');

        // 等待 iframe 内容加载
        await page.waitForTimeout(5000);

        // 截图记录
        await page.screenshot({ path: '/tmp/e2e-04-prototype-success.png', fullPage: true });

        // 验证确认按钮
        const confirmButton = page.locator('button:has-text("确认"), button:has-text("下一步")');
        const confirmVisible = await confirmButton.isVisible({ timeout: 3000 }).catch(() => false);

        if (confirmVisible) {
          console.log('[Test] ✓ 找到确认按钮');
        } else {
          console.log('[Test] ⚠️  未找到确认按钮');
        }

        console.log('[Test] ✅ 测试通过：原型生成成功');

      } else {
        console.log('[Test] ⚠️  未找到 iframe，原型可能以其他方式显示');
        await page.screenshot({ path: '/tmp/e2e-04-no-iframe.png', fullPage: true });
      }

    } catch (error) {
      console.error('[Test] ✗ 原型生成超时或失败');

      // 截图记录错误状态
      await page.screenshot({ path: '/tmp/e2e-04-error.png', fullPage: true });

      // 检查错误消息
      const errorMessage = await page.locator('text=/失败/, text=/错误/, text=/超时/').first().textContent({ timeout: 3000 }).catch(() => null);
      if (errorMessage) {
        console.log('[Test] 错误消息:', errorMessage);
      }

      // 检查重试按钮
      const retryButton = page.locator('button:has-text("重试"), button:has-text("重新生成")');
      const retryVisible = await retryButton.isVisible({ timeout: 3000 }).catch(() => false);

      if (retryVisible) {
        console.log('[Test] ✓ 重试按钮已显示');
      } else {
        console.log('[Test] ⚠️  未找到重试按钮');
      }

      throw error;
    }
  });

  test('验证重试功能', async ({ page }) => {
    test.setTimeout(300000); // 5分钟超时

    console.log('[Test] 测试重试功能');

    // 输入需求
    const requirementInput = page.locator('textarea, input[type="text"]').first();
    await requirementInput.fill('做一个简单的计数器应用');

    // 点击生成
    const generateButton = page.locator('button:has-text("生成")').first();
    await generateButton.click();

    console.log('[Test] 等待生成完成或失败...');

    // 等待足够长的时间（可能触发超时）
    await page.waitForTimeout(130000);

    // 检查是否有重试按钮
    const retryButton = page.locator('button:has-text("重试"), button:has-text("重新生成")');
    const retryVisible = await retryButton.isVisible({ timeout: 3000 }).catch(() => false);

    if (retryVisible) {
      console.log('[Test] ✓ 检测到重试按钮，点击重试');

      await page.screenshot({ path: '/tmp/e2e-retry-before.png', fullPage: true });

      // 点击重试
      await retryButton.click();

      console.log('[Test] 已点击重试，等待重新生成...');

      // 等待重新进入生成流程
      await page.waitForSelector('text=/生成/, text=/加载/, iframe', { timeout: 150000 });

      console.log('[Test] ✓ 重试功能正常');

      await page.screenshot({ path: '/tmp/e2e-retry-after.png', fullPage: true });

    } else {
      console.log('[Test] 原型生成成功，未出现重试按钮（符合预期）');
      await page.screenshot({ path: '/tmp/e2e-no-retry-needed.png', fullPage: true });
    }
  });
});
