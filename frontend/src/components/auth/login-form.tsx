'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useToast } from '@/hooks/use-toast';
import { login } from '@/lib/api/auth';

/**
 * 登录表单组件
 *
 * 功能：
 * - 用户名/邮箱 + 密码登录
 * - 表单验证
 * - 错误提示
 * - 加载状态
 *
 * 使用场景：
 * - 登录页面 (/login)
 *
 * 登录流程：
 * 1. 用户输入用户名/邮箱和密码
 * 2. 前端验证表单
 * 3. 调用后端登录API
 * 4. 后端验证凭据并返回JWT token
 * 5. 前端存储token并跳转到首页
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

interface LoginFormProps {
  /**
   * 登录成功回调
   * 用于父组件处理登录后的逻辑（如跳转）
   */
  onSuccess?: () => void;
}

export function LoginForm({ onSuccess }: LoginFormProps) {
  const router = useRouter();
  const { toast } = useToast();

  // 表单状态
  const [identifier, setIdentifier] = useState(''); // 用户名或邮箱
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  // 表单验证错误
  const [errors, setErrors] = useState<{
    identifier?: string;
    password?: string;
  }>({});

  /**
   * 验证表单
   * 返回true表示验证通过
   */
  const validateForm = (): boolean => {
    const newErrors: typeof errors = {};

    // 验证用户名/邮箱
    if (!identifier.trim()) {
      newErrors.identifier = '请输入用户名或邮箱';
    } else if (identifier.length < 3) {
      newErrors.identifier = '用户名至少3个字符';
    }

    // 验证密码
    if (!password) {
      newErrors.password = '请输入密码';
    } else if (password.length < 6) {
      newErrors.password = '密码至少6个字符';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  /**
   * 处理登录提交
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // 验证表单
    if (!validateForm()) {
      return;
    }

    setLoading(true);
    setErrors({});

    try {
      // Phase 3.3完成 - 调用真实登录API
      const response = await login(identifier, password);

      toast({
        title: '登录成功',
        description: `欢迎回来，${response.username}！`,
      });

      // 调用成功回调或跳转到首页
      if (onSuccess) {
        onSuccess();
      } else {
        router.push('/');
      }
    } catch (error) {
      // console.error('Login error:', error); // 移除控制台报错，避免干扰用户
      toast({
        title: '登录失败',
        description: error instanceof Error ? error.message : '用户名或密码错误',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full space-y-6">
      <form onSubmit={handleSubmit} className="space-y-4">
        {/* 用户名/邮箱输入 */}
        <div className="space-y-2">
          <Label htmlFor="identifier" className="text-slate-700 dark:text-slate-300">用户名或邮箱</Label>
          <Input
            id="identifier"
            type="text"
            placeholder="请输入用户名或邮箱"
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            disabled={loading}
            className={`text-slate-900 dark:text-slate-100 bg-white/80 dark:bg-slate-900/50 border-slate-200 dark:border-slate-700 placeholder:text-slate-400 ${errors.identifier ? 'border-red-500' : ''}`}
          />
          {errors.identifier && (
            <p className="text-sm text-red-500">{errors.identifier}</p>
          )}
        </div>

        {/* 密码输入 */}
        <div className="space-y-2">
          <Label htmlFor="password" className="text-slate-700 dark:text-slate-300">密码</Label>
          <Input
            id="password"
            type="password"
            placeholder="请输入密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={loading}
            className={`text-slate-900 dark:text-slate-100 bg-white/80 dark:bg-slate-900/50 border-slate-200 dark:border-slate-700 placeholder:text-slate-400 ${errors.password ? 'border-red-500' : ''}`}
          />
          {errors.password && (
            <p className="text-sm text-red-500">{errors.password}</p>
          )}
          <div className="flex justify-end">
            <Link
              href="/forgot-password"
              className="text-sm text-indigo-500 hover:text-indigo-400 hover:underline"
            >
              忘记密码？
            </Link>
          </div>
        </div>

        {/* 登录按钮 */}
        <Button
          type="submit"
          className="w-full bg-indigo-600 hover:bg-indigo-500 text-white"
          disabled={loading}
        >
          {loading ? (
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
              登录中...
            </span>
          ) : (
            '登录'
          )}
        </Button>
      </form>
    </div>
  );
}
