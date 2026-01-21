"use client";

import React, { useState, useEffect, useCallback } from 'react';
import { G3LogViewer } from '@/components/generation/G3LogViewer';
import { queryTemplates } from '@/lib/api/templates';
import type { Template } from '@/types/template';
import { Loader2 } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { getToken } from '@/lib/auth/token';

/**
 * 后端统一响应结构
 */
interface BackendResult<T> {
  code?: number;
  success?: boolean;
  message?: string;
  data?: T;
}

/**
 * Repo 索引结果
 */
interface RepoIndexResult {
  success: boolean;
  skipped: boolean;
  fileCount: number;
  skippedCount: number;
  chunkCount: number;
  message: string;
}

/**
 * Repo 语义检索命中
 */
interface RepoSearchHit {
  filePath: string;
  score: number;
  content: string;
}

/**
 * Toolset 搜索命中
 */
interface ToolSearchMatch {
  filePath: string;
  lineNumber: number;
  line: string;
}

/**
 * Toolset 搜索结果
 */
interface ToolSearchResult {
  success: boolean;
  message: string;
  matches?: ToolSearchMatch[];
}

/**
 * Toolset 文件读取结果
 */
interface ToolFileReadResult {
  success: boolean;
  content?: string;
  message: string;
}

/**
 * Toolset 命令执行结果
 */
interface ToolCommandResult {
  success: boolean;
  stdout?: string;
  stderr?: string;
  exitCode?: number;
  durationMs?: number;
  message: string;
  policyDecision?: string;
  policyReason?: string;
}

/**
 * 统一请求后端 Result<T> 并返回 data
 */
async function requestBackendResult<T>(url: string, options: RequestInit = {}): Promise<T> {
  const resp = await fetch(url, options);
  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`);
  }
  const body = await resp.json() as BackendResult<T>;
  if (body.code && body.code !== 200) {
    throw new Error(body.message || '后端响应失败');
  }
  if (body.success === false) {
    throw new Error(body.message || '后端响应失败');
  }
  if (typeof body.data === 'undefined') {
    throw new Error(body.message || '后端响应为空');
  }
  return body.data;
}

/**
 * Lab 页面 - G3 引擎测试控制台
 *
 * 功能：
 * - 输入需求描述
 * - 选择行业模板（可选，用于加载 Blueprint 约束）
 * - 启动 G3 任务并实时查看日志
 */
export default function LabPage() {
  const [requirement, setRequirement] = useState(
    '创建一个安全事故管理应用 (Safety Incident App)。\n' +
    '功能要求：\n' +
    '1. 事故上报：员工可以提交事故报告，包含时间、地点、描述、图片等信息。\n' +
    '2. 审核流程：安全专员审核上报的事故，进行定级和指派。\n' +
    '3. 处理追踪：被指派的负责人更新处理进度，直至事故关闭。\n' +
    '4. 统计看板：展示各类型事故的数量、处理状态分布。\n' +
    '技术要求：\n' +
    '- 使用 Spring Boot 和 MyBatis-Plus\n' +
    '- 生成完整的 Entity, Mapper, Service, Controller\n' +
    '- 包含必要的 DTO 和 API 接口'
  );
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | undefined>();
  const [templates, setTemplates] = useState<Template[]>([]);
  const [isLoadingTemplates, setIsLoadingTemplates] = useState(true);
  const [jobId, setJobId] = useState<string | null>(null);

  const [ragIndexResult, setRagIndexResult] = useState<RepoIndexResult | null>(null);
  const [ragIndexError, setRagIndexError] = useState<string | null>(null);
  const [ragIndexLoading, setRagIndexLoading] = useState(false);
  const [ragQuery, setRagQuery] = useState('G3OrchestratorService');
  const [ragResults, setRagResults] = useState<RepoSearchHit[]>([]);
  const [ragSearchLoading, setRagSearchLoading] = useState(false);
  const [ragSearchError, setRagSearchError] = useState<string | null>(null);

  const [toolQuery, setToolQuery] = useState('G3OrchestratorService');
  const [toolResults, setToolResults] = useState<ToolSearchMatch[]>([]);
  const [toolSearchLoading, setToolSearchLoading] = useState(false);
  const [toolSearchError, setToolSearchError] = useState<string | null>(null);

  const [filePath, setFilePath] = useState('');
  const [fileReadResult, setFileReadResult] = useState<ToolFileReadResult | null>(null);
  const [fileReadLoading, setFileReadLoading] = useState(false);

  const [shellCommand, setShellCommand] = useState('rg --version');
  const [shellResult, setShellResult] = useState<ToolCommandResult | null>(null);
  const [shellLoading, setShellLoading] = useState(false);

  const apiBaseUrl = getApiBaseUrl();

  /**
   * 构建后端请求头（携带 Token）
   */
  const buildHeaders = useCallback(() => {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    const token = getToken();
    if (token) {
      headers.Authorization = token;
    }
    return headers;
  }, []);

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

  const handleRepoIndex = useCallback(async () => {
    setRagIndexLoading(true);
    setRagIndexError(null);
    try {
      const result = await requestBackendResult<RepoIndexResult>(
        `${apiBaseUrl}/v1/g3/knowledge/repo/index`,
        {
          method: 'POST',
          headers: buildHeaders(),
          body: JSON.stringify({}),
        }
      );
      setRagIndexResult(result);
    } catch (error) {
      setRagIndexError(error instanceof Error ? error.message : '索引失败');
    } finally {
      setRagIndexLoading(false);
    }
  }, [apiBaseUrl, buildHeaders]);

  const handleRepoSearch = useCallback(async () => {
    const keyword = ragQuery.trim();
    if (!keyword) return;
    setRagSearchLoading(true);
    setRagSearchError(null);
    try {
      const result = await requestBackendResult<RepoSearchHit[]>(
        `${apiBaseUrl}/v1/g3/knowledge/repo/search?q=${encodeURIComponent(keyword)}&topK=5`,
        {
          method: 'GET',
          headers: buildHeaders(),
        }
      );
      setRagResults(result);
    } catch (error) {
      setRagSearchError(error instanceof Error ? error.message : '检索失败');
    } finally {
      setRagSearchLoading(false);
    }
  }, [apiBaseUrl, buildHeaders, ragQuery]);

  const handleToolSearch = useCallback(async () => {
    const keyword = toolQuery.trim();
    if (!keyword) return;
    setToolSearchLoading(true);
    setToolSearchError(null);
    try {
      const result = await requestBackendResult<ToolSearchResult>(
        `${apiBaseUrl}/v1/g3/tools/search?q=${encodeURIComponent(keyword)}&maxMatches=20`,
        {
          method: 'GET',
          headers: buildHeaders(),
        }
      );
      setToolResults(result.matches || []);
    } catch (error) {
      setToolSearchError(error instanceof Error ? error.message : '搜索失败');
    } finally {
      setToolSearchLoading(false);
    }
  }, [apiBaseUrl, buildHeaders, toolQuery]);

  const handleReadFile = useCallback(async () => {
    const target = filePath.trim();
    if (!target) {
      setFileReadResult({ success: false, message: '请输入文件路径' });
      return;
    }
    setFileReadLoading(true);
    setFileReadResult(null);
    try {
      const result = await requestBackendResult<ToolFileReadResult>(
        `${apiBaseUrl}/v1/g3/tools/read-file?path=${encodeURIComponent(target)}&maxLines=200`,
        {
          method: 'GET',
          headers: buildHeaders(),
        }
      );
      setFileReadResult(result);
    } catch (error) {
      setFileReadResult({
        success: false,
        message: error instanceof Error ? error.message : '读取失败',
      });
    } finally {
      setFileReadLoading(false);
    }
  }, [apiBaseUrl, buildHeaders, filePath]);

  const handleExecuteCommand = useCallback(async () => {
    const command = shellCommand.trim();
    if (!command) return;
    if (!jobId) {
      setShellResult({ success: false, message: '请先启动 G3 任务' });
      return;
    }
    setShellLoading(true);
    setShellResult(null);
    try {
      const result = await requestBackendResult<ToolCommandResult>(
        `${apiBaseUrl}/v1/g3/tools/execute`,
        {
          method: 'POST',
          headers: buildHeaders(),
          body: JSON.stringify({
            jobId,
            command,
            timeoutSeconds: 15,
          }),
        }
      );
      setShellResult(result);
    } catch (error) {
      setShellResult({
        success: false,
        message: error instanceof Error ? error.message : '执行失败',
      });
    } finally {
      setShellLoading(false);
    }
  }, [apiBaseUrl, buildHeaders, jobId, shellCommand]);

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
              <div className="text-xs font-mono text-slate-400 space-x-2">
                <span>{selectedTemplate ? `模板: ${selectedTemplate.name}` : '纯需求模式'}</span>
                <span title={jobId || ''}>
                  任务ID: {jobId ? `${jobId.slice(0, 8)}...` : '未启动'}
                </span>
              </div>
            </div>
            <G3LogViewer
              requirement={requirement}
              templateId={selectedTemplateId}
              disabled={!requirement || requirement.trim().length < 10}
              onJobIdChange={setJobId}
            />
          </section>

          {/* RAG + Toolset 操作区 */}
          <section className="grid gap-6 lg:grid-cols-2">
            <div className="p-6 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-200">RAG 知识库</h2>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={handleRepoIndex}
                  disabled={ragIndexLoading}
                >
                  {ragIndexLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : '索引仓库'}
                </Button>
              </div>
              {ragIndexResult && (
                <div className="text-xs text-slate-500">
                  {ragIndexResult.success
                    ? `索引完成：文件 ${ragIndexResult.fileCount}，切片 ${ragIndexResult.chunkCount}`
                    : ragIndexResult.skipped
                      ? `索引跳过：${ragIndexResult.message}`
                      : `索引失败：${ragIndexResult.message}`}
                </div>
              )}
              {ragIndexError && (
                <div className="text-xs text-red-500">索引失败：{ragIndexError}</div>
              )}

              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Input
                    value={ragQuery}
                    onChange={(e) => setRagQuery(e.target.value)}
                    placeholder="输入检索关键词..."
                  />
                  <Button size="sm" onClick={handleRepoSearch} disabled={ragSearchLoading}>
                    {ragSearchLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : '检索'}
                  </Button>
                </div>
                {ragSearchError && (
                  <div className="text-xs text-red-500">检索失败：{ragSearchError}</div>
                )}
                <ScrollArea className="h-56 rounded-md border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 p-3">
                  {ragResults.length === 0 ? (
                    <div className="text-xs text-slate-500">暂无检索结果</div>
                  ) : (
                    <div className="space-y-3">
                      {ragResults.map((hit, index) => (
                        <div key={`${hit.filePath}-${index}`} className="text-xs text-slate-600 dark:text-slate-300">
                          <div className="font-mono text-[11px] text-indigo-500">{hit.filePath}</div>
                          <div className="text-[10px] text-slate-400">相似度: {hit.score?.toFixed(3)}</div>
                          <pre className="mt-1 whitespace-pre-wrap text-[11px] leading-relaxed">
                            {hit.content}
                          </pre>
                        </div>
                      ))}
                    </div>
                  )}
                </ScrollArea>
              </div>
            </div>

            <div className="p-6 rounded-lg border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-200">Toolset / Shell</h2>
                <div className="text-xs text-slate-500">
                  {jobId ? `当前任务: ${jobId.slice(0, 8)}...` : '请先启动任务'}
                </div>
              </div>

              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Input
                    value={toolQuery}
                    onChange={(e) => setToolQuery(e.target.value)}
                    placeholder="搜索工作区内容..."
                  />
                  <Button size="sm" onClick={handleToolSearch} disabled={toolSearchLoading}>
                    {toolSearchLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : '搜索'}
                  </Button>
                </div>
                {toolSearchError && (
                  <div className="text-xs text-red-500">搜索失败：{toolSearchError}</div>
                )}
                <ScrollArea className="h-40 rounded-md border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 p-3">
                  {toolResults.length === 0 ? (
                    <div className="text-xs text-slate-500">暂无搜索结果</div>
                  ) : (
                    <div className="space-y-2">
                      {toolResults.map((match, index) => (
                        <div key={`${match.filePath}-${match.lineNumber}-${index}`} className="text-xs text-slate-600 dark:text-slate-300">
                          <div className="font-mono text-[11px] text-emerald-500">
                            {match.filePath}:{match.lineNumber}
                          </div>
                          <div className="text-[11px] whitespace-pre-wrap">{match.line}</div>
                        </div>
                      ))}
                    </div>
                  )}
                </ScrollArea>
              </div>

              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Input
                    value={filePath}
                    onChange={(e) => setFilePath(e.target.value)}
                    placeholder="读取文件路径，例如 backend/src/main/java/..."
                  />
                  <Button size="sm" onClick={handleReadFile} disabled={fileReadLoading}>
                    {fileReadLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : '读取'}
                  </Button>
                </div>
                <ScrollArea className="h-40 rounded-md border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 p-3">
                  {!fileReadResult ? (
                    <div className="text-xs text-slate-500">等待读取文件</div>
                  ) : fileReadResult.success ? (
                    <pre className="text-[11px] whitespace-pre-wrap leading-relaxed text-slate-700 dark:text-slate-200">
                      {fileReadResult.content}
                    </pre>
                  ) : (
                    <div className="text-xs text-red-500">读取失败：{fileReadResult.message}</div>
                  )}
                </ScrollArea>
              </div>

              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Input
                    value={shellCommand}
                    onChange={(e) => setShellCommand(e.target.value)}
                    placeholder='输入只读命令，例如 rg "G3" -n backend/src'
                  />
                  <Button size="sm" onClick={handleExecuteCommand} disabled={shellLoading || !jobId}>
                    {shellLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : '执行'}
                  </Button>
                </div>
                <div className="rounded-md border border-slate-200 dark:border-slate-800 bg-slate-50 dark:bg-slate-950 p-3 text-xs text-slate-600 dark:text-slate-300">
                  {!shellResult ? (
                    <div>等待执行命令</div>
                  ) : shellResult.success ? (
                    <div className="space-y-2">
                      <div>退出码: {shellResult.exitCode}，耗时: {shellResult.durationMs}ms</div>
                      {shellResult.stdout && (
                        <pre className="whitespace-pre-wrap text-[11px]">{shellResult.stdout}</pre>
                      )}
                      {shellResult.stderr && (
                        <pre className="whitespace-pre-wrap text-[11px] text-red-500">{shellResult.stderr}</pre>
                      )}
                    </div>
                  ) : (
                    <div className="text-red-500">执行失败：{shellResult.message}</div>
                  )}
                </div>
              </div>
            </div>
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
