/**
 * FormActions组件
 * 表单操作按钮组（V2.0深度融合版）
 *
 * V2.0改进：
 * - 移除独立的"快速Web预览"按钮
 * - 统一到V2.0创建流程（需求输入→意图识别→风格选择→原型预览→确认）
 * - 原型预览已集成OpenLovable快速生成能力（5-10秒）
 *
 * @author Ingenio Team
 * @since V2.0.1 (深度融合版)
 * @deprecated 建议使用V2.0创建流程 /create-v2
 */

'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Sparkles, Zap } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';

/**
 * FormActions组件Props
 */
export interface FormActionsProps {
  /** 表单是否有效（需求长度≥10） */
  isValid: boolean;
  /** 是否正在加载 */
  isLoading: boolean;
  /** 需求描述（用于快速预览） */
  requirement: string;
  /** 完整生成回调（主按钮） */
  onFullGeneration: (e: React.FormEvent) => void;
  /** 自定义类名 */
  className?: string;
}

/**
 * FormActions组件
 * V2.0深度融合版：统一到V2.0创建流程
 */
export function FormActions({
  isValid,
  isLoading,
  requirement,
  onFullGeneration,
  className,
}: FormActionsProps): React.ReactElement {
  const router = useRouter();

  /**
   * 跳转到V2.0创建流程
   * V2.0流程已集成OpenLovable快速预览能力
   * 可选择携带需求作为初始值
   */
  function handleGoToV2(e: React.MouseEvent<HTMLButtonElement>): void {
    e.preventDefault();
    // 如果有有效需求，可以携带到V2.0页面作为初始值
    if (requirement && requirement.trim().length >= 10) {
      // 未来可以通过URL参数或状态管理传递需求
      // 目前直接跳转到V2.0页面
      router.push(`/create-v2`);
    } else {
      router.push(`/create-v2`);
    }
  }

  return (
    <div className={className}>
      {/* V2.0提示 */}
      <Alert className="mb-4 border-purple-200 bg-purple-50 dark:bg-purple-900/10">
        <Zap className="h-4 w-4 text-purple-600" />
        <AlertDescription className="text-purple-800 dark:text-purple-200">
          <strong>V2.0已集成快速预览：</strong>
          在风格选择后会自动生成5-10秒的快速原型，支持聊天式迭代修改。
        </AlertDescription>
      </Alert>

      {/* 按钮布局 */}
      <div className="flex flex-col sm:flex-row gap-4">
        {/* V2.0创建流程按钮（推荐） */}
        <Button
          type="button"
          onClick={handleGoToV2}
          disabled={isLoading}
          size="lg"
          variant="outline"
          className="h-14 flex-1 gap-2 rounded-2xl text-base font-semibold shadow-md transition-all hover:shadow-lg border-purple-300 hover:border-purple-400"
        >
          <Zap className="h-5 w-5 text-purple-500" />
          V2.0智能创建（推荐）
        </Button>

        {/* 完整生成按钮（保留兼容） */}
        <Button
          type="submit"
          onClick={onFullGeneration}
          disabled={isLoading || !isValid}
          size="lg"
          className="h-14 flex-1 gap-2 rounded-2xl text-base font-semibold shadow-lg transition-all hover:shadow-xl"
        >
          {isLoading ? (
            <>
              <svg
                className="h-5 w-5 animate-spin"
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
              >
                <circle
                  className="opacity-25"
                  cx="12"
                  cy="12"
                  r="10"
                  stroke="currentColor"
                  strokeWidth="4"
                />
                <path
                  className="opacity-75"
                  fill="currentColor"
                  d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                />
              </svg>
              AI正在分析你的需求...
            </>
          ) : (
            <>
              <Sparkles className="h-5 w-5" />
              直接生成
            </>
          )}
        </Button>
      </div>

      {/* 快捷提示 */}
      <p className="text-center text-sm text-muted-foreground mt-4">
        按{' '}
        <kbd className="rounded border px-1.5 py-0.5 text-xs">⌘</kbd> +{' '}
        <kbd className="rounded border px-1.5 py-0.5 text-xs">Enter</kbd>{' '}
        快速提交
      </p>
    </div>
  );
}
