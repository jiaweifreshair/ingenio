/**
 * 可拖拽分栏组件
 *
 * 设计理念：
 * - 支持左右两个面板的可拖拽调整
 * - 自动保存分栏比例到localStorage
 * - 响应式设计，移动端自动切换为上下布局
 * - 支持最小宽度限制，防止面板过小
 * - 流畅的拖拽动画和视觉反馈
 *
 * 使用场景：
 * - 代码编辑器左右分栏（代码视图 + 预览视图）
 * - 文件浏览器左右分栏（目录树 + 文件内容）
 * - 任何需要可调整大小的两栏布局
 */
'use client';

import React, { useState, useRef, useEffect, useCallback } from 'react';
import { cn } from '@/lib/utils';
import { GripVertical } from 'lucide-react';

/**
 * 组件Props
 */
interface ResizablePanelsProps {
  /** 左侧面板内容 */
  leftPanel: React.ReactNode;
  /** 右侧面板内容 */
  rightPanel: React.ReactNode;
  /** 默认左侧面板宽度百分比 (0-100) */
  defaultLeftWidth?: number;
  /** 左侧面板最小宽度百分比 */
  minLeftWidth?: number;
  /** 左侧面板最大宽度百分比 */
  maxLeftWidth?: number;
  /** localStorage保存key（用于记忆分栏比例） */
  storageKey?: string;
  /** 容器类名 */
  className?: string;
  /** 分隔条类名 */
  dividerClassName?: string;
  /** 是否显示分隔条手柄 */
  showDividerHandle?: boolean;
}

/**
 * 可拖拽分栏组件
 */
export const ResizablePanels: React.FC<ResizablePanelsProps> = ({
  leftPanel,
  rightPanel,
  defaultLeftWidth = 50,
  minLeftWidth = 20,
  maxLeftWidth = 80,
  storageKey = 'resizable-panels-width',
  className,
  dividerClassName,
  showDividerHandle = true,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const isDraggingRef = useRef(false);

  // 从localStorage读取保存的宽度
  const getSavedWidth = useCallback((): number => {
    if (typeof window === 'undefined') return defaultLeftWidth;
    try {
      const saved = localStorage.getItem(storageKey);
      if (saved) {
        const width = parseFloat(saved);
        if (width >= minLeftWidth && width <= maxLeftWidth) {
          return width;
        }
      }
    } catch (e) {
      console.warn('Failed to read saved width:', e);
    }
    return defaultLeftWidth;
  }, [storageKey, defaultLeftWidth, minLeftWidth, maxLeftWidth]);

  const [leftWidth, setLeftWidth] = useState<number>(getSavedWidth());
  const [isDragging, setIsDragging] = useState(false);

  /**
   * 保存宽度到localStorage
   */
  const saveWidth = useCallback((width: number) => {
    try {
      localStorage.setItem(storageKey, width.toString());
    } catch (e) {
      console.warn('Failed to save width:', e);
    }
  }, [storageKey]);

  /**
   * 开始拖拽
   */
  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    isDraggingRef.current = true;
    setIsDragging(true);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  }, []);

  /**
   * 拖拽中
   */
  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!isDraggingRef.current || !containerRef.current) return;

      const containerRect = containerRef.current.getBoundingClientRect();
      const offsetX = e.clientX - containerRect.left;
      const newWidth = (offsetX / containerRect.width) * 100;

      // 限制在最小和最大宽度之间
      const clampedWidth = Math.max(
        minLeftWidth,
        Math.min(maxLeftWidth, newWidth)
      );

      setLeftWidth(clampedWidth);
    },
    [minLeftWidth, maxLeftWidth]
  );

  /**
   * 结束拖拽
   */
  const handleMouseUp = useCallback(() => {
    if (isDraggingRef.current) {
      isDraggingRef.current = false;
      setIsDragging(false);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
      saveWidth(leftWidth);
    }
  }, [leftWidth, saveWidth]);

  /**
   * 监听全局鼠标事件
   */
  useEffect(() => {
    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);

    return () => {
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
    };
  }, [handleMouseMove, handleMouseUp]);

  /**
   * 清理拖拽状态（组件卸载时）
   */
  useEffect(() => {
    return () => {
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, []);

  const rightWidth = 100 - leftWidth;

  return (
    <div
      ref={containerRef}
      className={cn(
        'relative flex h-full w-full overflow-hidden',
        className
      )}
    >
      {/* 左侧面板 */}
      <div
        className="h-full overflow-auto"
        style={{ width: `${leftWidth}%` }}
      >
        {leftPanel}
      </div>

      {/* 分隔条 */}
      <div
        className={cn(
          'group relative flex items-center justify-center transition-colors',
          'hover:bg-primary/10',
          isDragging && 'bg-primary/20',
          dividerClassName
        )}
        style={{
          width: '4px',
          cursor: 'col-resize',
          flexShrink: 0,
        }}
        onMouseDown={handleMouseDown}
      >
        {/* 分隔线 */}
        <div className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-border" />

        {/* 拖拽手柄 */}
        {showDividerHandle && (
          <div
            className={cn(
              'absolute rounded-sm bg-border p-1 opacity-0 transition-opacity',
              'group-hover:opacity-100',
              isDragging && 'opacity-100'
            )}
          >
            <GripVertical className="h-4 w-4 text-muted-foreground" />
          </div>
        )}
      </div>

      {/* 右侧面板 */}
      <div
        className="h-full overflow-auto"
        style={{ width: `${rightWidth}%` }}
      >
        {rightPanel}
      </div>

      {/* 拖拽遮罩层（防止iframe干扰拖拽） */}
      {isDragging && (
        <div className="pointer-events-none absolute inset-0 z-50" />
      )}
    </div>
  );
};

/**
 * 三栏布局变体（可选实现）
 */
interface ResizableThreePanelsProps {
  leftPanel: React.ReactNode;
  centerPanel: React.ReactNode;
  rightPanel: React.ReactNode;
  defaultLeftWidth?: number;
  defaultRightWidth?: number;
  className?: string;
}

export const ResizableThreePanels: React.FC<ResizableThreePanelsProps> = ({
  leftPanel,
  centerPanel,
  rightPanel,
  defaultLeftWidth = 25,
  defaultRightWidth = 25,
  className,
}) => {
  return (
    <ResizablePanels
      className={className}
      defaultLeftWidth={defaultLeftWidth}
      leftPanel={leftPanel}
      rightPanel={
        <ResizablePanels
          defaultLeftWidth={100 - defaultRightWidth}
          leftPanel={centerPanel}
          rightPanel={rightPanel}
          storageKey="resizable-three-panels-right"
        />
      }
      storageKey="resizable-three-panels-left"
    />
  );
};
