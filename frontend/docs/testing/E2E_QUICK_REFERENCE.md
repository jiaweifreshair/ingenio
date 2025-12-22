# E2E Testing Quick Reference

> **Last Updated**: 2025-11-14
> **For**: Ingenio Frontend Developers
> **Test Framework**: Playwright

---

## Quick Commands

```bash
# Run all E2E tests (chromium only, fastest)
pnpm e2e:chromium

# Run specific test file
pnpm exec playwright test src/e2e/notifications.spec.ts

# Run tests in headed mode (see browser)
pnpm exec playwright test --headed

# Run tests in debug mode (step through)
pnpm exec playwright test --debug

# Run specific test by name
pnpm exec playwright test -g "should display notification panel"

# View HTML test report
pnpm exec playwright show-report

# Run tests with trace (for debugging failures)
pnpm exec playwright test --trace on
```

---

## Test Writing Best Practices

### 1. Use Semantic Selectors ✅

```typescript
// ✅ Good - Uses semantic role
await page.getByRole('button', { name: 'Submit', exact: true })

// ✅ Good - Uses test ID
await page.getByTestId('submit-button')

// ❌ Bad - Fragile CSS selector
await page.locator('.btn.btn-primary')

// ❌ Bad - Ambiguous text selector
await page.locator('text=Submit')
```

### 2. Handle 404s Gracefully ✅

```typescript
// ✅ Good - Waits for UI element, not network
await page.goto('/account');
await expect(page.getByRole('heading', { name: '个人中心' })).toBeVisible();
// Passes whether API succeeds or returns 404

// ❌ Bad - Hangs when API returns 404
await page.goto('/account');
await page.waitForLoadState('networkidle'); // Hangs!
```

### 3. Avoid Hard-coded Waits ✅

```typescript
// ✅ Good - Wait for specific condition
await expect(page.getByText('Success')).toBeVisible();

// ✅ Good - Wait for animation (if duration known)
await page.waitForTimeout(300); // Only for known CSS transitions

// ❌ Bad - Arbitrary wait
await page.waitForTimeout(5000);
```

### 4. Handle Animations Properly ✅

```typescript
// ✅ Good - Wait for animation, then check
await advancedSettings.click();
await page.waitForTimeout(500); // CSS transition: 0.3s
await expect(advancedSettingsContent).toBeVisible();

// ❌ Bad - Check immediately (fails mid-animation)
await advancedSettings.click();
await expect(advancedSettingsContent).toBeVisible();
```

### 5. Use Descriptive Test Names ✅

```typescript
// ✅ Good - Clear what is being tested
test('should display notification panel when clicking bell icon', async ({ page }) => {
  // ...
});

// ❌ Bad - Vague
test('test notification', async ({ page }) => {
  // ...
});
```

---

## Common Patterns

### Pattern 1: Navigate and Wait for Page Load

```typescript
test('should navigate to dashboard', async ({ page }) => {
  await page.goto('http://localhost:3000/dashboard');

  // Wait for key element (not networkidle)
  await expect(page.getByRole('heading', { name: '应用管理' })).toBeVisible();

  // Now safe to interact
  await page.getByRole('button', { name: '创建应用' }).click();
});
```

### Pattern 2: Click and Wait for Navigation

```typescript
test('should navigate when clicking CTA', async ({ page }) => {
  await page.goto('http://localhost:3000');

  // Click and wait for navigation to complete
  await Promise.all([
    page.waitForNavigation(),
    page.getByRole('button', { name: '免费开始' }).click()
  ]);

  // Verify we're on the new page
  await expect(page).toHaveURL(/.*\/create/);
});
```

### Pattern 3: Handle API Responses

```typescript
test('should load data from API', async ({ page }) => {
  // Set up response listener BEFORE navigation
  const responsePromise = page.waitForResponse(
    res => res.url().includes('/api/v1/apps') && res.status() === 200
  );

  await page.goto('http://localhost:3000/dashboard');

  // Wait for API call
  const response = await responsePromise;
  const data = await response.json();

  // Verify UI reflects data
  expect(data.apps.length).toBeGreaterThan(0);
  await expect(page.locator('.app-card')).toHaveCount(data.apps.length);
});
```

### Pattern 4: Test Responsive Design

```typescript
test('should be mobile responsive', async ({ page }) => {
  // Set mobile viewport
  await page.setViewportSize({ width: 375, height: 667 });

  await page.goto('http://localhost:3000');

  // Verify mobile menu
  await expect(page.getByRole('button', { name: 'Menu' })).toBeVisible();

  // Verify desktop nav is hidden
  await expect(page.locator('nav.desktop-nav')).not.toBeVisible();
});
```

### Pattern 5: Handle Form Submission

```typescript
test('should submit form successfully', async ({ page }) => {
  await page.goto('http://localhost:3000/create');

  // Fill form
  await page.getByLabel('应用名称').fill('测试应用');
  await page.getByLabel('应用描述').fill('这是一个测试');

  // Submit and wait for response
  const responsePromise = page.waitForResponse('/api/v1/apps');
  await page.getByRole('button', { name: '提交' }).click();
  await responsePromise;

  // Verify success message
  await expect(page.getByText('创建成功')).toBeVisible();
});
```

---

## Debugging Tips

### 1. Use Screenshots on Failure

```typescript
test('my test', async ({ page }) => {
  await page.goto('http://localhost:3000');

  try {
    await expect(page.getByText('Expected text')).toBeVisible();
  } catch (error) {
    // Take screenshot before throwing
    await page.screenshot({ path: 'test-failed.png', fullPage: true });
    throw error;
  }
});
```

### 2. Use Console Logs

```typescript
test('debug test', async ({ page }) => {
  // Listen for console messages
  page.on('console', msg => console.log('PAGE LOG:', msg.text()));

  await page.goto('http://localhost:3000');

  // Your test logic...
});
```

### 3. Use Playwright Inspector

```bash
# Run test in debug mode
pnpm exec playwright test --debug src/e2e/notifications.spec.ts

# Then use these commands in inspector:
# - Click "Pick locator" to find selectors
# - Step through test with controls
# - Inspect page state at any point
```

### 4. Check Network Requests

```typescript
test('debug network', async ({ page }) => {
  // Log all requests
  page.on('request', request =>
    console.log('>>', request.method(), request.url())
  );

  // Log all responses
  page.on('response', response =>
    console.log('<<', response.status(), response.url())
  );

  await page.goto('http://localhost:3000');
});
```

---

## Common Errors and Solutions

### Error 1: "element not found"

**Cause**: Element not yet rendered or wrong selector

**Solutions**:
```typescript
// 1. Add explicit wait
await expect(page.getByText('My Text')).toBeVisible({ timeout: 10000 });

// 2. Check if element exists first
const element = page.getByText('My Text');
if (await element.isVisible()) {
  await element.click();
}

// 3. Use more specific selector
await page.getByRole('button', { name: 'My Text', exact: true });
```

### Error 2: "strict mode violation: resolved to X elements"

**Cause**: Selector matches multiple elements

**Solutions**:
```typescript
// 1. Use exact match
await page.getByRole('heading', { name: 'Title', exact: true });

// 2. Use test ID
await page.getByTestId('unique-element');

// 3. Narrow scope
await page.locator('#container').getByText('Title');

// 4. Get first match explicitly
await page.locator('text=Title').first();
```

### Error 3: "Timeout waiting for network idle"

**Cause**: API returns 404 or request never completes

**Solutions**:
```typescript
// ❌ Don't use waitForLoadState('networkidle')
await page.waitForLoadState('networkidle');

// ✅ Wait for specific element instead
await expect(page.getByRole('main')).toBeVisible();
```

### Error 4: "Test is flaky (passes sometimes, fails sometimes)"

**Causes and Solutions**:
```typescript
// 1. Race condition - wait for animations
await button.click();
await page.waitForTimeout(300); // CSS transition duration
await expect(content).toBeVisible();

// 2. Network timing - add retry
await expect(async () => {
  const response = await page.request.get('/api/data');
  expect(response.status()).toBe(200);
}).toPass({ timeout: 5000 });

// 3. Element not interactive - wait for it
await element.waitFor({ state: 'visible' });
await element.click();
```

---

## Test Organization

### Directory Structure

```
src/e2e/
├── account.spec.ts           # Account settings tests
├── ai-capability-picker.spec.ts  # AI capability picker tests
├── agent-visualization.spec.ts   # Agent visualization tests
├── components.spec.ts        # Reusable component tests
├── dashboard.spec.ts         # Dashboard tests
├── homepage.spec.ts          # Homepage tests
├── navigation.spec.ts        # Navigation flow tests
├── notifications.spec.ts     # Notification center tests
├── templates.spec.ts         # Template gallery tests
├── wizard.spec.ts            # Wizard flow tests
└── utils/
    └── test-helpers.ts       # Shared test utilities
```

### Test File Template

```typescript
import { test, expect } from '@playwright/test';

test.describe('Feature Name', () => {
  // Run before each test
  test.beforeEach(async ({ page }) => {
    await page.goto('http://localhost:3000/your-page');
  });

  test('should do something', async ({ page }) => {
    // Arrange
    const button = page.getByRole('button', { name: 'Click Me' });

    // Act
    await button.click();

    // Assert
    await expect(page.getByText('Success')).toBeVisible();
  });

  test('should handle error case', async ({ page }) => {
    // Test error handling...
  });
});
```

---

## Performance Tips

### 1. Reuse Browser Context

```typescript
// ✅ Good - Share context between tests
test.describe('My tests', () => {
  let context;
  let page;

  test.beforeAll(async ({ browser }) => {
    context = await browser.newContext();
    page = await context.newPage();
  });

  test.afterAll(async () => {
    await context.close();
  });

  // Your tests use same page...
});
```

### 2. Run Tests in Parallel

```typescript
// playwright.config.ts
export default {
  workers: 5, // Run 5 tests in parallel
  fullyParallel: true,
};
```

### 3. Skip Slow Tests Locally

```typescript
// Use test.slow() for tests that need more time
test.slow();
test('slow integration test', async ({ page }) => {
  // This test gets 3x the normal timeout
});

// Or skip in development
test.skip(process.env.NODE_ENV === 'development', 'Slow test');
test('expensive test', async ({ page }) => {
  // ...
});
```

---

## Assertion Cheat Sheet

```typescript
// Element visibility
await expect(element).toBeVisible();
await expect(element).toBeHidden();

// Element state
await expect(element).toBeEnabled();
await expect(element).toBeDisabled();
await expect(element).toBeChecked();

// Text content
await expect(element).toHaveText('Expected text');
await expect(element).toContainText('Partial text');

// Attributes
await expect(element).toHaveAttribute('href', '/link');
await expect(element).toHaveClass('active');

// Count
await expect(page.locator('.item')).toHaveCount(5);

// URL
await expect(page).toHaveURL(/.*\/dashboard/);
await expect(page).toHaveURL('http://localhost:3000/dashboard');

// Screenshot comparison
await expect(page).toHaveScreenshot('homepage.png');
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: E2E Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup Node
        uses: actions/setup-node@v3
        with:
          node-version: '18'

      - name: Install dependencies
        run: pnpm install

      - name: Start backend
        run: docker-compose up -d

      - name: Wait for backend
        run: npx wait-on http://localhost:8080/health

      - name: Run E2E tests
        run: pnpm e2e:chromium

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: playwright-report
          path: playwright-report/
```

---

## Resources

- **Playwright Docs**: https://playwright.dev
- **Test Status Dashboard**: [E2E_TEST_STATUS.md](./E2E_TEST_STATUS.md)
- **Phase 3.4 Report**: [PHASE_3.4_E2E_REPORT.md](./PHASE_3.4_E2E_REPORT.md)
- **E2E Testing Guide**: [E2E_TESTING_GUIDE.md](./E2E_TESTING_GUIDE.md)

---

## Getting Help

1. **Check existing tests** for similar patterns
2. **Read Playwright docs** for specific features
3. **Use Playwright Inspector** for debugging
4. **Check test status dashboard** for known issues
5. **Ask team** in #testing Slack channel

---

**Last Updated**: 2025-11-14
**Maintained By**: Test Automation Team
**Questions?** Post in #frontend-testing channel
