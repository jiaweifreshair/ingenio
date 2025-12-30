package com.ingenio.backend.service;

import com.ingenio.backend.dto.CodeGenerationResult;
import com.ingenio.backend.dto.CompilationResult;
import com.ingenio.backend.dto.TestResult;
import com.ingenio.backend.dto.request.validation.CompileValidationRequest;
import com.ingenio.backend.dto.request.validation.FullValidationRequest;
import com.ingenio.backend.dto.request.validation.TestValidationRequest;
import com.ingenio.backend.dto.response.validation.FullValidationResponse;
import com.ingenio.backend.dto.response.validation.ValidationResponse;
import com.ingenio.backend.entity.ValidationResultEntity;
import com.ingenio.backend.mapper.ValidationResultMapper;
import com.ingenio.backend.service.adapter.ValidationRequestAdapter;
import com.ingenio.backend.service.adapter.ValidationResultAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static java.util.Arrays.asList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ValidationService单元测试
 *
 * 测试覆盖（Phase 1）：
 * 1. CompilationValidator集成
 * 2. 适配器层转换
 * 3. 编译成功场景
 * 4. 编译失败场景（含错误解析）
 * 5. 数据库持久化
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ValidationService - 验证服务测试（Phase 1重构版）")
class ValidationServiceTest {

    @Mock
    private CompilationValidator compilationValidator;

    @Mock
    private ValidationRequestAdapter validationRequestAdapter;

    @Mock
    private ValidationResultAdapter validationResultAdapter;

    @Mock
    private ValidationResultMapper validationResultMapper;

    @Mock
    private TestExecutor testExecutor;

    @Mock
    private CoverageCalculator coverageCalculator;

    @InjectMocks
    private ValidationService validationService;

    private UUID appSpecId;
    private CompileValidationRequest request;

    @BeforeEach
    void setUp() {
        appSpecId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("TypeScript编译验证测试")
    class TypeScriptCompilation {

        @Test
        @DisplayName("TypeScript编译成功 - 应返回PASSED状态")
        void shouldPassWhenTypeScriptCompilationSucceeds() {
            // Given: TypeScript编译请求
            request = CompileValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .language("typescript")
                    .code("const hello: string = 'world';")
                    .compilerVersion("5.3.0")
                    .build();

            // 模拟适配器转换
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("nextjs")
                    .projectRoot("/tmp/test-project")
                    .build();
            when(validationRequestAdapter.toCodeGenerationResult(any())).thenReturn(codeResult);

            // 模拟编译成功
            CompilationResult compilationResult = CompilationResult.builder()
                    .success(true)
                    .compiler("tsc")
                    .compilerVersion("5.3.0")
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(1500L)
                    .command("tsc --noEmit")
                    .build();
            when(compilationValidator.compile(any())).thenReturn(compilationResult);

            // 模拟响应转换
            ValidationResponse expectedResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("compile")
                    .passed(true)
                    .status("PASSED")
                    .qualityScore(100)
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(1500L)
                    .completedAt(Instant.now())
                    .details(new HashMap<>())
                    .build();
            when(validationResultAdapter.toValidationResponse(any(), any())).thenReturn(expectedResponse);

            // When: 执行编译验证
            ValidationResponse response = validationService.validateCompile(request);

            // Then: 验证结果
            assertThat(response).isNotNull();
            assertThat(response.getPassed()).isTrue();
            assertThat(response.getStatus()).isEqualTo("PASSED");
            assertThat(response.getQualityScore()).isEqualTo(100);
            assertThat(response.getErrors()).isEmpty();

            // 验证方法调用
            verify(validationRequestAdapter).toCodeGenerationResult(request);
            verify(compilationValidator).compile(codeResult);
            verify(validationResultAdapter).toValidationResponse(compilationResult, appSpecId);
            verify(validationResultMapper).insert(any(ValidationResultEntity.class));
        }

        @Test
        @DisplayName("TypeScript编译失败 - 应返回FAILED状态和错误信息")
        void shouldFailWhenTypeScriptCompilationHasErrors() {
            // Given: TypeScript编译请求（有错误的代码）
            request = CompileValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .language("typescript")
                    .code("const hello: number = 'world';") // 类型错误
                    .build();

            // 模拟适配器转换
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("nextjs")
                    .projectRoot("/tmp/test-project")
                    .build();
            when(validationRequestAdapter.toCodeGenerationResult(any())).thenReturn(codeResult);

            // 模拟编译失败（含错误信息）
            List<CompilationResult.CompilationError> errors = List.of(
                    CompilationResult.CompilationError.builder()
                            .filePath("src/page.tsx")
                            .lineNumber(1)
                            .columnNumber(7)
                            .message("Type 'string' is not assignable to type 'number'")
                            .errorCode("TS2322")
                            .build()
            );

            CompilationResult compilationResult = CompilationResult.builder()
                    .success(false)
                    .compiler("tsc")
                    .compilerVersion("5.3.0")
                    .errors(errors)
                    .warnings(new ArrayList<>())
                    .durationMs(800L)
                    .command("tsc --noEmit")
                    .build();
            when(compilationValidator.compile(any())).thenReturn(compilationResult);

            // 模拟响应转换（含错误）
            List<String> errorMessages = List.of("src/page.tsx:1:7 - Type 'string' is not assignable to type 'number' (TS2322)");
            ValidationResponse expectedResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("compile")
                    .passed(false)
                    .status("FAILED")
                    .qualityScore(90) // 100 - 10 (1个错误)
                    .errors(errorMessages)
                    .warnings(new ArrayList<>())
                    .durationMs(800L)
                    .completedAt(Instant.now())
                    .details(new HashMap<>())
                    .build();
            when(validationResultAdapter.toValidationResponse(any(), any())).thenReturn(expectedResponse);

            // When: 执行编译验证
            ValidationResponse response = validationService.validateCompile(request);

            // Then: 验证结果
            assertThat(response).isNotNull();
            assertThat(response.getPassed()).isFalse();
            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(response.getQualityScore()).isEqualTo(90);
            assertThat(response.getErrors()).hasSize(1);
            assertThat(response.getErrors().get(0)).contains("Type 'string' is not assignable to type 'number'");

            // 验证方法调用
            verify(compilationValidator).compile(any());
            verify(validationResultMapper).insert(any(ValidationResultEntity.class));
        }
    }

    @Nested
    @DisplayName("Java编译验证测试")
    class JavaCompilation {

        @Test
        @DisplayName("Java编译成功 - 应返回PASSED状态")
        void shouldPassWhenJavaCompilationSucceeds() {
            // Given: Java编译请求
            request = CompileValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .language("java")
                    .code("public class Main { public static void main(String[] args) {} }")
                    .compilerVersion("17")
                    .build();

            // 模拟适配器转换
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("spring-boot")
                    .projectRoot("/tmp/test-project")
                    .build();
            when(validationRequestAdapter.toCodeGenerationResult(any())).thenReturn(codeResult);

            // 模拟编译成功
            CompilationResult compilationResult = CompilationResult.builder()
                    .success(true)
                    .compiler("javac + maven")
                    .compilerVersion("17")
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(3000L)
                    .command("mvn clean compile")
                    .build();
            when(compilationValidator.compile(any())).thenReturn(compilationResult);

            // 模拟响应转换
            ValidationResponse expectedResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("compile")
                    .passed(true)
                    .status("PASSED")
                    .qualityScore(100)
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(3000L)
                    .completedAt(Instant.now())
                    .details(new HashMap<>())
                    .build();
            when(validationResultAdapter.toValidationResponse(any(), any())).thenReturn(expectedResponse);

            // When: 执行编译验证
            ValidationResponse response = validationService.validateCompile(request);

            // Then: 验证结果
            assertThat(response).isNotNull();
            assertThat(response.getPassed()).isTrue();
            assertThat(response.getQualityScore()).isEqualTo(100);

            // 验证方法调用
            verify(validationRequestAdapter).toCodeGenerationResult(request);
            verify(compilationValidator).compile(codeResult);
        }

        @Test
        @DisplayName("Java编译失败 - 应正确解析Maven编译错误")
        void shouldParseJavaCompilationErrors() {
            // Given: Java编译请求（语法错误）
            request = CompileValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .language("java")
                    .code("public class Main { public static void main(String[] args) { undefinedVariable; } }")
                    .build();

            // 模拟适配器转换
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("spring-boot")
                    .projectRoot("/tmp/test-project")
                    .build();
            when(validationRequestAdapter.toCodeGenerationResult(any())).thenReturn(codeResult);

            // 模拟编译失败
            List<CompilationResult.CompilationError> errors = List.of(
                    CompilationResult.CompilationError.builder()
                            .filePath("src/main/java/Main.java")
                            .lineNumber(1)
                            .columnNumber(56)
                            .message("cannot find symbol: variable undefinedVariable")
                            .errorCode("compiler.err.cant.resolve.location")
                            .build()
            );

            CompilationResult compilationResult = CompilationResult.builder()
                    .success(false)
                    .compiler("javac + maven")
                    .errors(errors)
                    .warnings(new ArrayList<>())
                    .durationMs(2500L)
                    .build();
            when(compilationValidator.compile(any())).thenReturn(compilationResult);

            // 模拟响应转换
            List<String> errorMessages = List.of("src/main/java/Main.java:1:56 - cannot find symbol: variable undefinedVariable (compiler.err.cant.resolve.location)");
            ValidationResponse expectedResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("compile")
                    .passed(false)
                    .status("FAILED")
                    .qualityScore(90)
                    .errors(errorMessages)
                    .warnings(new ArrayList<>())
                    .durationMs(2500L)
                    .completedAt(Instant.now())
                    .details(new HashMap<>())
                    .build();
            when(validationResultAdapter.toValidationResponse(any(), any())).thenReturn(expectedResponse);

            // When: 执行编译验证
            ValidationResponse response = validationService.validateCompile(request);

            // Then: 验证错误解析
            assertThat(response.getErrors()).hasSize(1);
            assertThat(response.getErrors().get(0)).contains("cannot find symbol");
        }
    }

    @Nested
    @DisplayName("数据库持久化测试")
    class DatabasePersistence {

        @Test
        @DisplayName("验证结果应正确保存到数据库")
        void shouldPersistValidationResultToDatabase() {
            // Given: 编译验证请求
            request = CompileValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .language("typescript")
                    .code("const x = 1;")
                    .build();

            // 模拟所有依赖
            when(validationRequestAdapter.toCodeGenerationResult(any())).thenReturn(
                    CodeGenerationResult.builder().taskId(appSpecId).build()
            );
            when(compilationValidator.compile(any())).thenReturn(
                    CompilationResult.builder().success(true).durationMs(1000L).build()
            );
            when(validationResultAdapter.toValidationResponse(any(), any())).thenReturn(
                    ValidationResponse.builder()
                            .validationId(UUID.randomUUID())
                            .passed(true)
                            .status("PASSED")
                            .qualityScore(100)
                            .errors(new ArrayList<>())
                            .warnings(new ArrayList<>())
                            .durationMs(1000L)
                            .completedAt(Instant.now())
                            .details(new HashMap<>())
                            .build()
            );

            // When: 执行验证
            validationService.validateCompile(request);

            // Then: 验证数据库插入
            ArgumentCaptor<ValidationResultEntity> entityCaptor = ArgumentCaptor.forClass(ValidationResultEntity.class);
            verify(validationResultMapper).insert(entityCaptor.capture());

            ValidationResultEntity savedEntity = entityCaptor.getValue();
            assertThat(savedEntity).isNotNull();
            assertThat(savedEntity.getAppSpecId()).isEqualTo(appSpecId);
            assertThat(savedEntity.getValidationType()).isEqualTo("compile"); // 小写，匹配ValidationType.COMPILE.getValue()
            assertThat(savedEntity.getStatus()).isEqualTo("passed"); // 小写，匹配Status.PASSED.getValue()
            assertThat(savedEntity.getIsPassed()).isTrue();
            assertThat(savedEntity.getQualityScore()).isEqualTo(100);
            assertThat(savedEntity.getDurationMs()).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionHandling {

        @Test
        @DisplayName("当CompilationValidator抛出异常时应包装为RuntimeException")
        void shouldWrapCompilationValidatorException() {
            // Given: 编译器抛出异常
            request = CompileValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .language("typescript")
                    .code("const x = 1;")
                    .build();

            when(validationRequestAdapter.toCodeGenerationResult(any())).thenReturn(
                    CodeGenerationResult.builder().taskId(appSpecId).build()
            );
            when(compilationValidator.compile(any())).thenThrow(new RuntimeException("编译器崩溃"));

            // When & Then: 应包装为RuntimeException并包含原始错误信息
            assertThatThrownBy(() -> validationService.validateCompile(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("编译验证失败");
        }
    }

    @Nested
    @DisplayName("测试验证集成测试（Phase 2）")
    class TestValidation {

        @Test
        @DisplayName("单元测试执行成功 - 应返回PASSED状态")
        void shouldPassWhenUnitTestsSucceed() {
            // Given: 单元测试请求
            TestValidationRequest testRequest = TestValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .testType("unit")
                    .testFiles(List.of("src/test/UserServiceTest.java"))
                    .generateCoverage(true)
                    .build();

            // 模拟适配器转换
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("spring-boot")
                    .projectRoot("/tmp/test-project")
                    .build();
            when(validationRequestAdapter.toCodeGenerationResultForTest(any())).thenReturn(codeResult);

            // 模拟测试执行成功
            TestResult testResult = TestResult.builder()
                    .testType("unit")
                    .allPassed(true)
                    .totalTests(10)
                    .passedTests(10)
                    .failedTests(0)
                    .skippedTests(0)
                    .coverage(0.92)
                    .lineCoverage(0.92)
                    .branchCoverage(0.88)
                    .durationMs(2000L)
                    .framework("JUnit 5")
                    .failures(new ArrayList<>())
                    .build();
            when(testExecutor.runUnitTests(any())).thenReturn(testResult);

            // 模拟响应转换
            ValidationResponse expectedResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("unit")
                    .passed(true)
                    .status("PASSED")
                    .qualityScore(100)
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(2000L)
                    .completedAt(Instant.now())
                    .details(new HashMap<>())
                    .build();
            when(validationResultAdapter.toValidationResponseFromTestResult(any(), any())).thenReturn(expectedResponse);

            // When: 执行测试验证
            ValidationResponse response = validationService.validateTest(testRequest);

            // Then: 验证结果
            assertThat(response).isNotNull();
            assertThat(response.getPassed()).isTrue();
            assertThat(response.getStatus()).isEqualTo("PASSED");
            assertThat(response.getQualityScore()).isEqualTo(100);
            assertThat(response.getErrors()).isEmpty();

            // 验证方法调用
            verify(validationRequestAdapter).toCodeGenerationResultForTest(testRequest);
            verify(testExecutor).runUnitTests(codeResult);
            verify(validationResultAdapter).toValidationResponseFromTestResult(testResult, appSpecId);
            verify(validationResultMapper).insert(any(ValidationResultEntity.class));
        }

        @Test
        @DisplayName("单元测试失败 - 应返回FAILED状态和失败信息")
        void shouldFailWhenUnitTestsHaveFailures() {
            // Given: 单元测试请求（有失败的测试）
            TestValidationRequest testRequest = TestValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .testType("unit")
                    .testFiles(List.of("src/test/UserServiceTest.java"))
                    .generateCoverage(true)
                    .build();

            // 模拟适配器转换
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("spring-boot")
                    .projectRoot("/tmp/test-project")
                    .build();
            when(validationRequestAdapter.toCodeGenerationResultForTest(any())).thenReturn(codeResult);

            // 模拟测试失败（含失败信息）
            List<TestResult.TestFailure> failures = List.of(
                    TestResult.TestFailure.builder()
                            .suiteName("UserServiceTest")
                            .testName("testCreateUser")
                            .message("Expected 1 but was 2")
                            .stackTrace("at UserServiceTest.testCreateUser(UserServiceTest.java:42)")
                            .build()
            );

            TestResult testResult = TestResult.builder()
                    .testType("unit")
                    .allPassed(false)
                    .totalTests(10)
                    .passedTests(9)
                    .failedTests(1)
                    .skippedTests(0)
                    .coverage(0.85)
                    .durationMs(2500L)
                    .framework("JUnit 5")
                    .failures(failures)
                    .build();
            when(testExecutor.runUnitTests(any())).thenReturn(testResult);

            // 模拟响应转换（含失败）
            List<String> errorMessages = List.of("[UserServiceTest] testCreateUser - Expected 1 but was 2");
            ValidationResponse expectedResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("unit")
                    .passed(false)
                    .status("FAILED")
                    .qualityScore(87) // (90% pass rate × 50) + (85% coverage × 50) = 45 + 42.5 ≈ 87
                    .errors(errorMessages)
                    .warnings(new ArrayList<>())
                    .durationMs(2500L)
                    .completedAt(Instant.now())
                    .details(new HashMap<>())
                    .build();
            when(validationResultAdapter.toValidationResponseFromTestResult(any(), any())).thenReturn(expectedResponse);

            // When: 执行测试验证
            ValidationResponse response = validationService.validateTest(testRequest);

            // Then: 验证结果
            assertThat(response).isNotNull();
            assertThat(response.getPassed()).isFalse();
            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(response.getQualityScore()).isEqualTo(87);
            assertThat(response.getErrors()).hasSize(1);
            assertThat(response.getErrors().get(0)).contains("Expected 1 but was 2");

            // 验证方法调用
            verify(testExecutor).runUnitTests(any());
            verify(validationResultMapper).insert(any(ValidationResultEntity.class));
        }

        @Test
        @DisplayName("E2E测试执行成功 - 应调用正确的执行器")
        void shouldCallCorrectExecutorForE2ETests() {
            // Given: E2E测试请求
            TestValidationRequest testRequest = TestValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .testType("e2e")
                    .testFiles(List.of("e2e/login.spec.ts"))
                    .generateCoverage(false)
                    .build();

            // 模拟适配器转换
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("nextjs")
                    .projectRoot("/tmp/test-project")
                    .build();
            when(validationRequestAdapter.toCodeGenerationResultForTest(any())).thenReturn(codeResult);

            // 模拟E2E测试执行成功
            TestResult testResult = TestResult.builder()
                    .testType("e2e")
                    .allPassed(true)
                    .totalTests(5)
                    .passedTests(5)
                    .failedTests(0)
                    .skippedTests(0)
                    .coverage(null) // E2E测试通常不计算覆盖率
                    .durationMs(15000L)
                    .framework("Playwright")
                    .failures(new ArrayList<>())
                    .build();
            when(testExecutor.runE2ETests(any())).thenReturn(testResult);

            // 模拟响应转换
            ValidationResponse expectedResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("e2e")
                    .passed(true)
                    .status("PASSED")
                    .qualityScore(100)
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(15000L)
                    .completedAt(Instant.now())
                    .details(new HashMap<>())
                    .build();
            when(validationResultAdapter.toValidationResponseFromTestResult(any(), any())).thenReturn(expectedResponse);

            // When: 执行E2E测试验证
            ValidationResponse response = validationService.validateTest(testRequest);

            // Then: 验证调用了runE2ETests而非runUnitTests
            verify(testExecutor).runE2ETests(codeResult);
            verify(testExecutor, never()).runUnitTests(any());
            assertThat(response.getPassed()).isTrue();
        }

        @Test
        @DisplayName("不支持的测试类型 - 应抛出IllegalArgumentException")
        void shouldThrowExceptionForUnsupportedTestType() {
            // Given: 不支持的测试类型
            TestValidationRequest testRequest = TestValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .testType("invalid-type")
                    .testFiles(List.of("test.spec.ts"))
                    .build();

            // 模拟适配器转换
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("nextjs")
                    .build();
            when(validationRequestAdapter.toCodeGenerationResultForTest(any())).thenReturn(codeResult);

            // When & Then: 应抛出IllegalArgumentException
            assertThatThrownBy(() -> validationService.validateTest(testRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持的测试类型")
                    .hasMessageContaining("invalid-type");

            // 验证没有调用任何测试执行器
            verify(testExecutor, never()).runUnitTests(any());
            verify(testExecutor, never()).runE2ETests(any());
        }

        @Test
        @DisplayName("集成测试执行 - 应复用单元测试执行器")
        void shouldReuseUnitTestExecutorForIntegrationTests() {
            // Given: 集成测试请求
            TestValidationRequest testRequest = TestValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .testType("integration")
                    .testFiles(List.of("src/test/integration/ApiIntegrationTest.java"))
                    .generateCoverage(true)
                    .build();

            // 模拟适配器转换
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("spring-boot")
                    .build();
            when(validationRequestAdapter.toCodeGenerationResultForTest(any())).thenReturn(codeResult);

            // 模拟测试执行成功
            TestResult testResult = TestResult.builder()
                    .testType("integration")
                    .allPassed(true)
                    .totalTests(8)
                    .passedTests(8)
                    .failedTests(0)
                    .coverage(0.88)
                    .durationMs(5000L)
                    .framework("JUnit 5")
                    .failures(new ArrayList<>())
                    .build();
            when(testExecutor.runUnitTests(any())).thenReturn(testResult);

            // 模拟响应转换
            ValidationResponse expectedResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("integration")
                    .passed(true)
                    .status("PASSED")
                    .qualityScore(100)
                    .build();
            when(validationResultAdapter.toValidationResponseFromTestResult(any(), any())).thenReturn(expectedResponse);

            // When: 执行集成测试验证
            ValidationResponse response = validationService.validateTest(testRequest);

            // Then: 验证调用了runUnitTests（集成测试复用单元测试执行器）
            verify(testExecutor).runUnitTests(codeResult);
            verify(testExecutor, never()).runE2ETests(any());
            assertThat(response.getPassed()).isTrue();
        }

        @Test
        @DisplayName("测试验证数据库持久化 - 应正确保存到数据库")
        void shouldPersistTestValidationResultToDatabase() {
            // Given: 测试验证请求
            TestValidationRequest testRequest = TestValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .testType("unit")
                    .testFiles(List.of("test.spec.ts"))
                    .build();

            // 模拟所有依赖
            when(validationRequestAdapter.toCodeGenerationResultForTest(any())).thenReturn(
                    CodeGenerationResult.builder().taskId(appSpecId).build()
            );
            when(testExecutor.runUnitTests(any())).thenReturn(
                    TestResult.builder()
                            .allPassed(true)
                            .totalTests(10)
                            .passedTests(10)
                            .coverage(0.90)
                            .durationMs(3000L)
                            .build()
            );
            when(validationResultAdapter.toValidationResponseFromTestResult(any(), any())).thenReturn(
                    ValidationResponse.builder()
                            .validationId(UUID.randomUUID())
                            .validationType("unit")
                            .passed(true)
                            .status("PASSED")
                            .qualityScore(100)
                            .errors(new ArrayList<>())
                            .warnings(new ArrayList<>())
                            .durationMs(3000L)
                            .completedAt(Instant.now())
                            .details(new HashMap<>())
                            .build()
            );

            // When: 执行验证
            validationService.validateTest(testRequest);

            // Then: 验证数据库插入
            ArgumentCaptor<ValidationResultEntity> entityCaptor = ArgumentCaptor.forClass(ValidationResultEntity.class);
            verify(validationResultMapper).insert(entityCaptor.capture());

            ValidationResultEntity savedEntity = entityCaptor.getValue();
            assertThat(savedEntity).isNotNull();
            assertThat(savedEntity.getAppSpecId()).isEqualTo(appSpecId);
            assertThat(savedEntity.getValidationType()).isEqualTo("test"); // 小写，匹配ValidationType.TEST.getValue()
            assertThat(savedEntity.getStatus()).isEqualTo("passed"); // 小写，匹配Status.PASSED.getValue()
            assertThat(savedEntity.getIsPassed()).isTrue();
            assertThat(savedEntity.getQualityScore()).isEqualTo(100);
            assertThat(savedEntity.getDurationMs()).isEqualTo(3000L);
        }
    }

    /**
     * 覆盖率验证测试（Phase 3.3）
     */
    @Nested
    @DisplayName("覆盖率验证")
    class CoverageValidation {

        private final String projectRoot = "/tmp/test-project";
        private final String projectType = "nextjs";

        @Test
        @DisplayName("Istanbul覆盖率验证成功 - 应返回PASSED状态")
        void shouldPassWhenIstanbulCoverageMeetsQualityGate() {
            // Given: 覆盖率计算结果（Istanbul，覆盖率≥85%）
            com.ingenio.backend.dto.CoverageResult coverageResult =
                    com.ingenio.backend.dto.CoverageResult.builder()
                            .projectType("nextjs")
                            .tool("istanbul")
                            .overallCoverage(0.92)
                            .lineCoverage(0.90)
                            .branchCoverage(0.94)
                            .functionCoverage(0.95)
                            .statementCoverage(0.91)
                            .meetsQualityGate(true)
                            .reportPath("/tmp/test-project/coverage/coverage-summary.json")
                            .filesCoverage(new java.util.HashMap<>())
                            .build();

            when(coverageCalculator.calculate(projectRoot, projectType)).thenReturn(coverageResult);

            // Mock适配器返回
            ValidationResponse mockResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("coverage")
                    .passed(true)
                    .status("PASSED")
                    .qualityScore(92)
                    .details(new java.util.HashMap<>())
                    .errors(new java.util.ArrayList<>())
                    .warnings(new java.util.ArrayList<>())
                    .durationMs(0L)
                    .completedAt(java.time.Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromCoverageResult(
                    coverageResult, appSpecId)).thenReturn(mockResponse);

            // When: 执行覆盖率验证
            ValidationResponse response = validationService.validateCoverage(
                    appSpecId, projectRoot, projectType);

            // Then: 验证结果
            assertThat(response).isNotNull();
            assertThat(response.getPassed()).isTrue();
            assertThat(response.getStatus()).isEqualTo("PASSED");
            assertThat(response.getValidationType()).isEqualTo("coverage");
            assertThat(response.getQualityScore()).isEqualTo(92); // (int)(0.92 * 100)

            // 验证方法调用
            verify(coverageCalculator).calculate(projectRoot, projectType);
            verify(validationResultAdapter).toValidationResponseFromCoverageResult(
                    coverageResult, appSpecId);
            verify(validationResultMapper).insert(any(ValidationResultEntity.class));
        }

        @Test
        @DisplayName("JaCoCo覆盖率验证成功 - 应返回PASSED状态")
        void shouldPassWhenJacocoCoverageMeetsQualityGate() {
            // Given: JaCoCo覆盖率计算结果
            com.ingenio.backend.dto.CoverageResult coverageResult =
                    com.ingenio.backend.dto.CoverageResult.builder()
                            .projectType("spring-boot")
                            .tool("jacoco")
                            .overallCoverage(0.88)
                            .lineCoverage(0.90)
                            .branchCoverage(0.86)
                            .functionCoverage(0.92)
                            .statementCoverage(null) // JaCoCo不提供语句覆盖率
                            .meetsQualityGate(true)
                            .reportPath("/tmp/test-project/target/site/jacoco/jacoco.xml")
                            .filesCoverage(new java.util.HashMap<>())
                            .build();

            when(coverageCalculator.calculate(projectRoot, "spring-boot")).thenReturn(coverageResult);

            // Mock适配器返回
            ValidationResponse mockResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("coverage")
                    .passed(true)
                    .status("PASSED")
                    .qualityScore(88)
                    .details(new java.util.HashMap<>())
                    .errors(new java.util.ArrayList<>())
                    .warnings(new java.util.ArrayList<>())
                    .durationMs(0L)
                    .completedAt(java.time.Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromCoverageResult(
                    coverageResult, appSpecId)).thenReturn(mockResponse);

            // When: 执行覆盖率验证
            ValidationResponse response = validationService.validateCoverage(
                    appSpecId, projectRoot, "spring-boot");

            // Then: 验证结果
            assertThat(response).isNotNull();
            assertThat(response.getPassed()).isTrue();
            assertThat(response.getQualityScore()).isEqualTo(88);

            verify(coverageCalculator).calculate(projectRoot, "spring-boot");
        }

        @Test
        @DisplayName("覆盖率不足85% - 应返回FAILED状态和警告")
        void shouldFailWhenCoverageBelowQualityGate() {
            // Given: 覆盖率不足（70%）
            com.ingenio.backend.dto.CoverageResult coverageResult =
                    com.ingenio.backend.dto.CoverageResult.builder()
                            .projectType("nextjs")
                            .tool("istanbul")
                            .overallCoverage(0.70)
                            .lineCoverage(0.72)
                            .branchCoverage(0.68)
                            .functionCoverage(0.75)
                            .statementCoverage(0.71)
                            .meetsQualityGate(false)
                            .reportPath("/tmp/test-project/coverage/coverage-summary.json")
                            .filesCoverage(new java.util.HashMap<>())
                            .build();

            when(coverageCalculator.calculate(projectRoot, projectType)).thenReturn(coverageResult);

            // Mock适配器返回包含警告的响应
            ValidationResponse mockResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("coverage")
                    .passed(false)
                    .status("FAILED")
                    .qualityScore(70)
                    .details(new java.util.HashMap<>())
                    .errors(new java.util.ArrayList<>())
                    .warnings(java.util.List.of("代码覆盖率不足：70.00% （要求≥85%）"))
                    .durationMs(0L)
                    .completedAt(java.time.Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromCoverageResult(
                    coverageResult, appSpecId)).thenReturn(mockResponse);

            // When: 执行覆盖率验证
            ValidationResponse response = validationService.validateCoverage(
                    appSpecId, projectRoot, projectType);

            // Then: 验证结果
            assertThat(response).isNotNull();
            assertThat(response.getPassed()).isFalse();
            assertThat(response.getStatus()).isEqualTo("FAILED");
            assertThat(response.getWarnings()).isNotEmpty();
            assertThat(response.getWarnings().get(0)).contains("70.00%").contains("≥85%");
        }

        @Test
        @DisplayName("覆盖率报告缺失 - 应返回0覆盖率")
        void shouldReturnZeroCoverageWhenReportMissing() {
            // Given: 覆盖率报告不存在，CoverageCalculator返回0覆盖率
            com.ingenio.backend.dto.CoverageResult zeroCoverage =
                    com.ingenio.backend.dto.CoverageResult.zero("nextjs", "istanbul");

            when(coverageCalculator.calculate(projectRoot, projectType)).thenReturn(zeroCoverage);

            // Mock适配器返回
            ValidationResponse mockResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("coverage")
                    .passed(false)
                    .status("FAILED")
                    .qualityScore(0)
                    .details(new java.util.HashMap<>())
                    .errors(new java.util.ArrayList<>())
                    .warnings(java.util.List.of("代码覆盖率不足：0.00% （要求≥85%）"))
                    .durationMs(0L)
                    .completedAt(java.time.Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromCoverageResult(
                    zeroCoverage, appSpecId)).thenReturn(mockResponse);

            // When: 执行覆盖率验证
            ValidationResponse response = validationService.validateCoverage(
                    appSpecId, projectRoot, projectType);

            // Then: 验证结果
            assertThat(response).isNotNull();
            assertThat(response.getPassed()).isFalse();

            verify(coverageCalculator).calculate(projectRoot, projectType);
        }

        @Test
        @DisplayName("不支持的项目类型 - 应抛出异常")
        void shouldThrowExceptionForUnsupportedProjectType() {
            // Given: 不支持的项目类型
            String unsupportedType = "unsupported";
            when(coverageCalculator.calculate(projectRoot, unsupportedType))
                    .thenThrow(new IllegalArgumentException("不支持的项目类型: " + unsupportedType));

            // When & Then: 执行覆盖率验证应抛出异常
            assertThatThrownBy(() ->
                    validationService.validateCoverage(appSpecId, projectRoot, unsupportedType))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("覆盖率验证失败");

            verify(coverageCalculator).calculate(projectRoot, unsupportedType);
        }

        @Test
        @DisplayName("持久化覆盖率验证结果到数据库 - 验证ValidationType为COVERAGE")
        void shouldPersistCoverageValidationResultToDatabase() {
            // Given: 覆盖率计算结果
            com.ingenio.backend.dto.CoverageResult coverageResult =
                    com.ingenio.backend.dto.CoverageResult.builder()
                            .projectType("nextjs")
                            .tool("istanbul")
                            .overallCoverage(0.92)
                            .lineCoverage(0.90)
                            .branchCoverage(0.94)
                            .functionCoverage(0.95)
                            .statementCoverage(0.91)
                            .meetsQualityGate(true)
                            .reportPath("/tmp/test-project/coverage/coverage-summary.json")
                            .filesCoverage(new java.util.HashMap<>())
                            .build();

            when(coverageCalculator.calculate(projectRoot, projectType)).thenReturn(coverageResult);

            // Mock适配器返回
            ValidationResponse mockResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("coverage")
                    .passed(true)
                    .status("PASSED")
                    .qualityScore(92)
                    .details(new java.util.HashMap<>())
                    .errors(new java.util.ArrayList<>())
                    .warnings(new java.util.ArrayList<>())
                    .durationMs(0L)
                    .completedAt(java.time.Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromCoverageResult(
                    coverageResult, appSpecId)).thenReturn(mockResponse);

            // When: 执行覆盖率验证
            validationService.validateCoverage(appSpecId, projectRoot, projectType);

            // Then: 验证数据库插入操作（使用ArgumentCaptor捕获实际插入的实体）
            ArgumentCaptor<ValidationResultEntity> entityCaptor =
                    ArgumentCaptor.forClass(ValidationResultEntity.class);
            verify(validationResultMapper).insert(entityCaptor.capture());

            ValidationResultEntity savedEntity = entityCaptor.getValue();
            assertThat(savedEntity).isNotNull();
            assertThat(savedEntity.getAppSpecId()).isEqualTo(appSpecId);
            assertThat(savedEntity.getValidationType()).isEqualTo("coverage"); // 小写，匹配ValidationType.COVERAGE.getValue()
            assertThat(savedEntity.getStatus()).isEqualTo("passed"); // 小写，匹配Status.PASSED.getValue()
            assertThat(savedEntity.getIsPassed()).isTrue();
            assertThat(savedEntity.getQualityScore()).isEqualTo(92);
        }
    }

    /**
     * Phase 4: validateFull三环验证测试
     *
     * 测试场景：
     * 1. 串行验证 - 所有阶段成功
     * 2. 串行验证 - failFast=true，第二阶段失败
     * 3. 串行验证 - failFast=false，继续执行所有阶段
     * 4. 并行验证 - 所有阶段成功
     * 5. coverage阶段验证
     */
    @Nested
    @DisplayName("Phase 4: validateFull三环验证")
    class FullValidation {

        private UUID appSpecId;
        private UUID tenantId;

        @BeforeEach
        void setUp() {
            appSpecId = UUID.randomUUID();
            tenantId = UUID.randomUUID();
        }

        @Test
        @DisplayName("串行验证 - 所有阶段成功（compile→test→coverage）")
        void shouldPassAllStagesInSequence() {
            // Given: 三个阶段的验证请求
            FullValidationRequest request = FullValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .tenantId(tenantId)
                    .stages(Arrays.asList("compile", "test", "coverage"))
                    .code("export function greet(name: string): string { return `Hello, ${name}!`; }")
                    .language("typescript")
                    .projectRoot("/tmp/test-project")
                    .projectType("nextjs")
                    .testFiles(new ArrayList<>())
                    .parallel(false)
                    .failFast(false)
                    .build();

            // Mock validationRequestAdapter转换请求
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("nextjs")
                    .projectRoot("/tmp/test-project")
                    .build();

            when(validationRequestAdapter.toCodeGenerationResult(any(CompileValidationRequest.class)))
                    .thenReturn(codeResult);

            when(validationRequestAdapter.toCodeGenerationResultForTest(any(TestValidationRequest.class)))
                    .thenReturn(codeResult);

            // Mock编译验证成功
            when(compilationValidator.compile(any(CodeGenerationResult.class)))
                    .thenReturn(CompilationResult.builder()
                            .success(true)
                            .errors(new ArrayList<>())
                            .warnings(new ArrayList<>())
                            .build());

            ValidationResponse compileResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("compile")
                    .passed(true)
                    .status("passed")
                    .qualityScore(100)
                    .details(new HashMap<>())
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(100L)
                    .completedAt(Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponse(any(CompilationResult.class), eq(appSpecId)))
                    .thenReturn(compileResponse);

            // Mock测试验证成功
            when(testExecutor.runUnitTests(any(CodeGenerationResult.class)))
                    .thenReturn(TestResult.builder()

                            .totalTests(10)
                            .passedTests(10)
                            .allPassed(true)
                            .failedTests(0)
                            .build());

            ValidationResponse testResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("test")
                    .passed(true)
                    .status("passed")
                    .qualityScore(100)
                    .details(Map.of("totalTests", 10, "passedTests", 10))
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(200L)
                    .completedAt(Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromTestResult(any(TestResult.class), eq(appSpecId)))
                    .thenReturn(testResponse);

            // Mock覆盖率验证成功
            when(coverageCalculator.calculate("/tmp/test-project", "nextjs"))
                    .thenReturn(com.ingenio.backend.dto.CoverageResult.builder()
                            .projectType("nextjs")
                            .tool("istanbul")
                            .overallCoverage(0.92)
                            .lineCoverage(0.90)
                            .branchCoverage(0.94)
                            .meetsQualityGate(true)
                            .build());

            ValidationResponse coverageResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("coverage")
                    .passed(true)
                    .status("passed")
                    .qualityScore(92)
                    .details(new HashMap<>())
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(150L)
                    .completedAt(Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromCoverageResult(any(), eq(appSpecId)))
                    .thenReturn(coverageResponse);

            // When: 执行三环集成验证
            FullValidationResponse response = validationService.validateFull(request);

            // Then: 验证所有阶段都成功
            assertThat(response).isNotNull();
            assertThat(response.getOverallStatus()).isEqualTo("passed");
            assertThat(response.getStages()).hasSize(3);

            // 验证compile阶段
            FullValidationResponse.StageResult compileStage = response.getStages().get("compile");
            assertThat(compileStage).isNotNull();
            assertThat(compileStage.getPassed()).isTrue();
            assertThat(compileStage.getStatus()).isEqualTo("passed");

            // 验证test阶段
            FullValidationResponse.StageResult testStage = response.getStages().get("test");
            assertThat(testStage).isNotNull();
            assertThat(testStage.getPassed()).isTrue();
            assertThat(testStage.getStatus()).isEqualTo("passed");

            // 验证coverage阶段
            FullValidationResponse.StageResult coverageStage = response.getStages().get("coverage");
            assertThat(coverageStage).isNotNull();
            assertThat(coverageStage.getPassed()).isTrue();
            assertThat(coverageStage.getStatus()).isEqualTo("passed");

            // 验证方法调用
            verify(compilationValidator).compile(any(CodeGenerationResult.class));
            verify(testExecutor).runUnitTests(any(CodeGenerationResult.class));
            verify(coverageCalculator).calculate("/tmp/test-project", "nextjs");
        }

        @Test
        @DisplayName("串行验证 - failFast=true，test阶段失败，跳过coverage")
        void shouldStopOnFirstFailureWhenFailFastEnabled() {
            // Given: failFast=true，test阶段失败
            FullValidationRequest request = FullValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .tenantId(tenantId)
                    .stages(Arrays.asList("compile", "test", "coverage"))
                    .code("export function test(): void {}")
                    .language("typescript")
                    .projectRoot("/tmp/test-project")
                    .projectType("nextjs")
                    .parallel(false)
                    .failFast(true)  // 启用failFast
                    .build();

            // Mock validationRequestAdapter转换请求
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("nextjs")
                    .projectRoot("/tmp/test-project")
                    .build();

            when(validationRequestAdapter.toCodeGenerationResult(any(CompileValidationRequest.class)))
                    .thenReturn(codeResult);

            when(validationRequestAdapter.toCodeGenerationResultForTest(any(TestValidationRequest.class)))
                    .thenReturn(codeResult);

            // Mock编译验证成功
            when(compilationValidator.compile(any(CodeGenerationResult.class)))
                    .thenReturn(CompilationResult.builder()

                            .success(true)
                            .errors(new ArrayList<>())
                            .build());

            ValidationResponse compileResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("compile")
                    .passed(true)
                    .status("passed")
                    .qualityScore(100)
                    .details(new HashMap<>())
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(100L)
                    .completedAt(Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponse(any(CompilationResult.class), eq(appSpecId)))
                    .thenReturn(compileResponse);

            // Mock测试验证失败
            when(testExecutor.runUnitTests(any(CodeGenerationResult.class)))
                    .thenReturn(TestResult.builder()

                            .totalTests(10)
                            .passedTests(8)
                            .allPassed(false)
                            .failedTests(2)  // 2个测试失败
                            .build());

            ValidationResponse testResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("test")
                    .passed(false)  // 测试失败
                    .status("failed")
                    .qualityScore(80)
                    .details(Map.of("totalTests", 10, "passedTests", 8, "failedTests", 2))
                    .errors(Arrays.asList("Test case 1 failed", "Test case 2 failed"))
                    .warnings(new ArrayList<>())
                    .durationMs(200L)
                    .completedAt(Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromTestResult(any(TestResult.class), eq(appSpecId)))
                    .thenReturn(testResponse);

            // When: 执行三环集成验证
            FullValidationResponse response = validationService.validateFull(request);

            // Then: test失败后，coverage阶段被跳过
            assertThat(response).isNotNull();
            assertThat(response.getOverallStatus()).isEqualTo("failed");
            assertThat(response.getStages()).hasSize(3);

            // compile阶段成功
            assertThat(response.getStages().get("compile").getPassed()).isTrue();

            // test阶段失败
            FullValidationResponse.StageResult testStage = response.getStages().get("test");
            assertThat(testStage.getPassed()).isFalse();
            assertThat(testStage.getStatus()).isEqualTo("failed");
            assertThat(testStage.getErrorMessage()).contains("Test case");

            // coverage阶段被跳过
            FullValidationResponse.StageResult coverageStage = response.getStages().get("coverage");
            assertThat(coverageStage).isNotNull();
            assertThat(coverageStage.getStatus()).isEqualTo("skipped");
            assertThat(coverageStage.getPassed()).isFalse();
            assertThat(coverageStage.getErrorMessage()).isEqualTo("前置阶段验证失败");

            // 验证coverageCalculator没有被调用（因为被跳过）
            verify(coverageCalculator, never()).calculate(anyString(), anyString());
        }

        @Test
        @DisplayName("并行验证 - 所有阶段并行执行成功")
        void shouldRunAllStagesInParallel() throws Exception {
            // Given: parallel=true，需要提供Executor用于并行执行
            // 使用反射注入测试用的Executor
            java.util.concurrent.Executor threadPoolExecutor = java.util.concurrent.Executors.newFixedThreadPool(3);
            java.lang.reflect.Field executorField = ValidationService.class.getDeclaredField("validationExecutor");
            executorField.setAccessible(true);
            executorField.set(validationService, threadPoolExecutor);

            FullValidationRequest request = FullValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .tenantId(tenantId)
                    .stages(Arrays.asList("compile", "test"))
                    .code("export function test(): void {}")
                    .language("typescript")
                    .projectRoot("/tmp/test-project")
                    .projectType("nextjs")
                    .parallel(true)  // 启用并行验证
                    .failFast(false)
                    .build();

            // Mock validationRequestAdapter转换请求
            CodeGenerationResult codeResult = CodeGenerationResult.builder()
                    .taskId(appSpecId)
                    .projectType("nextjs")
                    .projectRoot("/tmp/test-project")
                    .build();

            when(validationRequestAdapter.toCodeGenerationResult(any(CompileValidationRequest.class)))
                    .thenReturn(codeResult);

            when(validationRequestAdapter.toCodeGenerationResultForTest(any(TestValidationRequest.class)))
                    .thenReturn(codeResult);

            // Mock编译验证成功
            when(compilationValidator.compile(any(CodeGenerationResult.class)))
                    .thenReturn(CompilationResult.builder()

                            .success(true)
                            .errors(new ArrayList<>())
                            .build());

            ValidationResponse compileResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("compile")
                    .passed(true)
                    .status("passed")
                    .qualityScore(100)
                    .details(new HashMap<>())
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(100L)
                    .completedAt(Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponse(any(CompilationResult.class), eq(appSpecId)))
                    .thenReturn(compileResponse);

            // Mock测试验证成功
            when(testExecutor.runUnitTests(any(CodeGenerationResult.class)))
                    .thenReturn(TestResult.builder()

                            .totalTests(10)
                            .passedTests(10)
                            .allPassed(true)
                            .failedTests(0)
                            .build());

            ValidationResponse testResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("test")
                    .passed(true)
                    .status("passed")
                    .qualityScore(100)
                    .details(Map.of("totalTests", 10, "passedTests", 10))
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(200L)
                    .completedAt(Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromTestResult(any(TestResult.class), eq(appSpecId)))
                    .thenReturn(testResponse);

            // When: 执行并行验证
            FullValidationResponse response = validationService.validateFull(request);

            // Then: 所有阶段都成功
            assertThat(response).isNotNull();
            assertThat(response.getOverallStatus()).isEqualTo("passed");
            assertThat(response.getStages()).hasSize(2);

            assertThat(response.getStages().get("compile").getPassed()).isTrue();
            assertThat(response.getStages().get("test").getPassed()).isTrue();

            // 验证两个阶段都被执行
            verify(compilationValidator).compile(any(CodeGenerationResult.class));
            verify(testExecutor).runUnitTests(any(CodeGenerationResult.class));
        }

        @Test
        @DisplayName("coverage阶段独立验证 - 成功")
        void shouldValidateCoverageStage() {
            // Given: 只验证coverage阶段
            FullValidationRequest request = FullValidationRequest.builder()
                    .appSpecId(appSpecId)
                    .tenantId(tenantId)
                    .stages(Arrays.asList("coverage"))
                    .projectRoot("/tmp/test-project")
                    .projectType("spring-boot")
                    .parallel(false)
                    .failFast(false)
                    .build();

            // Mock覆盖率验证成功
            when(coverageCalculator.calculate("/tmp/test-project", "spring-boot"))
                    .thenReturn(com.ingenio.backend.dto.CoverageResult.builder()
                            .projectType("spring-boot")
                            .tool("jacoco")
                            .overallCoverage(0.88)
                            .lineCoverage(0.85)
                            .branchCoverage(0.91)
                            .meetsQualityGate(true)
                            .build());

            ValidationResponse coverageResponse = ValidationResponse.builder()
                    .validationId(UUID.randomUUID())
                    .validationType("coverage")
                    .passed(true)
                    .status("passed")
                    .qualityScore(88)
                    .details(new HashMap<>())
                    .errors(new ArrayList<>())
                    .warnings(new ArrayList<>())
                    .durationMs(150L)
                    .completedAt(Instant.now())
                    .build();

            when(validationResultAdapter.toValidationResponseFromCoverageResult(any(), eq(appSpecId)))
                    .thenReturn(coverageResponse);

            // When: 执行coverage验证
            FullValidationResponse response = validationService.validateFull(request);

            // Then: coverage阶段成功
            assertThat(response).isNotNull();
            assertThat(response.getOverallStatus()).isEqualTo("passed");
            assertThat(response.getStages()).hasSize(1);

            FullValidationResponse.StageResult coverageStage = response.getStages().get("coverage");
            assertThat(coverageStage).isNotNull();
            assertThat(coverageStage.getPassed()).isTrue();
            assertThat(coverageStage.getStatus()).isEqualTo("passed");

            verify(coverageCalculator).calculate("/tmp/test-project", "spring-boot");
        }
    }
}
