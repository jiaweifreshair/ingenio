import type { NextConfig } from "next";

/**
 * Next.js配置
 * 秒构AI前端配置
 */
const nextConfig: NextConfig = {
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
};

export default nextConfig;
