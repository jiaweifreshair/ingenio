'use client';

/**
 * 设计方案详情面板组件
 *
 * 功能：
 * - 显示完整的设计方案详情
 * - 展示UI预览、设计说明、颜色方案等
 * - 支持滑动关闭
 */

import { useEffect, useRef } from 'react';
import Image from 'next/image';
import type { DesignScheme } from '@/types/design';

export interface DesignDetailPanelProps {
  /** 设计方案数据 */
  design: DesignScheme | null;
  /** 是否显示面板 */
  open: boolean;
  /** 关闭面板回调 */
  onClose: () => void;
  /** 选择方案回调 */
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
 * 设计方案详情面板组件
 */
export function DesignDetailPanel({
  design,
  open,
  onClose,
  onSelect,
}: DesignDetailPanelProps) {
  const panelRef = useRef<HTMLDivElement>(null);

  // 点击外部关闭面板
  useEffect(() => {
    if (!open) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (
        panelRef.current &&
        !panelRef.current.contains(event.target as Node)
      ) {
        onClose();
      }
    };

    // 延迟添加事件监听，避免立即触发
    const timer = setTimeout(() => {
      document.addEventListener('mousedown', handleClickOutside);
    }, 100);

    return () => {
      clearTimeout(timer);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [open, onClose]);

  // ESC键关闭面板
  useEffect(() => {
    if (!open) return;

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('keydown', handleEscape);
    return () => document.removeEventListener('keydown', handleEscape);
  }, [open, onClose]);

  if (!open || !design) return null;

  const variantName = getVariantName(design.variantId);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
      <div
        ref={panelRef}
        className="relative max-h-[90vh] w-full max-w-4xl overflow-y-auto rounded-2xl bg-white shadow-2xl"
      >
        {/* 关闭按钮 */}
        <button
          onClick={onClose}
          className="absolute right-4 top-4 z-10 flex h-10 w-10 items-center justify-center rounded-full bg-white/80 text-gray-600 backdrop-blur-sm transition-colors hover:bg-white hover:text-gray-900"
          aria-label="关闭"
        >
          <svg
            className="h-6 w-6"
            fill="none"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="2"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        {/* 预览图 */}
        <div className="relative h-96 w-full overflow-hidden rounded-t-2xl bg-gradient-to-br from-gray-100 to-gray-200">
          {design.preview ? (
            <Image
              src={design.preview}
              alt={variantName}
              fill
              className="object-contain"
            />
          ) : (
            <div className="flex h-full items-center justify-center text-gray-400">
              <svg
                className="h-24 w-24"
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
        </div>

        {/* 详情内容 */}
        <div className="p-8">
          {/* 方案名称 */}
          <h2 className="mb-4 text-3xl font-bold text-gray-900">
            {variantName}
          </h2>

          {/* 设计说明 */}
          <section className="mb-8">
            <h3 className="mb-3 text-lg font-semibold text-gray-900">
              设计说明
            </h3>
            <p className="text-gray-700 leading-relaxed">{design.style}</p>
          </section>

          {/* 风格关键词 */}
          <section className="mb-8">
            <h3 className="mb-3 text-lg font-semibold text-gray-900">
              风格关键词
            </h3>
            <div className="flex flex-wrap gap-2">
              {design.styleKeywords.map((keyword, index) => (
                <span
                  key={index}
                  className="rounded-full bg-blue-100 px-4 py-2 text-sm font-medium text-blue-700"
                >
                  {keyword}
                </span>
              ))}
            </div>
          </section>

          {/* 颜色方案 */}
          <section className="mb-8">
            <h3 className="mb-3 text-lg font-semibold text-gray-900">
              颜色方案
            </h3>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              <ColorItem
                label="主色调"
                color={design.colorTheme.primaryColor}
              />
              <ColorItem
                label="次要色"
                color={design.colorTheme.secondaryColor}
              />
              <ColorItem
                label="背景色"
                color={design.colorTheme.backgroundColor}
              />
              <ColorItem label="文字色" color={design.colorTheme.textColor} />
              <ColorItem label="强调色" color={design.colorTheme.accentColor} />
            </div>
          </section>

          {/* 布局特点 */}
          <section className="mb-8">
            <h3 className="mb-3 text-lg font-semibold text-gray-900">
              布局特点
            </h3>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <InfoItem label="布局类型" value={design.layoutType} />
              <InfoItem label="组件库" value={design.componentLibrary} />
            </div>
          </section>

          {/* 设计特点 */}
          {design.features && design.features.length > 0 && (
            <section className="mb-8">
              <h3 className="mb-3 text-lg font-semibold text-gray-900">
                设计特点
              </h3>
              <ul className="space-y-2">
                {design.features.map((feature, index) => (
                  <li key={index} className="flex items-start gap-2">
                    <svg
                      className="mt-1 h-5 w-5 flex-shrink-0 text-green-500"
                      fill="none"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth="2"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path d="M5 13l4 4L19 7" />
                    </svg>
                    <span className="text-gray-700">{feature}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          {/* 技术信息 */}
          {design.generationTimeMs && (
            <section className="mb-8">
              <h3 className="mb-3 text-lg font-semibold text-gray-900">
                技术信息
              </h3>
              <div className="rounded-lg bg-gray-50 p-4">
                <div className="text-sm text-gray-600">
                  生成耗时: {(design.generationTimeMs / 1000).toFixed(2)}秒
                </div>
              </div>
            </section>
          )}

          {/* 操作按钮 */}
          <div className="flex gap-4">
            <button
              onClick={onClose}
              className="flex-1 rounded-lg border border-gray-300 px-6 py-3 text-base font-medium text-gray-700 transition-colors hover:bg-gray-50"
            >
              关闭
            </button>
            <button
              onClick={() => {
                onSelect?.(design);
                onClose();
              }}
              className="flex-1 rounded-lg bg-blue-500 px-6 py-3 text-base font-medium text-white transition-colors hover:bg-blue-600"
            >
              使用此方案
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * 颜色项组件
 */
function ColorItem({ label, color }: { label: string; color: string }) {
  return (
    <div className="rounded-lg border border-gray-200 p-4">
      <div
        className="mb-3 h-16 w-full rounded-md shadow-sm"
        style={{ backgroundColor: color }}
      />
      <p className="mb-1 text-sm font-medium text-gray-900">{label}</p>
      <p className="text-xs font-mono text-gray-500">{color}</p>
    </div>
  );
}

/**
 * 信息项组件
 */
function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-gray-200 p-4">
      <p className="mb-1 text-sm font-medium text-gray-500">{label}</p>
      <p className="text-base font-medium text-gray-900">{value}</p>
    </div>
  );
}
