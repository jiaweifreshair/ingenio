import { defineConfig, configDefaults } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";

/**
 * Vitest 控制台告警压制
 *
 * 是什么：Vite CJS API 废弃告警的环境开关。
 * 做什么：默认压制该告警，减少测试噪音。
 * 为什么：避免在本地/CI 输出过多非关键日志。
 */
if (!process.env.VITE_CJS_IGNORE_WARNING) {
  process.env.VITE_CJS_IGNORE_WARNING = "1";
}

/**
 * Vitest配置 - 单元测试
 * 秒构AI前端测试配置
 */
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    // 重要：Playwright 的 E2E 用例位于 src/e2e 与 tests/e2e，不应被 Vitest 作为单测收集执行
    exclude: [...configDefaults.exclude, "src/e2e/**", "tests/e2e/**"],
    coverage: {
      provider: "v8",
      reporter: ["text", "json", "html"],
      exclude: [
        "node_modules/",
        "src/test/",
        "**/*.config.{ts,js}",
        "**/*.d.ts",
        "**/types/**",
      ],
      thresholds: {
        lines: 85,
        functions: 85,
        branches: 85,
        statements: 85,
      },
    },
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
});
