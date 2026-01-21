import type { NextConfig } from "next";

/**
 * Next.js配置
 * 秒构AI前端配置
 */
// 说明：当本机存在多个 lockfile（例如用户目录下的 pnpm-lock.yaml）时，
// Next.js 可能会错误推断 workspace root 并给出警告，甚至影响构建/追踪。
// 显式指定 outputFileTracingRoot，可稳定 monorepo/多 lockfile 场景下的行为。
const outputFileTracingRoot = typeof __dirname === 'string' ? __dirname : process.cwd();
const isDev = process.env.NODE_ENV === "development";

const nextConfig: NextConfig = {
  outputFileTracingRoot,
  /**
   * 开发环境使用独立的 distDir，避免与 next build 的产物混用导致静态资源/清单错配
   *（表现为 /_next/static/css 与 /_next/static/chunks 404，页面无样式/无交互）
   */
  distDir: isDev ? ".next-dev" : ".next",

  /* 严格模式 */
  reactStrictMode: true,

  /* ESLint配置 - 开发阶段允许警告不阻塞构建 */
  eslint: {
    ignoreDuringBuilds: false, // 保持检查
    dirs: ['src'], // 检查src目录
  },

  /* TypeScript配置 */
  typescript: {
    ignoreBuildErrors: false, // TypeScript错误必须修复
  },

  /* 实验性特性 */
  experimental: {
    /* 类型化路由（开发阶段暂时禁用，方便快速迭代） */
    // typedRoutes: true,
  },

  /* 图片配置 */
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "**",
      },
    ],
  },

  /* 开发环境代理配置 - 解决跨域问题 */
  async rewrites() {
    /**
     * 是否启用同源代理
     *
     * 说明：
     * - 仅当 NEXT_PUBLIC_API_BASE_URL 显式为空字符串时启用
     * - 避免在直连后端模式下触发 Next.js 内部 http-proxy 产生弃用告警
     */
    const enableDevProxy = process.env.NEXT_PUBLIC_API_BASE_URL === '';

    if (!enableDevProxy) {
      return [];
    }

    // 默认后端地址，可从环境变量读取 (BACKEND_API_URL 包含 /api 后缀)
    const backendUrl = process.env.BACKEND_API_URL || 'http://localhost:8080/api';
    
    return [
      {
        source: '/api/:path*',
        destination: `${backendUrl}/:path*`,
      },
    ];
  },
};

export default nextConfig;
