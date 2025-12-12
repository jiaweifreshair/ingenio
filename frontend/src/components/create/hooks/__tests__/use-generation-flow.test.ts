/**
 * useGenerationFlow Hook单元测试
 *
 * 测试场景：
 * - 表单提交验证
 * - 分析流程启动
 * - 风格选择处理
 * - 取消风格选择
 * - 完整生成流程（成功）
 * - 错误处理
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useGenerationFlow } from '../use-generation-flow';
import { UNIAIX_MODELS, type UniaixModel } from '@/lib/api/uniaix';
import type { AnalysisProgressMessage } from '@/hooks/use-analysis-sse';

// Mock依赖
const mockPush = vi.fn();
const mockToast = vi.fn();
const mockConnect = vi.fn();
const mockGenerateAppSpec = vi.fn();

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

vi.mock('@/lib/api/generate', () => ({
  generateAppSpec: (...args: unknown[]) => mockGenerateAppSpec(...args),
  APIError: class APIError extends Error {},
}));

describe('useGenerationFlow', () => {
  let defaultProps: {
    requirement: string;
    selectedModel: UniaixModel;
    setLoading: ReturnType<typeof vi.fn>;
    setShowAnalysis: ReturnType<typeof vi.fn>;
    setCurrentPhase: ReturnType<typeof vi.fn>;
    setShowSuccess: ReturnType<typeof vi.fn>;
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
    };

    // 清空所有mock
    mockPush.mockClear();
    mockToast.mockClear();
    mockConnect.mockClear();
    mockGenerateAppSpec.mockClear();

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
   * 验证表单提交后正确启动SSE分析
   */
  it('应该在表单提交成功后启动SSE分析', async () => {
    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    const mockEvent = { preventDefault: vi.fn() } as unknown as React.FormEvent;

    await act(async () => {
      await result.current.handleFormSubmit(mockEvent);
    });

    // 等待异步操作完成
    await waitFor(() => {
      expect(mockConnect).toHaveBeenCalled();
    });

    expect(defaultProps.setLoading).toHaveBeenCalledWith(true);
    expect(defaultProps.setShowAnalysis).toHaveBeenCalledWith(true);
    expect(defaultProps.setCurrentPhase).toHaveBeenCalledWith('analyzing');
  });

  /**
   * 测试4: 风格选择处理
   * 验证用户选择风格后切换到生成阶段
   */
  it('应该在用户选择风格后切换到生成阶段', async () => {
    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    // Mock成功的API响应
    mockGenerateAppSpec.mockResolvedValue({
      success: true,
      data: { appSpecId: 'test-app-spec-123' },
    });

    await act(async () => {
      result.current.handleStyleSelected('现代极简');
    });

    expect(defaultProps.setCurrentPhase).toHaveBeenCalledWith('generating');
  });

  /**
   * 测试5: 取消风格选择
   * 验证取消风格选择后返回idle状态
   */
  it('应该在取消风格选择后返回idle状态', () => {
    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    act(() => {
      result.current.handleStyleCancel();
    });

    expect(defaultProps.setLoading).toHaveBeenCalledWith(false);
    expect(defaultProps.setShowAnalysis).toHaveBeenCalledWith(false);
    expect(defaultProps.setCurrentPhase).toHaveBeenCalledWith('idle');
  });

  /**
   * 测试6: 完整生成流程（成功）
   * 验证生成成功后显示成功动画并导航
   */
  it('应该在生成成功后显示成功动画并导航', async () => {
    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    // Mock成功的API响应
    mockGenerateAppSpec.mockResolvedValue({
      success: true,
      data: { appSpecId: 'test-app-spec-123' },
    });

    await act(async () => {
      result.current.handleStyleSelected('现代极简');
    });

    // 等待异步操作完成
    await waitFor(() => {
      expect(defaultProps.setCurrentPhase).toHaveBeenCalledWith('navigating');
    });

    expect(defaultProps.setShowSuccess).toHaveBeenCalledWith(true);
    expect(defaultProps.setLoading).toHaveBeenCalledWith(false);
    expect(mockToast).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '生成成功',
      })
    );
  });

  /**
   * 测试7: 生成失败处理（API错误）
   * 验证API返回错误时显示错误提示
   */
  it('应该在API返回错误时显示错误提示', async () => {
    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    // Mock失败的API响应
    mockGenerateAppSpec.mockResolvedValue({
      success: false,
      error: '服务器错误',
    });

    await act(async () => {
      result.current.handleStyleSelected('现代极简');
    });

    // 等待异步操作完成
    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: '生成失败',
          description: '服务器错误',
          variant: 'destructive',
        })
      );
    });

    expect(defaultProps.setLoading).toHaveBeenCalledWith(false);
    expect(defaultProps.setShowAnalysis).toHaveBeenCalledWith(false);
    expect(defaultProps.setCurrentPhase).toHaveBeenCalledWith('idle');
  });

  /**
   * 测试8: 生成失败处理（appSpecId为空）
   * 验证appSpecId为空时显示错误提示
   */
  it('应该在appSpecId为空时显示错误提示', async () => {
    const { result } = renderHook(() => useGenerationFlow(defaultProps));

    // Mock返回空appSpecId的响应
    mockGenerateAppSpec.mockResolvedValue({
      success: true,
      data: { appSpecId: '' },
    });

    await act(async () => {
      result.current.handleStyleSelected('现代极简');
    });

    // 等待异步操作完成
    await waitFor(() => {
      expect(mockToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: '生成失败',
          description: expect.stringContaining('未返回有效的AppSpec ID'),
          variant: 'destructive',
        })
      );
    });

    expect(defaultProps.setLoading).toHaveBeenCalledWith(false);
    expect(defaultProps.setCurrentPhase).toHaveBeenCalledWith('idle');
  });

  /**
   * 测试9: SSE分析状态同步
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
