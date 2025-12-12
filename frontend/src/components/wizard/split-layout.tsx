/**
 * 分屏布局组件
 * 实现左右可调整大小的分屏布局，支持响应式设计
 */
'use client';

import React, { useState, useRef, useEffect } from 'react';
import { cn } from '@/lib/utils';

/**
 * 分屏布局组件Props
 */
interface SplitLayoutProps {
  /** 左侧内容 */
  leftContent: React.ReactNode;
  /** 右侧内容 */
  rightContent: React.ReactNode;
  /** 默认左侧宽度百分比 */
  defaultLeftWidth?: number;
  /** 最小左侧宽度 (px) */
  minLeftWidth?: number;
  /** 最大左侧宽度百分比 */
  maxLeftWidthPercent?: number;
  /** 是否可调整大小 */
  resizable?: boolean;
  /** 类名 */
  className?: string;
}

/**
 * 分屏布局组件
 */
export const SplitLayout: React.FC<SplitLayoutProps> = ({
  leftContent,
  rightContent,
  defaultLeftWidth = 40,
  minLeftWidth = 320,
  maxLeftWidthPercent = 60,
  resizable = true,
  className,
}) => {
  const [leftWidth, setLeftWidth] = useState(defaultLeftWidth);
  const [isDragging, setIsDragging] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // 处理鼠标拖拽
  const handleMouseDown = (e: React.MouseEvent) => {
    if (!resizable) return;
    e.preventDefault();
    setIsDragging(true);
  };

  // 处理鼠标移动
  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDragging || !containerRef.current) return;

      const container = containerRef.current;
      const containerRect = container.getBoundingClientRect();
      const newLeftWidth = ((e.clientX - containerRect.left) / containerRect.width) * 100;

      // 计算最小宽度百分比
      const minWidthPercent = (minLeftWidth / containerRect.width) * 100;

      // 限制在最小和最大宽度之间
      const clampedWidth = Math.max(
        minWidthPercent,
        Math.min(maxLeftWidthPercent, newLeftWidth)
      );

      setLeftWidth(clampedWidth);
    };

    const handleMouseUp = () => {
      setIsDragging(false);
    };

    if (isDragging) {
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    }

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isDragging, minLeftWidth, maxLeftWidthPercent]);

  return (
    <div
      ref={containerRef}
      className={cn(
        "flex w-full h-full overflow-hidden",
        className
      )}
    >
      {/* 左侧面板 */}
      <div
        className="flex-shrink-0 overflow-auto"
        style={{ width: `${leftWidth}%` }}
      >
        {leftContent}
      </div>

      {/* 分隔线 */}
      {resizable && (
        <div
          className={cn(
            "group relative w-1 flex-shrink-0 bg-border cursor-col-resize hover:bg-primary/50 transition-colors",
            isDragging && "bg-primary"
          )}
          onMouseDown={handleMouseDown}
        >
          {/* 拖拽手柄 */}
          <div className="absolute inset-y-0 left-1/2 -translate-x-1/2 w-4 flex items-center justify-center">
            <div className={cn(
              "h-12 w-1 rounded-full bg-border group-hover:bg-primary transition-colors",
              isDragging && "bg-primary"
            )} />
          </div>
        </div>
      )}

      {/* 右侧面板 */}
      <div className="flex-1 overflow-auto">
        {rightContent}
      </div>
    </div>
  );
};
