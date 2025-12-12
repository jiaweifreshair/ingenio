/**
 * ModelSelectorCard组件单元测试
 *
 * 测试场景：
 * - 卡片渲染
 * - 说明文字显示
 * - ModelSelector组件渲染
 * - value prop传递
 * - onValueChange回调
 * - disabled状态
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ModelSelectorCard } from '../model-selector-card';
import { UNIAIX_MODELS } from '@/lib/api/uniaix';

// Mock ModelSelector组件
vi.mock('@/components/ai/model-selector', () => ({
  ModelSelector: ({
    value,
    onValueChange,
    disabled,
  }: {
    value: string;
    onValueChange: (v: string) => void;
    disabled?: boolean;
  }) => (
    <div data-testid="model-selector">
      <button
        onClick={() => !disabled && onValueChange('new-model')}
        disabled={disabled}
        data-value={value}
      >
        ModelSelector
      </button>
    </div>
  ),
}));

describe('ModelSelectorCard', () => {
  /**
   * 测试1: 卡片渲染
   * 验证组件正确渲染卡片布局
   */
  it('应该正确渲染卡片布局', () => {
    const onValueChange = vi.fn();

    render(
      <ModelSelectorCard
        value={UNIAIX_MODELS.QWEN_MAX}
        onValueChange={onValueChange}
      />
    );

    expect(screen.getByText('AI模型')).toBeInTheDocument();
    expect(screen.getByTestId('model-selector')).toBeInTheDocument();
  });

  /**
   * 测试2: 说明文字显示
   * 验证说明文字正确显示
   */
  it('应该显示正确的说明文字', () => {
    const onValueChange = vi.fn();

    render(
      <ModelSelectorCard
        value={UNIAIX_MODELS.QWEN_MAX}
        onValueChange={onValueChange}
      />
    );

    expect(
      screen.getByText('选择最适合的AI模型来理解你的需求')
    ).toBeInTheDocument();
  });

  /**
   * 测试3: value传递
   * 验证value正确传递给ModelSelector
   */
  it('应该正确传递value给ModelSelector', () => {
    const onValueChange = vi.fn();

    render(
      <ModelSelectorCard
        value={UNIAIX_MODELS.QWEN_TURBO}
        onValueChange={onValueChange}
      />
    );

    const button = screen.getByRole('button', { name: /ModelSelector/ });
    expect(button).toHaveAttribute('data-value', UNIAIX_MODELS.QWEN_TURBO);
  });

  /**
   * 测试4: onValueChange回调
   * 验证onValueChange回调正确触发
   */
  it('应该在ModelSelector变更时触发onValueChange回调', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();

    render(
      <ModelSelectorCard
        value={UNIAIX_MODELS.QWEN_MAX}
        onValueChange={onValueChange}
      />
    );

    const button = screen.getByRole('button', { name: /ModelSelector/ });
    await user.click(button);

    expect(onValueChange).toHaveBeenCalledWith('new-model');
  });

  /**
   * 测试5: disabled状态
   * 验证disabled prop正确传递并禁用ModelSelector
   */
  it('应该在disabled为true时禁用ModelSelector', () => {
    const onValueChange = vi.fn();

    render(
      <ModelSelectorCard
        value={UNIAIX_MODELS.QWEN_MAX}
        onValueChange={onValueChange}
        disabled={true}
      />
    );

    const button = screen.getByRole('button', { name: /ModelSelector/ });
    expect(button).toBeDisabled();
  });

  /**
   * 测试6: 自定义className
   * 验证自定义className正确应用
   */
  it('应该正确应用自定义className', () => {
    const onValueChange = vi.fn();
    const { container } = render(
      <ModelSelectorCard
        value={UNIAIX_MODELS.QWEN_MAX}
        onValueChange={onValueChange}
        className="custom-class"
      />
    );

    const card = container.querySelector('.custom-class');
    expect(card).toBeInTheDocument();
  });
});
