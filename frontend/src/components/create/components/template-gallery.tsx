/**
 * TemplateGallery组件
 * 模板横向滚动展示 + "浏览全部"按钮
 *
 * 功能：
 * - 横向滚动的模板卡片列表
 * - 点击模板填充输入框
 * - 浏览全部模板按钮（跳转到/templates）
 * - 响应式设计
 *
 * @author Ingenio Team
 * @since V2.0
 */

'use client';

import React from 'react';
import Link from 'next/link';
import { ScrollArea, ScrollBar } from '@/components/ui/scroll-area';
import { TemplateCard, type TemplateCardProps } from '@/components/home/template-card';
import { Library, ChevronRight } from 'lucide-react';

/**
 * TemplateGallery组件Props
 */
export interface TemplateGalleryProps {
  /** 模板列表 */
  templates: TemplateCardProps[];
  /** 模板点击回调 */
  onTemplateClick: (template: { id: string; title: string; description: string }) => void;
  /** 自定义类名 */
  className?: string;
}

/**
 * TemplateGallery组件
 * 横向滚动的模板展示区
 */
export function TemplateGallery({
  templates,
  onTemplateClick,
  className,
}: TemplateGalleryProps): React.ReactElement {
  return (
    <div className={`space-y-4 ${className || ''}`}>
      {/* 标题行 */}
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-muted-foreground">快速模板</h2>
        <span className="text-xs text-muted-foreground">点击填充 →</span>
      </div>

      {/* 横向滚动的模板卡片 */}
      <ScrollArea className="w-full whitespace-nowrap">
        <div className="flex gap-4 pb-4">
          {templates.map((template) => (
            <div key={template.id} data-template={template.id}>
              <TemplateCard {...template} onClick={onTemplateClick} />
            </div>
          ))}
        </div>
        <ScrollBar orientation="horizontal" />
      </ScrollArea>

      {/* 浏览全部模板按钮 */}
      <div className="flex justify-center pt-2">
        <Link
          href="/templates"
          className="group flex items-center gap-2 rounded-xl border-2 border-dashed border-border/50 bg-card/30 px-6 py-3 text-sm font-medium text-muted-foreground backdrop-blur-xl transition-all hover:border-primary/50 hover:bg-card/50 hover:text-primary hover:shadow-lg"
        >
          <Library className="h-4 w-4 transition-transform group-hover:scale-110" />
          浏览全部模板
          <ChevronRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
        </Link>
      </div>
    </div>
  );
}
