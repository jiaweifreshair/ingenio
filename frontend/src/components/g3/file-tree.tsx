"use client";

import { useMemo, useState, useCallback } from "react";
import { cn } from "@/lib/utils";
import {
  ChevronRight,
  ChevronDown,
  Folder,
  FolderOpen,
  FileCode,
  FileJson,
  Database,
  File,
  Coffee,
  Braces,
  Hash,
} from "lucide-react";

/**
 * 文件树节点数据结构
 */
export interface FileTreeNode {
  /** 节点名称（文件名或目录名） */
  name: string;
  /** 完整路径 */
  path: string;
  /** 是否为目录 */
  isDirectory: boolean;
  /** 子节点（仅目录有） */
  children?: FileTreeNode[];
  /** 原始数据ID（仅文件有） */
  id?: string;
  /** 是否有错误（仅文件有） */
  hasErrors?: boolean;
  /** 生成者（仅文件有） */
  generatedBy?: string;
  /** 轮次（仅文件有） */
  round?: number;
}

/**
 * 扁平文件项（输入数据格式）
 */
export interface FlatFileItem {
  id: string;
  filePath: string;
  hasErrors?: boolean;
  generatedBy?: string;
  round?: number;
}

/**
 * 内部构建用的节点类型（children为Map，便于查找）
 */
interface BuildNode {
  name: string;
  path: string;
  isDirectory: boolean;
  children: Map<string, BuildNode>;
  id?: string;
  hasErrors?: boolean;
  generatedBy?: string;
  round?: number;
}

/**
 * 将扁平文件列表转换为树形结构，并应用空包折叠（Compact Folders）
 */
export function buildFileTree(files: FlatFileItem[]): FileTreeNode[] {
  const root = new Map<string, BuildNode>();

  // 1. 构建原始树
  for (const file of files) {
    const parts = file.filePath.split("/").filter(Boolean);
    let currentLevel = root;
    let currentPath = "";

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      currentPath = currentPath ? `${currentPath}/${part}` : part;
      const isLast = i === parts.length - 1;

      if (!currentLevel.has(part)) {
        currentLevel.set(part, {
          name: part,
          path: currentPath,
          isDirectory: !isLast,
          children: new Map(),
          ...(isLast && {
            id: file.id,
            hasErrors: file.hasErrors,
            generatedBy: file.generatedBy,
            round: file.round,
          }),
        });
      }

      const node = currentLevel.get(part)!;
      if (!isLast) {
        if (!node.isDirectory) {
          node.isDirectory = true; // 处理先有文件后有子目录的情况（虽少见）
        }
        currentLevel = node.children;
      }
    }
  }

  // 2. 递归处理空包折叠
  function processCompactFolders(level: Map<string, BuildNode>): FileTreeNode[] {
    const nodes: FileTreeNode[] = [];

    for (const node of level.values()) {
      if (node.isDirectory) {
        // 尝试折叠：如果该目录只有一个子节点，且子节点也是目录
        let currentNode = node;
        let mergedName = node.name;
        let mergedPath = node.path;

        while (
          currentNode.isDirectory &&
          currentNode.children.size === 1
        ) {
          const onlyChild = currentNode.children.values().next().value;
          if (onlyChild && onlyChild.isDirectory) {
            currentNode = onlyChild;
            mergedName = `${mergedName}/${onlyChild.name}`;
            mergedPath = onlyChild.path;
          } else {
            break;
          }
        }

        // 递归处理子节点
        const processedChildren = processCompactFolders(currentNode.children);

        nodes.push({
          name: mergedName,
          path: mergedPath, // 使用最深层的路径，确保 toggle 逻辑正确
          isDirectory: true,
          children: processedChildren,
        });
      } else {
        // 文件节点直接添加
        nodes.push({
          name: node.name,
          path: node.path,
          isDirectory: false,
          id: node.id,
          hasErrors: node.hasErrors,
          generatedBy: node.generatedBy,
          round: node.round,
        });
      }
    }

    // 排序：目录在前，文件在后
    return nodes.sort((a, b) => {
      if (a.isDirectory && !b.isDirectory) return -1;
      if (!a.isDirectory && b.isDirectory) return 1;
      return a.name.localeCompare(b.name);
    });
  }

  return processCompactFolders(root);
}

/**
 * 获取文件图标 (Updated for Apple Tech style)
 */
function getFileIcon(filename: string) {
  const ext = filename.split(".").pop()?.toLowerCase();

  switch (ext) {
    case "java":
      // Red Coffee Cup for Java
      return <Coffee className="w-4 h-4 text-red-400" strokeWidth={2} />;
    case "ts":
    case "tsx":
      return <FileCode className="w-4 h-4 text-blue-400" />;
    case "js":
    case "jsx":
      return <FileCode className="w-4 h-4 text-yellow-400" />;
    case "json":
      return <Braces className="w-4 h-4 text-amber-400" />;
    case "sql":
      return <Database className="w-4 h-4 text-indigo-400" />;
    case "xml":
    case "html":
      return <FileCode className="w-4 h-4 text-orange-400" />;
    case "yaml":
    case "yml":
      return <FileJson className="w-4 h-4 text-pink-400" />; // Settings -> FileJson usually looks cleaner
    case "md":
      return <File className="w-4 h-4 text-white/60" />;
    case "css":
    case "scss":
      return <Hash className="w-4 h-4 text-blue-300" />;
    default:
      return <File className="w-4 h-4 text-white/40" />;
  }
}

/**
 * 树节点组件Props
 */
interface TreeNodeProps {
  node: FileTreeNode;
  depth: number;
  selectedId: string | null;
  expandedPaths: Set<string>;
  onSelect: (node: FileTreeNode) => void;
  onToggle: (path: string) => void;
}

/**
 * 树节点组件
 */
function TreeNode({ node, depth, selectedId, expandedPaths, onSelect, onToggle }: TreeNodeProps) {
  const isExpanded = expandedPaths.has(node.path);
  const isSelected = node.id === selectedId;
  const paddingLeft = depth * 12 + 8; // 减小缩进，更紧凑

  if (node.isDirectory) {
    return (
      <div>
        <button
          className={cn(
            "w-full flex items-center gap-1.5 py-1 hover:bg-white/[0.04] transition-colors text-left rounded-md", // 更圆角，更淡的hover
            isExpanded && ""
          )}
          style={{ paddingLeft }}
          onClick={() => onToggle(node.path)}
        >
          {isExpanded ? (
            <ChevronDown className="w-3.5 h-3.5 text-white/50 shrink-0" />
          ) : (
            <ChevronRight className="w-3.5 h-3.5 text-white/50 shrink-0" />
          )}
          {isExpanded ? (
            <FolderOpen className="w-3.5 h-3.5 text-blue-400/90 shrink-0" fill="currentColor" fillOpacity={0.2} /> // 蓝色文件夹，带填充
          ) : (
            <Folder className="w-3.5 h-3.5 text-blue-400/90 shrink-0" fill="currentColor" fillOpacity={0.2} />
          )}
          <span className="text-[11px] font-medium text-white/80 truncate font-mono tracking-tight">{node.name}</span>
        </button>
        {isExpanded && node.children && (
          <div>
            {node.children.map((child) => (
              <TreeNode
                key={child.path}
                node={child}
                depth={depth + 1}
                selectedId={selectedId}
                expandedPaths={expandedPaths}
                onSelect={onSelect}
                onToggle={onToggle}
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
      className={cn(
        "w-full flex items-center gap-2 py-1 hover:bg-white/[0.04] transition-colors text-left rounded-md",
        isSelected && "bg-blue-500/20 text-blue-100" // 选中状态优化
      )}
      style={{ paddingLeft: paddingLeft + 18 }} // 对齐
      onClick={() => onSelect(node)}
    >
      {getFileIcon(node.name)}
      <span
        className={cn(
          "text-[11px] truncate font-mono tracking-tight",
          isSelected ? "text-blue-100 font-medium" : "text-white/70",
          node.hasErrors && "text-red-300"
        )}
      >
        {node.name}
      </span>
      {node.hasErrors && (
        <span className="ml-auto mr-2 w-1.5 h-1.5 rounded-full bg-red-500 shrink-0" />
      )}
    </button>
  );
}

/**
 * 文件树组件Props
 */
interface FileTreeProps {
  files: FlatFileItem[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  keyword?: string;
  className?: string;
}

/**
 * IDE风格文件树组件 (Optimized)
 */
export function FileTree({ files, selectedId, onSelect, keyword = "", className }: FileTreeProps) {
  // 默认展开所有目录 (Simplified logic for initial state could be improved, but keep basic for now)
  const [expandedPaths, setExpandedPaths] = useState<Set<string>>(() => {
    // 初始不全展开，或者智能展开？全展开比较方便查看生成的代码
    // 这里保持逻辑，但需要适配 compact folders 的 path
    // 由于 path 变了，这里预计算比较麻烦，暂且留空让 buildFileTree 计算后用户手动点或者默认都展开？
    // 为了体验，我们还是全展开吧，但是需要知道 compact 后的 path。
    // 简单起见，初始为空，或者全部展开（需要遍历 tree）。
    // 让我们在 render 时计算 tree，然后 useMemo 只需要 files。
    return new Set<string>();
  });
  
  // Ref to track if we've initialized expansion (to expand all by default once)
  const [initialized, setInitialized] = useState(false);

  // 过滤文件（搜索时）
  const filteredFiles = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw) return files;
    return files.filter((f) => f.filePath.toLowerCase().includes(kw));
  }, [files, keyword]);

  // 构建树形结构
  const tree = useMemo(() => buildFileTree(filteredFiles), [filteredFiles]);

  // Auto-expand all on first load or filter change
  useMemo(() => {
    if (!initialized || keyword) {
       const allPaths = new Set<string>();
       function traverse(nodes: FileTreeNode[]) {
         for (const node of nodes) {
           if (node.isDirectory) {
             allPaths.add(node.path);
             if (node.children) traverse(node.children);
           }
         }
       }
       traverse(tree);
       setExpandedPaths(allPaths);
       if (!initialized && tree.length > 0) setInitialized(true);
    }
  }, [tree, keyword, initialized]);


  const handleToggle = useCallback((path: string) => {
    setExpandedPaths((prev) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  }, []);

  const handleSelect = useCallback(
    (node: FileTreeNode) => {
      if (node.id) {
        onSelect(node.id);
      }
    },
    [onSelect]
  );
  
  if (tree.length === 0) {
    return (
      <div className={cn("py-10 text-center text-xs text-white/30", className)}>
        {keyword ? "No matches" : "No files"}
      </div>
    );
  }

  return (
    <div className={cn("flex flex-col min-h-0 select-none", className)}>
      <div className="flex-1 overflow-y-auto px-2 py-2">
        {tree.map((node) => (
          <TreeNode
            key={node.path}
            node={node}
            depth={0}
            selectedId={selectedId}
            expandedPaths={expandedPaths}
            onSelect={handleSelect}
            onToggle={handleToggle}
          />
        ))}
      </div>
    </div>
  );
}
