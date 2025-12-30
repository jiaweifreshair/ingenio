/**
 * 模板API客户端
 *
 * 架构说明：
 * - 生产环境：调用真实后端API
 * - 开发环境：可选择使用Mock数据（通过环境变量控制）
 *
 * 截断防护：Mock数据已提取到 templates.mock.ts，减少主文件体积
 *
 * @module lib/api/templates
 */
// [CHECKPOINT:FILE_START:templates.ts]

import {
  Template,
  TemplateCategory,
  TemplateDifficulty,
  TemplateQueryParams,
  TemplatePageResponse,
  CategoryMeta,
} from "@/types/template";
import { RequirementIntent } from "@/types/intent";
import { get, post } from "@/lib/api/client";

// ============================================================================
// 环境配置
// ============================================================================

/**
 * 是否使用Mock数据
 * 仅在开发环境且显式启用时使用Mock
 */
const USE_MOCK = process.env.NODE_ENV === 'development' &&
  process.env.NEXT_PUBLIC_USE_MOCK_TEMPLATES === 'true';

/**
 * 动态导入Mock数据（仅在需要时加载，减少bundle体积）
 */
async function getMockTemplates(): Promise<Template[]> {
  if (!USE_MOCK) {
    throw new Error('[Templates API] Mock数据仅在开发环境可用');
  }
  const { MOCK_TEMPLATES } = await import('./templates.mock');
  return MOCK_TEMPLATES;
}

// ============================================================================
// 分类API
// ============================================================================

/**
 * 分类元数据定义
 */
const CATEGORY_META: Omit<CategoryMeta, 'count'>[] = [
  { id: TemplateCategory.ALL, name: "全部模板", icon: "📦" },
  { id: TemplateCategory.ECOMMERCE, name: "电商类", icon: "🛒" },
  { id: TemplateCategory.SOCIAL, name: "社交类", icon: "💬" },
  { id: TemplateCategory.TOOLS, name: "工具类", icon: "🔧" },
  { id: TemplateCategory.CONTENT, name: "内容类", icon: "📝" },
  { id: TemplateCategory.EDUCATION, name: "教育类", icon: "🎓" },
  { id: TemplateCategory.OTHER, name: "其他", icon: "📱" },
];

/**
 * 获取所有分类的元数据
 *
 * @returns 分类元数据列表（含模板数量）
 * @throws Error 当API请求失败时
 */
export async function getCategories(): Promise<CategoryMeta[]> {
  console.log('[Templates API] 获取分类列表');

  // Mock模式
  if (USE_MOCK) {
    const templates = await getMockTemplates();
    return CATEGORY_META.map(cat => ({
      ...cat,
      count: cat.id === TemplateCategory.ALL
        ? templates.length
        : templates.filter(t => t.category === cat.id).length,
    }));
  }

  // 真实API调用
  try {
    const response = await get<CategoryMeta[]>('/v1/templates/categories');

    if (!response.success || !response.data) {
      throw new Error(response.error || '获取分类列表失败');
    }

    return response.data;
  } catch (error) {
    console.error('[Templates API] 获取分类列表失败:', error);
    throw error;
  }
}

// ============================================================================
// 模板查询API
// ============================================================================

/**
 * 查询模板列表
 *
 * 支持筛选条件：
 * - category: 分类筛选
 * - difficulty: 难度筛选
 * - platform: 平台筛选
 * - search: 关键词搜索
 * - sortBy: 排序方式（newest/popular/rating）
 * - page/pageSize: 分页参数
 *
 * @param params - 查询参数
 * @returns 分页的模板列表
 * @throws Error 当API请求失败时
 */
export async function queryTemplates(
  params: TemplateQueryParams = {}
): Promise<TemplatePageResponse> {
  console.log('[Templates API] 查询模板列表, params:', params);

  // Mock模式
  if (USE_MOCK) {
    return queryTemplatesMock(params);
  }

  // 真实API调用
  try {
    // 构建查询字符串
    const queryParams = new URLSearchParams();
    if (params.category) queryParams.set('category', params.category);
    if (params.difficulty) queryParams.set('difficulty', params.difficulty);
    if (params.platform) queryParams.set('platform', params.platform);
    if (params.search) queryParams.set('search', params.search);
    if (params.sortBy) queryParams.set('sortBy', params.sortBy);
    if (params.page) queryParams.set('page', String(params.page));
    if (params.pageSize) queryParams.set('pageSize', String(params.pageSize));

    const queryString = queryParams.toString();
    const url = queryString ? `/v1/templates?${queryString}` : '/v1/templates';

    const response = await get<TemplatePageResponse | Template[]>(url);

    if (!response.success || !response.data) {
      throw new Error(response.error || '查询模板列表失败');
    }

    // 兼容后端返回数组的情况（后端暂时未实现完整分页结构）
    if (Array.isArray(response.data)) {
      const items = response.data as Template[];
      const page = params.page || 1;
      const pageSize = params.pageSize || 50;
      
      // 注意：后端目前返回的是 limit 限制后的列表，无法得知真实 total
      // 暂时假设 total = items.length (如果是第一页且不满pageSize) 或 items.length + 1 (暗示还有下一页)
      // 这里的逻辑主要是为了让前端不报错
      
      return {
        items,
        total: items.length, // 临时：因为后端没返回总数
        page,
        pageSize,
        totalPages: 1, // 临时：假设只有一页
      };
    }

    return response.data as TemplatePageResponse;
  } catch (error) {
    console.error('[Templates API] 查询模板列表失败:', error);
    throw error;
  }
}

/**
 * Mock模式的模板查询实现
 * @internal
 */
async function queryTemplatesMock(
  params: TemplateQueryParams
): Promise<TemplatePageResponse> {
  const templates = await getMockTemplates();
  let filtered = [...templates];

  // 分类筛选
  if (params.category && params.category !== TemplateCategory.ALL) {
    filtered = filtered.filter(t => t.category === params.category);
  }

  // 难度筛选
  if (params.difficulty) {
    filtered = filtered.filter(t => t.difficulty === params.difficulty);
  }

  // 平台筛选
  if (params.platform) {
    filtered = filtered.filter(t => t.platforms.includes(params.platform!));
  }

  // 搜索关键词
  if (params.search) {
    const keyword = params.search.toLowerCase();
    filtered = filtered.filter(t =>
      t.name.toLowerCase().includes(keyword) ||
      t.description.toLowerCase().includes(keyword) ||
      t.tags.some(tag => tag.toLowerCase().includes(keyword))
    );
  }

  // 排序
  filtered = sortTemplates(filtered, params.sortBy);

  // 分页
  const page = params.page || 1;
  const pageSize = params.pageSize || 12;
  const start = (page - 1) * pageSize;
  const items = filtered.slice(start, start + pageSize);

  return {
    items,
    total: filtered.length,
    page,
    pageSize,
    totalPages: Math.ceil(filtered.length / pageSize),
  };
}

/**
 * 模板排序工具函数
 * @internal
 */
function sortTemplates(
  templates: Template[],
  sortBy?: string
): Template[] {
  const sorted = [...templates];

  switch (sortBy) {
    case "newest":
      return sorted.sort((a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
    case "popular":
      return sorted.sort((a, b) => b.usageCount - a.usageCount);
    case "rating":
      return sorted.sort((a, b) => b.rating - a.rating);
    default:
      return sorted.sort((a, b) => b.usageCount - a.usageCount);
  }
}

// ============================================================================
// 模板详情API
// ============================================================================

/**
 * 获取模板详情
 *
 * @param id - 模板ID
 * @returns 模板详情，不存在时返回null
 * @throws Error 当API请求失败时
 */
export async function getTemplateById(id: string): Promise<Template | null> {
  console.log('[Templates API] 获取模板详情, id:', id);

  // Mock模式
  if (USE_MOCK) {
    const templates = await getMockTemplates();
    return templates.find(t => t.id === id) || null;
  }

  // 真实API调用
  try {
    const response = await get<Template>(`/v1/templates/${id}`);

    if (!response.success) {
      if (response.error?.includes('404') || response.error?.includes('not found')) {
        return null;
      }
      throw new Error(response.error || '获取模板详情失败');
    }

    return response.data || null;
  } catch (error) {
    console.error('[Templates API] 获取模板详情失败:', error);
    throw error;
  }
}

// ============================================================================
// 模板操作API
// ============================================================================

/**
 * 使用模板（增加使用次数）
 *
 * @param id - 模板ID
 * @throws Error 当API请求失败时
 */
export async function useTemplate(id: string): Promise<void> {
  console.log('[Templates API] 使用模板, id:', id);

  // Mock模式：仅记录日志
  if (USE_MOCK) {
    console.log('[Templates API] Mock模式，跳过使用次数更新');
    return;
  }

  // 真实API调用
  try {
    const response = await post<void>(`/v1/templates/${id}/use`, {});

    if (!response.success) {
      throw new Error(response.error || '更新使用次数失败');
    }
  } catch (error) {
    console.error('[Templates API] 更新使用次数失败:', error);
    throw error;
  }
}

/**
 * 收藏模板
 *
 * @param id - 模板ID
 * @throws Error 当API请求失败时
 */
export async function favoriteTemplate(id: string): Promise<void> {
  console.log('[Templates API] 收藏模板, id:', id);

  // Mock模式：仅记录日志
  if (USE_MOCK) {
    console.log('[Templates API] Mock模式，跳过收藏操作');
    return;
  }

  // 真实API调用
  try {
    const response = await post<void>(`/v1/templates/${id}/favorite`, {});

    if (!response.success) {
      throw new Error(response.error || '收藏模板失败');
    }
  } catch (error) {
    console.error('[Templates API] 收藏模板失败:', error);
    throw error;
  }
}

// ============================================================================
// V2.0 意图匹配API
// ============================================================================

/**
 * V2.0 获取与意图匹配的行业模板
 *
 * 功能：
 * - 根据用户意图类型（克隆/设计/混合）获取匹配的行业模板
 * - 返回按匹配度排序的模板列表
 * - 用于V2.0 wizard流程的模板选择步骤
 *
 * @param intent - 用户意图类型
 * @returns 匹配的模板列表（按匹配度排序，最多6个）
 * @throws Error 当请求失败或后端返回错误时
 *
 * @example
 * ```typescript
 * const templates = await getMatchedTemplates(RequirementIntent.CLONE_EXISTING_WEBSITE);
 * console.log(`找到 ${templates.length} 个匹配模板`);
 * ```
 */
export async function getMatchedTemplates(
  intent: RequirementIntent
): Promise<Template[]> {
  console.log('[Templates API] 获取匹配模板, intent:', intent);

  // Mock模式
  if (USE_MOCK) {
    return getMatchedTemplatesMock(intent);
  }

  // 真实API调用
  try {
    const response = await get<Template[]>(
      `/v1/templates/matched?intent=${intent}`
    );

    if (!response.success || !response.data) {
      throw new Error(response.error || '获取匹配模板失败');
    }

    console.log('[Templates API] 匹配模板获取成功:', response.data.length, '个');
    return response.data;
  } catch (error) {
    console.error('[Templates API] 获取匹配模板失败:', error);
    throw error;
  }
}

/**
 * Mock模式的意图匹配实现
 * @internal
 */
async function getMatchedTemplatesMock(
  intent: RequirementIntent
): Promise<Template[]> {
  const templates = await getMockTemplates();
  let matched: Template[] = [];

  switch (intent) {
    case RequirementIntent.CLONE_EXISTING_WEBSITE:
      // 克隆意图：推荐成熟的、使用率高的模板
      matched = templates.filter(t =>
        t.usageCount > 800 ||
        t.rating >= 4.5 ||
        t.category === TemplateCategory.ECOMMERCE ||
        t.category === TemplateCategory.SOCIAL
      ).sort((a, b) => b.usageCount - a.usageCount);
      break;

    case RequirementIntent.DESIGN_FROM_SCRATCH:
      // 从零设计意图：推荐简单易定制的模板
      matched = templates.filter(t =>
        t.difficulty === TemplateDifficulty.SIMPLE ||
        t.difficulty === TemplateDifficulty.MEDIUM
      ).sort((a, b) => b.rating - a.rating);
      break;

    case RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE:
      // 混合意图：推荐功能完整且可定制性强的模板
      matched = templates.filter(t =>
        t.features.length >= 5 ||
        t.platforms.length >= 3
      ).sort((a, b) => {
        const scoreA = a.usageCount * 0.6 + a.rating * 40;
        const scoreB = b.usageCount * 0.6 + b.rating * 40;
        return scoreB - scoreA;
      });
      break;

    default:
      matched = [...templates].sort((a, b) => b.usageCount - a.usageCount);
  }

  // 限制返回数量（最多6个）
  const result = matched.slice(0, 6);
  console.log(`[Templates API] Mock匹配完成，找到 ${result.length} 个模板`);

  return result;
}

// [CHECKPOINT:FILE_END:templates.ts:SUCCESS]
