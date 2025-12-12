/**
 * TemplateLoadedBanner组件单元测试
 *
 * 测试场景：
 * - Banner渲染
 * - 模板名称显示
 * - 说明文字显示
 * - 更换模板链接
 * - 清除按钮功能
 * - 绿色主题样式
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TemplateLoadedBanner } from '../template-loaded-banner';

describe('TemplateLoadedBanner', () => {
  const mockTemplate = {
    id: 'template-1',
    name: '电商平台模板',
    description: '适合电商平台的模板',
    content: '模板内容...',
  };

  /**
   * 测试1: Banner渲染
   * 验证Banner正确渲染
   */
  it('应该正确渲染Banner', () => {
    const onClear = vi.fn();

    render(<TemplateLoadedBanner template={mockTemplate} onClear={onClear} />);

    expect(screen.getByText('使用模板')).toBeInTheDocument();
  });

  /**
   * 测试2: 模板名称显示
   * 验证模板名称在Badge中正确显示
   */
  it('应该在Badge中显示模板名称', () => {
    const onClear = vi.fn();

    render(<TemplateLoadedBanner template={mockTemplate} onClear={onClear} />);

    expect(screen.getByText('电商平台模板')).toBeInTheDocument();
  });

  /**
   * 测试3: 说明文字显示
   * 验证说明文字正确显示
   */
  it('应该显示说明文字', () => {
    const onClear = vi.fn();

    render(<TemplateLoadedBanner template={mockTemplate} onClear={onClear} />);

    expect(
      screen.getByText(/已自动填充模板内容，您可以继续编辑或直接提交/)
    ).toBeInTheDocument();
  });

  /**
   * 测试4: 更换模板链接
   * 验证更换模板按钮渲染并链接正确
   */
  it('应该渲染更换模板链接', () => {
    const onClear = vi.fn();

    render(<TemplateLoadedBanner template={mockTemplate} onClear={onClear} />);

    const link = screen.getByRole('link', { name: /更换模板/ });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/templates');
  });

  /**
   * 测试5: 清除按钮功能
   * 验证点击清除按钮触发onClear回调
   */
  it('应该在点击清除按钮时触发onClear回调', async () => {
    const user = userEvent.setup();
    const onClear = vi.fn();

    render(
      <TemplateLoadedBanner template={mockTemplate} onClear={onClear} />
    );

    // 找到所有按钮
    const buttons = screen.getAllByRole('button');
    // 清除按钮是第二个按钮（更换模板后面的）
    const clearButton = buttons[buttons.length - 1];

    await user.click(clearButton);
    expect(onClear).toHaveBeenCalledTimes(1);
  });

  /**
   * 测试6: 绿色主题样式
   * 验证Banner使用绿色主题
   */
  it('应该使用绿色主题样式', () => {
    const onClear = vi.fn();
    const { container } = render(
      <TemplateLoadedBanner template={mockTemplate} onClear={onClear} />
    );

    // 验证绿色边框类名
    const banner = container.querySelector('.border-green-200');
    expect(banner).toBeInTheDocument();
  });

  /**
   * 测试7: 自定义className
   * 验证自定义className正确应用
   */
  it('应该正确应用自定义className', () => {
    const onClear = vi.fn();
    const { container } = render(
      <TemplateLoadedBanner
        template={mockTemplate}
        onClear={onClear}
        className="custom-banner"
      />
    );

    const banner = container.querySelector('.custom-banner');
    expect(banner).toBeInTheDocument();
  });
});
