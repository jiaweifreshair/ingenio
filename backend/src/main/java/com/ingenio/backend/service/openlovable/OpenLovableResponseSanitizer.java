package com.ingenio.backend.service.openlovable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenLovable AI 输出清洗器（用于 apply 阶段）
 *
 * <p>问题背景：
 * <ul>
 *   <li>OpenLovable-CN 在创建 E2B 沙箱时，会预置一套稳定的 Vite/React/Tailwind 基础工程（含 `package.json`、`vite.config.*` 等）。</li>
 *   <li>AI 有概率在输出中重复生成这些“基础配置文件”，并在 apply 时覆盖沙箱内的模板文件。</li>
 *   <li>一旦关键配置被覆盖（如端口不一致、allowedHosts 缺失、dev script 被改写），就会导致 Vite 无法在预览端口启动，前端 iframe 预览表现为白屏/空白。</li>
 * </ul>
 *
 * <p>处理策略：
 * <ul>
 *   <li>在写入沙箱前，移除 AI 输出中的“高风险配置文件” <file> 片段，保护沙箱模板的稳定性。</li>
 *   <li>该策略优先保障“可预览”，依赖安装由上游 OpenLovable 的 detect-and-install 逻辑负责。</li>
 * </ul>
 */
public final class OpenLovableResponseSanitizer {

    /**
     * 需要保护的高风险配置文件（以文件名维度判断，避免路径差异导致漏网）
     *
     * <p>说明：这些文件一旦被 AI 覆盖，极易导致：
     * <ul>
     *   <li>预览端口不匹配（Closed Port / no service running）</li>
     *   <li>Host 校验失败（Blocked request / host not allowed）</li>
     *   <li>dev 脚本缺失导致重启失败（npm run dev 无效）</li>
     * </ul>
     */
    private static final Set<String> BLOCKED_FILENAMES = Set.of(
            "package.json",
            "package-lock.json",
            "pnpm-lock.yaml",
            "yarn.lock",
            "vite.config.js",
            "vite.config.ts",
            "vite.config.mjs",
            "vite.config.cjs",
            "tailwind.config.js",
            "tailwind.config.ts",
            "postcss.config.js",
            "postcss.config.cjs",
            "tsconfig.json",
            "tsconfig.app.json",
            "tsconfig.node.json"
    );

    /**
     * 匹配 AI 输出的 <file> 片段：
     * - 支持单引号/双引号
     * - 容错：缺少 </file> 时，允许匹配到文本末尾（避免截断导致无法识别）
     */
    private static final Pattern FILE_BLOCK_PATTERN = Pattern.compile(
            "(<file\\s+path=['\"]([^'\"]+)['\"][^>]*>)([\\s\\S]*?)(</file>|$)",
            Pattern.CASE_INSENSITIVE
    );

    private OpenLovableResponseSanitizer() {
        // 工具类禁止实例化
    }

    /**
     * 清洗用于 apply 的 AI 输出，移除高风险配置文件片段。
     *
     * @param response AI 原始输出（包含多个 <file> 片段）
     * @return 清洗结果（包含清洗后的输出与被移除的文件路径列表）
     */
    public static SanitizeResult sanitizeForSandboxApply(String response) {
        if (response == null || response.isBlank()) {
            return new SanitizeResult(response, List.of());
        }

        Matcher matcher = FILE_BLOCK_PATTERN.matcher(response);
        StringBuffer buffer = new StringBuffer();
        List<String> removedPaths = new ArrayList<>();

        while (matcher.find()) {
            String originalPath = matcher.group(2);
            String normalizedPath = normalizePath(originalPath);

            if (shouldBlock(normalizedPath)) {
                removedPaths.add(normalizedPath);
                matcher.appendReplacement(buffer, "");
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
            }
        }

        matcher.appendTail(buffer);

        if (removedPaths.isEmpty()) {
            return new SanitizeResult(response, List.of());
        }

        return new SanitizeResult(buffer.toString(), List.copyOf(removedPaths));
    }

    private static boolean shouldBlock(String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return false;
        }

        String fileName = normalizedPath;
        int lastSlash = normalizedPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash + 1 < normalizedPath.length()) {
            fileName = normalizedPath.substring(lastSlash + 1);
        }

        String lower = fileName.toLowerCase(Locale.ROOT);

        // 环境变量文件（禁止写入/禁止入库）
        if (lower.startsWith(".env")) {
            return true;
        }

        return BLOCKED_FILENAMES.contains(lower);
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null) {
            return "";
        }

        String trimmed = rawPath.trim();
        // 统一分隔符，避免 Windows 路径导致判断失败
        String normalized = trimmed.replace('\\', '/');
        // 去掉常见前缀
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        return normalized;
    }

    /**
     * 清洗结果
     * @param sanitizedResponse 清洗后的输出
     * @param removedPaths 被移除的文件路径（已规范化）
     */
    public record SanitizeResult(String sanitizedResponse, List<String> removedPaths) {}
}

