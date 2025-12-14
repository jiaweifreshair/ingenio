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
 * 判断是否为后端通用错误文案
 * 避免覆盖更有意义的 HTTP 状态描述（如 Bad Gateway）
 */
function isGenericSystemError(message: string): boolean {
  const normalized = message.trim();
  return (
    normalized === '系统错误' ||
    normalized === 'System error' ||
    normalized === 'Internal Server Error'
  );
}

/**
 * 从非 2xx 响应中提取更友好的错误信息
 * - 优先读取 JSON 中的 message/error 字段
 * - 若为“系统错误”等泛化文案，则保留 HTTP statusText
 * - 若 statusText 为空，则按常见状态码补全文案
 */
async function getReadableErrorMessage(response: Response): Promise<string> {
  const STATUS_TEXT_MAP: Record<number, string> = {
    502: 'Bad Gateway',
    500: 'Internal Server Error',
    503: 'Service Unavailable',
    504: 'Gateway Timeout',
  };

  const httpText =
    response.statusText ||
    STATUS_TEXT_MAP[response.status] ||
    `HTTP ${response.status}`;

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) {
    return httpText;
  }

  try {
    const errorBody = (await response.json()) as unknown;
    if (typeof errorBody === 'object' && errorBody !== null) {
      const bodyRecord = errorBody as Record<string, unknown>;
      const messageCandidate =
        (typeof bodyRecord.message === 'string' && bodyRecord.message.trim()) ||
        (typeof bodyRecord.error === 'string' && bodyRecord.error.trim()) ||
        '';

      if (messageCandidate) {
        return isGenericSystemError(messageCandidate) ? httpText : messageCandidate;
      }
    }
  } catch {
    // 解析失败时回退到 HTTP 文案
  }

  return httpText;
}

/**
 * 获取项目统计数据
 * 
 * 注意：此接口通过 Next.js BFF 代理访问后端，避免跨域问题
 */
export async function getProjectStats(): Promise<ProjectStats> {
  try {
    const response = await fetch(
      `${API_BASE_URL}/v1/projects/stats`,
      buildFetchOptions({ method: 'GET' })
    );

    if (!response.ok) {
      const errorMessage = await getReadableErrorMessage(response);
      throw new Error(`获取统计数据失败: ${errorMessage}`);
    }

    const raw = await response.json();
    const result = normalizeApiResponse<ProjectStats>(raw);

    if (!result.success) {
      // 提供更详细的错误信息用于调试
      const errorDetail = result.message || result.error || '获取统计数据失败';
      console.error('getProjectStats API error:', { code: result.code, message: errorDetail, raw });
      throw new Error(errorDetail);
    }

    // 确保返回有效数据，提供默认值防止 undefined
    const data = result.data;
    if (!data) {
      // 返回默认统计数据，避免页面崩溃
      return {
        totalProjects: 0,
        monthlyNewProjects: 0,
        generatingTasks: 0,
        publishedProjects: 0,
        draftProjects: 0,
        archivedProjects: 0,
      };
    }

    return data;
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
    const errorMessage = await getReadableErrorMessage(response);
    throw new Error(`获取项目列表失败: ${errorMessage}`);
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
