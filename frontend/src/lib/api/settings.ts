/**
 * 项目设置API客户端
 * 与后端ProjectController交互
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import type {
  ProjectSettings,
  ProjectMember,
  InviteMemberRequest,
  TransferProjectRequest,
  UpdateProjectSettingsRequest,
} from '@/types/settings';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { normalizeApiResponse } from '@/lib/api/response';
import { generateTraceId } from '@/lib/api/trace-id';

const API_BASE_URL = getApiBaseUrl();

/**
 * 获取项目设置
 */
export async function getProjectSettings(projectId: string): Promise<ProjectSettings> {
  const response = await fetch(`${API_BASE_URL}/v1/projects/${projectId}/settings`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'x-trace-id': generateTraceId(),
    },
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`获取项目设置失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<ProjectSettings>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '获取项目设置失败');
  }

  return result.data as ProjectSettings;
}

/**
 * 更新项目设置
 */
export async function updateProjectSettings(
  projectId: string,
  settings: UpdateProjectSettingsRequest
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/projects/${projectId}/settings`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'x-trace-id': generateTraceId(),
    },
    credentials: 'include',
    body: JSON.stringify(settings),
  });

  if (!response.ok) {
    throw new Error(`更新项目设置失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '更新项目设置失败');
  }
}

/**
 * 转移项目
 */
export async function transferProject(
  projectId: string,
  request: TransferProjectRequest
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/projects/${projectId}/transfer`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-trace-id': generateTraceId(),
    },
    credentials: 'include',
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`转移项目失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '转移项目失败');
  }
}

/**
 * 获取项目成员列表
 */
export async function getProjectMembers(projectId: string): Promise<ProjectMember[]> {
  const response = await fetch(`${API_BASE_URL}/v1/projects/${projectId}/members`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'x-trace-id': generateTraceId(),
    },
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`获取成员列表失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<ProjectMember[]>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '获取成员列表失败');
  }

  return result.data as ProjectMember[];
}

/**
 * 邀请成员
 */
export async function inviteMember(
  projectId: string,
  request: InviteMemberRequest
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/projects/${projectId}/members`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-trace-id': generateTraceId(),
    },
    credentials: 'include',
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`邀请成员失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '邀请成员失败');
  }
}

/**
 * 移除成员
 */
export async function removeMember(projectId: string, memberId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/v1/projects/${projectId}/members/${memberId}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      'x-trace-id': generateTraceId(),
    },
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`移除成员失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '移除成员失败');
  }
}

/**
 * 更新成员角色
 */
export async function updateMemberRole(
  projectId: string,
  memberId: string,
  role: 'editor' | 'viewer'
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/v1/projects/${projectId}/members/${memberId}/role`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'x-trace-id': generateTraceId(),
      },
      credentials: 'include',
      body: JSON.stringify({ role }),
    }
  );

  if (!response.ok) {
    throw new Error(`更新成员角色失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '更新成员角色失败');
  }
}
