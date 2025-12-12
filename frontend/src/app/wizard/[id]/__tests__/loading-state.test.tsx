/**
 * LoadingState组件测试
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LoadingState } from '../components/loading-state';

describe('LoadingState', () => {
  it('应该正确渲染默认标题和消息', () => {
    render(<LoadingState />);

    expect(screen.getByText('AppSpec 生成向导')).toBeInTheDocument();
    expect(screen.getByText('正在加载向导...')).toBeInTheDocument();
  });

  it('应该正确渲染自定义标题', () => {
    render(<LoadingState title="自定义加载标题" />);

    expect(screen.getByText('自定义加载标题')).toBeInTheDocument();
  });

  it('应该正确渲染自定义消息', () => {
    render(<LoadingState message="正在加载数据..." />);

    expect(screen.getByText('正在加载数据...')).toBeInTheDocument();
  });

  it('应该显示加载中Badge', () => {
    render(<LoadingState />);

    expect(screen.getByText('加载中')).toBeInTheDocument();
  });

  it('应该显示加载动画', () => {
    const { container } = render(<LoadingState />);

    const loaders = container.querySelectorAll('.animate-spin');
    expect(loaders.length).toBeGreaterThan(0);
  });

  it('应该使用全屏布局', () => {
    const { container } = render(<LoadingState />);

    const rootElement = container.firstChild;
    expect(rootElement).toHaveClass('h-screen');
    expect(rootElement).toHaveClass('bg-background');
  });
});
