/**
 * 自动滚动Hook
 *
 * 功能：
 * - 监听内容变化，自动滚动到底部
 * - 支持用户手动滚动时暂停自动滚动
 * - 提供"回到底部"快捷按钮
 * - 检测是否在底部附近（100px阈值）
 *
 * 使用场景：
 * - 实时日志流显示
 * - 聊天消息列表
 * - Agent执行状态更新
 */
'use client';

import { useEffect, useRef, useState, useCallback } from 'react';

/**
 * Hook配置选项
 */
export interface UseAutoScrollOptions {
  /** 是否启用自动滚动 */
  enabled?: boolean;
  /** 判断是否在底部的阈值（像素） */
  threshold?: number;
  /** 滚动动画时长（毫秒） */
  animationDuration?: number;
  /** 是否平滑滚动 */
  smooth?: boolean;
}

/**
 * 自动滚动Hook返回值
 */
export interface UseAutoScrollResult {
  /** 滚动容器ref（需要绑定到scrollable元素） */
  scrollRef: React.RefObject<HTMLDivElement | null>;
  /** 是否在底部附近 */
  isAtBottom: boolean;
  /** 是否自动滚动启用 */
  isAutoScrollEnabled: boolean;
  /** 手动滚动到底部 */
  scrollToBottom: () => void;
  /** 启用自动滚动 */
  enableAutoScroll: () => void;
  /** 禁用自动滚动 */
  disableAutoScroll: () => void;
  /** 切换自动滚动 */
  toggleAutoScroll: () => void;
}

/**
 * 自动滚动Hook
 */
export function useAutoScroll(
  dependencies: unknown[] = [],
  options: UseAutoScrollOptions = {}
): UseAutoScrollResult {
  const {
    enabled = true,
    threshold = 100,
    smooth = true,
  } = options;

  const scrollRef = useRef<HTMLDivElement>(null);
  const [isAtBottom, setIsAtBottom] = useState(true);
  const [isAutoScrollEnabled, setIsAutoScrollEnabled] = useState(enabled);
  const userScrolledRef = useRef(false);
  const scrollTimeoutRef = useRef<NodeJS.Timeout | undefined>(undefined);

  /**
   * 检查是否在底部附近
   */
  const checkIfAtBottom = useCallback(() => {
    if (!scrollRef.current) return false;

    const { scrollTop, scrollHeight, clientHeight } = scrollRef.current;
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight;

    return distanceFromBottom <= threshold;
  }, [threshold]);

  /**
   * 滚动到底部
   */
  const scrollToBottom = useCallback(() => {
    if (!scrollRef.current) return;

    const element = scrollRef.current;

    if (smooth) {
      element.scrollTo({
        top: element.scrollHeight,
        behavior: 'smooth',
      });
    } else {
      element.scrollTop = element.scrollHeight;
    }

    setIsAtBottom(true);
  }, [smooth]);

  /**
   * 启用自动滚动
   */
  const enableAutoScroll = useCallback(() => {
    setIsAutoScrollEnabled(true);
    userScrolledRef.current = false;
    scrollToBottom();
  }, [scrollToBottom]);

  /**
   * 禁用自动滚动
   */
  const disableAutoScroll = useCallback(() => {
    setIsAutoScrollEnabled(false);
    userScrolledRef.current = true;
  }, []);

  /**
   * 切换自动滚动
   */
  const toggleAutoScroll = useCallback(() => {
    if (isAutoScrollEnabled) {
      disableAutoScroll();
    } else {
      enableAutoScroll();
    }
  }, [isAutoScrollEnabled, enableAutoScroll, disableAutoScroll]);

  /**
   * 监听用户滚动
   */
  useEffect(() => {
    const element = scrollRef.current;
    if (!element) return;

    const handleScroll = () => {
      const atBottom = checkIfAtBottom();
      setIsAtBottom(atBottom);

      // 清除之前的定时器
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current);
      }

      // 如果用户向上滚动，禁用自动滚动
      if (!atBottom && isAutoScrollEnabled) {
        userScrolledRef.current = true;
        setIsAutoScrollEnabled(false);
      }

      // 如果用户滚动到底部，重新启用自动滚动
      if (atBottom && userScrolledRef.current) {
        scrollTimeoutRef.current = setTimeout(() => {
          userScrolledRef.current = false;
          setIsAutoScrollEnabled(true);
        }, 500); // 延迟500ms确认用户意图
      }
    };

    element.addEventListener('scroll', handleScroll, { passive: true });

    return () => {
      element.removeEventListener('scroll', handleScroll);
      if (scrollTimeoutRef.current) {
        clearTimeout(scrollTimeoutRef.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAutoScrollEnabled]);

  /**
   * 监听内容变化，自动滚动
   */
  useEffect(() => {
    if (isAutoScrollEnabled && !userScrolledRef.current) {
      // 延迟滚动，确保DOM已更新
      const timer = setTimeout(() => {
        scrollToBottom();
      }, 50);

      return () => clearTimeout(timer);
    }
  }, [...dependencies, isAutoScrollEnabled]);

  return {
    scrollRef,
    isAtBottom,
    isAutoScrollEnabled,
    scrollToBottom,
    enableAutoScroll,
    disableAutoScroll,
    toggleAutoScroll,
  };
}
