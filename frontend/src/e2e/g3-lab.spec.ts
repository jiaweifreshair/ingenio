import { test, expect } from '@playwright/test';

test.describe('G3 Engine Lab', () => {
  test('should successfully run the G3 engine simulation', async ({ page }) => {
    // 1. Navigate to G3 Lab
    await page.goto('/lab/g3');
    
    // 2. Check title
    await expect(page.getByRole('heading', { name: 'G3 Engine Lab' })).toBeVisible();
    
    // 3. Enter requirement (if not already filled)
    const input = page.getByPlaceholder('Enter mission objective...');
    await input.fill('Create a secure login system');
    
    // 4. Start Engine
    const startBtn = page.getByRole('button', { name: 'START ENGINE' });
    await startBtn.click();
    
    // 5. Verify Engine Status changes
    await expect(page.locator('text=System Status: ONLINE')).toBeVisible({ timeout: 10000 });
    
    // 6. Verify Logs appearing
    const logStream = page.locator('.flex-1.break-all').first();
    await expect(logStream).toBeVisible({ timeout: 10000 });
    
    // 7. Wait for completion (simulation takes time)
    // We expect "Defense Successful" or "Code Ready" eventually
    await expect(page.locator('text=Defense Successful')).toBeVisible({ timeout: 30000 });
    
    // 8. Verify Reset state
    await expect(startBtn).toContainText('START ENGINE');
  });
});
