import Link from 'next/link';

import { RegisterForm } from '@/components/auth/register-form';

/**
 * 注册页面
 *
 * 路由: /register
 *
 * 功能：
 * - 展示注册表单
 * - 支持用户名 + 邮箱 + 密码注册
 * - 支持OAuth注册（Google、GitHub）
 * - 提供登录链接
 * - 用户协议确认
 *
 * 页面布局：
 * - 居中卡片式设计
 * - 响应式布局（桌面端和移动端）
 * - 统一的认证页面风格
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

export default function RegisterPage() {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-[#0f172a] p-4 text-slate-200">
      {/* 极简风格背景 */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-indigo-900/20 via-slate-900 to-slate-900" />
      
      {/* 装饰性光晕 - 更微妙 */}
      <div className="absolute -top-[20%] -right-[10%] h-[600px] w-[600px] rounded-full bg-indigo-500/10 blur-[100px]" />
      <div className="absolute -bottom-[20%] -left-[10%] h-[600px] w-[600px] rounded-full bg-blue-500/10 blur-[100px]" />

      {/* 内容层 */}
      <div className="relative z-10 flex w-full flex-col items-center max-w-[420px]">
        
        {/* 品牌Logo - 更专业的设计 */}
        <div className="mb-10 flex flex-col items-center gap-4">
          <Link href="/" className="flex flex-col items-center gap-4 hover:opacity-90 transition-opacity">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-tr from-indigo-500 to-blue-600 shadow-lg shadow-indigo-500/20">
              <span className="text-2xl font-bold text-white">秒</span>
            </div>
            <div className="text-center">
              <h1 className="text-2xl font-semibold text-white tracking-tight">
                秒构 AI
              </h1>
              <p className="mt-2 text-sm text-slate-400">
                AI驱动的自然语言编程平台
              </p>
            </div>
          </Link>
        </div>

        {/* 注册卡片 */}
        <div className="w-full rounded-2xl border border-white/10 bg-white/5 p-6 shadow-xl backdrop-blur-xl sm:p-8">
          <div className="mb-6 space-y-1">
            <h2 className="text-2xl font-bold text-white">创建账号</h2>
            <p className="text-sm text-slate-400">
              输入你的信息以创建一个新账号
            </p>
          </div>
          <RegisterForm />
        </div>

        {/* 页脚链接 */}
        <div className="mt-8 flex flex-col items-center gap-2 text-center text-xs text-slate-500">
          <p>
            注册即表示你同意我们的{' '}
            <Link href="/terms" className="text-indigo-400 hover:text-indigo-300 hover:underline">
              服务条款
            </Link>{' '}
            和{' '}
            <Link href="/privacy" className="text-indigo-400 hover:text-indigo-300 hover:underline">
              隐私政策
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * 页面元数据
 * 用于SEO优化和页面标题
 */
export const metadata = {
  title: '注册 - Ingenio',
  description: '创建Ingenio账号，开始使用AI驱动的自然语言编程平台',
};
