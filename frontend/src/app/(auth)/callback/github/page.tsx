import { Suspense } from 'react';
import GitHubCallbackContent from './github-callback-content';

/**
 * GitHub OAuth回调页面
 *
 * 功能：
 * - 接收GitHub OAuth授权码
 * - 调用后端完成GitHub登录
 * - 处理登录成功/失败
 * - 跳转到目标页面
 *
 * URL格式：
 * /callback/github?code=xxx&state=xxx
 *
 * 流程：
 * 1. GitHub重定向用户到此页面，携带授权码
 * 2. 前端提取授权码
 * 3. 调用后端GitHub登录API
 * 4. 后端验证授权码，获取用户信息，生成JWT token
 * 5. 前端存储token，跳转到dashboard
 *
 * Next.js 15要求：
 * - useSearchParams()必须在Suspense边界内使用
 * - 这样可以支持静态生成和流式渲染
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
export default function GitHubCallbackPage() {
  return (
    <Suspense
      fallback={
        <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-background to-muted">
          <div className="max-w-md w-full mx-auto p-8">
            <div className="bg-card rounded-lg shadow-lg p-8 text-center space-y-6">
              <div className="flex justify-center">
                <div className="w-16 h-16 bg-primary rounded-full flex items-center justify-center">
                  <span className="text-2xl font-bold text-primary-foreground">I</span>
                </div>
              </div>
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
              <h2 className="text-2xl font-semibold">加载中...</h2>
              <p className="text-muted-foreground">正在初始化登录流程</p>
            </div>
          </div>
        </div>
      }
    >
      <GitHubCallbackContent />
    </Suspense>
  );
}
