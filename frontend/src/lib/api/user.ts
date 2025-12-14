/**
 * 用户API客户端
 * 与后端UserController交互
 *
 * 功能：
 * - 个人信息管理（获取、更新、头像上传、修改密码）
 * - API密钥管理（列表、生成、删除）
 * - 安全设置（登录设备、操作日志、两步验证）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import { getToken } from '@/lib/auth/token';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * API基础URL（包含/api前缀）
 * 与client.ts保持一致
 */
const API_BASE_URL = getApiBaseUrl();

/**
 * 用户信息类型
 */
export interface UserProfile {
  id: string;
  username: string;
  email: string;
  phone?: string;
  avatar?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * 更新用户信息请求
 */
export interface UpdateProfileRequest {
  username?: string;
  email?: string;
  phone?: string;
}

/**
 * 修改密码请求
 */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/**
 * API密钥类型
 * 与后端 ApiKeyResponse 对应
 */
export interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;        // 密钥前缀（显示用，如：ing_xxxxx）
  fullKey?: string;         // 完整密钥（仅在创建时返回一次）
  description?: string;
  scopes?: string[];
  isActive?: boolean;
  lastUsedAt?: string;      // 最后使用时间
  lastUsedIp?: string;
  usageCount?: number;
  rateLimit?: number;
  expiresAt?: string;
  createdAt: string;
}

/**
 * 生成API密钥请求
 */
export interface CreateApiKeyRequest {
  name: string;
  expiresInDays?: number;
}

/**
 * 登录设备类型
 */
export interface LoginDevice {
  id: string;
  deviceName: string;
  deviceType: 'Desktop' | 'Mobile' | 'Tablet';
  browser: string;
  os: string;
  location: string;
  ipAddress: string;
  lastActive: string;
  isCurrent: boolean;
}

/**
 * 操作日志类型
 */
export interface ActivityLog {
  id: string;
  action: string;
  description: string;
  timestamp: string;
  ipAddress: string;
  status: 'Success' | 'Failed';
  metadata?: Record<string, unknown>;
}

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

// ==================== 个人信息管理 ====================

/**
 * 获取用户信息
 *
 * 注意：此API依赖后端UserController实现
 * 当前后端未实现该接口，返回null
 * TODO: 待后端实现 /v1/user/profile 端点后移除降级逻辑
 */
export async function getUserProfile(): Promise<UserProfile | null> {
  try {
    const response = await fetch(
      `${API_BASE_URL}/v1/user/profile`,
      buildFetchOptions({ method: 'GET' })
    );

    // 当API不存在时（404），优雅降级返回null
    if (response.status === 404) {
      console.warn('[用户API] 后端UserController未实现，返回null');
      return null;
    }

    if (!response.ok) {
      throw new Error(`获取用户信息失败: ${response.statusText}`);
    }

    const raw = await response.json();
    const result = normalizeApiResponse<UserProfile>(raw);

    if (!result.success) {
      throw new Error(result.message || result.error || '获取用户信息失败');
    }

    return result.data as UserProfile;
  } catch (error) {
    // 捕获网络错误等异常，返回null
    if (error instanceof TypeError && error.message.includes('fetch')) {
      console.warn('[用户API] 网络请求失败，返回null');
      return null;
    }
    throw error;
  }
}

/**
 * 更新用户信息
 */
export async function updateUserProfile(data: UpdateProfileRequest): Promise<UserProfile> {
  const response = await fetch(
    `${API_BASE_URL}/v1/user/profile`,
    buildFetchOptions({
      method: 'PUT',
      body: JSON.stringify(data),
    })
  );

  if (!response.ok) {
    throw new Error(`更新用户信息失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<UserProfile>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '更新用户信息失败');
  }

  return result.data as UserProfile;
}

/**
 * 上传用户头像
 *
 * @param file - 头像文件（支持JPG、PNG，不超过2MB）
 */
export async function uploadAvatar(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('avatar', file);

  const token = getToken();
  const headers: Record<string, string> = {};

  if (token) {
    // SaToken不需要Bearer前缀，直接使用token值
    headers['Authorization'] = token;
  }

  const response = await fetch(`${API_BASE_URL}/v1/user/avatar`, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`上传头像失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<{ avatarUrl: string }>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '上传头像失败');
  }

  return (result.data as { avatarUrl: string }).avatarUrl;
}

/**
 * 修改密码
 */
export async function changePassword(data: ChangePasswordRequest): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/v1/user/password`,
    buildFetchOptions({
      method: 'PUT',
      body: JSON.stringify(data),
    })
  );

  if (!response.ok) {
    throw new Error(`修改密码失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '修改密码失败');
  }
}

// ==================== API密钥管理 ====================

/**
 * 获取API密钥列表
 */
export async function listApiKeys(): Promise<ApiKey[]> {
  const response = await fetch(
    `${API_BASE_URL}/v1/user/api-keys`,
    buildFetchOptions({ method: 'GET' })
  );

  if (!response.ok) {
    throw new Error(`获取API密钥列表失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<ApiKey[]>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '获取API密钥列表失败');
  }

  return result.data as ApiKey[];
}

/**
 * 生成新的API密钥
 */
export async function createApiKey(data: CreateApiKeyRequest): Promise<ApiKey> {
  const response = await fetch(
    `${API_BASE_URL}/v1/user/api-keys`,
    buildFetchOptions({
      method: 'POST',
      body: JSON.stringify(data),
    })
  );

  if (!response.ok) {
    throw new Error(`生成API密钥失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<ApiKey>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '生成API密钥失败');
  }

  return result.data as ApiKey;
}

/**
 * 删除API密钥
 */
export async function deleteApiKey(keyId: string): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/v1/user/api-keys/${keyId}`,
    buildFetchOptions({ method: 'DELETE' })
  );

  if (!response.ok) {
    throw new Error(`删除API密钥失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '删除API密钥失败');
  }
}

// ==================== 安全设置 ====================

/**
 * 获取登录设备列表
 */
export async function listLoginDevices(): Promise<LoginDevice[]> {
  const response = await fetch(
    `${API_BASE_URL}/v1/user/devices`,
    buildFetchOptions({ method: 'GET' })
  );

  if (!response.ok) {
    throw new Error(`获取登录设备失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<LoginDevice[]>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '获取登录设备失败');
  }

  return result.data as LoginDevice[];
}

/**
 * 移除登录设备
 */
export async function removeLoginDevice(deviceId: string): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/v1/user/devices/${deviceId}`,
    buildFetchOptions({ method: 'DELETE' })
  );

  if (!response.ok) {
    throw new Error(`移除登录设备失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '移除登录设备失败');
  }
}

/**
 * 获取操作日志
 */
export async function listActivityLogs(params?: {
  current?: number;
  size?: number;
}): Promise<{
  records: ActivityLog[];
  total: number;
  current: number;
  size: number;
  pages: number;
}> {
  const queryParams = new URLSearchParams();
  if (params?.current) queryParams.append('current', params.current.toString());
  if (params?.size) queryParams.append('size', params.size.toString());

  const response = await fetch(
    `${API_BASE_URL}/v1/user/logs?${queryParams.toString()}`,
    buildFetchOptions({ method: 'GET' })
  );

  if (!response.ok) {
    throw new Error(`获取操作日志失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<{
    records: ActivityLog[];
    total: number;
    current: number;
    size: number;
    pages: number;
  }>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '获取操作日志失败');
  }

  return result.data as {
    records: ActivityLog[];
    total: number;
    current: number;
    size: number;
    pages: number;
  };
}

/**
 * 启用/禁用两步验证
 */
export async function toggleTwoFactor(enabled: boolean): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/v1/user/two-factor`,
    buildFetchOptions({
      method: 'PUT',
      body: JSON.stringify({ enabled }),
    })
  );

  if (!response.ok) {
    throw new Error(`设置两步验证失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<void>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '设置两步验证失败');
  }
}

/**
 * 获取两步验证状态
 */
export async function getTwoFactorStatus(): Promise<boolean> {
  const response = await fetch(
    `${API_BASE_URL}/v1/user/two-factor`,
    buildFetchOptions({ method: 'GET' })
  );

  if (!response.ok) {
    throw new Error(`获取两步验证状态失败: ${response.statusText}`);
  }

  const raw = await response.json();
  const result = normalizeApiResponse<{ enabled: boolean }>(raw);

  if (!result.success) {
    throw new Error(result.message || result.error || '获取两步验证状态失败');
  }

  return (result.data as { enabled: boolean }).enabled;
}
