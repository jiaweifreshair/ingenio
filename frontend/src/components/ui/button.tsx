"use client"

import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";

/**
 * Button组件变体定义
 * 使用CVA进行样式变体管理
 *
 * 增强特性：
 * - hover时轻微上浮（-translate-y-0.5）
 * - 阴影扩大效果
 * - active时轻微缩小（scale-95）
 * - 平滑过渡动画
 */
const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-lg text-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 active:scale-95 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-primary/90 shadow-sm hover:shadow-lg hover:-translate-y-0.5",
        destructive: "bg-destructive text-destructive-foreground hover:bg-destructive/90 shadow-sm hover:shadow-md",
        outline: "border border-border bg-background hover:bg-accent hover:text-accent-foreground hover:shadow-md hover:border-primary/50",
        secondary: "bg-secondary text-secondary-foreground hover:bg-secondary/80 hover:shadow-sm",
        ghost: "hover:bg-accent hover:text-accent-foreground",
        link: "text-primary underline-offset-4 hover:underline",
      },
      size: {
        default: "h-10 px-4 py-2",
        sm: "h-9 rounded-md px-3",
        lg: "h-11 rounded-lg px-8",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

/**
 * Button组件Props
 */
export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  /** 是否作为子组件的插槽 */
  asChild?: boolean;
  /** 是否禁用动画 */
  disableAnimation?: boolean;
}

/**
 * Button组件 - 增强版
 *
 * 秒构AI按钮组件，支持多种变体和尺寸
 *
 * 增强特性：
 * - Framer Motion弹性动画
 * - hover/tap微交互
 * - 尊重prefers-reduced-motion
 */
const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, disableAnimation = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    const prefersReducedMotion = typeof window !== 'undefined'
      ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
      : false;

    const shouldAnimate = !disableAnimation && !prefersReducedMotion;

    if (shouldAnimate && !asChild) {
      /* eslint-disable @typescript-eslint/no-unused-vars */
      // 从props中排除可能与framer-motion冲突的HTML事件
      const {
        onDrag,
        onDragEnd,
        onDragStart,
        onDragEnter,
        onDragLeave,
        onDragOver,
        onDrop,
        onAnimationStart,
        onAnimationEnd,
        onAnimationIteration,
        ...motionProps
      } = props as React.ButtonHTMLAttributes<HTMLButtonElement>;

      return (
        <motion.button
          className={cn(buttonVariants({ variant, size, className }))}
          ref={ref}
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          transition={{
            type: "spring",
            stiffness: 400,
            damping: 17,
          }}
          {...motionProps}
        />
      );
    }

    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  }
);
Button.displayName = "Button";

export { Button, buttonVariants };
