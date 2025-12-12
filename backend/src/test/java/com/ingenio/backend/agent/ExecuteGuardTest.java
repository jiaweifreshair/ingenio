package com.ingenio.backend.agent;

import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * ExecuteGuard单元测试
 *
 * 测试覆盖所有前置条件检查场景：
 * 1. AppSpec存在性检查
 * 2. 意图识别完成检查
 * 3. 设计风格选择检查
 * 4. 前端原型代码存在检查
 * 5. 原型预览URL可访问性检查
 * 6. 用户设计确认检查
 * 7. 检查报告生成
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecuteGuard - Execute阶段前置条件检查守卫测试")
class ExecuteGuardTest {

    @Mock
    private AppSpecMapper appSpecMapper;

    @InjectMocks
    private ExecuteGuard executeGuard;

    private UUID testAppSpecId;
    private AppSpecEntity validAppSpec;

    @BeforeEach
    void setUp() {
        testAppSpecId = UUID.randomUUID();

        // 创建满足所有条件的有效AppSpec
        Map<String, Object> frontendPrototype = new HashMap<>();
        frontendPrototype.put("app.tsx", "export default function App() { return <div>Hello</div>; }");
        frontendPrototype.put("package.json", "{ \"name\": \"prototype\" }");

        validAppSpec = AppSpecEntity.builder()
            .id(testAppSpecId)
            .intentType("DESIGN_FROM_SCRATCH")
            .selectedStyle("A")
            .frontendPrototype(frontendPrototype)
            .frontendPrototypeUrl("https://example.com/preview")
            .designConfirmed(true)
            .designConfirmedAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("AppSpec存在性检查")
    class AppSpecExistsCheck {

        @Test
        @DisplayName("当AppSpec不存在时应抛出EXECUTE_GUARD_APPSPEC_NOT_FOUND异常")
        void shouldThrowExceptionWhenAppSpecNotFound() {
            // Given
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_APPSPEC_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("当AppSpec存在时不应抛出异常")
        void shouldNotThrowExceptionWhenAppSpecExists() {
            // Given - 使用不可访问的URL触发后续检查失败
            validAppSpec.setFrontendPrototypeUrl("invalid-url");
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then - 不会因为AppSpec不存在而失败，会因为URL无效而失败
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_PROTOTYPE_URL_INVALID.getCode());
        }
    }

    @Nested
    @DisplayName("意图识别完成检查")
    class IntentClassifiedCheck {

        @Test
        @DisplayName("当意图类型为空时应抛出EXECUTE_GUARD_INTENT_NOT_CLASSIFIED异常")
        void shouldThrowExceptionWhenIntentTypeIsNull() {
            // Given
            validAppSpec.setIntentType(null);
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_INTENT_NOT_CLASSIFIED.getCode());
        }

        @Test
        @DisplayName("当意图类型为空字符串时应抛出异常")
        void shouldThrowExceptionWhenIntentTypeIsEmpty() {
            // Given
            validAppSpec.setIntentType("");
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_INTENT_NOT_CLASSIFIED.getCode());
        }

        @Test
        @DisplayName("当意图类型为有效值时不应抛出异常")
        void shouldNotThrowExceptionWhenIntentTypeIsValid() {
            // Given - 设置无效URL使测试在URL检查阶段失败
            validAppSpec.setIntentType("CLONE_EXISTING_WEBSITE");
            validAppSpec.setFrontendPrototypeUrl("invalid-url");
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then - 应该通过意图检查，在URL检查失败
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_PROTOTYPE_URL_INVALID.getCode());
        }
    }

    @Nested
    @DisplayName("设计风格选择检查")
    class StyleSelectedCheck {

        @Test
        @DisplayName("当未选择设计风格时应抛出EXECUTE_GUARD_NO_SELECTED_STYLE异常")
        void shouldThrowExceptionWhenStyleNotSelected() {
            // Given
            validAppSpec.setSelectedStyle(null);
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_NO_SELECTED_STYLE.getCode());
        }

        @Test
        @DisplayName("当设计风格为空字符串时应抛出异常")
        void shouldThrowExceptionWhenStyleIsEmpty() {
            // Given
            validAppSpec.setSelectedStyle("  ");
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_NO_SELECTED_STYLE.getCode());
        }
    }

    @Nested
    @DisplayName("前端原型代码检查")
    class FrontendPrototypeCheck {

        @Test
        @DisplayName("当前端原型代码为null时应抛出EXECUTE_GUARD_NO_FRONTEND_PROTOTYPE异常")
        void shouldThrowExceptionWhenPrototypeIsNull() {
            // Given
            validAppSpec.setFrontendPrototype(null);
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_NO_FRONTEND_PROTOTYPE.getCode());
        }

        @Test
        @DisplayName("当前端原型代码为空Map时应抛出异常")
        void shouldThrowExceptionWhenPrototypeIsEmpty() {
            // Given
            validAppSpec.setFrontendPrototype(new HashMap<>());
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_NO_FRONTEND_PROTOTYPE.getCode());
        }
    }

    @Nested
    @DisplayName("原型预览URL检查")
    class PrototypeUrlCheck {

        @Test
        @DisplayName("当原型预览URL为空时应抛出EXECUTE_GUARD_PROTOTYPE_URL_INVALID异常")
        void shouldThrowExceptionWhenUrlIsNull() {
            // Given
            validAppSpec.setFrontendPrototypeUrl(null);
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_PROTOTYPE_URL_INVALID.getCode());
        }

        @Test
        @DisplayName("当原型预览URL为空字符串时应抛出异常")
        void shouldThrowExceptionWhenUrlIsEmpty() {
            // Given
            validAppSpec.setFrontendPrototypeUrl("");
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_PROTOTYPE_URL_INVALID.getCode());
        }

        @Test
        @DisplayName("当原型预览URL格式无效时应抛出异常")
        void shouldThrowExceptionWhenUrlIsInvalid() {
            // Given
            validAppSpec.setFrontendPrototypeUrl("not-a-valid-url");
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_PROTOTYPE_URL_INVALID.getCode());
        }
    }

    @Nested
    @DisplayName("设计确认检查")
    class DesignConfirmedCheck {

        @Test
        @DisplayName("当用户未确认设计时应抛出EXECUTE_GUARD_PROTOTYPE_NOT_CONFIRMED异常")
        void shouldThrowExceptionWhenDesignNotConfirmed() {
            // Given - 设置可访问的URL（使用httpbin.org）
            validAppSpec.setFrontendPrototypeUrl("https://httpbin.org/get");
            validAppSpec.setDesignConfirmed(false);
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_PROTOTYPE_NOT_CONFIRMED.getCode());
        }

        @Test
        @DisplayName("当设计确认标志为null时应抛出异常")
        void shouldThrowExceptionWhenDesignConfirmedIsNull() {
            // Given
            validAppSpec.setFrontendPrototypeUrl("https://httpbin.org/get");
            validAppSpec.setDesignConfirmed(null);
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When & Then
            assertThatThrownBy(() -> executeGuard.checkPreconditions(testAppSpecId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.EXECUTE_GUARD_PROTOTYPE_NOT_CONFIRMED.getCode());
        }
    }

    @Nested
    @DisplayName("检查报告生成")
    class CheckReportGeneration {

        @Test
        @DisplayName("当AppSpec不存在时报告应显示所有检查失败")
        void shouldReturnAllFailedWhenAppSpecNotFound() {
            // Given
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(null);

            // When
            ExecuteGuardReport report = executeGuard.getCheckReport(testAppSpecId);

            // Then
            assertThat(report.isAppSpecExists()).isFalse();
            assertThat(report.isIntentClassified()).isFalse();
            assertThat(report.isStyleSelected()).isFalse();
            assertThat(report.isFrontendPrototypeExists()).isFalse();
            assertThat(report.isPrototypeUrlAccessible()).isFalse();
            assertThat(report.isDesignConfirmed()).isFalse();
            assertThat(report.isAllChecksPassed()).isFalse();
            assertThat(report.getProgressPercentage()).isEqualTo(0);
        }

        @Test
        @DisplayName("当部分检查通过时报告应正确反映进度")
        void shouldReflectPartialProgress() {
            // Given
            validAppSpec.setDesignConfirmed(false);
            validAppSpec.setFrontendPrototypeUrl("invalid-url");
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When
            ExecuteGuardReport report = executeGuard.getCheckReport(testAppSpecId);

            // Then
            assertThat(report.isAppSpecExists()).isTrue();
            assertThat(report.isIntentClassified()).isTrue();
            assertThat(report.isStyleSelected()).isTrue();
            assertThat(report.isFrontendPrototypeExists()).isTrue();
            assertThat(report.isPrototypeUrlAccessible()).isFalse();
            assertThat(report.isDesignConfirmed()).isFalse();
            assertThat(report.isAllChecksPassed()).isFalse();
            assertThat(report.getProgressPercentage()).isEqualTo(66); // 4/6 = 66%
        }

        @Test
        @DisplayName("报告应提供正确的阻塞原因")
        void shouldProvideCorrectBlockingReason() {
            // Given - 设计未确认
            validAppSpec.setDesignConfirmed(false);
            validAppSpec.setFrontendPrototypeUrl("https://httpbin.org/get");
            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(validAppSpec);

            // When
            ExecuteGuardReport report = executeGuard.getCheckReport(testAppSpecId);

            // Then
            assertThat(report.getBlockingReason()).contains("确认设计方案");
        }

        @Test
        @DisplayName("当所有检查通过时阻塞原因应为null")
        void shouldReturnNullBlockingReasonWhenAllPassed() {
            // Given - 创建完全有效的AppSpec（除了URL需要实际可访问）
            Map<String, Object> frontendPrototype = new HashMap<>();
            frontendPrototype.put("app.tsx", "code");

            AppSpecEntity completeAppSpec = AppSpecEntity.builder()
                .id(testAppSpecId)
                .intentType("DESIGN_FROM_SCRATCH")
                .selectedStyle("A")
                .frontendPrototype(frontendPrototype)
                .frontendPrototypeUrl("https://httpbin.org/get")
                .designConfirmed(true)
                .designConfirmedAt(Instant.now())
                .build();

            when(appSpecMapper.selectById(testAppSpecId)).thenReturn(completeAppSpec);

            // When
            ExecuteGuardReport report = executeGuard.getCheckReport(testAppSpecId);

            // Then
            assertThat(report.isAllChecksPassed()).isTrue();
            assertThat(report.getBlockingReason()).isNull();
            assertThat(report.getProgressPercentage()).isEqualTo(100);
        }
    }
}
