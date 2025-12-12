'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { Button, ButtonProps } from '@/components/ui/button';

/**
 * CountdownButton 组件 Props
 */
interface CountdownButtonProps extends Omit<ButtonProps, 'onClick' | 'disabled' | 'children'> {
  /** 点击按钮的回调函数 */
  onClick: () => void | Promise<void>;
  /** 倒计时秒数（默认60秒） */
  countdown?: number;
  /** 初始按钮文本（默认"发送验证码"） */
  initialText?: string;
  /** 倒计时期间按钮文本模板（默认"{count}秒后重试"） */
  countdownText?: (count: number) => string;
  /** 是否禁用按钮（外部控制） */
  externalDisabled?: boolean;
}

/**
 * 倒计时按钮组件
 *
 * 功能：
 * - 点击后启动倒计时（默认60秒）
 * - 倒计时期间按钮禁用
 * - 显示剩余秒数
 * - 倒计时结束后恢复可点击状态
 * - 支持自定义倒计时时长和文本
 * - 倒计时状态持久化到localStorage（刷新页面后继续倒计时）
 *
 * 使用场景：
 * - 发送邮箱验证码
 * - 发送手机验证码
 * - 防止频繁请求
 *
 * @author Ingenio Team
 * @since Phase 5.2
 */
export function CountdownButton({
  onClick,
  countdown = 60,
  initialText = '发送验证码',
  countdownText = (count) => `${count}秒后重试`,
  externalDisabled = false,
  ...buttonProps
}: CountdownButtonProps) {
  // 剩余秒数
  const [remaining, setRemaining] = useState<number>(0);

  // 是否正在执行点击操作（用于显示加载状态）
  const [isExecuting, setIsExecuting] = useState(false);

  // localStorage key（用于持久化倒计时状态）
  const storageKey = 'countdown_button_end_time';

  /**
   * 初始化倒计时
   * 检查localStorage中是否有未完成的倒计时
   */
  useEffect(() => {
    const savedEndTime = localStorage.getItem(storageKey);
    if (savedEndTime) {
      const endTime = parseInt(savedEndTime, 10);
      const now = Date.now();
      const remainingTime = Math.ceil((endTime - now) / 1000);

      if (remainingTime > 0) {
        setRemaining(remainingTime);
      } else {
        // 倒计时已过期，清除localStorage
        localStorage.removeItem(storageKey);
      }
    }
  }, [storageKey]);

  /**
   * 倒计时逻辑
   */
  useEffect(() => {
    if (remaining <= 0) {
      return;
    }

    const timer = setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          // 倒计时结束，清除localStorage
          localStorage.removeItem(storageKey);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [remaining, storageKey]);

  /**
   * 开始倒计时
   */
  const startCountdown = useCallback(() => {
    setRemaining(countdown);

    // 保存倒计时结束时间到localStorage
    const endTime = Date.now() + countdown * 1000;
    localStorage.setItem(storageKey, endTime.toString());
  }, [countdown, storageKey]);

  /**
   * 处理按钮点击
   */
  const handleClick = async () => {
    if (remaining > 0 || isExecuting || externalDisabled) {
      return;
    }

    try {
      setIsExecuting(true);

      // 执行onClick回调
      await onClick();

      // 启动倒计时
      startCountdown();
    } catch (error) {
      // 如果onClick抛出错误，不启动倒计时
      console.error('CountdownButton onClick error:', error);
      throw error; // 重新抛出错误，让父组件处理
    } finally {
      setIsExecuting(false);
    }
  };

  // 按钮是否禁用
  const isDisabled = remaining > 0 || isExecuting || externalDisabled;

  // 按钮文本
  const buttonText = remaining > 0 ? countdownText(remaining) : initialText;

  return (
    <Button
      {...buttonProps}
      onClick={handleClick}
      disabled={isDisabled}
      variant={buttonProps.variant || 'outline'}
    >
      {isExecuting ? (
        <span className="flex items-center gap-2">
          <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
              fill="none"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
            />
          </svg>
          发送中...
        </span>
      ) : (
        buttonText
      )}
    </Button>
  );
}
