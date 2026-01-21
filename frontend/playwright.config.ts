import { defineConfig, devices } from "@playwright/test";

/**
 * 受限/代理环境兼容：
 * - 部分环境会默认注入 HTTP(S)_PROXY/ALL_PROXY，导致浏览器访问 localhost 也走代理而连接失败
 * - E2E 只依赖本机 WebServer，因此这里主动清理代理并设置 NO_PROXY
 */
for (const key of [
  "HTTP_PROXY",
  "HTTPS_PROXY",
  "ALL_PROXY",
  "http_proxy",
  "https_proxy",
  "all_proxy",
]) {
  delete process.env[key];
}
process.env.NO_PROXY = process.env.NO_PROXY
  ? `${process.env.NO_PROXY},localhost,127.0.0.1,::1`
  : "localhost,127.0.0.1,::1";
process.env.no_proxy = process.env.NO_PROXY;

/**
 * Playwright配置 - E2E测试
 * 秒构AI端到端测试配置
 *
 * 优化策略（Week 1 Day 3 Phase 3.1）：
 * - 本地开发：仅测试chromium（快速反馈）
 * - CI环境：测试全部浏览器（保证兼容性）
 * - 超时时间：根据.env.test配置
 * - 并发控制：本地并发，CI串行（稳定性）
 *
 * 创建时间：2025-11-14
 */
export default defineConfig({
  testDir: "./src/e2e",
  fullyParallel: !process.env.CI, // CI环境不并行，提升稳定性
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  // 本地默认使用 HTML 报告，但禁用失败后自动启动报告服务，避免脚本/CI 场景卡住不退出
  reporter: process.env.CI ? "github" : [["html", { open: "never" }]],

  // 全局超时配置
  timeout: parseInt(process.env.PLAYWRIGHT_TIMEOUT || "30000", 10),

  use: {
    // 默认使用 127.0.0.1，避免 localhost 在部分环境优先解析到 IPv6(::1) 导致连接拒绝
    baseURL: process.env.PLAYWRIGHT_TEST_BASE_URL || "http://127.0.0.1:3000",
    trace: "on-first-retry",
    screenshot: "only-on-failure",

    // 视频录制：仅失败时（调试用）
    video: "retain-on-failure",

    // 操作超时
    actionTimeout: 10000,
    navigationTimeout: 30000,
  },

  // 浏览器项目配置
  // 策略：本地开发仅测试chromium，CI环境测试全部
  projects: process.env.CI
    ? [
        // CI环境：测试所有浏览器和设备
        {
          name: "chromium",
          use: { ...devices["Desktop Chrome"] },
        },
        {
          name: "firefox",
          use: { ...devices["Desktop Firefox"] },
        },
        {
          name: "webkit",
          use: { ...devices["Desktop Safari"] },
        },
        {
          name: "Mobile Chrome",
          use: { ...devices["Pixel 5"] },
        },
        {
          name: "Mobile Safari",
          use: { ...devices["iPhone 12"] },
        },
      ]
    : [
        // 本地开发：仅测试chromium（快速反馈）
        {
          name: "chromium",
          use: { ...devices["Desktop Chrome"] },
        },
      ],

  webServer: {
    // 绑定到 127.0.0.1，避免在受限环境下监听 0.0.0.0 导致 EPERM
    command: "pnpm dev --hostname 127.0.0.1 --port 3000",
    // url 需与 hostname 保持一致，避免 Playwright 通过 localhost(IPv6) 探测时误判不可达
    url: "http://127.0.0.1:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
    // stdout: 'ignore', // 减少日志噪音
    // stderr: 'pipe',
  },
});
