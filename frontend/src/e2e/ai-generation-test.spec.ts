import { test, expect } from '@playwright/test';

test.describe('AI Code Generation Flow', () => {
  test('should successfully generate code from requirement without network or API errors', async ({ page }) => {
    // 1. Go to homepage
    await page.goto('http://localhost:3000');

    // 2. Enter requirement
    const requirementInput = page.getByPlaceholder(/在这里输入你想做什么/i);
    await expect(requirementInput).toBeVisible();
    await requirementInput.fill('做一个简单的待办事项列表应用，支持添加、删除和标记完成');

    // 3. Click Generate button
    const generateButton = page.getByRole('button', { name: /生成/i });
    await generateButton.click();

    // 4. Wait for analysis to start and monitor for error toasts
    // Check for "Analysis failed" or "API调用失败" or "Invalid API Key" in the UI
    const errorToast = page.locator('.toast-destructive, [role="alert"]').filter({ hasText: /失败|Invalid|Error/i });
    await expect(errorToast).not.toBeVisible({ timeout: 10000 });

    // 5. Wait for Intent Analysis to complete (step 1)
    // Look for text indicating the next step or success of analysis
    await expect(page.getByText(/意图识别|设计风格/i)).toBeVisible({ timeout: 60000 });

    console.log('Intent analysis completed successfully.');

    // 6. Select a style (if prompted) - assuming it goes to style selection or template selection
    // If it goes to template selection first
    const templateSelection = page.getByText(/选择模板|行业模板/i);
    if (await templateSelection.isVisible()) {
      const skipButton = page.getByText(/跳过/i);
      if (await skipButton.isVisible()) {
        await skipButton.click();
      } else {
        // select first template
        await page.locator('.template-card').first().click();
      }
    }

    // Wait for style selection
    await expect(page.getByText(/选择设计风格/i)).toBeVisible({ timeout: 10000 });

    // Click a style (e.g., the first one)
    const firstStyle = page.locator('[data-testid="style-card"]').first(); // Assuming a testid or class
    // Fallback if testid not present, try finding by text or generic card
    if (await firstStyle.count() > 0) {
      await firstStyle.click();
    } else {
      await page.getByText(/极简/i).first().click();
    }

    console.log('Style selected.');

    // 7. Wait for Prototype Generation to start
    await expect(page.getByText(/正在生成原型|原型预览/i)).toBeVisible({ timeout: 10000 });

    // 8. Monitor for generation errors again
    await expect(errorToast).not.toBeVisible({ timeout: 10000 });

    // 9. Wait for Sandbox to load (success criterion)
    // This implies the backend successfully called the AI and generated code
    await expect(page.frameLocator('iframe[title="原型预览"]').locator('body')).toBeVisible({ timeout: 120000 });

    console.log('Prototype generated and sandbox loaded.');
  });
});
