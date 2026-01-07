/**
 * 登录页面
 * 支持多种登录方式：微信扫码、账号密码
 *
 * 功能：
 * - 微信扫码登录
 * - 用户名/邮箱 + 密码登录
 * - OAuth登录（Google、GitHub）
 * - 用户协议确认
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

'use client';

import { useState } from 'react';
import Link from 'next/link';
import NextImage from 'next/image';
import { useRouter } from 'next/navigation';
import { WechatLogin } from '@/components/auth/wechat-login';
import { LoginForm } from '@/components/auth/login-form';
import { Checkbox } from '@/components/ui/checkbox';
import { useToast } from '@/hooks/use-toast';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

/**
 * 登录方式枚举
 */
type LoginMode = 'account' | 'wechat';

/**
 * 登录页面组件
 */
export default function LoginPage(): React.ReactElement {
  const router = useRouter();
  const { toast } = useToast();

  // 登录方式状态（默认账号密码登录）
  const [loginMode, setLoginMode] = useState<LoginMode>('account');

  // 用户协议勾选状态（默认勾选）
  const [agreedToTerms, setAgreedToTerms] = useState(true);

  /**
   * 处理登录成功
   */
  const handleLoginSuccess = () => {
    toast({
      title: '登录成功',
      description: '欢迎回来！',
    });

    // 跳转到首页
    router.push('/');
  };

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-[#0f172a] p-4 text-slate-200">
      {/* 极简风格背景 */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-indigo-900/20 via-slate-900 to-slate-900" />
      
      {/* 装饰性光晕 - 更微妙 */}
      <div className="absolute -top-[20%] -right-[10%] h-[600px] w-[600px] rounded-full bg-indigo-500/10 blur-[100px]" />
      <div className="absolute -bottom-[20%] -left-[10%] h-[600px] w-[600px] rounded-full bg-blue-500/10 blur-[100px]" />

      {/* 内容层 */}
      <div className="relative z-10 flex w-full flex-col items-center max-w-[420px]">
        
        <div className="mb-10 flex flex-col items-center gap-4">
          <NextImage src="/logo.png" alt="Ingenio Logo" width={128} height={128} className="h-32 w-32" />
          <div className="text-center">
            <h1 className="text-2xl font-semibold text-white tracking-tight">
              Ingenio 妙构
            </h1>
            <p className="mt-2 text-sm text-slate-400">
              激发创意，快速构建下一代应用
            </p>
          </div>
        </div>

        {/* 登录卡片 */}
        <div className="w-full rounded-2xl border border-white/10 bg-white/5 p-6 shadow-xl backdrop-blur-xl sm:p-8">
          <Tabs
            value={loginMode}
            onValueChange={(value) => setLoginMode(value as LoginMode)}
            className="w-full"
          >
            <TabsList className="grid w-full grid-cols-2 bg-black/20 p-1 mb-6">
              <TabsTrigger
                value="account"
                className="data-[state=active]:bg-white/10 data-[state=active]:text-white text-slate-400 transition-all duration-200"
              >
                账号登录
              </TabsTrigger>
              <TabsTrigger
                value="wechat"
                className="data-[state=active]:bg-white/10 data-[state=active]:text-white text-slate-400 transition-all duration-200"
              >
                微信登录
              </TabsTrigger>
            </TabsList>

            <TabsContent value="account" className="mt-0 outline-none">
              <LoginForm onSuccess={handleLoginSuccess} />
            </TabsContent>

            <TabsContent value="wechat" className="mt-0 outline-none">
              <div className="flex flex-col items-center justify-center py-4 space-y-4">
                <WechatLogin
                  onSuccess={handleLoginSuccess}
                  disabled={!agreedToTerms}
                />
                <p className="text-xs text-slate-500">
                  请使用微信扫描二维码登录
                </p>
              </div>
            </TabsContent>
          </Tabs>

          {/* 协议勾选 - 样式优化 */}
          <div className="mt-6 flex items-start gap-3 px-1">
            <Checkbox
              id="terms"
              checked={agreedToTerms}
              onCheckedChange={(checked) => setAgreedToTerms(checked as boolean)}
              className="mt-0.5 border-slate-600 data-[state=checked]:bg-indigo-500 data-[state=checked]:border-indigo-500"
            />
            <label htmlFor="terms" className="cursor-pointer text-xs leading-relaxed text-slate-400 select-none">
              我已阅读并同意
              <Link href="/terms" className="text-indigo-400 hover:text-indigo-300 hover:underline ml-1" target="_blank">
                《用户协议》
              </Link>
              和
              <Link href="/privacy" className="text-indigo-400 hover:text-indigo-300 hover:underline ml-1" target="_blank">
                《隐私政策》
              </Link>
            </label>
          </div>
        </div>

        {/* 底部版权 */}
        <div className="mt-8 text-center">
          <p className="text-xs text-slate-600">
            © 2025 Ingenio AI. All rights reserved.
          </p>
        </div>
      </div>
    </div>
  );
}
