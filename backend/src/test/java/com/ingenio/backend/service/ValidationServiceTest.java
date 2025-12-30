package com.ingenio.backend.service;

import com.ingenio.backend.dto.CodeGenerationResult;
import com.ingenio.backend.dto.CompilationResult;
import com.ingenio.backend.dto.request.validation.CompileValidationRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

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
}
