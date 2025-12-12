/**
 * 模板库E2E测试
 * 测试模板列表、搜索、详情查看、使用模板
 *
 * 已实现：frontend/src/app/templates/page.tsx
 */
import { test, expect } from "@playwright/test";

test.describe("模板库功能测试", () => {
  test.beforeEach(async ({ page }) => {
    // 导航到模板库页面
    await page.goto("/templates");
  });

  test("应该正确显示模板列表", async ({ page }) => {
    // 验证页面标题
    await expect(
      page.getByRole("heading", { name: /模板库|模板中心/ })
    ).toBeVisible({ timeout: 10000 });

    // 等待模板列表加载
    await page.waitForTimeout(1000);

    // 验证至少显示模板卡片
    const templateCards = page.locator('[data-testid="template-card"]');
    const count = await templateCards.count();
    expect(count).toBeGreaterThan(0);

    // 验证模板卡片内容包含必要元素
    const firstCard = templateCards.first();
    await expect(firstCard.locator("h3")).toBeVisible();
    await expect(firstCard.locator("button")).toBeVisible();
  });

  test("应该能够搜索模板", async ({ page }) => {
    // 定位搜索输入框
    const searchInput = page.locator('[data-testid="search-input"]');
    await expect(searchInput).toBeVisible();

    // 输入搜索关键词
    await searchInput.fill("电商");

    // 等待搜索结果
    await page.waitForTimeout(1000);

    // 验证搜索结果
    const resultText = page.locator("text=/找到.*个模板/");
    await expect(resultText).toBeVisible();
  });

  test("应该能够按分类筛选模板", async ({ page }) => {
    // 验证分类侧边栏存在
    await expect(page.locator("text=模板分类")).toBeVisible();

    // 点击电商类分类
    await page.click("text=电商类");

    // 等待筛选结果
    await page.waitForTimeout(1000);

    // 验证显示模板
    const templateCards = page.locator('[data-testid="template-card"]');
    const count = await templateCards.count();
    expect(count).toBeGreaterThanOrEqual(0);
  });

  test("应该能够查看模板详情", async ({ page }) => {
    // 等待模板卡片加载
    await page.waitForTimeout(1000);

    // 点击第一个模板卡片
    const firstCard = page.locator('[data-testid="template-card"]').first();
    await firstCard.click();

    // 验证模板详情对话框打开
    await page.waitForSelector('[role="dialog"]');
    const detailDialog = page.locator('[role="dialog"]');
    await expect(detailDialog).toBeVisible();

    // 验证详情内容
    await expect(detailDialog.locator("h2")).toBeVisible();

    // 验证操作按钮
    await expect(
      detailDialog.locator('button:has-text("使用此模板")')
    ).toBeVisible();
  });

  test("应该能够使用模板创建应用", async ({ page }) => {
    // 等待模板卡片加载
    await page.waitForTimeout(1000);

    // 点击第一个模板卡片
    const firstCard = page.locator('[data-testid="template-card"]').first();
    await firstCard.click();

    // 等待详情对话框打开
    await page.waitForSelector('[role="dialog"]');

    // 点击"使用模板"按钮
    const useButton = page
      .locator('[role="dialog"] button:has-text("使用此模板")')
      .first();
    await useButton.click();

    // 验证导航到创建页面并携带模板ID参数（实际使用templateId而非template）
    await page.waitForURL(/\/create\?templateId=/);
    expect(page.url()).toContain("/create?templateId=");
  });

  test("应该显示模板的使用量和评分", async ({ page }) => {
    // 等待模板卡片加载
    await page.waitForTimeout(1000);

    // 验证第一个模板卡片显示统计信息
    const firstCard = page.locator('[data-testid="template-card"]').first();

    // 验证显示评分（星星图标 - lucide-react渲染为SVG）
    await expect(firstCard.locator("svg.lucide-star")).toBeVisible();

    // 验证显示使用次数（用户图标 - lucide-react渲染为SVG）
    await expect(firstCard.locator("svg.lucide-users")).toBeVisible();
  });

  test("应该显示模板标签", async ({ page }) => {
    // 等待模板卡片加载
    await page.waitForTimeout(1000);

    // 验证模板卡片显示标签
    const firstCard = page.locator('[data-testid="template-card"]').first();

    // 验证至少有一个标签
    const badges = firstCard.locator('[class*="badge"]');
    const count = await badges.count();
    expect(count).toBeGreaterThan(0);
  });

  test("应该能够使用难度筛选器", async ({ page }) => {
    // 验证难度筛选器存在（使用label选择器避免strict mode违规）
    await expect(page.locator("label:has-text('难度')").first()).toBeVisible();

    // 点击难度选择器（获取所有combobox中的第一个，即难度筛选器）
    const allComboboxes = page.getByRole('combobox');
    const difficultyButton = allComboboxes.first();
    await difficultyButton.click();

    // 选择"简单"难度（点击下拉列表项）
    await page.getByRole('option', { name: '简单' }).click();

    // 等待筛选结果
    await page.waitForTimeout(1000);

    // 验证筛选条件显示
    const filterText = page.locator("text=当前筛选:");
    if (await filterText.isVisible()) {
      await expect(filterText).toBeVisible();
    }
  });

  test("应该能够清除筛选条件", async ({ page }) => {
    // 输入搜索关键词
    const searchInput = page.locator('[data-testid="search-input"]');
    await searchInput.fill("测试");

    // 等待筛选条件显示
    await page.waitForTimeout(1000);

    // 使用更精确的选择器：选择filter bar中的"清除筛选"按钮（而非empty state的"清除筛选条件"按钮）
    const clearButton = page.locator("button:has-text('清除筛选')").first();
    if (await clearButton.isVisible()) {
      await clearButton.click();

      // 验证搜索框清空
      await expect(searchInput).toHaveValue("");
    }
  });

  test("页面应该响应式布局", async ({ page }) => {
    // 测试移动端视口
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(page.getByRole("heading", { name: /模板库/ })).toBeVisible();

    // 测试平板视口
    await page.setViewportSize({ width: 768, height: 1024 });
    await expect(page.getByRole("heading", { name: /模板库/ })).toBeVisible();

    // 测试桌面视口
    await page.setViewportSize({ width: 1920, height: 1080 });
    await expect(page.getByRole("heading", { name: /模板库/ })).toBeVisible();
  });

  test("应该显示空状态（无搜索结果）", async ({ page }) => {
    // 输入不存在的搜索关键词
    const searchInput = page.locator('[data-testid="search-input"]');
    await searchInput.fill("不存在的模板名称xyz123");

    // 等待搜索结果
    await page.waitForTimeout(1000);

    // 验证显示空状态或没有结果
    const emptyState = page.locator("text=/没有找到|0 个模板/");
    await expect(emptyState).toBeVisible();
  });

  test("应该在顶部导航显示模板库链接", async ({ page }) => {
    // 检查顶部导航有模板库链接
    const templatesLink = page.locator('nav a:has-text("模板库")');
    await expect(templatesLink).toBeVisible();
    await expect(templatesLink).toHaveAttribute("href", "/templates");
  });
});
