'use client';

/**
 * 设计方案对比视图组件
 *
 * 功能：
 * - 左右并排对比2个设计方案
 * - 显示多个对比维度
 * - 支持切换对比方案
 */

import { useState } from 'react';
import Image from 'next/image';
import type { DesignScheme } from '@/types/design';

export interface DesignCompareViewProps {
  /** 所有可用的设计方案 */
  designs: DesignScheme[];
  /** 初始选中的左侧方案索引 */
  initialLeftIndex?: number;
  /** 初始选中的右侧方案索引 */
  initialRightIndex?: number;
  /** 关闭对比视图回调 */
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
 * 设计方案对比视图组件
 */
export function DesignCompareView({
  designs,
  initialLeftIndex = 0,
  initialRightIndex = 1,
  onClose,
  onSelect,
}: DesignCompareViewProps) {
  const [leftIndex, setLeftIndex] = useState(initialLeftIndex);
  const [rightIndex, setRightIndex] = useState(initialRightIndex);

  const leftDesign = designs[leftIndex];
  const rightDesign = designs[rightIndex];

  if (!leftDesign || !rightDesign) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 bg-white overflow-y-auto">
      {/* 顶部操作栏 */}
      <div className="sticky top-0 z-10 border-b border-gray-200 bg-white/80 backdrop-blur-sm">
        <div className="container mx-auto flex items-center justify-between px-6 py-4">
          <h2 className="text-2xl font-bold text-gray-900">方案对比</h2>
          <button
            onClick={onClose}
            className="flex h-10 w-10 items-center justify-center rounded-full text-gray-600 transition-colors hover:bg-gray-100 hover:text-gray-900"
            aria-label="关闭对比"
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
        </div>
      </div>

      {/* 对比内容 */}
      <div className="container mx-auto px-6 py-8">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
          {/* 左侧方案 */}
          <CompareColumn
            design={leftDesign}
            designs={designs}
            currentIndex={leftIndex}
            onIndexChange={setLeftIndex}
            onSelect={onSelect}
          />

          {/* 右侧方案 */}
          <CompareColumn
            design={rightDesign}
            designs={designs}
            currentIndex={rightIndex}
            onIndexChange={setRightIndex}
            onSelect={onSelect}
          />
        </div>
      </div>
    </div>
  );
}

/**
 * 对比列组件
 */
interface CompareColumnProps {
  design: DesignScheme;
  designs: DesignScheme[];
  currentIndex: number;
  onIndexChange: (index: number) => void;
  onSelect?: (design: DesignScheme) => void;
}

function CompareColumn({
  design,
  designs,
  currentIndex,
  onIndexChange,
  onSelect,
}: CompareColumnProps) {
  const variantName = getVariantName(design.variantId);

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
      {/* 方案选择器 */}
      <div className="mb-6">
        <label className="mb-2 block text-sm font-medium text-gray-700">
          选择方案
        </label>
        <select
          value={currentIndex}
          onChange={(e) => onIndexChange(Number(e.target.value))}
          className="w-full rounded-lg border border-gray-300 px-4 py-2 text-gray-900 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
        >
          {designs.map((d, index) => (
            <option key={d.variantId} value={index}>
              {getVariantName(d.variantId)}
            </option>
          ))}
        </select>
      </div>

      {/* 预览图 */}
      <div className="relative mb-6 h-64 w-full overflow-hidden rounded-lg bg-gradient-to-br from-gray-100 to-gray-200">
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
      </div>

      {/* 对比维度 */}
      <div className="space-y-6">
        {/* 视觉风格 */}
        <CompareSection title="视觉风格">
          <p className="text-sm text-gray-700">{design.style}</p>
          <div className="mt-2 flex flex-wrap gap-2">
            {design.styleKeywords.slice(0, 3).map((keyword, index) => (
              <span
                key={index}
                className="rounded-full bg-blue-100 px-3 py-1 text-xs font-medium text-blue-700"
              >
                {keyword}
              </span>
            ))}
          </div>
        </CompareSection>

        {/* 颜色方案 */}
        <CompareSection title="颜色方案">
          <div className="flex gap-2">
            <ColorDot
              color={design.colorTheme.primaryColor}
              label="主色调"
            />
            <ColorDot
              color={design.colorTheme.secondaryColor}
              label="次要色"
            />
            <ColorDot
              color={design.colorTheme.accentColor}
              label="强调色"
            />
          </div>
        </CompareSection>

        {/* 布局特点 */}
        <CompareSection title="布局特点">
          <div className="space-y-2 text-sm text-gray-700">
            <p>类型: {design.layoutType}</p>
            <p>组件库: {design.componentLibrary}</p>
          </div>
        </CompareSection>

        {/* 设计特点 */}
        {design.features && design.features.length > 0 && (
          <CompareSection title="设计特点">
            <ul className="space-y-1">
              {design.features.slice(0, 3).map((feature, index) => (
                <li key={index} className="flex items-start gap-2 text-sm text-gray-700">
                  <svg
                    className="mt-0.5 h-4 w-4 flex-shrink-0 text-green-500"
                    fill="none"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path d="M5 13l4 4L19 7" />
                  </svg>
                  <span>{feature}</span>
                </li>
              ))}
            </ul>
          </CompareSection>
        )}

        {/* 生成耗时 */}
        {design.generationTimeMs && (
          <CompareSection title="技术指标">
            <p className="text-sm text-gray-700">
              生成耗时: {(design.generationTimeMs / 1000).toFixed(2)}秒
            </p>
          </CompareSection>
        )}
      </div>

      {/* 选择按钮 */}
      <button
        onClick={() => onSelect?.(design)}
        className="mt-6 w-full rounded-lg bg-blue-500 px-6 py-3 text-base font-medium text-white transition-colors hover:bg-blue-600"
      >
        使用此方案
      </button>
    </div>
  );
}

/**
 * 对比区块组件
 */
function CompareSection({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <h4 className="mb-2 text-sm font-semibold text-gray-900">{title}</h4>
      {children}
    </div>
  );
}

/**
 * 颜色圆点组件
 */
function ColorDot({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex flex-col items-center gap-1">
      <div
        className="h-10 w-10 rounded-full shadow-sm ring-1 ring-gray-200"
        style={{ backgroundColor: color }}
        title={label}
      />
      <span className="text-xs text-gray-500">{label}</span>
    </div>
  );
}
