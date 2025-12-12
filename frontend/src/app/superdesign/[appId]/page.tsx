'use client';

/**
 * SuperDesign设计方案选择页面
 *
 * 路由: /superdesign/[appId]
 *
 * 功能：
 * 1. 展示3个AI生成的设计方案（A/B/C）
 * 2. 查看方案详情
 * 3. 对比不同方案
 * 4. 选择最终使用的方案并跳转到代码生成
 */

import { useEffect, useState, use } from "react";
import { useRouter } from 'next/navigation';
import { DesignCard, DesignDetailPanel, DesignCompareView } from '@/components/superdesign';
import { generateDesigns } from '@/lib/api/superdesign';
import { getAppSpec } from '@/lib/api/appspec';
import type { DesignScheme, EntityInfo } from '@/types/design';

interface PageProps {
  params: Promise<{
    appId: string;
  }>;
}

/**
 * 设计方案选择主页面
 */
export default function SuperDesignPage({ params }: PageProps) {
  const router = useRouter();
  const { appId } = use(params);

  // 状态管理
  const [designs, setDesigns] = useState<DesignScheme[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDesign, setSelectedDesign] = useState<DesignScheme | null>(null);
  const [detailDesign, setDetailDesign] = useState<DesignScheme | null>(null);
  const [showDetail, setShowDetail] = useState(false);
  const [showCompare, setShowCompare] = useState(false);

  // 加载设计方案
  useEffect(() => {
    loadDesigns();
  }, [appId]);

  /**
   * 加载设计方案
   *
   * 流程：
   * 1. 从后端获取AppSpec数据（包含规划结果）
   * 2. 从planResult.modules中提取实体信息
   * 3. 构建DesignRequest并调用SuperDesign API生成3个方案
   */
  async function loadDesigns() {
    try {
      setLoading(true);
      setError(null);

      // Step 1: 获取AppSpec数据
      const appSpecResponse = await getAppSpec(appId);
      if (!appSpecResponse.success || !appSpecResponse.data) {
        throw new Error(appSpecResponse.error || '无法获取AppSpec数据');
      }

      const appSpec = appSpecResponse.data;

      // Step 2: 从planResult.modules提取实体信息
      const entities: EntityInfo[] = [];
      if (appSpec.planResult?.modules) {
        for (const planModule of appSpec.planResult.modules) {
          // 将模块的数据模型转换为实体信息
          if (planModule.dataModels && planModule.dataModels.length > 0) {
            for (const modelName of planModule.dataModels) {
              entities.push({
                name: modelName.toLowerCase(),
                displayName: modelName,
                primaryFields: ['id', 'name'], // 使用默认字段，实际应从schema中提取
                viewType: 'list' as const,
              });
            }
          }
        }
      }

      // 如果没有提取到实体，使用一个默认实体避免API调用失败
      if (entities.length === 0) {
        entities.push({
          name: 'item',
          displayName: '数据项',
          primaryFields: ['id', 'name'],
          viewType: 'list' as const,
        });
      }

      // Step 3: 构建DesignRequest
      const designRequest = {
        taskId: appId,
        userPrompt: appSpec.userRequirement || '未提供需求描述',
        entities,
        targetPlatform: 'android' as const,
        uiFramework: 'compose_multiplatform' as const,
        colorScheme: 'light' as const,
        includeAssets: true,
      };

      // Step 4: 调用SuperDesign API生成设计方案
      const result = await generateDesigns(designRequest);
      setDesigns(result);
    } catch (err) {
      console.error('加载设计方案失败:', err);
      setError(err instanceof Error ? err.message : '加载设计方案失败');
    } finally {
      setLoading(false);
    }
  }

  /**
   * 查看方案详情
   */
  function handleViewDetail(design: DesignScheme) {
    setDetailDesign(design);
    setShowDetail(true);
  }

  /**
   * 关闭详情面板
   */
  function handleCloseDetail() {
    setShowDetail(false);
    // 延迟清除数据，等待动画完成
    setTimeout(() => setDetailDesign(null), 300);
  }

  /**
   * 选择设计方案
   */
  function handleSelectDesign(design: DesignScheme) {
    setSelectedDesign(design);

    // 显示确认对话框
    if (confirm(`确认使用${getVariantName(design.variantId)}吗？\n\n选择后将跳转到代码生成页面。`)) {
      // 跳转到代码生成页面
      router.push(`/wizard/${appId}?design=${design.variantId}`);
    }
  }

  /**
   * 打开对比视图
   */
  function handleOpenCompare() {
    if (designs.length < 2) {
      alert('至少需要2个方案才能进行对比');
      return;
    }
    setShowCompare(true);
  }

  /**
   * 关闭对比视图
   */
  function handleCloseCompare() {
    setShowCompare(false);
  }

  /**
   * 重新生成设计方案
   */
  function handleRegenerate() {
    if (confirm('确认重新生成设计方案吗？\n\n这将丢弃当前所有方案。')) {
      loadDesigns();
    }
  }

  // 加载中状态
  if (loading) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50">
        <div className="container mx-auto px-6 py-12">
          <LoadingState />
        </div>
      </div>
    );
  }

  // 错误状态
  if (error) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50">
        <div className="container mx-auto px-6 py-12">
          <ErrorState error={error} onRetry={loadDesigns} />
        </div>
      </div>
    );
  }

  // 对比视图
  if (showCompare) {
    return (
      <DesignCompareView
        designs={designs}
        onClose={handleCloseCompare}
        onSelect={handleSelectDesign}
      />
    );
  }

  // 主界面
  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50">
      <div className="container mx-auto px-6 py-12">
        {/* 页面标题 */}
        <div className="mb-8">
          <h1 className="mb-3 text-4xl font-bold text-gray-900">
            选择设计方案
          </h1>
          <p className="text-lg text-gray-600">
            AI为您生成了3个不同风格的设计方案，请选择最适合您的方案
          </p>
        </div>

        {/* 操作栏 */}
        <div className="mb-8 flex flex-wrap items-center justify-between gap-4">
          <div className="flex gap-3">
            <button
              onClick={handleOpenCompare}
              disabled={designs.length < 2}
              className="flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <svg
                className="h-5 w-5"
                fill="none"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
              </svg>
              方案对比
            </button>

            <button
              onClick={handleRegenerate}
              className="flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50"
            >
              <svg
                className="h-5 w-5"
                fill="none"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="2"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              重新生成
            </button>
          </div>

          {selectedDesign && (
            <div className="rounded-lg bg-blue-50 px-4 py-2 text-sm font-medium text-blue-700">
              已选择: {getVariantName(selectedDesign.variantId)}
            </div>
          )}
        </div>

        {/* 设计方案列表 */}
        {designs.length > 0 ? (
          <div className="grid grid-cols-1 gap-8 md:grid-cols-2 lg:grid-cols-3">
            {designs.map((design) => (
              <DesignCard
                key={design.variantId}
                design={design}
                selected={selectedDesign?.variantId === design.variantId}
                onViewDetail={handleViewDetail}
                onSelect={handleSelectDesign}
              />
            ))}
          </div>
        ) : (
          <div className="rounded-xl border-2 border-dashed border-gray-300 bg-white p-12 text-center">
            <svg
              className="mx-auto mb-4 h-16 w-16 text-gray-400"
              fill="none"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="1.5"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path d="M9.172 16.172a4 4 0 015.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p className="text-gray-600">暂无设计方案</p>
          </div>
        )}

        {/* 提示信息 */}
        <div className="mt-12 rounded-xl bg-blue-50 p-6">
          <h3 className="mb-3 flex items-center gap-2 text-lg font-semibold text-blue-900">
            <svg
              className="h-6 w-6"
              fill="none"
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth="2"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            提示
          </h3>
          <ul className="space-y-2 text-sm text-blue-800">
            <li className="flex items-start gap-2">
              <span className="mt-1">•</span>
              <span>点击方案卡片查看详细信息</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="mt-1">•</span>
              <span>使用&quot;方案对比&quot;功能查看方案之间的差异</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="mt-1">•</span>
              <span>选择方案后将自动跳转到代码生成页面</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="mt-1">•</span>
              <span>如果不满意当前方案，可以点击&quot;重新生成&quot;</span>
            </li>
          </ul>
        </div>
      </div>

      {/* 详情面板 */}
      <DesignDetailPanel
        design={detailDesign}
        open={showDetail}
        onClose={handleCloseDetail}
        onSelect={handleSelectDesign}
      />
    </div>
  );
}

/**
 * 加载状态组件
 */
function LoadingState() {
  return (
    <div className="text-center">
      <div className="mb-6 inline-block h-16 w-16 animate-spin rounded-full border-4 border-blue-200 border-t-blue-600" />
      <h2 className="mb-2 text-2xl font-bold text-gray-900">
        AI正在生成设计方案...
      </h2>
      <p className="text-gray-600">这通常需要5-10秒，请稍候</p>
    </div>
  );
}

/**
 * 错误状态组件
 */
function ErrorState({
  error,
  onRetry,
}: {
  error: string;
  onRetry: () => void;
}) {
  return (
    <div className="text-center">
      <div className="mb-6 inline-flex h-16 w-16 items-center justify-center rounded-full bg-red-100 text-red-600">
        <svg
          className="h-8 w-8"
          fill="none"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="2"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </div>
      <h2 className="mb-2 text-2xl font-bold text-gray-900">加载失败</h2>
      <p className="mb-6 text-gray-600">{error}</p>
      <button
        onClick={onRetry}
        className="rounded-lg bg-blue-500 px-6 py-3 text-base font-medium text-white transition-colors hover:bg-blue-600"
      >
        重试
      </button>
    </div>
  );
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
