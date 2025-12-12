'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuthStore } from '@/stores/auth-store';
import { useToast } from '@/hooks/use-toast';

/**
 * Google OAuth回调页面内容组件
 *
 * 功能：
 * - 处理Google OAuth授权码
 * - ���用后端完成Google登录
 * - 处理登录成功/失败状态
 * - 跳转到目标页面
 *
 * 此组件必须在Suspense边界内使用（因为使用了useSearchParams）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
export default function GoogleCallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { toast } = useToast();
  const loginWithGoogle = useAuthStore((state) => state.loginWithGoogle);

  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    const handleGoogleCallback = async () => {
      try {
        // 提取授权码
        const code = searchParams.get('code');
        const error = searchParams.get('error');

        // 检查是否有错误（用户拒绝授权等）
        if (error) {
          setStatus('error');
          setErrorMessage(
            error === 'access_denied'
              ? '您已取消Google授权'
              : `Google授权失败: ${error}`
          );
          toast({
            title: 'Google登录失败',
            description: errorMessage || 'Google授权失败',
            variant: 'destructive',
          });
          setTimeout(() => router.push('/login'), 2000);
          return;
        }

        // 检查授权码是否存在
        if (!code) {
          setStatus('error');
          setErrorMessage('缺少授权码');
          toast({
            title: 'Google登录失败',
            description: '缺少授权码，请重试',
            variant: 'destructive',
          });
          setTimeout(() => router.push('/login'), 2000);
          return;
        }

        // 调用后端Google登录API
        await loginWithGoogle(code);

        // 登录成功
        setStatus('success');
        toast({
          title: 'Google登录成功',
          description: '欢迎回来！',
        });

        // 跳转到首页
        setTimeout(() => router.push('/'), 1000);
      } catch (error) {
        console.error('Google OAuth callback error:', error);
        setStatus('error');
        setErrorMessage(
          error instanceof Error ? error.message : 'Google登录失败，请重试'
        );
        toast({
          title: 'Google登录失败',
          description: errorMessage,
          variant: 'destructive',
        });
        setTimeout(() => router.push('/login'), 2000);
      }
    };

    handleGoogleCallback();
  }, [searchParams, loginWithGoogle, router, toast, errorMessage]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-background to-muted">
      <div className="max-w-md w-full mx-auto p-8">
        <div className="bg-card rounded-lg shadow-lg p-8 text-center space-y-6">
          {/* Logo */}
          <div className="flex justify-center">
            <div className="w-16 h-16 bg-primary rounded-full flex items-center justify-center">
              <span className="text-2xl font-bold text-primary-foreground">I</span>
            </div>
          </div>

          {/* 状态显示 */}
          {status === 'loading' && (
            <>
              <div className="flex justify-center">
                <svg
                  className="animate-spin h-12 w-12 text-primary"
                  viewBox="0 0 24 24"
                >
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
              </div>
              <h2 className="text-2xl font-semibold">正在完成Google登录...</h2>
              <p className="text-muted-foreground">请稍候，正在验证您的账号</p>
            </>
          )}

          {status === 'success' && (
            <>
              <div className="flex justify-center">
                <svg
                  className="h-12 w-12 text-green-500"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              </div>
              <h2 className="text-2xl font-semibold text-green-600">登录成功！</h2>
              <p className="text-muted-foreground">正在跳转到控制台...</p>
            </>
          )}

          {status === 'error' && (
            <>
              <div className="flex justify-center">
                <svg
                  className="h-12 w-12 text-red-500"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
              </div>
              <h2 className="text-2xl font-semibold text-red-600">登录失败</h2>
              <p className="text-muted-foreground">{errorMessage}</p>
              <p className="text-sm text-muted-foreground">正在返回登录页...</p>
            </>
          )}
        </div>
      </div>

      {/* 背景装饰 */}
      <div className="fixed inset-0 -z-10 overflow-hidden">
        <div className="absolute -top-40 -right-40 h-80 w-80 rounded-full bg-primary/20 blur-3xl" />
        <div className="absolute -bottom-40 -left-40 h-80 w-80 rounded-full bg-secondary/20 blur-3xl" />
      </div>
    </div>
  );
}
