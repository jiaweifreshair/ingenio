/**
 * SmartWizard 防回归：分析阶段不应自动跳转到下一步
 *
 * 背景：
 * - 之前存在“只要最后一条 SSE 消息 progress=100，就自动切到原型确认”的逻辑
 * - 当 Step 1 完成时 progress 可能为 100，但全流程尚未结束，导致用户未确认就被动跳转
 *
 * 目标：
 * - 即便 routingResult 已返回、日志里出现 progress=100，也不应自动进入 PrototypeConfirmation
 * - 只能由用户显式确认技术方案（PlanDisplay/确认按钮）触发下一步
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import { SmartWizard } from '../smart-wizard';
import { LanguageProvider } from '@/contexts/LanguageContext';

// ========== Mocks ==========

const mockPush = vi.fn();
const mockToast = vi.fn();
const mockStartSession = vi.fn();
const mockRouteRequirement = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: mockToast }),
}));

vi.mock('@/hooks/use-interactive-analysis-sse', () => ({
  useInteractiveAnalysisSse: () => ({
    state: {
      sessionId: 'session-1',
      currentStep: 1,
      status: 'RUNNING',
      messages: [
        {
          step: 1,
          stepName: '需求语义解析',
          description: '正在解构您的自然语言需求...',
          status: 'COMPLETED',
          progress: 100,
          detail: 'mock completed',
          timestamp: new Date().toISOString(),
        },
      ],
      stepResults: {},
      error: null,
    },
    startSession: mockStartSession,
    confirmStep: vi.fn(),
    modifyStep: vi.fn(),
  }),
}));

vi.mock('@/components/ui/resizable-panels', () => ({
  ResizablePanels: ({ leftPanel, rightPanel }: { leftPanel: React.ReactNode; rightPanel: React.ReactNode }) => (
    <div>
      <div data-testid="mock-left-panel">{leftPanel}</div>
      <div data-testid="mock-right-panel">{rightPanel}</div>
    </div>
  ),
}));

vi.mock('@/components/prototype/interaction-panel', () => ({
  InteractionPanel: () => <div data-testid="mock-interaction-panel" />,
}));

vi.mock('@/components/analysis/AnalysisProgressPanel', () => ({
  AnalysisProgressPanel: ({
    finalResult,
  }: {
    finalResult?: { appSpecId?: string } | null;
  }) => (
    <div>
      <div data-testid="mock-analysis-progress-panel" />
      <div data-testid="mock-routing-appspec">{finalResult?.appSpecId || ''}</div>
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
    mockStartSession.mockReset();
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

  it('progress=100 时也不应自动进入原型确认', async () => {
    render(
      <LanguageProvider>
        <SmartWizard
          initialRequirement="创建一个安全事故管理应用，包含上报/审核/整改闭环/统计看板"
          onBack={() => {}}
        />
      </LanguageProvider>
    );

    // 等待路由结果写入（routingResult -> AnalysisProgressPanel.finalResult）
    await waitFor(() => {
      expect(screen.getByTestId('mock-routing-appspec').textContent).toBe('app-1');
    });

    // 预期：仍处于分析页（未出现原型确认组件）
    expect(screen.getByTestId('mock-analysis-progress-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('mock-prototype-confirmation')).not.toBeInTheDocument();

    // 等待一段时间：如果未来有人误加 setTimeout 自动切换，应在这里被捕获
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 1000));
    });

    expect(screen.queryByTestId('mock-prototype-confirmation')).not.toBeInTheDocument();
  });

  /**
   * 严格模式下的启动流程不应重复触发。
   *
   * 是什么：模拟 React.StrictMode 的双调用行为。
   * 做什么：确保 startSession / routeRequirement 只执行一次。
   * 为什么：避免开发态触发重复会话导致确认步骤错乱。
   */
  it('StrictMode 下仅触发一次会话启动', async () => {
    render(
      <React.StrictMode>
        <LanguageProvider>
          <SmartWizard
            initialRequirement="创建一个校园安全小卫士应用，包含扫描、分析、发现隐患与修复方案"
            onBack={() => {}}
          />
        </LanguageProvider>
      </React.StrictMode>
    );

    await waitFor(() => {
      expect(mockStartSession).toHaveBeenCalledTimes(1);
      expect(mockRouteRequirement).toHaveBeenCalledTimes(1);
    });
  });
});
