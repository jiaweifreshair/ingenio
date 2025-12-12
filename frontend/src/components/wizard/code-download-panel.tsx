/**
 * 代码下载面板组件
 * 显示生成的代码下载链接、文件统计信息和文件列表
 *
 * 功能：
 * - 显示代码ZIP包下载按钮
 * - 展示文件统计信息（总文件数、AI集成文件数等）
 * - 可展开查看完整文件列表
 * - 高亮显示AI集成相关文件
 */

'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Download,
  FileCode,
  Database,
  Package,
  Layers,
  Smartphone,
  Bot,
  Settings,
  FileText,
  ChevronDown,
  ChevronUp,
  Sparkles
} from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * 代码生成摘要接口（匹配后端GenerateFullResponse.CodeGenerationSummary）
 */
export interface CodeGenerationSummary {
  /** 总文件数 */
  totalFiles: number;
  /** 数据库Schema文件数 */
  databaseSchemaFiles: number;
  /** 数据模型文件数 */
  dataModelFiles: number;
  /** Repository文件数 */
  repositoryFiles: number;
  /** ViewModel文件数 */
  viewModelFiles: number;
  /** UI界面文件数 */
  uiScreenFiles: number;
  /** AI集成文件数 */
  aiIntegrationFiles: number;
  /** 配置文件数 */
  configFiles: number;
  /** 文档文件数 */
  documentFiles: number;
  /** 总文件大小（字节） */
  totalSize: number;
  /** ZIP文件名 */
  zipFileName: string;
}

/**
 * 组件Props
 */
export interface CodeDownloadPanelProps {
  /** 代码下载URL（指向MinIO的ZIP文件） */
  codeDownloadUrl?: string;
  /** 代码生成摘要 */
  codeSummary?: CodeGenerationSummary;
  /** 生成的文件列表 */
  generatedFileList?: string[];
  /** 自定义类名 */
  className?: string;
}

/**
 * 格式化文件大小
 * @param bytes 字节数
 * @returns 格式化后的字符串（如 "2.5 MB"）
 */
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
}

/**
 * 判断文件是否为AI相关文件
 * @param filePath 文件路径
 * @returns 是否为AI文件
 */
function isAIFile(filePath: string): boolean {
  const lowerPath = filePath.toLowerCase();
  return (
    lowerPath.includes('/ai/') ||
    lowerPath.includes('aiservice') ||
    lowerPath.includes('aiconfig') ||
    lowerPath.includes('aiclient') ||
    lowerPath.includes('chatbot') ||
    lowerPath.includes('rag')
  );
}

/**
 * 获取文件类型图标
 * @param filePath 文件路径
 * @returns 图标组件
 */
function getFileIcon(filePath: string) {
  const lowerPath = filePath.toLowerCase();

  if (isAIFile(filePath)) {
    return <Bot className="w-4 h-4 text-purple-500" />;
  }
  if (lowerPath.includes('/data/model/')) {
    return <Database className="w-4 h-4 text-blue-500" />;
  }
  if (lowerPath.includes('/data/repository/')) {
    return <Package className="w-4 h-4 text-green-500" />;
  }
  if (lowerPath.includes('/viewmodel/')) {
    return <Layers className="w-4 h-4 text-orange-500" />;
  }
  if (lowerPath.includes('/screen/') || lowerPath.includes('/ui/')) {
    return <Smartphone className="w-4 h-4 text-pink-500" />;
  }
  if (lowerPath.endsWith('.gradle') || lowerPath.endsWith('.properties')) {
    return <Settings className="w-4 h-4 text-gray-500" />;
  }
  if (lowerPath.endsWith('.md') || lowerPath.endsWith('.txt')) {
    return <FileText className="w-4 h-4 text-yellow-500" />;
  }
  return <FileCode className="w-4 h-4 text-gray-400" />;
}

/**
 * 代码下载面板组件
 */
export function CodeDownloadPanel({
  codeDownloadUrl,
  codeSummary,
  generatedFileList,
  className
}: CodeDownloadPanelProps) {
  const [isFileListExpanded, setIsFileListExpanded] = useState(false);

  // 如果没有代码下载信息，不显示面板
  if (!codeDownloadUrl && !codeSummary) {
    return null;
  }

  const hasAIIntegration = codeSummary && codeSummary.aiIntegrationFiles > 0;

  return (
    <Card className={cn('border-primary/20', className)}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Download className="w-5 h-5 text-primary" />
          代码下载
          {hasAIIntegration && (
            <Badge variant="secondary" className="ml-2 bg-purple-100 text-purple-700">
              <Sparkles className="w-3 h-3 mr-1" />
              包含AI集成
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* 下载按钮区域 */}
        {codeDownloadUrl && (
          <div className="flex items-center gap-4">
            <Button
              size="lg"
              onClick={() => window.open(codeDownloadUrl, '_blank')}
              className="flex-1"
            >
              <Download className="w-4 h-4 mr-2" />
              下载完整代码包
            </Button>
            {codeSummary && (
              <div className="text-sm text-muted-foreground">
                {codeSummary.zipFileName}
                <br />
                {formatFileSize(codeSummary.totalSize)}
              </div>
            )}
          </div>
        )}

        {/* 文件统计信息 */}
        {codeSummary && (
          <>
            <Separator />
            <div>
              <h4 className="text-sm font-semibold mb-3">文件统计</h4>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                {/* 总文件数 */}
                <div className="flex items-center gap-2 p-3 bg-muted rounded-lg">
                  <FileCode className="w-4 h-4 text-primary" />
                  <div>
                    <div className="text-lg font-bold">{codeSummary.totalFiles}</div>
                    <div className="text-xs text-muted-foreground">总文件</div>
                  </div>
                </div>

                {/* 数据模型文件 */}
                {codeSummary.dataModelFiles > 0 && (
                  <div className="flex items-center gap-2 p-3 bg-muted rounded-lg">
                    <Database className="w-4 h-4 text-blue-500" />
                    <div>
                      <div className="text-lg font-bold">{codeSummary.dataModelFiles}</div>
                      <div className="text-xs text-muted-foreground">数据模型</div>
                    </div>
                  </div>
                )}

                {/* Repository文件 */}
                {codeSummary.repositoryFiles > 0 && (
                  <div className="flex items-center gap-2 p-3 bg-muted rounded-lg">
                    <Package className="w-4 h-4 text-green-500" />
                    <div>
                      <div className="text-lg font-bold">{codeSummary.repositoryFiles}</div>
                      <div className="text-xs text-muted-foreground">Repository</div>
                    </div>
                  </div>
                )}

                {/* ViewModel文件 */}
                {codeSummary.viewModelFiles > 0 && (
                  <div className="flex items-center gap-2 p-3 bg-muted rounded-lg">
                    <Layers className="w-4 h-4 text-orange-500" />
                    <div>
                      <div className="text-lg font-bold">{codeSummary.viewModelFiles}</div>
                      <div className="text-xs text-muted-foreground">ViewModel</div>
                    </div>
                  </div>
                )}

                {/* UI界面文件 */}
                {codeSummary.uiScreenFiles > 0 && (
                  <div className="flex items-center gap-2 p-3 bg-muted rounded-lg">
                    <Smartphone className="w-4 h-4 text-pink-500" />
                    <div>
                      <div className="text-lg font-bold">{codeSummary.uiScreenFiles}</div>
                      <div className="text-xs text-muted-foreground">UI界面</div>
                    </div>
                  </div>
                )}

                {/* AI集成文件 - 重点显示 */}
                {codeSummary.aiIntegrationFiles > 0 && (
                  <div className="flex items-center gap-2 p-3 bg-purple-50 border border-purple-200 rounded-lg">
                    <Bot className="w-4 h-4 text-purple-600" />
                    <div>
                      <div className="text-lg font-bold text-purple-700">
                        {codeSummary.aiIntegrationFiles}
                      </div>
                      <div className="text-xs text-purple-600">AI集成</div>
                    </div>
                  </div>
                )}

                {/* 配置文件 */}
                {codeSummary.configFiles > 0 && (
                  <div className="flex items-center gap-2 p-3 bg-muted rounded-lg">
                    <Settings className="w-4 h-4 text-gray-500" />
                    <div>
                      <div className="text-lg font-bold">{codeSummary.configFiles}</div>
                      <div className="text-xs text-muted-foreground">配置文件</div>
                    </div>
                  </div>
                )}

                {/* 文档文件 */}
                {codeSummary.documentFiles > 0 && (
                  <div className="flex items-center gap-2 p-3 bg-muted rounded-lg">
                    <FileText className="w-4 h-4 text-yellow-500" />
                    <div>
                      <div className="text-lg font-bold">{codeSummary.documentFiles}</div>
                      <div className="text-xs text-muted-foreground">文档</div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </>
        )}

        {/* 文件列表（可展开） */}
        {generatedFileList && generatedFileList.length > 0 && (
          <>
            <Separator />
            <div>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setIsFileListExpanded(!isFileListExpanded)}
                className="w-full justify-between"
              >
                <span className="text-sm font-semibold">
                  文件清单 ({generatedFileList.length} 个文件)
                </span>
                {isFileListExpanded ? (
                  <ChevronUp className="w-4 h-4" />
                ) : (
                  <ChevronDown className="w-4 h-4" />
                )}
              </Button>

              {isFileListExpanded && (
                <ScrollArea className="h-64 mt-3 rounded-md border p-3">
                  <div className="space-y-1">
                    {generatedFileList.map((file, index) => {
                      const isAI = isAIFile(file);
                      return (
                        <div
                          key={index}
                          className={cn(
                            'flex items-center gap-2 px-2 py-1.5 rounded text-xs font-mono',
                            isAI
                              ? 'bg-purple-50 text-purple-700 border border-purple-200'
                              : 'hover:bg-muted'
                          )}
                        >
                          {getFileIcon(file)}
                          <span className="flex-1 truncate">{file}</span>
                          {isAI && (
                            <Badge
                              variant="secondary"
                              className="ml-auto text-[10px] bg-purple-100 text-purple-700"
                            >
                              AI
                            </Badge>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </ScrollArea>
              )}
            </div>
          </>
        )}

        {/* AI集成提示 */}
        {hasAIIntegration && (
          <div className="flex items-start gap-3 p-4 bg-purple-50 border border-purple-200 rounded-lg">
            <Sparkles className="w-5 h-5 text-purple-600 mt-0.5" />
            <div className="flex-1">
              <h4 className="text-sm font-semibold text-purple-900 mb-1">
                AI功能已集成
              </h4>
              <p className="text-xs text-purple-700">
                此项目包含 <strong>{codeSummary!.aiIntegrationFiles}</strong> 个AI集成文件，
                已为您配置好聊天机器人、RAG检索等AI能力。
                请查看 <code className="bg-purple-100 px-1 py-0.5 rounded">AIService.kt</code> 和{' '}
                <code className="bg-purple-100 px-1 py-0.5 rounded">AIConfig.kt</code>{' '}
                了解使用方法。
              </p>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
