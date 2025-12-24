package com.ingenio.backend.codegen.kuikly;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * KuiklyUIGenerator单元测试
 *
 * <p>测试KuiklyUI多端代码生成器的功能：</p>
 * <ul>
 *   <li>共享模块（Kotlin DSL）代码生成</li>
 *   <li>各平台壳应用生成（Android、iOS、HarmonyOS、Web）</li>
 *   <li>页面、组件、API服务代码生成</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-12-22
 */
@DisplayName("KuiklyUIGenerator单元测试")
class KuiklyUIGeneratorTest {

    private KuiklyUIGenerator generator;
    private List<Entity> testEntities;
    private String testAppName;

    @BeforeEach
    void setUp() {
        generator = new KuiklyUIGenerator();
        testAppName = "TestApp";

        // 创建测试实体
        testEntities = createTestEntities();
    }

    /**
     * 创建测试实体数据（使用Builder模式）
     */
    private List<Entity> createTestEntities() {
        // User实体
        Entity userEntity = Entity.builder()
            .name("User")
            .description("用户实体")
            .fields(Arrays.asList(
                Field.builder().name("id").type(FieldType.BIGINT).primaryKey(true).description("用户ID").build(),
                Field.builder().name("username").type(FieldType.VARCHAR).length(50).nullable(false).description("用户名").build(),
                Field.builder().name("email").type(FieldType.VARCHAR).length(100).description("邮箱").build(),
                Field.builder().name("createdAt").type(FieldType.TIMESTAMPTZ).description("创建时间").build()
            ))
            .build();

        // Task实体
        Entity taskEntity = Entity.builder()
            .name("Task")
            .description("任务实体")
            .fields(Arrays.asList(
                Field.builder().name("id").type(FieldType.BIGINT).primaryKey(true).description("任务ID").build(),
                Field.builder().name("title").type(FieldType.VARCHAR).length(200).nullable(false).description("任务标题").build(),
                Field.builder().name("description").type(FieldType.TEXT).description("任务描述").build(),
                Field.builder().name("completed").type(FieldType.BOOLEAN).description("是否完成").build(),
                Field.builder().name("dueDate").type(FieldType.DATE).description("截止日期").build()
            ))
            .build();

        return Arrays.asList(userEntity, taskEntity);
    }

    @Nested
    @DisplayName("多端代码生成测试")
    class MultiPlatformGenerationTests {

        @Test
        @DisplayName("应该成功生成多端代码")
        void shouldGenerateMultiPlatformCode() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getEntityCount()).isEqualTo(2);
            assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("应该生成所有平台代码")
        void shouldGenerateAllPlatforms() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);

            // Assert
            Map<KuiklyUIGenerator.Platform, KuiklyUIGenerator.PlatformCode> platformCodes = result.getPlatformCodes();
            assertThat(platformCodes).isNotNull();
            assertThat(platformCodes).containsKeys(
                KuiklyUIGenerator.Platform.SHARED,
                KuiklyUIGenerator.Platform.ANDROID,
                KuiklyUIGenerator.Platform.IOS,
                KuiklyUIGenerator.Platform.HARMONYOS,
                KuiklyUIGenerator.Platform.WEB
            );
        }

        @Test
        @DisplayName("应该处理空实体列表")
        void shouldHandleEmptyEntityList() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(
                List.of(), testAppName
            );

            // Assert
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getEntityCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("应该处理null实体列表")
        void shouldHandleNullEntityList() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(null, testAppName);

            // Assert
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("实体列表不能为null");
        }
    }

    @Nested
    @DisplayName("共享模块生成测试")
    class SharedModuleTests {

        @Test
        @DisplayName("应该生成共享模块核心文件")
        void shouldGenerateSharedModuleCoreFiles() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode sharedCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.SHARED);

            // Assert
            assertThat(sharedCode).isNotNull();
            Map<String, String> files = sharedCode.getFiles();
            assertThat(files).isNotNull();

            // 验证必要文件存在
            assertThat(files.keySet()).anyMatch(key -> key.contains("MainPager.kt"));
            assertThat(files.keySet()).anyMatch(key -> key.contains("ApiService.kt"));
        }

        @Test
        @DisplayName("共享模块代码应包含@Page注解")
        void sharedModuleShouldContainPageAnnotation() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode sharedCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.SHARED);

            // Assert
            Map<String, String> files = sharedCode.getFiles();
            String mainPagerContent = files.entrySet().stream()
                .filter(e -> e.getKey().contains("MainPager.kt"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");

            assertThat(mainPagerContent).contains("@Page");
            assertThat(mainPagerContent).contains("ComposeView");
        }

        @Test
        @DisplayName("共享模块应生成实体CRUD页面")
        void sharedModuleShouldGenerateEntityPages() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode sharedCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.SHARED);

            // Assert
            Map<String, String> files = sharedCode.getFiles();

            // 验证User实体页面
            assertThat(files.keySet()).anyMatch(key -> key.contains("User") && key.contains("ListPager"));
            assertThat(files.keySet()).anyMatch(key -> key.contains("User") && key.contains("DetailPager"));
            assertThat(files.keySet()).anyMatch(key -> key.contains("User") && key.contains("FormPager"));

            // 验证Task实体页面
            assertThat(files.keySet()).anyMatch(key -> key.contains("Task") && key.contains("ListPager"));
        }

        @Test
        @DisplayName("共享模块应生成API服务")
        void sharedModuleShouldGenerateApiService() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode sharedCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.SHARED);

            // Assert
            Map<String, String> files = sharedCode.getFiles();
            String apiServiceContent = files.entrySet().stream()
                .filter(e -> e.getKey().contains("ApiService.kt"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");

            assertThat(apiServiceContent).contains("HttpClient");
            assertThat(apiServiceContent).contains("getUserList");
            assertThat(apiServiceContent).contains("getTaskList");
        }
    }

    @Nested
    @DisplayName("Android壳生成测试")
    class AndroidShellTests {

        @Test
        @DisplayName("应该生成Android壳应用文件")
        void shouldGenerateAndroidShellFiles() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode androidCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.ANDROID);

            // Assert
            assertThat(androidCode).isNotNull();
            Map<String, String> files = androidCode.getFiles();
            assertThat(files).isNotNull();

            // 验证核心文件
            assertThat(files.keySet()).anyMatch(key -> key.contains("build.gradle.kts"));
            assertThat(files.keySet()).anyMatch(key -> key.contains("MainActivity.kt"));
            assertThat(files.keySet()).anyMatch(key -> key.contains("AndroidManifest.xml"));
        }

        @Test
        @DisplayName("Android壳应包含KuiklyUI SDK配置")
        void androidShellShouldContainKuiklySdkConfig() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode androidCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.ANDROID);

            // Assert
            Map<String, String> files = androidCode.getFiles();
            String buildGradleContent = files.entrySet().stream()
                .filter(e -> e.getKey().contains("build.gradle.kts"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");

            assertThat(buildGradleContent).contains("kuikly");
        }
    }

    @Nested
    @DisplayName("iOS壳生成测试")
    class IOSShellTests {

        @Test
        @DisplayName("应该生成iOS壳应用文件")
        void shouldGenerateIOSShellFiles() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode iosCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.IOS);

            // Assert
            assertThat(iosCode).isNotNull();
            Map<String, String> files = iosCode.getFiles();
            assertThat(files).isNotNull();

            // 验证核心文件（支持多种命名约定）
            assertThat(files.keySet()).anyMatch(key ->
                key.contains(".swift") || key.contains("Info.plist") || key.contains("ContentView"));
        }
    }

    @Nested
    @DisplayName("HarmonyOS壳生成测试")
    class HarmonyOSShellTests {

        @Test
        @DisplayName("应该生成HarmonyOS壳应用文件")
        void shouldGenerateHarmonyOSShellFiles() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode ohosCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.HARMONYOS);

            // Assert
            assertThat(ohosCode).isNotNull();
            Map<String, String> files = ohosCode.getFiles();
            assertThat(files).isNotNull();

            // 验证核心文件（ArkTS）
            assertThat(files.keySet()).anyMatch(key -> key.contains(".ets") || key.contains("module.json"));
        }
    }

    @Nested
    @DisplayName("Web壳生成测试")
    class WebShellTests {

        @Test
        @DisplayName("应该生成Web壳应用文件")
        void shouldGenerateWebShellFiles() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode webCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.WEB);

            // Assert
            assertThat(webCode).isNotNull();
            Map<String, String> files = webCode.getFiles();
            assertThat(files).isNotNull();

            // 验证核心文件
            assertThat(files.keySet()).anyMatch(key -> key.contains("index.html") || key.contains("package.json"));
        }
    }

    @Nested
    @DisplayName("平台枚举测试")
    class PlatformEnumTests {

        @Test
        @DisplayName("应该正确定义平台信息")
        void shouldDefineCorrectPlatformInfo() {
            // Assert
            assertThat(KuiklyUIGenerator.Platform.SHARED.getDisplayName()).isEqualTo("Shared");
            assertThat(KuiklyUIGenerator.Platform.SHARED.getTechStack()).contains("Kotlin DSL");

            assertThat(KuiklyUIGenerator.Platform.ANDROID.getDisplayName()).isEqualTo("Android");
            assertThat(KuiklyUIGenerator.Platform.IOS.getDisplayName()).isEqualTo("iOS");
            assertThat(KuiklyUIGenerator.Platform.HARMONYOS.getDisplayName()).isEqualTo("HarmonyOS");
            assertThat(KuiklyUIGenerator.Platform.WEB.getDisplayName()).isEqualTo("Web");
        }
    }

    @Nested
    @DisplayName("代码质量测试")
    class CodeQualityTests {

        @Test
        @DisplayName("生成的Kotlin代码应该语法正确")
        void generatedKotlinCodeShouldBeSyntacticallyCorrect() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode sharedCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.SHARED);

            // Assert
            Map<String, String> files = sharedCode.getFiles();
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String filename = entry.getKey();
                String content = entry.getValue();

                if (filename.endsWith(".kt")) {
                    // 验证Kotlin语法基本正确
                    assertThat(content).contains("package");

                    // 验证括号匹配
                    long openBraces = content.chars().filter(ch -> ch == '{').count();
                    long closeBraces = content.chars().filter(ch -> ch == '}').count();
                    assertThat(openBraces).isEqualTo(closeBraces);
                }
            }
        }

        @Test
        @DisplayName("生成的代码应包含完整注释")
        void generatedCodeShouldContainComments() {
            // Act
            KuiklyUIGenerator.MultiPlatformResult result = generator.generateMultiPlatform(testEntities, testAppName);
            KuiklyUIGenerator.PlatformCode sharedCode = result.getPlatformCodes().get(KuiklyUIGenerator.Platform.SHARED);

            // Assert
            Map<String, String> files = sharedCode.getFiles();
            String apiServiceContent = files.entrySet().stream()
                .filter(e -> e.getKey().contains("ApiService.kt"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");

            // 验证包含注释
            assertThat(apiServiceContent).containsPattern("//.*|/\\*.*\\*/");
        }
    }
}
