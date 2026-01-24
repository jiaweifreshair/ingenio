/**
 * Next.js Middleware - 路由守卫
 * 在Edge Runtime中运行，保护需要认证的路由
 *
 * 功能：
 * - 检查用户认证状态（通过Cookie中的Token）
 * - 未认证用户重定向到登录页
 * - 已认证用户访问登录页时重定向到dashboard
 *
 * @author Ingenio Team
 * @since 1.0.0 - Phase 5
 */

import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

/**
 * 需要认证保护的路由路径
 * 这些路径必须登录后才能访问
 */
const protectedPaths = [
  '/dashboard',
  '/create',
  '/wizard',
  '/publish',
  '/settings',
  '/account',
  '/notifications',
  '/templates',
  '/versions',
  '/superdesign',
];

/**
 * 公开路径（不需要认证）
 * 已认证用户访问这些路径时会被重定向到dashboard
 */
const publicPaths = [
  '/login',
  '/(auth)/login',
  '/(auth)/register',
  '/(auth)/oauth/callback',
];

/**
 * 检查路径是否需要认证保护
 *
 * @param pathname - 请求路径
 * @returns 如果需要保护返回true
 */
function isProtectedPath(pathname: string): boolean {
  return protectedPaths.some(path => pathname.startsWith(path));
}

/**
 * 检查路径是否为公开路径（登录/注册）
 *
 * @param pathname - 请求路径
 * @returns 如果是公开路径返回true
 */
function isPublicPath(pathname: string): boolean {
  return publicPaths.some(path => pathname.startsWith(path));
}

/**
 * 从Cookie中获取认证Token
 *
 * @param request - Next.js请求对象
 * @returns Token字符串，如果不存在返回null
 */
function getTokenFromCookie(request: NextRequest): string | null {
  return request.cookies.get('auth_token')?.value || null;
}

/**
 * Next.js Middleware主函数
 * 在每个请求到达路由处理器之前执行
 *
 * @param request - Next.js请求对象
 * @returns NextResponse - 响应对象（允许通过、重定向或拒绝）
 */
export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // 获取认证Token（从Cookie）
  const token = getTokenFromCookie(request);
  const isAuthenticated = !!token;

  // 调试日志（仅开发环境）
  if (process.env.NODE_ENV === 'development') {
    console.log('[Middleware]', {
      pathname,
      isAuthenticated,
      hasToken: !!token,
    });
  }

  // 情况1: 访问受保护的路径，但未认证
  if (isProtectedPath(pathname) && !isAuthenticated) {
    console.log('[Middleware] 未认证用户访问受保护路径，重定向到登录页:', pathname);

    // 构建登录URL，并记录原始访问路径（登录后可返回）
    const loginUrl = new URL('/login', request.url);
    loginUrl.searchParams.set('redirect', pathname);

    return NextResponse.redirect(loginUrl);
  }

  // 情况2: 已认证用户访问公开路径（登录/注册页）
  if (isPublicPath(pathname) && isAuthenticated) {
    console.log('[Middleware] 已认证用户访问登录页，重定向到首页');

    // 重定向到首页
    const homeUrl = new URL('/', request.url);
    return NextResponse.redirect(homeUrl);
  }

  // 情况3: API路由和静态资源，直接放行
  if (pathname.startsWith('/api') || pathname.startsWith('/_next') || pathname.includes('.')) {
    return NextResponse.next();
  }

  // 情况4: 其他路径，允许访问
  return NextResponse.next();
}

/**
 * Middleware配置
 * 定义哪些路径需要执行middleware
 *
 * 排除：
 * - API路由: /api/*
 * - Next.js内部路由: /_next/*
 * - 静态文件: *.ico, *.png, *.svg, *.jpg, *.jpeg, *.gif, *.webp
 */
export const config = {
  matcher: [
    /*
     * 匹配所有路径，除了：
     * - api路由 (以 /api 开头)
     * - _next静态文件 (以 /_next 开头)
     * - 图标文件 (.ico, .png, .svg, .jpg, .jpeg, .gif, .webp)
     */
    '/((?!api|_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
};
