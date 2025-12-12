/**
 * Checkbox组件
 *
 * 简化的复选框组件，用于多选场景
 */
'use client';

import * as React from 'react';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface CheckboxProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, 'checked' | 'onChange'> {
  checked?: boolean;
  onCheckedChange?: (checked: boolean) => void;
}

export const Checkbox = React.forwardRef<HTMLButtonElement, CheckboxProps>(
  ({ checked = false, onCheckedChange, disabled = false, className, ...props }, ref) => {
    return (
      <button
        ref={ref}
        type="button"
        role="checkbox"
        aria-checked={checked}
        disabled={disabled}
        className={cn(
          'peer h-4 w-4 shrink-0 rounded-sm border border-primary ring-offset-background',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
          'disabled:cursor-not-allowed disabled:opacity-50',
          checked
            ? 'bg-primary text-primary-foreground'
            : 'bg-background',
          className
        )}
        onClick={(e) => {
          if (!disabled) {
            onCheckedChange?.(!checked);
          }
          props.onClick?.(e);
        }}
        {...props}
      >
        {checked && <Check className="h-3 w-3" />}
      </button>
    );
  }
);

Checkbox.displayName = 'Checkbox';
