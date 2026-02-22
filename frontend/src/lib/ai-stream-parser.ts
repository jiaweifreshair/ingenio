/**
 * 生成的文件信息
 */
export interface GeneratedFile {
  path: string;
  content: string;
  type: string;
  completed: boolean;
}

/**
 * 解析 `<file path="...">...</file>` 风格的XML片段（更鲁棒）
 *
 * 典型问题：
 * - 流式/续写时，模型可能在未输出 `</file>` 的情况下再次输出新的 `<file ...>`，导致出现嵌套/重叠标签
 * - 直接用正则 `<file ...>...?</file>` 会把两个文件块“吞”成一个，最终在前端看到“代码拼接/重复”
 *
 * 处理策略：
 * - 按文本顺序扫描 `<file ...>`，遇到新的 `<file ...>` 但前一个还没闭合时，将前一个视为无效片段并丢弃
 * - 只有遇到 `</file>` 的块才认为 completed=true
 * - 如果最后一个 `<file ...>` 到文本结尾都没闭合，则作为 currentFile 候选
 */
function parseFileXmlBlocks(text: string): {
  blocks: Array<{ path: string; content: string }>;
  current: { path: string; content: string } | null;
} {
  const blocks: Array<{ path: string; content: string }> = [];
  let current: { path: string; content: string } | null = null;

  const openTagRe = /^<file\s+path=(?:"([^"]+)"|'([^']+)')[^>]*>/i;
  let cursor = 0;

  while (cursor < text.length) {
    const openIndex = text.indexOf('<file', cursor);
    if (openIndex === -1) break;

    const openSlice = text.slice(openIndex);
    const openMatch = openSlice.match(openTagRe);
    if (!openMatch) {
      cursor = openIndex + 5;
      continue;
    }

    const openTag = openMatch[0];
    const path = openMatch[1] || openMatch[2] || '';
    const contentStart = openIndex + openTag.length;

    const nextOpenIndex = text.indexOf('<file', contentStart);
    const closeIndex = text.indexOf('</file>', contentStart);

    if (closeIndex !== -1 && (nextOpenIndex === -1 || closeIndex < nextOpenIndex)) {
      blocks.push({ path, content: text.slice(contentStart, closeIndex) });
      cursor = closeIndex + '</file>'.length;
      continue;
    }

    if (nextOpenIndex !== -1) {
      // 被新的 <file ...> 打断，视为上一个文件块无效（避免正则吞并导致的拼接）
      cursor = nextOpenIndex;
      continue;
    }

    // 走到末尾仍未闭合，作为 currentFile 候选
    current = { path, content: text.slice(contentStart) };
    break;
  }

  return { blocks, current };
}

/**
 * 从文件路径推断文件类型
 */
export function getFileType(path: string): string {
  const ext = path.split('.').pop()?.toLowerCase() || '';
  const typeMap: Record<string, string> = {
    'js': 'javascript',
    'jsx': 'javascript',
    'ts': 'typescript',
    'tsx': 'typescript',
    'css': 'css',
    'scss': 'scss',
    'html': 'html',
    'json': 'json',
    'md': 'markdown',
  };
  return typeMap[ext] || 'text';
}

/**
 * 从AI响应中解析文件
 * 支持多种格式：
 * 1. <file path="...">...</file>
 * 2. <boltAction type="file" filePath="...">...</boltAction>
 * 3. ```lang filename="path" ... ```
 * 4. ```lang:path ... ```
 * 5. 代码块内部注释: // filename: path
 *
 * 使用Map去重，同一路径只保留最新内容
 */
export function parseFilesFromResponse(text: string): { files: GeneratedFile[]; currentFile: GeneratedFile | null } {
  // 使用Map去重，key是文件路径，value是文件对象
  const fileMap = new Map<string, GeneratedFile>();
  let currentFile: GeneratedFile | null = null;

  let match: RegExpExecArray | null;

  // 1. 解析 <file path="...">...</file>（增强鲁棒性，避免嵌套/重叠导致拼接）
  const { blocks: fileBlocks, current: openFile } = parseFileXmlBlocks(text);
  for (const block of fileBlocks) {
    fileMap.set(block.path, {
      path: block.path,
      content: block.content.trim(),
      type: getFileType(block.path),
      completed: true,
    });
  }

  // 2. 匹配 Bolt 风格 <boltAction type="file" filePath="...">...</boltAction>
  const boltRegex = /<boltAction\s+type="file"\s+filePath="([^"]+)">([\s\S]*?)<\/boltAction>/g;
  while ((match = boltRegex.exec(text)) !== null) {
    const [, path, content] = match;
    fileMap.set(path, {
      path,
      content: content.trim(),
      type: getFileType(path),
      completed: true,
    });
  }

  // 3. 检查是否有正在生成的 XML 标签（<file> 或 <boltAction>）
  // 3.1 优先使用 parseFileXmlBlocks 识别到的“末尾未闭合 <file>”
  if (openFile && openFile.path && !fileMap.has(openFile.path)) {
    currentFile = {
      path: openFile.path,
      content: openFile.content.trim(),
      type: getFileType(openFile.path),
      completed: false,
    };
  }

  // 3.2 兼容未闭合的 <boltAction ...>
  if (!currentFile) {
    const openBoltMatch = text.match(/<boltAction\s+type="file"\s+filePath="([^"]+)">([\s\S]*)$/);
    if (openBoltMatch) {
      const path = openBoltMatch[1];
      const content = openBoltMatch[2];
      if (path && !fileMap.has(path)) {
        currentFile = {
          path,
          content: content.trim(),
          type: getFileType(path),
          completed: false,
        };
      }
    }
  }

  // 4. 解析 Markdown 代码块
  // 如果 XML 解析没有结果，或者我们想支持混合输出，继续解析 Markdown
  // 许多 AI 模型会输出 markdown 格式
  
  // 匹配 ```lang path 或 ```lang filename="path" 或 ```lang title="path"
  // 捕获组: 1=lang(可选), 2=属性/路径部分, 3=内容
  const codeBlockRegex = /```(\w+)?(?:\s+([^\n]+))?\n([\s\S]*?)```/g;
  
  while ((match = codeBlockRegex.exec(text)) !== null) {
    const [, lang, attributes, content] = match;
    let filePath = '';
    
    // 尝试从属性部分提取路径
    if (attributes) {
      // 尝试匹配 filename="path" 或 title="path"
      const attrMatch = attributes.match(/(?:filename|title)=["']([^"']+)["']/);
      if (attrMatch) {
        filePath = attrMatch[1];
      } else {
        // 尝试直接匹配路径 (如: src/app/page.tsx)
        // 排除常见的非路径字符，简单的启发式
        const possiblePath = attributes.trim();
        if (possiblePath && !possiblePath.includes('=') && (possiblePath.includes('/') || possiblePath.includes('.'))) {
          filePath = possiblePath;
        }
      }
    }
    
    // 如果属性中没找到路径，尝试从代码内容的第一行注释中提取
    if (!filePath) {
      const firstLine = content.trim().split('\n')[0];
      // 支持 // filename: path 或 <!-- filename: path -->
      const commentMatch = firstLine.match(/^(?:\/\/|<!--|#)\s*(?:filename:|file:)?\s*([^\s<]+)(?:-->)?$/i);
      if (commentMatch) {
        // 简单的验证：看起来像路径
        const p = commentMatch[1];
        if (p.includes('.') || p.includes('/')) {
          filePath = p;
        }
      }
    }

    // 如果仍然没有路径，但这是唯一的代码块且没有其他文件，可以给个默认名 (可选，这里暂不处理)
    // 或者如果是 debug 生成，可能就是简单的文件名
    if (!filePath && attributes) {
        // 最后的尝试：如果是 "filename:path" 格式 (旧逻辑)
        const parts = attributes.split(':');
        if (parts.length === 2) {
             filePath = parts[1].trim();
        } else if (attributes.includes('.') || attributes.includes('/')) {
             filePath = attributes.trim();
        }
    }

    if (filePath) {
      fileMap.set(filePath, {
        path: filePath,
        content: content.trim(),
        type: lang || getFileType(filePath),
        completed: true,
      });
    }
  }

  // 5. 检查未闭合的代码块
  if (!currentFile && !text.endsWith('```')) {
    const openBlockMatch = text.match(/```(\w+)?(?:\s+([^\n]+))?\n([\s\S]*)$/);
    if (openBlockMatch) {
      const [, lang, attributes, content] = openBlockMatch;
      let filePath = '';
      
      // 同样的提取逻辑...
      if (attributes) {
        const attrMatch = attributes.match(/(?:filename|title)=["']([^"']+)["']/);
        if (attrMatch) {
          filePath = attrMatch[1];
        } else {
          const possiblePath = attributes.trim();
          if (possiblePath && !possiblePath.includes('=') && (possiblePath.includes('/') || possiblePath.includes('.'))) {
            filePath = possiblePath;
          }
        }
      }

      if (!filePath && content) {
          const firstLine = content.trim().split('\n')[0];
          const commentMatch = firstLine.match(/^(?:\/\/|<!--|#)\s*(?:filename:|file:)?\s*([^\s<]+)(?:-->)?$/i);
          if (commentMatch) {
              const p = commentMatch[1];
              if (p.includes('.') || p.includes('/')) {
                  filePath = p;
              }
          }
      }

      if (filePath && !fileMap.has(filePath)) {
        currentFile = {
          path: filePath,
          content: content.trim(),
          type: lang || getFileType(filePath),
          completed: false,
        };
      }
    }
  }

  // 将Map转换为数组
  const files = Array.from(fileMap.values());

  return { files, currentFile };
}

/**
 * 合并生成文件列表
 *
 * 是什么：在已有文件列表的基础上合并新的文件结果。
 * 做什么：保留未改动文件，覆盖同路径文件，并把新增文件追加到末尾。
 * 为什么：迭代修复通常只返回少量文件，直接替换会造成文件丢失与白屏。
 */
export function mergeGeneratedFiles<T extends GeneratedFile>(previous: T[], next: T[]): T[] {
  const prevList = Array.isArray(previous) ? previous : [];
  const nextList = Array.isArray(next) ? next : [];

  if (prevList.length === 0) {
    return [...nextList];
  }
  if (nextList.length === 0) {
    return [...prevList];
  }

  const merged = new Map<string, T>();
  const order: string[] = [];

  for (const file of prevList) {
    if (!file?.path) {
      continue;
    }
    if (!merged.has(file.path)) {
      order.push(file.path);
    }
    merged.set(file.path, file);
  }

  for (const file of nextList) {
    if (!file?.path) {
      continue;
    }
    if (!merged.has(file.path)) {
      order.push(file.path);
    }
    merged.set(file.path, file);
  }

  return order.map(path => merged.get(path)).filter(Boolean) as T[];
}
