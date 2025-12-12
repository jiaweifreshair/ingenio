/**
 * 项目API客户端
 * 与后端ProjectController交互
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import { getToken } from '@/lib/auth/token';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { normalizeApiResponse } from '@/lib/api/response';
import type {
  Project,
  ProjectStats,
  ProjectFilters,
  PageResult,
} from '@/types/project';

/**
 * API基础地址（包含/api前缀）
 * 与client.ts保持一致，统一使用包含context-path的完整地址
 */
const API_BASE_URL = getApiBaseUrl();

/**
 * 构建带Authorization header的fetch配置
 */
function buildFetchOptions(options: RequestInit = {}): RequestInit {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (token) {
    // SaToken不需要Bearer前缀，直接使用token值
    headers['Authorization'] = token;
  }

  // 合并自定义headers
  if (options.headers) {
    const customHeaders = new Headers(options.headers);
    customHeaders.forEach((value, key) => {
      headers[key] = value;
    });
  }

  return {
    ...options,
    headers,
    credentials: 'include',
  };
}

/**
 * 获取项目统计数据
 */
export async function getProjectStats(): Promise<ProjectStats> {
  try {
    const response = await fetch(
      `${API_BASE_URL}/v1/projects/stats`,
      buildFetchOptions({ method: 'GET' })
    );

    if (!response.ok) {
      // 尝试解析错误响应体
      let errorMessage = response.statusText;
      try {
        const errorBody = await response.json();
        errorMessage = errorBody.message || errorBody.error || response.statusText;
      } catch {
        // 解析失败，忽略
      }
      
      throw new Error(`获取统计数据失败: ${errorMessage || `HTTP ${response.status}`}`);
    }

    const raw = await response.json();
    const result = normalizeApiResponse<ProjectStats>(raw);

    if (!result.success) {
      throw new Error(result.message || result.error || '获取统计数据失败');
    }

    return result.data as ProjectStats;
  } catch (error) {
    console.error('getProjectStats error:', error);
    throw error;
  }
}

/**
 * 分页查询用户项目列表
 */
export async function listProjects(
  filters: ProjectFilters = {}
): Promise<PageResult<Project>> {
  const { status, keyword, current = 1, size = 12 } = filters;

  // 构建查询参数
  const params = new URLSearchParams();
  params.append('current', current.toString());
  params.append('size', size.toString());

  if (status) {
    params.append('status', status);
  }

  if (keyword && keyword.trim()) {
    params.append('keyword', keyword.trim());
  }

  const response = await fetch(
    `${API_BASE_URL}/v1/projects?${params.toString()}`,
    buildFetchOptions({ method: 'GET' })
  );

  if (!response.ok) {
    throw new Error(`获取项目列表失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<PageResult<Project>>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '获取项目列表失败');
  }

  return result.data as PageResult<Project>;
}

/**
 * 根据ID获取项目详情
 */
export async function getProjectById(id: string): Promise<Project> {
  const response = await fetch(
    `${API_BASE_URL}/v1/projects/${id}`,
    buildFetchOptions({ method: 'GET' })
  );

  if (!response.ok) {
    throw new Error(`获取项目详情失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<Project>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '获取项目详情失败');
  }

  return result.data as Project;
}

/**
 * 删除项目
 */
export async function deleteProject(id: string): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/v1/projects/${id}`,
    buildFetchOptions({ method: 'DELETE' })
  );

  if (!response.ok) {
    throw new Error(`删除项目失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '删除项目失败');
  }
}

/**
 * 复制项目（Fork）
 */
export async function forkProject(id: string): Promise<Project> {
  const response = await fetch(
    `${API_BASE_URL}/v1/projects/${id}/fork`,
    buildFetchOptions({ method: 'POST' })
  );

  if (!response.ok) {
    throw new Error(`复制项目失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<Project>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '复制项目失败');
  }

  return result.data as Project;
}

/**
 * 发布项目
 */
export async function publishProject(id: string): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/v1/projects/${id}/publish`,
    buildFetchOptions({ method: 'POST' })
  );

  if (!response.ok) {
    throw new Error(`发布项目失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '发布项目失败');
  }
}

/**
 * 归档项目
 */
export async function archiveProject(id: string): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/v1/projects/${id}/archive`,
    buildFetchOptions({ method: 'POST' })
  );

  if (!response.ok) {
    throw new Error(`归档项目失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '归档项目失败');
  }
}
