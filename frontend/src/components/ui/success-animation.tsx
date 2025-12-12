/**
 * SuccessAnimation组件 - 成功庆祝动画
 *
 * 特性：
 * - 对勾动画（绘制路径动画）
 * - 彩纸庆祝效果（可选）
 * - 缩放弹跳动画
 * - 支持自定义颜色和大小
 */
"use client"

import React, { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { cn } from '@/lib/utils'
import { CheckCircle2 } from 'lucide-react'

interface SuccessAnimationProps {
  /** 是否显示动画 */
  show?: boolean
  /** 大小（px） */
  size?: number
  /** 颜色 */
  color?: string
  /** 是否显示彩纸 */
  showConfetti?: boolean
  /** 动画完成回调 */
  onComplete?: () => void
  /** 自定义类名 */
  className?: string
}

/**
 * 彩纸粒子组件
 */
const ConfettiParticle: React.FC<{
  delay: number
  x: number
  color: string
}> = ({ delay, x, color }) => {
  return (
    <motion.div
      className="absolute w-2 h-2 rounded-sm"
      style={{
        backgroundColor: color,
        left: '50%',
        top: '50%',
      }}
      initial={{ scale: 0, x: 0, y: 0, opacity: 1 }}
      animate={{
        scale: [0, 1, 0.8],
        x: [0, x, x * 1.2],
        y: [0, -100, -200],
        opacity: [1, 1, 0],
        rotate: [0, 180, 360],
      }}
      transition={{
        duration: 1.2,
        delay,
        ease: [0.4, 0, 0.2, 1],
      }}
    />
  )
}

/**
 * 成功动画组件
 */
export const SuccessAnimation: React.FC<SuccessAnimationProps> = ({
  show = true,
  size = 64,
  color = '#22c55e', // green-500
  showConfetti = false,
  onComplete,
  className,
}) => {
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    if (show) {
      setMounted(true)
      // 动画完成后回调
      const timer = setTimeout(() => {
        onComplete?.()
      }, 1000)
      return () => clearTimeout(timer)
    }
  }, [show, onComplete])

  if (!show || !mounted) return null

  // 彩纸颜色配置
  const confettiColors = [
    '#ef4444', // red
    '#f59e0b', // amber
    '#22c55e', // green
    '#3b82f6', // blue
    '#a855f7', // purple
    '#ec4899', // pink
  ]

  // 彩纸位置配置
  const confettiPositions = [
    { x: -60, delay: 0 },
    { x: -40, delay: 0.05 },
    { x: -20, delay: 0.1 },
    { x: 0, delay: 0.15 },
    { x: 20, delay: 0.1 },
    { x: 40, delay: 0.05 },
    { x: 60, delay: 0 },
  ]

  return (
    <div className={cn("relative inline-flex items-center justify-center", className)}>
      {/* 成功图标 - 弹跳缩放动画 */}
      <motion.div
        initial={{ scale: 0, rotate: -180 }}
        animate={{ scale: 1, rotate: 0 }}
        transition={{
          type: "spring",
          stiffness: 260,
          damping: 20,
          delay: 0.1,
        }}
      >
        <CheckCircle2
          size={size}
          style={{ color }}
          strokeWidth={2.5}
        />
      </motion.div>

      {/* 彩纸效果 */}
      {showConfetti && (
        <div className="absolute inset-0 pointer-events-none">
          {confettiPositions.map((pos, i) => (
            <ConfettiParticle
              key={i}
              delay={pos.delay}
              x={pos.x}
              color={confettiColors[i % confettiColors.length]}
            />
          ))}
        </div>
      )}

      {/* 光圈扩散动画 */}
      <motion.div
        className="absolute inset-0 rounded-full border-4 opacity-0"
        style={{ borderColor: color }}
        initial={{ scale: 1, opacity: 0.6 }}
        animate={{ scale: 1.5, opacity: 0 }}
        transition={{
          duration: 0.6,
          ease: "easeOut",
          delay: 0.2,
        }}
      />
    </div>
  )
}

/**
 * 成功Toast - 带动画的成功提示
 */
export const SuccessToast: React.FC<{
  title: string
  description?: string
  onClose?: () => void
}> = ({ title, description, onClose }) => {
  useEffect(() => {
    const timer = setTimeout(() => {
      onClose?.()
    }, 3000)
    return () => clearTimeout(timer)
  }, [onClose])

  return (
    <motion.div
      className="flex items-start gap-3 rounded-lg border bg-card p-4 shadow-lg"
      initial={{ opacity: 0, y: 50, scale: 0.3 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, scale: 0.5, transition: { duration: 0.2 } }}
      transition={{
        type: "spring",
        stiffness: 300,
        damping: 25,
      }}
    >
      <SuccessAnimation size={24} showConfetti={false} />
      <div className="flex-1">
        <p className="font-semibold text-sm">{title}</p>
        {description && (
          <p className="text-sm text-muted-foreground mt-1">{description}</p>
        )}
      </div>
    </motion.div>
  )
}
