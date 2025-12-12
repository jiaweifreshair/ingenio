/**
 * AnalysisPanel组件单元测试
 *
 * 测试场景：
 * - 左右分屏布局渲染
 * - 需求文本显示
 * - AI模型信息显示
 * - 选中风格显示
 * - 分析阶段显示AnalysisProgressPanel
 * - 风格选择阶段显示StylePicker
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AnalysisPanel } from '../analysis-panel';
import { UNIAIX_MODELS } from '@/lib/api/uniaix';

// Mock子组件
vi.mock('@/components/analysis/AnalysisProgressPanel', () => ({
  AnalysisProgressPanel: ({ messages }: { messages: unknown[] }) => (
    <div data-testid="analysis-progress-panel">
      AnalysisProgressPanel (messages: {messages.length})
    </div>
  ),
}));

vi.mock('@/components/design/style-picker', () => ({
  StylePicker: ({ userRequirement }: { userRequirement: string }) => (
    <div data-testid="style-picker">
      StylePicker (requirement: {userRequirement})
    </div>
  ),
}));

describe('AnalysisPanel', () => {
  const defaultProps = {
    requirement: '创建一个电商平台',
    selectedModel: UNIAIX_MODELS.QWEN_MAX,
    selectedStyle: null,
    currentPhase: 'analyzing' as const,
    messages: [],
    isConnected: true,
    isCompleted: false,
    analysisError: null,
    onStyleSelected: vi.fn(),
    onStyleCancel: vi.fn(),
  };

  /**
   * 测试1: 基础渲染
   * 验证组件正确渲染左右分屏布局
   */
  it('应该正确渲染左右分屏布局', () => {
    render(<AnalysisPanel {...defaultProps} />);

    // 验证需求展示区域
    expect(screen.getByText('你的需求')).toBeInTheDocument();
    expect(screen.getByText('创建一个电商平台')).toBeInTheDocument();

    // 验证配置信息区域
    expect(screen.getByText(/AI模型：/)).toBeInTheDocument();
  });

  /**
   * 测试2: 需求文本显示
   * 验证用户输入的需求正确显示
   */
  it('应该正确显示需求文本', () => {
    const longRequirement = '创建一个功能完整的电商平台，支持商品管理、购物车、订单处理等功能';

    render(<AnalysisPanel {...defaultProps} requirement={longRequirement} />);

    expect(screen.getByText(longRequirement)).toBeInTheDocument();
  });

  /**
   * 测试3: AI模型信息显示
   * 验证选中的AI模型信息正确显示
   */
  it('应该正确显示AI模型信息', () => {
    render(<AnalysisPanel {...defaultProps} selectedModel={UNIAIX_MODELS.QWEN_MAX} />);

    // 验证模型名称包含在文本中
    const modelText = screen.getByText(/AI模型：/);
    expect(modelText).toBeInTheDocument();
  });

  /**
   * 测试4: 选中风格显示
   * 验证选中风格时正确显示风格信息
   */
  it('应该在有选中风格时显示风格信息', () => {
    render(<AnalysisPanel {...defaultProps} selectedStyle="现代极简" />);

    expect(screen.getByText(/选中风格：/)).toBeInTheDocument();
    expect(screen.getByText('现代极简')).toBeInTheDocument();
  });

  /**
   * 测试5: 未选中风格时不显示
   * 验证未选中风格时不显示风格信息
   */
  it('应该在未选中风格时不显示风格信息', () => {
    render(<AnalysisPanel {...defaultProps} selectedStyle={null} />);

    expect(screen.queryByText(/选中风格：/)).not.toBeInTheDocument();
  });

  /**
   * 测试6: 分析阶段显示AnalysisProgressPanel
   * 验证分析阶段显示分析进度面板
   */
  it('应该在analyzing阶段显示AnalysisProgressPanel', () => {
    const messages = [
      {
        type: 'phase' as const,
        step: 1,
        stepName: 'Intent Analysis',
        description: 'Analyzing user intent...',
        status: 'RUNNING' as const,
        progress: 20,
        percentage: 20,
        details: ['Analyzing keywords...'],
        timestamp: new Date().toISOString()
      },
    ];

    render(
      <AnalysisPanel {...defaultProps} currentPhase="analyzing" messages={messages} />
    );

    expect(screen.getByTestId('analysis-progress-panel')).toBeInTheDocument();
    expect(screen.queryByTestId('style-picker')).not.toBeInTheDocument();
  });

  /**
   * 测试7: 风格选择阶段显示StylePicker
   * 验证风格选择阶段显示风格选择器
   */
  it('应该在style-selection阶段显示StylePicker', () => {
    render(<AnalysisPanel {...defaultProps} currentPhase="style-selection" />);

    expect(screen.getByTestId('style-picker')).toBeInTheDocument();
    expect(screen.queryByTestId('analysis-progress-panel')).not.toBeInTheDocument();
  });
});
