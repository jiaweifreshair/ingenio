/**
 * ExploreMoreCard组件测试
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ExploreMoreCard } from '../components/explore-more-card';

describe('ExploreMoreCard', () => {
  const mockAppSpecId = 'test-app-spec-123';
  const mockOnNavigate = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('应该正确渲染卡片标题', () => {
    render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    expect(screen.getByText('探索更多功能')).toBeInTheDocument();
  });

  it('应该正确渲染AI能力选择按钮', () => {
    render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    expect(screen.getByText('AI能力选择')).toBeInTheDocument();
    expect(
      screen.getByText('智能分析需求，推荐AI能力组合')
    ).toBeInTheDocument();
  });

  it('应该正确渲染SuperDesign按钮', () => {
    render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    expect(screen.getByText('SuperDesign')).toBeInTheDocument();
    expect(
      screen.getByText('AI生成3种设计风格方案')
    ).toBeInTheDocument();
  });

  it('应该正确渲染时光机版本按钮', () => {
    render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    expect(screen.getByText('时光机版本')).toBeInTheDocument();
    expect(screen.getByText('查看版本历史，一键回溯')).toBeInTheDocument();
  });

  it('点击AI能力选择按钮应该调用onNavigate', async () => {
    const user = userEvent.setup();
    render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    const button = screen.getByRole('button', { name: /AI能力选择/i });
    await user.click(button);

    expect(mockOnNavigate).toHaveBeenCalledWith('/wizard/ai-capabilities');
  });

  it('点击SuperDesign按钮应该调用onNavigate并传入appSpecId', async () => {
    const user = userEvent.setup();
    render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    const button = screen.getByRole('button', { name: /SuperDesign/i });
    await user.click(button);

    expect(mockOnNavigate).toHaveBeenCalledWith(
      `/superdesign/${mockAppSpecId}`
    );
  });

  it('点击时光机版本按钮应该调用onNavigate', async () => {
    const user = userEvent.setup();
    render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    const button = screen.getByRole('button', { name: /时光机版本/i });
    await user.click(button);

    expect(mockOnNavigate).toHaveBeenCalledWith('/dashboard');
  });

  it('应该渲染正确的图标', () => {
    const { container } = render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    const icons = container.querySelectorAll('svg');
    expect(icons.length).toBeGreaterThan(0);
  });

  it('应该使用渐变背景', () => {
    const { container } = render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    const card = container.querySelector('.bg-gradient-to-br');
    expect(card).toBeInTheDocument();
  });

  it('应该使用网格布局', () => {
    const { container } = render(
      <ExploreMoreCard appSpecId={mockAppSpecId} onNavigate={mockOnNavigate} />
    );

    const grid = container.querySelector('.grid');
    expect(grid).toBeInTheDocument();
    expect(grid).toHaveClass('grid-cols-1', 'md:grid-cols-3');
  });

  it('应该应用自定义类名', () => {
    const customClassName = 'custom-class';
    const { container } = render(
      <ExploreMoreCard
        appSpecId={mockAppSpecId}
        onNavigate={mockOnNavigate}
        className={customClassName}
      />
    );

    const card = container.firstChild;
    expect(card).toHaveClass(customClassName);
  });
});
