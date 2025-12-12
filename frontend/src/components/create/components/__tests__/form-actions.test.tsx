/**
 * FormActions组件单元测试
 *
 * 测试场景：
 * - 两个按钮渲染
 * - 快速预览按钮跳转
 * - 完整生成按钮回调
 * - loading状态显示
 * - 按钮禁用状态
 * - 快捷键提示显示
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FormActions } from '../form-actions';

// Mock Next.js router
const mockPush = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

describe('FormActions', () => {
  beforeEach(() => {
    mockPush.mockClear();
  });

  /**
   * 测试1: 两个按钮渲染
   * 验证快速预览和完整生成两个按钮都正确渲染
   */
  it('应该渲染快速预览和完整生成两个按钮', () => {
    const onFullGeneration = vi.fn();

    render(
      <FormActions
        isValid={true}
        isLoading={false}
        requirement="test requirement"
        onFullGeneration={onFullGeneration}
      />
    );

    expect(screen.getByRole('button', { name: /快速Web预览/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /生成应用/ })).toBeInTheDocument();
  });

  /**
   * 测试2: 快速预览按钮跳转
   * 验证点击快速预览按钮触发路由跳转
   */
  it('应该在点击快速预览按钮时跳转到预览页面', async () => {
    const user = userEvent.setup();
    const onFullGeneration = vi.fn();

    render(
      <FormActions
        isValid={true}
        isLoading={false}
        requirement="test requirement"
        onFullGeneration={onFullGeneration}
      />
    );

    const quickPreviewButton = screen.getByRole('button', { name: /快速Web预览/ });
    await user.click(quickPreviewButton);

    expect(mockPush).toHaveBeenCalledWith(
      expect.stringContaining('/preview-quick/')
    );
    expect(mockPush).toHaveBeenCalledWith(
      expect.stringContaining(encodeURIComponent('test requirement'))
    );
  });

  /**
   * 测试3: 完整生成按钮回调
   * 验证点击完整生成按钮触发onFullGeneration回调
   */
  it('应该在点击完整生成按钮时触发onFullGeneration回调', async () => {
    const user = userEvent.setup();
    const onFullGeneration = vi.fn();

    render(
      <FormActions
        isValid={true}
        isLoading={false}
        requirement="test requirement"
        onFullGeneration={onFullGeneration}
      />
    );

    const fullGenerationButton = screen.getByRole('button', { name: /生成应用/ });
    await user.click(fullGenerationButton);

    expect(onFullGeneration).toHaveBeenCalledTimes(1);
  });

  /**
   * 测试4: loading状态显示
   * 验证isLoading为true时显示加载动画和文本
   */
  it('应该在isLoading为true时显示加载状态', () => {
    const onFullGeneration = vi.fn();

    render(
      <FormActions
        isValid={true}
        isLoading={true}
        requirement="test requirement"
        onFullGeneration={onFullGeneration}
      />
    );

    expect(screen.getByText(/AI正在分析你的需求/)).toBeInTheDocument();
    // 验证加载动画存在
    const spinner = document.querySelector('.animate-spin');
    expect(spinner).toBeInTheDocument();
  });

  /**
   * 测试5: 按钮禁用状态（isValid=false）
   * 验证表单无效时两个按钮都被禁用
   */
  it('应该在isValid为false时禁用两个按钮', () => {
    const onFullGeneration = vi.fn();

    render(
      <FormActions
        isValid={false}
        isLoading={false}
        requirement="short"
        onFullGeneration={onFullGeneration}
      />
    );

    const quickPreviewButton = screen.getByRole('button', { name: /快速Web预览/ });
    const fullGenerationButton = screen.getByRole('button', { name: /生成应用/ });

    expect(quickPreviewButton).toBeDisabled();
    expect(fullGenerationButton).toBeDisabled();
  });

  /**
   * 测试6: 按钮禁用状态（isLoading=true）
   * 验证加载中时两个按钮都被禁用
   */
  it('应该在isLoading为true时禁用两个按钮', () => {
    const onFullGeneration = vi.fn();

    render(
      <FormActions
        isValid={true}
        isLoading={true}
        requirement="test requirement"
        onFullGeneration={onFullGeneration}
      />
    );

    const quickPreviewButton = screen.getByRole('button', { name: /快速Web预览/ });
    const fullGenerationButton = screen.getByRole('button', { name: /AI正在分析你的需求/ });

    expect(quickPreviewButton).toBeDisabled();
    expect(fullGenerationButton).toBeDisabled();
  });

  /**
   * 测试7: 快捷键提示显示
   * 验证快捷键提示文本正确显示
   */
  it('应该显示快捷键提示', () => {
    const onFullGeneration = vi.fn();

    render(
      <FormActions
        isValid={true}
        isLoading={false}
        requirement="test requirement"
        onFullGeneration={onFullGeneration}
      />
    );

    expect(screen.getByText(/按/)).toBeInTheDocument();
    expect(screen.getByText(/快速提交/)).toBeInTheDocument();
    // 验证kbd元素
    const kbdElements = document.querySelectorAll('kbd');
    expect(kbdElements.length).toBeGreaterThan(0);
  });
});
