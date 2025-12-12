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

  // 1. 匹配 <file path="...">...</file> 格式
  // 使用 [\s\S]*? 非贪婪匹配内容
  const fileRegex = /<file\s+path="([^"]+)">([\s\S]*?)<\/file>/g;
  let match;
  while ((match = fileRegex.exec(text)) !== null) {
    const [, path, content] = match;
    fileMap.set(path, {
      path,
      content: content.trim(),
      type: getFileType(path),
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
  // 匹配未闭合的标签
  const openTagMatch = text.match(/(?:<file\s+path="([^"]+)">|<boltAction\s+type="file"\s+filePath="([^"]+)">)([\s\S]*)$/);
  if (openTagMatch) {
    // path 可能在 index 1 或 2
    const path = openTagMatch[1] || openTagMatch[2];
    const content = openTagMatch[3];
    
    // 只有当该文件尚未被完整解析时，才作为 currentFile
    if (!fileMap.has(path)) {
      currentFile = {
        path,
        content: content.trim(),
        type: getFileType(path),
        completed: false,
      };
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
