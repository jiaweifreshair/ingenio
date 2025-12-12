/**
 * 工具函数单元测试
 * 秒构AI前端单元测试示例
 *
 * 测试目标：src/lib/utils.ts
 * 测试策略：
 * - 测试正常情况
 * - 测试边界情况
 * - 测试错误处理
 *
 * Week 1 Day 3 Phase 3.1 - 测试环境搭建
 * 创建时间：2025-11-14
 */

import { describe, it, expect } from "vitest";
import { cn } from "./utils";

describe("cn() - className合并工具", () => {
  it("应该合并多个className", () => {
    const result = cn("text-red-500", "bg-blue-100");
    expect(result).toBe("text-red-500 bg-blue-100");
  });

  it("应该过滤falsy值", () => {
    const result = cn("text-red-500", false, null, undefined, "bg-blue-100");
    expect(result).toBe("text-red-500 bg-blue-100");
  });

  it("应该处理条件className", () => {
    const isActive = true;
    const result = cn("base-class", isActive && "active-class");
    expect(result).toBe("base-class active-class");
  });

  it("应该处理空输入", () => {
    const result = cn();
    expect(result).toBe("");
  });

  it("应该合并Tailwind冲突类名（后者优先）", () => {
    // clsx + tailwind-merge 的组合应该解决冲突
    const result = cn("p-4", "p-2");
    // tailwind-merge应该只保留最后的p-2
    expect(result).toBe("p-2");
  });

  it("应该处理对象形式的className", () => {
    const result = cn({
      "text-red-500": true,
      "bg-blue-100": false,
      "font-bold": true,
    });
    expect(result).toContain("text-red-500");
    expect(result).toContain("font-bold");
    expect(result).not.toContain("bg-blue-100");
  });

  it("应该处理数组形式的className", () => {
    const result = cn(["text-red-500", "bg-blue-100"]);
    expect(result).toBe("text-red-500 bg-blue-100");
  });

  it("应该处理复杂混合情况", () => {
    const isActive = true;
    const isPrimary = false;
    const result = cn(
      "base-class",
      isActive && "active-class",
      isPrimary && "primary-class",
      {
        "hover-class": true,
        "disabled-class": false,
      },
      ["focus-class", "ring-class"]
    );

    expect(result).toContain("base-class");
    expect(result).toContain("active-class");
    expect(result).toContain("hover-class");
    expect(result).toContain("focus-class");
    expect(result).toContain("ring-class");
    expect(result).not.toContain("primary-class");
    expect(result).not.toContain("disabled-class");
  });
});
