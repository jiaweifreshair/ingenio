package com.ingenio.backend.agent.g3.impl;

import com.ingenio.backend.agent.g3.ICoachAgent;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import com.ingenio.backend.prompt.PromptTemplateService;
import com.ingenio.backend.service.g3.G3ContextBuilder;
import com.ingenio.backend.service.g3.G3ToolsetService;
import com.ingenio.backend.service.g3.G3ToolsetService.SearchResult;
import com.ingenio.backend.service.g3.hooks.G3HookPipeline;
import com.ingenio.backend.service.g3.G3KnowledgeStore;
import com.ingenio.backend.mapper.g3.G3ArtifactMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CoachAgentImpl 单元测试
 *
 * 测试教练Agent的核心功能：
 * 1. 分析编译错误并生成修复代码
 * 2. 判断错误是否可自动修复
 * 3. 提取错误行号和分类错误类型
 * 4. 处理各种异常情况
 */
@ExtendWith(MockitoExtension.class)
class CoachAgentImplTest {

    @Mock
    private AIProviderFactory aiProviderFactory;

    @Mock
    private AIProvider aiProvider;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private G3ContextBuilder contextBuilder;

    @Mock
    private G3ArtifactMapper artifactMapper;

    @Mock
    private G3KnowledgeStore knowledgeStore;

    @Mock
    private G3ToolsetService toolsetService;

    @Mock
    private G3HookPipeline hookPipeline;

    @Mock
    private Consumer<G3LogEntry> logConsumer;

    @InjectMocks
    private CoachAgentImpl coachAgent;

    private G3JobEntity testJob;
    private UUID testJobId;
    private G3ArtifactEntity errorArtifact;
    private List<G3ValidationResultEntity> validationResults;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();

        // 说明：部分用例不会触发 AI 调用或提示词拼装，这里使用 lenient 避免 Strict Stubs 因“未使用桩”报错。
        lenient().when(promptTemplateService.coachAnalysisTemplate()).thenReturn("分析\\n%s\\n%s");
        lenient().when(promptTemplateService.coachFixTemplate()).thenReturn("修复\\n%s\\n%s\\n%s");
        lenient().when(promptTemplateService.coachFixPomXmlTemplate()).thenReturn("pom.xml\\n%s\\n%s");
        lenient().when(contextBuilder.buildGlobalContext(any())).thenReturn("# 可用类索引\\n");
        lenient().when(knowledgeStore.search(anyString(), any(), anyInt())).thenReturn(List.of());
        lenient().when(knowledgeStore.searchRepo(anyString(), any(), any(), anyInt())).thenReturn(List.of());
        lenient().when(toolsetService.searchWorkspace(any(), anyString(), anyInt(), any()))
                .thenReturn(SearchResult.success(new ArrayList<>()));
        lenient().when(hookPipeline.wrapProvider(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        testJob = G3JobEntity.builder()
                .id(testJobId)
                .requirement("创建用户管理系统")
                .currentRound(1)
                .build();

        // 创建一个有编译错误的产物
        errorArtifact = G3ArtifactEntity.builder()
                .id(UUID.randomUUID())
                .jobId(testJobId)
                .filePath("src/main/java/com/test/User.java")
                .fileName("User.java")
                .content("""
                        package com.test;

                        public class User {
                            private String name;
                            private Integer age;

                            public String getName() {
                                return name
                            }
                        }
                        """)
                .language("java")
                .version(1)
                .hasErrors(true)
                .compilerOutput("User.java:7: error: ';' expected\n                return name\n                           ^\n1 error")
                .generatedBy(G3ArtifactEntity.GeneratedBy.BACKEND_CODER.getValue())
                .generationRound(0)
                .build();

        // 创建验证结果
        validationResults = new ArrayList<>();
        G3ValidationResultEntity validationResult = G3ValidationResultEntity.builder()
                .id(UUID.randomUUID())
                .jobId(testJobId)
                .round(1)
                .passed(false)
                .errorCount(1)
                .warningCount(0)
                .stderr("User.java:7: error: ';' expected\n")
                .build();
        validationResults.add(validationResult);
    }

    /**
     * 测试：成功修复编译错误
     * 期望：返回修复后的产物，包含分析报告
     */
    @Test
    void fix_shouldFixCompilationErrors() throws AIProvider.AIException {
        // GIVEN
        String fixedCode = """
                package com.test;

                public class User {
                    private String name;
                    private Integer age;

                    public String getName() {
                        return name;
                    }
                }
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(fixedCode)
                        .model("test-model")
                        .build());

        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                List.of(errorArtifact),
                validationResults,
                logConsumer
        );

        // THEN
        assertTrue(result.success(), "修复应该成功");
        assertNotNull(result.fixedArtifacts(), "应返回修复后的产物");
        assertFalse(result.fixedArtifacts().isEmpty(), "修复产物列表不应为空");
        assertNotNull(result.analysisReport(), "应包含分析报告");

        G3ArtifactEntity fixedArtifact = result.fixedArtifacts().get(0);
        assertEquals(testJobId, fixedArtifact.getJobId(), "产物应关联正确的Job");
        assertEquals(errorArtifact.getFilePath(), fixedArtifact.getFilePath(), "文件路径应保持一致");
        assertTrue(fixedArtifact.getContent().contains("return name;"), "修复后的代码应包含分号");
        assertEquals(G3ArtifactEntity.GeneratedBy.COACH.getValue(), fixedArtifact.getGeneratedBy(), "产物应标记为COACH生成");
        assertEquals(errorArtifact.getVersion() + 1, fixedArtifact.getVersion(), "版本号应递增");
        assertEquals(errorArtifact.getId(), fixedArtifact.getParentArtifactId(), "应设置父产物ID");

        // 验证日志输出
        verify(logConsumer, atLeastOnce()).accept(argThat((G3LogEntry log) ->
                log.getLevel().equals(G3LogEntry.Level.SUCCESS.getValue())
        ));
    }

    /**
     * 测试：错误产物列表为空时应返回失败
     * 期望：返回失败结果
     */
    @Test
    void fix_whenNoErrorArtifacts_shouldReturnFailure() {
        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                List.of(),
                validationResults,
                logConsumer
        );

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertNotNull(result.errorMessage(), "应包含错误信息");
        assertTrue(result.errorMessage().contains("没有需要修复的产物"), "错误信息应说明原因");
    }

    /**
     * 测试：错误产物列表为null时应返回失败
     * 期望：返回失败结果
     */
    @Test
    void fix_whenArtifactsIsNull_shouldReturnFailure() {
        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                null,
                validationResults,
                logConsumer
        );

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertTrue(result.errorMessage().contains("没有需要修复的产物"), "错误信息应说明原因");
    }

    /**
     * 测试：AI提供商不可用时应返回失败
     * 期望：返回失败结果
     */
    @Test
    void fix_whenAIProviderUnavailable_shouldReturnFailure() {
        // GIVEN
        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(false);

        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                List.of(errorArtifact),
                validationResults,
                logConsumer
        );

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertTrue(result.errorMessage().contains("AI提供商不可用"), "错误信息应说明原因");
    }

    /**
     * 测试：AI调用异常时应捕获并返回失败
     * 期望：返回失败结果，记录错误日志
     */
    @Test
    void fix_whenAICallFails_shouldReturnFailure() throws AIProvider.AIException {
        // GIVEN
        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenThrow(new AIProvider.AIException("API调用失败", "test-provider"));

        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                List.of(errorArtifact),
                validationResults,
                logConsumer
        );

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertNotNull(result.errorMessage(), "应包含错误信息");

        // 验证错误日志
        verify(logConsumer, atLeastOnce()).accept(argThat((G3LogEntry log) ->
                log.getLevel().equals(G3LogEntry.Level.ERROR.getValue())
        ));
    }

    /**
     * 测试：无法自动修复的错误应标记为cannotFix
     * 期望：返回cannotFix结果，包含分析报告
     */
    @Test
    void fix_whenErrorCannotBeFixed_shouldReturnCannotFix() throws AIProvider.AIException {
        // GIVEN: OutOfMemoryError无法自动修复
        errorArtifact.setCompilerOutput("java.lang.OutOfMemoryError: Java heap space");

        String analysisReport = """
                错误类型: 内存错误
                错误原因: 堆内存溢出
                修复建议: 增加JVM堆内存大小
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(analysisReport)
                        .model("test-model")
                        .build());

        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                List.of(errorArtifact),
                validationResults,
                logConsumer
        );

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertNotNull(result.analysisReport(), "应包含分析报告");
        assertTrue(result.analysisReport().contains("无法自动修复"), "报告应说明无法修复");

        // 验证警告日志
        verify(logConsumer, atLeastOnce()).accept(argThat((G3LogEntry log) ->
                log.getLevel().equals(G3LogEntry.Level.WARN.getValue())
        ));
    }

    /**
     * 测试：canAutoFix应正确判断可修复的错误
     * 期望：返回true
     */
    @Test
    void canAutoFix_withFixableError_shouldReturnTrue() {
        assertTrue(coachAgent.canAutoFix("error: cannot find symbol"), "符号未定义应可修复");
        assertTrue(coachAgent.canAutoFix("error: incompatible types"), "类型不兼容应可修复");
        assertTrue(coachAgent.canAutoFix("error: method xxx in class Yyy cannot be applied"), "方法参数错误应可修复");
        assertTrue(coachAgent.canAutoFix("error: package com.test does not exist"), "包不存在应可修复");
        assertTrue(coachAgent.canAutoFix("error: ';' expected"), "缺少分号应可修复");
        assertTrue(coachAgent.canAutoFix("error: missing return statement"), "缺少返回语句应可修复");
    }

    /**
     * 测试：canAutoFix应正确判断不可修复的错误
     * 期望：返回false
     */
    @Test
    void canAutoFix_withNonFixableError_shouldReturnFalse() {
        assertFalse(coachAgent.canAutoFix("OutOfMemoryError"), "内存溢出不可修复");
        assertFalse(coachAgent.canAutoFix("StackOverflowError"), "栈溢出不可修复");
        assertFalse(coachAgent.canAutoFix("Could not find or load main class"), "主类问题不可修复");
        assertFalse(coachAgent.canAutoFix("java.lang.UnsupportedClassVersionError"), "JDK版本问题不可修复");
        assertFalse(coachAgent.canAutoFix("Access denied"), "权限问题不可修复");
    }

    /**
     * 测试：canAutoFix对空输入应返回false
     * 期望：返回false
     */
    @Test
    void canAutoFix_withEmptyInput_shouldReturnFalse() {
        assertFalse(coachAgent.canAutoFix(null), "null应返回false");
        assertFalse(coachAgent.canAutoFix(""), "空字符串应返回false");
        assertFalse(coachAgent.canAutoFix("   "), "空白字符串应返回false");
    }

    /**
     * 测试：canAutoFix对未知格式但包含error:的错误应返回true（尝试修复）
     * 期望：返回true
     */
    @Test
    void canAutoFix_withUnknownErrorFormat_shouldReturnTrue() {
        assertTrue(coachAgent.canAutoFix("error: some unknown error format"), "未知格式但包含error:应尝试修复");
        assertTrue(coachAgent.canAutoFix("错误: 未知错误格式"), "包含中文错误标记应尝试修复");
    }

    /**
     * 测试：extractErrorLines应正确提取错误行号
     * 期望：返回正确的行号列表
     */
    @Test
    void extractErrorLines_shouldExtractLineNumbers() {
        String compilerOutput = """
                User.java:7: error: ';' expected
                User.java:15: error: incompatible types
                [ERROR] /path/to/User.java:[23,5] cannot find symbol
                """;

        List<Integer> lines = coachAgent.extractErrorLines(compilerOutput);

        assertFalse(lines.isEmpty(), "应提取到行号");
        assertTrue(lines.contains(7), "应包含行号7");
        assertTrue(lines.contains(15), "应包含行号15");
        assertTrue(lines.contains(23), "应包含行号23");
    }

    /**
     * 测试：extractErrorLines对空输入应返回空列表
     * 期望：返回空列表
     */
    @Test
    void extractErrorLines_withEmptyInput_shouldReturnEmptyList() {
        assertTrue(coachAgent.extractErrorLines(null).isEmpty(), "null应返回空列表");
        assertTrue(coachAgent.extractErrorLines("").isEmpty(), "空字符串应返回空列表");
        assertTrue(coachAgent.extractErrorLines("no line numbers here").isEmpty(), "无行号应返回空列表");
    }

    /**
     * 测试：classifyError应正确分类错误类型
     * 期望：返回正确的错误分类
     */
    @Test
    void classifyError_shouldClassifyCorrectly() {
        assertEquals("SYMBOL_NOT_FOUND", coachAgent.classifyError("cannot find symbol: class User"), "符号未找到");
        assertEquals("SYMBOL_NOT_FOUND", coachAgent.classifyError("package com.test does not exist"), "包不存在");
        assertEquals("TYPE_MISMATCH", coachAgent.classifyError("incompatible types: String cannot be converted to Integer"), "类型不匹配");
        assertEquals("METHOD_SIGNATURE", coachAgent.classifyError("method getUser in class Service cannot be applied"), "方法签名错误");
        assertEquals("SYNTAX_ERROR", coachAgent.classifyError("';' expected"), "语法错误 - 缺少分号");
        assertEquals("SYNTAX_ERROR", coachAgent.classifyError("illegal start of expression"), "语法错误 - 非法表达式");
        assertEquals("EXCEPTION_HANDLING", coachAgent.classifyError("unreported exception IOException"), "异常处理");
        assertEquals("MISSING_RETURN", coachAgent.classifyError("missing return statement"), "缺少返回语句");
        assertEquals("OTHER", coachAgent.classifyError("some other error"), "其他错误");
        assertEquals("UNKNOWN", coachAgent.classifyError(null), "null应返回UNKNOWN");
    }

    /**
     * 测试：analyzeError应返回错误分析报告
     * 期望：返回包含错误分析的字符串
     */
    @Test
    void analyzeError_shouldReturnAnalysisReport() throws AIProvider.AIException {
        // GIVEN
        String analysisReport = """
                错误类型: 语法错误
                错误位置: 第7行
                错误原因: 缺少分号
                修复建议: 在return语句后添加分号
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(analysisReport)
                        .model("test-model")
                        .build());

        // WHEN
        String result = coachAgent.analyzeError(errorArtifact, errorArtifact.getCompilerOutput());

        // THEN
        assertNotNull(result, "应返回分析报告");
        assertTrue(result.contains("错误类型"), "应包含错误类型");
        assertTrue(result.contains("修复建议"), "应包含修复建议");
    }

    /**
     * 测试：analyzeError在AI不可用时应返回错误信息
     * 期望：返回AI不可用的提示
     */
    @Test
    void analyzeError_whenAIUnavailable_shouldReturnError() {
        // GIVEN
        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(false);

        // WHEN
        String result = coachAgent.analyzeError(errorArtifact, errorArtifact.getCompilerOutput());

        // THEN
        assertTrue(result.contains("AI提供商不可用"), "应说明AI不可用");
    }

    /**
     * 测试：修复多个错误文件
     * 期望：返回多个修复后的产物
     */
    @Test
    void fix_withMultipleErrors_shouldFixAll() throws AIProvider.AIException {
        // GIVEN
        G3ArtifactEntity errorArtifact2 = G3ArtifactEntity.builder()
                .id(UUID.randomUUID())
                .jobId(testJobId)
                .filePath("src/main/java/com/test/Product.java")
                .fileName("Product.java")
                .content("package com.test;\npublic class Product {}")
                .compilerOutput("Product.java:2: error: cannot find symbol")
                .hasErrors(true)
                .version(1)
                .generatedBy(G3ArtifactEntity.GeneratedBy.BACKEND_CODER.getValue())
                .generationRound(0)
                .build();

        String fixedCode1 = "package com.test;\npublic class User { private String name; public String getName() { return name; } }";
        String fixedCode2 = "package com.test;\npublic class Product {}";

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder().content(fixedCode1).model("test").build())
                .thenReturn(AIProvider.AIResponse.builder().content(fixedCode2).model("test").build());

        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                List.of(errorArtifact, errorArtifact2),
                validationResults,
                logConsumer
        );

        // THEN
        assertTrue(result.success(), "修复应该成功");
        // CoachAgent修复2个错误文件后，还会生成3个文档文件(progress.md, findings.md, task_plan.md)
        assertEquals(5, result.fixedArtifacts().size(), "应修复2个文件 + 生成3个文档文件");
        assertTrue(result.analysisReport().contains("成功 2 个"), "报告应说明修复了2个文件");
    }

    /**
     * 测试：Agent基础属性
     * 期望：返回正确的名称、角色、描述
     */
    @Test
    void agentProperties_shouldReturnCorrectValues() {
        assertEquals("CoachAgent", coachAgent.getName(), "应返回正确的名称");
        assertEquals(G3LogEntry.Role.COACH, coachAgent.getRole(), "应返回COACH角色");
        assertNotNull(coachAgent.getDescription(), "应有描述信息");
        assertTrue(coachAgent.getDescription().contains("教练"), "描述应提到教练");
    }

    /**
     * 测试：execute方法应抛出异常（Coach不直接执行）
     * 期望：抛出G3AgentException
     */
    @Test
    void execute_shouldThrowException() {
        // WHEN & THEN
        ICoachAgent.G3AgentException exception = assertThrows(
                ICoachAgent.G3AgentException.class,
                () -> coachAgent.execute(testJob, logConsumer),
                "execute应抛出异常"
        );

        assertEquals("CoachAgent", exception.getAgentName(), "异常应包含Agent名称");
        assertEquals(G3LogEntry.Role.COACH, exception.getRole(), "异常应包含Agent角色");
        assertTrue(exception.getMessage().contains("fix()方法调用"), "异常信息应说明应使用fix方法");
    }

    /**
     * 测试：AI返回的代码包含markdown标记应被清理
     * 期望：修复后的代码不包含markdown标记
     */
    @Test
    void fix_shouldCleanMarkdownFromAIResponse() throws AIProvider.AIException {
        // GIVEN
        String fixedCodeWithMarkdown = """
                ```java
                package com.test;

                public class User {
                    private String name;
                    public String getName() { return name; }
                }
                ```
                """;

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(fixedCodeWithMarkdown)
                        .model("test-model")
                        .build());

        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                List.of(errorArtifact),
                validationResults,
                logConsumer
        );

        // THEN
        assertTrue(result.success(), "修复应该成功");
        G3ArtifactEntity fixedArtifact = result.fixedArtifacts().get(0);
        assertFalse(fixedArtifact.getContent().contains("```"), "修复后的代码不应包含markdown标记");
        assertTrue(fixedArtifact.getContent().contains("package com.test"), "应包含package声明");
    }

    /**
     * 测试：AI返回无效Java代码应标记为修复失败
     * 期望：返回cannotFix结果
     */
    @Test
    void fix_whenAIReturnsInvalidCode_shouldReportFailure() throws AIProvider.AIException {
        // GIVEN: AI返回的代码缺少class定义或括号不匹配
        String invalidCode = "this is not valid java code";

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(invalidCode)
                        .model("test-model")
                        .build());

        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                List.of(errorArtifact),
                validationResults,
                logConsumer
        );

        // THEN
        assertFalse(result.success(), "应返回失败");
        assertNotNull(result.analysisReport(), "应包含分析报告");
        assertTrue(result.analysisReport().contains("失败"), "报告应说明修复失败");
    }

    /**
     * 测试：从验证结果中提取编译输出
     * 期望：当产物没有compilerOutput时，从validationResults提取
     */
    @Test
    void fix_shouldExtractCompilerOutputFromValidation() throws AIProvider.AIException {
        // GIVEN: 产物没有compilerOutput，但validationResults有错误信息
        errorArtifact.setCompilerOutput(null);

        String fixedCode = "package com.test;\npublic class User { private String name; public String getName() { return name; } }";

        when(aiProviderFactory.getProvider()).thenReturn(aiProvider);
        when(aiProvider.isAvailable()).thenReturn(true);
        when(aiProvider.generate(anyString(), any()))
                .thenReturn(AIProvider.AIResponse.builder()
                        .content(fixedCode)
                        .model("test-model")
                        .build());

        // WHEN
        ICoachAgent.CoachResult result = coachAgent.fix(
                testJob,
                List.of(errorArtifact),
                validationResults,
                logConsumer
        );

        // THEN
        assertTrue(result.success(), "应成功修复");
        // 验证AI调用时使用了从validation提取的编译输出
        verify(aiProvider, atLeastOnce()).generate(contains("expected"), any());
    }
}
