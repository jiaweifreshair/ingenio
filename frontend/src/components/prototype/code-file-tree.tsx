'use client';

import React, { useState } from 'react';
import { ChevronRight, ChevronDown, File, Folder, FolderOpen, FileCode2, FileJson, Palette, Code2 } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * 文件节点接口
 */
export interface FileNode {
  /** 文件路径 */
  path: string;
  /** 文件内容 */
  content: string;
  /** 文件类型 */
  type: 'react' | 'javascript' | 'typescript' | 'css' | 'json' | 'other';
  /** 是否已完成生成 */
  completed: boolean;
  /** 是否已被用户编辑 */
  edited?: boolean;
}

/**
 * 文件树节点（内部使用）
 */
interface TreeNode {
  name: string;
  path: string;
  type: 'file' | 'folder';
  children?: TreeNode[];
  file?: FileNode;
}

/**
 * CodeFileTree组件属性
 */
export interface CodeFileTreeProps {
  /** 文件列表 */
  files: FileNode[];
  /** 选中的文件路径 */
  selectedPath?: string;
  /** 文件选择回调 */
  onFileSelect: (file: FileNode) => void;
  /** 自定义className */
  className?: string;
}

/**
 * 根据文件扩展名获取文件类型图标
 */
function getFileIcon(_path: string, type: FileNode['type']) {
  if (type === 'react') return <FileCode2 className="h-4 w-4 text-blue-500" />;
  if (type === 'json') return <FileJson className="h-4 w-4 text-yellow-500" />;
  if (type === 'css') return <Palette className="h-4 w-4 text-pink-500" />;
  if (type === 'typescript') return <Code2 className="h-4 w-4 text-blue-600" />;
  if (type === 'javascript') return <Code2 className="h-4 w-4 text-yellow-600" />;
  return <File className="h-4 w-4 text-gray-500" />;
}

/**
 * 验证文件路径是否有效
 * 过滤掉包含XML标签碎片的无效路径
 */
function isValidFilePath(path: string): boolean {
  if (!path || typeof path !== 'string') return false;
  
  // 过滤包含XML标签碎片的路径
  if (path.includes('<file') || path.includes('</file>') || path.includes('path="') || path.includes('path=\'')) {
    return false;
  }
  
  // 过滤以 < 或 > 开头或结尾的路径
  if (path.startsWith('<') || path.startsWith('>') || path.endsWith('<') || path.endsWith('>')) {
    return false;
  }
  
  // 过滤包含明显HTML/XML字符的路径
  if (path.includes('">') || path.includes('">')) {
    return false;
  }
  
  // 路径应该看起来像文件路径（至少包含一个点或斜杠）
  // 但也允许简单的文件名如 "README"
  return true;
}

/**
 * 构建文件树结构
 */
function buildFileTree(files: FileNode[]): TreeNode[] {
  const root: TreeNode[] = [];

  // 过滤掉无效路径的文件
  const validFiles = files.filter(file => isValidFilePath(file.path));

  validFiles.forEach((file) => {
    const parts = file.path.split('/');
    let currentLevel = root;

    parts.forEach((part, index) => {
      const isFile = index === parts.length - 1;
      const existingNode = currentLevel.find((node) => node.name === part);

      if (existingNode) {
        if (!isFile && existingNode.children) {
          currentLevel = existingNode.children;
        }
      } else {
        const newNode: TreeNode = {
          name: part,
          path: parts.slice(0, index + 1).join('/'),
          type: isFile ? 'file' : 'folder',
          ...(isFile ? { file } : { children: [] }),
        };

        currentLevel.push(newNode);

        if (!isFile && newNode.children) {
          currentLevel = newNode.children;
        }
      }
    });
  });

  return root;
}

/**
 * 文件树节点组件
 */
function TreeNodeComponent({
  node,
  level,
  selectedPath,
  onFileSelect,
  expandedFolders,
  toggleFolder,
}: {
  node: TreeNode;
  level: number;
  selectedPath?: string;
  onFileSelect: (file: FileNode) => void;
  expandedFolders: Set<string>;
  toggleFolder: (path: string) => void;
}) {
  const isExpanded = expandedFolders.has(node.path);
  const isSelected = node.file && node.path === selectedPath;

  if (node.type === 'folder') {
    return (
      <div>
        <button
          onClick={() => toggleFolder(node.path)}
          className={cn(
            'w-full flex items-center gap-2 px-2 py-1.5 text-sm hover:bg-gray-100 dark:hover:bg-gray-800 rounded transition-colors',
            'text-left'
          )}
          style={{ paddingLeft: `${level * 12 + 8}px` }}
        >
          {isExpanded ? (
            <ChevronDown className="h-4 w-4 text-gray-600 dark:text-gray-400" />
          ) : (
            <ChevronRight className="h-4 w-4 text-gray-600 dark:text-gray-400" />
          )}
          {isExpanded ? (
            <FolderOpen className="h-4 w-4 text-blue-500" />
          ) : (
            <Folder className="h-4 w-4 text-blue-500" />
          )}
          <span className="font-medium text-gray-900 dark:text-gray-100">{node.name}</span>
        </button>

        {isExpanded && node.children && (
          <div>
            {node.children.map((child) => (
              <TreeNodeComponent
                key={child.path}
                node={child}
                level={level + 1}
                selectedPath={selectedPath}
                onFileSelect={onFileSelect}
                expandedFolders={expandedFolders}
                toggleFolder={toggleFolder}
              />
            ))}
          </div>
        )}
      </div>
    );
  }

  // 文件节点
  return (
    <button
      onClick={() => node.file && onFileSelect(node.file)}
      className={cn(
        'w-full flex items-center gap-2 px-2 py-1.5 text-sm hover:bg-gray-100 dark:hover:bg-gray-800 rounded transition-colors',
        'text-left',
        isSelected && 'bg-blue-50 dark:bg-blue-900/20 border-l-2 border-blue-500'
      )}
      style={{ paddingLeft: `${level * 12 + 28}px` }}
    >
      {node.file && getFileIcon(node.path, node.file.type)}
      <span className={cn(
        'text-gray-700 dark:text-gray-300',
        isSelected && 'font-medium text-blue-600 dark:text-blue-400'
      )}>
        {node.name}
      </span>
      {node.file?.edited && (
        <span className="ml-auto text-xs text-orange-500 dark:text-orange-400">●</span>
      )}
      {node.file && !node.file.completed && (
        <span className="ml-auto text-xs text-gray-400 dark:text-gray-600">生成中...</span>
      )}
    </button>
  );
}

/**
 * CodeFileTree - 代码文件树组件
 *
 * 功能：
 * - 显示生成的代码文件树结构
 * - 支持文件夹展开/折叠
 * - 支持文件选择和高亮
 * - 显示文件类型图标
 * - 标记已编辑和未完成的文件
 *
 * @param props - 组件属性
 * @returns React组件
 */
export function CodeFileTree({
  files,
  selectedPath,
  onFileSelect,
  className,
}: CodeFileTreeProps): React.ReactElement {
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set(['src']));

  const toggleFolder = (path: string) => {
    setExpandedFolders((prev) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  };

  const tree = buildFileTree(files);

  if (files.length === 0) {
    return (
      <div className={cn(
        'flex flex-col items-center justify-center h-full text-center p-6',
        className
      )}>
        <FileCode2 className="h-12 w-12 text-gray-400 dark:text-gray-600 mb-4" />
        <p className="text-sm text-gray-500 dark:text-gray-400">
          暂无生成的文件
        </p>
      </div>
    );
  }

  return (
    <div className={cn('w-full h-full overflow-y-auto', className)}>
      <div className="p-2">
        {tree.map((node) => (
          <TreeNodeComponent
            key={node.path}
            node={node}
            level={0}
            selectedPath={selectedPath}
            onFileSelect={onFileSelect}
            expandedFolders={expandedFolders}
            toggleFolder={toggleFolder}
          />
        ))}
      </div>
    </div>
  );
}
