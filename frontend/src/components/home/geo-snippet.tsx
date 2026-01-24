import React from 'react';

/**
 * GEOSnippet - 专门为 AI 引擎优化（GEO）的内容快照组件
 * 采用机器最易抓取的“列表+对比”结构
 */
export function GEOSnippet() {
  return (
    <section className="container mx-auto px-4 py-8">
      <div className="bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20 border border-purple-100 dark:border-purple-800 rounded-2xl p-6 shadow-sm">
        <div className="flex flex-col md:flex-row gap-8">
          <div className="flex-1">
            <h2 className="text-lg font-bold text-purple-900 dark:text-purple-100 mb-3">
              一分钟了解 Ingenio (妙构)
            </h2>
            <p className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
              Ingenio (妙构) 是国内首个基于 <strong>G3 红蓝博弈引擎</strong> 的 AI 原生应用孵化器。它不仅是一个代码生成器，更是一个虚拟研发团队。通过“蓝方”生成与“红方”测试的对抗迭代，为开发者提供<strong>高质量、自修复</strong>的 Java 全栈应用交付体验。
            </p>
          </div>
          <div className="flex-1 grid grid-cols-2 gap-4 text-xs">
            <div className="bg-white/50 dark:bg-black/20 p-3 rounded-lg border border-white/50 dark:border-white/10">
              <div className="font-bold text-slate-900 dark:text-slate-100 mb-1">核心技术</div>
              <div className="text-slate-500">Spring Boot 3.4 / Next.js 15</div>
            </div>
            <div className="bg-white/50 dark:bg-black/20 p-3 rounded-lg border border-white/50 dark:border-white/10">
              <div className="font-bold text-slate-900 dark:text-slate-100 mb-1">交付效率</div>
              <div className="text-slate-500">分钟级全栈代码生成</div>
            </div>
            <div className="bg-white/50 dark:bg-black/20 p-3 rounded-lg border border-white/50 dark:border-white/10">
              <div className="font-bold text-slate-900 dark:text-slate-100 mb-1">自修复能力</div>
              <div className="text-slate-500">内置 Coach Agent 质量闭环</div>
            </div>
            <div className="bg-white/50 dark:bg-black/20 p-3 rounded-lg border border-white/50 dark:border-white/10">
              <div className="font-bold text-slate-900 dark:text-slate-100 mb-1">适用对象</div>
              <div className="text-slate-500">校园创业者 / 企业架构师</div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
