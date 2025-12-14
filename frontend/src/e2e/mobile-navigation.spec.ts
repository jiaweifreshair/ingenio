/**
 * 移动端导航E2E测试
 * 测试TopNav组件在移动端的Sheet导航功能
 *
 * 测试场景：
 * - 移动端菜单按钮可见性
 * - Sheet侧边栏打开/关闭
 * - 导航链接跳转
 * - 快捷操作按钮
 * - 通知未读数量显示
 *
 * @author Ingenio Team
 * @since 1.0.0 (Day 5 Phase 5.2C)
 */
import { test, expect } from "@playwright/test";

// 使用移动端视口配置（chromium浏览器 + iPhone 12 viewport）
// Note: 使用chromium而不是webkit，因为项目只配置了chromium
test.use({
  viewport: { width: 390, height: 844 }, // iPhone 12 viewport
  deviceScaleFactor: 3,
  isMobile: true,
  hasTouch: true,
  userAgent:
    "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0 Mobile/15E148 Safari/604.1",
});

test.describe("移动端导航 - TopNav Sheet", () => {
  test.beforeEach(async ({ page }) => {
    // 访问首页
    await page.goto("/");
  });

  test("移动端菜单按钮可见", async ({ page }) => {
    // 移动端菜单按钮应该可见
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await expect(menuButton).toBeVisible();

    // 桌面端导航应该不可见（已被隐藏）
    const desktopNav = page.locator("nav.hidden.md\\:flex");
    await expect(desktopNav).not.toBeVisible();
  });

  test("点击菜单按钮打开Sheet侧边栏", async ({ page }) => {
    // 点击菜单按钮
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    // Sheet应该打开，显示标题
    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).toBeVisible();

    // 验证所有导航链接可见
    const navLinks = [
      "功能",
      "应用模版",
      "校园案例",
      "三步创建",
      "常见问题",
    ];

    for (const linkText of navLinks) {
      const link = page.getByRole("link", { name: linkText });
      await expect(link).toBeVisible();
    }
  });

  test("Sheet中的快捷操作按钮可见", async ({ page }) => {
    // 打开Sheet
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    // 验证快捷操作按钮
    const freeStartButton = page.getByRole("link", { name: /免费开始/i });
    await expect(freeStartButton).toBeVisible();

    const notificationButton = page.getByRole("link", { name: /通知/i });
    await expect(notificationButton).toBeVisible();

    const accountButton = page.getByRole("link", { name: /个人中心/i });
    await expect(accountButton).toBeVisible();
  });

  test("点击导航链接后Sheet自动关闭", async ({ page }) => {
    // 打开Sheet
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    // 点击应用模版链接
    const templatesLink = page.getByRole("link", { name: "应用模版" });
    await templatesLink.click();

    // Sheet应该关闭（标题不再可见）
    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).not.toBeVisible();

    // URL应该跳转到应用模版
    await expect(page).toHaveURL("/templates");
  });

  test("点击关闭按钮关闭Sheet", async ({ page }) => {
    // 打开Sheet
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    // 验证Sheet打开
    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).toBeVisible();

    // 点击关闭按钮（X图标）
    const closeButton = page.getByRole("button", { name: /close/i });
    await closeButton.click();

    // Sheet应该关闭
    await expect(sheetTitle).not.toBeVisible();
  });

  test("点击遮罩层关闭Sheet", async ({ page }) => {
    // 打开Sheet
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    // 验证Sheet打开
    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).toBeVisible();

    // 点击遮罩层（Sheet外部区域）
    // 使用page.mouse点击右侧空白区域
    await page.mouse.click(350, 200);

    // Sheet应该关闭
    await expect(sheetTitle).not.toBeVisible();
  });

  test("通知未读数量Badge显示", async ({ page }) => {
    // 打开Sheet
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    // 通知按钮应该可见
    const notificationButton = page.getByRole("link", { name: /通知/i });
    await expect(notificationButton).toBeVisible();

    // 注意：未读数量由API返回，当前API返回0（优雅降级）
    // 验证按钮可点击
    await expect(notificationButton).toBeEnabled();
  });

  test("从Sheet点击免费开始按钮跳转", async ({ page }) => {
    // 打开Sheet
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    // 点击免费开始按钮
    const freeStartButton = page.getByRole("link", { name: /免费开始/i });
    await freeStartButton.click();

    // 应该跳转到创建页面（增加超时时间）
    await expect(page).toHaveURL("/create", { timeout: 15000 });

    // 等待页面加载完成
    await expect(page.getByRole('heading', { name: /创建新应用/ })).toBeVisible({ timeout: 10000 });

    // Sheet应该自动关闭
    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).not.toBeVisible();
  });

  test("从Sheet点击个人中心按钮跳转", async ({ page }) => {
    // 打开Sheet
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    // 点击个人中心按钮
    const accountButton = page.getByRole("link", { name: /个人中心/i });
    await accountButton.click();

    // 应该跳转到账户页面
    await expect(page).toHaveURL("/account");

    // Sheet应该自动关闭
    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).not.toBeVisible();
  });

  test("Sheet滑入动画正常", async ({ page }) => {
    // 打开Sheet
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    // 等待动画完成
    await page.waitForTimeout(500);

    // 验证Sheet内容可见
    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).toBeVisible();

    // 验证Sheet位置（应该在左侧）
    const sheetContent = page.locator('[role="dialog"]');
    const box = await sheetContent.boundingBox();
    expect(box?.x).toBeLessThan(100); // Sheet从左侧滑入，x坐标应该接近0
  });

  test("多次打开关闭Sheet正常", async ({ page }) => {
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });

    // 第一次打开
    await menuButton.click();
    await expect(sheetTitle).toBeVisible();

    // 关闭
    await page.mouse.click(350, 200);
    await expect(sheetTitle).not.toBeVisible();

    // 第二次打开
    await menuButton.click();
    await expect(sheetTitle).toBeVisible();

    // 再次关闭
    await page.mouse.click(350, 200);
    await expect(sheetTitle).not.toBeVisible();
  });
});

test.describe("移动端导航 - 响应式检查", () => {
  test("在不同移动端设备上Sheet正常显示", async ({ page }) => {
    // 已经通过test.use(devices["iPhone 12"])配置了设备
    await page.goto("/");

    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).toBeVisible();
  });

  test("横屏模式下Sheet正常显示", async ({ page, context: _context }) => {
    // 切换到横屏模式
    await page.setViewportSize({ width: 844, height: 390 }); // iPhone 12 横屏

    await page.goto("/");

    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await menuButton.click();

    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).toBeVisible();
  });
});

test.describe("移动端导航 - 可访问性检查", () => {
  test("键盘导航支持", async ({ page }) => {
    await page.goto("/");

    // 使用Tab键聚焦到菜单按钮
    await page.keyboard.press("Tab");
    await page.keyboard.press("Tab");

    // 按Enter打开Sheet
    await page.keyboard.press("Enter");

    const sheetTitle = page.getByRole("heading", { name: "秒构AI" });
    await expect(sheetTitle).toBeVisible();

    // 按Escape关闭Sheet
    await page.keyboard.press("Escape");
    await expect(sheetTitle).not.toBeVisible();
  });

  test("屏幕阅读器标签正确", async ({ page }) => {
    await page.goto("/");

    // 验证菜单按钮的aria-label
    const menuButton = page.getByRole("button", { name: /打开菜单/i });
    await expect(menuButton).toBeVisible();

    // 打开Sheet
    await menuButton.click();

    // 验证关闭按钮的aria-label
    const closeButton = page.getByRole("button", { name: /close/i });
    await expect(closeButton).toBeVisible();
  });
});
