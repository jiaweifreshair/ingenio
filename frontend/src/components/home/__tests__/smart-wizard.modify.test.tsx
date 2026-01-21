/**
 * SmartWizard 方案修改能力单元测试
 *
 * 目标：
 * - 在“分析/方案评审”阶段允许用户增量修改需求（Chat/输入框）
 * - 修改时复用 appSpecId，避免新建 AppSpec 导致上下文断裂
 *
 * 说明：
 * - 本测试不依赖真实 SSE/后端，使用 Mock 保证稳定性
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SmartWizard } from '../smart-wizard';

// ========== Mocks ==========

/**
 * 路由请求参数结构（测试用途）
 */
type RouteRequirementCall = {
  appSpecId?: string;
  userRequirement?: string;
};

const mockPush = vi.fn();
const mockToast = vi.fn();
const mockStartAnalysis = vi.fn();
const mockRouteRequirement = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: mockToast }),
}));

vi.mock('@/hooks/use-analysis-sse', () => ({
  useAnalysisSse: () => ({
    connect: mockStartAnalysis,
    messages: [],
    isConnected: true,
    isCompleted: false,
    error: null,
  }),
}));

vi.mock('@/components/analysis/AnalysisProgressPanel', () => ({
  AnalysisProgressPanel: ({
    onModifyPlan,
    finalResult,
  }: {
    onModifyPlan?: (req: string) => void;
    finalResult?: { appSpecId?: string } | null;
  }) => (
    <div>
      <div data-testid="mock-routing-appspec">{finalResult?.appSpecId || ''}</div>
      <button
        type="button"
        data-testid="mock-plan-modify"
        onClick={() => onModifyPlan?.('增加需求：数据库改为 MongoDB')}
      >
        modify
      </button>
    </div>
  ),
}));

vi.mock('@/components/prototype/prototype-confirmation', () => ({
  PrototypeConfirmation: () => <div data-testid="mock-prototype-confirmation" />,
}));

vi.mock('@/components/g3/g3-console', () => ({
  G3Console: () => <div data-testid="mock-g3-console" />,
}));

vi.mock('@/lib/api/plan-routing', () => ({
  routeRequirement: (...args: unknown[]) => mockRouteRequirement(...args),
  confirmDesign: vi.fn(),
  updateAppSpecRequirement: vi.fn(),
}));

describe('SmartWizard', () => {
  beforeEach(() => {
    mockPush.mockReset();
    mockToast.mockReset();
    mockStartAnalysis.mockReset();
    mockRouteRequirement.mockReset();

    mockRouteRequirement.mockResolvedValue({
      appSpecId: 'app-1',
      intent: 'DESIGN_FROM_SCRATCH',
      confidence: 0.9,
      branch: 'DESIGN',
      matchedTemplateResults: [],
      styleVariants: [],
      prototypeGenerated: false,
      nextAction: '请选择您喜欢的设计风格',
      requiresUserConfirmation: true,
      metadata: {},
    });
  });

  it('修改方案时应复用 appSpecId 并重启分析', async () => {
    render(
      <SmartWizard
        initialRequirement="创建一个安全事故管理应用，包含上报/审核/整改闭环/统计看板"
        onBack={() => {}}
      />
    );

    // 初次启动：应触发一次分析 + 一次路由
    await waitFor(() => {
      expect(mockStartAnalysis.mock.calls.length).toBeGreaterThanOrEqual(1);
      expect(mockRouteRequirement.mock.calls.length).toBeGreaterThanOrEqual(1);
    });

    // 等待路由结果写入状态（SmartWizard 会把 routingResult 作为 finalResult 透传到 AnalysisProgressPanel）
    await waitFor(() => {
      expect(screen.getByTestId('mock-routing-appspec').textContent).toBe('app-1');
    });

    // 触发“方案修改”（增量修改）
    fireEvent.click(screen.getByTestId('mock-plan-modify'));

    await waitFor(() => {
      // 修改后应再次启动分析
      expect(mockStartAnalysis.mock.calls.length).toBeGreaterThanOrEqual(2);
      // 修改后应再次路由
      expect(mockRouteRequirement.mock.calls.length).toBeGreaterThanOrEqual(2);
    });

    // 修改后的路由：必须带上 appSpecId，保证复用同一个 AppSpec
    const lastCallArg = mockRouteRequirement.mock.calls[mockRouteRequirement.mock.calls.length - 1]?.[0] as RouteRequirementCall;
    expect(lastCallArg.appSpecId).toBe('app-1');
    expect(String(lastCallArg.userRequirement)).toContain('需求变更');
    expect(String(lastCallArg.userRequirement)).toContain('MongoDB');
  });
});
