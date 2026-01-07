package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * 可自动修复的错误模式
     */
    private static final List<String> AUTO_FIXABLE_PATTERNS = List.of(
            "cannot find symbol",           // 符号未定义
            "incompatible types",           // 类型不兼容
            "method .* in .* cannot be applied", // 方法参数错误
            "package .* does not exist",    // 包不存在
            "cannot access class",          // 类访问问题
            "non-static .* cannot be referenced", // 静态引用错误
            "unreported exception",         // 未处理异常
            "missing return statement",     // 缺少返回语句
            ";' expected",                  // 缺少分号
            "class .* is public, should be declared", // 类声明问题
            "'\\)' expected",               // 缺少右括号
            "'\\{' expected",               // 缺少左花括号
            "illegal start of expression",  // 表达式语法错误

            // Maven / 构建类错误（用于尝试修复 pom.xml）
            "could not resolve dependencies",
            "the following artifacts could not be resolved",
            "non-resolvable parent pom",
            "failed to read artifact descriptor",
            "could not find artifact",
            "could not transfer artifact",
            "failed to execute goal"
    );

    /**
     * 无法自动修复的错误模式
     */
    private static final List<String> NON_FIXABLE_PATTERNS = List.of(
            "OutOfMemoryError",             // 内存溢出
            "StackOverflowError",           // 栈溢出
            "Could not find or load main class", // 主类问题
            "java.lang.UnsupportedClassVersionError", // JDK版本问题
            "Access denied",                // 权限问题
            "connection refused",
            "operation not permitted",
            "unknown host",
            "timed out"
    );

    /**
     * pom.xml 修复提示词模板（XML）
     */
    private static final String FIX_POM_XML_PROMPT_TEMPLATE = """
        你是一个资深的 Maven 构建工程师。请修复 pom.xml 导致的构建失败问题。

        ## 当前 pom.xml
        ```xml
        %s
        ```

        ## Maven 构建失败输出（摘要）
        ```
        %s
        ```

        ## 目标
        - 让 `mvn compile` 能通过
        - 只做必要的最小改动
        - 保持 Spring Boot 3.4.x + Java 17

        ## 可用策略（按需选择）
        1) 依赖解析失败：补充 `<repositories>` / `<pluginRepositories>`（优先加入阿里云公共仓库镜像 + Maven Central）
        2) Lombok/注解处理失败：补充 `maven-compiler-plugin` 的 annotationProcessorPaths 或相关配置
        3) 插件解析失败：补充 pluginRepositories 或明确 plugin 版本

        ## 输出格式要求
        ⚠️ 只输出修复后的完整 pom.xml（纯 XML），不要输出任何解释文字、不要输出 ``` 标记。
        """;

    /**
     * 修复提示词模板
     * 优化版本: 添加Chain-of-Thought调试步骤、常见错误模式、修复示例、质量检查
     */
    private static final String FIX_PROMPT_TEMPLATE = """
        你是一个专业的Java调试专家。请按照Chain-of-Thought步骤分析并修复编译错误。

        ## 原始代码
        ```java
        %s
        ```

        ## 编译错误信息
        ```
        %s
        ```

        ## 调试步骤（Chain-of-Thought）

        ### Step 1: 错误定位
        - 提取错误行号和错误类型
        - 识别问题代码片段
        - 确定错误上下文（类/方法/表达式）

        ### Step 2: 根因分析
        - 错误类型归类（符号未定义/类型不兼容/方法签名错误/语法错误）
        - 分析为什么会出现这个错误
        - 确定修复的最小改动范围

        ### Step 3: 生成修复方案
        - 仅修改必要的代码
        - 保持原有逻辑和结构不变
        - 确保修复后代码符合Java语法

        ## 常见错误类型及修复策略

        ### 1. 符号未定义（cannot find symbol）
        - **原因**: 缺少import语句、变量名拼写错误、类不存在
        - **修复**: 添加正确的import、修正拼写、检查依赖

        ### 2. 类型不兼容（incompatible types）
        - **原因**: 赋值类型不匹配、泛型类型错误
        - **修复**: 添加类型转换、修正泛型声明

        ### 3. 方法参数错误（method cannot be applied）
        - **原因**: 参数类型/数量不匹配
        - **修复**: 调整参数类型、补充缺失参数、移除多余参数

        ### 4. 缺少import
        - **原因**: 使用了外部类但未导入
        - **修复**: 在package语句后添加import

        ### 5. UUID字段问题
        - **特殊情况**: MyBatis-Plus的UUID字段需要UUIDv8TypeHandler
        - **修复**: 确保@TableField(typeHandler = UUIDv8TypeHandler.class)存在

        ### 6. 枚举类型使用
        - **原因**: 枚举值访问方式错误
        - **修复**: 使用EnumClass.VALUE而非字符串

        ## 修复示例

        **示例1: 缺少import**
        错误: cannot find symbol - class UUID
        修复: 在package后添加 `import java.util.UUID;`

        **示例2: 类型不兼容**
        错误: incompatible types: String cannot be converted to LocalDateTime
        修复: 使用 `LocalDateTime.parse(dateString)` 转换

        **示例3: 方法参数错误**
        错误: method create(UUID,String,String) cannot be applied to (String,String)
        修复: 添加缺失的UUID参数 `UUID.randomUUID()`

        ## 输出格式要求

        ⚠️ **重要**：直接输出修复后的完整Java代码，**不要**添加```java标记或任何解释文字。

        必须保持：
        - 原有的package语句
        - 原有的类结构和方法签名
        - 原有的注释和JavaDoc

        ## 质量检查清单（修复前自检）
        - [ ] 已添加所有必需的import语句
        - [ ] 类型转换正确（无强制类型转换警告）
        - [ ] 方法调用参数匹配
        - [ ] UUID字段有正确的TypeHandler注解
        - [ ] 枚举字段使用正确的访问方式
        - [ ] 输出是纯Java代码（无```标记）

        现在请修复上述代码。
        """;

    /**
     * 错误分析提示词模板
     */
    private static final String ANALYSIS_PROMPT_TEMPLATE = """
        你是一个专业的Java编译错误分析专家。请分析以下编译错误，给出简洁的中文分析报告。

        ## 源代码文件
        %s

        ## 编译错误输出
        ```
        %s
        ```

        ## 分析要求
        1. 识别错误类型（语法错误/类型错误/依赖错误/其他）
        2. 定位错误位置（行号、代码片段）
        3. 解释错误原因（用简洁的中文）
        4. 给出修复建议

        ## 输出格式
        错误类型: [类型]
        错误位置: 第[行号]行
        错误原因: [简洁描述]
        修复建议: [具体建议]
        """;

    public CoachAgentImpl(AIProviderFactory aiProviderFactory) {
        this.aiProviderFactory = aiProviderFactory;
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

            List<G3ArtifactEntity> fixedArtifacts = new ArrayList<>();
            StringBuilder analysisReport = new StringBuilder();
            int fixedCount = 0;
            int failedCount = 0;

            // 逐个修复错误文件
            // 注：compilerOutput 直接从 G3ArtifactEntity 获取，validationResults 用于获取全局编译信息
            for (G3ArtifactEntity artifact : errorArtifacts) {
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
                    String fixedCode = generateFix(artifact, compilerOutput, aiProvider);

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
                    logConsumer.accept(G3LogEntry.error(getRole(), artifact.getFileName() + " 修复异常: " + e.getMessage()));
                    analysisReport.append("【").append(artifact.getFileName()).append("】修复异常: ").append(e.getMessage()).append("\n\n");
                }
            }

            // 生成总结
            String summary = String.format("修复完成: 成功 %d 个, 失败 %d 个", fixedCount, failedCount);
            logConsumer.accept(G3LogEntry.info(getRole(), summary));
            analysisReport.insert(0, "=== 修复报告 ===\n" + summary + "\n\n");

            if (fixedArtifacts.isEmpty()) {
                return CoachResult.cannotFix(analysisReport.toString());
            }

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

            String prompt = String.format(ANALYSIS_PROMPT_TEMPLATE, artifact.getFileName(), compilerOutput);
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
    private String generateFix(G3ArtifactEntity artifact, String compilerOutput, AIProvider aiProvider) {
        String fileName = artifact.getFileName() != null ? artifact.getFileName() : "";
        String filePath = artifact.getFilePath() != null ? artifact.getFilePath() : "";

        // pom.xml 走专用 XML 修复流程
        if ("pom.xml".equalsIgnoreCase(fileName) || filePath.endsWith("/pom.xml") || "pom.xml".equalsIgnoreCase(filePath)) {
            return generatePomFix(artifact, compilerOutput, aiProvider);
        }

        String prompt = String.format(FIX_PROMPT_TEMPLATE, artifact.getContent(), compilerOutput);

        AIProvider.AIResponse response = aiProvider.generate(prompt,
                AIProvider.AIRequest.builder()
                        .temperature(0.1)  // 低温度保证稳定性
                        .maxTokens(8000)
                        .build());

        String fixedCode = response.content();

        // 清理可能的markdown标记
        fixedCode = cleanMarkdown(fixedCode);

        // 验证修复后的代码基本结构
        if (!isValidJavaCode(fixedCode)) {
            log.warn("[{}] 生成的修复代码结构无效", AGENT_NAME);
            return null;
        }

        return fixedCode;
    }

    /**
     * 生成 pom.xml 修复内容
     */
    private String generatePomFix(G3ArtifactEntity artifact, String compilerOutput, AIProvider aiProvider) {
        String prompt = String.format(FIX_POM_XML_PROMPT_TEMPLATE, artifact.getContent(), compilerOutput);

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
     * 创建修复后的产物
     */
    private G3ArtifactEntity createFixedArtifact(G3ArtifactEntity original, String fixedCode) {
        G3ArtifactEntity fixed = G3ArtifactEntity.create(
                original.getJobId(),
                original.getFilePath(),
                fixedCode,
                G3ArtifactEntity.GeneratedBy.COACH,
                original.getGenerationRound() + 1
        );

        // 设置版本和父产物关系
        fixed.setVersion(original.getVersion() + 1);
        fixed.setParentArtifactId(original.getId());

        return fixed;
    }

    /**
     * 清理Markdown标记
     */
    private String cleanMarkdown(String code) {
        if (code == null) return "";

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
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            if (braceCount < 0) return false; // 右括号多于左括号
        }

        return hasPackage && hasClass && braceCount == 0;
    }

    /**
     * 验证 pom.xml 基本结构
     */
    private boolean isValidPomXml(String xml) {
        if (xml == null || xml.isBlank()) return false;
        String trimmed = xml.trim();
        if (!trimmed.startsWith("<")) return false;
        return trimmed.contains("<project") && trimmed.contains("</project>");
    }

    /**
     * 从编译输出中提取错误行号
     */
    public List<Integer> extractErrorLines(String compilerOutput) {
        List<Integer> lines = new ArrayList<>();

        if (compilerOutput == null) return lines;

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
        if (compilerOutput == null) return "UNKNOWN";

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
}
