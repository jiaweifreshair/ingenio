import { get, put, del, type APIResponse } from "./client";

/**
 * AppSpec数据结构
 */
export interface AppSpec {
  /** 唯一标识符 */
  id: string;
  /** 版本号 */
  version: string;
  /** 租户ID */
  tenantId: string;
  /** 用户ID */
  userId: string;
  /** 创建时间 */
  createdAt: string;
  /** 更新时间 */
  updatedAt: string;
  /** 用户需求描述 */
  userRequirement: string;
  /** 项目类型 */
  projectType?: string;
  /** AppSpec内容（JSON字符串或对象） */
  specContent?: string | Record<string, unknown>;
  /** 规划结果 */
  planResult: {
    modules: Array<{
      name: string;
      description: string;
      priority: string;
      complexity: number;
      dependencies: string[];
      dataModels: string[];
      pages: string[];
    }>;
    complexityScore: number;
    reasoning: string;
    suggestedTechStack: string[];
    estimatedHours: number;
    recommendations: string;
  };
  /** 验证结果 */
  validateResult: {
    isValid: boolean;
    qualityScore: number;
    issues: Array<{
      severity: string;
      type: string;
      message: string;
      location?: string;
    }>;
    suggestions: string[];
  };
  /** 是否验证通过 */
  isValid: boolean;
  /** 质量评分 */
  qualityScore: number;
  /** 状态 */
  status: string;
  /** 生成时间 */
  generatedAt: string;
  /** 耗时（毫秒） */
  durationMs: number;
}

/**
 * AppSpec列表项
 */
export interface AppSpecListItem {
  id: string;
  version: string;
  userRequirement: string;
  projectType?: string;
  qualityScore: number;
  status: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * AppSpec查询选项
 */
export interface AppSpecQueryOptions {
  /** 排序字段 */
  sortBy?: 'createdAt' | 'updatedAt' | 'qualityScore';
  /** 排序方向 */
  sortOrder?: 'asc' | 'desc';
  /** 状态过滤 */
  status?: string;
  /** 分页 */
  page?: number;
  /** 每页大小 */
  limit?: number;
}

/**
 * AppSpec列表响应
 */
export interface AppSpecListResponse {
  items: AppSpecListItem[];
  total: number;
  page: number;
  limit: number;
  hasNext: boolean;
  hasPrev: boolean;
}

/**
 * 获取AppSpec详情
 *
 * @param id - AppSpec ID
 * @returns AppSpec详情
 */
export async function getAppSpec(id: string): Promise<APIResponse<AppSpec>> {
  return get<AppSpec>(`/v1/appspecs/${id}`);
}

/**
 * 更新AppSpec
 *
 * @param id - AppSpec ID
 * @param data - 更新数据
 * @returns 更新后的AppSpec
 */
export async function updateAppSpec(
  id: string,
  data: Partial<AppSpec>
): Promise<APIResponse<AppSpec>> {
  return put<AppSpec>(`/v1/appspecs/${id}`, data);
}

/**
 * 删除AppSpec
 *
 * @param id - AppSpec ID
 * @returns 删除结果
 */
export async function deleteAppSpec(id: string): Promise<APIResponse<{ deleted: boolean }>> {
  return del<{ deleted: boolean }>(`/v1/appspecs/${id}`);
}

/**
 * 获取AppSpec列表
 *
 * @param options - 查询选项
 * @returns AppSpec列表
 */
export async function getAppSpecList(
  options?: AppSpecQueryOptions
): Promise<APIResponse<AppSpecListResponse>> {
  const params = new URLSearchParams();

  if (options?.sortBy) params.append('sortBy', options.sortBy);
  if (options?.sortOrder) params.append('sortOrder', options.sortOrder);
  if (options?.status) params.append('status', options.status);
  if (options?.page) params.append('page', options.page.toString());
  if (options?.limit) params.append('limit', options.limit.toString());

  const query = params.toString();
  const endpoint = `/v1/appspecs${query ? `?${query}` : ''}`;

  return get<AppSpecListResponse>(endpoint);
}