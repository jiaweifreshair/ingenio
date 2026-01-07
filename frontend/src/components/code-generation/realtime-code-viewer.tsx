/**
 * 实时代码查看器组件
 *
 * 设计理念：
 * - 实时显示AI生成的代码内容
 * - 文件树导航，支持多文件切换
 * - 代码内容实时流式更新（打字机效果）
 * - 语法高亮显示
 * - 复制代码功能
 *
 * 与code-view.tsx的区别：
 * - 实时更新：支持流式内容追加
 * - 生成状态：显示每个文件的生成进度
 * - 动画效果：代码逐字符/逐行显示
 *
 * 使用场景：
 * - 向导页面实时显示代码生成过程
 * - 用户可见AI实时生成的代码
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
'use client';

import React, { useState, useEffect, useRef, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus, oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useToast } from '@/hooks/use-toast';
import {
  Check,
  Copy,
  FileCode,
  FileJson,
  FileText,
  FileType,
  FolderOpen,
  Folder,
  ChevronRight,
  ChevronDown,
  Loader2,
  CheckCircle2,
  Circle,
  RefreshCw,
} from 'lucide-react';
import type { GeneratedFile } from '@/hooks/use-code-generation-stream';

/**
 * 文件树节点
 */
interface FileTreeNode {
  name: string;
  path: string;
  type: 'file' | 'folder';
  children?: FileTreeNode[];
  file?: GeneratedFile;
}

/**
 * 组件Props
 */
interface RealtimeCodeViewerProps {
  /** 生成的文件列表 */
  files: GeneratedFile[];
  /** 当前正在生成的文件路径 */
  currentFile?: string | null;
  /** 是否正在生成 */
  isGenerating?: boolean;
  /** 是否显示行号 */
  showLineNumbers?: boolean;
  /** 容器类名 */
  className?: string;
  /** 是否启用暗色主题 */
  darkMode?: boolean;
  /** 文件选择回调 */
  onFileSelect?: (path: string) => void;
  /** 是否启用实时滚动（自动滚动到底部） */
  autoScroll?: boolean;
}

/**
 * 根据文件名获取语言标识
 */
const getLanguageFromPath = (filePath: string): string => {
  const ext = filePath.split('.').pop()?.toLowerCase() || '';
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
    scss: 'scss',
    html: 'html',
    vue: 'vue',
    py: 'python',
    go: 'go',
    rs: 'rust',
    swift: 'swift',
  };
  return languageMap[ext] || 'text';
};

/**
 * 根据文件名获取图标
 */
const getFileIcon = (fileName: string, isCompleted?: boolean, isGenerating?: boolean) => {
  if (isGenerating) {
    return <Loader2 className="h-4 w-4 animate-spin text-blue-500" />;
  }

  if (isCompleted) {
    return <CheckCircle2 className="h-4 w-4 text-green-500" />;
  }

  const ext = fileName.split('.').pop()?.toLowerCase() || '';
  switch (ext) {
    case 'json':
      return <FileJson className="h-4 w-4 text-yellow-500" />;
    case 'kt':
    case 'kts':
    case 'java':
    case 'ts':
    case 'tsx':
    case 'js':
    case 'jsx':
      return <FileCode className="h-4 w-4 text-blue-500" />;
    case 'xml':
    case 'html':
      return <FileType className="h-4 w-4 text-orange-500" />;
    case 'md':
      return <FileText className="h-4 w-4 text-gray-500" />;
    default:
      return <Circle className="h-4 w-4 text-gray-400" />;
  }
};

/**
 * 验证文件路径是否有效
 * 过滤掉包含XML标签碎片的无效路径
 */
const isValidFilePath = (path: string): boolean => {
  if (!path || typeof path !== 'string') return false;
  
  // 过滤包含XML标签碎片的路径
  if (path.includes('<file') || path.includes('</file>') || path.includes('path="') || path.includes("path='")) {
    return false;
  }
  
  // 过滤以 < 或 > 开头或结尾的路径
  if (path.startsWith('<') || path.startsWith('>') || path.endsWith('<') || path.endsWith('>')) {
    return false;
  }
  
  // 过滤包含明显HTML/XML字符的路径
  if (path.includes('">') || path.includes("'>")) {
    return false;
  }
  
  return true;
};

/**
 * 构建文件树
 */
const buildFileTree = (files: GeneratedFile[]): FileTreeNode[] => {
  const root: FileTreeNode[] = [];
  const pathMap = new Map<string, FileTreeNode>();

  // 过滤掉无效路径的文件
  const validFiles = files.filter(file => isValidFilePath(file.path));

  // 按路径排序
  const sortedFiles = [...validFiles].sort((a, b) => a.path.localeCompare(b.path));

  for (const file of sortedFiles) {
    const parts = file.path.split('/').filter(Boolean);
    let currentPath = '';

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      const isFile = i === parts.length - 1;
      const parentPath = currentPath;
      currentPath = currentPath ? `${currentPath}/${part}` : part;

      if (!pathMap.has(currentPath)) {
        const node: FileTreeNode = {
          name: part,
          path: currentPath,
          type: isFile ? 'file' : 'folder',
          children: isFile ? undefined : [],
          file: isFile ? file : undefined,
        };

        pathMap.set(currentPath, node);

        if (parentPath) {
          const parent = pathMap.get(parentPath);
          parent?.children?.push(node);
        } else {
          root.push(node);
        }
      }
    }
  }

  return root;
};

/**
 * 文件树节点组件
 */
const FileTreeItem: React.FC<{
  node: FileTreeNode;
  level: number;
  selectedPath: string | null;
  currentGeneratingPath: string | null;
  onSelect: (path: string) => void;
}> = ({ node, level, selectedPath, currentGeneratingPath, onSelect }) => {
  const [isExpanded, setIsExpanded] = useState(true);
  const isSelected = selectedPath === node.path;
  const isGenerating = currentGeneratingPath === node.path;
  const isCompleted = node.file?.completed;

  if (node.type === 'folder') {
    return (
      <div>
        <button
          onClick={() => setIsExpanded(!isExpanded)}
          className={cn(
            'flex items-center gap-1.5 w-full px-2 py-1 text-sm rounded-md',
            'hover:bg-muted/50 transition-colors',
            'text-left'
          )}
          style={{ paddingLeft: `${level * 12 + 8}px` }}
        >
          {isExpanded ? (
            <ChevronDown className="h-3 w-3 text-muted-foreground" />
          ) : (
            <ChevronRight className="h-3 w-3 text-muted-foreground" />
          )}
          {isExpanded ? (
            <FolderOpen className="h-4 w-4 text-yellow-500" />
          ) : (
            <Folder className="h-4 w-4 text-yellow-500" />
          )}
          <span className="truncate">{node.name}</span>
        </button>

        <AnimatePresence>
          {isExpanded && node.children && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              transition={{ duration: 0.2 }}
            >
              {node.children.map(child => (
                <FileTreeItem
                  key={child.path}
                  node={child}
                  level={level + 1}
                  selectedPath={selectedPath}
                  currentGeneratingPath={currentGeneratingPath}
                  onSelect={onSelect}
                />
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    );
  }

  return (
    <button
      onClick={() => onSelect(node.path)}
      className={cn(
        'flex items-center gap-1.5 w-full px-2 py-1 text-sm rounded-md',
        'hover:bg-muted/50 transition-colors',
        'text-left',
        isSelected && 'bg-primary/10 text-primary',
        isGenerating && 'bg-blue-100 dark:bg-blue-900/30'
      )}
      style={{ paddingLeft: `${level * 12 + 8}px` }}
    >
      {getFileIcon(node.name, isCompleted, isGenerating)}
      <span className="truncate flex-1">{node.name}</span>
      {isGenerating && (
        <Badge variant="secondary" className="text-xs py-0 px-1">
          生成中
        </Badge>
      )}
      {isCompleted && !isGenerating && (
        <Badge variant="outline" className="text-xs py-0 px-1 text-green-600">
          完成
        </Badge>
      )}
    </button>
  );
};

/**
 * 实时代码查看器组件
 */
export const RealtimeCodeViewer: React.FC<RealtimeCodeViewerProps> = ({
  files,
  currentFile,
  isGenerating = false,
  showLineNumbers = true,
  className,
  darkMode = true,
  onFileSelect,
  autoScroll = true,
}) => {
  const { toast } = useToast();
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const codeContainerRef = useRef<HTMLDivElement>(null);

  // 平滑打字机效果Hook
  const useSmoothCode = (targetCode: string, isLive: boolean) => {
    const [displayedCode, setDisplayedCode] = useState(isLive ? '' : targetCode);
    const targetCodeRef = useRef(targetCode);

    useEffect(() => {
      targetCodeRef.current = targetCode;
      if (!isLive) {
        setDisplayedCode(targetCode);
      }
    }, [targetCode, isLive]);

    useEffect(() => {
      if (!isLive) return;

      let rafId: number;
      const update = () => {
        setDisplayedCode(current => {
          if (current === targetCodeRef.current) return current;
          const diff = targetCodeRef.current.length - current.length;
          if (diff <= 0) return targetCodeRef.current;

          // 动态速度控制：根据剩余字符数调整打字速度
          // 基础速度：每次2个字符
          // 追赶速度：剩余量的10%
          const step = Math.max(2, Math.ceil(diff / 10));
          return targetCodeRef.current.slice(0, current.length + step);
        });
        rafId = requestAnimationFrame(update);
      };
      rafId = requestAnimationFrame(update);
      return () => cancelAnimationFrame(rafId);
    }, [isLive]);

    return displayedCode;
  };

  // 构建文件树
  const fileTree = useMemo(() => buildFileTree(files), [files]);

  // 选中的文件
  const selectedFile = useMemo(() => {
    if (!selectedPath) return files[0] || null;
    return files.find(f => f.path === selectedPath) || null;
  }, [files, selectedPath]);

  // 应用平滑效果
  const isSelectedFileGenerating = currentFile === selectedFile?.path && isGenerating;
  const smoothContent = useSmoothCode(
    selectedFile?.content || '',
    isSelectedFileGenerating || false
  );

  // 自动选择当前正在生成的文件
  useEffect(() => {
    if (currentFile && isGenerating) {
      setSelectedPath(currentFile);
    }
  }, [currentFile, isGenerating]);

  // 自动滚动到底部
  useEffect(() => {
    if (autoScroll && codeContainerRef.current && isSelectedFileGenerating) {
      const container = codeContainerRef.current;
      // 只有当用户已经在接近底部时才自动滚动，避免打扰用户查看上面的代码
      const isNearBottom = container.scrollHeight - container.scrollTop - container.clientHeight < 200;
      if (isNearBottom) {
        container.scrollTop = container.scrollHeight;
      }
    }
  }, [smoothContent, autoScroll, isSelectedFileGenerating]);

  /**
   * 处理文件选择
   */
  const handleFileSelect = (path: string) => {
    setSelectedPath(path);
    onFileSelect?.(path);
  };

  /**
   * 复制代码
   */
  const handleCopy = async () => {
    if (!selectedFile) return;

    try {
      await navigator.clipboard.writeText(selectedFile.content);
      setCopied(true);
      toast({
        title: '复制成功',
        description: `已复制 ${selectedFile.path} 到剪贴板`,
        duration: 2000,
      });

      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast({
        title: '复制失败',
        description: '无法访问剪贴板',
        variant: 'destructive',
      });
    }
  };

  // 语法高亮主题
  const syntaxTheme = darkMode ? vscDarkPlus : oneLight;

  return (
    <div className={cn('flex h-full border rounded-lg overflow-hidden', className)}>
      {/* 左侧文件树 */}
      <div className="w-64 border-r bg-muted/20 flex flex-col">
        {/* 文件树头部 */}
        <div className="px-3 py-2 border-b bg-muted/30 flex items-center justify-between">
          <span className="text-sm font-medium">文件</span>
          <Badge variant="secondary" className="text-xs">
            {files.length}
          </Badge>
        </div>

        {/* 文件树内容 */}
        <ScrollArea className="flex-1">
          <div className="p-2">
            {files.length === 0 ? (
              <div className="text-center py-8 text-muted-foreground text-sm">
                {isGenerating ? (
                  <div className="flex flex-col items-center gap-2">
                    <Loader2 className="h-5 w-5 animate-spin" />
                    <span>等待文件生成...</span>
                  </div>
                ) : (
                  <span>暂无文件</span>
                )}
              </div>
            ) : (
              fileTree.map(node => (
                <FileTreeItem
                  key={node.path}
                  node={node}
                  level={0}
                  selectedPath={selectedPath}
                  currentGeneratingPath={currentFile || null}
                  onSelect={handleFileSelect}
                />
              ))
            )}
          </div>
        </ScrollArea>

        {/* 文件树底部统计 */}
        <div className="px-3 py-2 border-t bg-muted/30 text-xs text-muted-foreground">
          <div className="flex items-center justify-between">
            <span>{files.filter(f => f.completed).length} 完成</span>
            <span>{files.filter(f => !f.completed).length} 进行中</span>
          </div>
        </div>
      </div>

      {/* 右侧代码内容 */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* 代码头部 */}
        <div className="px-4 py-2 border-b bg-muted/30 flex items-center justify-between">
          <div className="flex items-center gap-2 min-w-0">
            {selectedFile ? (
              <>
                {getFileIcon(
                  selectedFile.path,
                  selectedFile.completed,
                  currentFile === selectedFile.path && isGenerating
                )}
                <span className="text-sm font-mono truncate">
                  {selectedFile.path}
                </span>
                <Badge variant="secondary" className="text-xs ml-2">
                  {getLanguageFromPath(selectedFile.path)}
                </Badge>
                {currentFile === selectedFile.path && isGenerating && (
                  <motion.span
                    className="text-xs text-blue-500 flex items-center gap-1"
                    animate={{ opacity: [1, 0.5, 1] }}
                    transition={{ duration: 1.5, repeat: Infinity }}
                  >
                    <RefreshCw className="h-3 w-3 animate-spin" />
                    实时更新中
                  </motion.span>
                )}
              </>
            ) : (
              <span className="text-sm text-muted-foreground">选择文件查看代码</span>
            )}
          </div>

          {/* 操作按钮 */}
          {selectedFile && (
            <Button
              variant="ghost"
              size="sm"
              onClick={handleCopy}
              className="gap-1"
            >
              {copied ? (
                <>
                  <Check className="h-4 w-4 text-green-500" />
                  已复制
                </>
              ) : (
                <>
                  <Copy className="h-4 w-4" />
                  复制
                </>
              )}
            </Button>
          )}
        </div>

        {/* 代码内容 */}
        <div
          ref={codeContainerRef}
          className="flex-1 overflow-auto"
        >
          {selectedFile ? (
            <div className="relative min-h-full">
              <SyntaxHighlighter
                language={getLanguageFromPath(selectedFile.path)}
                style={syntaxTheme}
                showLineNumbers={showLineNumbers}
                wrapLines={true}
                customStyle={{
                  margin: 0,
                  padding: '1rem',
                  fontSize: '0.875rem',
                  lineHeight: '1.6',
                  background: darkMode
                    ? 'hsl(var(--background))'
                    : 'hsl(var(--muted) / 0.3)',
                  minHeight: '100%',
                }}
                codeTagProps={{
                  style: {
                    fontFamily: '"Fira Code", "JetBrains Mono", "Cascadia Code", monospace',
                  },
                }}
              >
                {smoothContent || '// 等待内容生成...'}
              </SyntaxHighlighter>

              {/* 生成中的光标效果 */}
              {isSelectedFileGenerating && (
                <motion.div
                  className="absolute bottom-4 left-4 h-4 w-2 bg-primary"
                  animate={{ opacity: [1, 0] }}
                  transition={{ duration: 0.5, repeat: Infinity, repeatType: 'reverse' }}
                />
              )}
            </div>
          ) : (
            <div className="flex items-center justify-center h-full text-muted-foreground">
              <div className="text-center">
                <FileCode className="h-12 w-12 mx-auto mb-4 opacity-50" />
                <p className="text-sm">请从左侧选择文件查看代码</p>
              </div>
            </div>
          )}
        </div>

        {/* 代码底部状态栏 */}
        <div className="px-4 py-1.5 border-t bg-muted/30 text-xs text-muted-foreground flex items-center justify-between">
          {selectedFile ? (
            <>
              <div className="flex items-center gap-4">
                <span>{smoothContent.split('\n').length} 行</span>
                <span>{smoothContent.length} 字符</span>
              </div>
              <div className="flex items-center gap-2">
                {selectedFile.completed ? (
                  <span className="text-green-600 flex items-center gap-1">
                    <CheckCircle2 className="h-3 w-3" />
                    生成完成
                  </span>
                ) : (
                  <span className="text-blue-500 flex items-center gap-1">
                    <Loader2 className="h-3 w-3 animate-spin" />
                    生成中...
                  </span>
                )}
              </div>
            </>
          ) : (
            <span>无选中文件</span>
          )}
        </div>
      </div>
    </div>
  );
};

export default RealtimeCodeViewer;
