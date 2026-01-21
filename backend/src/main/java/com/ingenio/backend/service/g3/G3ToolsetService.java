package com.ingenio.backend.service.g3;

import com.ingenio.backend.config.G3ToolsetProperties;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.mapper.g3.G3JobMapper;
import com.ingenio.backend.service.g3.hooks.G3HookContext;
import com.ingenio.backend.service.g3.hooks.G3HookEventType;
import com.ingenio.backend.service.g3.hooks.G3HookPipeline;
import com.ingenio.backend.service.g3.hooks.G3HookResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * G3 Toolset 服务（受控 Shell + 文件读取 + 搜索）。
 *
 * <p>目标：</p>
 * <ul>
 *   <li>为 Agent 提供可审计、可控的“探索能力”；</li>
 *   <li>默认只开放只读命令，避免写入或破坏性操作。</li>
 * </ul>
 */
@Service
public class G3ToolsetService {

    private static final Logger log = LoggerFactory.getLogger(G3ToolsetService.class);

    private static final Pattern DANGEROUS_TOKENS = Pattern.compile("[;&|><`]|\\$\\(");

    private final G3ToolsetProperties toolsetProperties;
    private final G3JobMapper jobMapper;
    private final G3SandboxService sandboxService;
    private final G3HookPipeline hookPipeline;

    public G3ToolsetService(
            G3ToolsetProperties toolsetProperties,
            G3JobMapper jobMapper,
            G3SandboxService sandboxService,
            G3HookPipeline hookPipeline) {
        this.toolsetProperties = toolsetProperties;
        this.jobMapper = jobMapper;
        this.sandboxService = sandboxService;
        this.hookPipeline = hookPipeline;
    }

    /**
     * 在沙箱内执行命令（只读/诊断）。
     *
     * @param jobId       任务ID
     * @param command     执行命令
     * @param timeoutSec  超时（秒）
     * @param logConsumer 日志回调
     * @return 执行结果
     */
    public ToolCommandResult runSandboxCommand(UUID jobId, String command, Integer timeoutSec,
            Consumer<G3LogEntry> logConsumer) {
        if (!toolsetProperties.isEnabled()) {
            return ToolCommandResult.failure("Toolset 已禁用");
        }
        if (jobId == null) {
            return ToolCommandResult.failure("jobId 不能为空");
        }
        if (command == null || command.isBlank()) {
            return ToolCommandResult.failure("command 不能为空");
        }

        G3JobEntity job = jobMapper.selectById(jobId);

        G3HookContext beforeContext = buildToolContext(
                job,
                "shell",
                command,
                G3HookEventType.BEFORE_TOOL,
                null,
                null,
                null,
                logConsumer
        );
        G3HookResult hookResult = hookPipeline.beforeTool(beforeContext);
        if (hookResult.isBlocked()) {
            G3HookContext afterContext = buildToolContext(
                    job,
                    "shell",
                    command,
                    G3HookEventType.AFTER_TOOL,
                    false,
                    null,
                    hookResult.getReason(),
                    logConsumer
            );
            hookPipeline.afterTool(afterContext, hookResult);
            return ToolCommandResult.failure(hookResult.getReason());
        }

        String rejectReason = getCommandRejectReason(command);
        if (rejectReason != null) {
            G3HookResult rejectResult = G3HookResult.block(rejectReason);
            G3HookContext afterContext = buildToolContext(
                    job,
                    "shell",
                    command,
                    G3HookEventType.AFTER_TOOL,
                    false,
                    null,
                    rejectReason,
                    logConsumer
            );
            hookPipeline.afterTool(afterContext, rejectResult);
            return ToolCommandResult.failure(rejectReason);
        }

        if (job == null || job.getSandboxId() == null || job.getSandboxId().isBlank()) {
            G3HookResult rejectResult = G3HookResult.block("任务未绑定沙箱，无法执行命令");
            G3HookContext afterContext = buildToolContext(
                    job,
                    "shell",
                    command,
                    G3HookEventType.AFTER_TOOL,
                    false,
                    null,
                    "任务未绑定沙箱，无法执行命令",
                    logConsumer
            );
            hookPipeline.afterTool(afterContext, rejectResult);
            return ToolCommandResult.failure("任务未绑定沙箱，无法执行命令");
        }

        int timeout = timeoutSec != null ? timeoutSec : toolsetProperties.getDefaultTimeoutSeconds();
        if (logConsumer != null) {
            logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR,
                    "Toolset 执行命令: " + command));
        }

        G3SandboxService.SandboxCommandResult result = sandboxService.executeCommand(
                job.getSandboxId(), command, timeout);

        String stdout = maskSensitive(result.stdout());
        String stderr = maskSensitive(result.stderr());

        ToolCommandResult response = ToolCommandResult.success(
                trimOutput(stdout),
                trimOutput(stderr),
                result.exitCode(),
                result.durationMs()
        );

        if (result.exitCode() != 0) {
            if (logConsumer != null) {
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.EXECUTOR,
                        "Toolset 命令执行失败: exitCode=" + result.exitCode()));
            }
        }

        G3HookContext afterContext = buildToolContext(
                job,
                "shell",
                command,
                G3HookEventType.AFTER_TOOL,
                result.exitCode() == 0,
                result.exitCode(),
                result.exitCode() == 0 ? null : "exitCode=" + result.exitCode(),
                logConsumer
        );
        hookPipeline.afterTool(afterContext, hookResult);

        return response;
    }

    /**
     * 读取工作区文件（受限于 workspaceRoot）。
     *
     * @param relativePath 相对路径
     * @param maxLines     最大行数
     * @return 读取结果
     */
    public FileReadResult readWorkspaceFile(String relativePath, Integer maxLines) {
        return readWorkspaceFile(null, relativePath, maxLines, null);
    }

    /**
     * 读取工作区文件（带任务上下文，用于审计）。
     *
     * @param job          任务实体（可为空）
     * @param relativePath 相对路径
     * @param maxLines     最大行数
     * @param logConsumer  日志回调
     * @return 读取结果
     */
    public FileReadResult readWorkspaceFile(G3JobEntity job, String relativePath, Integer maxLines,
            Consumer<G3LogEntry> logConsumer) {
        if (!toolsetProperties.isEnabled()) {
            return FileReadResult.failure("Toolset 已禁用");
        }
        if (relativePath == null || relativePath.isBlank()) {
            return FileReadResult.failure("路径不能为空");
        }

        G3HookContext beforeContext = buildToolContext(
                job,
                "read_file",
                relativePath,
                G3HookEventType.BEFORE_TOOL,
                null,
                null,
                null,
                logConsumer
        );
        G3HookResult hookResult = hookPipeline.beforeTool(beforeContext);
        if (hookResult.isBlocked()) {
            G3HookContext afterContext = buildToolContext(
                    job,
                    "read_file",
                    relativePath,
                    G3HookEventType.AFTER_TOOL,
                    false,
                    null,
                    hookResult.getReason(),
                    logConsumer
            );
            hookPipeline.afterTool(afterContext, hookResult);
            return FileReadResult.failure(hookResult.getReason());
        }

        FileReadResult result = readWorkspaceFileInternal(relativePath, maxLines);
        G3HookContext afterContext = buildToolContext(
                job,
                "read_file",
                relativePath,
                G3HookEventType.AFTER_TOOL,
                result.isSuccess(),
                result.isSuccess() ? 0 : 1,
                result.isSuccess() ? null : result.getMessage(),
                logConsumer
        );
        hookPipeline.afterTool(afterContext, hookResult);
        return result;
    }

    /**
     * 在工作区执行文本搜索（简单版）。
     *
     * @param query      关键字
     * @param maxMatches 最大匹配数
     * @return 搜索结果
     */
    public SearchResult searchWorkspace(String query, Integer maxMatches) {
        return searchWorkspace(null, query, maxMatches, null);
    }

    /**
     * 在工作区执行文本搜索（带任务上下文，用于审计）。
     *
     * @param job         任务实体（可为空）
     * @param query       关键字
     * @param maxMatches  最大匹配数
     * @param logConsumer 日志回调
     * @return 搜索结果
     */
    public SearchResult searchWorkspace(G3JobEntity job, String query, Integer maxMatches,
            Consumer<G3LogEntry> logConsumer) {
        if (!toolsetProperties.isEnabled()) {
            return SearchResult.failure("Toolset 已禁用");
        }
        if (query == null || query.isBlank()) {
            return SearchResult.failure("query 不能为空");
        }
        G3HookContext beforeContext = buildToolContext(
                job,
                "search_workspace",
                query,
                G3HookEventType.BEFORE_TOOL,
                null,
                null,
                null,
                logConsumer
        );
        G3HookResult hookResult = hookPipeline.beforeTool(beforeContext);
        if (hookResult.isBlocked()) {
            G3HookContext afterContext = buildToolContext(
                    job,
                    "search_workspace",
                    query,
                    G3HookEventType.AFTER_TOOL,
                    false,
                    null,
                    hookResult.getReason(),
                    logConsumer
            );
            hookPipeline.afterTool(afterContext, hookResult);
            return SearchResult.failure(hookResult.getReason());
        }

        SearchResult result = searchWorkspaceInternal(query, maxMatches);
        G3HookContext afterContext = buildToolContext(
                job,
                "search_workspace",
                query,
                G3HookEventType.AFTER_TOOL,
                result.isSuccess(),
                result.isSuccess() ? 0 : 1,
                result.isSuccess() ? null : result.getMessage(),
                logConsumer
        );
        hookPipeline.afterTool(afterContext, hookResult);
        return result;
    }

    /**
     * 批量读取工作区文件（简单版）。
     *
     * @param relativePaths 相对路径列表
     * @param maxLines      每个文件最大行数
     * @return 批量读取结果
     */
    public BatchFileReadResult readWorkspaceFiles(List<String> relativePaths, Integer maxLines) {
        return readWorkspaceFiles(relativePaths, maxLines, null, null);
    }

    /**
     * 批量读取工作区文件（带任务上下文，用于审计）。
     *
     * @param relativePaths 相对路径列表
     * @param maxLines      每个文件最大行数
     * @param job           任务实体（可为空）
     * @param logConsumer   日志回调
     * @return 批量读取结果
     */
    public BatchFileReadResult readWorkspaceFiles(List<String> relativePaths, Integer maxLines,
            G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        if (!toolsetProperties.isEnabled()) {
            return BatchFileReadResult.failure("Toolset 已禁用");
        }
        if (relativePaths == null || relativePaths.isEmpty()) {
            return BatchFileReadResult.failure("paths 不能为空");
        }

        String toolInput = "count=" + relativePaths.size();
        G3HookContext beforeContext = buildToolContext(
                job,
                "read_files",
                toolInput,
                G3HookEventType.BEFORE_TOOL,
                null,
                null,
                null,
                logConsumer
        );
        G3HookResult hookResult = hookPipeline.beforeTool(beforeContext);
        if (hookResult.isBlocked()) {
            G3HookContext afterContext = buildToolContext(
                    job,
                    "read_files",
                    toolInput,
                    G3HookEventType.AFTER_TOOL,
                    false,
                    null,
                    hookResult.getReason(),
                    logConsumer
            );
            hookPipeline.afterTool(afterContext, hookResult);
            return BatchFileReadResult.failure(hookResult.getReason());
        }

        BatchFileReadResult result = readWorkspaceFilesInternal(relativePaths, maxLines);
        G3HookContext afterContext = buildToolContext(
                job,
                "read_files",
                toolInput,
                G3HookEventType.AFTER_TOOL,
                result.isSuccess(),
                result.isSuccess() ? 0 : 1,
                result.isSuccess() ? null : result.getMessage(),
                logConsumer
        );
        hookPipeline.afterTool(afterContext, hookResult);
        return result;
    }

    /**
     * 批量读取并生成摘要（用于构建RAG上下文）。
     *
     * @param relativePaths   相对路径列表
     * @param maxLinesPerFile 每个文件最大行数
     * @param maxSummaryChars 摘要最大长度
     * @param job             任务实体（可为空）
     * @param logConsumer     日志回调
     * @return 摘要结果
     */
    public SummaryResult summarizeWorkspaceFiles(List<String> relativePaths, Integer maxLinesPerFile,
            Integer maxSummaryChars, G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        if (!toolsetProperties.isEnabled()) {
            return SummaryResult.failure("Toolset 已禁用");
        }
        if (relativePaths == null || relativePaths.isEmpty()) {
            return SummaryResult.failure("paths 不能为空");
        }

        String toolInput = "count=" + relativePaths.size();
        G3HookContext beforeContext = buildToolContext(
                job,
                "summarize_files",
                toolInput,
                G3HookEventType.BEFORE_TOOL,
                null,
                null,
                null,
                logConsumer
        );
        G3HookResult hookResult = hookPipeline.beforeTool(beforeContext);
        if (hookResult.isBlocked()) {
            G3HookContext afterContext = buildToolContext(
                    job,
                    "summarize_files",
                    toolInput,
                    G3HookEventType.AFTER_TOOL,
                    false,
                    null,
                    hookResult.getReason(),
                    logConsumer
            );
            hookPipeline.afterTool(afterContext, hookResult);
            return SummaryResult.failure(hookResult.getReason());
        }

        BatchFileReadResult readResult = readWorkspaceFilesInternal(relativePaths, maxLinesPerFile);
        SummaryResult summaryResult = summarizeWorkspaceFilesInternal(readResult, maxSummaryChars);
        G3HookContext afterContext = buildToolContext(
                job,
                "summarize_files",
                toolInput,
                G3HookEventType.AFTER_TOOL,
                summaryResult.isSuccess(),
                summaryResult.isSuccess() ? 0 : 1,
                summaryResult.isSuccess() ? null : summaryResult.getMessage(),
                logConsumer
        );
        hookPipeline.afterTool(afterContext, hookResult);
        return summaryResult;
    }

    /**
     * 读取工作区文件的核心逻辑（不触发 Hook）。
     */
    private FileReadResult readWorkspaceFileInternal(String relativePath, Integer maxLines) {
        Path root = resolveWorkspaceRoot();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            return FileReadResult.failure("路径超出工作区范围");
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return FileReadResult.failure("文件不存在: " + relativePath);
        }
        if (!isAllowedExtension(target)) {
            return FileReadResult.failure("文件类型不在允许范围内: " + relativePath);
        }
        if (isFileTooLarge(target, toolsetProperties.getMaxFileSizeBytes())) {
            return FileReadResult.failure("文件过大，已拒绝读取: " + relativePath);
        }

        int limit = maxLines != null ? maxLines : 200;
        try (BufferedReader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            List<String> lines = new ArrayList<>();
            String line;
            boolean truncated = false;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() >= limit) {
                    truncated = true;
                    break;
                }
            }
            if (truncated) {
                lines.add("...(已截断)");
            }
            return FileReadResult.success(String.join("\n", lines));
        } catch (IOException e) {
            return FileReadResult.failure("读取文件失败: " + e.getMessage());
        }
    }

    /**
     * 批量读取文件的核心逻辑（不触发 Hook）。
     */
    private BatchFileReadResult readWorkspaceFilesInternal(List<String> relativePaths, Integer maxLines) {
        int maxBatch = toolsetProperties.getMaxBatchFiles();
        int limit = maxBatch > 0 ? Math.min(relativePaths.size(), maxBatch) : relativePaths.size();
        List<FileReadItem> items = new ArrayList<>();
        int successCount = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < limit; i++) {
            String path = relativePaths.get(i);
            if (path == null || path.isBlank()) {
                continue;
            }
            if (!seen.add(path)) {
                continue;
            }
            FileReadResult single = readWorkspaceFileInternal(path, maxLines);
            if (single.isSuccess()) {
                successCount++;
            }
            items.add(new FileReadItem(path, single.isSuccess(), single.getContent(), single.getMessage()));
        }
        if (items.isEmpty()) {
            return BatchFileReadResult.failure("无可读取文件");
        }
        return BatchFileReadResult.success(items, successCount);
    }

    /**
     * 搜索工作区的核心逻辑（不触发 Hook）。
     */
    private SearchResult searchWorkspaceInternal(String query, Integer maxMatches) {
        Path root = resolveWorkspaceRoot();
        int limit = maxMatches != null ? maxMatches : 50;
        int maxScanFiles = toolsetProperties.getMaxSearchFiles();
        long maxFileSize = toolsetProperties.getMaxSearchFileSizeBytes();

        List<SearchMatch> matches = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            Iterator<Path> iterator = paths.iterator();
            int scanned = 0;
            while (iterator.hasNext()) {
                if (matches.size() >= limit || scanned >= maxScanFiles) {
                    break;
                }
                Path path = iterator.next();
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (isExcludedPath(path) || !isAllowedExtension(path)) {
                    continue;
                }
                scanned++;
                if (isFileTooLarge(path, maxFileSize)) {
                    continue;
                }
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String line;
                    int lineNumber = 0;
                    while ((line = reader.readLine()) != null) {
                        lineNumber++;
                        if (line.contains(query)) {
                            String relative = root.relativize(path).toString().replace("\\", "/");
                            matches.add(new SearchMatch(relative, lineNumber, line.trim()));
                            if (matches.size() >= limit) {
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    log.debug("Toolset 搜索跳过文件 {}: {}", path, e.getMessage());
                }
            }
        } catch (IOException e) {
            return SearchResult.failure("搜索失败: " + e.getMessage());
        }

        return SearchResult.success(matches);
    }

    /**
     * 多文件摘要生成（不触发 Hook）。
     */
    private SummaryResult summarizeWorkspaceFilesInternal(BatchFileReadResult readResult, Integer maxSummaryChars) {
        if (readResult == null || !readResult.isSuccess() || readResult.getItems() == null) {
            return SummaryResult.failure("读取结果为空，无法生成摘要");
        }
        int maxChars = maxSummaryChars != null ? maxSummaryChars : 2000;
        StringBuilder builder = new StringBuilder();
        builder.append("### 工作区摘要\n");
        int added = 0;
        for (FileReadItem item : readResult.getItems()) {
            if (!item.isSuccess()) {
                continue;
            }
            String summary = maskSensitive(summarizeContent(item.getPath(), item.getContent()));
            if (summary.isBlank()) {
                continue;
            }
            builder.append("#### ").append(item.getPath()).append("\n");
            builder.append(summary).append("\n");
            added++;
            if (builder.length() >= maxChars) {
                break;
            }
        }
        if (added == 0) {
            return SummaryResult.failure("未生成有效摘要");
        }
        String result = builder.length() > maxChars
                ? builder.substring(0, maxChars) + "...(已截断)"
                : builder.toString();
        return SummaryResult.success(result, added);
    }

    /**
     * 构建工具 Hook 上下文（统一注入任务与审计信息）。
     */
    private G3HookContext buildToolContext(
            G3JobEntity job,
            String toolName,
            String toolInput,
            G3HookEventType eventType,
            Boolean success,
            Integer exitCode,
            String errorMessage,
            Consumer<G3LogEntry> logConsumer) {
        return G3HookContext.builder()
                .eventType(eventType)
                .jobId(job != null ? job.getId() : null)
                .tenantId(job != null ? job.getTenantId() : null)
                .userId(job != null ? job.getUserId() : null)
                .toolName(toolName)
                .toolInput(toolInput)
                .success(success)
                .exitCode(exitCode)
                .errorMessage(errorMessage)
                .logConsumer(logConsumer)
                .build();
    }

    private String summarizeContent(String path, String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String lower = path != null ? path.toLowerCase() : "";
        List<String> lines = content.lines().toList();
        if (lower.endsWith(".java")) {
            return summarizeJava(lines);
        }
        if (lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".js")) {
            return summarizeTs(lines);
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return summarizeYaml(lines);
        }
        if (lower.endsWith(".xml")) {
            return summarizeXml(lines);
        }
        if (lower.endsWith(".properties")) {
            return summarizeProperties(lines);
        }
        if (lower.endsWith(".md")) {
            return summarizeMarkdown(lines);
        }
        return summarizeGeneric(lines);
    }

    private String summarizeJava(List<String> lines) {
        String pkg = lines.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("package "))
                .findFirst()
                .orElse("");
        List<String> declarations = lines.stream()
                .map(String::trim)
                .filter(line -> line.matches(".*\\b(class|interface|enum)\\s+\\w+.*"))
                .limit(5)
                .toList();
        StringBuilder builder = new StringBuilder();
        if (!pkg.isBlank()) {
            builder.append(pkg).append("\n");
        }
        if (!declarations.isEmpty()) {
            builder.append(String.join("\n", declarations));
        }
        if (builder.length() == 0) {
            return summarizeGeneric(lines);
        }
        return builder.toString();
    }

    private String summarizeTs(List<String> lines) {
        List<String> exports = lines.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("export "))
                .limit(6)
                .toList();
        if (!exports.isEmpty()) {
            return String.join("\n", exports);
        }
        return summarizeGeneric(lines);
    }

    private String summarizeYaml(List<String> lines) {
        List<String> keys = lines.stream()
                .filter(line -> !line.startsWith(" ") && line.contains(":"))
                .map(String::trim)
                .filter(line -> !line.startsWith("#"))
                .limit(8)
                .toList();
        if (!keys.isEmpty()) {
            return String.join("\n", keys);
        }
        return summarizeGeneric(lines);
    }

    private String summarizeXml(List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("<?xml")) {
                continue;
            }
            if (trimmed.startsWith("<") && !trimmed.startsWith("</")) {
                return trimmed;
            }
        }
        return summarizeGeneric(lines);
    }

    private String summarizeProperties(List<String> lines) {
        List<String> props = lines.stream()
                .map(String::trim)
                .filter(line -> !line.startsWith("#"))
                .filter(line -> line.contains("="))
                .limit(8)
                .toList();
        if (!props.isEmpty()) {
            return String.join("\n", props);
        }
        return summarizeGeneric(lines);
    }

    private String summarizeMarkdown(List<String> lines) {
        List<String> headings = lines.stream()
                .map(String::trim)
                .filter(line -> line.startsWith("#"))
                .limit(5)
                .toList();
        if (!headings.isEmpty()) {
            return String.join("\n", headings);
        }
        return summarizeGeneric(lines);
    }

    private String summarizeGeneric(List<String> lines) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .limit(5)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /**
     * 解析命令拒绝原因（返回 null 表示允许）。
     *
     * <p>用途：</p>
     * <ul>
     *   <li>统一 Toolset 的安全策略判断；</li>
     *   <li>返回可审计的拒绝原因，便于日志与前端提示。</li>
     * </ul>
     */
    private String getCommandRejectReason(String command) {
        if (DANGEROUS_TOKENS.matcher(command).find()) {
            return "命令包含危险符号";
        }

        String[] tokens = command.trim().split("\\s+");
        if (tokens.length == 0) {
            return "命令为空";
        }

        String base = tokens[0].trim();
        Set<String> deny = new HashSet<>(toolsetProperties.getDenyCommands());
        if (deny.contains(base)) {
            return "命令被策略拒绝: " + base;
        }

        Set<String> allow = new HashSet<>(toolsetProperties.getAllowCommands());
        if (!allow.contains(base)) {
            return "命令不在允许范围内: " + base;
        }

        return null;
    }

    /**
     * 判断文件扩展名是否允许（为空表示不限制）。
     */
    private boolean isAllowedExtension(Path path) {
        List<String> allowed = toolsetProperties.getAllowFileExtensions();
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        String name = path.getFileName().toString().toLowerCase();
        for (String ext : allowed) {
            if (name.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断路径是否命中排除片段（用于跳过缓存/构建目录）。
     */
    private boolean isExcludedPath(Path path) {
        List<String> excludes = toolsetProperties.getExcludePathContains();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        String normalized = path.toAbsolutePath().normalize().toString().replace("\\", "/");
        for (String fragment : excludes) {
            if (fragment == null || fragment.isBlank()) {
                continue;
            }
            String normalizedFragment = fragment.replace("\\", "/");
            if (normalized.contains(normalizedFragment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文件是否超过给定大小阈值。
     */
    private boolean isFileTooLarge(Path path, long maxBytes) {
        if (maxBytes <= 0) {
            return false;
        }
        try {
            return Files.size(path) > maxBytes;
        } catch (IOException e) {
            return true;
        }
    }

    private String trimOutput(String output) {
        if (output == null) {
            return "";
        }
        int maxChars = toolsetProperties.getMaxOutputChars();
        int maxLines = toolsetProperties.getMaxOutputLines();

        String trimmed = output.length() > maxChars ? output.substring(0, maxChars) + "\n...(已截断)\n" : output;

        try (BufferedReader reader = new BufferedReader(new StringReader(trimmed))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() >= maxLines) {
                    lines.add("...(已截断)");
                    break;
                }
            }
            return String.join("\n", lines);
        } catch (IOException e) {
            return trimmed;
        }
    }

    private String maskSensitive(String output) {
        if (output == null) {
            return "";
        }
        String masked = output;
        masked = masked.replaceAll("(?i)(authorization\\s*:\\s*)([^\\n\\r]+)", "$1***");
        masked = masked.replaceAll("(?i)(bearer\\s+)([A-Za-z0-9._-]+)", "$1***");
        masked = masked.replaceAll("sk-[A-Za-z0-9]{16,}", "sk-***");
        masked = masked.replaceAll("(?i)(password|secret|token)\\s*[:=]\\s*([^\\s]+)", "$1=***");
        return masked;
    }

    private Path resolveWorkspaceRoot() {
        String configured = toolsetProperties.getWorkspaceRoot();
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).normalize();
        }
        return Paths.get(System.getProperty("user.dir")).normalize();
    }

    /**
     * Toolset 命令执行结果。
     */
    public static class ToolCommandResult {
        private boolean success;
        private String stdout;
        private String stderr;
        private int exitCode;
        private int durationMs;
        private String message;
        /**
         * 工具执行策略决策（ALLOW/BLOCK）。
         */
        private String policyDecision;
        /**
         * 工具执行策略说明。
         */
        private String policyReason;

        public static ToolCommandResult success(String stdout, String stderr, int exitCode, int durationMs) {
            ToolCommandResult result = new ToolCommandResult();
            result.success = true;
            result.stdout = stdout;
            result.stderr = stderr;
            result.exitCode = exitCode;
            result.durationMs = durationMs;
            result.message = "ok";
            result.policyDecision = "ALLOW";
            result.policyReason = "ok";
            return result;
        }

        public static ToolCommandResult failure(String message) {
            ToolCommandResult result = new ToolCommandResult();
            result.success = false;
            result.message = message;
            result.policyDecision = "BLOCK";
            result.policyReason = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public int getDurationMs() {
            return durationMs;
        }

        public String getMessage() {
            return message;
        }

        public String getPolicyDecision() {
            return policyDecision;
        }

        public String getPolicyReason() {
            return policyReason;
        }
    }

    /**
     * 文件读取结果。
     */
    public static class FileReadResult {
        private boolean success;
        private String content;
        private String message;

        public static FileReadResult success(String content) {
            FileReadResult result = new FileReadResult();
            result.success = true;
            result.content = content;
            result.message = "ok";
            return result;
        }

        public static FileReadResult failure(String message) {
            FileReadResult result = new FileReadResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getContent() {
            return content;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 搜索匹配条目。
     */
    public static class SearchMatch {
        private String filePath;
        private int lineNumber;
        private String line;

        public SearchMatch(String filePath, int lineNumber, String line) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.line = line;
        }

        public String getFilePath() {
            return filePath;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getLine() {
            return line;
        }
    }

    /**
     * 搜索结果。
     */
    public static class SearchResult {
        private boolean success;
        private List<SearchMatch> matches;
        private String message;

        public static SearchResult success(List<SearchMatch> matches) {
            SearchResult result = new SearchResult();
            result.success = true;
            result.matches = matches;
            result.message = "ok";
            return result;
        }

        public static SearchResult failure(String message) {
            SearchResult result = new SearchResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<SearchMatch> getMatches() {
            return matches;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 批量读取条目。
     */
    public static class FileReadItem {
        private String path;
        private boolean success;
        private String content;
        private String message;

        public FileReadItem(String path, boolean success, String content, String message) {
            this.path = path;
            this.success = success;
            this.content = content;
            this.message = message;
        }

        public String getPath() {
            return path;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getContent() {
            return content;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 批量读取结果。
     */
    public static class BatchFileReadResult {
        private boolean success;
        private List<FileReadItem> items;
        private int successCount;
        private String message;

        public static BatchFileReadResult success(List<FileReadItem> items, int successCount) {
            BatchFileReadResult result = new BatchFileReadResult();
            result.success = true;
            result.items = items;
            result.successCount = successCount;
            result.message = "ok";
            return result;
        }

        public static BatchFileReadResult failure(String message) {
            BatchFileReadResult result = new BatchFileReadResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<FileReadItem> getItems() {
            return items;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 多文件摘要结果。
     */
    public static class SummaryResult {
        private boolean success;
        private String summary;
        private int fileCount;
        private String message;

        public static SummaryResult success(String summary, int fileCount) {
            SummaryResult result = new SummaryResult();
            result.success = true;
            result.summary = summary;
            result.fileCount = fileCount;
            result.message = "ok";
            return result;
        }

        public static SummaryResult failure(String message) {
            SummaryResult result = new SummaryResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getSummary() {
            return summary;
        }

        public int getFileCount() {
            return fileCount;
        }

        public String getMessage() {
            return message;
        }
    }
}
