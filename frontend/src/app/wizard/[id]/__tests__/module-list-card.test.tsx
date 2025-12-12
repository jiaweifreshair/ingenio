/**
 * ModuleListCard组件测试
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ModuleListCard, type Module } from '../components/module-list-card';

describe('ModuleListCard', () => {
  const mockModules: Module[] = [
    {
      name: '用户管理模块',
      description: '处理用户注册、登录、权限管理等功能',
      priority: 'high',
      complexity: 3,
      pages: ['LoginPage', 'RegisterPage'],
    },
    {
      name: '任务管理模块',
      description: '创建任务、分配任务、跟踪进度',
      priority: 'medium',
      complexity: 4,
      pages: ['TaskListPage', 'TaskDetailPage'],
    },
    {
      name: '数据分析模块',
      description: '统计报表、数据可视化',
      priority: 'low',
      complexity: 2,
      pages: ['DashboardPage'],
    },
  ];

  it('应该正确渲染卡片标题和模块数量', () => {
    render(<ModuleListCard modules={mockModules} />);

    expect(screen.getByText(`功能模块 (${mockModules.length})`)).toBeInTheDocument();
  });

  it('应该正确渲染所有模块名称', () => {
    render(<ModuleListCard modules={mockModules} />);

    expect(screen.getByText('用户管理模块')).toBeInTheDocument();
    expect(screen.getByText('任务管理模块')).toBeInTheDocument();
    expect(screen.getByText('数据分析模块')).toBeInTheDocument();
  });

  it('应该正确渲染所有模块描述', () => {
    render(<ModuleListCard modules={mockModules} />);

    expect(
      screen.getByText('处理用户注册、登录、权限管理等功能')
    ).toBeInTheDocument();
    expect(
      screen.getByText('创建任务、分配任务、跟踪进度')
    ).toBeInTheDocument();
    expect(screen.getByText('统计报表、数据可视化')).toBeInTheDocument();
  });

  it('应该正确显示模块复杂度', () => {
    render(<ModuleListCard modules={mockModules} />);

    expect(screen.getByText('复杂度 3')).toBeInTheDocument();
    expect(screen.getByText('复杂度 4')).toBeInTheDocument();
    expect(screen.getByText('复杂度 2')).toBeInTheDocument();
  });

  it('应该正确显示高优先级Badge', () => {
    render(<ModuleListCard modules={mockModules} />);

    expect(screen.getByText('高优先级')).toBeInTheDocument();
  });

  it('应该正确显示中优先级Badge', () => {
    render(<ModuleListCard modules={mockModules} />);

    expect(screen.getByText('中优先级')).toBeInTheDocument();
  });

  it('应该正确显示低优先级Badge', () => {
    render(<ModuleListCard modules={mockModules} />);

    expect(screen.getByText('低优先级')).toBeInTheDocument();
  });

  it('应该在modules为空数组时返回null', () => {
    const { container } = render(<ModuleListCard modules={[]} />);

    expect(container.firstChild).toBeNull();
  });

  it('应该正确渲染FileText图标', () => {
    const { container } = render(<ModuleListCard modules={mockModules} />);

    const icons = container.querySelectorAll('svg');
    expect(icons.length).toBeGreaterThan(0);
  });

  it('应该使用正确的布局类', () => {
    const { container } = render(<ModuleListCard modules={mockModules} />);

    const grid = container.querySelector('.grid');
    expect(grid).toBeInTheDocument();
    expect(grid).toHaveClass('gap-2');
  });

  it('应该为每个模块渲染独立的卡片项', () => {
    const { container } = render(<ModuleListCard modules={mockModules} />);

    const moduleItems = container.querySelectorAll('.bg-muted');
    expect(moduleItems.length).toBe(mockModules.length);
  });
});
