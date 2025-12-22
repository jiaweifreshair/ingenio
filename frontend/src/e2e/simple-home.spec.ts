import { test, expect } from '@playwright/test';

test('首页应该能正常加载', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/秒构AI/);
});
