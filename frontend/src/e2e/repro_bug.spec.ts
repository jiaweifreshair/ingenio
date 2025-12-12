
import { test, expect, type Page } from '@playwright/test';

// Configuration
const BASE_URL = 'http://localhost:3000';
const AI_API_TIMEOUT = 120000; // Increase timeout as AI might be slow

// User credentials (from v2-ui-integration.spec.ts)
const TEST_USER = {
  username: 'testuser009',
  email: 'test009@example.com',
  password: 'Test1234',
};

// Auth helper
async function setAuthToken(page: Page) {
  await page.goto(`${BASE_URL}/`);
  
  // Try to login
  try {
    const loginResponse = await page.evaluate(async (credentials) => {
      const response = await fetch('http://localhost:8080/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: credentials.username,
          password: credentials.password,
        }),
      });
      return response.json();
    }, TEST_USER);

    if (loginResponse.success && loginResponse.data?.token) {
       const token = loginResponse.data.token;
       await page.context().addCookies([{
         name: 'auth_token',
         value: token,
         domain: 'localhost',
         path: '/',
       }]);
       await page.evaluate((t) => {
         localStorage.setItem('auth_token', t);
       }, token);
       console.log('Logged in successfully');
       return token;
    }
  } catch (e) {
    console.error('Login failed', e);
  }
  // If login fails, we might proceed as guest or fail. 
  // For now, let's assume login works or isn't strictly required for the bug if it's UI state.
}

test('Reproduction: Prototype Preview jumps back to Technology Selection', async ({ page }) => {
  // 1. Setup
  await setAuthToken(page);
  await page.goto(`${BASE_URL}/`); // Go to home which has the HeroBanner
  
  // 2. Input Requirement
  // The HeroBanner has a textarea.
  const textarea = page.locator('textarea');
  await expect(textarea).toBeVisible();
  await textarea.fill('创建一个简单的个人博客，包含文章列表和详情页');
  
  // 3. Submit
  const submitBtn = page.locator('button:has-text("生成")');
  await submitBtn.click();
  
  // 4. Wait for Analysis (Intent Result)
  // This step takes time.
  console.log('Waiting for intent analysis...');
  // The IntentResultPanel should appear. It has "意图识别结果" text maybe?
  // Looking at HeroBanner.tsx: <IntentResultPanel ... />
  // We can look for text "意图识别完成" or the panel itself.
  await expect(page.locator('text=意图识别完成')).toBeVisible({ timeout: AI_API_TIMEOUT });
  
  // 5. Confirm Intent
  const confirmIntentBtn = page.locator('button:has-text("确认并继续")'); // Guessing the text, or use selector
  // Let's look for a button that likely means "Next" or "Confirm"
  // In IntentResultPanel, usually there is a primary button.
  if (await confirmIntentBtn.isVisible()) {
      await confirmIntentBtn.click();
  } else {
      // Maybe "Generate" or similar?
      // Let's use a broad selector
      await page.locator('button[variant="default"]').click(); 
      // This is risky. Let's try to find the specific button text from the code later if this fails.
      // But for now, let's assume standard UI.
      // Actually, let's check intent-result-panel.tsx if possible, but I'll trust the flow.
      // Better yet, I'll search for "确认"
      await page.click('button:has-text("确认")');
  }

  // 6. Template Selection (Might be skipped or shown)
  console.log('Waiting for template selection or style selection...');
  // It might go to Template Selection or Style Selection.
  // If "Template Selection" appears, skip it or select one.
  const templateSection = page.locator('text=选择行业模板');
  if (await templateSection.isVisible({ timeout: 5000 })) {
      console.log('Template selection visible, selecting first one or skipping');
      // Look for a template card or skip button
      const skipBtn = page.locator('button:has-text("跳过")');
      if (await skipBtn.isVisible()) {
          await skipBtn.click();
      } else {
          // Select first template
          await page.locator('.template-card').first().click(); // Hypothetical class
      }
  }

  // 7. Style Selection
  console.log('Waiting for style selection...');
  await expect(page.locator('text=选择设计风格')).toBeVisible({ timeout: 30000 });
  
  // Select "Apple" style (or any)
  const appleStyle = page.locator('text=Apple Design'); // Assuming text
  if (await appleStyle.isVisible()) {
      await appleStyle.click();
  } else {
      // Click the first available style
      await page.click('div[role="button"]'); // Risky
  }
  
  // Confirm Style (if needed, or maybe clicking style is enough? 
  // In HeroBanner: handleSelectStyle is called. It usually sets state.
  // StyleSelectionPanel usually has cards. Clicking one might auto-advance or require confirm.
  // Looking at code: handleSelectStyle sets PROTOTYPE_PREVIEW.
  // So probably clicking the style card triggers it.

  // 8. Prototype Preview
  console.log('Waiting for Prototype Preview...');
  await expect(page.locator('text=原型预览')).toBeVisible({ timeout: 30000 });
  
  // 9. Observe for "Jump Back"
  // The user says it jumps back to "Technology Selection".
  // "Technology Selection" is Step 2 in the Timeline.
  // We can check if "原型预览" disappears and "技术选型" becomes active or the view changes.
  
  // Let's wait a few seconds to see if it reverts automatically?
  // Or maybe upon interaction?
  // "Prototype Preview jumps back to Technology Selection & Architecture Design"
  // This sounds like an automatic revert or a crash that resets state.
  
  await page.waitForTimeout(5000);
  
  // Check if we are still on Prototype Preview
  const isPrototypeVisible = await page.locator('text=原型预览').isVisible();
  console.log(`Is Prototype Preview still visible? ${isPrototypeVisible}`);
  
  if (!isPrototypeVisible) {
      // Check if we are back at Tech Selection (Intent Result?)
      const isTechSelectionVisible = await page.locator('text=技术选型').isVisible();
       console.log(`Is Tech Selection visible? ${isTechSelectionVisible}`);
      if (isTechSelectionVisible) {
          throw new Error('Bug Reproduced: Jumped back to Technology Selection!');
      }
  } else {
      console.log('Prototype Preview persisted. Maybe try clicking "Confirm"?');
      // Try clicking confirm
      await page.click('button:has-text("确认设计")');
      
      // Should go to Backend Generation
      await expect(page.locator('text=后端代码生成中')).toBeVisible();
  }
});
