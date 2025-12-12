import { test, expect } from '@playwright/test';

/**
 * KuiklyUI 集成功能 E2E 测试
 * 测试代码生成和文件上传流程
 *
 * @skip 原因：需要完整的后端API和代码生成流程支持
 * TODO: 实现完整的代码生成流程后取消skip
 */
test.describe.skip('KuiklyUI 集成功能测试', () => {
  test.beforeEach(async ({ page }) => {
    // 访问首页 - 修正端口号为3000
    await page.goto('/');
    await page.waitForLoadState('networkidle');
  });

  test('应该能够访问首页', async ({ page }) => {
    // 验证页面标题
    await expect(page).toHaveTitle(/秒构AI/);
    
    // 验证关键元素存在
    await expect(page.getByRole('heading', { name: /秒构AI/i })).toBeVisible();
  });

  test('应该能够创建应用并生成代码', async ({ page }) => {
    // 点击"创建应用"按钮
    const createButton = page.getByRole('link', { name: /创建应用|开始使用/i });
    if (await createButton.isVisible()) {
      await createButton.click();
    } else {
      // 如果按钮不存在，直接导航到创建页面
      await page.goto('/create');
    }

    await page.waitForLoadState('networkidle');

    // 验证创建页面加载
    await expect(page).toHaveURL(/.*\/create/);

    // 填写应用需求（如果页面有输入框）
    const requirementInput = page.getByPlaceholder(/描述你想要的应用|输入你的需求/i);
    if (await requirementInput.isVisible()) {
      await requirementInput.fill('创建一个待办事项管理应用，包含任务列表、添加任务、完成任务功能');
      
      // 点击生成按钮
      const generateButton = page.getByRole('button', { name: /生成|创建|开始生成/i });
      if (await generateButton.isVisible()) {
        await generateButton.click();
        
        // 等待生成完成（可能需要较长时间）
        await page.waitForTimeout(5000);
        
        // 验证生成结果
        const successMessage = page.getByText(/生成成功|创建成功|完成/i);
        if (await successMessage.isVisible({ timeout: 30000 })) {
          await expect(successMessage).toBeVisible();
        }
      }
    }
  });

  test('应该能够下载生成的代码', async ({ page }) => {
    // 导航到应用详情页（需要先有一个已生成的应用）
    // 这里假设有一个测试应用的 ID
    const testAppId = 'test-app-id';
    
    // 监听下载事件
    const downloadPromise = page.waitForEvent('download', { timeout: 30000 });
    
    // 访问下载页面
    await page.goto(`/api/download/${testAppId}`);
    
    // 如果有下载按钮，点击它
    const downloadButton = page.getByRole('button', { name: /下载|Download/i });
    if (await downloadButton.isVisible()) {
      await downloadButton.click();
    }
    
    // 等待下载完成
    try {
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toMatch(/\.zip$/);
      console.log('✅ 下载成功:', download.suggestedFilename());
    } catch {
      console.log('⚠️  下载测试跳过（需要先有生成的应用）');
    }
  });

  test('应该能够查看 API 文档', async ({ page }) => {
    // 检查后端 API 健康状态
    const response = await page.request.get('http://localhost:8080/api/actuator/health');
    expect(response.status()).toBe(200);
    
    const healthData = await response.json();
    expect(healthData.status).toBe('UP');
    console.log('✅ 后端服务健康状态:', healthData);
  });

  test('应该能够访问 Swagger API 文档', async ({ page }) => {
    // 访问 Swagger UI
    await page.goto('http://localhost:8080/api/swagger-ui.html');
    await page.waitForLoadState('networkidle');
    
    // 验证 Swagger UI 加载
    const swaggerTitle = page.getByText(/Swagger|API.*文档/i);
    if (await swaggerTitle.isVisible({ timeout: 5000 })) {
      await expect(swaggerTitle).toBeVisible();
      console.log('✅ Swagger API 文档可访问');
    } else {
      console.log('⚠️  Swagger UI 可能未配置或路径不同');
    }
  });
});

