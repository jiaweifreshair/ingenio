package com.ingenio.backend.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KuiklyUIRenderer单元测试
 * 测试Kotlin Multiplatform代码生成的正确性
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KuiklyUIRenderer单元测试")
class KuiklyUIRendererTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private KuiklyUIRenderer renderer;

    private Map<String, Object> testAppSpec;

    /**
     * 初始化测试数据
     */
    @BeforeEach
    void setUp() {
        testAppSpec = createBasicAppSpec();
    }

    /**
     * 创建基本的AppSpec测试数据
     */
    private Map<String, Object> createBasicAppSpec() {
        Map<String, Object> appSpec = new HashMap<>();
        appSpec.put("appName", "TestApp");
        appSpec.put("version", "1.0.0");

        // 创建页面列表
        List<Map<String, Object>> pages = new ArrayList<>();

        // 首页
        Map<String, Object> homePage = new HashMap<>();
        homePage.put("id", "home");
        homePage.put("name", "首页");

        List<Map<String, Object>> homeComponents = new ArrayList<>();

        // Text组件
        Map<String, Object> textComponent = new HashMap<>();
        textComponent.put("id", "titleText");
        textComponent.put("type", "Text");
        Map<String, Object> textProps = new HashMap<>();
        textProps.put("text", "欢迎使用TestApp");
        textProps.put("fontSize", 20);
        textProps.put("color", "#333333");
        textProps.put("bold", true);
        textProps.put("marginBottom", 16);
        textComponent.put("props", textProps);
        homeComponents.add(textComponent);

        // Button组件
        Map<String, Object> buttonComponent = new HashMap<>();
        buttonComponent.put("id", "actionButton");
        buttonComponent.put("type", "Button");
        Map<String, Object> buttonProps = new HashMap<>();
        buttonProps.put("text", "开始使用");
        buttonProps.put("width", 200);
        buttonProps.put("height", 44);
        buttonProps.put("borderRadius", 8);
        buttonProps.put("onClick", "navigateTo:detail");
        buttonComponent.put("props", buttonProps);
        homeComponents.add(buttonComponent);

        homePage.put("components", homeComponents);
        pages.add(homePage);

        // 详情页
        Map<String, Object> detailPage = new HashMap<>();
        detailPage.put("id", "detail");
        detailPage.put("name", "详情页");
        detailPage.put("components", new ArrayList<>());
        pages.add(detailPage);

        appSpec.put("pages", pages);

        return appSpec;
    }

    /**
     * 测试渲染基本AppSpec成功
     * 验证点：
     * 1. 生成必要的项目文件
     * 2. 文件数量正确
     * 3. 文件内容包含关键配置
     */
    @Test
    @DisplayName("测试渲染基本AppSpec成功")
    void testRenderBasicAppSpec() {
        // Act
        Map<String, String> files = renderer.render(testAppSpec);

        // Assert
        assertNotNull(files);
        assertTrue(files.size() >= 7, "应该生成至少7个文件");

        // 验证核心文件存在
        assertTrue(files.containsKey("settings.gradle.kts"), "应该包含settings.gradle.kts");
        assertTrue(files.containsKey("build.gradle.kts"), "应该包含build.gradle.kts");
        assertTrue(files.containsKey("core/build.gradle.kts"), "应该包含core/build.gradle.kts");
        assertTrue(files.containsKey("androidApp/build.gradle.kts"), "应该包含androidApp/build.gradle.kts");
        assertTrue(files.containsKey("core/src/commonMain/kotlin/pages/HomePage.kt"), "应该包含HomePage.kt");
        assertTrue(files.containsKey("core/src/commonMain/kotlin/utils/NavigationHelper.kt"), "应该包含NavigationHelper.kt");
        assertTrue(files.containsKey("README.md"), "应该包含README.md");
        assertTrue(files.containsKey("gradle.properties"), "应该包含gradle.properties");

        // 验证settings.gradle.kts内容
        String settingsContent = files.get("settings.gradle.kts");
        assertTrue(settingsContent.contains("TestApp"), "settings.gradle.kts应该包含应用名称");
        assertTrue(settingsContent.contains("include(\":core\")"), "应该包含core模块");
        assertTrue(settingsContent.contains("include(\":androidApp\")"), "应该包含androidApp模块");

        // 验证HomePage.kt内容
        String homePageContent = files.get("core/src/commonMain/kotlin/pages/HomePage.kt");
        assertTrue(homePageContent.contains("class HomePage"), "应该包含HomePage类");
        assertTrue(homePageContent.contains("@Page(\"home\")"), "应该包含@Page注解");
        assertTrue(homePageContent.contains("Pager()"), "应该继承Pager类");
    }

    /**
     * 测试渲染AppSpec失败 - AppSpec为空
     */
    @Test
    @DisplayName("测试渲染AppSpec失败 - AppSpec为null")
    void testRenderEmptyAppSpec() {
        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            renderer.render(null);
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("AppSpec不能为空"));
    }

    /**
     * 测试渲染AppSpec失败 - AppSpec为空Map
     */
    @Test
    @DisplayName("测试渲染AppSpec失败 - AppSpec为空Map")
    void testRenderEmptyMap() {
        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            renderer.render(new HashMap<>());
        });

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 测试Text组件生成
     * 验证点：
     * 1. Text组件DSL代码正确
     * 2. 属性映射正确（text、fontSize、color、bold）
     */
    @Test
    @DisplayName("测试Text组件生成")
    void testGenerateTextComponent() {
        // Act
        Map<String, String> files = renderer.render(testAppSpec);

        // Assert
        String homePageContent = files.get("core/src/commonMain/kotlin/pages/HomePage.kt");
        assertNotNull(homePageContent);

        // 验证Text组件生成
        assertTrue(homePageContent.contains("Text {"), "应该包含Text组件");
        assertTrue(homePageContent.contains("text(\"欢迎使用TestApp\")"), "应该包含文本内容");
        assertTrue(homePageContent.contains("fontSize(20f)"), "应该包含字体大小");
        assertTrue(homePageContent.contains("color(Color.parseColor(\"#333333\"))"), "应该包含颜色");
        assertTrue(homePageContent.contains("fontWeightBold()"), "应该包含加粗属性");
        assertTrue(homePageContent.contains("marginBottom(16f)"), "应该包含底部外边距");
    }

    /**
     * 测试Button组件生成
     * 验证点：
     * 1. Button组件DSL代码正确
     * 2. 点击事件处理正确
     * 3. navigateTo格式解析正确
     */
    @Test
    @DisplayName("测试Button组件生成")
    void testGenerateButtonComponent() {
        // Act
        Map<String, String> files = renderer.render(testAppSpec);

        // Assert
        String homePageContent = files.get("core/src/commonMain/kotlin/pages/HomePage.kt");
        assertNotNull(homePageContent);

        // 验证Button组件生成
        assertTrue(homePageContent.contains("Button {"), "应该包含Button组件");
        assertTrue(homePageContent.contains("titleAttr {"), "应该包含titleAttr");
        assertTrue(homePageContent.contains("text(\"开始使用\")"), "应该包含按钮文本");
        assertTrue(homePageContent.contains("size(width = 200f, height = 44f)"), "应该包含尺寸");
        assertTrue(homePageContent.contains("cornerRadius(8f)"), "应该包含圆角");

        // 验证点击事件
        assertTrue(homePageContent.contains("onClick {"), "应该包含onClick事件");
        assertTrue(homePageContent.contains("导航到detail页面"), "应该包含导航注释");
        assertTrue(homePageContent.contains("RouterModule"), "应该包含RouterModule");
        assertTrue(homePageContent.contains("openPage(\"detail\""), "应该包含openPage调用");
    }

    /**
     * 测试导航事件处理
     * 验证点：
     * 1. navigateTo:pageId格式正确解析
     * 2. 生成正确的导航代码
     */
    @Test
    @DisplayName("测试导航事件生成")
    void testNavigationEventGeneration() {
        // Act
        Map<String, String> files = renderer.render(testAppSpec);

        // Assert
        String homePageContent = files.get("core/src/commonMain/kotlin/pages/HomePage.kt");
        assertNotNull(homePageContent);

        // 验证导航代码
        assertTrue(homePageContent.contains("ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)"),
                "应该包含获取RouterModule代码");
        assertTrue(homePageContent.contains(".openPage(\"detail\", JSONObject())"),
                "应该包含openPage调用");
    }

    /**
     * 测试多页面生成
     * 验证点：
     * 1. 生成多个页面文件
     * 2. 首页使用HomePage命名
     * 3. 其他页面使用{PageId}Page命名
     */
    @Test
    @DisplayName("测试多页面生成")
    void testMultiplePageGeneration() {
        // Act
        Map<String, String> files = renderer.render(testAppSpec);

        // Assert
        assertTrue(files.containsKey("core/src/commonMain/kotlin/pages/HomePage.kt"),
                "应该包含HomePage.kt");
        assertTrue(files.containsKey("core/src/commonMain/kotlin/pages/DetailPage.kt"),
                "应该包含DetailPage.kt");

        // 验证DetailPage内容
        String detailPageContent = files.get("core/src/commonMain/kotlin/pages/DetailPage.kt");
        assertNotNull(detailPageContent);
        assertTrue(detailPageContent.contains("class DetailPage"), "应该包含DetailPage类");
        assertTrue(detailPageContent.contains("@Page(\"detail\")"), "应该包含@Page注解");
    }

    /**
     * 测试数据模型生成
     * 验证点：
     * 1. 生成Kotlin data class
     * 2. 字段类型映射正确
     * 3. 可选字段处理正确
     */
    @Test
    @DisplayName("测试数据模型生成")
    void testGenerateDataModels() {
        // Arrange - 添加数据模型到AppSpec
        List<Map<String, Object>> dataModels = new ArrayList<>();

        Map<String, Object> userModel = new HashMap<>();
        userModel.put("name", "User");

        List<Map<String, Object>> fields = new ArrayList<>();

        Map<String, Object> idField = new HashMap<>();
        idField.put("name", "id");
        idField.put("type", "String");
        idField.put("required", true);
        fields.add(idField);

        Map<String, Object> nameField = new HashMap<>();
        nameField.put("name", "name");
        nameField.put("type", "String");
        nameField.put("required", true);
        fields.add(nameField);

        Map<String, Object> emailField = new HashMap<>();
        emailField.put("name", "email");
        emailField.put("type", "String");
        emailField.put("required", false); // 可选字段
        fields.add(emailField);

        userModel.put("fields", fields);
        dataModels.add(userModel);

        testAppSpec.put("dataModels", dataModels);

        // Act
        Map<String, String> files = renderer.render(testAppSpec);

        // Assert
        assertTrue(files.containsKey("core/src/commonMain/kotlin/models/DataModels.kt"),
                "应该包含DataModels.kt");

        String dataModelsContent = files.get("core/src/commonMain/kotlin/models/DataModels.kt");
        assertNotNull(dataModelsContent);

        // 验证数据模型代码
        assertTrue(dataModelsContent.contains("@Serializable"), "应该包含@Serializable注解");
        assertTrue(dataModelsContent.contains("data class User"), "应该包含User数据类");
        assertTrue(dataModelsContent.contains("val id: String"), "应该包含id字段");
        assertTrue(dataModelsContent.contains("val name: String"), "应该包含name字段");
        assertTrue(dataModelsContent.contains("val email: String? = null"),
                "可选字段应该标注为nullable");
    }

    /**
     * 测试NavigationHelper生成
     * 验证点：
     * 1. 生成页面ID常量
     * 2. 生成导航工具方法
     */
    @Test
    @DisplayName("测试NavigationHelper生成")
    void testGenerateNavigationHelper() {
        // Act
        Map<String, String> files = renderer.render(testAppSpec);

        // Assert
        String navHelperContent = files.get("core/src/commonMain/kotlin/utils/NavigationHelper.kt");
        assertNotNull(navHelperContent);

        // 验证页面ID常量
        assertTrue(navHelperContent.contains("object PageIds"), "应该包含PageIds对象");
        assertTrue(navHelperContent.contains("const val HOME = \"home\""), "应该包含HOME常量");
        assertTrue(navHelperContent.contains("const val DETAIL = \"detail\""), "应该包含DETAIL常量");

        // 验证导航方法
        assertTrue(navHelperContent.contains("fun openPage(pageId: String"), "应该包含openPage方法");
        assertTrue(navHelperContent.contains("fun closePage()"), "应该包含closePage方法");
        assertTrue(navHelperContent.contains("fun backToHome()"), "应该包含backToHome方法");
    }

    /**
     * 测试View/Container组件生成
     */
    @Test
    @DisplayName("测试View组件生成")
    void testGenerateViewComponent() {
        // Arrange - 添加View组件
        Map<String, Object> appSpec = new HashMap<>(testAppSpec);
        List<Map<String, Object>> pages = (List<Map<String, Object>>) appSpec.get("pages");
        Map<String, Object> page = pages.get(0);
        List<Map<String, Object>> components = (List<Map<String, Object>>) page.get("components");

        Map<String, Object> viewComponent = new HashMap<>();
        viewComponent.put("id", "container");
        viewComponent.put("type", "View");
        Map<String, Object> viewProps = new HashMap<>();
        viewProps.put("width", 300);
        viewProps.put("height", 200);
        viewProps.put("backgroundColor", "#F0F0F0");
        viewProps.put("padding", 16);
        viewComponent.put("props", viewProps);
        components.add(viewComponent);

        // Act
        Map<String, String> files = renderer.render(appSpec);

        // Assert
        String homePageContent = files.get("core/src/commonMain/kotlin/pages/HomePage.kt");
        assertTrue(homePageContent.contains("View {"), "应该包含View组件");
        assertTrue(homePageContent.contains("size(300f, 200f)"), "应该包含尺寸");
        assertTrue(homePageContent.contains("backgroundColor(Color.parseColor(\"#F0F0F0\"))"),
                "应该包含背景颜色");
        assertTrue(homePageContent.contains("padding(16f)"), "应该包含内边距");
    }

    /**
     * 测试Input组件生成
     */
    @Test
    @DisplayName("测试Input组件生成")
    void testGenerateInputComponent() {
        // Arrange - 添加Input组件
        Map<String, Object> appSpec = new HashMap<>(testAppSpec);
        List<Map<String, Object>> pages = (List<Map<String, Object>>) appSpec.get("pages");
        Map<String, Object> page = pages.get(0);
        List<Map<String, Object>> components = (List<Map<String, Object>>) page.get("components");

        Map<String, Object> inputComponent = new HashMap<>();
        inputComponent.put("id", "usernameInput");
        inputComponent.put("type", "Input");
        Map<String, Object> inputProps = new HashMap<>();
        inputProps.put("placeholder", "请输入用户名");
        inputProps.put("width", 300);
        inputProps.put("height", 40);
        inputComponent.put("props", inputProps);
        components.add(inputComponent);

        // Act
        Map<String, String> files = renderer.render(appSpec);

        // Assert
        String homePageContent = files.get("core/src/commonMain/kotlin/pages/HomePage.kt");
        assertTrue(homePageContent.contains("InputView {"), "应该包含InputView组件");
        assertTrue(homePageContent.contains("placeholder(\"请输入用户名\")"), "应该包含占位符");
        assertTrue(homePageContent.contains("size(300f, 40f)"), "应该包含尺寸");
        assertTrue(homePageContent.contains("fontSize(14f)"), "应该包含字体大小");
    }

    /**
     * 测试Image组件生成
     */
    @Test
    @DisplayName("测试Image组件生成")
    void testGenerateImageComponent() {
        // Arrange - 添加Image组件
        Map<String, Object> appSpec = new HashMap<>(testAppSpec);
        List<Map<String, Object>> pages = (List<Map<String, Object>>) appSpec.get("pages");
        Map<String, Object> page = pages.get(0);
        List<Map<String, Object>> components = (List<Map<String, Object>>) page.get("components");

        Map<String, Object> imageComponent = new HashMap<>();
        imageComponent.put("id", "logo");
        imageComponent.put("type", "Image");
        Map<String, Object> imageProps = new HashMap<>();
        imageProps.put("src", "https://example.com/logo.png");
        imageProps.put("width", 100);
        imageProps.put("height", 100);
        imageComponent.put("props", imageProps);
        components.add(imageComponent);

        // Act
        Map<String, String> files = renderer.render(appSpec);

        // Assert
        String homePageContent = files.get("core/src/commonMain/kotlin/pages/HomePage.kt");
        assertTrue(homePageContent.contains("Image {"), "应该包含Image组件");
        assertTrue(homePageContent.contains("imageUrl(\"https://example.com/logo.png\")"),
                "应该包含图片URL");
        assertTrue(homePageContent.contains("size(100f, 100f)"), "应该包含尺寸");
        assertTrue(homePageContent.contains("scaleType(ImageView.ScaleType.CENTER_CROP)"),
                "应该包含缩放类型");
    }

    /**
     * 测试README文档生成
     */
    @Test
    @DisplayName("测试README文档生成")
    void testGenerateReadme() {
        // Act
        Map<String, String> files = renderer.render(testAppSpec);

        // Assert
        String readmeContent = files.get("README.md");
        assertNotNull(readmeContent);

        // 验证README内容
        assertTrue(readmeContent.contains("# TestApp"), "应该包含应用名称");
        assertTrue(readmeContent.contains("Ingenio Platform"), "应该包含平台名称");
        assertTrue(readmeContent.contains("Kotlin Multiplatform"), "应该包含技术栈");
        assertTrue(readmeContent.contains("Android"), "应该包含Android平台");
        assertTrue(readmeContent.contains("iOS"), "应该包含iOS平台");
        assertTrue(readmeContent.contains("H5"), "应该包含H5平台");
        assertTrue(readmeContent.contains("鸿蒙"), "应该包含鸿蒙平台");

        // 验证页面列表
        assertTrue(readmeContent.contains("| home | 首页 |"), "应该包含首页信息");
        assertTrue(readmeContent.contains("| detail | 详情页 |"), "应该包含详情页信息");
    }

    /**
     * 测试gradle.properties生成
     */
    @Test
    @DisplayName("测试gradle.properties生成")
    void testGenerateGradleProperties() {
        // Act
        Map<String, String> files = renderer.render(testAppSpec);

        // Assert
        String gradlePropsContent = files.get("gradle.properties");
        assertNotNull(gradlePropsContent);

        // 验证Gradle属性
        assertTrue(gradlePropsContent.contains("kotlin.code.style=official"),
                "应该包含Kotlin代码风格配置");
        assertTrue(gradlePropsContent.contains("android.useAndroidX=true"),
                "应该包含AndroidX配置");
        assertTrue(gradlePropsContent.contains("org.gradle.jvmargs"),
                "应该包含JVM参数配置");
    }

    /**
     * 测试渲染器元数据
     */
    @Test
    @DisplayName("测试渲染器元数据")
    void testRendererMetadata() {
        // Assert
        assertEquals("KuiklyUIRenderer", renderer.getRendererName());
        assertEquals("Kotlin Multiplatform + KuiklyUI", renderer.getSupportedFramework());
        assertTrue(renderer.isSupported(testAppSpec));
        assertFalse(renderer.isSupported(new HashMap<>()), "空AppSpec不应该被支持");
    }
}
