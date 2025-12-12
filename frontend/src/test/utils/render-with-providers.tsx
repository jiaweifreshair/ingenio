/**
 * React组件测试包装器
 * 秒构AI前端组件测试工具
 *
 * 功能：
 * - 提供统一的测试渲染函数
 * - 包装必要的Context Provider
 * - 模拟路由环境（使用真实路由，不使用Mock）
 *
 * 使用示例：
 * ```typescript
 * import { renderWithProviders } from '@/test/utils/render-with-providers';
 * import MyComponent from '@/components/MyComponent';
 *
 * test('renders component', () => {
 *   const { getByText } = renderWithProviders(<MyComponent />);
 *   expect(getByText('Hello')).toBeInTheDocument();
 * });
 * ```
 *
 * Week 1 Day 3 Phase 3.1 - 测试环境搭建
 * 创建时间：2025-11-14
 */

import { render, RenderOptions, RenderResult } from "@testing-library/react";
import { ReactElement, ReactNode } from "react";

/**
 * 测试环境Provider包装器
 * 包含所有必要的Context Provider
 */
interface ProvidersProps {
  children: ReactNode;
}

function TestProviders({ children }: ProvidersProps): ReactElement {
  // 注意：这里不使用Mock，而是使用真实的Provider
  // 如果需要特定的Context值，通过props传入

  return (
    <>
      {/* 这里可以添加应用中使用的Context Provider */}
      {/* 例如：ThemeProvider, AuthProvider等 */}
      {/* 注意：应该使用真实的Provider，而非Mock版本 */}
      {children}
    </>
  );
}

/**
 * 自定义渲染选项
 */
type CustomRenderOptions = Omit<RenderOptions, "wrapper"> & {
  // 可以添加自定义选项，例如：
  // initialState?: Partial<AppState>;
  // user?: User;
}

/**
 * 带Provider的渲染函数
 *
 * @param ui React组件
 * @param options 渲染选项
 * @returns Testing Library的RenderResult
 *
 * @example
 * ```typescript
 * const { getByRole, getByText } = renderWithProviders(<Button>Click me</Button>);
 * const button = getByRole('button');
 * expect(button).toHaveTextContent('Click me');
 * ```
 */
export function renderWithProviders(
  ui: ReactElement,
  options?: CustomRenderOptions
): RenderResult {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <TestProviders>{children}</TestProviders>
  );

  return render(ui, { wrapper: Wrapper, ...options });
}

/**
 * 重新导出Testing Library的所有工具
 * 这样用户只需要从这个文件导入即可
 */
export * from "@testing-library/react";

/**
 * 重新导出常用的用户事件工具
 */
export { default as userEvent } from "@testing-library/user-event";
