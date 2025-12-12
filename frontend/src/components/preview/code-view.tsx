/**
 * 代码视图组件
 *
 * 设计理念：
 * - 多文件Tab切换，支持查看生成的不同代码文件
 * - 语法高亮显示，支持Kotlin、XML、JSON、YAML等
 * - 一键复制代码到剪贴板
 * - VS Code风格的暗色主题
 * - 行号显示，便于定位
 *
 * 使用场景：
 * - 预览页面的代码视图
 * - 生成结果的代码展示
 * - 代码对比和审查
 */
'use client';

import React, { useState } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Check,
  Copy,
  FileCode,
  FileJson,
  FileText,
  FileType,
  FileX2,
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

/**
 * 代码文件接口
 */
export interface CodeFile {
  /** 文件ID */
  id: string;
  /** 文件名 */
  name: string;
  /** 文件路径 */
  path: string;
  /** 文件内容 */
  content: string;
  /** 编程语言 */
  language: string;
  /** 文件大小（字节） */
  size?: number;
}

/**
 * 组件Props
 */
interface CodeViewProps {
  /** 代码文件列表 */
  files: CodeFile[];
  /** 默认选中的文件ID */
  defaultFileId?: string;
  /** 是否显示行号 */
  showLineNumbers?: boolean;
  /** 容器类名 */
  className?: string;
  /** 是否启用代码复制 */
  enableCopy?: boolean;
}

/**
 * 根据文件名获取语言标识
 */
const getLanguageFromFileName = (fileName: string): string => {
  const ext = fileName.split('.').pop()?.toLowerCase() || '';
  const languageMap: Record<string, string> = {
    kt: 'kotlin',
    kts: 'kotlin',
    java: 'java',
    xml: 'xml',
    json: 'json',
    yaml: 'yaml',
    yml: 'yaml',
    gradle: 'groovy',
    properties: 'properties',
    md: 'markdown',
    sql: 'sql',
    sh: 'bash',
    js: 'javascript',
    ts: 'typescript',
    tsx: 'tsx',
    jsx: 'jsx',
    css: 'css',
    html: 'html',
  };
  return languageMap[ext] || 'text';
};

/**
 * 根据文件名获取图标
 */
const getFileIcon = (fileName: string) => {
  const ext = fileName.split('.').pop()?.toLowerCase() || '';
  switch (ext) {
    case 'json':
      return <FileJson className="h-4 w-4 text-yellow-500" />;
    case 'kt':
    case 'kts':
    case 'java':
      return <FileCode className="h-4 w-4 text-blue-500" />;
    case 'xml':
    case 'html':
      return <FileType className="h-4 w-4 text-orange-500" />;
    case 'md':
      return <FileText className="h-4 w-4 text-gray-500" />;
    default:
      return <FileX2 className="h-4 w-4 text-gray-400" />;
  }
};

/**
 * 格式化文件大小
 */
const formatFileSize = (bytes?: number): string => {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
};

/**
 * 代码视图组件
 */
export const CodeView: React.FC<CodeViewProps> = ({
  files,
  defaultFileId,
  showLineNumbers = true,
  className,
  enableCopy = true,
}) => {
  const { toast } = useToast();
  const [activeFileId, setActiveFileId] = useState<string>(
    defaultFileId || files[0]?.id || ''
  );
  const [copiedFileId, setCopiedFileId] = useState<string | null>(null);

  /**
   * 复制代码到剪贴板
   */
  const handleCopy = async (file: CodeFile) => {
    try {
      await navigator.clipboard.writeText(file.content);
      setCopiedFileId(file.id);
      toast({
        title: '✅ 复制成功',
        description: `已复制 ${file.name} 到剪贴板`,
        duration: 2000,
      });

      // 2秒后重置复制状态
      setTimeout(() => {
        setCopiedFileId(null);
      }, 2000);
    } catch {
      toast({
        title: '❌ 复制失败',
        description: '无法访问剪贴板',
        variant: 'destructive',
        duration: 3000,
      });
    }
  };

  if (files.length === 0) {
    return (
      <div className={cn('flex h-full items-center justify-center', className)}>
        <div className="text-center">
          <FileX2 className="mx-auto h-12 w-12 text-muted-foreground" />
          <p className="mt-4 text-sm text-muted-foreground">暂无代码文件</p>
        </div>
      </div>
    );
  }

  const activeFile = files.find(f => f.id === activeFileId) || files[0];

  return (
    <div className={cn('flex h-full flex-col', className)}>
      {/* 文件Tab栏 */}
      <Tabs
        value={activeFileId}
        onValueChange={setActiveFileId}
        className="flex h-full flex-col"
      >
        <div className="border-b border-border bg-muted/30 px-2">
          <TabsList className="h-auto w-full justify-start gap-1 bg-transparent p-0">
            {files.map(file => {
              const isCopied = copiedFileId === file.id;
              return (
                <TabsTrigger
                  key={file.id}
                  value={file.id}
                  className={cn(
                    'group relative gap-2 rounded-none border-b-2 border-transparent px-4 py-2',
                    'data-[state=active]:border-primary data-[state=active]:bg-background',
                    'hover:bg-background/50'
                  )}
                >
                  {getFileIcon(file.name)}
                  <span className="text-sm font-medium">{file.name}</span>
                  {file.size && (
                    <Badge variant="secondary" className="ml-1 text-xs">
                      {formatFileSize(file.size)}
                    </Badge>
                  )}

                  {/* 复制按钮 */}
                  {enableCopy && (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="ml-2 h-6 w-6 p-0 opacity-0 transition-opacity group-hover:opacity-100"
                      onClick={e => {
                        e.stopPropagation();
                        handleCopy(file);
                      }}
                      title="复制代码"
                    >
                      {isCopied ? (
                        <Check className="h-3 w-3 text-green-500" />
                      ) : (
                        <Copy className="h-3 w-3" />
                      )}
                    </Button>
                  )}
                </TabsTrigger>
              );
            })}
          </TabsList>
        </div>

        {/* 代码内容区域 */}
        <div className="flex-1 overflow-hidden">
          {files.map(file => (
            <TabsContent
              key={file.id}
              value={file.id}
              className="h-full m-0 data-[state=inactive]:hidden"
            >
              <ScrollArea className="h-full">
                <div className="relative">
                  {/* 语言标识 */}
                  <div className="absolute right-4 top-4 z-10">
                    <Badge variant="secondary" className="text-xs">
                      {file.language || getLanguageFromFileName(file.name)}
                    </Badge>
                  </div>

                  {/* 代码高亮 */}
                  <SyntaxHighlighter
                    language={file.language || getLanguageFromFileName(file.name)}
                    style={vscDarkPlus}
                    showLineNumbers={showLineNumbers}
                    wrapLines={true}
                    customStyle={{
                      margin: 0,
                      padding: '1.5rem',
                      fontSize: '0.875rem',
                      lineHeight: '1.5',
                      background: 'hsl(var(--background))',
                      minHeight: '100%',
                    }}
                    codeTagProps={{
                      style: {
                        fontFamily: '"Fira Code", "JetBrains Mono", "Cascadia Code", monospace',
                      },
                    }}
                  >
                    {file.content}
                  </SyntaxHighlighter>
                </div>
              </ScrollArea>
            </TabsContent>
          ))}
        </div>
      </Tabs>

      {/* 底部状态栏 */}
      <div className="flex items-center justify-between border-t border-border bg-muted/30 px-4 py-2 text-xs text-muted-foreground">
        <div className="flex items-center gap-4">
          <span>文件: {activeFile.name}</span>
          <span>路径: {activeFile.path}</span>
        </div>
        <div className="flex items-center gap-4">
          <span>
            {activeFile.content.split('\n').length} 行
          </span>
          {activeFile.size && (
            <span>{formatFileSize(activeFile.size)}</span>
          )}
          <span className="uppercase">
            {activeFile.language || getLanguageFromFileName(activeFile.name)}
          </span>
        </div>
      </div>
    </div>
  );
};
