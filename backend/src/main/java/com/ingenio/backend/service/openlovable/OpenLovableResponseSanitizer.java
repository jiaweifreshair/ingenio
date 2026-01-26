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

        Matcher matcher = FILE_BLOCK_PATTERN.matcher(response);
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
                String mergedContent = mergePackageJson(content);
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
                String correctedPath = correctJsxExtension(normalizedPath, content);
                if (!correctedPath.equals(normalizedPath)) {
                    String correctedOpenTag = openTag.replace(originalPath, correctedPath);
                    String replacement = correctedOpenTag + content + closeTag;
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
                    log.info("JSX扩展名修正: {} -> {}", normalizedPath, correctedPath);
                } else {
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
                }
            }
        }

        matcher.appendTail(buffer);

        return new SanitizeResult(buffer.toString(), List.copyOf(removedPaths), List.copyOf(mergedPaths),
                List.copyOf(truncatedPaths));
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

            if (orderedBlocks.containsKey(normalizedPath)) {
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
