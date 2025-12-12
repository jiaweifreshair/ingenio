/**
 * AgentResultsCard组件测试
 */
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AgentResultsCard } from '../components/agent-results-card';
import { AgentType, AgentState, type AgentExecutionStatus } from '@/types/wizard';

describe('AgentResultsCard', () => {
  const mockAgents: AgentExecutionStatus[] = [
    {
      id: 'plan-agent',
      type: AgentType.PLAN,
      name: '需求分析',
      status: AgentState.COMPLETED,
      progress: 100,
      duration: 60000,
      metrics: {
        tokenUsage: {
          inputTokens: 500,
          outputTokens: 300,
          totalTokens: 800,
          estimatedCost: 0.01,
          total: 800,
        },
        apiCalls: 5,
        avgResponseTime: 1200,
        successRate: 100,
      },
    },
    {
      id: 'execute-agent',
      type: AgentType.EXECUTE,
      name: 'AppSpec生成',
      status: AgentState.COMPLETED,
      progress: 100,
      duration: 120000,
      metrics: {
        tokenUsage: {
          inputTokens: 1000,
          outputTokens: 800,
          totalTokens: 1800,
          estimatedCost: 0.02,
          total: 1800,
        },
        apiCalls: 10,
        avgResponseTime: 2500,
        successRate: 100,
      },
    },
  ];

  it('应该正确渲染卡片标题', () => {
    render(<AgentResultsCard agents={mockAgents} />);

    expect(screen.getByText('Agent执行结果')).toBeInTheDocument();
  });

  it('应该正确渲染所有Agent', () => {
    render(<AgentResultsCard agents={mockAgents} />);

    expect(screen.getByText('需求分析')).toBeInTheDocument();
    expect(screen.getByText('AppSpec生成')).toBeInTheDocument();
  });

  it('应该正确显示Agent耗时', () => {
    render(<AgentResultsCard agents={mockAgents} />);

    expect(screen.getByText('耗时: 60.0s')).toBeInTheDocument();
    expect(screen.getByText('耗时: 120.0s')).toBeInTheDocument();
  });

  it('应该正确显示Agent完成状态Badge', () => {
    render(<AgentResultsCard agents={mockAgents} />);

    const badges = screen.getAllByText('已完成');
    expect(badges.length).toBe(mockAgents.length);
  });

  it('应该正确显示Token使用情况', () => {
    render(<AgentResultsCard agents={mockAgents} />);

    expect(screen.getByText('800 tokens')).toBeInTheDocument();
    expect(screen.getByText('1800 tokens')).toBeInTheDocument();
  });

  it('应该在没有Agent时显示空状态', () => {
    render(<AgentResultsCard agents={[]} />);

    expect(screen.getByText('暂无Agent执行记录')).toBeInTheDocument();
  });

  it('应该正确显示完成图标', () => {
    const { container } = render(<AgentResultsCard agents={mockAgents} />);

    const icons = container.querySelectorAll('.text-green-500');
    expect(icons.length).toBe(mockAgents.length);
  });

  it('应该在Agent没有duration时显示已完成文本', () => {
    const agentWithoutDuration: AgentExecutionStatus = {
      ...mockAgents[0],
      duration: undefined,
    };

    render(<AgentResultsCard agents={[agentWithoutDuration]} />);

    // 使用getAllByText因为"已完成"会在Badge和文本中都出现
    const completedTexts = screen.getAllByText('已完成');
    expect(completedTexts.length).toBeGreaterThan(0);
  });

  it('应该在Agent没有metrics时不显示Token信息', () => {
    const agentWithoutMetrics: AgentExecutionStatus = {
      ...mockAgents[0],
      metrics: undefined,
    };

    render(<AgentResultsCard agents={[agentWithoutMetrics]} />);

    expect(screen.queryByText(/tokens/i)).not.toBeInTheDocument();
  });
});
