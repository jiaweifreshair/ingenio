'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { useToast } from '@/hooks/use-toast';
import { register } from '@/lib/api/auth';

/**
 * 注册表单组件
 *
 * 功能：
 * - 用户注册（用户名、邮箱、密码）
 * - 表单验证（邮箱格式、密码强度、密码确认）
 * - 用户协议确认
 * - 错误提示
 * - 加载状态
 *
 * 使用场景：
 * - 注册页面 (/register)
 *
 * 注册流程：
 * 1. 用户输入用户名、邮箱、密码并同意协议
 * 2. 前端验证表单
 * 3. 调用后端注册API
 * 4. 后端创建用户并返回JWT token
 * 5. 前端存储token并跳转到控制台
 *
 * @author Ingenio Team
 * @since 1.0.0
 * @updated 2025-01-XX - 移除邮箱验证码功能，简化注册流程
 */

interface RegisterFormProps {
  /**
   * 注册成功回调
   * 用于父组件处理注册后的逻辑（如跳转）
   */
  onSuccess?: () => void;
}

export function RegisterForm({ onSuccess }: RegisterFormProps) {
  const router = useRouter();
  const { toast } = useToast();

  // 表单状态
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [agreedToTerms, setAgreedToTerms] = useState(false);
  const [loading, setLoading] = useState(false);

  // 表单验证错误
  const [errors, setErrors] = useState<{
    username?: string;
    email?: string;
    password?: string;
    confirmPassword?: string;
    terms?: string;
  }>({});

  /**
   * 验证邮箱格式
   */
  const isValidEmail = (email: string): boolean => {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  };

  /**
   * 验证密码强度
   * 要求：至少8个字符，包含大小写字母和数字
   */
  const isStrongPassword = (password: string): boolean => {
    if (password.length < 8) return false;
    const hasUpperCase = /[A-Z]/.test(password);
    const hasLowerCase = /[a-z]/.test(password);
    const hasNumber = /[0-9]/.test(password);
    return hasUpperCase && hasLowerCase && hasNumber;
  };


  /**
   * 验证表单
   * 返回true表示验证通过
   */
  const validateForm = (): boolean => {
    const newErrors: typeof errors = {};

    // 验证用户名
    if (!username.trim()) {
      newErrors.username = '请输入用户名';
    } else if (username.length < 3) {
      newErrors.username = '用户名至少3个字符';
    } else if (username.length > 20) {
      newErrors.username = '用户名最多20个字符';
    } else if (!/^[a-zA-Z0-9_]+$/.test(username)) {
      newErrors.username = '用户名只能包含字母、数字和下划线';
    }

    // 验证邮箱
    if (!email.trim()) {
      newErrors.email = '请输入邮箱';
    } else if (!isValidEmail(email)) {
      newErrors.email = '邮箱格式不正确';
    }


    // 验证密码
    if (!password.trim()) {
      newErrors.password = '请输入密码';
    } else if (password.length < 8) {
      newErrors.password = '密码至少8个字符';
    } else if (!isStrongPassword(password)) {
      newErrors.password = '密码必须包含大小写字母和数字';
    }

    // 验证确认密码
    if (!confirmPassword.trim()) {
      newErrors.confirmPassword = '请确认密码';
    } else if (confirmPassword !== password) {
      newErrors.confirmPassword = '两次输入的密码不一致';
    }

    // 验证用户协议
    if (!agreedToTerms) {
      newErrors.terms = '请阅读并同意用户协议和隐私政策';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  /**
   * 处理注册提交
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
      // 调用真实注册API
      const response = await register(username, email, password);

      toast({
        title: '注册成功',
        description: `欢迎加入Ingenio，${response.username}！`,
      });

      // 调用成功回调或跳转到首页
      if (onSuccess) {
        onSuccess();
      } else {
        router.push('/');
      }
    } catch (error) {
      console.error('Register error:', error);
      toast({
        title: '注册失败',
        description: error instanceof Error ? error.message : '注册失败，请稍后重试',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full space-y-6">
      <form onSubmit={handleSubmit} className="space-y-4">
        {/* 用户名输入 */}
        <div className="space-y-2">
          <Label htmlFor="username">用户名</Label>
          <Input
            id="username"
            type="text"
            placeholder="请输入用户名（3-20个字符）"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            disabled={loading}
            className={errors.username ? 'border-red-500' : ''}
          />
          {errors.username && (
            <p className="text-sm text-red-500">{errors.username}</p>
          )}
        </div>

        {/* 邮箱输入 */}
        <div className="space-y-2">
          <Label htmlFor="email">邮箱</Label>
          <Input
            id="email"
            type="email"
            placeholder="请输入邮箱地址"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={loading}
            className={errors.email ? 'border-red-500' : ''}
          />
          {errors.email && (
            <p className="text-sm text-red-500">{errors.email}</p>
          )}
        </div>


        {/* 密码输入 */}
        <div className="space-y-2">
          <Label htmlFor="password">密码</Label>
          <Input
            id="password"
            type="password"
            placeholder="请输入密码（至少8个字符）"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={loading}
            className={errors.password ? 'border-red-500' : ''}
          />
          {errors.password && (
            <p className="text-sm text-red-500">{errors.password}</p>
          )}
        </div>

        {/* 确认密码输入 */}
        <div className="space-y-2">
          <Label htmlFor="confirmPassword">确认密码</Label>
          <Input
            id="confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            disabled={loading}
            className={errors.confirmPassword ? 'border-red-500' : ''}
          />
          {errors.confirmPassword && (
            <p className="text-sm text-red-500">{errors.confirmPassword}</p>
          )}
        </div>

        {/* 用户协议 */}
        <div className="space-y-2">
          <div className="flex items-start gap-2">
            <Checkbox
              checked={agreedToTerms}
              onCheckedChange={(checked) => setAgreedToTerms(checked === true)}
              disabled={loading}
            />
            <label className="text-sm leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">
              我已阅读并同意{' '}
              <Link href="/terms" className="text-primary hover:underline">
                用户协议
              </Link>{' '}
              和{' '}
              <Link href="/privacy" className="text-primary hover:underline">
                隐私政策
              </Link>
            </label>
          </div>
          {errors.terms && (
            <p className="text-sm text-red-500">{errors.terms}</p>
          )}
        </div>

        {/* 注册按钮 */}
        <Button
          type="submit"
          className="w-full"
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
              注册中...
            </span>
          ) : (
            '注册'
          )}
        </Button>
      </form>

      {/* 登录链接 */}
      <div className="text-center text-sm">
        <span className="text-muted-foreground">已有账号？</span>{' '}
        <Link href="/login" className="text-primary hover:underline">
          立即登录
        </Link>
      </div>
    </div>
  );
}
