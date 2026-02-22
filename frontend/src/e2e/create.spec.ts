/**
 * 创建页面E2E测试
 * 测试应用创建表单和提交流程（完整V2.0流程：分析 -> 风格 -> 生成 -> 导航）
 */
import { test, expect, type Page } from '@playwright/test';
import { silencePageConsole } from './utils/console-guard';

// 辅助函数：设置认证Token到Cookie和localStorage
async function setAuthToken(page: Page, token: string = 'mock_jwt_token_for_testing') {
  await page.context().addCookies([
    {
      name: 'auth_token',
      value: token,
      domain: 'localhost',
      path: '/',
    }
  ]);

  await page.evaluate((t) => {
    localStorage.setItem('auth_token', t);
  }, token);
}

test.describe('创建页面功能测试', () => {
  test.beforeEach(async ({ page }) => {
    silencePageConsole(page);

    // 0. 模拟登录状态
    // 先导航到一个公共页面以建立上下文
    await page.goto('/login');
    await setAuthToken(page);

    // 1. Mock SSE分析接口
    await page.route('**/v1/generate/analyze-stream', async (route) => {
      // 构建SSE格式的响应字符串
      const msg1 = JSON.stringify({
        step: 1,
        stepName: '意图识别',
        description: '正在分析您的需求...',
        status: 'RUNNING',
        progress: 20,
        timestamp: new Date().toISOString()
      });
      const msg2 = JSON.stringify({
        step: 2,
        stepName: '结构规划',
        description: '正在规划应用结构...',
        status: 'RUNNING',
        progress: 80,
        timestamp: new Date().toISOString()
      });
      const completeMsg = JSON.stringify({ result: 'ok' });

      const sseBody = [
        `event: progress\ndata: ${msg1}\n\n`,
        `event: progress\ndata: ${msg2}\n\n`,
        `event: complete\ndata: ${completeMsg}\n\n`,
      ].join('');

      await route.fulfill({
        status: 200,
        contentType: 'text/event-stream',
        body: sseBody,
      });
    });

    // 2. Mock 风格生成接口
    await page.route('**/v1/superdesign/generate-7-styles', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            styles: Array(7).fill(null).map((_, i) => ({
              style: i === 0 ? 'DEFAULT' : `STYLE_${i}`,
              htmlContent: '<div class="preview">Style Preview</div>',
              aiGenerated: true,
              generationTime: 100
            })),
            totalGenerationTime: 700,
            warnings: []
          }
        }),
      });
    });

    // 3. Mock 完整生成接口
    await page.route('**/v1/generate/full', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            appSpecId: '12345678-1234-1234-1234-1234567890ab',
            planResult: {
              modules: [{ name: '测试模块', description: '测试描述' }],
              complexityScore: 50,
              reasoning: '测试推理',
              suggestedTechStack: ['React', 'Node.js'],
              estimatedHours: 40,
              recommendations: '测试建议',
            },
            validateResult: {
              isValid: true,
              qualityScore: 85,
              issues: [],
              suggestions: [],
            },
            isValid: true,
            qualityScore: 85,
            status: 'completed',
            durationMs: 1000,
            generatedAt: new Date().toISOString(),
          },
          metadata: {
            requestId: 'test-123',
            timestamp: new Date().toISOString(),
            latencyMs: 100,
          },
        }),
      });
    });
    
    // 4. Mock 用户信息接口 (防止前端获取用户信息失败)
    await page.route('**/v1/user/profile', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          data: {
            id: 'test-user-id',
            username: 'testuser',
            email: 'test@example.com',
            avatar: 'https://example.com/avatar.png'
          }
        })
      });
    });
    
    // 5. Mock 消息通知接口 (TopNav轮询)
    await page.route('**/v1/notifications/unread-count', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(0)
      });
    });

    await page.goto('/create');
  });

  test('应该正确显示创建页面元素', async ({ page }) => {
    // 验证页面标题
    await expect(page.getByRole('heading', { name: /创建新应用/ })).toBeVisible();

    // 验证输入框存在
    await expect(page.getByPlaceholder(/描述你想要的应用/)).toBeVisible();

    // 验证提交按钮存在
    await expect(page.getByRole('button', { name: /直接生成/ })).toBeVisible();
  });

  test('空表单提交应该显示验证错误', async ({ page }) => {
    // 空表单时，按钮应该被disabled
    const submitButton = page.getByRole('button', { name: /直接生成/ });
    await expect(submitButton).toBeDisabled();
  });

  test('提交有效表单应走完完整流程并导航', async ({ page }) => {
    // 1. 输入需求描述
    await page.getByPlaceholder(/描述你想要的应用/).fill('创建一个问卷调查系统，包含问卷设计、发布和统计功能');

    // 2. 点击生成按钮
    const submitButton = page.getByRole('button', { name: /直接生成/ });
    await expect(submitButton).toBeEnabled();
    await submitButton.click();

    // 3. 验证进入分析阶段
    // 检查是否显示分析进度面板 (AnalysisProgressPanel)
    await expect(page.getByText('正在分析您的需求...')).toBeVisible();

    // 4. 验证进入风格选择阶段
    // 等待风格选择器出现 (StylePicker)
    await expect(page.getByText('选择你喜欢的设计风格')).toBeVisible({ timeout: 15000 });
    
    // 验证风格卡片加载
    await expect(page.locator('.preview').first()).toBeVisible();

    // 5. 选择一个风格
    // 点击第一个风格的"选择此风格"按钮
    const selectButton = page.getByRole('button', { name: '选择此风格' }).first();
    await selectButton.click();

    // 6. 验证导航到向导页面
    // URL应该包含 /wizard/12345678-1234-1234-1234-1234567890ab
    await expect(page).toHaveURL(new RegExp('/wizard/12345678-1234-1234-1234-1234567890ab'), { timeout: 10000 });
  });
});
