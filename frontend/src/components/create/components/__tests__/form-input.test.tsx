/**
 * FormInput组件单元测试
 *
 * 测试场景：
 * - 基础渲染和placeholder
 * - 用户输入和onChange回调
 * - 值受控更新
 * - 禁用状态
 * - 字符计数显示
 * - Cmd/Ctrl+Enter快捷键提交
 *
 * @author Ingenio Team
 * @since V2.0 Day 6 Phase 6.4
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FormInput } from '../form-input';

describe('FormInput', () => {
  /**
   * 测试1: 基础渲染
   * 验证组件正确渲染，显示placeholder
   */
  it('应该正确渲染输入框和placeholder', () => {
    const onChange = vi.fn();
    render(<FormInput value="" onChange={onChange} />);

    const textarea = screen.getByRole('textbox');
    expect(textarea).toBeInTheDocument();
    expect(textarea).toHaveAttribute('placeholder', expect.stringContaining('描述你想要的应用'));
  });

  /**
   * 测试2: 用户输入
   * 验证用户输入时触发onChange回调
   */
  it('应该在用户输入时触发onChange回调', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<FormInput value="" onChange={onChange} />);

    const textarea = screen.getByRole('textbox');
    await user.type(textarea, 'test input');

    // onChange会为每个字符触发一次
    expect(onChange).toHaveBeenCalled();
    expect(onChange).toHaveBeenCalledWith(expect.stringContaining('t'));
  });

  /**
   * 测试3: 受控组件
   * 验证value prop正确控制输入框的值
   */
  it('应该作为受控组件正确显示value', () => {
    const onChange = vi.fn();
    const { rerender } = render(<FormInput value="initial" onChange={onChange} />);

    const textarea = screen.getByRole('textbox');
    expect(textarea).toHaveValue('initial');

    // 更新value prop
    rerender(<FormInput value="updated" onChange={onChange} />);
    expect(textarea).toHaveValue('updated');
  });

  /**
   * 测试4: 禁用状态
   * 验证disabled prop正确禁用输入框
   */
  it('应该在disabled为true时禁用输入框', () => {
    const onChange = vi.fn();
    render(<FormInput value="" onChange={onChange} disabled={true} />);

    const textarea = screen.getByRole('textbox');
    expect(textarea).toBeDisabled();
  });

  /**
   * 测试5: 字符计数显示
   * 验证字符计数和最小长度提示正确显示
   */
  it('应该正确显示字符计数和最小长度提示', () => {
    const onChange = vi.fn();
    const { container, rerender } = render(
      <FormInput value="short" onChange={onChange} minLength={10} />
    );

    // 显示当前字符数（检查文本内容）
    const charCountText1 = container.querySelector('.text-xs.text-muted-foreground');
    expect(charCountText1?.textContent).toContain('5 字符');
    expect(charCountText1?.textContent).toContain('至少需要10个字符');

    // 更新为满足最小长度的值（16字符正好）
    rerender(
      <FormInput value="long enough text" onChange={onChange} minLength={10} />
    );

    // 字符数更新，最小长度提示消失（注意"long enough text"是16字符）
    const charCountText2 = container.querySelector('.text-xs.text-muted-foreground');
    expect(charCountText2?.textContent).toContain('16 字符');
    expect(charCountText2?.textContent).not.toContain('至少需要10个字符');
  });

  /**
   * 测试6: Cmd+Enter快捷键提交（macOS）
   * 验证Cmd+Enter触发onSubmit回调
   */
  it('应该在按下Cmd+Enter时触发onSubmit回调', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onSubmit = vi.fn();

    render(
      <FormInput value="test" onChange={onChange} onSubmit={onSubmit} />
    );

    const textarea = screen.getByRole('textbox');
    await user.click(textarea);
    // 使用fireEvent模拟键盘事件（userEvent对Meta键支持有限）
    const { fireEvent } = await import('@testing-library/react');
    fireEvent.keyDown(textarea, { key: 'Enter', metaKey: true });

    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  /**
   * 测试7: Ctrl+Enter快捷键提交（Windows/Linux）
   * 验证Ctrl+Enter触发onSubmit回调
   */
  it('应该在按下Ctrl+Enter时触发onSubmit回调', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const onSubmit = vi.fn();

    render(
      <FormInput value="test" onChange={onChange} onSubmit={onSubmit} />
    );

    const textarea = screen.getByRole('textbox');
    await user.click(textarea);
    // 使用fireEvent模拟键盘事件（更可靠）
    const { fireEvent } = await import('@testing-library/react');
    fireEvent.keyDown(textarea, { key: 'Enter', ctrlKey: true });

    expect(onSubmit).toHaveBeenCalledTimes(1);
  });
});
