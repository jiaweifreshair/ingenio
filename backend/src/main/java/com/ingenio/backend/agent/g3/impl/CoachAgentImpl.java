package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3ErrorSignature;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3SessionMemory;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.g3.G3ContextBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 教练Agent实现
 * 负责分析编译/测试错误并生成修复代码
 *
 * 核心职责：
 * 1. 解析编译器错误输出（javac/maven错误格式）
 * 2. 识别错误类型（语法错误、类型错误、依赖缺失等）
 * 3. 生成修复后的代码版本
 * 4. 判断错误是否可自动修复
 *
 * Coach是G3引擎"自修复"能力的核心组件
 */
@Slf4j
@Component
public class CoachAgentImpl implements ICoachAgent {

    private static final String AGENT_NAME = "CoachAgent";

    private final AIProviderFactory aiProviderFactory;
    private final PromptTemplateService promptTemplateService;
    private final G3ContextBuilder contextBuilder;

    /**
     * 可自动修复的错误模式
     */
    private static final List<String> AUTO_FIXABLE_PATTERNS = List.of(
            "cannot find symbol", // 符号未定义
            "incompatible types", // 类型不兼容
            "method .* in .* cannot be applied", // 方法参数错误
            "package .* does not exist", // 包不存在
            "cannot access class", // 类访问问题
            "non-static .* cannot be referenced", // 静态引用错误
            "unreported exception", // 未处理异常
            "missing return statement", // 缺少返回语句
            ";' expected", // 缺少分号
            "class .* is public, should be declared", // 类声明问题
            "'\\)' expected", // 缺少右括号
            "'\\{' expected", // 缺少左花括号
            "illegal start of expression", // 表达式语法错误

            // Maven / 构建类错误（用于尝试修复 pom.xml）
            "could not resolve dependencies",
            "the following artifacts could not be resolved",
            "non-resolvable parent pom",
            "failed to read artifact descriptor",
            "could not find artifact",
            "could not transfer artifact",
            "failed to execute goal");

    /**
     * 无法自动修复的错误模式
     */
    private static final List<String> NON_FIXABLE_PATTERNS = List.of(
            "OutOfMemoryError", // 内存溢出
            "StackOverflowError", // 栈溢出
            "Could not find or load main class", // 主类问题
            "java.lang.UnsupportedClassVersionError", // JDK版本问题
            "Access denied", // 权限问题
            "connection refused",
            "operation not permitted",
            "unknown host",
            "timed out");

    public CoachAgentImpl(
            AIProviderFactory aiProviderFactory,
            PromptTemplateService promptTemplateService,
            G3ContextBuilder contextBuilder) {
        this.aiProviderFactory = aiProviderFactory;
        this.promptTemplateService = promptTemplateService;
        this.contextBuilder = contextBuilder;
    }

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public String getDescription() {
        return "教练Agent - 分析编译错误并生成修复代码";
    }

    @Override
    public List<G3ArtifactEntity> execute(G3JobEntity job, Consumer<G3LogEntry> logConsumer) throws G3AgentException {
        // Coach不直接执行，而是通过fix方法调用
        throw new G3AgentException(AGENT_NAME, getRole(), "Coach Agent应通过fix()方法调用");
    }

    @Override
    public CoachResult fix(
            G3JobEntity job,
            List<G3ArtifactEntity> errorArtifacts,
            List<G3ValidationResultEntity> validationResults,
            G3SessionMemory sessionMemory,
            Consumer<G3LogEntry> logConsumer) {

        if (errorArtifacts == null || errorArtifacts.isEmpty()) {
            return CoachResult.failure("没有需要修复的产物");
        }

        try {
            AIProvider aiProvider = aiProviderFactory.getProvider();
            if (!aiProvider.isAvailable()) {
                return CoachResult.failure("AI提供商不可用");
            }

            logConsumer.accept(G3LogEntry.info(getRole(), "开始分析 " + errorArtifacts.size() + " 个错误文件..."));

            // 构建“可用类索引”（来自 context.md），用于提升 import/类型修复的命中率
            String compactContext = buildSafeCompactContext(job, logConsumer);

            List<G3ArtifactEntity> fixedArtifacts = new ArrayList<>();
            StringBuilder analysisReport = new StringBuilder();
            int fixedCount = 0;
            int failedCount = 0;

            // 逐个修复错误文件
            // 注：compilerOutput 直接从 G3ArtifactEntity 获取，validationResults 用于获取全局编译信息

            // ========== 错误聚合分析（v2.1.0 新增）==========
            ErrorAggregation aggregation = analyzeErrorsAggregated(errorArtifacts, validationResults);
            logConsumer.accept(G3LogEntry.info(getRole(), String.format("错误分析: %d 个文件, %d 类错误, 核心问题: %s",
                    aggregation.fileCount(), aggregation.errorTypes().size(), aggregation.coreIssue())));

            // 构建修复历史上下文（v2.1.0 新增）
            String repairHistoryContext = sessionMemory != null ? sessionMemory.buildCoachContext() : "";

            // 按优先级排序产物
            List<G3ArtifactEntity> sortedArtifacts = prioritizeArtifacts(errorArtifacts, aggregation);

            for (G3ArtifactEntity artifact : sortedArtifacts) {
                // 保守的 pom.xml 兜底策略（v2.1.0 新增）
                if (shouldSkipPomFallback(artifact, sessionMemory)) {
                    logConsumer.accept(G3LogEntry.warn(getRole(),
                            "pom.xml 已修复过，跳过重复兜底修复"));
                    continue;
                }

                // 编译器输出直接从产物实体获取
                String compilerOutput = artifact.getCompilerOutput();
                if (compilerOutput == null || compilerOutput.isBlank()) {
                    // 如果产物没有编译输出，尝试从验证结果获取
                    compilerOutput = extractCompilerOutputFromValidation(validationResults, artifact.getFileName());
                }

                logConsumer.accept(G3LogEntry.info(getRole(), "正在修复: " + artifact.getFileName()));

                // 检查是否可自动修复
                if (!canAutoFix(compilerOutput)) {
                    logConsumer.accept(G3LogEntry.warn(getRole(), artifact.getFileName() + " 无法自动修复"));
                    analysisReport.append("【").append(artifact.getFileName()).append("】无法自动修复\n");
                    analysisReport.append(analyzeError(artifact, compilerOutput)).append("\n\n");
                    failedCount++;
                    continue;
                }

                // 执行修复
                try {
                    String fixedCode = generateFixWithHistory(artifact, compilerOutput, compactContext,
                            repairHistoryContext, aiProvider);

                    if (fixedCode != null && !fixedCode.isBlank()) {
                        G3ArtifactEntity fixedArtifact = createFixedArtifact(artifact, fixedCode);
                        fixedArtifacts.add(fixedArtifact);
                        fixedCount++;

                        logConsumer.accept(G3LogEntry.success(getRole(), artifact.getFileName() + " 修复完成"));
                        analysisReport.append("【").append(artifact.getFileName()).append("】修复成功\n\n");
                    } else {
                        failedCount++;
                        logConsumer.accept(G3LogEntry.warn(getRole(), artifact.getFileName() + " 修复失败"));
                        analysisReport.append("【").append(artifact.getFileName()).append("】修复失败 - 无法生成有效代码\n\n");
                    }

                } catch (Exception e) {
                    failedCount++;
                    log.error("[{}] 修复 {} 失败: {}", AGENT_NAME, artifact.getFileName(), e.getMessage());
                    logConsumer
                            .accept(G3LogEntry.error(getRole(), artifact.getFileName() + " 修复异常: " + e.getMessage()));
                    analysisReport.append("【").append(artifact.getFileName()).append("】修复异常: ").append(e.getMessage())
                            .append("\n\n");
                }
            }

            // 生成总结
            String summary = String.format("修复完成: 成功 %d 个, 失败 %d 个", fixedCount, failedCount);
            logConsumer.accept(G3LogEntry.info(getRole(), summary));
            analysisReport.insert(0, "=== 修复报告 ===\n" + summary + "\n\n");

            if (fixedArtifacts.isEmpty()) {
                return CoachResult.cannotFix(analysisReport.toString());
            }

            // === Planning with Files: Update Docs ===
            // 1. progress.md
            String progress = "\n\n## " + java.time.LocalDateTime.now() + " Coach Agent\n" +
                    "- Action: Fixed " + fixedCount + " files, Failed " + failedCount + "\n";
            fixedArtifacts.add(G3ArtifactEntity.create(job.getId(), "docs/progress.md", progress,
                    G3ArtifactEntity.GeneratedBy.COACH, 0));

            // 2. findings.md (Append analysis report)
            if (analysisReport.length() > 0) {
                String findings = "\n\n## Coach Findings\n" + analysisReport.toString();
                fixedArtifacts.add(G3ArtifactEntity.create(job.getId(), "docs/findings.md", findings,
                        G3ArtifactEntity.GeneratedBy.COACH, 0));
            }

            // 3. task_plan.md (Mark Verification as in-progress/done)
            String taskPlan = """
                    # Implementation Plan

                    - [x] Design Phase (Architect)
                    - [x] Coding Phase (Backend)
                    - [/] Verification Phase (Coach)
                    """;
            fixedArtifacts.add(G3ArtifactEntity.create(job.getId(), "docs/task_plan.md", taskPlan,
                    G3ArtifactEntity.GeneratedBy.COACH, 0));

            return CoachResult.success(fixedArtifacts, analysisReport.toString());

        } catch (Exception e) {
            log.error("[{}] 修复过程异常: {}", AGENT_NAME, e.getMessage(), e);
            logConsumer.accept(G3LogEntry.error(getRole(), "修复过程异常: " + e.getMessage()));
            return CoachResult.failure("修复过程异常: " + e.getMessage());
        }
    }

    @Override
    public String analyzeError(G3ArtifactEntity artifact, String compilerOutput) {
        try {
            AIProvider aiProvider = aiProviderFactory.getProvider();
            if (!aiProvider.isAvailable()) {
                return "AI提供商不可用，无法分析错误";
            }

            String prompt = String.format(promptTemplateService.coachAnalysisTemplate(), artifact.getFileName(),
                    compilerOutput);
            AIProvider.AIResponse response = aiProvider.generate(prompt,
                    AIProvider.AIRequest.builder()
                            .temperature(0.1)
                            .maxTokens(1000)
                            .build());

            return response.content();

        } catch (Exception e) {
            log.error("[{}] 错误分析失败: {}", AGENT_NAME, e.getMessage());
            return "错误分析失败: " + e.getMessage();
        }
    }

    @Override
    public boolean canAutoFix(String compilerOutput) {
        if (compilerOutput == null || compilerOutput.isBlank()) {
            return false;
        }

        String lowerOutput = compilerOutput.toLowerCase();

        // 检查是否包含不可修复的错误
        for (String pattern : NON_FIXABLE_PATTERNS) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(compilerOutput).find()) {
                log.debug("[{}] 检测到不可修复错误: {}", AGENT_NAME, pattern);
                return false;
            }
        }

        // 检查是否包含可修复的错误
        for (String pattern : AUTO_FIXABLE_PATTERNS) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(compilerOutput).find()) {
                log.debug("[{}] 检测到可修复错误: {}", AGENT_NAME, pattern);
                return true;
            }
        }

        // 默认尝试修复（如果错误信息格式未知）
        return compilerOutput.contains("error:") || compilerOutput.contains("错误:");
    }

    /**
     * 生成修复代码
     */
    private String generateFix(
            G3ArtifactEntity artifact,
            String compilerOutput,
            String compactContext,
            String repairHistory,
            AIProvider aiProvider) {
        String fileName = artifact.getFileName() != null ? artifact.getFileName() : "";
        String filePath = artifact.getFilePath() != null ? artifact.getFilePath() : "";

        // pom.xml 走专用 XML 修复流程
        if ("pom.xml".equalsIgnoreCase(fileName) || filePath.endsWith("/pom.xml")
                || "pom.xml".equalsIgnoreCase(filePath)) {
            return generatePomFix(artifact, compilerOutput, aiProvider);
        }

        String safeContext = (compactContext == null || compactContext.isBlank()) ? "(暂无可用类索引)\n" : compactContext;
        String safeHistory = (repairHistory == null || repairHistory.isBlank()) ? "(首次修复)\n" : repairHistory;

        // fix.txt 模板现在需要4个参数: 修复历史、上下文、代码、错误信息
        String prompt = String.format(promptTemplateService.coachFixTemplate(), safeHistory, safeContext, artifact.getContent(),
                compilerOutput);

        AIProvider.AIResponse response = aiProvider.generate(prompt,
                AIProvider.AIRequest.builder()
                        .temperature(0.1) // 低温度保证稳定性
                        .maxTokens(8000)
                        .build());

        String fixedCode = response.content();

        // 清理可能的markdown标记
        fixedCode = cleanMarkdown(fixedCode);
        fixedCode = sanitizeJavaSource(fixedCode);

        // 验证修复后的代码基本结构
        if (!isValidJavaCode(fixedCode)) {
            log.warn("[{}] 生成的修复代码结构无效", AGENT_NAME);
            return null;
        }

        return fixedCode;
    }

    /**
     * 净化 Java 源码输出，避免 AI 把解释性文字混入文件导致编译失败。
     *
     * 规则（保守裁剪）：
     * - 移除 package 之前的所有内容
     * - 移除最后一个 '}' 之后的所有内容
     * - 去除 Markdown 代码块标记（调用方已做一次，这里再兜底）
     */
    private String sanitizeJavaSource(String code) {
        if (code == null)
            return "";
        String normalized = cleanMarkdown(code).replace("\r", "").trim();

        int packagePos = normalized.indexOf("package ");
        if (packagePos > 0) {
            normalized = normalized.substring(packagePos).trim();
        }

        int lastBrace = normalized.lastIndexOf('}');
        if (lastBrace >= 0 && lastBrace + 1 < normalized.length()) {
            normalized = normalized.substring(0, lastBrace + 1).trim();
        }

        return normalized + "\n";
    }

    /**
     * 生成 pom.xml 修复内容
     */
    private String generatePomFix(G3ArtifactEntity artifact, String compilerOutput, AIProvider aiProvider) {
        String prompt = String.format(promptTemplateService.coachFixPomXmlTemplate(), artifact.getContent(),
                compilerOutput);

        AIProvider.AIResponse response = aiProvider.generate(prompt,
                AIProvider.AIRequest.builder()
                        .temperature(0.1)
                        .maxTokens(6000)
                        .build());

        String fixedXml = cleanMarkdown(response.content());

        if (!isValidPomXml(fixedXml)) {
            log.warn("[{}] 生成的 pom.xml 结构无效", AGENT_NAME);
            return null;
        }

        return fixedXml;
    }

    /**
     * 安全构建精简上下文（best-effort）。
     *
     * <p>
     * 说明：
     * </p>
     * <ul>
     * <li>上下文来自 G3 三文件规划中的 context.md；</li>
     * <li>仅用于辅助 AI 判断“类/包是否存在”，不应导致主流程中断。</li>
     * </ul>
     */
    private String buildSafeCompactContext(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        try {
            if (job == null || job.getId() == null)
                return "";
            String ctx = contextBuilder.buildGlobalContext(job.getId());
            // 兜底限制：避免 context.md 过大导致 token 浪费/上游超时
            return truncate(ctx, 8000);
        } catch (Exception e) {
            logConsumer.accept(G3LogEntry.warn(getRole(), "构建可用类索引失败（忽略）: " + e.getMessage()));
            return "";
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null)
            return "";
        if (maxChars <= 0)
            return "";
        String normalized = text.trim();
        if (normalized.length() <= maxChars)
            return normalized;
        return normalized.substring(0, maxChars) + "\n...(已截断)\n";
    }

    /**
     * 创建修复后的产物
     */
    private G3ArtifactEntity createFixedArtifact(G3ArtifactEntity original, String fixedCode) {
        G3ArtifactEntity fixed = G3ArtifactEntity.create(
                original.getJobId(),
                original.getFilePath(),
                fixedCode,
                G3ArtifactEntity.GeneratedBy.COACH,
                original.getGenerationRound() + 1);

        // 设置版本和父产物关系
        fixed.setVersion(original.getVersion() + 1);
        fixed.setParentArtifactId(original.getId());

        return fixed;
    }

    /**
     * 清理Markdown标记
     */
    private String cleanMarkdown(String code) {
        if (code == null)
            return "";

        // 移除代码块标记（兼容 ```java / ```xml / ```）
        code = code.replaceAll("```[a-zA-Z]*\\s*", "");
        code = code.replaceAll("```\\s*", "");

        return code.trim();
    }

    /**
     * 验证Java代码基本结构
     */
    private boolean isValidJavaCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }

        // 检查基本结构
        boolean hasPackage = code.contains("package ");
        boolean hasClass = Pattern.compile("(?:public\\s+)?(?:class|interface|enum)\\s+\\w+")
                .matcher(code).find();

        // 检查括号匹配
        int braceCount = 0;
        for (char c : code.toCharArray()) {
            if (c == '{')
                braceCount++;
            if (c == '}')
                braceCount--;
            if (braceCount < 0)
                return false; // 右括号多于左括号
        }

        return hasPackage && hasClass && braceCount == 0;
    }

    /**
     * 验证 pom.xml 基本结构
     */
    private boolean isValidPomXml(String xml) {
        if (xml == null || xml.isBlank())
            return false;
        String trimmed = xml.trim();
        if (!trimmed.startsWith("<"))
            return false;
        return trimmed.contains("<project") && trimmed.contains("</project>");
    }

    /**
     * 从编译输出中提取错误行号
     */
    public List<Integer> extractErrorLines(String compilerOutput) {
        List<Integer> lines = new ArrayList<>();

        if (compilerOutput == null)
            return lines;

        // 匹配 "文件名:行号:" 或 "[ERROR] 文件名:[行号,列号]" 格式
        Pattern linePattern = Pattern.compile("(?::\\s*(\\d+):|\\[(\\d+),\\d+\\])");
        Matcher matcher = linePattern.matcher(compilerOutput);

        while (matcher.find()) {
            String lineNum = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                lines.add(Integer.parseInt(lineNum));
            } catch (NumberFormatException e) {
                // 忽略无效行号
            }
        }

        return lines;
    }

    /**
     * 分类错误类型
     */
    public String classifyError(String compilerOutput) {
        if (compilerOutput == null)
            return "UNKNOWN";

        String lower = compilerOutput.toLowerCase();

        if (lower.contains("cannot find symbol") || lower.contains("package") && lower.contains("does not exist")) {
            return "SYMBOL_NOT_FOUND";
        }
        if (lower.contains("incompatible types") || lower.contains("cannot be converted")) {
            return "TYPE_MISMATCH";
        }
        if (lower.contains("method") && lower.contains("cannot be applied")) {
            return "METHOD_SIGNATURE";
        }
        if (lower.contains(";' expected") || lower.contains("illegal start")) {
            return "SYNTAX_ERROR";
        }
        if (lower.contains("unreported exception")) {
            return "EXCEPTION_HANDLING";
        }
        if (lower.contains("missing return")) {
            return "MISSING_RETURN";
        }

        return "OTHER";
    }

    /**
     * 从验证结果中提取特定文件的编译输出
     *
     * @param validationResults 验证结果列表
     * @param fileName          目标文件名
     * @return 编译输出字符串
     */
    private String extractCompilerOutputFromValidation(
            List<G3ValidationResultEntity> validationResults,
            String fileName) {

        if (validationResults == null || validationResults.isEmpty()) {
            return "";
        }

        StringBuilder output = new StringBuilder();

        for (G3ValidationResultEntity result : validationResults) {
            // 检查 stderr 是否包含目标文件的错误
            String stderr = result.getStderr();
            if (stderr != null && stderr.contains(fileName)) {
                // 提取与该文件相关的错误行
                String[] lines = stderr.split("\n");
                for (String line : lines) {
                    if (line.contains(fileName) || line.contains("error:") || line.contains("symbol:")) {
                        output.append(line).append("\n");
                    }
                }
            }

            // 检查解析后的错误
            List<G3ValidationResultEntity.ParsedError> errors = result.getParsedErrors();
            if (errors != null) {
                for (G3ValidationResultEntity.ParsedError error : errors) {
                    if (error.getFile() != null && error.getFile().contains(fileName)) {
                        output.append(String.format("%s:%d: %s: %s\n",
                                error.getFile(),
                                error.getLine(),
                                error.getSeverity(),
                                error.getMessage()));
                    }
                }
            }
        }

        return output.toString();
    }

    // ========== v2.1.0 新增：错误聚合分析和辅助方法 ==========

    /**
     * 错误聚合分析结果
     */
    public record ErrorAggregation(
            int fileCount,
            List<String> errorTypes,
            String coreIssue,
            Map<String, Integer> errorTypeCount) {
    }

    /**
     * 聚合分析所有错误，识别核心问题
     */
    private ErrorAggregation analyzeErrorsAggregated(
            List<G3ArtifactEntity> errorArtifacts,
            List<G3ValidationResultEntity> validationResults) {

        Map<String, Integer> errorTypeCount = new java.util.HashMap<>();

        for (G3ArtifactEntity artifact : errorArtifacts) {
            String output = artifact.getCompilerOutput();
            if (output == null || output.isBlank()) {
                output = extractCompilerOutputFromValidation(validationResults, artifact.getFileName());
            }
            String errorType = G3ErrorSignature.getErrorTypeDescription(output);
            errorTypeCount.merge(errorType, 1, Integer::sum);
        }

        List<String> errorTypes = new ArrayList<>(errorTypeCount.keySet());

        // 核心问题：出现次数最多的错误类型
        String coreIssue = errorTypeCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未知");

        return new ErrorAggregation(
                errorArtifacts.size(),
                errorTypes,
                coreIssue,
                errorTypeCount);
    }

    /**
     * 按优先级排序产物（核心错误文件优先）
     */
    private List<G3ArtifactEntity> prioritizeArtifacts(
            List<G3ArtifactEntity> artifacts,
            ErrorAggregation aggregation) {
        // 简单策略：pom.xml 放最后（避免优先修 pom 导致真正的 Java 错误被忽略）
        return artifacts.stream()
                .sorted((a, b) -> {
                    boolean aIsPom = "pom.xml".equalsIgnoreCase(a.getFileName());
                    boolean bIsPom = "pom.xml".equalsIgnoreCase(b.getFileName());
                    if (aIsPom && !bIsPom)
                        return 1;
                    if (!aIsPom && bIsPom)
                        return -1;
                    return 0;
                })
                .collect(Collectors.toList());
    }

    /**
     * 保守的 pom.xml 兜底策略：已修复过则跳过
     */
    private boolean shouldSkipPomFallback(G3ArtifactEntity artifact, G3SessionMemory sessionMemory) {
        if (!"pom.xml".equalsIgnoreCase(artifact.getFileName())) {
            return false;
        }
        return sessionMemory != null && sessionMemory.hasRepairedFile("pom.xml");
    }

    /**
     * 生成修复代码（带修复历史上下文）
     */
    private String generateFixWithHistory(
            G3ArtifactEntity artifact,
            String compilerOutput,
            String compactContext,
            String repairHistoryContext,
            AIProvider aiProvider) {

        String fileName = artifact.getFileName() != null ? artifact.getFileName() : "";
        String filePath = artifact.getFilePath() != null ? artifact.getFilePath() : "";

        // pom.xml 走专用 XML 修复流程
        if ("pom.xml".equalsIgnoreCase(fileName) || filePath.endsWith("/pom.xml")
                || "pom.xml".equalsIgnoreCase(filePath)) {
            return generatePomFix(artifact, compilerOutput, aiProvider);
        }

        String safeContext = (compactContext == null || compactContext.isBlank()) ? "(暂无可用类索引)\n" : compactContext;
        String safeHistory = (repairHistoryContext == null || repairHistoryContext.isBlank()) ? "(首次修复)\n"
                : repairHistoryContext;

        // 使用增强版 Prompt（包含修复历史）
        String prompt = buildEnhancedFixPrompt(safeHistory, safeContext, artifact.getContent(), compilerOutput);

        AIProvider.AIResponse response = aiProvider.generate(prompt,
                AIProvider.AIRequest.builder()
                        .temperature(0.1) // 低温度保证稳定性
                        .maxTokens(8000)
                        .build());

        String fixedCode = response.content();

        // 清理可能的markdown标记
        fixedCode = cleanMarkdown(fixedCode);
        fixedCode = sanitizeJavaSource(fixedCode);

        // 验证修复后的代码基本结构
        if (!isValidJavaCode(fixedCode)) {
            log.warn("[{}] 生成的修复代码结构无效", AGENT_NAME);
            return null;
        }

        return fixedCode;
    }

    /**
     * 构建增强版修复 Prompt（包含修复历史）
     */
    private String buildEnhancedFixPrompt(
            String repairHistory,
            String classIndex,
            String fileContent,
            String compilerOutput) {

        return String.format("""
                ## 修复历史
                %s

                ## 可用类索引
                %s

                ## 当前文件内容
                ```java
                %s
                ```

                ## 编译错误
                ```
                %s
                ```

                ## 任务
                请修复上述 Java 代码中的编译错误。

                注意：
                1. 参考"修复历史"，不要重复之前失败的修复方案
                2. 参考"可用类索引"，确保 import 和类型引用正确
                3. 检查方法名是否匹配（例如 Result.error(msg) vs Result.failed(msg)）
                4. 检查是否缺少 Lombok 注解或 import
                5. 只输出修复后的完整 Java 文件，不要任何解释
                """, repairHistory, classIndex, fileContent, compilerOutput);
    }
}
