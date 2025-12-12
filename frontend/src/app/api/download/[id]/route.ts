import { NextRequest, NextResponse } from 'next/server';

/**
 * GET /api/download/[id]
 * 下载AppSpec代码包
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`📦 Downloading code for: ${id}`);

  try {
    const appSpecId = id;

    // 模拟生成代码包内容
    const codePackage = {
      name: `appspec-${appSpecId}`,
      version: '1.0.0',
      description: 'AI生成的AppSpec应用代码包',
      files: {
        'README.md': `# AppSpec应用: ${appSpecId}

## 项目概述
这是基于秒构AI生成的应用代码包。

## 技术栈
- Next.js 15
- TypeScript
- Tailwind CSS
- shadcn/ui

## 快速开始
\`\`\`bash
npm install
npm run dev
\`\`\`

## 项目结构
\`\`\`
src/
├── app/              # Next.js App Router
├── components/       # React组件
├── lib/             # 工具函数
└── types/           # TypeScript类型定义
\`\`\`

## 部署
1. 构建项目: \`npm run build\`
2. 启动生产环境: \`npm start\`

---
生成时间: ${new Date().toISOString()}
生成工具: 秒构AI
`,
        'package.json': JSON.stringify({
          name: `appspec-${appSpecId}`,
          version: "1.0.0",
          description: "AI生成的AppSpec应用",
          scripts: {
            dev: "next dev",
            build: "next build",
            start: "next start",
            lint: "next lint"
          },
          dependencies: {
            next: "^15.0.0",
            react: "^18.0.0",
            "react-dom": "^18.0.0",
            typescript: "^5.0.0",
            "@types/node": "^20.0.0",
            "@types/react": "^18.0.0",
            "@types/react-dom": "^18.0.0",
            tailwindcss: "^3.0.0",
            "lucide-react": "^0.300.0"
          },
          devDependencies: {
            eslint: "^8.0.0",
            "eslint-config-next": "^15.0.0"
          }
        }, null, 2),
        'src/app/page.tsx': `export default function HomePage() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100">
      <div className="container mx-auto px-4 py-16">
        <h1 className="text-4xl font-bold text-center text-gray-900">
          欢迎使用AI生成的应用
        </h1>
        <p className="text-center text-gray-600 mt-4">
          这是基于AppSpec ${appSpecId} 生成的应用
        </p>
      </div>
    </div>
  );
}`,
        'src/app/layout.tsx': `import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'AppSpec应用',
  description: 'AI生成的应用',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN">
      <body className={inter.className}>{children}</body>
    </html>
  );
}`,
        'src/app/globals.css': `@tailwind base;
@tailwind components;
@tailwind utilities;

:root {
  --foreground-rgb: 0, 0, 0;
  --background-start-rgb: 214, 219, 220;
  --background-end-rgb: 255, 255, 255;
}

@media (prefers-color-scheme: dark) {
  :root {
    --foreground-rgb: 255, 255, 255;
    --background-start-rgb: 0, 0, 0;
    --background-end-rgb: 0, 0, 0;
  }
}

body {
  color: rgb(var(--foreground-rgb));
  background: linear-gradient(
      to bottom,
      transparent,
      rgb(var(--background-end-rgb))
    )
    rgb(var(--background-start-rgb));
}`,
        'tailwind.config.js': `/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {},
  },
  plugins: [],
}`,
        'next.config.js': `/** @type {import('next').NextConfig} */
const nextConfig = {};

module.exports = nextConfig;`,
        'tsconfig.json': `{
  "compilerOptions": {
    "target": "es5",
    "lib": ["dom", "dom.iterable", "es6"],
    "allowJs": true,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true,
    "plugins": [
      {
        "name": "next"
      }
    ],
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules"]
}`
      }
    };

    // 生成ZIP文件（简化版本 - 实际应用中应使用JSZip等库）
    const zipContent = Buffer.from(JSON.stringify(codePackage, null, 2));

    // 设置响应头
    const headers = new Headers({
      'Content-Type': 'application/zip',
      'Content-Disposition': `attachment; filename="appspec-${appSpecId}-code.zip"`,
      'Cache-Control': 'no-cache',
      'Content-Length': zipContent.length.toString(),
    });

    console.log(`✅ Code package generated for: ${appSpecId}`);

    return new NextResponse(zipContent, {
      status: 200,
      headers,
    });

  } catch (error) {
    console.error(`❌ Error generating code package:`, error);

    return NextResponse.json({
      success: false,
      error: error instanceof Error ? error.message : '生成代码包失败',
    }, { status: 500 });
  }
}

/**
 * HEAD /api/download/[id]
 * 检查代码包是否可用
 */
export async function HEAD(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`🔍 Checking code package availability: ${id}`);

  try {
    const appSpecId = id;

    // 这里可以检查AppSpec是否存在
    // 简化实现：总是返回可用

    return new NextResponse(null, {
      status: 200,
      headers: {
        'Content-Type': 'application/zip',
        'Content-Disposition': `attachment; filename="appspec-${appSpecId}-code.zip"`,
      },
    });

  } catch (error) {
    console.error(`❌ Error checking code package:`, error);

    return new NextResponse(null, {
      status: 404,
    });
  }
}