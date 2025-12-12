/**
 * 向导页面E2E测试
 * 测试AppSpec生成流程和步骤展示
 *
 * 测试策略：
 * - 使用API Mock模拟后端响应（仅用于前端E2E测试，不违反零Mock集成测试策略）
 * - 确保页面在各种场景下都能正确渲染
 * - 验证用户交互流程的完整性
 *
 * 注意：test-app-123直接显示完成状态，避免无限循环问题
 */
import { test, expect } from '@playwright/test';

/**
 * 测试前准备：添加必要的API Mock
 * 确保测试环境下API调用不会失败
 */
test.beforeEach(async ({ page }) => {
  // Mock getAppSpec API - 返回测试AppSpec数据
  await page.route('**/api/v1/appspecs/test-app-123', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          id: 'test-app-123',
          version: '1.0.0',
          tenantId: 'test-tenant',
          userId: 'test-user',
          createdAt: '2025-11-10T00:00:00Z',
          updatedAt: '2025-11-10T00:00:00Z',
          userRequirement: '测试应用需求描述',
          projectType: 'web',
          planResult: {
            modules: [
              {
                name: '用户管理模块',
                description: '处理用户注册、登录、权限管理等功能',
                priority: 'high',
                complexity: 3,
                dependencies: [],
                dataModels: ['User', 'Role'],
                pages: ['LoginPage', 'RegisterPage']
              },
              {
                name: '任务管理模块',
                description: '创建任务、分配任务、跟踪进度',
                priority: 'high',
                complexity: 4,
                dependencies: ['用户管理模块'],
                dataModels: ['Task', 'TaskAssignment'],
                pages: ['TaskListPage', 'TaskDetailPage']
              },
              {
                name: '团队协作模块',
                description: '团队成员管理、协作工具集成',
                priority: 'medium',
                complexity: 3,
                dependencies: ['用户管理模块'],
                dataModels: ['Team', 'TeamMember'],
                pages: ['TeamPage']
              },
              {
                name: '数据分析模块',
                description: '统计报表、数据可视化',
                priority: 'medium',
                complexity: 4,
                dependencies: ['任务管理模块'],
                dataModels: ['Report', 'Analytics'],
                pages: ['DashboardPage']
              }
            ],
            complexityScore: 75,
            reasoning: '基于功能模块数量和复杂度计算',
            suggestedTechStack: ['React', 'TypeScript', 'Next.js', 'PostgreSQL'],
            estimatedHours: 48,
            recommendations: '建议采用敏捷开发模式，分阶段交付'
          },
          validateResult: {
            isValid: true,
            qualityScore: 85,
            issues: [],
            suggestions: ['可以考虑添加单元测试', '建议完善错误处理']
          },
          isValid: true,
          qualityScore: 85,
          status: 'COMPLETED',
          generatedAt: '2025-11-10T00:00:00Z',
          durationMs: 5000
        }
      })
    });
  });

  // Mock generation task status API - 返回任务状态
  await page.route('**/v1/generate/status/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          taskId: 'task-123',
          taskName: '测试任务',
          userRequirement: '测试应用需求描述',
          status: 'COMPLETED',
          statusDescription: '生成完成',
          currentAgent: 'VALIDATE',
          progress: 100,
          agents: [
            {
              agentType: 'PLAN',
              agentName: '需求分析',
              status: 'completed',
              statusDescription: '分析完成',
              startedAt: '2025-11-10T00:00:00Z',
              completedAt: '2025-11-10T00:01:00Z',
              durationMs: 60000,
              progress: 100,
              resultSummary: '已识别4个功能模块',
              errorMessage: '',
              metadata: {}
            },
            {
              agentType: 'EXECUTE',
              agentName: 'AppSpec生成',
              status: 'completed',
              statusDescription: '生成完成',
              startedAt: '2025-11-10T00:01:00Z',
              completedAt: '2025-11-10T00:03:00Z',
              durationMs: 120000,
              progress: 100,
              resultSummary: '已生成AppSpec',
              errorMessage: '',
              metadata: {}
            },
            {
              agentType: 'VALIDATE',
              agentName: '质量验证',
              status: 'completed',
              statusDescription: '验证完成',
              startedAt: '2025-11-10T00:03:00Z',
              completedAt: '2025-11-10T00:05:00Z',
              durationMs: 120000,
              progress: 100,
              resultSummary: '质量评分: 85',
              errorMessage: '',
              metadata: {}
            }
          ],
          startedAt: '2025-11-10T00:00:00Z',
          completedAt: '2025-11-10T00:05:00Z',
          estimatedRemainingSeconds: 0,
          appSpecId: 'test-app-123',
          qualityScore: 85,
          downloadUrl: '/api/download/test-app-123',
          previewUrl: '/preview/test-app-123',
          tokenUsage: {
            planTokens: 1000,
            executeTokens: 2000,
            validateTokens: 500,
            totalTokens: 3500,
            estimatedCost: 0.035
          },
          errorMessage: '',
          createdAt: '2025-11-10T00:00:00Z',
          updatedAt: '2025-11-10T00:05:00Z'
        }
      })
    });
  });

  // Mock async generation API - 创建异步任务
  await page.route('**/v1/generate/async', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        data: {
          taskId: 'task-123'
        }
      })
    });
  });

  // Mock WebSocket连接（可选，如果需要测试WebSocket功能）
  // 注意：Playwright默认不支持WebSocket mock，这里仅添加示例
  // 实际WebSocket测试可能需要使用真实的WebSocket服务或专门的mock库
});

test.describe('向导页面功能测试', () => {
  test('应该显示生成完成标题', async ({ page }) => {
    // 使用测试ID访问向导页面
    await page.goto('/wizard/test-app-123');

    // 验证页面标题 - 完成状态显示"生成完成！"
    await expect(page.getByRole('heading', { name: '生成完成！' })).toBeVisible({ timeout: 10000 });

    // 验证显示App ID
    await expect(page.getByText(/ID: test-app-123/)).toBeVisible();
  });

  test('应该显示三个生成步骤Agent', async ({ page }) => {
    await page.goto('/wizard/test-app-123');

    // 等待页面加载完成
    await expect(page.getByRole('heading', { name: '生成完成！' })).toBeVisible({ timeout: 10000 });

    // 验证三个Agent显示（完成状态下在Agent执行结果卡片中）
    await expect(page.getByText('需求分析')).toBeVisible();
    await expect(page.getByText('AppSpec生成')).toBeVisible();
    await expect(page.getByText('质量验证')).toBeVisible();
  });

  test('应该显示总体进度条', async ({ page }) => {
    await page.goto('/wizard/test-app-123');

    // 等待页面加载完成
    await expect(page.getByRole('heading', { name: '生成完成！' })).toBeVisible({ timeout: 10000 });

    // 验证进度条存在
    await expect(page.getByText('总体进度')).toBeVisible();

    // 验证进度百分比显示
    await expect(page.getByText('100%')).toBeVisible();
  });

  test('生成完成后应该显示结果', async ({ page }) => {
    await page.goto('/wizard/test-app-123');

    // 等待生成完成（最多30秒）- 使用更具体的选择器
    await expect(page.getByRole('heading', { name: '生成完成！' })).toBeVisible({ timeout: 30000 });

    // 验证结果统计显示 - V2.0版本显示GenerationStats组件的统计信息
    await expect(page.getByText('页面数量', { exact: true })).toBeVisible();
    await expect(page.getByText('API端点', { exact: true })).toBeVisible();
    await expect(page.getByText('数据库表', { exact: true })).toBeVisible();
    await expect(page.getByText('代码行数', { exact: true })).toBeVisible();
    await expect(page.getByText('生成耗时', { exact: true })).toBeVisible();
  });

  test('生成完成后应该显示操作按钮', async ({ page }) => {
    await page.goto('/wizard/test-app-123');

    // 等待生成完成 - 使用更具体的选择器
    await expect(page.getByRole('heading', { name: '生成完成！' })).toBeVisible({ timeout: 30000 });

    // 验证QuickActionCards中的操作卡片存在（V2.0使用Card布局，不是Button）
    // 使用heading level 3来定位卡片标题
    await expect(page.getByRole('heading', { level: 3, name: /预览应用/ })).toBeVisible();
    await expect(page.getByRole('heading', { level: 3, name: /下载代码/ })).toBeVisible();
    await expect(page.getByRole('heading', { level: 3, name: /SuperDesign方案/ })).toBeVisible();
    await expect(page.getByRole('heading', { level: 3, name: /配置发布/ })).toBeVisible();
    await expect(page.getByRole('heading', { level: 3, name: /应用设置/ })).toBeVisible();
    await expect(page.getByRole('heading', { level: 3, name: /分享应用/ })).toBeVisible();
  });

  test('点击预览按钮应该可以点击', async ({ page }) => {
    await page.goto('/wizard/test-app-123');

    // 等待生成完成
    await expect(page.getByRole('heading', { name: '生成完成！' })).toBeVisible({ timeout: 30000 });

    // 验证预览卡片可见（V2.0使用Link卡片，不是Button）
    const previewCard = page.getByRole('heading', { level: 3, name: /预览应用/ });
    await expect(previewCard).toBeVisible();

    // 点击卡片 - 点击heading会触发父级Link的导航
    await previewCard.click();

    // 验证导航到预览页面
    await expect(page).toHaveURL(/\/preview\/test-app-123/, { timeout: 5000 });
  });

  test('点击发布按钮应该可以点击', async ({ page }) => {
    await page.goto('/wizard/test-app-123');

    // 等待生成完成
    await expect(page.getByRole('heading', { name: '生成完成！' })).toBeVisible({ timeout: 30000 });

    // 验证配置发布卡片可见（V2.0使用Link卡片，标题为"配置发布"不是"发布应用"）
    const publishCard = page.getByRole('heading', { level: 3, name: /配置发布/ });
    await expect(publishCard).toBeVisible();

    // 点击卡片 - 点击heading会触发父级Link的导航
    await publishCard.click();

    // 验证导航到发布页面
    await expect(page).toHaveURL(/\/publish\/test-app-123/, { timeout: 5000 });
  });

  test('应该显示Agent执行结果', async ({ page }) => {
    await page.goto('/wizard/test-app-123');

    // 等待生成完成
    await expect(page.getByRole('heading', { name: '生成完成！' })).toBeVisible({ timeout: 30000 });

    // 验证Agent执行结果卡片
    await expect(page.getByText('Agent执行结果')).toBeVisible();

    // 验证三个Agent的完成状态
    const completedBadges = page.locator('text=已完成');
    await expect(completedBadges).toHaveCount(3);
  });

  test('应该显示功能模块列表', async ({ page }) => {
    await page.goto('/wizard/test-app-123');

    // 等待生成完成
    await expect(page.getByRole('heading', { name: '生成完成！' })).toBeVisible({ timeout: 30000 });

    // 验证功能模块标题
    await expect(page.getByText('功能模块 (4)')).toBeVisible();

    // 验证4个模块显示
    await expect(page.getByText('用户管理模块')).toBeVisible();
    await expect(page.getByText('任务管理模块')).toBeVisible();
    await expect(page.getByText('团队协作模块')).toBeVisible();
    await expect(page.getByText('数据分析模块')).toBeVisible();
  });
});
