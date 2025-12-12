'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { useToast } from '@/hooks/use-toast';

/**
 * OAuth按钮组件
 *
 * 功能：
 * - 提供Google和GitHub OAuth登录按钮
 * - 处理OAuth重定向流程
 * - 显示加载状态和错误提示
 *
 * 使用场景：
 * - 登录页面
 * - 注册页面
 *
 * OAuth流程：
 * 1. 用户点击OAuth按钮
 * 2. 重定向到第三方授权页面
 * 3. 用户授权后重定向回callback
 * 4. 后端接收code并exchange token
 * 5. 返回JWT token给前端
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

interface OAuthButtonsProps {
  /**
   * 是否禁用按钮
   * 例如：用户未同意协议时禁用
   */
  disabled?: boolean;

  /**
   * 按钮布局方向
   * - horizontal: 水平排列（适合桌面端）
   * - vertical: 垂直排列（适合移动端）
   */
  layout?: 'horizontal' | 'vertical';
}

export function OAuthButtons({
  disabled = false,
  layout = 'horizontal'
}: OAuthButtonsProps) {
  const { toast } = useToast();
  const [loading, setLoading] = useState<'google' | 'github' | null>(null);

  /**
   * 处理Google OAuth登录
   * 构建授权URL并重定向
   */
  const handleGoogleLogin = () => {
    if (disabled) {
      toast({
        title: '请先同意用户协议',
        variant: 'destructive',
      });
      return;
    }

    try {
      setLoading('google');

      // Google OAuth配置
      const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
      const redirectUri = process.env.NEXT_PUBLIC_GOOGLE_REDIRECT_URI ||
        `${window.location.origin}/api/v1/auth/oauth/google/callback`;

      // 构建Google OAuth授权URL
      const authUrl = new URL('https://accounts.google.com/o/oauth2/v2/auth');
      authUrl.searchParams.set('client_id', clientId || '');
      authUrl.searchParams.set('redirect_uri', redirectUri);
      authUrl.searchParams.set('response_type', 'code');
      authUrl.searchParams.set('scope', 'openid email profile');
      authUrl.searchParams.set('access_type', 'offline');
      authUrl.searchParams.set('prompt', 'consent');

      // 重定向到Google授权页面
      window.location.href = authUrl.toString();
    } catch (error) {
      console.error('Google OAuth error:', error);
      setLoading(null);
      toast({
        title: 'Google登录失败',
        description: error instanceof Error ? error.message : '未知错误',
        variant: 'destructive',
      });
    }
  };

  /**
   * 处理GitHub OAuth登录
   * 构建授权URL并重定向
   */
  const handleGitHubLogin = () => {
    if (disabled) {
      toast({
        title: '请先同意用户协议',
        variant: 'destructive',
      });
      return;
    }

    try {
      setLoading('github');

      // GitHub OAuth配置
      const clientId = process.env.NEXT_PUBLIC_GITHUB_CLIENT_ID;
      const redirectUri = process.env.NEXT_PUBLIC_GITHUB_REDIRECT_URI ||
        `${window.location.origin}/api/v1/auth/oauth/github/callback`;

      // 构建GitHub OAuth授权URL
      const authUrl = new URL('https://github.com/login/oauth/authorize');
      authUrl.searchParams.set('client_id', clientId || '');
      authUrl.searchParams.set('redirect_uri', redirectUri);
      authUrl.searchParams.set('scope', 'user:email');

      // 重定向到GitHub授权页面
      window.location.href = authUrl.toString();
    } catch (error) {
      console.error('GitHub OAuth error:', error);
      setLoading(null);
      toast({
        title: 'GitHub登录失败',
        description: error instanceof Error ? error.message : '未知错误',
        variant: 'destructive',
      });
    }
  };

  const containerClass = layout === 'horizontal'
    ? 'flex flex-row gap-4'
    : 'flex flex-col gap-3';

  return (
    <div className={containerClass}>
      {/* Google登录按钮 */}
      <Button
        type="button"
        variant="outline"
        onClick={handleGoogleLogin}
        disabled={disabled || loading !== null}
        className="flex-1"
      >
        {loading === 'google' ? (
          <span className="flex items-center gap-2">
            <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
                fill="none"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
            正在跳转...
          </span>
        ) : (
          <span className="flex items-center gap-2">
            <svg className="h-5 w-5" viewBox="0 0 24 24">
              <path
                fill="currentColor"
                d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              />
              <path
                fill="currentColor"
                d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              />
              <path
                fill="currentColor"
                d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
              />
              <path
                fill="currentColor"
                d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
              />
            </svg>
            使用 Google 登录
          </span>
        )}
      </Button>

      {/* GitHub登录按钮 */}
      <Button
        type="button"
        variant="outline"
        onClick={handleGitHubLogin}
        disabled={disabled || loading !== null}
        className="flex-1"
      >
        {loading === 'github' ? (
          <span className="flex items-center gap-2">
            <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
                fill="none"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
            正在跳转...
          </span>
        ) : (
          <span className="flex items-center gap-2">
            <svg className="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" />
            </svg>
            使用 GitHub 登录
          </span>
        )}
      </Button>
    </div>
  );
}
