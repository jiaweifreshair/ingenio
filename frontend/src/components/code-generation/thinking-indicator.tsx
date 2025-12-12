/**
 * AI思考过程指示器组件
 *
 * 设计理念：
 * - 实时显示AI正在思考的内容
 * - 动态打字机效果展示思考文字
 * - 脉冲动画表示活跃状态
 * - 进度指示器显示整体完成度
 *
 * 使用场景：
 * - 代码生成时显示AI思考过程
 * - 需求分析时显示分析进度
 * - 任何需要展示AI处理状态的场景
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
'use client';

import React, { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '@/lib/utils';
import { Progress } from '@/components/ui/progress';
import {
  Brain,
  Sparkles,
  Loader2,
  FileCode,
  CheckCircle2,
  XCircle,
} from 'lucide-react';

/**
 * 思考阶段枚举
 */
export type ThinkingPhase =
  | 'idle'           // 空闲
  | 'analyzing'      // 分析需求
  | 'designing'      // 设计架构
  | 'generating'     // 生成代码
  | 'completed'      // 完成
  | 'error';         // 错误

/**
 * 组件Props
 */
interface ThinkingIndicatorProps {
  /** 当前思考消息 */
  message: string | null;
  /** 思考预计持续时间（毫秒） */
  duration?: number | null;
  /** 当前阶段 */
  phase?: ThinkingPhase;
  /** 整体进度（0-100） */
  progress?: number;
  /** 当前正在生成的文件 */
  currentFile?: string | null;
  /** 已完成文件数 */
  completedFiles?: number;
  /** 总文件数 */
  totalFiles?: number;
  /** 是否显示详细信息 */
  showDetails?: boolean;
  /** 容器类名 */
  className?: string;
  /** 错误信息 */
  error?: string | null;
}

/**
 * 阶段配置
 */
const phaseConfig: Record<ThinkingPhase, {
  icon: React.ReactNode;
  color: string;
  bgColor: string;
  label: string;
}> = {
  idle: {
    icon: <Brain className="h-5 w-5" />,
    color: 'text-gray-400',
    bgColor: 'bg-gray-100 dark:bg-gray-800',
    label: '等待中',
  },
  analyzing: {
    icon: <Brain className="h-5 w-5" />,
    color: 'text-blue-500',
    bgColor: 'bg-blue-100 dark:bg-blue-900/30',
    label: '分析需求',
  },
  designing: {
    icon: <Sparkles className="h-5 w-5" />,
    color: 'text-purple-500',
    bgColor: 'bg-purple-100 dark:bg-purple-900/30',
    label: '设计架构',
  },
  generating: {
    icon: <FileCode className="h-5 w-5" />,
    color: 'text-green-500',
    bgColor: 'bg-green-100 dark:bg-green-900/30',
    label: '生成代码',
  },
  completed: {
    icon: <CheckCircle2 className="h-5 w-5" />,
    color: 'text-emerald-500',
    bgColor: 'bg-emerald-100 dark:bg-emerald-900/30',
    label: '已完成',
  },
  error: {
    icon: <XCircle className="h-5 w-5" />,
    color: 'text-red-500',
    bgColor: 'bg-red-100 dark:bg-red-900/30',
    label: '错误',
  },
};

/**
 * 打字机效果组件
 */
const TypewriterText: React.FC<{ text: string; speed?: number }> = ({
  text,
  speed = 30,
}) => {
  const [displayedText, setDisplayedText] = useState('');
  const [currentIndex, setCurrentIndex] = useState(0);

  useEffect(() => {
    // 重置
    setDisplayedText('');
    setCurrentIndex(0);
  }, [text]);

  useEffect(() => {
    if (currentIndex < text.length) {
      const timer = setTimeout(() => {
        setDisplayedText(prev => prev + text[currentIndex]);
        setCurrentIndex(prev => prev + 1);
      }, speed);

      return () => clearTimeout(timer);
    }
  }, [currentIndex, text, speed]);

  return (
    <span className="inline-block">
      {displayedText}
      {currentIndex < text.length && (
        <motion.span
          className="inline-block w-0.5 h-4 bg-current ml-0.5"
          animate={{ opacity: [1, 0] }}
          transition={{ duration: 0.5, repeat: Infinity, repeatType: 'reverse' }}
        />
      )}
    </span>
  );
};

/**
 * 脉冲圆点组件
 */
const PulsingDot: React.FC<{ color: string }> = ({ color }) => (
  <span className="relative flex h-3 w-3">
    <span
      className={cn(
        'absolute inline-flex h-full w-full animate-ping rounded-full opacity-75',
        color
      )}
    />
    <span
      className={cn(
        'relative inline-flex h-3 w-3 rounded-full',
        color
      )}
    />
  </span>
);

/**
 * AI思考过程指示器组件
 */
export const ThinkingIndicator: React.FC<ThinkingIndicatorProps> = ({
  message,
  duration,
  phase = 'idle',
  progress = 0,
  currentFile,
  completedFiles = 0,
  totalFiles = 0,
  showDetails = true,
  className,
  error,
}) => {
  const [elapsedTime, setElapsedTime] = useState(0);
  const config = phaseConfig[error ? 'error' : phase];

  // 计时器
  useEffect(() => {
    if (phase !== 'idle' && phase !== 'completed' && phase !== 'error') {
      const interval = setInterval(() => {
        setElapsedTime(prev => prev + 1);
      }, 1000);

      return () => clearInterval(interval);
    } else {
      setElapsedTime(0);
    }
  }, [phase]);

  /**
   * 格式化时间
   */
  const formatTime = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return mins > 0 ? `${mins}分${secs}秒` : `${secs}秒`;
  };

  const isActive = phase !== 'idle' && phase !== 'completed' && phase !== 'error';

  return (
    <div
      className={cn(
        'rounded-lg border transition-all duration-300',
        config.bgColor,
        'border-border/50',
        className
      )}
    >
      {/* 主内容区 */}
      <div className="p-4">
        {/* 头部：状态和阶段 */}
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-3">
            {/* 状态图标 */}
            <div className={cn('flex items-center gap-2', config.color)}>
              {isActive ? (
                <motion.div
                  animate={{ rotate: 360 }}
                  transition={{ duration: 2, repeat: Infinity, ease: 'linear' }}
                >
                  <Loader2 className="h-5 w-5" />
                </motion.div>
              ) : (
                config.icon
              )}
              <span className="font-medium">{config.label}</span>
            </div>

            {/* 活跃指示器 */}
            {isActive && <PulsingDot color="bg-green-500" />}
          </div>

          {/* 计时器 */}
          {isActive && (
            <div className="text-sm text-muted-foreground">
              已用时: {formatTime(elapsedTime)}
              {duration && (
                <span className="ml-1 text-xs opacity-70">
                  / 预计 {formatTime(Math.ceil(duration / 1000))}
                </span>
              )}
            </div>
          )}
        </div>

        {/* 思考消息 - 打字机效果 */}
        <AnimatePresence mode="wait">
          {(message || error) && (
            <motion.div
              key={message || error}
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 10 }}
              className={cn(
                'text-sm mb-3 min-h-[1.5rem]',
                error ? 'text-red-600 dark:text-red-400' : 'text-foreground/80'
              )}
            >
              {error ? (
                <span>{error}</span>
              ) : (
                <TypewriterText text={message || ''} speed={20} />
              )}
            </motion.div>
          )}
        </AnimatePresence>

        {/* 进度条 */}
        {(isActive || phase === 'completed') && (
          <div className="space-y-2">
            <Progress
              value={progress}
              className="h-2"
              gradient={isActive}
              animated
            />
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>{Math.round(progress)}%</span>
              {totalFiles > 0 && (
                <span>
                  {completedFiles} / {totalFiles} 文件
                </span>
              )}
            </div>
          </div>
        )}

        {/* 当前文件 */}
        {showDetails && currentFile && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            className="mt-3 pt-3 border-t border-border/30"
          >
            <div className="flex items-center gap-2 text-sm">
              <FileCode className="h-4 w-4 text-muted-foreground" />
              <span className="text-muted-foreground">正在生成:</span>
              <code className="px-2 py-0.5 rounded bg-muted text-xs font-mono">
                {currentFile}
              </code>
            </div>
          </motion.div>
        )}
      </div>

      {/* 底部统计 - 详细模式 */}
      {showDetails && totalFiles > 0 && (
        <div className="px-4 py-2 border-t border-border/30 bg-muted/20">
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <div className="flex items-center gap-4">
              <span className="flex items-center gap-1">
                <CheckCircle2 className="h-3 w-3 text-green-500" />
                {completedFiles} 完成
              </span>
              <span className="flex items-center gap-1">
                <Loader2 className="h-3 w-3 text-blue-500" />
                {totalFiles - completedFiles} 进行中
              </span>
            </div>
            <span>共 {totalFiles} 个文件</span>
          </div>
        </div>
      )}
    </div>
  );
};

export default ThinkingIndicator;
