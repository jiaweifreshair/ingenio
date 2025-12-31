/**
 * useGenerationFlow Hook单元测试
 *
 * 测试场景：
 * - 表单提交验证
 * - 分析流程启动（SSE + V2路由API）
 * - 取消返回处理
 * - 确认设计并生成代码
 *
 * V2.0简化版：使用V2 API链路
 *
 * @author Ingenio Team
 * @since V2.0
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useGenerationFlow } from '../use-generation-flow';
import { UNIAIX_MODELS, type UniaixModel } from '@/lib/api/uniaix';
import type { AnalysisProgressMessage } from '@/hooks/use-analysis-sse';
import type { PlanRoutingResult, RoutingBranch } from '@/lib/api/plan-routing';
import { RequirementIntent } from '@/types/intent';

// Mock依赖
const mockPush = vi.fn();
const mockToast = vi.fn();
const mockConnect = vi.fn();
const mockRouteRequirement = vi.fn();
const mockSelectTemplate = vi.fn();
const mockConfirmDesign = vi.fn();
const mockExecuteCodeGeneration = vi.fn();

const mockAnalysisSse = {
  messages: [] as AnalysisProgressMessage[],
  isConnected: false,
  isCompleted: false,
  error: null as string | null,
  connect: mockConnect,
};

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

vi.mock('@/hooks/use-toast', () => ({
  useToast: () => ({ toast: mockToast }),
}));

vi.mock('@/hooks/use-analysis-sse', () => ({
  useAnalysisSse: () => mockAnalysisSse,
}));

vi.mock('@/lib/api/plan-routing', () => ({
  routeRequirement: (...args: unknown[]) => mockRouteRequirement(...args),
  selectTemplate: (...args: unknown[]) => mockSelectTemplate(...args),
  confirmDesign: (...args: unknown[]) => mockConfirmDesign(...args),
  executeCodeGeneration: (...args: unknown[]) => mockExecuteCodeGeneration(...args),
}));

describe('useGenerationFlow', () => {
  let defaultProps: {
    requirement: string;
    selectedModel: UniaixModel;
    setLoading: ReturnType<typeof vi.fn>;
    setShowAnalysis: ReturnType<typeof vi.fn>;
    setCurrentPhase: ReturnType<typeof vi.fn>;
    setShowSuccess: ReturnType<typeof vi.fn>;
    routingResult?: PlanRoutingResult | null;
    setRoutingResult?: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    // 重新创建props以避免mock污染
    // 注意：requirement长度必须≥10字符
    defaultProps = {
      requirement: '创建一个功能完整的电商平台',
      selectedModel: UNIAIX_MODELS.QWEN_MAX,
      setLoading: vi.fn(),
      setShowAnalysis: vi.fn(),
      setCurrentPhase: vi.fn(),
      setShowSuccess: vi.fn(),
      routingResult: null,
      setRoutingResult: vi.fn(),
    };

    // 清空所有mock
    mockPush.mockClear();
    mockToast.mockClear();
    mockConnect.mockClear();
    mockRouteRequirement.mockClear();
    mockSelectTemplate.mockClear();
    mockConfirmDesign.mockClear();
    mockExecuteCodeGeneration.mockClear();

    // 重置mockAnalysisSse
    mockAnalysisSse.messages = [];
    mockAnalysisSse.isConnected = false;
    mockAnalysisSse.isCompleted = false;
    mockAnalysisSse.error = null;
  });

  /**
   * 测试1: 表单提交验证（空需求）
   * 验证空需求时显示错误提示
   */
  it('应该在需求为空时显示验证错误', async () => {
    const { result } = renderHook(() =>
      useGenerationFlow({
        ...defaultProps,
        requirement: '',
      })
    );

    const mockEvent = { preventDefault: vi.fn() } as unknown as React.FormEvent;

    await act(async () => {
      await result.current.handleFormSubmit(mockEvent);
    });

    expect(mockToast).toHaveBeenCalledWith({
      title: '验证错误',
      description: '需求描述不能为空',
      variant: 'destructive',
    });
    expect(mockConnect).not.toHaveBeenCalled();
  });

  /**
   * 测试2: 表单提交验证（需求太短）
   * 验证需求长度不足10字符时显示错误提示
   */
  it('应该在需求太短时显示验证错误', async () => {
    const { result } = renderHook(() =>
      useGenerationFlow({
        ...defaultProps,
        requirement: '短需求',
      })
    );

    const mockEvent = { preventDefault: vi.fn() } as unknown as React.FormEvent;

    await act(async () => {
      await result.current.handleFormSubmit(mockEvent);
    });

    expect(mockToast).toHaveBeenCalledWith({
      title: '验证错误',
      description: '需求描述至少需要10个字符',
      variant: 'destructive',
    });
    expect(mockConnect).not.toHaveBeenCalled();
  });

  /**
   * 测试3: 表单提交成功启动分析
   * 验证表单提交后正确启动SSE分析和V2路由API
   */
  it('应该在表单提交成功后启动SSE分析和V2路由API', async () => {
    mockRouteRequirement.mockResolvedValue({
      appSpecId: 'test-app-spec-123',
      intent: 'design',
    });

    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    const mockEvent = { preventDefault: vi.fn() } as unknown as React.FormEvent;

    await act(async () => {
      await result.current.handleFormSubmit(mockEvent);
    });

    // 等待异步操作完成
    await waitFor(() => {
      expect(mockConnect).toHaveBeenCalled();
      expect(mockRouteRequirement).toHaveBeenCalledWith({
        userRequirement: '创建一个功能完整的电商平台',
      });
    });

    expect(defaultProps.setLoading).toHaveBeenCalledWith(true);
    expect(defaultProps.setShowAnalysis).toHaveBeenCalledWith(true);
    expect(defaultProps.setCurrentPhase).toHaveBeenCalledWith('analyzing');
  });

  /**
   * 测试4: 取消返回处理
   * 验证取消后返回idle状态
   */
  it('应该在取消后返回idle状态', () => {
    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    act(() => {
      result.current.handleStyleCancel();
    });

    expect(defaultProps.setLoading).toHaveBeenCalledWith(false);
    expect(defaultProps.setShowAnalysis).toHaveBeenCalledWith(false);
    expect(defaultProps.setCurrentPhase).toHaveBeenCalledWith('idle');
    expect(defaultProps.setRoutingResult).toHaveBeenCalledWith(null);
  });

  /**
   * 测试5: 确认设计并生成代码（成功）
   * 验证确认设计后调用代码生成API并导航
   */
  it('应该在确认设计后成功生成代码并导航', async () => {
    const propsWithResult = {
      ...defaultProps,
      routingResult: {
        appSpecId: 'test-app-spec-123',
        intent: RequirementIntent.DESIGN_FROM_SCRATCH,
        confidence: 0.95,
        branch: 'DESIGN' as RoutingBranch,
        prototypeGenerated: true,
        nextAction: 'confirm_design',
        requiresUserConfirmation: true,
      },
    };

    mockConfirmDesign.mockResolvedValue({
      success: true,
      canProceedToExecute: true,
    });

    mockExecuteCodeGeneration.mockResolvedValue({
      success: true,
    });

    const { result } = renderHook(() => useGenerationFlow(propsWithResult));

    await act(async () => {
      await result.current.handleConfirmDesign();
    });

    await waitFor(() => {
      expect(mockConfirmDesign).toHaveBeenCalledWith('test-app-spec-123');
      expect(mockExecuteCodeGeneration).toHaveBeenCalledWith('test-app-spec-123');
    });

    expect(defaultProps.setShowSuccess).toHaveBeenCalledWith(true);
    expect(mockToast).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '生成成功',
      })
    );
  });

  /**
   * 测试6: 确认设计失败处理
   * 验证设计确认失败时显示错误提示
   */
  it('应该在设计确认失败时显示错误提示', async () => {
    const propsWithResult = {
      ...defaultProps,
      routingResult: {
        appSpecId: 'test-app-spec-123',
        intent: RequirementIntent.DESIGN_FROM_SCRATCH,
        confidence: 0.95,
        branch: 'DESIGN' as RoutingBranch,
        prototypeGenerated: true,
        nextAction: 'confirm_design',
        requiresUserConfirmation: true,
      },
    };

    mockConfirmDesign.mockResolvedValue({
      success: false,
      message: '设计确认失败',
    });

    const { result } = renderHook(() => useGenerationFlow(propsWithResult));

    await act(async () => {
      await result.current.handleConfirmDesign();
    });

    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: '生成失败',
          variant: 'destructive',
        })
      );
    });

    expect(defaultProps.setLoading).toHaveBeenCalledWith(false);
  });

  /**
   * 测试7: 无routingResult时显示错误
   * 验证没有routingResult时显示错误提示
   */
  it('应该在没有routingResult时显示错误提示', async () => {
    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    await act(async () => {
      await result.current.handleConfirmDesign();
    });

    expect(mockToast).toHaveBeenCalledWith({
      title: '错误',
      description: 'AppSpec ID 丢失，请重新开始',
      variant: 'destructive',
    });
  });

  /**
   * 测试8: SSE分析状态同步
   * 验证SSE分析状态正确暴露给组件
   */
  it('应该正确暴露SSE分析状态', () => {
    mockAnalysisSse.messages = [
      {
        type: 'phase',
        step: 1,
        stepName: 'Intent Analysis',
        description: 'Analyzing user intent...',
        status: 'RUNNING',
        progress: 20,
        percentage: 20,
        details: ['Analyzing keywords...'],
        timestamp: new Date().toISOString()
      } as AnalysisProgressMessage,
    ];
    mockAnalysisSse.isConnected = true;
    mockAnalysisSse.isCompleted = false;

    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    expect(result.current.messages).toHaveLength(1);
    expect(result.current.isConnected).toBe(true);
    expect(result.current.isCompleted).toBe(false);
    expect(result.current.analysisError).toBeNull();
  });
});
