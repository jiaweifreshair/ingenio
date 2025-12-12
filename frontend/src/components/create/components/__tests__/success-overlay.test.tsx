/**
 * SuccessOverlay组件单元测试
 *
 * 测试场景：
 * - show为true时显示overlay
 * - show为false时隐藏overlay
 * - 成功动画渲染
 * - 提示文字显示
 * - 背景模糊效果
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SuccessOverlay } from '../success-overlay';

// Mock SuccessAnimation组件
vi.mock('@/components/ui/success-animation', () => ({
  SuccessAnimation: ({ size, showConfetti }: { size: number; showConfetti: boolean }) => (
    <div data-testid="success-animation" data-size={size} data-confetti={showConfetti}>
      SuccessAnimation
    </div>
  ),
}));

describe('SuccessOverlay', () => {
  /**
   * 测试1: show为true时显示overlay
   * 验证show为true时组件正确渲染
   */
  it('应该在show为true时显示overlay', () => {
    render(<SuccessOverlay show={true} />);

    expect(screen.getByTestId('success-animation')).toBeInTheDocument();
    expect(screen.getByText('生成成功！')).toBeInTheDocument();
  });

  /**
   * 测试2: show为false时隐藏overlay
   * 验证show为false时组件返回null
   */
  it('应该在show为false时隐藏overlay', () => {
    const { container } = render(<SuccessOverlay show={false} />);

    expect(container.firstChild).toBeNull();
    expect(screen.queryByTestId('success-animation')).not.toBeInTheDocument();
  });

  /**
   * 测试3: 成功动画渲染
   * 验证SuccessAnimation组件正确渲染并传递props
   */
  it('应该正确渲染SuccessAnimation组件', () => {
    render(<SuccessOverlay show={true} />);

    const animation = screen.getByTestId('success-animation');
    expect(animation).toHaveAttribute('data-size', '96');
    expect(animation).toHaveAttribute('data-confetti', 'true');
  });

  /**
   * 测试4: 成功文字显示
   * 验证"生成成功！"文字正确显示
   */
  it('应该显示生成成功文字', () => {
    render(<SuccessOverlay show={true} />);

    expect(screen.getByText('生成成功！')).toBeInTheDocument();
  });

  /**
   * 测试5: 跳转提示显示
   * 验证跳转提示文字正确显示
   */
  it('应该显示跳转提示文字', () => {
    render(<SuccessOverlay show={true} />);

    expect(screen.getByText('正在跳转到向导页面...')).toBeInTheDocument();
  });

  /**
   * 测试6: 背景模糊效果
   * 验证背景模糊类名存在
   */
  it('应该应用背景模糊效果', () => {
    const { container } = render(<SuccessOverlay show={true} />);

    const overlay = container.querySelector('.backdrop-blur-sm');
    expect(overlay).toBeInTheDocument();
  });

  /**
   * 测试7: z-index层级
   * 验证overlay具有高z-index确保在最顶层
   */
  it('应该具有正确的z-index层级', () => {
    const { container } = render(<SuccessOverlay show={true} />);

    const overlay = container.querySelector('.z-50');
    expect(overlay).toBeInTheDocument();
  });

  /**
   * 测试8: 自定义className
   * 验证自定义className正确应用
   */
  it('应该正确应用自定义className', () => {
    const { container } = render(<SuccessOverlay show={true} className="custom-overlay" />);

    const overlay = container.querySelector('.custom-overlay');
    expect(overlay).toBeInTheDocument();
  });
});
