/**
 * Token管理工具
 * 处理JWT认证Token的存储、获取和清除
 *
 * V2.0升级：同时支持localStorage和Cookie
 * - localStorage: 客户端JavaScript访问
 * - Cookie: Next.js middleware (Edge Runtime) 访问
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @updated 2025-11-21 - Phase 5.2
 */

// Token存储键名
const TOKEN_KEY = 'auth_token';

// Cookie配置
const COOKIE_MAX_AGE = 7 * 24 * 60 * 60; // 7天（秒）

/**
 * 存储认证Token
 * 同时写入localStorage和Cookie，确保middleware和客户端都能访问
 *
 * @param token - JWT认证Token
 */
export function setToken(token: string): void {
  if (typeof window !== 'undefined') {
    // 写入localStorage（客户端访问）
    localStorage.setItem(TOKEN_KEY, token);

    // 写入Cookie（middleware访问）
    setTokenCookie(token);
  }
}

/**
 * 获取认证Token
 * 优先从localStorage读取，如果不存在则从Cookie读取
 *
 * @returns 存储的Token，如果不存在则返回null
 */
export function getToken(): string | null {
  if (typeof window !== 'undefined') {
    // 优先从localStorage读取
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
      return token;
    }

    // 如果localStorage不存在，尝试从Cookie读取
    return getTokenFromCookie();
  }
  return null;
}

/**
 * 清除存储的认证Token
 * 同时清除localStorage和Cookie
 * 通常在用户登出或Token过期时调用
 */
export function clearToken(): void {
  if (typeof window !== 'undefined') {
    // 清除localStorage
    localStorage.removeItem(TOKEN_KEY);

    // 清除Cookie
    clearTokenCookie();
  }
}

/**
 * 检查Token是否存在
 *
 * @returns 如果Token存在返回true，否则返回false
 */
export function hasToken(): boolean {
  return !!getToken();
}

/**
 * 设置Token到Cookie
 *
 * @param token - JWT认证Token
 */
function setTokenCookie(token: string): void {
  // 判断是否为生产环境
  const isProduction = process.env.NODE_ENV === 'production';

  // Cookie配置
  const cookieOptions = [
    `${TOKEN_KEY}=${token}`,
    `max-age=${COOKIE_MAX_AGE}`,
    'path=/',
    'samesite=lax', // 允许跨站导航携带Cookie
    ...(isProduction ? ['secure'] : []), // 生产环境强制HTTPS
  ].join('; ');

  document.cookie = cookieOptions;
}

/**
 * 从Cookie读取Token
 *
 * @returns Token字符串，如果不存在返回null
 */
function getTokenFromCookie(): string | null {
  const cookies = document.cookie.split('; ');
  const tokenCookie = cookies.find(cookie => cookie.startsWith(`${TOKEN_KEY}=`));

  if (tokenCookie) {
    return tokenCookie.split('=')[1];
  }

  return null;
}

/**
 * 清除Token Cookie
 */
function clearTokenCookie(): void {
  // 设置过期时间为过去，浏览器会自动删除Cookie
  document.cookie = `${TOKEN_KEY}=; path=/; max-age=0`;
}
