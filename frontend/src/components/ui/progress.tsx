"use client"

import * as React from "react"
import { cn } from "@/lib/utils"
import { motion } from "framer-motion"

interface ProgressProps extends React.HTMLAttributes<HTMLDivElement> {
  value?: number
  /** 是否显示渐变效果 */
  gradient?: boolean
  /** 是否启用动画 */
  animated?: boolean
  /** 动画持续时间（秒） */
  animationDuration?: number
}

/**
 * Progress组件 - 增强版
 *
 * 增强特性：
 * - 平滑的Framer Motion动画
 * - 渐变色进度条（紫-蓝-粉渐变）
 * - 可配置动画持续时间
 * - 支持prefers-reduced-motion
 */
const Progress = React.forwardRef<HTMLDivElement, ProgressProps>(
  ({
    className,
    value = 0,
    gradient = false,
    animated = true,
    animationDuration = 0.5,
    ...props
  }, ref) => {
    const prefersReducedMotion = typeof window !== 'undefined'
      ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
      : false;

    const shouldAnimate = animated && !prefersReducedMotion;

    return (
      <div
        ref={ref}
        className={cn(
          "relative h-4 w-full overflow-hidden rounded-full bg-secondary",
          className
        )}
        {...props}
      >
        {shouldAnimate ? (
          <motion.div
            className={cn(
              "h-full w-full flex-1 transition-all",
              gradient
                ? "bg-gradient-to-r from-purple-500 via-blue-500 to-pink-500"
                : "bg-primary"
            )}
            initial={{ width: 0 }}
            animate={{ width: `${value || 0}%` }}
            transition={{
              duration: animationDuration,
              ease: [0.4, 0, 0.2, 1], // easeInOut cubic bezier
            }}
          />
        ) : (
          <div
            className={cn(
              "h-full w-full flex-1 transition-all",
              gradient
                ? "bg-gradient-to-r from-purple-500 via-blue-500 to-pink-500"
                : "bg-primary"
            )}
            style={{ transform: `translateX(-${100 - (value || 0)}%)` }}
          />
        )}
      </div>
    )
  }
)

Progress.displayName = "Progress"

export { Progress }
