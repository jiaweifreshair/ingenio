/**
 * PageTransition组件 - 页面切换过渡动画
 *
 * 特性：
 * - 淡入淡出 + 滑动动画
 * - 支持不同过渡方向
 * - 尊重prefers-reduced-motion
 * - 优化性能（使用transform和opacity）
 */
"use client"

import React from 'react'
import { motion, AnimatePresence, type Variants } from 'framer-motion'

type TransitionType = 'fade' | 'slide-up' | 'slide-down' | 'slide-left' | 'slide-right' | 'scale'

interface PageTransitionProps {
  children: React.ReactNode
  /** 过渡类型 */
  type?: TransitionType
  /** 动画持续时间（秒） */
  duration?: number
  /** 延迟（秒） */
  delay?: number
  /** 自定义类名 */
  className?: string
}

/**
 * 过渡动画变体配置
 */
const transitionVariants: Record<TransitionType, Variants> = {
  fade: {
    hidden: { opacity: 0 },
    visible: { opacity: 1 },
    exit: { opacity: 0 },
  },
  'slide-up': {
    hidden: { opacity: 0, y: 20 },
    visible: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: -20 },
  },
  'slide-down': {
    hidden: { opacity: 0, y: -20 },
    visible: { opacity: 1, y: 0 },
    exit: { opacity: 0, y: 20 },
  },
  'slide-left': {
    hidden: { opacity: 0, x: 20 },
    visible: { opacity: 1, x: 0 },
    exit: { opacity: 0, x: -20 },
  },
  'slide-right': {
    hidden: { opacity: 0, x: -20 },
    visible: { opacity: 1, x: 0 },
    exit: { opacity: 0, x: 20 },
  },
  scale: {
    hidden: { opacity: 0, scale: 0.95 },
    visible: { opacity: 1, scale: 1 },
    exit: { opacity: 0, scale: 0.95 },
  },
}

/**
 * 页面过渡组件
 */
export const PageTransition: React.FC<PageTransitionProps> = ({
  children,
  type = 'fade',
  duration = 0.3,
  delay = 0,
  className,
}) => {
  const prefersReducedMotion = typeof window !== 'undefined'
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false

  if (prefersReducedMotion) {
    return <div className={className}>{children}</div>
  }

  return (
    <motion.div
      className={className}
      initial="hidden"
      animate="visible"
      exit="exit"
      variants={transitionVariants[type]}
      transition={{
        duration,
        delay,
        ease: [0.4, 0, 0.2, 1], // easeInOut
      }}
    >
      {children}
    </motion.div>
  )
}

/**
 * 列表项Stagger动画组件
 */
interface StaggerListProps {
  children: React.ReactNode[]
  /** 每个子项延迟时间（秒） */
  staggerDelay?: number
  /** 自定义类名 */
  className?: string
}

export const StaggerList: React.FC<StaggerListProps> = ({
  children,
  staggerDelay = 0.07,
  className,
}) => {
  const prefersReducedMotion = typeof window !== 'undefined'
    ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
    : false

  if (prefersReducedMotion) {
    return <div className={className}>{children}</div>
  }

  const containerVariants: Variants = {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: staggerDelay,
      },
    },
  }

  const itemVariants: Variants = {
    hidden: { opacity: 0, y: 20 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.4,
        ease: [0.4, 0, 0.2, 1],
      },
    },
  }

  return (
    <motion.div
      className={className}
      variants={containerVariants}
      initial="hidden"
      animate="visible"
    >
      {React.Children.map(children, (child, index) => (
        <motion.div key={index} variants={itemVariants}>
          {child}
        </motion.div>
      ))}
    </motion.div>
  )
}

/**
 * 模态框动画包装器
 */
export const ModalTransition: React.FC<{
  children: React.ReactNode
  isOpen: boolean
}> = ({ children, isOpen }) => {
  return (
    <AnimatePresence mode="wait">
      {isOpen && (
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.95 }}
          transition={{
            duration: 0.2,
            ease: [0.4, 0, 0.2, 1],
          }}
        >
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  )
}
