'use client';

/**
 * TypewriterCode 组件
 *
 * 结合打字机效果和语法高亮的代码显示组件。
 * 用于在代码生成过程中展示逐步输出的效果，让用户感知到持续的进度。
 *
 * 特性：
 * - 逐字符/逐块显示代码（打字机效果）
 * - 动态速度调整（落后太多时自动加速追赶）
 * - 语法高亮（基于 react-syntax-highlighter）
 * - 进度指示器
 * - 支持立即显示模式（instant）
 */

import { useState, useEffect, useRef, useCallback } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/cjs/styles/prism';

/**
 * TypewriterCode 组件属性
 */
interface TypewriterCodeProps {
  /** 完整代码（可能还在增长） */
  code: string;
  /** 语法高亮语言 */
  language: string;
  /** 基础速度（ms），默认 2 */
  speed?: number;
  /** 每次显示的字符数，默认 5 */
  chunkSize?: number;
  /** 是否立即显示完整内容 */
  instant?: boolean;
  /** 代码生成是否完成 */
  completed?: boolean;
  /** 是否显示行号 */
  showLineNumbers?: boolean;
  /** 容器 className */
  className?: string;
  /** 自定义样式 */
  customStyle?: React.CSSProperties;
  /** 是否显示进度指示器 */
  showProgress?: boolean;
}

/**
 * 打字机效果的代码高亮组件
 */
export function TypewriterCode({
  code,
  language,
  speed = 2,
  chunkSize = 5,
  instant = false,
  completed = false,
  showLineNumbers = true,
  className,
  customStyle,
  showProgress = true,
}: TypewriterCodeProps) {
  // 已显示的字符数
  const [displayedLength, setDisplayedLength] = useState(instant ? code.length : 0);
  // 定时器引用
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  // 上一次的代码长度，用于检测新内容
  const prevCodeLengthRef = useRef(code.length);

  /**
   * 清理定时器
   */
  const clearTimer = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  }, []);

  /**
   * 计算动态速度和块大小
   * 落后太多时加速追赶，避免显示严重滞后
   */
  const calculateDynamicParams = useCallback(
    (lag: number) => {
      let dynamicChunkSize = chunkSize;
      let dynamicSpeed = speed;

      if (lag > 500) {
        // 落后超过500字符，大幅加速
        dynamicChunkSize = Math.max(50, Math.floor(lag / 10));
        dynamicSpeed = 1;
      } else if (lag > 100) {
        // 落后超过100字符，适度加速
        dynamicChunkSize = Math.max(20, Math.floor(lag / 5));
        dynamicSpeed = 1;
      }

      return { dynamicChunkSize, dynamicSpeed };
    },
    [chunkSize, speed]
  );

  // 主要的打字机效果逻辑
  useEffect(() => {
    // 立即显示模式
    if (instant) {
      setDisplayedLength(code.length);
      return;
    }

    // 清理之前的定时器
    clearTimer();

    // 如果已经显示完且生成完成，不需要定时器
    if (displayedLength >= code.length && completed) {
      return;
    }

    // 计算落后的字符数
    const lag = code.length - displayedLength;

    // 如果没有落后，等待新内容
    if (lag <= 0) {
      return;
    }

    // 计算动态参数
    const { dynamicChunkSize, dynamicSpeed } = calculateDynamicParams(lag);

    // 启动定时器
    intervalRef.current = setInterval(() => {
      setDisplayedLength((prev) => {
        const next = Math.min(prev + dynamicChunkSize, code.length);
        return next;
      });
    }, dynamicSpeed);

    return clearTimer;
  }, [code.length, displayedLength, instant, completed, calculateDynamicParams, clearTimer]);

  // 检测代码重置（新内容开始）
  useEffect(() => {
    // 如果代码长度变短，说明是新内容，重置显示长度
    if (code.length < prevCodeLengthRef.current) {
      setDisplayedLength(0);
    }
    prevCodeLengthRef.current = code.length;
  }, [code.length]);

  // 截取要显示的代码
  const displayedCode = code.slice(0, displayedLength);
  // 是否正在打字
  const isTyping = displayedLength < code.length;

  return (
    <div className={className}>
      <SyntaxHighlighter
        language={language}
        style={vscDarkPlus}
        customStyle={{
          margin: 0,
          padding: '1rem',
          fontSize: '0.75rem',
          background: 'transparent',
          minHeight: '100%',
          ...customStyle,
        }}
        showLineNumbers={showLineNumbers}
        wrapLongLines={true}
      >
        {displayedCode || '// 等待代码生成...'}
      </SyntaxHighlighter>
      {showProgress && isTyping && (
        <div className="flex items-center px-4 pb-2">
          <span className="inline-block w-2 h-4 bg-orange-400 animate-pulse" />
          <span className="ml-2 text-xs text-orange-400 animate-pulse">
            正在生成... ({displayedLength}/{code.length})
          </span>
        </div>
      )}
    </div>
  );
}
