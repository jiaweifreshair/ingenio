/**
 * Button组件单元测试
 * 秒构AI前端UI组件测试示例
 *
 * 测试目标：src/components/ui/button.tsx
 * 测试策略：
 * - 测试渲染正确
 * - 测试所有变体样式
 * - 测试点击事件
 * - 测试禁用状态
 * - 测试asChild功能
 *
 * Week 1 Day 3 Phase 3.1 - 测试环境搭建
 * 创建时间：2025-11-14
 */

import { describe, it, expect, vi } from "vitest";
import { renderWithProviders, screen } from "@/test/utils/render-with-providers";
import { Button } from "./button";

describe("Button组件", () => {
  describe("基础渲染", () => {
    it("应该正确渲染按钮文本", () => {
      renderWithProviders(<Button>点击我</Button>);
      expect(screen.getByRole("button")).toHaveTextContent("点击我");
    });

    it("应该应用默认变体样式", () => {
      renderWithProviders(<Button>默认按钮</Button>);
      const button = screen.getByRole("button");
      expect(button).toHaveClass("bg-primary");
      expect(button).toHaveClass("text-primary-foreground");
    });

    it("应该支持自定义className", () => {
      renderWithProviders(<Button className="custom-class">按钮</Button>);
      expect(screen.getByRole("button")).toHaveClass("custom-class");
    });
  });

  describe("变体样式", () => {
    it("应该渲染default变体", () => {
      renderWithProviders(<Button variant="default">Default</Button>);
      expect(screen.getByRole("button")).toHaveClass("bg-primary");
    });

    it("应该渲染destructive变体", () => {
      renderWithProviders(<Button variant="destructive">Delete</Button>);
      expect(screen.getByRole("button")).toHaveClass("bg-destructive");
    });

    it("应该渲染outline变体", () => {
      renderWithProviders(<Button variant="outline">Outline</Button>);
      const button = screen.getByRole("button");
      expect(button).toHaveClass("border");
      expect(button).toHaveClass("bg-background");
    });

    it("应该渲染secondary变体", () => {
      renderWithProviders(<Button variant="secondary">Secondary</Button>);
      expect(screen.getByRole("button")).toHaveClass("bg-secondary");
    });

    it("应该渲染ghost变体", () => {
      renderWithProviders(<Button variant="ghost">Ghost</Button>);
      expect(screen.getByRole("button")).toHaveClass("hover:bg-accent");
    });

    it("应该渲染link变体", () => {
      renderWithProviders(<Button variant="link">Link</Button>);
      expect(screen.getByRole("button")).toHaveClass("underline-offset-4");
    });
  });

  describe("尺寸变体", () => {
    it("应该渲染default尺寸", () => {
      renderWithProviders(<Button size="default">Default Size</Button>);
      expect(screen.getByRole("button")).toHaveClass("h-10");
    });

    it("应该渲染sm尺寸", () => {
      renderWithProviders(<Button size="sm">Small</Button>);
      expect(screen.getByRole("button")).toHaveClass("h-9");
    });

    it("应该渲染lg尺寸", () => {
      renderWithProviders(<Button size="lg">Large</Button>);
      expect(screen.getByRole("button")).toHaveClass("h-11");
    });

    it("应该渲染icon尺寸", () => {
      renderWithProviders(<Button size="icon">I</Button>);
      const button = screen.getByRole("button");
      expect(button).toHaveClass("h-10");
      expect(button).toHaveClass("w-10");
    });
  });

  describe("交互行为", () => {
    it("应该响应点击事件", async () => {
      const handleClick = vi.fn();
      renderWithProviders(<Button onClick={handleClick}>点击</Button>);

      const button = screen.getByRole("button");
      const user = await import("@testing-library/user-event");
      await user.default.click(button);

      expect(handleClick).toHaveBeenCalledTimes(1);
    });

    it("禁用状态下不应触发点击事件", async () => {
      const handleClick = vi.fn();
      renderWithProviders(
        <Button disabled onClick={handleClick}>
          禁用按钮
        </Button>
      );

      const button = screen.getByRole("button");
      expect(button).toBeDisabled();

      // 尝试点击禁用的按钮
      const user = await import("@testing-library/user-event");
      await user.default.click(button);

      // 点击事件不应被触发
      expect(handleClick).not.toHaveBeenCalled();
    });

    it("应该支持type属性", () => {
      renderWithProviders(<Button type="submit">提交</Button>);
      expect(screen.getByRole("button")).toHaveAttribute("type", "submit");
    });
  });

  describe("asChild功能", () => {
    it("应该将样式应用到子元素", () => {
      renderWithProviders(
        <Button asChild>
          <a href="/test">链接按钮</a>
        </Button>
      );

      const link = screen.getByRole("link");
      expect(link).toHaveClass("bg-primary");
      expect(link).toHaveAttribute("href", "/test");
    });
  });

  describe("动画效果", () => {
    it("应该包含motion动画配置", () => {
      renderWithProviders(<Button>动画按钮</Button>);
      const button = screen.getByRole("button");
      // 检查按钮是否被渲染（motion组件会添加额外的属性）
      expect(button).toBeInTheDocument();
      // Button使用framer-motion包装，应该包含transition样式
      expect(button).toHaveClass("transition-all");
    });
  });

  describe("边界情况", () => {
    it("应该处理空children", () => {
      renderWithProviders(<Button />);
      const button = screen.getByRole("button");
      expect(button).toBeInTheDocument();
      expect(button).toHaveTextContent("");
    });

    it("应该处理长文本", () => {
      const longText = "这是一个非常非常非常长的按钮文本内容测试";
      renderWithProviders(<Button>{longText}</Button>);
      expect(screen.getByRole("button")).toHaveTextContent(longText);
    });

    it("应该支持多个className组合", () => {
      renderWithProviders(
        <Button variant="outline" size="lg" className="custom-1 custom-2">
          组合样式
        </Button>
      );
      const button = screen.getByRole("button");
      expect(button).toHaveClass("border");
      expect(button).toHaveClass("h-11");
      expect(button).toHaveClass("custom-1");
      expect(button).toHaveClass("custom-2");
    });
  });
});
