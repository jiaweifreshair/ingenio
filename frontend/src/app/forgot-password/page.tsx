'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { VerificationCodeInput } from '@/components/auth/verification-code-input';
import { CountdownButton } from '@/components/auth/countdown-button';
import { useToast } from '@/hooks/use-toast';
import { sendEmailVerificationCode, verifyEmailCode } from '@/lib/api/auth';
import { ArrowLeft, Mail, Lock, CheckCircle } from 'lucide-react';

/**
 * 找回密码页面
 *
 * 功能：
 * - 三步密码重置流程
 * - 邮箱验证码验证
 * - 密码强度验证
 * - 友好的错误提示
 *
 * 流程：
 * Step 1: 输入邮箱地址 → 发送验证码
 * Step 2: 输入验证码 → 自动验证
 * Step 3: 设置新密码 → 完成重置
 *
 * @author Ingenio Team
 * @since Phase 6.1
 */
export default function ForgotPasswordPage() {
  const { toast } = useToast();

  // 当前步骤（1-3）
  const [currentStep, setCurrentStep] = useState(1);

  // Step 1: 邮箱输入
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState('');

  // Step 2: 验证码
  const [verificationCode, setVerificationCode] = useState('');
  const [verificationError, setVerificationError] = useState('');

  // Step 3: 新密码
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');

  // 加载状态
  const [isSubmitting, setIsSubmitting] = useState(false);

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
   * Step 1: 发送验证码
   */
  const handleSendCode = async () => {
    // 验证邮箱
    if (!email.trim()) {
      setEmailError('请输入邮箱地址');
      return;
    }

    if (!isValidEmail(email)) {
      setEmailError('邮箱格式不正确');
      return;
    }

    setEmailError('');

    try {
      // 调用API发送验证码
      await sendEmailVerificationCode(email, 'RESET_PASSWORD');

      toast({
        title: '验证码已发送',
        description: '请查收邮件，验证码5分钟内有效',
      });

      // 进入Step 2
      setCurrentStep(2);
    } catch (error) {
      console.error('Send code error:', error);
      toast({
        title: '发送失败',
        description: error instanceof Error ? error.message : '发送验证码失败，请稍后重试',
        variant: 'destructive',
      });
      throw error; // 重新抛出错误，让CountdownButton不启动倒计时
    }
  };

  /**
   * Step 2: 验证验证码
   */
  const handleVerifyCode = async (code: string) => {
    setVerificationCode(code);

    if (code.length !== 6) {
      return;
    }

    setIsSubmitting(true);
    setVerificationError('');

    try {
      // 调用API验证验证码
      await verifyEmailCode(email, code, 'RESET_PASSWORD');

      toast({
        title: '验证成功',
        description: '请设置新密码',
      });

      // 进入Step 3
      setCurrentStep(3);
    } catch (error) {
      console.error('Verify code error:', error);
      setVerificationError(error instanceof Error ? error.message : '验证码错误或已过期');
      toast({
        title: '验证失败',
        description: error instanceof Error ? error.message : '验证码错误或已过期',
        variant: 'destructive',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  /**
   * Step 3: 重置密码
   */
  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();

    // 验证新密码
    if (!newPassword) {
      setPasswordError('请输入新密码');
      return;
    }

    if (!isStrongPassword(newPassword)) {
      setPasswordError('密码至少8个字符，需包含大小写字母和数字');
      return;
    }

    // 验证密码确认
    if (newPassword !== confirmPassword) {
      setPasswordError('两次输入的密码不一致');
      return;
    }

    setPasswordError('');
    setIsSubmitting(true);

    try {
      // 调用重置密码API（使用验证码方式）
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api'}/v1/auth/reset-password-by-code`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          credentials: 'include',
          body: JSON.stringify({
            email,
            code: verificationCode,
            newPassword,
          }),
        }
      );

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.message || '密码重置失败');
      }

      toast({
        title: '密码重置成功',
        description: '请使用新密码登录',
      });

      // 跳转到登录页
      window.location.href = '/login';
    } catch (error) {
      console.error('Reset password error:', error);
      toast({
        title: '重置失败',
        description: error instanceof Error ? error.message : '密码重置失败，请稍后重试',
        variant: 'destructive',
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  /**
   * 返回上一步
   */
  const handleBackStep = () => {
    if (currentStep > 1) {
      setCurrentStep(currentStep - 1);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        {/* Logo和标题 */}
        <div className="text-center">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">秒构AI</h1>
          <p className="text-sm text-gray-600">找回密码</p>
        </div>

        {/* 步骤指示器 */}
        <div className="flex items-center justify-center space-x-4">
          {[1, 2, 3].map((step) => (
            <div key={step} className="flex items-center">
              <div
                className={`w-10 h-10 rounded-full flex items-center justify-center text-sm font-semibold transition-all ${
                  step < currentStep
                    ? 'bg-green-500 text-white'
                    : step === currentStep
                      ? 'bg-primary text-white ring-4 ring-primary/20'
                      : 'bg-gray-200 text-gray-600'
                }`}
              >
                {step < currentStep ? <CheckCircle className="w-5 h-5" /> : step}
              </div>
              {step < 3 && (
                <div
                  className={`w-12 h-1 mx-2 ${
                    step < currentStep ? 'bg-green-500' : 'bg-gray-200'
                  }`}
                />
              )}
            </div>
          ))}
        </div>

        {/* 主要内容 */}
        <div className="bg-white shadow-xl rounded-2xl p-8">
          {/* Step 1: 输入邮箱 */}
          {currentStep === 1 && (
            <div className="space-y-6">
              <div className="text-center space-y-2">
                <div className="mx-auto w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
                  <Mail className="w-6 h-6 text-blue-600" />
                </div>
                <h2 className="text-2xl font-semibold text-gray-900">验证邮箱</h2>
                <p className="text-sm text-gray-600">我们将向您的邮箱发送验证码</p>
              </div>

              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="email">邮箱地址</Label>
                  <Input
                    id="email"
                    type="email"
                    placeholder="请输入注册时使用的邮箱"
                    value={email}
                    onChange={(e) => {
                      setEmail(e.target.value);
                      setEmailError('');
                    }}
                    className={emailError ? 'border-red-500' : ''}
                  />
                  {emailError && <p className="text-sm text-red-500">{emailError}</p>}
                </div>

                <CountdownButton
                  onClick={handleSendCode}
                  className="w-full"
                  size="lg"
                  initialText="发送验证码"
                />
              </div>
            </div>
          )}

          {/* Step 2: 验证验证码 */}
          {currentStep === 2 && (
            <div className="space-y-6">
              <div className="text-center space-y-2">
                <div className="mx-auto w-12 h-12 bg-green-100 rounded-full flex items-center justify-center">
                  <Mail className="w-6 h-6 text-green-600" />
                </div>
                <h2 className="text-2xl font-semibold text-gray-900">输入验证码</h2>
                <p className="text-sm text-gray-600">
                  验证码已发送至 <span className="font-medium text-gray-900">{email}</span>
                </p>
              </div>

              <div className="space-y-4">
                <VerificationCodeInput
                  onChange={setVerificationCode}
                  onComplete={handleVerifyCode}
                  error={!!verificationError}
                  errorMessage={verificationError}
                  disabled={isSubmitting}
                />

                <div className="flex items-center justify-between text-sm">
                  <button
                    type="button"
                    onClick={handleBackStep}
                    className="text-gray-600 hover:text-gray-900 flex items-center gap-1"
                  >
                    <ArrowLeft className="w-4 h-4" />
                    返回上一步
                  </button>
                  <CountdownButton
                    onClick={handleSendCode}
                    variant="ghost"
                    size="sm"
                    initialText="重新发送"
                  />
                </div>
              </div>
            </div>
          )}

          {/* Step 3: 设置新密码 */}
          {currentStep === 3 && (
            <form onSubmit={handleResetPassword} className="space-y-6">
              <div className="text-center space-y-2">
                <div className="mx-auto w-12 h-12 bg-purple-100 rounded-full flex items-center justify-center">
                  <Lock className="w-6 h-6 text-purple-600" />
                </div>
                <h2 className="text-2xl font-semibold text-gray-900">设置新密码</h2>
                <p className="text-sm text-gray-600">请设置一个强密码来保护您的账户</p>
              </div>

              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="newPassword">新密码</Label>
                  <Input
                    id="newPassword"
                    type="password"
                    placeholder="至少8个字符，包含大小写字母和数字"
                    value={newPassword}
                    onChange={(e) => {
                      setNewPassword(e.target.value);
                      setPasswordError('');
                    }}
                    disabled={isSubmitting}
                    className={passwordError ? 'border-red-500' : ''}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="confirmPassword">确认新密码</Label>
                  <Input
                    id="confirmPassword"
                    type="password"
                    placeholder="请再次输入新密码"
                    value={confirmPassword}
                    onChange={(e) => {
                      setConfirmPassword(e.target.value);
                      setPasswordError('');
                    }}
                    disabled={isSubmitting}
                    className={passwordError ? 'border-red-500' : ''}
                  />
                </div>

                {passwordError && <p className="text-sm text-red-500">{passwordError}</p>}

                <div className="flex gap-3">
                  <Button
                    type="button"
                    onClick={handleBackStep}
                    variant="outline"
                    className="flex-1"
                    disabled={isSubmitting}
                  >
                    返回
                  </Button>
                  <Button type="submit" className="flex-1" disabled={isSubmitting}>
                    {isSubmitting ? '提交中...' : '完成重置'}
                  </Button>
                </div>
              </div>
            </form>
          )}
        </div>

        {/* 返回登录链接 */}
        <div className="text-center text-sm">
          <span className="text-gray-600">记起密码了？</span>{' '}
          <Link href="/login" className="text-primary hover:underline font-medium">
            返回登录
          </Link>
        </div>
      </div>
    </div>
  );
}
