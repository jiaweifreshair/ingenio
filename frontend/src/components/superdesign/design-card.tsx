'use client';

/**
 * 设计方案卡片组件
 *
 * 功能：
 * - 展示单个设计方案的预览
 * - 显示方案名称、风格关键词、颜色主题
 * - 提供选择和查看详情按钮
 */

import { useState } from 'react';
import Image from 'next/image';
import type { DesignScheme } from '@/types/design';

export interface DesignCardProps {
  /** 设计方案数据 */
  design: DesignScheme;
  /** 是否已选中 */
  selected?: boolean;
  /** 点击查看详情回调 */
  onViewDetail?: (design: DesignScheme) => void;
  /** 点击选择按钮回调 */
  onSelect?: (design: DesignScheme) => void;
}

/**
 * 根据方案ID获取方案名称
 */
function getVariantName(variantId: string): string {
  const names: Record<string, string> = {
    A: '方案A：现代极简',
    B: '方案B：活力时尚',
    C: '方案C：经典专业',
  };
  return names[variantId] || `方案${variantId}`;
}

/**
 * 设计方案卡片组件
 */
export function DesignCard({
  design,
  selected = false,
  onViewDetail,
  onSelect,
}: DesignCardProps) {
  const [imageError, setImageError] = useState(false);
  const variantName = getVariantName(design.variantId);

  return (
    <div
      className={`
        relative rounded-xl border-2 bg-white shadow-md transition-all duration-300
        hover:shadow-xl hover:-translate-y-1
        ${selected ? 'border-blue-500 ring-2 ring-blue-200' : 'border-gray-200'}
      `}
    >
      {/* 选中标识 */}
      {selected && (
        <div className="absolute -top-3 -right-3 z-10 flex h-8 w-8 items-center justify-center rounded-full bg-blue-500 text-white shadow-lg">
          <svg
            className="h-5 w-5"
            fill="none"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="2"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path d="M5 13l4 4L19 7" />
          </svg>
        </div>
      )}

      {/* 预览图 */}
      <div
        className="relative h-64 w-full cursor-pointer overflow-hidden rounded-t-xl bg-gradient-to-br from-gray-100 to-gray-200"
        onClick={() => onViewDetail?.(design)}
      >
        {design.preview && !imageError ? (
          <Image
            src={design.preview}
            alt={variantName}
            fill
            className="object-cover"
            onError={() => setImageError(true)}
          />
        ) : (
          <div className="flex h-full items-center justify-center text-gray-400">
            <svg
              className="h-16 w-16"
              fill="none"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="1.5"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
          </div>
        )}

        {/* 悬浮查看详情提示 */}
        <div className="absolute inset-0 flex items-center justify-center bg-black/0 transition-all hover:bg-black/40">
          <span className="translate-y-4 text-sm font-medium text-white opacity-0 transition-all group-hover:translate-y-0 group-hover:opacity-100">
            点击查看详情
          </span>
        </div>
      </div>

      {/* 内容区 */}
      <div className="p-6">
        {/* 方案名称 */}
        <h3 className="mb-2 text-xl font-bold text-gray-900">{variantName}</h3>

        {/* 设计风格描述 */}
        <p className="mb-4 text-sm text-gray-600 line-clamp-2">{design.style}</p>

        {/* 风格关键词标签 */}
        <div className="mb-4 flex flex-wrap gap-2">
          {design.styleKeywords.slice(0, 4).map((keyword, index) => (
            <span
              key={index}
              className="rounded-full bg-blue-100 px-3 py-1 text-xs font-medium text-blue-700"
            >
              {keyword}
            </span>
          ))}
        </div>

        {/* 颜色主题展示 */}
        <div className="mb-4">
          <p className="mb-2 text-xs font-medium text-gray-500">配色方案</p>
          <div className="flex gap-2">
            <div
              className="h-8 w-8 rounded-md shadow-sm ring-1 ring-gray-200"
              style={{ backgroundColor: design.colorTheme.primaryColor }}
              title={`主色调: ${design.colorTheme.primaryColor}`}
            />
            <div
              className="h-8 w-8 rounded-md shadow-sm ring-1 ring-gray-200"
              style={{ backgroundColor: design.colorTheme.secondaryColor }}
              title={`次要色: ${design.colorTheme.secondaryColor}`}
            />
            <div
              className="h-8 w-8 rounded-md shadow-sm ring-1 ring-gray-200"
              style={{ backgroundColor: design.colorTheme.accentColor }}
              title={`强调色: ${design.colorTheme.accentColor}`}
            />
          </div>
        </div>

        {/* 布局类型 */}
        <div className="mb-4 flex items-center gap-2 text-xs text-gray-500">
          <svg
            className="h-4 w-4"
            fill="none"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="2"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path d="M9 17V7m0 10a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h2a2 2 0 012 2m0 10a2 2 0 002 2h2a2 2 0 002-2M9 7a2 2 0 012-2h2a2 2 0 012 2m0 10V7m0 10a2 2 0 002 2h2a2 2 0 002-2V7a2 2 0 00-2-2h-2a2 2 0 00-2 2" />
          </svg>
          <span>布局: {design.layoutType}</span>
        </div>

        {/* 生成耗时 */}
        {design.generationTimeMs && (
          <div className="mb-4 text-xs text-gray-400">
            生成耗时: {(design.generationTimeMs / 1000).toFixed(2)}s
          </div>
        )}

        {/* 操作按钮 */}
        <div className="flex gap-3">
          <button
            onClick={() => onViewDetail?.(design)}
            className="flex-1 rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50"
          >
            查看详情
          </button>
          <button
            onClick={() => onSelect?.(design)}
            className={`
              flex-1 rounded-lg px-4 py-2 text-sm font-medium transition-colors
              ${
                selected
                  ? 'bg-blue-500 text-white hover:bg-blue-600'
                  : 'bg-blue-500 text-white hover:bg-blue-600'
              }
            `}
          >
            {selected ? '已选择' : '使用此方案'}
          </button>
        </div>
      </div>
    </div>
  );
}
