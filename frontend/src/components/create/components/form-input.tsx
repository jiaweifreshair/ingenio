/**
 * FormInput组件
 * 需求描述输入框 + 字数统计提示
 *
 * 功能：
 * - 大尺寸Textarea输入框
 * - 实时字数统计
 * - 最小字数提示
 * - 支持Cmd/Ctrl+Enter快捷提交
 * - 响应式高度
 *
 * @author Ingenio Team
 * @since V2.0
 */

'use client';

import React, { useCallback } from 'react';
import { Textarea } from '@/components/ui/textarea';

/**
 * FormInput组件Props
 */
export interface FormInputProps {
  /** 输入值 */
  value: string;
  /** 值变更回调 */
  onChange: (value: string) => void;
  /** 是否禁用 */
  disabled?: boolean;
  /** 最小字符数 */
  minLength?: number;
  /** 表单提交回调（Cmd/Ctrl+Enter触发） */
  onSubmit?: (e: React.FormEvent) => void;
  /** 自定义类名 */
  className?: string;
}

/**
 * FormInput组件
 * 需求输入框组件，支持快捷键提交
 */
export function FormInput({
  value,
  onChange,
  disabled = false,
  minLength = 10,
  onSubmit,
  className,
}: FormInputProps): React.ReactElement {
  /**
   * 处理输入值变更
   */
  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      onChange(e.target.value);
    },
    [onChange]
  );

  /**
   * 处理键盘快捷键
   * Cmd/Ctrl+Enter提交表单
   */
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        if (onSubmit) {
          onSubmit(e as unknown as React.FormEvent);
        }
      }
    },
    [onSubmit]
  );

  // 计算是否低于最小长度
  const isBelowMinLength = value.length < minLength;

  return (
    <div className={className}>
      <div className="group relative">
        <Textarea
          id="requirement"
          placeholder="描述你想要的应用...&#10;例如：创建一个校园二手交易平台，支持商品发布、搜索、聊天和交易评价"
          value={value}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          required
          disabled={disabled}
          className="min-h-[200px] resize-none rounded-2xl border-2 border-border/50 bg-card/30 p-6 text-base backdrop-blur-xl transition-all focus-visible:border-primary focus-visible:ring-4 focus-visible:ring-primary/20"
        />

        {/* 字数提示 */}
        <p className="mt-3 text-right text-xs text-muted-foreground">
          {value.length} 字符{' '}
          {isBelowMinLength && `（至少需要${minLength}个字符）`}
        </p>
      </div>
    </div>
  );
}
