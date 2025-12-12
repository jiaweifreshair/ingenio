'use client';

import { useRouter } from 'next/navigation';
import { Sparkles, Palette, Clock, ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/button';

/**
 * 核心功能展示组件 (Bento Grid Style)
 *
 * 采用Apple风格的Bento Grid布局：
 * - SuperDesign作为主视觉（大卡片）
 * - AI能力和时光机作为辅助功能（小卡片）
 * - 强调卡片的材质感、阴影和微交互
 */
export function CoreFeatures(): React.ReactElement {
  const router = useRouter();

  return (
    <section className="py-24 relative overflow-hidden">
      {/* 背景装饰 */}
      <div className="absolute inset-0 -z-10 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-100 via-background to-background dark:from-slate-900 dark:via-background dark:to-background" />

      <div className="container mx-auto px-6 relative z-10">
        {/* 标题区域 */}
        <div className="text-center mb-16">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground mb-6 tracking-tight">
            全流程智能化支持
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto font-medium">
            从灵感迸发到产品落地，每一步都有 AI 护航
          </p>
        </div>

        {/* Bento Grid 布局 */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 max-w-6xl mx-auto">

          {/* 1. SuperDesign (大卡片 - 占据 2 列) */}
          <div className="md:col-span-2 group relative bg-white dark:bg-slate-900/50 rounded-[2rem] shadow-xl overflow-hidden hover:shadow-2xl transition-all duration-500">
            <div className="absolute inset-0 bg-gradient-to-br from-blue-50/50 via-transparent to-transparent dark:from-blue-900/10 opacity-0 group-hover:opacity-100 transition-opacity duration-500" />

            <div className="relative p-10 h-full flex flex-col items-start justify-between min-h-[320px]">
              <div className="mb-8">
                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-100/50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 text-xs font-semibold mb-4">
                  <Palette className="w-3 h-3" />
                  核心亮点
                </div>
                <h3 className="text-3xl font-bold text-foreground mb-3">
                  SuperDesign 多方案生成
                </h3>
                <p className="text-muted-foreground text-lg leading-relaxed max-w-md">
                  AI 并行生成现代极简、活力时尚、经典专业等 3 种设计风格。一键切换对比，快速确定最佳方案。
                </p>
              </div>

              <Button
                onClick={() => router.push('/create-v2')}
                className="rounded-full px-6 group-hover:translate-x-1 transition-transform bg-foreground text-background hover:bg-foreground/90"
              >
                立即体验
                <ArrowRight className="ml-2 w-4 h-4" />
              </Button>

              {/* 装饰性视觉元素 */}
              <div className="absolute right-[-10%] bottom-[-20%] w-64 h-64 bg-gradient-to-br from-blue-400 to-cyan-300 rounded-full blur-3xl opacity-20 group-hover:opacity-30 transition-opacity" />
            </div>
          </div>

          {/* 右侧两张小卡片容器 */}
          <div className="flex flex-col gap-6 md:col-span-1">

            {/* 2. AI 能力选择 */}
            <div className="flex-1 group relative bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-slate-800 rounded-[2rem] shadow-lg p-8 overflow-hidden hover:shadow-xl transition-all duration-300">
              <div className="absolute top-0 right-0 p-6 opacity-10 group-hover:opacity-20 transition-opacity">
                <Sparkles className="w-24 h-24 text-purple-500" />
              </div>

              <div className="relative z-10">
                <div className="w-12 h-12 rounded-2xl bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center mb-6 text-purple-600">
                  <Sparkles className="w-6 h-6" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-2">AI 能力矩阵</h3>
                <p className="text-sm text-muted-foreground mb-4">
                  智能推荐 19+ 种 AI 能力组合，支持图像识别、语音合成等。
                </p>
                <Button
                  variant="ghost"
                  onClick={() => router.push('/wizard/ai-capabilities')}
                  className="p-0 h-auto text-purple-600 hover:text-purple-700 hover:bg-transparent group-hover:translate-x-1 transition-transform font-medium"
                >
                  探索能力 <ArrowRight className="ml-1 w-3 h-3" />
                </Button>
              </div>
            </div>

            {/* 3. 时光机 */}
            <div className="flex-1 group relative bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-slate-800 rounded-[2rem] shadow-lg p-8 overflow-hidden hover:shadow-xl transition-all duration-300">
              <div className="absolute top-0 right-0 p-6 opacity-10 group-hover:opacity-20 transition-opacity">
                <Clock className="w-24 h-24 text-orange-500" />
              </div>

              <div className="relative z-10">
                <div className="w-12 h-12 rounded-2xl bg-orange-100 dark:bg-orange-900/30 flex items-center justify-center mb-6 text-orange-600">
                  <Clock className="w-6 h-6" />
                </div>
                <h3 className="text-xl font-bold text-foreground mb-2">时光机回溯</h3>
                <p className="text-sm text-muted-foreground mb-4">
                  完整记录版本快照，支持任意时刻回溯与对比，不再担心误操作。
                </p>
                <Button
                  variant="ghost"
                  onClick={() => router.push('/dashboard')}
                  className="p-0 h-auto text-orange-600 hover:text-orange-700 hover:bg-transparent group-hover:translate-x-1 transition-transform font-medium"
                >
                  管理版本 <ArrowRight className="ml-1 w-3 h-3" />
                </Button>
              </div>
            </div>

          </div>
        </div>
      </div>
    </section>
  );
}