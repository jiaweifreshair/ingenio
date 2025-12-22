# React 19 Strict Mode Test Patterns

> **Author**: Ingenio Testing Team
> **Last Updated**: 2025-11-14
> **Purpose**: Document patterns for handling React 19 Strict Mode in unit and E2E tests

---

## Table of Contents

1. [Background](#background)
2. [The Problem](#the-problem)
3. [Unit Test Solutions](#unit-test-solutions)
4. [E2E Test Solutions](#e2e-test-solutions)
5. [Best Practices](#best-practices)

---

## Background

### What is React 19 Strict Mode?

React 19's Strict Mode intentionally **double-renders** components during development to help detect side effects and ensure components are resilient to being mounted/unmounted multiple times.

**Key Behaviors**:
- Components render twice (mount → unmount → mount)
- Effects run twice (setup → cleanup → setup)
- This only happens in development mode
- Production builds are unaffected

### Why This Affects Tests

When Strict Mode is enabled in test environments (via `<React.StrictMode>` wrapper), queries that expect a single element may find duplicates:

```typescript
// ❌ Fails in Strict Mode
const button = screen.getByRole('button', { name: '已选' });
// Error: Found multiple elements with role "button" and name /已选/
```

---

## The Problem

### Example Failure: AI Capability Picker

**Test Setup**:
```typescript
render(
  <AICapabilityPicker
    selectedCapabilities={[AICapabilityType.CHATBOT]}
    onSelectionChange={mockOnSelectionChange}
  />
);
```

**Failing Query**:
```typescript
// ❌ Fails: Multiple "已选" buttons due to Strict Mode double-rendering
const deselectButton = screen.getByRole('button', { name: /已选/ });
```

**Error Message**:
```
Found multiple elements with the role "button" and name `/已选/`

Here are the matching elements:
<button ...>已选</button>  // First render
<button ...>已选</button>  // Second render (Strict Mode)
```

---

## Unit Test Solutions

### Solution 1: Use `getAllByRole` + First Element (Recommended for Simple Cases)

**When to Use**: When any matching element is functionally equivalent

```typescript
// ✅ Fixed: Use getAllByRole and select first element
const deselectButtons = screen.getAllByRole('button', { name: /已选/ });
await user.click(deselectButtons[0]);

// Rationale: In Strict Mode, multiple "已选" buttons may exist,
// but they all trigger the same deselection action
```

**Pros**:
- Simple and concise
- No component changes needed
- Works well when elements are functionally identical

**Cons**:
- Less semantic than scoped queries
- May not work if elements have different behaviors

---

### Solution 2: Use `within()` for Scoped Queries (Recommended for Complex Cases)

**When to Use**: When you need to target a specific element within a container

```typescript
import { within } from '@testing-library/react';

// ✅ Fixed: Target specific capability card first, then its button
const chatbotCard = screen.getByRole('button', {
  name: /对话机器人.*已选中/  // aria-label includes "已选中" when selected
});

const deselectButton = within(chatbotCard).getByRole('button', {
  name: /已选/
});

await user.click(deselectButton);
```

**Pros**:
- More semantic and specific
- Tests what users actually see
- Better for complex component hierarchies

**Cons**:
- Slightly more verbose
- Requires understanding of component structure

---

### Solution 3: Use `data-testid` (Last Resort)

**When to Use**: When semantic queries are not possible or practical

```typescript
// In component:
<button data-testid={`deselect-${capability.id}`}>已选</button>

// In test:
const deselectButton = screen.getByTestId('deselect-chatbot');
await user.click(deselectButton);
```

**Pros**:
- Guaranteed unique selector
- No ambiguity

**Cons**:
- Requires component code changes
- Less semantic (doesn't test accessibility)
- Should be avoided if possible

---

## E2E Test Solutions

E2E tests with Playwright also encounter strict mode issues when the application uses React 19 Strict Mode.

### Pattern 1: Multiple Identical Buttons

**Problem**:
```typescript
// ❌ Fails: Multiple "开始创建" buttons
await page.getByText('开始创建').click();
```

**Solutions**:

**Option A: Use `.first()` or `.last()`**
```typescript
// ✅ Click first matching button
await page.getByRole('button', { name: '开始创建', exact: true }).first().click();
```

**Option B: Use more specific selector**
```typescript
// ✅ Use data-testid
await page.locator('[data-testid="wizard-start-button"]').click();

// ✅ Use CSS selector with context
await page.locator('.welcome-section button:has-text("开始创建")').click();
```

---

### Pattern 2: Multiple Step Indicators

**Problem**:
```typescript
// ❌ Fails: Multiple step 1 indicators
await page.getByText('步骤 1').click();
```

**Solutions**:

**Option A: Use active state selector**
```typescript
// ✅ Target only the active step
await page.locator('.step-indicator.active').click();
```

**Option B: Use ARIA current attribute**
```typescript
// ✅ Use aria-current attribute
await page.getByRole('button', { name: '步骤 1', current: true }).click();
```

---

### Pattern 3: Multiple Form Elements

**Problem**:
```typescript
// ❌ Fails: Multiple input fields with same placeholder
await page.getByPlaceholder('输入关键词').fill('test');
```

**Solutions**:

**Option A: Use label association**
```typescript
// ✅ Target by label
await page.getByLabel('搜索关键词').fill('test');
```

**Option B: Use container context**
```typescript
// ✅ Scope within container
const searchSection = page.locator('.search-section');
await searchSection.getByPlaceholder('输入关键词').fill('test');
```

---

## Best Practices

### 1. Prioritize Semantic Queries

Always prefer queries that match how users interact with the UI:

**Preference Order**:
1. **Role + Accessible Name**: `getByRole('button', { name: '已选' })`
2. **Label**: `getByLabelText('用户名')`
3. **Placeholder**: `getByPlaceholderText('搜索...')`
4. **Text**: `getByText('提交')`
5. **Test ID**: `getByTestId('submit-btn')` (last resort)

---

### 2. Use `getAllByRole` for Lists

When querying multiple elements is expected:

```typescript
// ✅ Good: Explicitly handle multiple elements
const selectButtons = screen.getAllByRole('button', { name: /\+ 选择/ });
expect(selectButtons).toHaveLength(19); // Verify count
await user.click(selectButtons[0]); // Click specific one
```

---

### 3. Add Descriptive Comments

Always explain why you're using `getAllByRole` or `.first()`:

```typescript
// ✅ Good: Explains the Strict Mode workaround
// 修复：使用getAllByRole处理React 19 Strict Mode双重渲染导致的多个"已选"按钮
// 在Strict Mode下，组件渲染两次，可能会出现多个"已选"按钮
// 我们选择第一个即可（任何一个都指向同一个能力的取消选择操作）
const deselectButtons = screen.getAllByRole('button', { name: /已选/ });
await user.click(deselectButtons[0]);
```

---

### 4. Test Accessibility Labels

Ensure components have proper ARIA labels to enable semantic queries:

```typescript
// ✅ Component has comprehensive aria-label
<Card
  role="button"
  aria-pressed={isSelected}
  aria-label={`${capability.name}，${isSelected ? '已选中' : '未选中'}`}
>
  <button>{isSelected ? '已选' : '+ 选择'}</button>
</Card>
```

This enables more specific queries:

```typescript
// ✅ Can target specific capability by name and state
const chatbotCard = screen.getByRole('button', {
  name: /对话机器人.*已选中/
});
```

---

### 5. Verify Stability

Always run tests multiple times to ensure fixes are stable:

```bash
# Run 3 times to check for flakiness
for i in {1..3}; do
  echo "=== Run $i ==="
  pnpm test src/components/ai/__tests__/ai-capability-picker.test.tsx --run
done
```

---

## Common Pitfalls

### Pitfall 1: Assuming Single Element

**❌ Don't**:
```typescript
const button = screen.getByRole('button', { name: '已选' });
// Fails if Strict Mode renders multiple
```

**✅ Do**:
```typescript
const buttons = screen.getAllByRole('button', { name: '已选' });
const button = buttons[0]; // Or use specific selection logic
```

---

### Pitfall 2: Using Generic Selectors

**❌ Don't**:
```typescript
await page.locator('button').click(); // Too generic
```

**✅ Do**:
```typescript
await page.getByRole('button', { name: '提交', exact: true }).first().click();
```

---

### Pitfall 3: Ignoring Accessibility

**❌ Don't**:
```typescript
const button = screen.getByTestId('mystery-button');
```

**✅ Do**:
```typescript
const button = screen.getByRole('button', { name: '提交订单' });
// Also tests that the button has proper accessible name
```

---

## Migration Checklist

When fixing strict mode test failures:

- [ ] Identify all `getByRole` queries that may find multiple elements
- [ ] Determine if elements are functionally equivalent
- [ ] Choose appropriate solution (getAllByRole vs within vs testid)
- [ ] Add explanatory comments
- [ ] Run tests 3+ times to verify stability
- [ ] Check that no regressions were introduced
- [ ] Update documentation if new patterns emerge

---

## References

- [React 19 Strict Mode Documentation](https://react.dev/reference/react/StrictMode)
- [Testing Library Best Practices](https://testing-library.com/docs/queries/about)
- [Playwright Selectors Guide](https://playwright.dev/docs/selectors)

---

**Status**: ✅ Applied to `ai-capability-picker.test.tsx` (Phase 4.1B)
**Next**: Apply to E2E tests (Phase 4.2)
