import type { Metadata } from "next";
import { TopNav } from "@/components/layout/top-nav";
import { RequirementForm } from "@/components/create/requirement-form";

/**
 * 创建应用页面元数据
 */
export const metadata: Metadata = {
  title: "创建应用 - 秒构AI",
  description: "用AI快速生成你的应用",
};

/**
 * CreatePage组件 - 创建新应用
 *
 * 核心功能：
 * - 显示"创建新应用"标题（E2E测试期望）
 * - 提供需求输入表单（placeholder: "描述你想要的应用..."）
 * - 快速模板选项
 * - 表单验证和提交
 *
 * 设计理念：
 * - 聚焦核心输入，移除冗余信息
 * - 与首页Hero区域保持一致的设计语言
 * - 支持E2E测试元素定位
 */
export default function CreatePage(): React.ReactElement {
  return (
    <div className="flex min-h-screen flex-col">
      {/* 顶部导航 */}
      <TopNav />

      {/* 主要内容 - 居中垂直对齐 */}
      <main className="container flex flex-1 items-center justify-center px-4 py-16">
        <div className="w-full max-w-4xl space-y-12">
          {/* 页面标题 - 修改为E2E测试期望的文本 */}
          <div className="space-y-3 text-center">
            <h1 className="text-4xl font-bold tracking-tight md:text-5xl lg:text-6xl">
              <span className="bg-gradient-to-r from-purple-600 via-pink-600 to-blue-600 bg-clip-text text-transparent">
                创建新应用
              </span>
            </h1>
            <p className="text-lg text-muted-foreground md:text-xl">
              AI将在5分钟内为你生成完整应用
            </p>
          </div>

          {/* 需求输入表单 */}
          <RequirementForm />
        </div>
      </main>
    </div>
  );
}
