/**
 * Vitest测试环境配置
 * 秒构AI单元测试初始化
 *
 * 设计原则：遵循零Mock策略（CLAUDE.md 5.8节）
 * - 使用真实后端API进行测试
 * - 避免Mock API响应和数据
 * - 测试环境变量来自.env.test文件
 *
 * Week 1 Day 3 Phase 3.1 - 测试环境搭建
 * 创建时间：2025-11-14
 */
import "@testing-library/jest-dom";
import { cleanup } from "@testing-library/react";
import { afterEach, beforeAll, afterAll } from "vitest";

// 加载测试环境变量
beforeAll(() => {
  // 确保测试环境变量已设置
  // Vitest会自动从.env.test加载环境变量
  if (!process.env.NEXT_PUBLIC_API_BASE_URL) {
    process.env.NEXT_PUBLIC_API_BASE_URL = "http://localhost:8080";
  }

  // Mock window.matchMedia（JSDOM不支持）
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {}, // 已废弃
      removeListener: () => {}, // 已废弃
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => true,
    }),
  });

  // Mock window.ResizeObserver（某些组件需要）
  global.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});

// 每个测试后自动清理React组件
afterEach(() => {
  cleanup();
});

// 配置全局测试超时时间
const timeout = parseInt(process.env.VITEST_TIMEOUT || "10000", 10);
if (timeout > 0) {
  // 注意：全局超时配置应在vitest.config.ts中设置
  // 这里仅作为环境变量的参考
}

// 禁用console.error在测试中的输出（减少噪音）
// 但保留console.warn以便发现潜在问题
const originalError = console.error;
beforeAll(() => {
  console.error = (...args: unknown[]) => {
    // 过滤掉React Testing Library的某些已知警告
    if (
      typeof args[0] === "string" &&
      (args[0].includes("Not implemented: HTMLFormElement.prototype.submit") ||
        args[0].includes("Could not parse CSS stylesheet"))
    ) {
      return;
    }
    originalError.call(console, ...args);
  };
});

// 清理：测试结束后恢复console.error
afterAll(() => {
  console.error = originalError;
});
