"use client";

import React, { useState, useEffect } from 'react';
import { G3LogViewer } from '@/components/generation/G3LogViewer';
import { queryTemplates } from '@/lib/api/templates';
import type { Template } from '@/types/template';
import { Loader2 } from 'lucide-react';

/**
 * Lab 页面 - G3 引擎测试控制台
 *
 * 功能：
 * - 输入需求描述
 * - 选择行业模板（可选，用于加载 Blueprint 约束）
 * - 启动 G3 任务并实时查看日志
 */
export default function LabPage() {
  const [requirement, setRequirement] = useState('');
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | undefined>();
  const [templates, setTemplates] = useState<Template[]>([]);
  const [isLoadingTemplates, setIsLoadingTemplates] = useState(true);

  // 加载模板列表
  useEffect(() => {
    async function loadTemplates() {
      try {
        const response = await queryTemplates({ pageSize: 20 });
        setTemplates(response.items);
      } catch (error) {
        console.error('[Lab] 加载模板失败:', error);
      } finally {
        setIsLoadingTemplates(false);
      }
    }
    loadTemplates();
  }, []);

  // 找到选中的模板
  const selectedTemplate = templates.find(t => t.id === selectedTemplateId);

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      <div className="container mx-auto py-10 space-y-8 max-w-6xl">
        {/* Header */}
        <div className="flex flex-col space-y-2">
          <div className="flex items-center gap-3">
            <div className="h-8 w-8 rounded-lg bg-indigo-600 flex items-center justify-center">
              <span className="text-white font-bold font-mono">G3</span>
            </div>
            <h1 className="text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              Ingenio Software Factory
            </h1>
          </div>
          <p className="text-slate-500 dark:text-slate-400 max-w-2xl">
            输入需求描述并选择行业模板（可选），观察 <strong>G3 引擎</strong> 自动生成代码。
            模板提供 <span className="font-semibold text-indigo-600 dark:text-indigo-400">Blueprint 约束</span>，
            确保生成的代码符合预定义的数据结构和 API 规范。
          </p>
        </div>

        <div className="grid gap-6">
          {/* 需求输入区域 */}
          <section className="p-6 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
            <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-200 mb-4">需求描述</h2>
            <textarea
              value={requirement}
              onChange={(e) => setRequirement(e.target.value)}
              placeholder="请输入您的需求，例如：创建一个博客系统，支持文章发布、标签管理和评论功能..."
              className="w-full h-32 p-4 rounded-lg border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 text-slate-900 dark:text-slate-100 resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
            <div className="mt-2 text-xs text-slate-500">
              提示：需求描述越详细，生成的代码质量越高。至少需要 10 个字符。
            </div>
          </section>

          {/* 模板选择区域 */}
          <section className="p-6 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-200">
                  行业模板 <span className="text-slate-400 text-sm font-normal">(可选)</span>
                </h2>
                <p className="text-xs text-slate-500 mt-1">
                  选择模板后，G3 引擎会加载该模板的 Blueprint 约束，确保生成代码符合规范
                </p>
              </div>
              {selectedTemplate && (
                <button
                  onClick={() => setSelectedTemplateId(undefined)}
                  className="text-xs text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
                >
                  清除选择
                </button>
              )}
            </div>

            {isLoadingTemplates ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-6 h-6 animate-spin text-slate-400" />
                <span className="ml-2 text-slate-500">加载模板中...</span>
              </div>
            ) : templates.length === 0 ? (
              <div className="text-center py-8 text-slate-500">
                暂无可用模板，将使用纯需求模式生成代码
              </div>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
                {templates.map((template) => (
                  <button
                    key={template.id}
                    onClick={() => setSelectedTemplateId(
                      selectedTemplateId === template.id ? undefined : template.id
                    )}
                    className={`
                      p-3 rounded-lg border text-left transition-all
                      ${selectedTemplateId === template.id
                        ? 'border-indigo-500 bg-indigo-50 dark:bg-indigo-900/20 ring-2 ring-indigo-500'
                        : 'border-slate-200 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600 bg-white dark:bg-slate-800'
                      }
                    `}
                  >
                    <div className="font-medium text-sm text-slate-800 dark:text-slate-200 truncate">
                      {template.name}
                    </div>
                    <div className="text-xs text-slate-500 mt-1 line-clamp-2">
                      {template.description}
                    </div>
                    <div className="flex items-center gap-2 mt-2">
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-400">
                        {template.category}
                      </span>
                      {template.rating > 0 && (
                        <span className="text-[10px] text-yellow-600">
                          ★ {template.rating.toFixed(1)}
                        </span>
                      )}
                    </div>
                  </button>
                ))}
              </div>
            )}

            {/* 选中模板的详细信息 */}
            {selectedTemplate && (
              <div className="mt-4 p-4 rounded-lg bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-800">
                <div className="flex items-start gap-3">
                  <div className="flex-1">
                    <div className="font-medium text-indigo-800 dark:text-indigo-200">
                      已选择：{selectedTemplate.name}
                    </div>
                    <div className="text-sm text-indigo-600 dark:text-indigo-400 mt-1">
                      {selectedTemplate.description}
                    </div>
                    {selectedTemplate.features && selectedTemplate.features.length > 0 && (
                      <div className="flex flex-wrap gap-1 mt-2">
                        {selectedTemplate.features.slice(0, 5).map((feature, i) => (
                          <span
                            key={i}
                            className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-100 dark:bg-indigo-800 text-indigo-700 dark:text-indigo-300"
                          >
                            {feature}
                          </span>
                        ))}
                        {selectedTemplate.features.length > 5 && (
                          <span className="text-[10px] text-indigo-500">
                            +{selectedTemplate.features.length - 5} 更多
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            )}
          </section>

          {/* G3 日志监控区域 */}
          <section>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-200">
                G3 执行日志
              </h2>
              <div className="text-xs font-mono text-slate-400">
                {selectedTemplate ? `模板: ${selectedTemplate.name}` : '纯需求模式'}
              </div>
            </div>
            <G3LogViewer
              requirement={requirement}
              templateId={selectedTemplateId}
              disabled={!requirement || requirement.trim().length < 10}
            />
          </section>

          {/* Agent 角色说明 */}
          <section className="grid md:grid-cols-3 gap-6">
            <div className="p-4 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
              <h3 className="font-semibold text-sm mb-2 text-blue-600">蓝方 (Player)</h3>
              <p className="text-xs text-slate-500">
                负责高速代码生成和创意解决方案。根据 Blueprint 约束生成符合规范的代码。
              </p>
            </div>
            <div className="p-4 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
              <h3 className="font-semibold text-sm mb-2 text-red-600">红方 (Coach)</h3>
              <p className="text-xs text-slate-500">
                负责对抗性测试、安全审计和边界情况检测。发现问题后指导 Player 修复。
              </p>
            </div>
            <div className="p-4 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900">
              <h3 className="font-semibold text-sm mb-2 text-green-600">执行方 (Judge)</h3>
              <p className="text-xs text-slate-500">
                确定性运行时环境。验证语法、运行单元测试，并进行 Blueprint 合规性检查。
              </p>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
