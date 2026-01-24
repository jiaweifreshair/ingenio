import { test } from '@playwright/test';

test('检查wizard完成页面按钮功能', async ({ page }) => {
  // 监听console输出
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  // 监听页面错误
  page.on('pageerror', error => console.log('PAGE ERROR:', error.message));

  // 访问页面
  console.log('正在访问wizard页面...');
  await page.goto('http://localhost:3000/wizard/546c0d51-ad21-4dc7-81c5-2ea6353fc931', {
    waitUntil: 'networkidle',
    timeout: 30000
  });

  console.log('页面加载完成');

  // 获取页面标题
  const title = await page.title();
  console.log('页面标题:', title);

  // 等待页面完全加载
  await page.waitForTimeout(2000);

  // 检查页面状态Badge
  const statusBadge = await page.locator('text="生成完成"').count();
  console.log('状态badge存在:', statusBadge > 0);

  // 检查所有按钮
  const buttons = await page.locator('button').all();
  console.log(`找到 ${buttons.length} 个按钮`);

  // 检查主要操作入口
  const mainButtons = [
    '下载代码',
    '配置发布',
    '应用设置',
    '分享应用'
  ];

  for (const buttonText of mainButtons) {
    const button = page.getByRole('button', { name: buttonText });
    const exists = await button.count() > 0;
    console.log(`\n按钮 "${buttonText}":`);
    console.log(`  - 存在: ${exists}`);

    if (exists) {
      const isVisible = await button.isVisible();
      const isEnabled = await button.isEnabled();
      console.log(`  - 可见: ${isVisible}`);
      console.log(`  - 可点击: ${isEnabled}`);

      // 获取按钮的onClick handler
      const hasOnClick = await button.evaluate((el) => {
        return typeof (el as HTMLButtonElement).onclick === 'function' ||
               el.hasAttribute('onclick');
      });
      console.log(`  - 有onClick: ${hasOnClick}`);

      // 尝试点击按钮看是否有反应
      if (isEnabled && buttonText === '生成新的AppSpec') {
        console.log(`\n测试点击 "${buttonText}" 按钮...`);
        const initialUrl = page.url();
        await button.click();
        await page.waitForTimeout(1000);
        const newUrl = page.url();
        console.log(`  - 点击前URL: ${initialUrl}`);
        console.log(`  - 点击后URL: ${newUrl}`);
        console.log(`  - URL变化: ${initialUrl !== newUrl}`);
      }
    }
  }

  // 检查探索更多功能按钮
  console.log('\n\n检查"探索更多功能"按钮:');
  const exploreButtons = [
    'AI能力选择',
    'SuperDesign',
    '时光机版本'
  ];

  for (const buttonText of exploreButtons) {
    const button = page.getByRole('button', { name: new RegExp(buttonText) });
    const exists = await button.count() > 0;
    console.log(`\n按钮包含 "${buttonText}":`);
    console.log(`  - 存在: ${exists}`);

    if (exists) {
      const isVisible = await button.isVisible();
      const isEnabled = await button.isEnabled();
      console.log(`  - 可见: ${isVisible}`);
      console.log(`  - 可点击: ${isEnabled}`);
    }
  }

  // 截图
  await page.screenshot({ path: '/tmp/wizard-page.png', fullPage: true });
  console.log('\n已保存截图到: /tmp/wizard-page.png');
});
