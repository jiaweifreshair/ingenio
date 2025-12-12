/**
 * AI能力选择器调试测试
 * 用于诊断为什么卡片没有显示
 */
import { test, expect } from '@playwright/test';

test.describe('AI能力选择器调试', () => {
  test('调试：检查页面加载和元素状态', async ({ page }) => {
    console.log('🔍 开始调试测试...');

    // 1. 访问页面
    console.log('1️⃣ 访问页面: /wizard/ai-capabilities');
    await page.goto('/wizard/ai-capabilities');
   await page.waitForLoadState('networkidle');

    // 2. 截图初始状态
    await page.screenshot({ path: 'debug-step1-initial.png', fullPage: true });
    console.log('📸 截图已保存: debug-step1-initial.png');

    // 3. 检查页面标题
    const title = await page.title();
    console.log(`📄 页面标题: ${title}`);

    // 4. 检查ai-capability-picker容器
    const pickerContainer = page.locator('[data-testid="ai-capability-picker"]');
    const pickerExists = await pickerContainer.count();
    console.log(`🎯 ai-capability-picker容器数量: ${pickerExists}`);

    if (pickerExists > 0) {
      await expect(pickerContainer).toBeVisible();
      console.log('✅ ai-capability-picker容器可见');
    } else {
      console.log('❌ 找不到ai-capability-picker容器');
    }

    // 5. 等待更长时间，看看卡片是否会出现
    console.log('⏳ 等待5秒，看卡片是否会出现...');
    await page.waitForTimeout(5000);

    // 6. 再次截图
    await page.screenshot({ path: 'debug-step2-after-wait.png', fullPage: true });
    console.log('📸 截图已保存: debug-step2-after-wait.png');

    // 7. 检查capability-card
    const cards = page.locator('[data-testid="capability-card"]');
    const cardCount = await cards.count();
    console.log(`🎴 capability-card卡片数量: ${cardCount}`);

    if (cardCount > 0) {
      console.log('✅ 找到了卡片！');
      // 检查第一个卡片的文本
      const firstCardText = await cards.first().textContent();
      console.log(`📝 第一个卡片内容: ${firstCardText}`);
    } else {
      console.log('❌ 没有找到任何卡片');

      // 检查是否有空状态
      const emptyState = page.locator('text=暂无匹配的AI能力');
      const hasEmptyState = await emptyState.count();
      console.log(`🗑️ 空状态提示数量: ${hasEmptyState}`);

      // 检查页面上的所有可见文本
      const bodyText = await page.locator('body').textContent();
      console.log(`📃 页面body文本（前500字符）: ${bodyText?.substring(0, 500)}`);
    }

    // 8. 检查控制台错误
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        console.log(`❌ 控制台错误: ${msg.text()}`);
      }
    });

    // 9. 检查网络请求
    page.on('response', (response) => {
      if (!response.ok()) {
        console.log(`❌ 请求失败: ${response.url()} - ${response.status()}`);
      }
    });

    // 10. 检查是否有AI_CAPABILITIES数据
    const hasAICaps = await page.evaluate(() => {
      return typeof (window as { AI_CAPABILITIES?: unknown }).AI_CAPABILITIES !== 'undefined';
    });
    console.log(`📦 window.AI_CAPABILITIES存在: ${hasAICaps}`);

    // 11. 检查React组件是否渲染
    const reactRoot = page.locator('#__next');
    const reactRootExists = await reactRoot.count();
    console.log(`⚛️ React根节点存在: ${reactRootExists > 0}`);

    // 12. 最终截图
    await page.screenshot({ path: 'debug-step3-final.png', fullPage: true });
    console.log('📸 最终截图已保存: debug-step3-final.png');

    // 断言：至少应该能看到页面标题
    // 使用精确的heading level来避免strict mode violation
    await expect(page.getByRole('heading', { level: 2, name: /选择AI能力/ })).toBeVisible();
    console.log('✅ 页面标题"选择AI能力"可见');
  });

  test('调试：检查组件props', async ({ page }) => {
    console.log('🔍 检查组件props...');

    await page.goto('/wizard/ai-capabilities');
    await page.waitForLoadState('networkidle');

    // 检查输入框是否存在
    const userRequirementInput = page.locator('[data-testid="user-requirement-input"]');
    const inputExists = await userRequirementInput.count();
    console.log(`📝 需求输入框存在: ${inputExists > 0}`);

    if (inputExists > 0) {
      // 尝试输入需求
      await userRequirementInput.fill('测试需求');
      console.log('✅ 已输入测试需求');

      // 等待防抖
      await page.waitForTimeout(500);

      // 检查卡片
      const cards = page.locator('[data-testid="capability-card"]');
      const cardCount = await cards.count();
      console.log(`🎴 输入需求后，卡片数量: ${cardCount}`);
    }
  });
});
