/**
 * Token自动刷新机制
 *
 * 功能：
 * - 检测Token即将过期
 * - 自动刷新Token
 * - 防止并发刷新
 *
 * @author Ingenio Team
 * @since 2026-01-18
 */

import { getToken, setToken } from './token';

// Token刷新状态
let isRefreshing = false;
let refreshPromise: Promise<string> | null = null;

// Token过期时间缓存（秒）
let tokenExpiresAt: number | null = null;

/**
 * 设置Token过期时间
 *
 * @param expiresIn - Token有效期（秒）
 */
export function setTokenExpiry(expiresIn: number): void {
  tokenExpiresAt = Date.now() / 1000 + expiresIn;
}

/**
 * 检查Token是否即将过期
 * 提前5分钟刷新Token
 *
 * @returns 是否需要刷新
 */
export function shouldRefreshToken(): boolean {
  if (!tokenExpiresAt) return false;

  const now = Date.now() / 1000;
  const timeUntilExpiry = tokenExpiresAt - now;

  // 提前5分钟刷新
  return timeUntilExpiry < 300;
}

/**
 * 刷新Token
 * 防止并发刷新
 *
 * @returns 新的Token
 */
export async function refreshTokenIfNeeded(): Promise<string | null> {
  const currentToken = getToken();

  if (!currentToken) {
    return null;
  }

  if (!shouldRefreshToken()) {
    return currentToken;
  }

  // 如果正在刷新，返回现有的Promise
  if (isRefreshing && refreshPromise) {
    return refreshPromise;
  }

  // 开始刷新
  isRefreshing = true;
  refreshPromise = performTokenRefresh();

  try {
    const newToken = await refreshPromise;
    return newToken;
  } finally {
    isRefreshing = false;
    refreshPromise = null;
  }
}

/**
 * 执行Token刷新
 *
 * @returns 新的Token
 */
async function performTokenRefresh(): Promise<string> {
  const currentToken = getToken();

  if (!currentToken) {
    throw new Error('未找到Token');
  }

  try {
    const response = await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api'}/v1/auth/refresh`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': currentToken,
      },
      credentials: 'include',
    });

    if (!response.ok) {
      throw new Error('Token刷新失败');
    }

    const data = await response.json();

    if (!data.data || !data.data.token) {
      throw new Error('Token刷新失败：服务器未返回新Token');
    }

    const newToken = data.data.token;
    const expiresIn = data.data.expiresIn || 86400; // 默认24小时

    // 更��Token和过期时间
    setToken(newToken);
    setTokenExpiry(expiresIn);

    return newToken;
  } catch (error) {
    console.error('Token刷新失败:', error);
    throw error;
  }
}

/**
 * 清除Token过期时间
 */
export function clearTokenExpiry(): void {
  tokenExpiresAt = null;
}
