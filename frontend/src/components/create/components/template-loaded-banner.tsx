/**
 * TemplateLoadedBanner组件
 * 模板加载提示横幅
 *
 * 功能：
 * - 显示当前加载的模板名称
 * - 提供"更换模板"按钮
 * - 提供"清除"按钮
 * - 绿色主题，突出显示
 *
 * @author Ingenio Team
 * @since V2.0
 */

'use client';

import React from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Library, X } from 'lucide-react';
import type { LoadedTemplate } from '@/types/requirement-form';

/**
 * TemplateLoadedBanner组件Props
 */
export interface TemplateLoadedBannerProps {
  /** 加载的模板信息 */
  template: LoadedTemplate;
  /** 清除模板回调 */
  onClear: () => void;
  /** 自定义类名 */
  className?: string;
}

/**
 * TemplateLoadedBanner组件
 * 模板加载提示横幅，绿色主题
 */
export function TemplateLoadedBanner({
  template,
  onClear,
  className,
}: TemplateLoadedBannerProps): React.ReactElement {
  return (
    <div
      className={`flex items-center justify-between rounded-2xl border border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/10 p-4 backdrop-blur-xl ${className || ''}`}
    >
      {/* 左侧：图标 + 文字信息 */}
      <div className="flex items-center gap-3">
        <Library className="h-5 w-5 text-green-600 dark:text-green-400" />
        <div>
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-green-900 dark:text-green-100">
              使用模板
            </span>
            <Badge
              variant="secondary"
              className="bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300"
            >
              {template.name}
            </Badge>
          </div>
          <p className="text-xs text-green-700 dark:text-green-400 mt-0.5">
            已自动填充模板内容，您可以继续编辑或直接提交
          </p>
        </div>
      </div>

      {/* 右侧：操作按钮 */}
      <div className="flex items-center gap-2">
        {/* 更换模板按钮 */}
        <Link href="/templates">
          <Button
            variant="outline"
            size="sm"
            className="border-green-200 dark:border-green-800 hover:bg-green-100 dark:hover:bg-green-900/20"
          >
            <Library className="mr-1.5 h-3.5 w-3.5" />
            更换模板
          </Button>
        </Link>

        {/* 清除按钮 */}
        <Button
          variant="ghost"
          size="sm"
          onClick={onClear}
          className="hover:bg-green-100 dark:hover:bg-green-900/20"
        >
          <X className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
