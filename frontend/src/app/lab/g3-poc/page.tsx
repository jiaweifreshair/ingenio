/**
 * G3 引擎 PoC 演示页面
 *
 * 展示前端独立的 G3 Engine MVP：
 * - Player Agent（蓝方）生成代码
 * - Executor（裁判）验证语法
 * - Coach Agent（红方）修复错误
 * - SSE 流式日志输出
 *
 * @module app/lab/g3-poc
 * @author Ingenio Team
 * @since Phase 1 - G3 Engine MVP
 */
"use client";

import { useState } from "react";
import { G3LogViewerAutonomous } from "@/components/generation/G3LogViewer";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Zap, Shield, Swords } from "lucide-react";
import Link from "next/link";

export default function G3PocPage() {
  const [demoKey, setDemoKey] = useState(0);

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900">
      {/* Header */}
      <header className="border-b border-slate-700/50 bg-slate-900/50 backdrop-blur-sm">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Link href="/">
              <Button variant="ghost" size="sm" className="text-slate-400 hover:text-white">
                <ArrowLeft className="w-4 h-4 mr-2" />
                返回首页
              </Button>
            </Link>
            <div className="h-6 w-px bg-slate-700" />
            <h1 className="text-xl font-bold text-white flex items-center gap-2">
              <Swords className="w-5 h-5 text-orange-500" />
              G3 Engine PoC
            </h1>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setDemoKey(prev => prev + 1)}
            className="border-slate-600 text-slate-300 hover:bg-slate-800"
          >
            重置演示
          </Button>
        </div>
      </header>

      {/* Main Content */}
      <main className="container mx-auto px-4 py-8">
        {/* Intro Section */}
        <div className="mb-8 text-center">
          <h2 className="text-2xl font-bold text-white mb-4">
            红蓝博弈代码生成引擎
          </h2>
          <p className="text-slate-400 max-w-2xl mx-auto">
            G3 = Game (博弈) + Generator (生成) + Guard (守护)。
            通过 Player（蓝方）和 Coach（红方）的对抗博弈，
            实现「生成 → 检查 → 修复」的自动化闭环。
          </p>
        </div>

        {/* Role Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
          <div className="bg-blue-500/10 border border-blue-500/30 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-3 h-3 rounded-full bg-blue-500" />
              <span className="font-bold text-blue-400">PLAYER (蓝方)</span>
            </div>
            <p className="text-sm text-slate-400">
              根据需求生成初始代码。在 PoC 阶段，故意生成带语法错误的代码以验证闭环。
            </p>
          </div>

          <div className="bg-green-500/10 border border-green-500/30 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Zap className="w-4 h-4 text-green-400" />
              <span className="font-bold text-green-400">EXECUTOR (裁判)</span>
            </div>
            <p className="text-sm text-slate-400">
              使用 TypeScript 编译器 API 在内存中检查代码语法，无需 Docker 或外部服务。
            </p>
          </div>

          <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Shield className="w-4 h-4 text-red-400" />
              <span className="font-bold text-red-400">COACH (红方)</span>
            </div>
            <p className="text-sm text-slate-400">
              分析错误并修复代码。在 PoC 阶段使用模式匹配修复，真实场景调用 LLM。
            </p>
          </div>
        </div>

        {/* G3 Log Viewer */}
        <div className="max-w-5xl mx-auto">
          <G3LogViewerAutonomous
            key={demoKey}
            defaultRequirement="创建一个计算斐波那契数列的 TypeScript 函数"
            autoStart={false}
          />
        </div>

        {/* Technical Notes */}
        <div className="mt-8 max-w-3xl mx-auto text-center">
          <h3 className="text-lg font-semibold text-white mb-3">技术实现</h3>
          <div className="flex flex-wrap justify-center gap-2">
            <span className="px-3 py-1 bg-slate-800 rounded-full text-xs text-slate-400">
              TypeScript Compiler API
            </span>
            <span className="px-3 py-1 bg-slate-800 rounded-full text-xs text-slate-400">
              Server-Sent Events (SSE)
            </span>
            <span className="px-3 py-1 bg-slate-800 rounded-full text-xs text-slate-400">
              AsyncGenerator
            </span>
            <span className="px-3 py-1 bg-slate-800 rounded-full text-xs text-slate-400">
              Next.js App Router
            </span>
            <span className="px-3 py-1 bg-slate-800 rounded-full text-xs text-slate-400">
              React 19
            </span>
          </div>
          <p className="mt-4 text-sm text-slate-500">
            Phase 1 MVP - 纯前端实现，验证闭环流程。Phase 2 将集成真实 LLM。
          </p>
        </div>
      </main>
    </div>
  );
}
