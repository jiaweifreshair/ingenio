'use client';

import React, { useState, useRef, useEffect, KeyboardEvent, ClipboardEvent } from 'react';
import { cn } from '@/lib/utils';

/**
 * VerificationCodeInput 组件 Props
 */
interface VerificationCodeInputProps {
  /** 验证码长度（默认6位） */
  length?: number;
  /** 验证码值变化回调 */
  onChange?: (value: string) => void;
  /** 输入完成回调（所有位数都填写完成） */
  onComplete?: (value: string) => void;
  /** 是否禁用输入 */
  disabled?: boolean;
  /** 是否显示错误状态 */
  error?: boolean;
  /** 错误提示信息 */
  errorMessage?: string;
  /** 自定义className */
  className?: string;
  /** 是否自动聚焦第一个输入框 */
  autoFocus?: boolean;
}

/**
 * 邮箱验证码输入组件
 *
 * 功能：
 * - 6位独立输入框（可配置位数）
 * - 自动聚焦下一个输入框
 * - 支持粘贴整个验证码
 * - 支持退格键删除
 * - 支持左右箭头键导航
 * - 错误状态显示
 * - 仅允许输入数字
 *
 * 使用场景：
 * - 邮箱验证码验证
 * - 手机验证码验证
 * - 双因素认证
 *
 * @author Ingenio Team
 * @since Phase 5.2
 */
export function VerificationCodeInput({
  length = 6,
  onChange,
  onComplete,
  disabled = false,
  error = false,
  errorMessage,
  className,
  autoFocus = true,
}: VerificationCodeInputProps) {
  // 存储每个输入框的值
  const [values, setValues] = useState<string[]>(Array(length).fill(''));

  // 存储每个输入框的ref
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  // 初始化inputRefs数组
  useEffect(() => {
    inputRefs.current = inputRefs.current.slice(0, length);
  }, [length]);

  // 自动聚焦第一个输入框
  useEffect(() => {
    if (autoFocus && inputRefs.current[0]) {
      inputRefs.current[0].focus();
    }
  }, [autoFocus]);

  /**
   * 处理单个输入框的值变化
   */
  const handleChange = (index: number, value: string) => {
    // 只允许输入数字
    if (value && !/^\d$/.test(value)) {
      return;
    }

    const newValues = [...values];
    newValues[index] = value;
    setValues(newValues);

    // 触发onChange回调
    const fullValue = newValues.join('');
    onChange?.(fullValue);

    // 如果输入了值，自动聚焦到下一个输入框
    if (value && index < length - 1) {
      inputRefs.current[index + 1]?.focus();
    }

    // 如果所有位数都填写完成，触发onComplete回调
    if (newValues.every(v => v !== '')) {
      onComplete?.(fullValue);
    }
  };

  /**
   * 处理键盘事件
   */
  const handleKeyDown = (index: number, e: KeyboardEvent<HTMLInputElement>) => {
    // Backspace键：删除当前值或聚焦到上一个输入框
    if (e.key === 'Backspace') {
      if (!values[index] && index > 0) {
        // 当前输入框为空，聚焦到上一个输入框并删除其值
        inputRefs.current[index - 1]?.focus();
        handleChange(index - 1, '');
      } else {
        // 删除当前值
        handleChange(index, '');
      }
    }

    // 左箭头键：聚焦到上一个输入框
    if (e.key === 'ArrowLeft' && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }

    // 右箭头键：聚焦到下一个输入框
    if (e.key === 'ArrowRight' && index < length - 1) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  /**
   * 处理粘贴事件
   * 支持粘贴完整验证码
   */
  const handlePaste = (e: ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();

    // 获取粘贴的文本
    const pastedData = e.clipboardData.getData('text/plain');

    // 只提取数字
    const digits = pastedData.replace(/\D/g, '').slice(0, length);

    if (digits.length > 0) {
      const newValues = Array(length).fill('');

      // 填充验证码
      for (let i = 0; i < digits.length; i++) {
        newValues[i] = digits[i];
      }

      setValues(newValues);

      // 触发onChange回调
      const fullValue = newValues.join('');
      onChange?.(fullValue);

      // 聚焦到最后一个填充的输入框或第一个空输入框
      const focusIndex = Math.min(digits.length, length - 1);
      inputRefs.current[focusIndex]?.focus();

      // 如果粘贴的验证码完整，触发onComplete回调
      if (digits.length === length) {
        onComplete?.(fullValue);
      }
    }
  };

  /**
   * 处理输入框聚焦
   * 选中当前输入框的内容，方便用户覆盖输入
   */
  const handleFocus = (index: number) => {
    inputRefs.current[index]?.select();
  };

  return (
    <div className={cn('space-y-2', className)}>
      {/* 输入框容器 */}
      <div className="flex gap-2 justify-center">
        {Array.from({ length }, (_, index) => (
          <input
            key={index}
            ref={(el) => {
              inputRefs.current[index] = el;
            }}
            type="text"
            inputMode="numeric"
            pattern="\d*"
            maxLength={1}
            value={values[index]}
            onChange={(e) => handleChange(index, e.target.value)}
            onKeyDown={(e) => handleKeyDown(index, e)}
            onPaste={handlePaste}
            onFocus={() => handleFocus(index)}
            disabled={disabled}
            className={cn(
              // 基础样式
              'w-12 h-14 text-center text-2xl font-semibold',
              'rounded-lg border-2 transition-all duration-200',
              'focus:outline-none focus:ring-2 focus:ring-offset-2',

              // 正常状态
              'border-gray-300 bg-white',
              'focus:border-primary focus:ring-primary',

              // 错误状态
              error && 'border-red-500 focus:border-red-500 focus:ring-red-500',

              // 禁用状态
              disabled && 'bg-gray-100 cursor-not-allowed opacity-50',

              // hover效果
              !disabled && 'hover:border-gray-400'
            )}
            aria-label={`验证码第${index + 1}位`}
          />
        ))}
      </div>

      {/* 错误提示 */}
      {error && errorMessage && (
        <p className="text-sm text-red-500 text-center">{errorMessage}</p>
      )}
    </div>
  );
}
