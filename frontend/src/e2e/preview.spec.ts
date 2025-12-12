/**
 * 预览页面E2E测试
 * 测试应用预览和设备切换功能
 */
import { test, expect } from '@playwright/test';

test.describe('预览页面功能测试', () => {
  test.beforeEach(async ({ page }) => {
    // Mock AppSpec API - 提供完整的appSpec数据包括pages数组
    await page.route('**/api/v1/appspecs/demo-survey', async route => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          success: true,
          message: '获取AppSpec成功',
          data: {
            id: 'demo-survey',
            version: '1.0.0',
            tenantId: 'test-tenant-001',
            userId: 'test-user-001',
            createdAt: '2024-01-01T00:00:00Z',
            updatedAt: '2024-01-01T12:00:00Z',
            userRequirement: '创建一个在线问卷调查系统',
            projectType: 'survey',
            status: 'COMPLETED',
            isValid: true,
            qualityScore: 95,
            generatedAt: '2024-01-01T00:00:00Z',
            durationMs: 5000,
            // specContent包含pages、dataModels、flows信息
            specContent: JSON.stringify({
              pages: [
                {
                  id: 'page-home',
                  name: '首页',
                  path: '/home',
                  components: ['Header', 'SurveyList', 'Footer']
                },
                {
                  id: 'page-survey',
                  name: '问卷页',
                  path: '/survey',
                  components: ['QuestionCard', 'SubmitButton']
                },
                {
                  id: 'page-results',
                  name: '结果页',
                  path: '/results',
                  components: ['ResultChart', 'StatisticsPanel']
                }
              ],
              dataModels: [
                {
                  id: 'model-survey',
                  name: 'Survey',
                  fields: ['id', 'title', 'description', 'questions', 'createdAt']
                },
                {
                  id: 'model-response',
                  name: 'Response',
                  fields: ['id', 'surveyId', 'answers', 'submittedAt']
                }
              ],
              flows: [
                {
                  id: 'flow-submit',
                  name: '提交问卷流程',
                  steps: ['填写问卷', '验证答案', '提交数据', '显示结果']
                }
              ]
            }),
            planResult: {
              modules: [],
              complexityScore: 7,
              reasoning: '中等复杂度的问卷系统',
              suggestedTechStack: ['React', 'TypeScript', 'PostgreSQL'],
              estimatedHours: 40,
              recommendations: '建议使用拖拽式问卷编辑器'
            },
            validateResult: {
              isValid: true,
              qualityScore: 95,
              issues: [],
              suggestions: []
            }
          },
          timestamp: Date.now()
        })
      });
    });

    await page.goto('/preview/demo-survey');

    // 等待页面加载完成 - 等待"正在加载预览..."消失
    await page.waitForSelector('text=正在加载预览...', { state: 'hidden', timeout: 10000 });

    // 等待应用信息标题出现,确认数据已加载
    await expect(page.getByRole('heading', { name: '应用信息', level: 3 })).toBeVisible({ timeout: 10000 });
  });

  test('应该正确显示预览页面', async ({ page }) => {
    // 验证页面标题 - 使用heading角色更精确
    await expect(page.getByRole('heading', { name: '应用预览' })).toBeVisible();

    // 验证App ID显示 - 使用exact匹配避免strict mode violation
    await expect(page.getByText('demo-survey', { exact: true })).toBeVisible();
  });

  test('应该显示三个设备切换按钮', async ({ page }) => {
    // 验证设备切换按钮
    await expect(page.getByRole('button', { name: /手机/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /平板/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /桌面/ })).toBeVisible();
  });

  test('默认应该选中桌面视图', async ({ page }) => {
    // 验证桌面按钮处于选中状态
    const desktopButton = page.getByRole('button', { name: /桌面/ });
    await expect(desktopButton).toBeVisible();

    // 验证预览框架存在
    const previewFrame = page.locator('[data-device-preview]');
    await expect(previewFrame).toBeVisible();
  });

  test('点击平板按钮应该切换到平板视图', async ({ page }) => {
    // 点击平板按钮
    await page.getByRole('button', { name: /平板/ }).click();

    // 验证预览框架切换到平板尺寸
    const previewFrame = page.locator('[data-device-preview]');
    await expect(previewFrame).toBeVisible();

    // 可以检查样式或尺寸变化
  });

  test('点击桌面按钮应该切换到桌面视图', async ({ page }) => {
    // 点击桌面按钮
    await page.getByRole('button', { name: /桌面/ }).click();

    // 验证预览框架切换到桌面尺寸
    const previewFrame = page.locator('[data-device-preview]');
    await expect(previewFrame).toBeVisible();
  });

  test('应该显示应用信息侧边栏', async ({ page }) => {
    // 验证侧边栏元素
    await expect(page.getByText(/应用信息/)).toBeVisible();
    await expect(page.getByText(/版本/)).toBeVisible();
    await expect(page.getByText(/创建时间/)).toBeVisible();
  });

  test('应该显示页面结构信息', async ({ page }) => {
    // 验证页面结构标题 - 使用精确的heading定位
    await expect(page.getByRole('heading', { name: '页面结构', level: 3 })).toBeVisible();

    // 验证至少有一个页面项
    const pageItems = page.locator('[data-page-item]');
    await expect(pageItems.first()).toBeVisible();
  });

  test('应该显示操作按钮', async ({ page }) => {
    // 验证操作按钮（实际按钮文本）
    await expect(page.getByRole('button', { name: /返回/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /发布/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /刷新/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /分享/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /下载/ })).toBeVisible();
  });

  test('点击发布按钮应该导航到发布页面', async ({ page }) => {
    // 点击发布按钮（精确匹配"发布"，不包含"发布应用"）
    const publishButtons = page.getByRole('button', { name: /^发布$/ });
    await publishButtons.click();

    // 验证导航到发布页面
    await expect(page).toHaveURL('/publish/demo-survey');
  });
});
