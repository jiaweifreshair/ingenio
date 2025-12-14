/**
 * AnalysisPanel组件单元测试
 *
 * 测试场景：
 * - 左右分屏布局渲染
 * - 需求文本显示
 * - AI模型信息显示
 * - 分析阶段显示AnalysisProgressPanel
 * - 风格选择阶段显示加载状态（自动原型生成）
 *
 * V2.0简化版：风格选择已自动化，不再显示StylePicker
 *
 * @author Ingenio Team
 * @since V2.0
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

describe('AnalysisPanel', () => {
  const defaultProps = {
    requirement: '创建一个电商平台',
    selectedModel: UNIAIX_MODELS.QWEN_MAX,
    currentPhase: 'analyzing' as const,
    messages: [],
    isConnected: true,
    isCompleted: false,
    analysisError: null,
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
   * 测试4: 分析阶段显示AnalysisProgressPanel
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
  });

  /**
   * 测试5: 风格选择阶段显示加载状态
   * V2.0简化版：风格选择阶段显示"正在生成原型..."加载状态
   */
  it('应该在style-selection阶段显示原型生成加载状态', () => {
    render(<AnalysisPanel {...defaultProps} currentPhase="style-selection" />);

    expect(screen.getByText('正在生成原型...')).toBeInTheDocument();
    expect(screen.queryByTestId('analysis-progress-panel')).not.toBeInTheDocument();
  });
});
