/**
 * SuperDesign API客户端
 * 提供AI设计方案生成和管理功能
 *
 * @version 2.0.0 - 新增7风格HTML预览生成功能
 */

import { post, get } from './client';
import type {
  DesignRequest,
  DesignScheme,
} from '@/types/design';
import type {
  Generate7StylesRequest,
  Generate7StylesResponse,
  DesignStyle,
} from '@/types/design-style';

/**
 * 生成3个UI设计方案
 *
 * @param request - 设计请求参数
 * @returns 3个不同风格的设计方案
 * @throws APIError 当API调用失败时
 *
 * @example
 * ```typescript
 * const designs = await generateDesigns({
 *   userPrompt: "构建图书管理系统",
 *   entities: [{ name: "book", displayName: "图书", primaryFields: ["title"], viewType: "list" }],
 *   targetPlatform: "android",
 *   uiFramework: "compose_multiplatform",
 *   colorScheme: "light",
 *   includeAssets: true
 * });
 * ```
 */
export async function generateDesigns(
  request: DesignRequest
): Promise<DesignScheme[]> {
  const response = await post<DesignScheme[]>(
    '/v1/superdesign/generate',
    request
  );

  if (!response.success || !response.data) {
    throw new Error(response.error || '生成设计方案失败');
  }

  return response.data;
}

/**
 * 获取设计请求示例
 *
 * @returns 示例设计请求，可用于测试或参考
 *
 * @example
 * ```typescript
 * const example = await getDesignExample();
 * console.log(example.userPrompt); // "构建一个图书管理系统..."
 * ```
 */
export async function getDesignExample(): Promise<DesignRequest> {
  const response = await get<DesignRequest>('/v1/superdesign/example');

  if (!response.success || !response.data) {
    throw new Error(response.error || '获取示例失败');
  }

  return response.data;
}

/**
 * 根据方案ID获取设计详情
 *
 * 注意：当前后端未实现此接口，这里提供占位符
 *
 * @param variantId - 方案ID (A/B/C)
 * @returns 设计方案详情
 */
export async function getDesignDetail(
  variantId: string
): Promise<DesignScheme> {
  // TODO: 等待后端实现 GET /v1/superdesign/{variantId} 接口
  throw new Error(`获取设计详情接口未实现: ${variantId}`);
}

/**
 * 选择最终使用的设计方案
 *
 * 注意：当前后端未实现此接口，这里提供占位符
 *
 * @param variantId - 选择的方案ID
 * @param taskId - 任务ID
 * @returns 选择结果
 */
export async function selectDesign(
  variantId: string,
  taskId: string
): Promise<{ success: boolean }> {
  // TODO: 等待后端实现 POST /v1/superdesign/select 接口
  console.log(`选择设计方案: ${variantId}, 任务ID: ${taskId}`);
  return { success: true };
}

// ============================================================================
// V2.0新增功能：7种风格HTML预览生成
// ============================================================================

/**
 * 生成7种风格的HTML预览 (V2.0新增)
 *
 * 调用后端接口生成7种不同设计风格的HTML预览页面，使用模板快速生成（<15秒）
 *
 * @param request - 7风格生成请求参数
 * @returns 包含7种风格预览的完整响应
 *
 * @example
 * ```typescript
 * const response = await generate7StylePreviews({
 *   userRequirement: "创建一个民宿预订平台，支持房源浏览、在线预订、评价管理",
 *   appType: "生活服务",
 *   targetPlatform: "web",
 *   useAICustomization: false, // 使用模板生成，速度快（2-3秒/风格）
 * });
 *
 * if (response.success && response.data) {
 *   console.log(`总耗时: ${response.data.totalGenerationTime}ms`);
 *   response.data.styles.forEach(style => {
 *     console.log(`风格 ${style.style}: ${style.generationTime}ms`);
 *   });
 * }
 * ```
 */
export async function generate7StylePreviews(
  request: Generate7StylesRequest
): Promise<{ success: boolean; data?: Generate7StylesResponse; error?: string }> {
  try {
    console.log('[SuperDesignAPI] 🎨 开始生成7种风格预览');
    console.log('[SuperDesignAPI] 请求参数:', JSON.stringify(request, null, 2));

    const startTime = Date.now();

    // 调用后端接口 (Phase 7 StyleController)
    const response = await post<Generate7StylesResponse>(
      '/v1/styles/generate-previews',
      request
    );

    const duration = Date.now() - startTime;

    if (!response.success || !response.data) {
      console.error('[SuperDesignAPI] ❌ 生成失败:', response.error);
      return {
        success: false,
        error: response.error || '生成7种风格预览失败',
      };
    }

    console.log('[SuperDesignAPI] ✅ 生成完成');
    console.log('[SuperDesignAPI] 客户端耗时:', duration, 'ms');
    console.log('[SuperDesignAPI] 服务端耗时:', response.data.totalGenerationTime, 'ms');
    console.log('[SuperDesignAPI] 成功生成风格数:', response.data.styles?.length || 0);

    if (response.data.warnings && response.data.warnings.length > 0) {
      console.warn('[SuperDesignAPI] ⚠️ 警告信息:', response.data.warnings);
    }

    return {
      success: true,
      data: response.data,
    };
  } catch (error) {
    console.error('[SuperDesignAPI] ❌ 生成7种风格异常:', error);

    return {
      success: false,
      error: error instanceof Error ? error.message : '未知错误',
    };
  }
}

/**
 * 获取风格显示信息
 * 用于在UI中展示风格的元数据（名称、描述、图标、适用场景等）
 *
 * @param styleCode - 风格代码（例如："modern_minimal"）
 * @returns 风格显示信息
 *
 * @example
 * ```typescript
 * const info = getStyleDisplayInfo(DesignStyle.MODERN_MINIMAL);
 * console.log(info.displayName); // "现代极简"
 * console.log(info.icon); // "✨"
 * console.log(info.suitableFor); // ["企业官网", "作品集", ...]
 * ```
 */
export function getStyleDisplayInfo(styleCode: DesignStyle | string): {
  identifier: string;
  displayName: string;
  displayNameEn: string;
  description: string;
  features: string[];
  icon: string;
  colorClass: string;
  suitableFor: string[];
  examples: string[];
} {
  const styleMap: Record<string, ReturnType<typeof getStyleDisplayInfo>> = {
    modern_minimal: {
      identifier: 'A',
      displayName: '现代极简',
      displayNameEn: 'Modern Minimal',
      description: '大留白、卡片式布局、简洁图标，强调内容本身',
      features: ['大留白', '卡片式', '简洁图标', '扁平设计', '高对比'],
      icon: '✨',
      colorClass: 'from-slate-500 to-gray-500',
      suitableFor: ['企业官网', '作品集', '博客', 'SaaS产品'],
      examples: ['Apple官网', 'Notion', 'Linear'],
    },
    vibrant_fashion: {
      identifier: 'B',
      displayName: '活力时尚',
      displayNameEn: 'Vibrant Fashion',
      description: '渐变色彩、圆角设计、网格布局，充满活力和现代感',
      features: ['渐变色彩', '圆角设计', '网格布局', '动态效果', '鲜艳配色'],
      icon: '🌈',
      colorClass: 'from-pink-500 to-purple-500',
      suitableFor: ['时尚电商', '创意工作室', '音乐应用', '社交平台'],
      examples: ['Instagram', 'Spotify', 'Dribbble'],
    },
    classic_professional: {
      identifier: 'C',
      displayName: '经典专业',
      displayNameEn: 'Classic Professional',
      description: '传统布局、信息密集、列表式展示，强调专业性和信息量',
      features: ['传统布局', '信息密集', '列表式', '数据表格', '严谨配色'],
      icon: '📊',
      colorClass: 'from-blue-600 to-indigo-600',
      suitableFor: ['企业管理', '数据分析', '新闻门户', '金融系统'],
      examples: ['Bloomberg', 'Reuters', 'SAP'],
    },
    future_tech: {
      identifier: 'D',
      displayName: '未来科技',
      displayNameEn: 'Future Tech',
      description: '深色主题、霓虹色彩、3D元素，展现科技感和未来感',
      features: ['深色主题', '霓虹色彩', '3D元素', '发光效果', '科技氛围'],
      icon: '🚀',
      colorClass: 'from-cyan-500 to-blue-500',
      suitableFor: ['科技产品', '游戏平台', 'AI工具', '区块链'],
      examples: ['Cyberpunk 2077', 'OpenAI', 'Vercel'],
    },
    immersive_3d: {
      identifier: 'E',
      displayName: '沉浸式3D',
      displayNameEn: 'Immersive 3D',
      description: '毛玻璃效果、深度阴影、视差滚动，打造沉浸式体验',
      features: ['毛玻璃', '深度阴影', '视差滚动', '3D变换', '层次感'],
      icon: '🎨',
      colorClass: 'from-purple-500 to-pink-500',
      suitableFor: ['品牌展示', '产品发布', '艺术作品', '高端电商'],
      examples: ['Apple产品页', 'Awwwards获奖作品'],
    },
    gamified: {
      identifier: 'F',
      displayName: '游戏化设计',
      displayNameEn: 'Gamified',
      description: '卡通风格、奖励反馈、成就系统，趣味性和互动性强',
      features: ['卡通风格', '奖励反馈', '成就系统', '动画丰富', '趣味互动'],
      icon: '🎮',
      colorClass: 'from-orange-500 to-red-500',
      suitableFor: ['教育应用', '健身应用', '儿童产品', '社区平台'],
      examples: ['Duolingo', 'Habitica', 'Khan Academy'],
    },
    natural_flow: {
      identifier: 'G',
      displayName: '自然流动',
      displayNameEn: 'Natural Flow',
      description: '有机曲线、自然配色、流体动画，展现自然和谐之美',
      features: ['有机曲线', '自然配色', '流体动画', '柔和过渡', '和谐美感'],
      icon: '🌿',
      colorClass: 'from-green-500 to-teal-500',
      suitableFor: ['健康养生', '环保公益', '生活方式', '瑜伽冥想'],
      examples: ['Calm', 'Headspace', 'Goop'],
    },
  };

  return (
    styleMap[styleCode] || {
      identifier: '?',
      displayName: '未知风格',
      displayNameEn: 'Unknown Style',
      description: '未知的设计风格',
      features: [],
      icon: '❓',
      colorClass: 'from-gray-500 to-gray-600',
      suitableFor: [],
      examples: [],
    }
  );
}
