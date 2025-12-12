/**
 * ErrorState组件测试
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ErrorState } from '../components/error-state';

describe('ErrorState', () => {
  const mockOnRetry = vi.fn();
  const mockOnBack = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('应该正确渲染默认标题', () => {
    render(
      <ErrorState
        error="测试错误"
        onRetry={mockOnRetry}
        onBack={mockOnBack}
      />
    );

    expect(screen.getByText('AppSpec 生成向导')).toBeInTheDocument();
  });

  it('应该正确渲染自定义标题', () => {
    render(
      <ErrorState
        title="自定义错误标题"
        error="测试错误"
        onRetry={mockOnRetry}
        onBack={mockOnBack}
      />
    );

    expect(screen.getByText('自定义错误标题')).toBeInTheDocument();
  });

  it('应该正确显示错误信息', () => {
    const errorMessage = '网络连接失败，请稍后重试';
    render(
      <ErrorState
        error={errorMessage}
        onRetry={mockOnRetry}
        onBack={mockOnBack}
      />
    );

    expect(screen.getByText(errorMessage)).toBeInTheDocument();
  });

  it('应该显示生成失败Badge', () => {
    render(
      <ErrorState
        error="测试错误"
        onRetry={mockOnRetry}
        onBack={mockOnBack}
      />
    );

    // 使用getAllByText因为"生成失败"在多个地方出现
    const failedTexts = screen.getAllByText('生成失败');
    expect(failedTexts.length).toBeGreaterThan(0);
  });

  it('应该显示错误图标', () => {
    const { container } = render(
      <ErrorState
        error="测试错误"
        onRetry={mockOnRetry}
        onBack={mockOnBack}
      />
    );

    const icon = container.querySelector('.text-destructive');
    expect(icon).toBeInTheDocument();
  });

  it('点击重试按钮应该调用onRetry回调', async () => {
    const user = userEvent.setup();
    render(
      <ErrorState
        error="测试错误"
        onRetry={mockOnRetry}
        onBack={mockOnBack}
      />
    );

    const retryButton = screen.getByRole('button', { name: /重试/i });
    await user.click(retryButton);

    expect(mockOnRetry).toHaveBeenCalledTimes(1);
  });

  it('点击返回创建按钮应该调用onBack回调', async () => {
    const user = userEvent.setup();
    render(
      <ErrorState
        error="测试错误"
        onRetry={mockOnRetry}
        onBack={mockOnBack}
      />
    );

    const backButton = screen.getByRole('button', { name: /返回创建/i });
    await user.click(backButton);

    expect(mockOnBack).toHaveBeenCalledTimes(1);
  });

  it('应该同时显示重试和返回按钮', () => {
    render(
      <ErrorState
        error="测试错误"
        onRetry={mockOnRetry}
        onBack={mockOnBack}
      />
    );

    expect(screen.getByRole('button', { name: /重试/i })).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /返回创建/i })
    ).toBeInTheDocument();
  });
});
