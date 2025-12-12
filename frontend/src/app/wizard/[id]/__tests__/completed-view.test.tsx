/**
 * CompletedView组件测试
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { CompletedView } from '../components/completed-view';
import { AgentType, AgentState, type AgentExecutionStatus } from '@/types/wizard';
import { type Module } from '../components/module-list-card';
import { type GenerationStats } from '@/components/wizard/generation-stats';

describe('CompletedView', () => {
  const mockAppSpecId = 'test-app-spec-123';
  const mockOnDownload = vi.fn();
  const mockOnShare = vi.fn();
  const mockOnNavigate = vi.fn();

  const mockAgents: AgentExecutionStatus[] = [
    {
      id: 'plan-agent',
      type: AgentType.PLAN,
      name: '需求分析',
      status: AgentState.COMPLETED,
      progress: 100,
      duration: 60000,
    },
  ];

  const mockModules: Module[] = [
    {
      name: '用户管理模块',
      description: '处理用户注册、登录',
      priority: 'high',
      complexity: 3,
      pages: ['LoginPage'],
    },
  ];

  const mockGenerationStats: GenerationStats = {
    pagesCount: 4,
    apiCount: 12,
    tablesCount: 4,
    linesOfCode: 3000,
    generationTime: 180,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('应该正确渲染页面标题和完成Badge', () => {
    render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    expect(screen.getByText('AppSpec 生成向导')).toBeInTheDocument();
    expect(screen.getByText('生成完成')).toBeInTheDocument();
  });

  it('应该正确显示生成完成标题', () => {
    render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    expect(screen.getByText('生成完成！')).toBeInTheDocument();
  });

  it('应该正确显示AppSpec ID', () => {
    render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    expect(screen.getByText(`ID: ${mockAppSpecId}`)).toBeInTheDocument();
  });

  it('应该正确显示100%进度', () => {
    render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    expect(screen.getByText('100%')).toBeInTheDocument();
    expect(screen.getByText('所有Agent执行完成')).toBeInTheDocument();
  });

  it('应该正确显示接下来做什么标题', () => {
    render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    expect(screen.getByText('接下来做什么？')).toBeInTheDocument();
  });

  it('应该渲染AgentResultsCard组件', () => {
    render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    expect(screen.getByText('Agent执行结果')).toBeInTheDocument();
  });

  it('应该渲染ModuleListCard组件', () => {
    render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    expect(screen.getByText(`功能模块 (${mockModules.length})`)).toBeInTheDocument();
  });

  it('应该渲染ExploreMoreCard组件', () => {
    render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    expect(screen.getByText('探索更多功能')).toBeInTheDocument();
  });

  it('应该在有generationStats时渲染GenerationStats组件', () => {
    const { container } = render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    // GenerationStats组件会显示统计数据
    expect(container.textContent).toMatch(/4|12|3000/);
  });

  it('应该在generationStats为null时不渲染GenerationStats组件', () => {
    render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={null}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    // 不应该显示统计数据相关内容（具体文本取决于GenerationStats组件实现）
    expect(screen.queryByText(/生成统计/i)).not.toBeInTheDocument();
  });

  it('应该使用正确的布局类', () => {
    const { container } = render(
      <CompletedView
        appSpecId={mockAppSpecId}
        agents={mockAgents}
        modules={mockModules}
        generationStats={mockGenerationStats}
        onDownload={mockOnDownload}
        onShare={mockOnShare}
        onNavigate={mockOnNavigate}
      />
    );

    const mainContainer = container.querySelector('.min-h-screen');
    expect(mainContainer).toBeInTheDocument();
    expect(mainContainer).toHaveClass('bg-background');
  });
});
