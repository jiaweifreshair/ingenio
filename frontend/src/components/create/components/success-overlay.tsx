/**
 * SuccessOverlay组件
 * 成功动画覆盖层
 *
 * 功能：
 * - 全屏覆盖层
 * - 成功动画展示
 * - 文字提示
 * - 背景模糊效果
 *
 * @author Ingenio Team
 * @since V2.0
 */

'use client';

import React from 'react';
import { SuccessAnimation } from '@/components/ui/success-animation';

/**
 * SuccessOverlay组件Props
 */
export interface SuccessOverlayProps {
  /** 是否显示 */
  show: boolean;
  /** 自定义类名 */
  className?: string;
}

/**
 * SuccessOverlay组件
 * 生成成功后的覆盖层动画
 */
export function SuccessOverlay({
  show,
  className,
}: SuccessOverlayProps): React.ReactElement | null {
  if (!show) {
    return null;
  }

  return (
    <div
      className={`fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm ${className || ''}`}
    >
      <div className="flex flex-col items-center gap-4">
        <SuccessAnimation size={96} showConfetti={true} />
        <p className="text-lg font-semibold">生成成功！</p>
        <p className="text-sm text-muted-foreground">正在跳转到向导页面...</p>
      </div>
    </div>
  );
}
