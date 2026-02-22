package com.ingenio.backend.service.openlovable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * OpenLovable AI 输出清洗器（用于 apply 阶段）
 *
 * <p>
 * 处理策略（V2.0 智能合并）：
 * <ul>
 * <li>对于 package.json：提取AI生成的依赖，与沙箱模板合并，保留模板的scripts/devDependencies等关键配置</li>
 * <li>对于其他高风险配置文件：仍然完全过滤，保护沙箱模板的稳定性</li>
 * </ul>
 */
public final class OpenLovableResponseSanitizer {

    private static final Logger log = LoggerFactory.getLogger(OpenLovableResponseSanitizer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 需要完全过滤的高风险配置文件（不包括package.json，它会被智能合并）
     */
    private static final Set<String> BLOCKED_FILENAMES = Set.of(
            "package-lock.json",
            "pnpm-lock.yaml",
            "yarn.lock");

    /**
     * 沙箱模板的 package.json 基础配置
     */
    private static final String SANDBOX_TEMPLATE_PACKAGE_JSON = """
            {
              "name": "vite-react-typescript-starter",
              "private": true,
              "version": "0.0.0",
              "type": "module",
              "scripts": {
                "dev": "vite --host 0.0.0.0 --port 5173",
                "build": "tsc -b && vite build",
                "lint": "eslint .",
                "preview": "vite preview"
              },
              "dependencies": {
                "react": "^18.3.1",
                "react-dom": "^18.3.1"
              },
              "devDependencies": {
                "@eslint/js": "^9.13.0",
                "@types/react": "^18.3.12",
                "@types/react-dom": "^18.3.1",
                "@vitejs/plugin-react": "^4.3.3",
                "autoprefixer": "^10.4.18",
                "eslint": "^9.13.0",
                "eslint-plugin-react-hooks": "^5.0.0",
                "eslint-plugin-react-refresh": "^0.4.14",
                "globals": "^15.11.0",
                "postcss": "^8.4.35",
                "tailwindcss": "^3.4.1",
                "typescript": "~5.6.2",
                "typescript-eslint": "^8.11.0",
                "vite": "^5.4.10"
              }
            }
            """;

    private static final Pattern FILE_BLOCK_PATTERN = Pattern.compile(
            "(<file\\s+path=['\"]([^'\"]+)['\"][^>]*>)([\\s\\S]*?)(</file>|$)",
            Pattern.CASE_INSENSITIVE);

    /**
     * lucide-react CommonJS 引用匹配（解构形式）。
     *
     * 是什么：匹配 `const { Icon } = require('lucide-react')` 格式。
     * 做什么：用于替换为 ESM import 语句。
     * 为什么：Vite ESM 环境中 require 会导致运行时错误。
     */
    private static final Pattern LUCIDE_REQUIRE_DESTRUCTURED_PATTERN = Pattern.compile(
            "^(\\s*)(const|let|var)\\s*\\{([^}]+)\\}\\s*=\\s*require\\(['\"]lucide-react['\"]\\);?\\s*$",
            Pattern.MULTILINE);

    /**
     * lucide-react CommonJS 引用匹配（命名空间形式）。
     *
     * 是什么：匹配 `const Icons = require('lucide-react')` 格式。
     * 做什么：用于替换为 `import * as Icons` 的写法。
     * 为什么：避免 CommonJS require 触发 ESM 环境崩溃。
     */
    private static final Pattern LUCIDE_REQUIRE_NAMESPACE_PATTERN = Pattern.compile(
            "^(\\s*)(const|let|var)\\s+(\\w+)\\s*=\\s*require\\(['\"]lucide-react['\"]\\);?\\s*$",
            Pattern.MULTILINE);

    /**
     * lucide-react ESM import 检测。
     *
     * 是什么：匹配任意 import ... from 'lucide-react'。
     * 做什么：用于判断是否已存在 ESM 导入。
     * 为什么：已存在 import 时无需重复添加。
     */
    private static final Pattern LUCIDE_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+.*\\s+from\\s+['\"]lucide-react['\"];?\\s*$",
            Pattern.MULTILINE);

    /**
     * lucide-react ESM 解构导入匹配。
     *
     * 是什么：匹配 `import { Icon } from 'lucide-react'` 格式。
     * 做什么：用于移除误导入的 React Hook，避免重复声明。
     * 为什么：避免 Vite/Babel 报 "Identifier has already been declared"。
     */
    private static final Pattern LUCIDE_NAMED_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s*\\{([^}]+)\\}\\s*from\\s+['\"]lucide-react['\"];?\\s*$",
            Pattern.MULTILINE);

    /**
     * React ESM import 匹配。
     *
     * 是什么：匹配 `import React, { useState } from 'react'` 等格式。
     * 做什么：用于补齐 Hook 导入。
     * 为什么：确保从 lucide-react 移除 Hook 后仍能正常使用。
     */
    private static final Pattern REACT_IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+([^;]+)\\s+from\\s+['\"]react['\"];?\\s*$",
            Pattern.MULTILINE);

    /**
     * React Hook 名称集合。
     *
     * 是什么：常用 React Hook 的白名单。
     * 做什么：用于剔除 lucide-react 导入中的 Hook。
     * 为什么：避免重复声明导致编译失败。
     */
    private static final Set<String> REACT_HOOK_NAMES = Set.of(
            "useState",
            "useEffect",
            "useMemo",
            "useCallback",
            "useRef",
            "useReducer",
            "useContext",
            "useLayoutEffect",
            "useId",
            "useTransition",
            "useDeferredValue",
            "useImperativeHandle",
            "useInsertionEffect",
            "useSyncExternalStore",
            "useDebugValue");

    /**
     * JSX 组件标签匹配。
     *
     * 是什么：匹配 JSX 中以大写字母开头的标签名。
     * 做什么：用于识别潜在的组件/图标引用。
     * 为什么：补齐 lucide-react 导入时需要知道使用了哪些组件。
     */
    private static final Pattern JSX_COMPONENT_PATTERN = Pattern.compile("<([A-Z][A-Za-z0-9_]*)\\b");

    /**
     * 本地组件定义匹配。
     *
     * 是什么：匹配 function/const/class 定义的组件名称。
     * 做什么：识别本地定义的组件，避免误加到 lucide-react 导入。
     * 为什么：只应补齐真正缺失的图标导入。
     */
    private static final Pattern LOCAL_COMPONENT_DECLARATION_PATTERN = Pattern.compile(
            "\\b(?:function|class|const)\\s+([A-Z][A-Za-z0-9_]*)\\b");

    /**
     * ESM import 语句匹配。
     *
     * 是什么：匹配 `import ... from '...'` 语句。
     * 做什么：用于提取已导入的标识符。
     * 为什么：避免将已导入的组件误加入 lucide-react。
     */
    private static final Pattern IMPORT_STATEMENT_PATTERN = Pattern.compile(
            "^\\s*import\\s+([^;]+?)\\s+from\\s+['\"][^'\"]+['\"];?\\s*$",
            Pattern.MULTILINE);

    /**
     * Tailwind @apply 规则匹配。
     *
     * 是什么：匹配 `@apply ...;` 语句。
     * 做什么：用于移除不存在的类（如 bg-subtle-noise）。
     * 为什么：避免 Tailwind 编译报错导致白屏。
     */
    private static final Pattern TAILWIND_APPLY_PATTERN = Pattern.compile("@apply\\s+([^;]+);");

    /**
     * JSX 保留组件名集合。
     *
     * 是什么：React 内置组件名称。
     * 做什么：用于过滤不应加入 lucide-react 的标识符。
     * 为什么：避免误将内置组件当作图标导入。
     */
    private static final Set<String> RESERVED_COMPONENT_NAMES = Set.of(
            "Fragment",
            "Suspense",
            "StrictMode",
            "Profiler");

    /**
     * Supabase 客户端创建语句匹配。
     *
     * 是什么：匹配 `const supabase = createClient(...)` 或 `export const supabase = createClient(...)`。
     * 做什么：用于注入环境变量存在性校验，避免运行时抛出 supabaseUrl 必填错误。
     * 为什么：Vite 预览中缺失变量时需要降级为 null，避免页面白屏。
     */
    private static final Pattern SUPABASE_CLIENT_DECLARATION_PATTERN = Pattern.compile(
            "^(\\s*)(export\\s+)?(const|let|var)\\s+(\\w+)\\s*=\\s*createClient\\(([^)]*)\\)\\s*;?\\s*$",
            Pattern.MULTILINE);

    private OpenLovableResponseSanitizer() {
    }

    /**
     * 清洗用于 apply 的 AI 输出
     * - package.json: 智能合并依赖
     * - 其他高风险文件: 完全过滤
     */
    public static SanitizeResult sanitizeForSandboxApply(String response) {
        if (response == null || response.isBlank()) {
            return new SanitizeResult(response, List.of(), List.of(), List.of());
        }

        String normalizedResponse = normalizeDuplicateScriptExtensions(response);
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(normalizedResponse);
        StringBuffer buffer = new StringBuffer();
        List<String> removedPaths = new ArrayList<>();
        List<String> mergedPaths = new ArrayList<>();
        List<String> truncatedPaths = new ArrayList<>();

        while (matcher.find()) {
            String openTag = matcher.group(1);
            String originalPath = matcher.group(2);
            String content = matcher.group(3);
            String closeTag = matcher.group(4);
            String normalizedPath = normalizePath(originalPath);
            String fileName = getFileName(normalizedPath);
            String normalizedContent = stripMarkdownCodeFence(content);

            if (isScriptFile(normalizedPath)) {
                normalizedContent = normalizeLucideReactImports(normalizedContent);
                normalizedContent = normalizeReactHookImports(normalizedContent);
                normalizedContent = ensureLucideIconImports(normalizedContent);
                normalizedContent = normalizeSupabaseClient(normalizedContent);
                normalizedContent = normalizeScenarioApiAliases(normalizedPath, normalizedContent);
            }
            if (isCssFile(normalizedPath)) {
                normalizedContent = normalizeTailwindNoiseApply(normalizedContent);
            }

            // 空脚本文件视为截断，避免写入空文件导致预览白屏
            if (isScriptFile(normalizedPath) && isBlankContent(normalizedContent)) {
                truncatedPaths.add(normalizedPath);
                log.warn("检测到空脚本文件，视为截断: {}", normalizedPath);
                matcher.appendReplacement(buffer, "");
                continue;
            }

            // 检测文件是否被截断
            if (isTruncated(content)) {
                truncatedPaths.add(normalizedPath);
                log.warn("检测到截断文件: {}", normalizedPath);
                // 不写入截断的文件
                matcher.appendReplacement(buffer, "");
                continue;
            }

            if ("package.json".equalsIgnoreCase(fileName)) {
                // 智能合并 package.json
                String mergedContent = mergePackageJson(normalizedContent);
                if (mergedContent != null) {
                    String replacement = openTag + mergedContent + closeTag;
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
                    mergedPaths.add(normalizedPath);
                    log.info("package.json 已智能合并，保留模板配置并添加AI生成的依赖");
                } else {
                    // 合并失败，保留原始内容
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                    log.warn("package.json 合并失败，保留AI原始内容");
                }
            } else if (shouldBlock(normalizedPath)) {
                removedPaths.add(normalizedPath);
                matcher.appendReplacement(buffer, "");
            } else {
                // 检测.js文件中的JSX语法，自动修正为.jsx扩展名
                String correctedPath = correctJsxExtension(normalizedPath, normalizedContent);
                if (!correctedPath.equals(normalizedPath)) {
                    String correctedOpenTag = openTag.replace(originalPath, correctedPath);
                    String replacement = correctedOpenTag + normalizedContent + closeTag;
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
                    log.info("JSX扩展名修正: {} -> {}", normalizedPath, correctedPath);
                } else {
                    String replacement = openTag + normalizedContent + closeTag;
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
                }
            }
        }

        matcher.appendTail(buffer);

        return new SanitizeResult(buffer.toString(), List.copyOf(removedPaths), List.copyOf(mergedPaths),
                List.copyOf(truncatedPaths));
    }

    /**
     * 处理脚本扩展名冲突（同一路径存在 .ts/.tsx 与 .js/.jsx 多版本）
     *
     * 是什么：对同一 basePath 的多种脚本扩展做去重。
     * 做什么：优先保留内容更完整的版本，内容相同则偏好 TypeScript。
     * 为什么：避免 Vite 按扩展名优先级加载到旧版本导致运行时错误。
     */
    private static String normalizeDuplicateScriptExtensions(String response) {
        List<FileBlock> blocks = extractFileBlocks(response);
        if (blocks.isEmpty()) {
            return response;
        }

        List<FileBlock> filtered = dedupeScriptExtensionConflicts(blocks);
        return buildResponseFromFileBlocks(filtered);
    }

    /**
     * 去重同一脚本路径的多扩展名版本
     *
     * 是什么：识别 src/App.tsx 与 src/App.jsx 等同路径冲突。
     * 做什么：按内容长度优先，长度一致时优先 TS/TSX。
     * 为什么：避免同路径多版本被 Vite 解析到非预期文件。
     */
    private static List<FileBlock> dedupeScriptExtensionConflicts(List<FileBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }

        Map<String, List<FileBlock>> scriptGroups = new LinkedHashMap<>();
        for (FileBlock block : blocks) {
            String path = block.normalizedPath();
            if (!isScriptFile(path) || isTypeDefinitionFile(path)) {
                continue;
            }
            String basePath = stripScriptExtension(path);
            scriptGroups.computeIfAbsent(basePath, key -> new ArrayList<>()).add(block);
        }

        Map<String, FileBlock> preferredByBasePath = new LinkedHashMap<>();
        for (Map.Entry<String, List<FileBlock>> entry : scriptGroups.entrySet()) {
            List<FileBlock> candidates = entry.getValue();
            if (candidates.size() <= 1) {
                preferredByBasePath.put(entry.getKey(), candidates.get(0));
                continue;
            }
            FileBlock preferred = pickPreferredScriptBlock(candidates);
            preferredByBasePath.put(entry.getKey(), preferred);
            log.info("检测到脚本扩展冲突，保留: {}，移除: {}",
                    preferred.normalizedPath(),
                    candidates.stream()
                            .map(FileBlock::normalizedPath)
                            .filter(path -> !path.equals(preferred.normalizedPath()))
                            .collect(Collectors.toList()));
        }

        List<FileBlock> filtered = new ArrayList<>();
        for (FileBlock block : blocks) {
            String path = block.normalizedPath();
            if (!isScriptFile(path) || isTypeDefinitionFile(path)) {
                filtered.add(block);
                continue;
            }
            String basePath = stripScriptExtension(path);
            FileBlock preferred = preferredByBasePath.get(basePath);
            if (preferred != null && preferred.normalizedPath().equals(path)) {
                filtered.add(block);
            }
        }

        return filtered;
    }

    /**
     * 选择最优脚本版本
     *
     * 是什么：在多扩展名候选中选出最可信的版本。
     * 做什么：内容更长优先，长度相同时优先 TS/TSX。
     * 为什么：尽量保留更完整的实现，减少迭代修复成本。
     */
    private static FileBlock pickPreferredScriptBlock(List<FileBlock> candidates) {
        FileBlock best = null;
        int bestLength = -1;
        int bestPriority = -1;

        for (FileBlock candidate : candidates) {
            int length = candidate.content() == null ? 0 : candidate.content().trim().length();
            int priority = getScriptExtensionPriority(candidate.normalizedPath());
            if (length > bestLength || (length == bestLength && priority > bestPriority)) {
                best = candidate;
                bestLength = length;
                bestPriority = priority;
            }
        }

        return best == null ? candidates.get(0) : best;
    }

    /**
     * 识别脚本文件的扩展优先级
     *
     * 是什么：不同扩展名的排序规则。
     * 做什么：在内容长度一致时提供稳定的选择顺序。
     * 为什么：避免出现不可预测的冲突处理结果。
     */
    private static int getScriptExtensionPriority(String filePath) {
        String extension = getScriptExtension(filePath);
        return switch (extension) {
            case ".tsx" -> 3;
            case ".ts" -> 2;
            case ".jsx" -> 1;
            case ".js" -> 0;
            default -> -1;
        };
    }

    /**
     * 获取脚本文件扩展名
     *
     * 是什么：提取 .ts/.tsx/.js/.jsx 后缀。
     * 做什么：用于扩展优先级与去重逻辑。
     * 为什么：保证扩展名判断逻辑集中且可维护。
     */
    private static String getScriptExtension(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        String lower = filePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".d.ts")) {
            return ".d.ts";
        }
        if (lower.endsWith(".tsx")) {
            return ".tsx";
        }
        if (lower.endsWith(".ts")) {
            return ".ts";
        }
        if (lower.endsWith(".jsx")) {
            return ".jsx";
        }
        if (lower.endsWith(".js")) {
            return ".js";
        }
        return "";
    }

    /**
     * 判断是否为类型定义文件
     *
     * 是什么：识别 .d.ts 类型声明文件。
     * 做什么：在脚本去重时跳过类型定义文件。
     * 为什么：类型文件不应参与“同名脚本版本”冲突处理。
     */
    private static boolean isTypeDefinitionFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        return filePath.toLowerCase(Locale.ROOT).endsWith(".d.ts");
    }

    /**
     * 获取脚本文件的 basePath
     *
     * 是什么：去除 .ts/.tsx/.js/.jsx 后缀得到统一路径。
     * 做什么：用于归并同路径的多扩展版本。
     * 为什么：便于执行统一去重策略。
     */
    private static String stripScriptExtension(String filePath) {
        String extension = getScriptExtension(filePath);
        if (extension.isBlank() || ".d.ts".equals(extension)) {
            return filePath;
        }
        return filePath.substring(0, filePath.length() - extension.length());
    }

    /**
     * 文件块解析结果
     *
     * 是什么：表示一次 <file ...>...</file> 解析出的路径与内容。
     * 做什么：用于剥离非文件文本、复用解析结果做校验/修复。
     * 为什么：避免重复正则解析导致的稳定性问题。
     */
    public record FileBlock(String normalizedPath, String rawPath, String openTag, String content, String closeTag) {
    }

    /**
     * 提取 AI 输出中的所有文件块
     *
     * 是什么：解析 <file path="...">...</file> 片段并结构化返回。
     * 做什么：供 apply 阶段构建"纯文件响应"与后续校验。
     * 为什么：避免非文件文本干扰上游解析与自动修复。
     */
    public static List<FileBlock> extractFileBlocks(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }

        Matcher matcher = FILE_BLOCK_PATTERN.matcher(response);
        Map<String, FileBlock> orderedBlocks = new LinkedHashMap<>();
        while (matcher.find()) {
            String openTag = matcher.group(1);
            String rawPath = matcher.group(2);
            String content = matcher.group(3);
            String closeTag = matcher.group(4);
            String normalizedPath = normalizePath(rawPath);

            if (normalizedPath.isBlank()) {
                continue;
            }

            String normalizedCloseTag = (closeTag == null || closeTag.isBlank()) ? "</file>" : closeTag;
            FileBlock block = new FileBlock(normalizedPath, rawPath, openTag, content, normalizedCloseTag);

            FileBlock existing = orderedBlocks.get(normalizedPath);
            if (existing != null) {
                boolean existingBlank = isBlankContent(existing.content());
                boolean newBlank = isBlankContent(content);
                if (newBlank && !existingBlank) {
                    continue;
                }
                orderedBlocks.remove(normalizedPath);
            }
            orderedBlocks.put(normalizedPath, block);
        }

        List<FileBlock> blocks = List.copyOf(orderedBlocks.values());

        // 检测未标记代码：如果没有文件块但响应看起来像代码，记录警告
        if (blocks.isEmpty() && looksLikeCode(response)) {
            log.warn("检测到未标记代码：AI返回了代码但没有使用 <file> 标签包裹。响应长度: {}, 前200字符: {}",
                    response.length(),
                    response.substring(0, Math.min(200, response.length())));
        }

        // 记录Prompt合规率指标
        log.info("OpenLovable响应分析: 文件数={}, 响应长度={}, 包含未标记代码={}",
                blocks.size(), response.length(), blocks.isEmpty() && looksLikeCode(response));

        return blocks;
    }

    /**
     * 检测响应是否看起来像代码
     *
     * 是什么：启发式检测方法，判断文本是否包含代码特征。
     * 做什么：检查常见的代码关键词和模式。
     * 为什么：用于识别AI返回了代码但没有使用文件标签的情况。
     */
    private static boolean looksLikeCode(String response) {
        if (response == null || response.isBlank()) {
            return false;
        }

        // 检查常见的代码关键词（JavaScript/TypeScript/React）
        boolean isJsCode = response.contains("import ") ||
                response.contains("export ") ||
                response.contains("function ") ||
                response.contains("const ") ||
                response.contains("class ") ||
                response.contains("interface ") ||
                response.contains("return (") ||
                response.contains("useState") ||
                response.contains("useEffect");

        // 检查 CSS 特征
        boolean isCssCode = response.contains("@tailwind") ||
                response.contains("@apply") ||
                response.contains("@import") ||
                response.contains("@keyframes") ||
                (response.contains("{") && response.contains("}") &&
                        (response.contains("color:") || response.contains("background:") ||
                                response.contains("margin:") || response.contains("padding:") ||
                                response.contains("display:") || response.contains("font-")));

        // 检查 HTML 特征
        boolean isHtmlCode = response.contains("<!DOCTYPE html>") ||
                response.contains("<html") ||
                response.contains("<head>") ||
                response.contains("<body>");

        // 检查 JSON 特征
        boolean isJsonCode = (response.trim().startsWith("{") && response.trim().endsWith("}") &&
                response.contains("\"")) ||
                (response.contains("\"dependencies\"") && response.contains("\"scripts\""));

        return isJsCode || isCssCode || isHtmlCode || isJsonCode;
    }

    /**
     * Markdown代码块匹配模式
     *
     * 是什么：匹配 ```lang filename="..." ... ``` 或 ```lang:path ... ``` 格式的代码块。
     * 做什么：提取语言、文件路径和代码内容。
     * 为什么：AI模型可能输出Markdown格式而非 <file> 标签格式。
     */
    private static final Pattern MARKDOWN_CODE_BLOCK_PATTERN = Pattern.compile(
            "```(\\w+)?(?:\\s+([^\\n]+))?\\n([\\s\\S]*?)```",
            Pattern.MULTILINE);

    /**
     * Bolt格式匹配模式
     *
     * 是什么：匹配 <boltAction type="file" filePath="...">...</boltAction> 格式。
     * 做什么：提取文件路径和代码内容。
     * 为什么：某些AI模型使用Bolt格式输出代码。
     */
    private static final Pattern BOLT_ACTION_PATTERN = Pattern.compile(
            "<boltAction\\s+type=\"file\"\\s+filePath=\"([^\"]+)\">(.*?)</boltAction>",
            Pattern.DOTALL);

    /**
     * 将AI输出转换为标准 <file> 格式
     *
     * 是什么：格式转换器，将各种AI输出格式统一转换为 <file path="...">...</file> 格式。
     * 做什么：
     *   1. 如果已有 <file> 标签，保持不变
     *   2. 检测并转换 Bolt 格式
     *   3. 检测并转换 Markdown 代码块格式
     *   4. 对于纯代码，尝试推断文件类型并包裹
     * 为什么：前端和部署流程都依赖 <file> 格式，需要统一输出格式。
     *
     * @param response AI原始输出
     * @return 转换后的响应（包含 <file> 标签）或原始响应（如果无法转换）
     */
    public static String convertToFileFormat(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }

        // 如果已经包含 <file 标签，不需要转换
        if (response.contains("<file")) {
            log.debug("响应已包含 <file> 标签，无需转换");
            return response;
        }

        StringBuilder converted = new StringBuilder();
        boolean hasConversion = false;

        // 1. 尝试转换 Bolt 格式
        String afterBolt = convertBoltFormat(response);
        if (!afterBolt.equals(response)) {
            log.info("已转换 Bolt 格式为 <file> 格式");
            response = afterBolt;
            hasConversion = true;
        }

        // 2. 如果转换后仍无 <file> 标签，尝试转换 Markdown 代码块
        if (!response.contains("<file")) {
            String afterMarkdown = convertMarkdownCodeBlocks(response);
            if (!afterMarkdown.equals(response)) {
                log.info("已转换 Markdown 代码块为 <file> 格式");
                response = afterMarkdown;
                hasConversion = true;
            }
        }

        // 3. 如果仍无 <file> 标签但看起来像代码，尝试包裹为 App.jsx
        if (!response.contains("<file") && looksLikeCode(response)) {
            String wrapped = wrapCodeAsFile(response);
            if (wrapped != null) {
                log.info("已将纯代码包裹为 <file> 格式");
                response = wrapped;
                hasConversion = true;
            }
        }

        if (hasConversion) {
            log.info("格式转换完成，响应长度: {} -> {}", response.length(), response.length());
        } else {
            log.debug("无需格式转换或转换失败");
        }

        return response;
    }

    /**
     * 转换 Bolt 格式为 <file> 格式
     */
    private static String convertBoltFormat(String response) {
        Matcher matcher = BOLT_ACTION_PATTERN.matcher(response);
        StringBuffer buffer = new StringBuffer();
        boolean found = false;

        while (matcher.find()) {
            found = true;
            String filePath = matcher.group(1);
            String content = matcher.group(2);
            String replacement = String.format("<file path=\"%s\">%s</file>", filePath, content);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }

        if (found) {
            matcher.appendTail(buffer);
            return buffer.toString();
        }
        return response;
    }

    /**
     * 转换 Markdown 代码块为 <file> 格式
     */
    private static String convertMarkdownCodeBlocks(String response) {
        Matcher matcher = MARKDOWN_CODE_BLOCK_PATTERN.matcher(response);
        StringBuilder result = new StringBuilder();
        boolean found = false;

        while (matcher.find()) {
            String lang = matcher.group(1);
            String attributes = matcher.group(2);
            String content = matcher.group(3);

            String filePath = extractFilePathFromMarkdownAttributes(lang, attributes, content);

            if (filePath != null && !filePath.isBlank()) {
                found = true;
                result.append(String.format("<file path=\"%s\">\n%s\n</file>\n\n", filePath, content.trim()));
            }
        }

        if (found) {
            return result.toString().trim();
        }
        return response;
    }

    /**
     * 从 Markdown 代码块属性中提取文件路径
     */
    private static String extractFilePathFromMarkdownAttributes(String lang, String attributes, String content) {
        // 尝试从属性中提取 filename="path" 或 title="path"
        if (attributes != null && !attributes.isBlank()) {
            Matcher attrMatcher = Pattern.compile("(?:filename|title)=[\"']([^\"']+)[\"']").matcher(attributes);
            if (attrMatcher.find()) {
                return attrMatcher.group(1);
            }

            // 尝试直接匹配路径 (如 src/App.jsx)
            String possiblePath = attributes.trim();
            if (!possiblePath.contains("=") && (possiblePath.contains("/") || possiblePath.contains("."))) {
                return possiblePath;
            }

            // 尝试 lang:path 格式
            if (possiblePath.contains(":")) {
                String[] parts = possiblePath.split(":", 2);
                if (parts.length == 2 && !parts[1].isBlank()) {
                    return parts[1].trim();
                }
            }
        }

        // 尝试从代码内容第一行注释中提取
        if (content != null && !content.isBlank()) {
            String firstLine = content.trim().split("\\n")[0];
            Matcher commentMatcher = Pattern.compile("^(?://|<!--|#)\\s*(?:filename:|file:)?\\s*([^\\s<]+)(?:-->)?$", Pattern.CASE_INSENSITIVE)
                    .matcher(firstLine);
            if (commentMatcher.find()) {
                String path = commentMatcher.group(1);
                if (path.contains(".") || path.contains("/")) {
                    return path;
                }
            }
        }

        // 根据语言推断默认文件名
        if (lang != null) {
            return inferDefaultFilePath(lang);
        }

        return null;
    }

    /**
     * 根据语言推断默认文件路径
     */
    private static String inferDefaultFilePath(String lang) {
        if (lang == null) return null;
        return switch (lang.toLowerCase()) {
            case "jsx", "javascript" -> "src/App.jsx";
            case "tsx", "typescript" -> "src/App.tsx";
            case "css" -> "src/index.css";
            case "html" -> "index.html";
            case "json" -> "package.json";
            default -> null;
        };
    }

    /**
     * 将纯代码包裹为 <file> 格式
     *
     * 是什么：对于没有任何格式标记的纯代码，尝试推断文件类型并包裹。
     * 做什么：分析代码特征，确定最可能的文件路径。
     * 为什么：某些AI可能直接输出代码而不加任何标记。
     */
    private static String wrapCodeAsFile(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        // 检测代码类型
        String filePath = "src/App.jsx"; // 默认

        if (response.contains("@tailwind") || response.contains("@apply")) {
            filePath = "src/index.css";
        } else if (response.contains("<!DOCTYPE html>") || response.contains("<html")) {
            filePath = "index.html";
        } else if (response.contains("\"dependencies\"") && response.contains("\"scripts\"")) {
            filePath = "package.json";
        } else if (response.contains("ReactDOM.createRoot") || response.contains("createRoot(")) {
            filePath = "src/main.jsx";
        } else if (response.contains("export default function") || response.contains("function App")) {
            filePath = "src/App.jsx";
        }

        // 移除可能的 markdown 包裹符号
        String cleanCode = response.trim();
        if (cleanCode.startsWith("```")) {
            int firstNewline = cleanCode.indexOf('\n');
            if (firstNewline > 0) {
                cleanCode = cleanCode.substring(firstNewline + 1);
            }
        }
        if (cleanCode.endsWith("```")) {
            cleanCode = cleanCode.substring(0, cleanCode.length() - 3);
        }
        cleanCode = cleanCode.trim();

        if (cleanCode.isBlank()) {
            return null;
        }

        log.info("将纯代码包裹为文件: {}", filePath);
        return String.format("<file path=\"%s\">\n%s\n</file>", filePath, cleanCode);
    }

    /**
     * 使用文件块重建“纯文件”响应
     *
     * 是什么：仅保留 <file ...>... </file> 的拼接结果。
     * 做什么：剥离 AI 解释性文本，生成稳定的 apply 输入。
     * 为什么：避免上游将非文件文本误判为文件内容导致白屏。
     */
    public static String buildResponseFromFileBlocks(List<FileBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            FileBlock block = blocks.get(i);
            builder.append(block.openTag()).append(block.content()).append(block.closeTag());
            if (i < blocks.size() - 1) {
                builder.append("\n\n");
            }
        }

        return builder.toString();
    }

    /**
     * 合并已有文件与新增文件块
     *
     * 是什么：将增量修复输出与沙箱已有文件合并为完整列表。
     * 做什么：保留新文件内容并补齐缺失文件。
     * 为什么：避免 patch apply 覆盖导致文件丢失/白屏。
     */
    public static List<FileBlock> mergeWithExistingFiles(
            List<FileBlock> patchBlocks,
            Map<String, String> existingFiles) {
        if ((patchBlocks == null || patchBlocks.isEmpty())
                && (existingFiles == null || existingFiles.isEmpty())) {
            return List.of();
        }

        Map<String, FileBlock> merged = new LinkedHashMap<>();
        if (patchBlocks != null) {
            for (FileBlock block : patchBlocks) {
                if (block == null || block.normalizedPath() == null || block.normalizedPath().isBlank()) {
                    continue;
                }
                merged.put(block.normalizedPath(), block);
            }
        }

        if (existingFiles != null) {
            for (Map.Entry<String, String> entry : existingFiles.entrySet()) {
                String rawPath = entry.getKey();
                if (rawPath == null || rawPath.isBlank()) {
                    continue;
                }
                String normalizedPath = normalizePath(rawPath);
                if (normalizedPath.isBlank() || merged.containsKey(normalizedPath)) {
                    continue;
                }
                String content = entry.getValue() != null ? entry.getValue() : "";
                String openTag = "<file path=\"" + normalizedPath + "\">";
                merged.put(normalizedPath, new FileBlock(normalizedPath, rawPath, openTag, content, "</file>"));
            }
        }

        return List.copyOf(merged.values());
    }

    /**
     * 智能合并 package.json：保留模板配置，添加AI生成的新依赖
     */
    @SuppressWarnings("unchecked")
    private static String mergePackageJson(String aiContent) {
        try {
            Map<String, Object> template = objectMapper.readValue(
                    SANDBOX_TEMPLATE_PACKAGE_JSON,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });

            Map<String, Object> aiPackage = objectMapper.readValue(
                    aiContent.trim(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    });

            // 合并 dependencies
            Map<String, String> templateDeps = (Map<String, String>) template.getOrDefault("dependencies",
                    new LinkedHashMap<>());
            Map<String, String> aiDeps = (Map<String, String>) aiPackage.getOrDefault("dependencies",
                    new LinkedHashMap<>());

            Map<String, String> mergedDeps = new LinkedHashMap<>(templateDeps);
            for (Map.Entry<String, String> entry : aiDeps.entrySet()) {
                String pkg = entry.getKey();
                // 只添加模板中没有的新依赖
                if (!mergedDeps.containsKey(pkg)) {
                    mergedDeps.put(pkg, entry.getValue());
                    log.info("添加AI生成的依赖: {} -> {}", pkg, entry.getValue());
                }
            }
            template.put("dependencies", mergedDeps);

            // 合并 devDependencies（同样只添加新的）
            Map<String, String> templateDevDeps = (Map<String, String>) template.getOrDefault("devDependencies",
                    new LinkedHashMap<>());
            Map<String, String> aiDevDeps = (Map<String, String>) aiPackage.getOrDefault("devDependencies",
                    new LinkedHashMap<>());

            Map<String, String> mergedDevDeps = new LinkedHashMap<>(templateDevDeps);
            for (Map.Entry<String, String> entry : aiDevDeps.entrySet()) {
                String pkg = entry.getKey();
                if (!mergedDevDeps.containsKey(pkg)) {
                    mergedDevDeps.put(pkg, entry.getValue());
                    log.info("添加AI生成的devDependency: {} -> {}", pkg, entry.getValue());
                }
            }
            template.put("devDependencies", mergedDevDeps);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(template);
        } catch (Exception e) {
            log.error("合并 package.json 失败: {}", e.getMessage());
            return null;
        }
    }

    private static boolean shouldBlock(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return false;
        }

        String fileName = getFileName(normalizedPath);
        String lower = fileName.toLowerCase(Locale.ROOT);

        // 环境变量文件
        if (lower.startsWith(".env")) {
            return true;
        }

        return BLOCKED_FILENAMES.contains(lower);
    }

    private static String getFileName(String normalizedPath) {
        if (normalizedPath == null)
            return "";
        int lastSlash = normalizedPath.lastIndexOf('/');
        return (lastSlash >= 0 && lastSlash + 1 < normalizedPath.length())
                ? normalizedPath.substring(lastSlash + 1)
                : normalizedPath;
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null)
            return "";
        String normalized = rawPath.trim().replace('\\', '/');
        while (normalized.startsWith("./"))
            normalized = normalized.substring(2);
        while (normalized.startsWith("/"))
            normalized = normalized.substring(1);
        return normalized;
    }

    /**
     * 判断内容是否为空
     *
     * 是什么：统一的空内容判断工具。
     * 做什么：识别 null/空白内容。
     * 为什么：避免空文件覆盖有效代码。
     */
    private static boolean isBlankContent(String content) {
        return content == null || content.trim().isEmpty();
    }

    /**
     * 判断是否为脚本类文件
     *
     * 是什么：基于扩展名识别 JS/TS/JSX/TSX 文件。
     * 做什么：限定导入规范化的作用范围。
     * 为什么：避免对非脚本文件做无意义处理。
     */
    private static boolean isScriptFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String lower = filePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".ts") || lower.endsWith(".tsx");
    }

    /**
     * 判断是否为样式文件。
     *
     * 是什么：基于扩展名识别 CSS 文件。
     * 做什么：限定 Tailwind 规则修复的作用范围。
     * 为什么：避免对非样式文件做无意义处理。
     */
    private static boolean isCssFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        String lower = filePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".css");
    }

    /**
     * 规范化 lucide-react 的 CommonJS require 引用
     *
     * 是什么：将 `require('lucide-react')` 转成 ESM import。
     * 做什么：替换/移除 require 语句，保留图标导入语义。
     * 为什么：Vite ESM 环境不支持 require，运行时会报错。
     */
    private static String normalizeLucideReactImports(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        boolean hasImport = LUCIDE_IMPORT_PATTERN.matcher(content).find();
        String normalized = content;

        java.util.List<String> destructured = new java.util.ArrayList<>();
        Matcher destructuredMatcher = LUCIDE_REQUIRE_DESTRUCTURED_PATTERN.matcher(normalized);
        while (destructuredMatcher.find()) {
            String group = destructuredMatcher.group(3);
            if (group != null && !group.isBlank()) {
                destructured.add(group);
            }
        }

        if (!destructured.isEmpty()) {
            String merged = mergeLucideNames(destructured);
            String replacement = hasImport
                    ? ""
                    : "import { " + merged + " } from 'lucide-react';";
            normalized = LUCIDE_REQUIRE_DESTRUCTURED_PATTERN.matcher(normalized)
                    .replaceAll(Matcher.quoteReplacement(replacement));
            hasImport = true;
        }

        Matcher namespaceMatcher = LUCIDE_REQUIRE_NAMESPACE_PATTERN.matcher(normalized);
        if (namespaceMatcher.find()) {
            String namespace = namespaceMatcher.group(3);
            String replacement = hasImport
                    ? ""
                    : "import * as " + namespace + " from 'lucide-react';";
            normalized = LUCIDE_REQUIRE_NAMESPACE_PATTERN.matcher(normalized)
                    .replaceAll(Matcher.quoteReplacement(replacement));
        }

        return normalized;
    }

    /**
     * 清理 lucide-react 导入中误写的 React Hook，并补齐 react 导入。
     *
     * 是什么：修复 `import { useState } from 'lucide-react'` 的常见错误。
     * 做什么：从 lucide-react 导入中移除 Hook，并确保 react 导入包含它们。
     * 为什么：避免重复声明或非法导入导致编译失败。
     */
    private static String normalizeReactHookImports(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        Matcher matcher = LUCIDE_NAMED_IMPORT_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        List<String> removedHooks = new ArrayList<>();

        while (matcher.find()) {
            String importList = matcher.group(1);
            List<String> items = splitImportList(importList);
            List<String> kept = new ArrayList<>();
            for (String item : items) {
                String importName = readImportName(item);
                if (REACT_HOOK_NAMES.contains(importName)) {
                    removedHooks.add(importName);
                    continue;
                }
                kept.add(item);
            }

            String replacement = "";
            if (!kept.isEmpty()) {
                replacement = "import { " + String.join(", ", kept) + " } from 'lucide-react';";
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);

        if (removedHooks.isEmpty()) {
            return content;
        }

        Set<String> uniqueHooks = removedHooks.stream()
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return ensureReactHookImports(buffer.toString(), uniqueHooks);
    }

    /**
     * 补齐 lucide-react 缺失图标导入。
     *
     * 是什么：识别 JSX 中使用但未导入的图标组件。
     * 做什么：将缺失的图标追加到 lucide-react 的解构导入中。
     * 为什么：避免运行时出现 “Icon is not defined”。
     */
    private static String ensureLucideIconImports(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        Matcher lucideMatcher = LUCIDE_NAMED_IMPORT_PATTERN.matcher(content);
        if (!lucideMatcher.find()) {
            return content;
        }

        String importList = lucideMatcher.group(1);
        Set<String> lucideImports = collectNamedImportNames(importList);
        Set<String> usedComponents = collectJsxComponentNames(content);
        if (usedComponents.isEmpty()) {
            return content;
        }

        Set<String> declaredComponents = collectDeclaredComponentNames(content);
        Set<String> importedNames = collectImportedNames(content);
        Set<String> missingIcons = new java.util.LinkedHashSet<>();

        for (String name : usedComponents) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (RESERVED_COMPONENT_NAMES.contains(name)) {
                continue;
            }
            if (lucideImports.contains(name)) {
                continue;
            }
            if (declaredComponents.contains(name)) {
                continue;
            }
            if (importedNames.contains(name)) {
                continue;
            }
            missingIcons.add(name);
        }

        if (missingIcons.isEmpty()) {
            return content;
        }

        String merged = mergeLucideNames(java.util.List.of(importList, String.join(", ", missingIcons)));
        String replacement = "import { " + merged + " } from 'lucide-react';";

        Matcher replaceMatcher = LUCIDE_NAMED_IMPORT_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (replaceMatcher.find()) {
            if (!replaced) {
                replaceMatcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
                replaced = true;
            } else {
                replaceMatcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            }
        }
        replaceMatcher.appendTail(buffer);

        return buffer.toString();
    }

    /**
     * 提取 JSX 中出现的组件名称。
     *
     * 是什么：扫描 JSX 标签，收集以大写字母开头的组件名。
     * 做什么：构建候选集合供导入补齐使用。
     * 为什么：确定哪些组件在代码中被实际使用。
     */
    private static Set<String> collectJsxComponentNames(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        Matcher matcher = JSX_COMPONENT_PATTERN.matcher(content);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 提取本地定义的组件名称。
     *
     * 是什么：匹配 function/const/class 定义的组件名。
     * 做什么：生成本地组件集合，避免误加 lucide 导入。
     * 为什么：本地组件不应被当作图标处理。
     */
    private static Set<String> collectDeclaredComponentNames(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        Matcher matcher = LOCAL_COMPONENT_DECLARATION_PATTERN.matcher(content);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 提取 import 中的标识符名称。
     *
     * 是什么：解析 ESM import 语句。
     * 做什么：收集默认导入与命名导入，包含 alias。
     * 为什么：避免把已导入组件重复加入 lucide-react。
     */
    private static Set<String> collectImportedNames(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        Matcher matcher = IMPORT_STATEMENT_PATTERN.matcher(content);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            String clause = matcher.group(1);
            if (clause == null || clause.isBlank()) {
                continue;
            }
            String trimmed = clause.trim();
            String[] parts = trimmed.split(",", 2);
            String defaultPart = parts[0].trim();
            if (!defaultPart.startsWith("{") && !defaultPart.startsWith("* as") && !defaultPart.isBlank()) {
                names.add(defaultPart);
            }
            if (parts.length > 1) {
                names.addAll(collectNamedImportNames(parts[1]));
            } else if (defaultPart.startsWith("{")) {
                names.addAll(collectNamedImportNames(defaultPart));
            }
        }
        return names;
    }

    /**
     * 提取命名导入中的标识符名称。
     *
     * 是什么：解析 `{ A, B as C }` 形式的导入。
     * 做什么：同时收集原名与别名，避免误判未导入。
     * 为什么：别名导入也代表已有引用。
     */
    private static Set<String> collectNamedImportNames(String importClause) {
        if (importClause == null || importClause.isBlank()) {
            return Set.of();
        }
        String trimmed = importClause.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Set.of();
        }
        String list = trimmed.substring(start + 1, end);
        List<String> items = splitImportList(list);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String[] parts = item.split("\\s+as\\s+");
            String base = parts[0].trim();
            if (!base.isBlank()) {
                names.add(base);
            }
            if (parts.length > 1) {
                String alias = parts[1].trim();
                if (!alias.isBlank()) {
                    names.add(alias);
                }
            }
        }
        return names;
    }

    /**
     * 清理 Tailwind @apply 中不存在的噪点背景类。
     *
     * 是什么：移除 `bg-subtle-noise` 这类未定义的类名。
     * 做什么：在 @apply 语句中剔除异常 token。
     * 为什么：避免 Tailwind 编译报错导致预览白屏。
     */
    private static String normalizeTailwindNoiseApply(String content) {
        if (content == null || content.isBlank() || !content.contains("bg-subtle-noise")) {
            return content;
        }
        Matcher matcher = TAILWIND_APPLY_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String classes = matcher.group(1);
            if (classes == null || !classes.contains("bg-subtle-noise")) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            String normalizedClasses = java.util.Arrays.stream(classes.split("\\s+"))
                    .filter(token -> token != null && !token.isBlank())
                    .filter(token -> !"bg-subtle-noise".equals(token))
                    .collect(Collectors.joining(" "));
            if (normalizedClasses.isBlank()) {
                matcher.appendReplacement(buffer, "");
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement("@apply " + normalizedClasses + ";"));
            }
            replaced = true;
        }
        matcher.appendTail(buffer);
        return replaced ? buffer.toString() : content;
    }

    /**
     * 解析 import 列表（逗号分隔）。
     *
     * 是什么：将 `{ A, B as C }` 解析为条目列表。
     * 做什么：用于逐项过滤/补齐。
     * 为什么：便于剔除错误导入并保留原顺序。
     */
    private static List<String> splitImportList(String importList) {
        if (importList == null || importList.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(importList.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * 读取 import 条目的原始名称。
     *
     * 是什么：获取 `useState` 或 `useState as Hook` 的主名称。
     * 做什么：用于匹配 React Hook 白名单。
     * 为什么：避免 alias 干扰判断。
     */
    private static String readImportName(String item) {
        if (item == null) {
            return "";
        }
        String[] parts = item.split("\\s+as\\s+");
        return parts.length > 0 ? parts[0].trim() : item.trim();
    }

    /**
     * 确保 react 导入包含缺失的 Hook。
     *
     * 是什么：补齐 `import { useState } from 'react'`。
     * 做什么：若已存在 React 导入则合并，否则新增一行。
     * 为什么：移除 lucide-react 中的 Hook 后依然可用。
     */
    private static String ensureReactHookImports(String content, Set<String> hooks) {
        if (hooks == null || hooks.isEmpty()) {
            return content;
        }

        Matcher matcher = REACT_IMPORT_PATTERN.matcher(content);
        if (!matcher.find()) {
            String importLine = "import { " + String.join(", ", hooks) + " } from 'react';";
            int firstImportIndex = content.indexOf("import ");
            if (firstImportIndex >= 0) {
                return content.substring(0, firstImportIndex)
                        + importLine + "\n" + content.substring(firstImportIndex);
            }
            return importLine + "\n" + content;
        }

        String fullImport = matcher.group(0);
        String importClause = matcher.group(1).trim();

        boolean hasNamespace = importClause.contains("* as");
        boolean hasNamed = importClause.contains("{") && importClause.contains("}");

        if (hasNamespace && !hasNamed) {
            String addition = fullImport + "\nimport { " + String.join(", ", hooks) + " } from 'react';";
            return content.replace(fullImport, addition);
        }

        String defaultPart = "";
        String namedPart = "";
        if (hasNamed) {
            int start = importClause.indexOf('{');
            int end = importClause.lastIndexOf('}');
            namedPart = importClause.substring(start + 1, end);
            defaultPart = importClause.substring(0, start).trim();
            if (defaultPart.endsWith(",")) {
                defaultPart = defaultPart.substring(0, defaultPart.length() - 1).trim();
            }
        } else {
            defaultPart = importClause;
        }

        List<String> existingNamed = splitImportList(namedPart);
        Set<String> existingNames = existingNamed.stream()
                .map(OpenLovableResponseSanitizer::readImportName)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        boolean changed = false;
        for (String hook : hooks) {
            if (!existingNames.contains(hook)) {
                existingNamed.add(hook);
                changed = true;
            }
        }

        if (!changed) {
            return content;
        }

        StringBuilder newClause = new StringBuilder();
        if (!defaultPart.isBlank()) {
            newClause.append(defaultPart);
        }
        if (!existingNamed.isEmpty()) {
            if (newClause.length() > 0) {
                newClause.append(", ");
            }
            newClause.append("{ ").append(String.join(", ", existingNamed)).append(" }");
        }

        String newImport = "import " + newClause + " from 'react';";
        return content.replace(fullImport, newImport);
    }

    /**
     * 规范化 Supabase 环境变量与 createClient 守护逻辑。
     *
     * 是什么：将 Next.js 的 env 变量替换为 Vite 变量，并为 createClient 添加空值保护。
     * 做什么：把 `process.env.NEXT_PUBLIC_SUPABASE_*` 转为 `import.meta.env.VITE_SUPABASE_*`，并包裹 createClient。
     * 为什么：OpenLovable 预览在 Vite 环境下运行，缺失变量会直接报错导致预览失败。
     */
    private static String normalizeSupabaseClient(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String normalized = content
                .replace("process.env.NEXT_PUBLIC_SUPABASE_URL", "import.meta.env.VITE_SUPABASE_URL")
                .replace("process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY", "import.meta.env.VITE_SUPABASE_ANON_KEY");

        if (!normalized.contains("createClient")
                || !normalized.contains("supabaseUrl")
                || !normalized.contains("supabaseAnonKey")) {
            return normalized;
        }

        Matcher matcher = SUPABASE_CLIENT_DECLARATION_PATTERN.matcher(normalized);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String originalLine = matcher.group(0);
            String args = matcher.group(5);
            if (args == null || !args.contains("supabaseUrl") || !args.contains("supabaseAnonKey")) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(originalLine));
                continue;
            }
            if (originalLine.contains("?") && originalLine.contains("createClient")) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(originalLine));
                continue;
            }

            String indent = matcher.group(1) == null ? "" : matcher.group(1);
            String exportToken = matcher.group(2) == null ? "" : matcher.group(2);
            String keyword = matcher.group(3);
            String variableName = matcher.group(4);
            String replacement = indent + exportToken + keyword + " " + variableName
                    + " = (supabaseUrl && supabaseAnonKey) ? createClient(" + args.trim() + ") : null;";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            replaced = true;
        }
        matcher.appendTail(buffer);

        return replaced ? buffer.toString() : normalized;
    }

    /**
     * 移除文件内容外层的 Markdown 代码围栏。
     *
     * 是什么：发现内容以 ``` 开头并以 ``` 结束时，剥离外围围栏。
     * 做什么：清理 AI 偶发输出的 Markdown code fence，避免写入后导致模块解析错误。
     * 为什么：部分模型在 <file> 标签内仍会输出 ```jsx，导致运行时报错或导出缺失。
     */
    private static String stripMarkdownCodeFence(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return content;
        }

        int lastFenceIndex = trimmed.lastIndexOf("```");
        if (lastFenceIndex <= 0) {
            return content;
        }

        String trailing = trimmed.substring(lastFenceIndex + 3).trim();
        if (!trailing.isEmpty()) {
            return content;
        }

        String inner = trimmed.substring(0, lastFenceIndex);
        if (inner.startsWith("```")) {
            inner = inner.substring(3);
            int newlineIndex = inner.indexOf('\n');
            if (newlineIndex >= 0) {
                inner = inner.substring(newlineIndex + 1);
            } else {
                inner = inner.replaceFirst("^[a-zA-Z0-9_-]+\\s*", "");
            }
        }

        return inner.trim();
    }

    /**
     * 规范化场景 API 的 mock 别名。
     *
     * 是什么：为 src/services/api.* 中的 scanDevice/generateSolution 补齐 mockScan/mockGenerateSolution。
     * 做什么：当真实函数存在且 mock 别名缺失时自动注入导出语句。
     * 为什么：避免前端引用 mockScan 时报错导致预览中断。
     */
    private static String normalizeScenarioApiAliases(String normalizedPath, String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (!isScenarioApiFile(normalizedPath)) {
            return content;
        }

        boolean hasScanDevice = content.contains("scanDevice");
        boolean hasGenerateSolution = content.contains("generateSolution");
        boolean hasMockScan = content.contains("mockScan");
        boolean hasMockGenerateSolution = content.contains("mockGenerateSolution");

        if ((!hasScanDevice || hasMockScan) && (!hasGenerateSolution || hasMockGenerateSolution)) {
            return content;
        }

        StringBuilder builder = new StringBuilder(content);
        if (!content.endsWith("\n")) {
            builder.append("\n");
        }
        builder.append("\n");
        if (hasScanDevice && !hasMockScan) {
            builder.append("export const mockScan = scanDevice;\n");
        }
        if (hasGenerateSolution && !hasMockGenerateSolution) {
            builder.append("export const mockGenerateSolution = generateSolution;\n");
        }

        return builder.toString();
    }

    /**
     * 判断是否为场景 API 文件。
     *
     * 是什么：识别 src/services/api.* 作为场景 API 入口文件。
     * 做什么：控制 mock 别名注入只发生在目标文件中。
     * 为什么：避免对非目标脚本进行误修改。
     */
    private static boolean isScenarioApiFile(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return false;
        }
        String lower = normalizedPath.replace('\\', '/').toLowerCase(Locale.ROOT);
        return lower.equals("src/services/api.js")
                || lower.equals("src/services/api.jsx")
                || lower.equals("src/services/api.ts")
                || lower.equals("src/services/api.tsx");
    }

    /**
     * 合并 lucide-react 的解构导入列表
     *
     * 是什么：将多个解构片段合并成一个去重列表。
     * 做什么：输出稳定、无重复的 import 名称序列。
     * 为什么：避免生成重复 import 或丢失图标名称。
     */
    private static String mergeLucideNames(java.util.List<String> rawNames) {
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        for (String raw : rawNames) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] parts = raw.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    merged.add(trimmed);
                }
            }
        }
        return String.join(", ", merged);
    }

    /**
     * 检测.js文件中的JSX语法，自动修正为.jsx扩展名
     * 解决Vite无法解析.js文件中JSX语法的问题
     *
     * @param filePath 文件路径
     * @param content  文件内容
     * @return 修正后的文件路径（如果包含JSX则改为.jsx）
     */
    private static String correctJsxExtension(String filePath, String content) {
        // 只处理.js文件
        if (filePath == null || !filePath.endsWith(".js")) {
            return filePath;
        }

        // 检测JSX语法特征：<Component 或 </Component 或 <tag> 形式
        // 排除注释中的 < 和比较运算符
        if (containsJsxSyntax(content)) {
            return filePath.substring(0, filePath.length() - 3) + ".jsx";
        }

        return filePath;
    }

    /**
     * 检测内容是否包含JSX语法
     */
    private static boolean containsJsxSyntax(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        // JSX标签模式：<ComponentName 或 </ComponentName 或 <tag>
        // 匹配React组件标签（大写开头）或HTML标签
        Pattern jsxPattern = Pattern.compile(
                "</?[A-Z][a-zA-Z0-9]*[\\s/>]|" + // React组件: <Component> </Component>
                        "<[a-z]+[\\s/>]|" + // HTML标签: <div> <span>
                        "\\{[^}]*<[^>]+>[^}]*\\}" // JSX表达式: {<Component />}
        );

        return jsxPattern.matcher(content).find();
    }

    /**
     * 检测文件内容是否被截断
     *
     * 检测模式:
     * 1. 单独一行的省略号
     * 2. 行尾的注释省略号
     * 3. 注释块中的省略号
     * 4. 文件末尾的省略号
     * 5. JSX/HTML 标签不匹配（括号未闭合）
     * 6. JavaScript 括号不匹配（花括号/圆括号/方括号）
     */
    private static boolean isTruncated(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        String trimmed = content.trim();

        // 检测文件末尾的省略号
        if (trimmed.endsWith("...") || trimmed.endsWith("…")) {
            return true;
        }

        // 检测单独一行的省略号
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.equals("...") || trimmedLine.equals("…")) {
                return true;
            }
            // 检测行尾的注释省略号
            if (trimmedLine.endsWith("// ...") || trimmedLine.endsWith("// …")) {
                return true;
            }
            // 检测注释块省略号
            if (trimmedLine.contains("/* ... */") || trimmedLine.contains("/* … */")) {
                return true;
            }
        }

        // 检测 JSX/TSX 文件的括号匹配（V2.1 增强）
        if (isJsxOrTsxContent(content)) {
            if (!areBracketsBalanced(content)) {
                log.warn("检测到括号不匹配，可能是 JSX 截断");
                return true;
            }
        }

        return false;
    }

    /**
     * 检测内容是否是 JSX/TSX 代码
     */
    private static boolean isJsxOrTsxContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        // 检测 React 特征
        return content.contains("import React") ||
                content.contains("from 'react'") ||
                content.contains("from \"react\"") ||
                content.contains("useState") ||
                content.contains("useEffect") ||
                content.contains("export default function") ||
                content.contains("export function") ||
                containsJsxSyntax(content);
    }

    /**
     * 检测括号是否平衡（花括号/圆括号/方括号/JSX标签）
     * 
     * 注意：这是一个简化版检测，主要用于识别明显的截断问题
     * 不处理字符串内的括号（可能导致误判，但可以容忍）
     */
    private static boolean areBracketsBalanced(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }

        int curlyBraces = 0; // {}
        int parentheses = 0; // ()
        int brackets = 0; // []
        int angleBrackets = 0; // <> for JSX

        boolean inString = false;
        boolean inTemplate = false;
        char stringChar = 0;

        char[] chars = content.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char prev = i > 0 ? chars[i - 1] : 0;

            // 跳过转义字符
            if (prev == '\\') {
                continue;
            }

            // 字符串处理
            if (!inString && !inTemplate && (c == '"' || c == '\'')) {
                inString = true;
                stringChar = c;
                continue;
            }
            if (inString && c == stringChar) {
                inString = false;
                continue;
            }

            // 模板字符串处理
            if (!inString && !inTemplate && c == '`') {
                inTemplate = true;
                continue;
            }
            if (inTemplate && c == '`') {
                inTemplate = false;
                continue;
            }

            // 跳过字符串和模板内的内容
            if (inString || inTemplate) {
                continue;
            }

            // 计数括号
            switch (c) {
                case '{' -> curlyBraces++;
                case '}' -> curlyBraces--;
                case '(' -> parentheses++;
                case ')' -> parentheses--;
                case '[' -> brackets++;
                case ']' -> brackets--;
                case '<' -> {
                    // 只计算看起来像 JSX 标签的 <
                    if (i + 1 < chars.length) {
                        char next = chars[i + 1];
                        if (Character.isLetter(next) || next == '/' || next == '>') {
                            angleBrackets++;
                        }
                    }
                }
                case '>' -> {
                    // 减少 JSX 标签计数
                    if (angleBrackets > 0 && (prev == '/' || Character.isLetter(prev) || prev == '"' || prev == '\'')) {
                        angleBrackets--;
                    }
                }
            }

            // 早期退出：如果计数变成负数，说明有未匹配的闭合括号
            if (curlyBraces < 0 || parentheses < 0 || brackets < 0) {
                return false;
            }
        }

        // 检查是否所有括号都匹配
        // 对于花括号和圆括号，必须严格匹配
        // 对于 JSX 标签，允许一定的容差（因为简化版检测可能不准确）
        boolean basicBalanced = curlyBraces == 0 && parentheses == 0 && brackets == 0;

        // 如果基础括号不平衡，记录日志
        if (!basicBalanced) {
            log.debug("括号不平衡: curly={}, paren={}, bracket={}, angle={}",
                    curlyBraces, parentheses, brackets, angleBrackets);
        }

        return basicBalanced;
    }

    /**
     * 清洗结果
     * 
     * @param sanitizedResponse 清洗后的输出
     * @param removedPaths      被完全移除的文件路径
     * @param mergedPaths       被智能合并的文件路径
     * @param truncatedPaths    被截断的文件路径
     */
    public record SanitizeResult(String sanitizedResponse, List<String> removedPaths, List<String> mergedPaths,
            List<String> truncatedPaths) {
    }
}
