/**
 * TemplateGallery组件单元测试
 *
 * 测试场景：
 * - 模板卡片列表渲染
 * - 标题和提示文字显示
 * - 模板点击触发回调
 * - 浏览全部模板链接
 * - 空模板列表处理
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TemplateGallery } from '../template-gallery';

// Mock子组件
vi.mock('@/components/ui/scroll-area', () => ({
  ScrollArea: ({ children, className }: { children: React.ReactNode; className?: string }) => (
    <div className={className}>{children}</div>
  ),
  ScrollBar: () => <div>ScrollBar</div>,
}));

vi.mock('@/components/home/template-card', () => ({
  TemplateCard: ({
    id,
    title,
    onClick,
  }: {
    id: string;
    title: string;
    onClick: (data: { id: string; title: string; description: string }) => void;
  }) => (
    <button
      data-testid={`template-card-${id}`}
      onClick={() => onClick({ id, title, description: 'test description' })}
    >
      {title}
    </button>
  ),
}));

describe('TemplateGallery', () => {
  const mockTemplates = [
    {
      id: 'template-1',
      title: '电商模版',
      description: '这是一个电商模版',
      icon: 'shopping-cart',
      color: 'from-blue-500 to-cyan-500',
    },
    {
      id: 'template-2',
      title: '博客模版',
      description: '这是一个博客模版',
      icon: 'file-text',
      color: 'from-green-500 to-emerald-500',
    },
  ];

  /**
   * 测试1: 模板卡片列表渲染
   * 验证所有模板卡片正确渲染
   */
  it('应该渲染所有模板卡片', () => {
    const onTemplateClick = vi.fn();

    render(
      <TemplateGallery templates={mockTemplates} onTemplateClick={onTemplateClick} />
    );

    expect(screen.getByTestId('template-card-template-1')).toBeInTheDocument();
    expect(screen.getByTestId('template-card-template-2')).toBeInTheDocument();
    expect(screen.getByTestId('template-card-template-3')).toBeInTheDocument();
  });

  /**
   * 测试2: 标题显示
   * 验证"快速模板"标题正确显示
   */
  it('应该显示快速模板标题', () => {
    const onTemplateClick = vi.fn();

    render(
      <TemplateGallery templates={mockTemplates} onTemplateClick={onTemplateClick} />
    );

    expect(screen.getByText('快速模板')).toBeInTheDocument();
  });

  /**
   * 测试3: 提示文字显示
   * 验证"点击填充"提示文字显示
   */
  it('应该显示点击提示文字', () => {
    const onTemplateClick = vi.fn();

    render(
      <TemplateGallery templates={mockTemplates} onTemplateClick={onTemplateClick} />
    );

    expect(screen.getByText(/点击填充/)).toBeInTheDocument();
  });

  /**
   * 测试4: 模板点击回调
   * 验证点击模板触发onTemplateClick回调
   */
  it('应该在点击模板时触发onTemplateClick回调', async () => {
    const user = userEvent.setup();
    const onTemplateClick = vi.fn();

    render(
      <TemplateGallery templates={mockTemplates} onTemplateClick={onTemplateClick} />
    );

    const templateCard = screen.getByTestId('template-card-template-1');
    await user.click(templateCard);

    expect(onTemplateClick).toHaveBeenCalledTimes(1);
    expect(onTemplateClick).toHaveBeenCalledWith({
      id: 'template-1',
      title: '电商平台模板',
      description: 'test description',
    });
  });

  /**
   * 测试5: 浏览全部模板链接
   * 验证"浏览全部模板"链接渲染并指向正确路径
   */
  it('应该渲染浏览全部模板链接', () => {
    const onTemplateClick = vi.fn();

    render(
      <TemplateGallery templates={mockTemplates} onTemplateClick={onTemplateClick} />
    );

    const link = screen.getByRole('link', { name: /浏览全部模板/ });
    expect(link).toBeInTheDocument();
    expect(link).toHaveAttribute('href', '/templates');
  });

  /**
   * 测试6: 空模板列表
   * 验证空模板列表时组件仍然正常渲染
   */
  it('应该在空模板列表时正常渲染', () => {
    const onTemplateClick = vi.fn();

    render(<TemplateGallery templates={[]} onTemplateClick={onTemplateClick} />);

    expect(screen.getByText('快速模板')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /浏览全部模板/ })).toBeInTheDocument();
  });

  /**
   * 测试7: 自定义className
   * 验证自定义className正确应用
   */
  it('应该正确应用自定义className', () => {
    const onTemplateClick = vi.fn();
    const { container } = render(
      <TemplateGallery
        templates={mockTemplates}
        onTemplateClick={onTemplateClick}
        className="custom-gallery"
      />
    );

    const gallery = container.querySelector('.custom-gallery');
    expect(gallery).toBeInTheDocument();
  });
});
