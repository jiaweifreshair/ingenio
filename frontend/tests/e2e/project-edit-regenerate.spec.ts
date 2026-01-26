import { test, expect } from '@playwright/test';

/**
 * End-to-End Test for Project Edit & Regenerate Flow
 * 
 * PREREQUISITES:
 * 1. Backend Service running on http://localhost:8080
 * 2. Frontend running on http://localhost:3000
 * 3. Database (PostgreSQL) and Redis running
 * 4. User logged in (or valid token present)
 */

const BASE_URL = 'http://localhost:3000';

test.describe('Project Edit and Regenerate Flow', () => {
  
  // Setup: Ensure we are logged in or bypass auth
  test.beforeEach(async ({ page }) => {
    // TODO: Implement proper login or mock auth
    // For now, we assume we might need to visit login or set checking token
    await page.goto(`${BASE_URL}/login`);
    // Example: Fill login if credentials known
    // await page.fill('input[name="email"]', 'test@example.com');
    // await page.fill('input[name="password"]', 'password');
    // await page.click('button[type="submit"]');
    
    // For development, you might be able to set a token directly if using localStorage
    // await page.addInitScript(() => {
    //   localStorage.setItem('satoken', 'mock-token');
    // });
  });

  test('should create a project, edit requirement, and regenerate', async ({ page }) => {
    // 1. Create a new project via Wizard
    await page.goto(`${BASE_URL}/wizard`);
    
    // Fill Requirement
    await page.fill('textarea[placeholder*="Describe"]', 'Create a simple Todo App');
    await page.click('button:has-text("Start Generation")'); // Adjust selector as needed

    // Wait for generation to complete (this might take time)
    // In test environment, maybe use a mock or wait for specific element
    await expect(page.locator('text=Generation Completed')).toBeVisible({ timeout: 60000 });
    
    // Get Project ID/AppSpec ID from URL
    const url = page.url();
    const appSpecId = url.split('/').pop();
    console.log('Generated AppSpec ID:', appSpecId);

    // 2. Go to Dashboard and Find the Project
    await page.goto(`${BASE_URL}/dashboard`);
    await expect(page.locator(`text=${appSpecId}`).or(page.locator('text=Todo App'))).toBeVisible();

    // 3. Click Edit (should go to Wizard with context)
    // Find the edit button for the project card
    await page.click(`.project-card:has-text("Todo App") button[aria-label="Edit"]`); 
    
    // Verify we are back in Wizard
    await expect(page).toHaveURL(new RegExp(`/wizard/.*`));
    await expect(page.locator('textarea')).toHaveValue(/Todo App/);

    // 4. Modify Requirement
    await page.fill('textarea', 'Create a simple Todo App with Dark Mode');
    
    // 5. Click Regenerate
    // Note: The button might still say "Start Generation" or "Regenerate" depending on UI state
    await page.click('button:has-text("Start Generation")');

    // 6. Verify New Generation Task started
    await expect(page.locator('text=Generating')).toBeVisible();
    
    // Wait for completion
    await expect(page.locator('text=Generation Completed')).toBeVisible({ timeout: 60000 });

    // 7. Verify Version History
    // Navigate to Version History
    await page.goto(`${BASE_URL}/versions/${appSpecId}`); // Note: this URL pattern might need adjustment if AppSpecId != ProjectId
    
    // Check if multiple versions exist
    await expect(page.locator('.version-timeline-item')).toHaveCount(2);
    
    // 8. Test Rollback
    // Click on the first (older) version
    await page.click('.version-timeline-item >> nth=1');
    await page.click('button:has-text("Rollback")');
    
    // Confirm dialog
    page.on('dialog', dialog => dialog.accept());
    
    // Verify Rollback version created
    await expect(page.locator('.version-timeline-item')).toHaveCount(3);
    await expect(page.locator('text=ROLLBACK')).toBeVisible();
  });

});
