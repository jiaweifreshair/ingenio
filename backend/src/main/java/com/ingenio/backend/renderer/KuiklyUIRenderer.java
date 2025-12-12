package com.ingenio.backend.renderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Kuikly UI渲染器
 * 将AppSpec转换为Kotlin Multiplatform + KuiklyUI代码
 * 生成可跨平台运行的Kotlin项目代码（Android、iOS、H5、鸿蒙）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KuiklyUIRenderer implements IRenderer {

    private final ObjectMapper objectMapper;

    /**
     * 渲染AppSpec为Kotlin Multiplatform代码
     *
     * @param appSpec AppSpec JSON
     * @return 生成的代码文件（文件名 -> 文件内容）
     * @throws BusinessException 当渲染失败时抛出
     */
    @Override
    public Map<String, String> render(Map<String, Object> appSpec) {
        if (appSpec == null || appSpec.isEmpty()) {
            log.error("渲染失败: AppSpec不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "AppSpec不能为空");
        }

        try {
            log.info("开始渲染AppSpec为Kotlin Multiplatform项目: appName={}", appSpec.get("appName"));

            Map<String, String> generatedFiles = new LinkedHashMap<>();

            // 1. 生成项目设置文件
            generatedFiles.put("settings.gradle.kts", generateSettingsGradle(appSpec));

            // 2. 生成根项目构建配置
            generatedFiles.put("build.gradle.kts", generateRootBuildGradle(appSpec));

            // 3. 生成核心模块构建配置（KMP配置）
            generatedFiles.put("core/build.gradle.kts", generateCoreBuildGradle(appSpec));

            // 4. 生成Android宿主应用构建配置
            generatedFiles.put("androidApp/build.gradle.kts", generateAndroidAppBuildGradle(appSpec));

            // 5. 生成Kotlin页面组件
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> pages = (List<Map<String, Object>>) appSpec.getOrDefault("pages", new ArrayList<>());

            // 生成首页（HomePage作为默认首页）
            if (!pages.isEmpty()) {
                Map<String, Object> firstPage = pages.get(0);
                String pageCode = generateKotlinPage(firstPage, true);
                generatedFiles.put("core/src/commonMain/kotlin/pages/HomePage.kt", pageCode);
            }

            // 生成其他页面
            for (int i = 1; i < pages.size(); i++) {
                Map<String, Object> page = pages.get(i);
                String pageId = (String) page.get("id");
                String pageName = capitalize(pageId) + "Page";
                String pageCode = generateKotlinPage(page, false);
                generatedFiles.put(String.format("core/src/commonMain/kotlin/pages/%s.kt", pageName), pageCode);
            }

            // 6. 生成数据模型（如果存在）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataModels = (List<Map<String, Object>>) appSpec.getOrDefault("dataModels", new ArrayList<>());
            if (!dataModels.isEmpty()) {
                generatedFiles.put("core/src/commonMain/kotlin/models/DataModels.kt", generateDataModelsKotlin(dataModels));
            }

            // 7. 生成导航工具类
            generatedFiles.put("core/src/commonMain/kotlin/utils/NavigationHelper.kt", generateNavigationHelper(appSpec));

            // 8. 生成README文档
            generatedFiles.put("README.md", generateReadmeKotlin(appSpec));

            // 9. 生成gradle.properties配置
            generatedFiles.put("gradle.properties", generateGradleProperties());

            log.info("渲染完成: 共生成{}个文件", generatedFiles.size());
            return generatedFiles;

        } catch (Exception e) {
            log.error("渲染失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.CODEGEN_FAILED, "Kotlin代码生成失败: " + e.getMessage());
        }
    }

    @Override
    public String getRendererName() {
        return "KuiklyUIRenderer";
    }

    @Override
    public String getSupportedFramework() {
        return "Kotlin Multiplatform + KuiklyUI";
    }

    @Override
    public boolean isSupported(Map<String, Object> appSpec) {
        return appSpec != null && appSpec.containsKey("pages");
    }

    /**
     * 生成settings.gradle.kts（项目设置）
     */
    private String generateSettingsGradle(Map<String, Object> appSpec) {
        String appName = (String) appSpec.getOrDefault("appName", "kuikly-app");
        return String.format("""
                rootProject.name = "%s"

                // 启用版本目录
                enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

                // 插件管理
                pluginManagement {
                    repositories {
                        google()
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                // 依赖解析管理
                dependencyResolutionManagement {
                    repositories {
                        google()
                        mavenCentral()
                        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
                    }
                }

                // 包含模块
                include(":core")
                include(":androidApp")
                """, appName);
    }

    /**
     * 生成根项目build.gradle.kts
     */
    private String generateRootBuildGradle(Map<String, Object> appSpec) {
        return """
                plugins {
                    // Kotlin Multiplatform
                    kotlin("multiplatform") version "1.9.20" apply false
                    kotlin("android") version "1.9.20" apply false

                    // Android
                    id("com.android.application") version "8.1.0" apply false
                    id("com.android.library") version "8.1.0" apply false
                }

                allprojects {
                    group = "com.kuikly.app"
                    version = "1.0.0"
                }

                tasks.register("clean", Delete::class) {
                    delete(rootProject.buildDir)
                }
                """;
    }

    /**
     * 生成core/build.gradle.kts（Kotlin Multiplatform配置）
     */
    private String generateCoreBuildGradle(Map<String, Object> appSpec) {
        return """
                plugins {
                    kotlin("multiplatform")
                    id("com.android.library")
                }

                kotlin {
                    // Android Target
                    androidTarget {
                        compilations.all {
                            kotlinOptions {
                                jvmTarget = "17"
                            }
                        }
                    }

                    // iOS Target
                    listOf(
                        iosX64(),
                        iosArm64(),
                        iosSimulatorArm64()
                    ).forEach { iosTarget ->
                        iosTarget.binaries.framework {
                            baseName = "core"
                            isStatic = true
                        }
                    }

                    // JS Target (for H5 and MiniProgram)
                    js(IR) {
                        browser {
                            commonWebpackConfig {
                                cssSupport {
                                    enabled.set(true)
                                }
                            }
                        }
                        binaries.executable()
                    }

                    // HarmonyOS Target
                    // Note: 鸿蒙支持需要额外配置，此处保留占位

                    sourceSets {
                        // Common Source Set
                        val commonMain by getting {
                            dependencies {
                                implementation("com.kuikly:core:1.0.0")
                                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                            }
                        }

                        val commonTest by getting {
                            dependencies {
                                implementation(kotlin("test"))
                            }
                        }

                        // Android Source Set
                        val androidMain by getting {
                            dependencies {
                                implementation("androidx.core:core-ktx:1.12.0")
                            }
                        }

                        // iOS Source Set
                        val iosX64Main by getting
                        val iosArm64Main by getting
                        val iosSimulatorArm64Main by getting
                        val iosMain by creating {
                            dependsOn(commonMain)
                            iosX64Main.dependsOn(this)
                            iosArm64Main.dependsOn(this)
                            iosSimulatorArm64Main.dependsOn(this)
                        }

                        // JS Source Set
                        val jsMain by getting {
                            dependencies {
                                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:18.2.0-pre.467")
                                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom:18.2.0-pre.467")
                            }
                        }
                    }
                }

                android {
                    namespace = "com.kuikly.app.core"
                    compileSdk = 34

                    defaultConfig {
                        minSdk = 24
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                }
                """;
    }

    /**
     * 生成androidApp/build.gradle.kts（Android宿主应用配置）
     */
    private String generateAndroidAppBuildGradle(Map<String, Object> appSpec) {
        String appName = (String) appSpec.getOrDefault("appName", "KuiklyApp");
        return String.format("""
                plugins {
                    id("com.android.application")
                    kotlin("android")
                }

                android {
                    namespace = "com.kuikly.app"
                    compileSdk = 34

                    defaultConfig {
                        applicationId = "com.kuikly.app"
                        minSdk = 24
                        targetSdk = 34
                        versionCode = 1
                        versionName = "1.0.0"
                    }

                    buildTypes {
                        getByName("release") {
                            isMinifyEnabled = false
                            proguardFiles(
                                getDefaultProguardFile("proguard-android-optimize.txt"),
                                "proguard-rules.pro"
                            )
                        }
                    }

                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }

                    kotlinOptions {
                        jvmTarget = "17"
                    }
                }

                dependencies {
                    implementation(project(":core"))
                    implementation("androidx.core:core-ktx:1.12.0")
                    implementation("androidx.appcompat:appcompat:1.6.1")
                    implementation("com.google.android.material:material:1.11.0")
                    implementation("com.kuikly:android:1.0.0")
                }
                """, appName);
    }

    /**
     * 生成Kotlin页面代码
     *
     * @param page       页面定义
     * @param isHomePage 是否为首页
     */
    private String generateKotlinPage(Map<String, Object> page, boolean isHomePage) {
        String pageId = (String) page.get("id");
        String pageName = (String) page.getOrDefault("name", pageId);
        String pageClassName = isHomePage ? "HomePage" : capitalize(pageId) + "Page";
        String pageAnnotation = isHomePage ? "home" : pageId;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> components = (List<Map<String, Object>>) page.getOrDefault("components", new ArrayList<>());

        StringBuilder componentCode = new StringBuilder();
        for (Map<String, Object> component : components) {
            componentCode.append(generateComponentKotlin(component, 3));
        }

        return String.format("""
                package pages

                import com.kuikly.core.Pager
                import com.kuikly.core.ViewBuilder
                import com.kuikly.core.annotations.Page
                import com.kuikly.core.components.*
                import com.kuikly.core.graphics.Color
                import com.kuikly.core.modules.RouterModule
                import org.json.JSONObject

                /**
                 * %s
                 * Generated by Ingenio Platform
                 */
                @Page("%s")
                internal class %s : Pager() {

                    override fun body(): ViewBuilder {
                        return {
                            attr {
                                size(pagerData.pageViewWidth, pagerData.pageViewHeight)
                                backgroundColor(Color.parseColor("#FFFFFF"))
                            }

                            Column {
                                attr {
                                    size(pagerData.pageViewWidth, pagerData.pageViewHeight)
                                    padding(16f)
                                }

                %s
                            }
                        }
                    }
                }
                """, pageName, pageAnnotation, pageClassName, componentCode.toString());
    }

    /**
     * 生成Kotlin组件DSL代码
     *
     * @param component 组件定义
     * @param indent    缩进级别
     */
    private String generateComponentKotlin(Map<String, Object> component, int indent) {
        String type = (String) component.getOrDefault("type", "View");
        String id = (String) component.get("id");

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) component.getOrDefault("props", new HashMap<>());

        String indentStr = "    ".repeat(indent);
        StringBuilder code = new StringBuilder();

        // 添加注释
        if (id != null && !id.isEmpty()) {
            code.append(String.format("%s// %s\n", indentStr, id));
        }

        // 根据组件类型生成DSL代码
        switch (type.toLowerCase()) {
            case "text" -> {
                String text = (String) props.getOrDefault("text", "Text");
                Integer fontSize = getIntValue(props, "fontSize", 16);
                String color = (String) props.getOrDefault("color", "#333333");

                code.append(String.format("%sText {\n", indentStr));
                code.append(String.format("%s    attr {\n", indentStr));
                code.append(String.format("%s        text(\"%s\")\n", indentStr, escapeKotlinString(text)));
                code.append(String.format("%s        fontSize(%df)\n", indentStr, fontSize));
                code.append(String.format("%s        color(Color.parseColor(\"%s\"))\n", indentStr, color));

                // 添加字体粗细
                Boolean bold = (Boolean) props.getOrDefault("bold", false);
                if (bold) {
                    code.append(String.format("%s        fontWeightBold()\n", indentStr));
                }

                // 添加外边距
                if (props.containsKey("marginBottom")) {
                    Integer marginBottom = getIntValue(props, "marginBottom", 0);
                    code.append(String.format("%s        marginBottom(%df)\n", indentStr, marginBottom));
                }

                code.append(String.format("%s    }\n", indentStr));
                code.append(String.format("%s}\n", indentStr));
            }

            case "button" -> {
                String text = (String) props.getOrDefault("text", "Button");
                Integer width = getIntValue(props, "width", 200);
                Integer height = getIntValue(props, "height", 44);
                String onClick = (String) props.get("onClick");

                code.append(String.format("%sButton {\n", indentStr));
                code.append(String.format("%s    attr {\n", indentStr));
                code.append(String.format("%s        titleAttr {\n", indentStr));
                code.append(String.format("%s            text(\"%s\")\n", indentStr, escapeKotlinString(text)));
                code.append(String.format("%s        }\n", indentStr));
                code.append(String.format("%s        size(width = %df, height = %df)\n", indentStr, width, height));

                // 添加圆角
                Integer borderRadius = getIntValue(props, "borderRadius", 8);
                code.append(String.format("%s        cornerRadius(%df)\n", indentStr, borderRadius));

                code.append(String.format("%s    }\n", indentStr));

                // 处理点击事件
                if (onClick != null && !onClick.isEmpty()) {
                    code.append(String.format("%s    \n", indentStr));
                    code.append(String.format("%s    event {\n", indentStr));
                    code.append(String.format("%s        onClick {\n", indentStr));

                    // 解析onClick：支持navigateTo:pageId格式
                    if (onClick.startsWith("navigateTo:")) {
                        String targetPage = onClick.substring("navigateTo:".length());
                        code.append(String.format("%s            // 导航到%s页面\n", indentStr, targetPage));
                        code.append(String.format("%s            ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)\n", indentStr));
                        code.append(String.format("%s                .openPage(\"%s\", JSONObject())\n", indentStr, targetPage));
                    } else {
                        code.append(String.format("%s            // TODO: 实现点击事件\n", indentStr));
                        code.append(String.format("%s            println(\"Button clicked: %s\")\n", indentStr, id));
                    }

                    code.append(String.format("%s        }\n", indentStr));
                    code.append(String.format("%s    }\n", indentStr));
                }

                code.append(String.format("%s}\n", indentStr));
            }

            case "view", "container" -> {
                String backgroundColor = (String) props.getOrDefault("backgroundColor", "#FFFFFF");
                Integer width = getIntValue(props, "width", null);
                Integer height = getIntValue(props, "height", null);

                code.append(String.format("%sView {\n", indentStr));
                code.append(String.format("%s    attr {\n", indentStr));

                if (width != null && height != null) {
                    code.append(String.format("%s        size(%df, %df)\n", indentStr, width, height));
                }

                code.append(String.format("%s        backgroundColor(Color.parseColor(\"%s\"))\n", indentStr, backgroundColor));

                // 添加内边距
                if (props.containsKey("padding")) {
                    Integer padding = getIntValue(props, "padding", 0);
                    code.append(String.format("%s        padding(%df)\n", indentStr, padding));
                }

                code.append(String.format("%s    }\n", indentStr));
                code.append(String.format("%s    \n", indentStr));
                code.append(String.format("%s    // TODO: 添加子组件\n", indentStr));
                code.append(String.format("%s}\n", indentStr));
            }

            case "input", "textfield" -> {
                String placeholder = (String) props.getOrDefault("placeholder", "请输入");
                Integer width = getIntValue(props, "width", 300);
                Integer height = getIntValue(props, "height", 40);

                code.append(String.format("%sInputView {\n", indentStr));
                code.append(String.format("%s    attr {\n", indentStr));
                code.append(String.format("%s        placeholder(\"%s\")\n", indentStr, escapeKotlinString(placeholder)));
                code.append(String.format("%s        size(%df, %df)\n", indentStr, width, height));
                code.append(String.format("%s        fontSize(14f)\n", indentStr));
                code.append(String.format("%s    }\n", indentStr));
                code.append(String.format("%s}\n", indentStr));
            }

            case "image" -> {
                String src = (String) props.getOrDefault("src", "");
                Integer width = getIntValue(props, "width", 100);
                Integer height = getIntValue(props, "height", 100);

                code.append(String.format("%sImage {\n", indentStr));
                code.append(String.format("%s    attr {\n", indentStr));

                if (!src.isEmpty()) {
                    code.append(String.format("%s        imageUrl(\"%s\")\n", indentStr, escapeKotlinString(src)));
                }

                code.append(String.format("%s        size(%df, %df)\n", indentStr, width, height));
                code.append(String.format("%s        scaleType(ImageView.ScaleType.CENTER_CROP)\n", indentStr));
                code.append(String.format("%s    }\n", indentStr));
                code.append(String.format("%s}\n", indentStr));
            }

            default -> {
                // 默认使用View组件
                code.append(String.format("%s// 未知组件类型: %s，使用View代替\n", indentStr, type));
                code.append(String.format("%sView {\n", indentStr));
                code.append(String.format("%s    attr {\n", indentStr));
                code.append(String.format("%s        size(100f, 100f)\n", indentStr));
                code.append(String.format("%s    }\n", indentStr));
                code.append(String.format("%s}\n", indentStr));
            }
        }

        code.append("\n");
        return code.toString();
    }

    /**
     * 生成数据模型代码
     */
    private String generateDataModelsKotlin(List<Map<String, Object>> dataModels) {
        StringBuilder code = new StringBuilder();
        code.append("package models\n\n");
        code.append("import kotlinx.serialization.Serializable\n\n");
        code.append("/**\n * 数据模型定义\n * Generated by Ingenio Platform\n */\n\n");

        for (Map<String, Object> model : dataModels) {
            String modelName = (String) model.get("name");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) model.getOrDefault("fields", new ArrayList<>());

            code.append("@Serializable\n");
            code.append(String.format("data class %s(\n", capitalize(modelName)));

            for (int i = 0; i < fields.size(); i++) {
                Map<String, Object> field = fields.get(i);
                String fieldName = (String) field.get("name");
                String fieldType = mapKotlinType((String) field.getOrDefault("type", "String"));
                Boolean required = (Boolean) field.getOrDefault("required", true);

                String nullability = required ? "" : "? = null";
                code.append(String.format("    val %s: %s%s", fieldName, fieldType, nullability));

                if (i < fields.size() - 1) {
                    code.append(",\n");
                } else {
                    code.append("\n");
                }
            }

            code.append(")\n\n");
        }

        return code.toString();
    }

    /**
     * 生成导航工具类
     */
    private String generateNavigationHelper(Map<String, Object> appSpec) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pages = (List<Map<String, Object>>) appSpec.getOrDefault("pages", new ArrayList<>());

        StringBuilder pageIds = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            Map<String, Object> page = pages.get(i);
            String pageId = (String) page.get("id");
            String pageName = (String) page.getOrDefault("name", pageId);
            pageIds.append(String.format("        const val %s = \"%s\"  // %s\n",
                    pageId.toUpperCase(), pageId, pageName));
        }

        return String.format("""
                package utils

                import com.kuikly.core.KuiklyRuntime
                import com.kuikly.core.modules.RouterModule
                import org.json.JSONObject

                /**
                 * 导航工具类
                 * 提供页面路由导航的便捷方法
                 * Generated by Ingenio Platform
                 */
                object NavigationHelper {

                    /**
                     * 页面ID常量
                     */
                    object PageIds {
                %s
                    }

                    /**
                     * 打开页面
                     *
                     * @param pageId 页面ID
                     * @param params 页面参数
                     */
                    fun openPage(pageId: String, params: JSONObject = JSONObject()) {
                        val router = KuiklyRuntime.ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                        router.openPage(pageId, params)
                    }

                    /**
                     * 关闭当前页面
                     */
                    fun closePage() {
                        RouterModule.closePage()
                    }

                    /**
                     * 返回首页
                     */
                    fun backToHome() {
                        val router = KuiklyRuntime.ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                        router.openPage(PageIds.HOME, JSONObject())
                    }
                }
                """, pageIds.toString());
    }

    /**
     * 生成README文档
     */
    private String generateReadmeKotlin(Map<String, Object> appSpec) {
        String appName = (String) appSpec.getOrDefault("appName", "Kuikly App");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pages = (List<Map<String, Object>>) appSpec.getOrDefault("pages", new ArrayList<>());

        StringBuilder pagesTable = new StringBuilder();
        for (Map<String, Object> page : pages) {
            String pageId = (String) page.get("id");
            String pageName = (String) page.getOrDefault("name", pageId);
            pagesTable.append(String.format("| %s | %s | `pages/%sPage.kt` |\n", pageId, pageName, capitalize(pageId)));
        }

        return String.format("""
                # %s

                Generated by **Ingenio Platform** - AI驱动的低代码平台

                基于 **Kotlin Multiplatform + KuiklyUI** 框架构建的跨平台应用

                ## 项目简介

                本项目使用Kotlin Multiplatform技术，支持以下平台：
                - **Android** - 原生Android应用
                - **iOS** - 原生iOS应用（通过Kotlin/Native）
                - **H5** - 网页应用（通过Kotlin/JS）
                - **小程序** - 微信小程序支持
                - **鸿蒙** - 华为鸿蒙系统（HarmonyOS）

                ## 技术栈

                - **Kotlin Multiplatform** - 跨平台代码共享
                - **KuiklyUI** - 声明式UI框架
                - **Gradle** - 构建工具
                - **Kotlin DSL** - 类型安全的构建脚本

                ## 项目结构

                ```
                %s/
                ├── core/                              # 核心跨平台模块
                │   ├── build.gradle.kts              # 核心模块构建配置
                │   └── src/
                │       ├── commonMain/               # 共享代码
                │       │   └── kotlin/
                │       │       ├── pages/            # 页面定义
                │       │       ├── models/           # 数据模型
                │       │       └── utils/            # 工具类
                │       ├── androidMain/              # Android特定代码
                │       ├── iosMain/                  # iOS特定代码
                │       └── jsMain/                   # JS特定代码（H5/小程序）
                ├── androidApp/                       # Android宿主应用
                │   └── build.gradle.kts             # Android应用构建配置
                ├── iosApp/                           # iOS宿主应用
                ├── h5App/                            # H5宿主应用
                ├── build.gradle.kts                  # 根项目构建配置
                ├── settings.gradle.kts               # 项目设置
                └── gradle.properties                 # Gradle属性配置
                ```

                ## 快速开始

                ### 前置要求

                - **JDK 17+** - Java开发工具包
                - **Android Studio** - Android开发IDE（推荐最新版本）
                - **Xcode** - iOS开发IDE（仅macOS，用于iOS构建）
                - **Gradle 8.0+** - 构建工具（通常随IDE自动安装）

                ### 安装依赖

                项目首次导入时，Gradle会自动下载所有依赖。

                ### 构建与运行

                #### Android

                ```bash
                # 构建Android应用
                ./gradlew :androidApp:assembleDebug

                # 安装到设备
                ./gradlew :androidApp:installDebug

                # 运行
                ./gradlew :androidApp:run
                ```

                #### iOS

                ```bash
                # 构建iOS Framework
                ./gradlew :core:linkDebugFrameworkIosArm64

                # 在Xcode中打开并运行
                open iosApp/iosApp.xcodeproj
                ```

                #### H5/小程序

                ```bash
                # 构建JS产物
                ./gradlew :core:jsBrowserProductionWebpack

                # 输出目录: core/build/distributions/
                ```

                ## 页面列表

                | 页面ID | 页面名称 | 文件路径 |
                |--------|---------|---------|
                %s

                ## 开发指南

                ### 添加新页面

                1. 在 `core/src/commonMain/kotlin/pages/` 创建新的Kotlin文件
                2. 使用 `@Page` 注解标注页面ID
                3. 继承 `Pager` 类并实现 `body()` 方法

                示例：

                ```kotlin
                @Page("mypage")
                internal class MyPage : Pager() {
                    override fun body(): ViewBuilder {
                        return {
                            Column {
                                attr {
                                    allCenter()
                                }

                                Text {
                                    attr {
                                        text("我的页面")
                                        fontSize(20f)
                                    }
                                }
                            }
                        }
                    }
                }
                ```

                ### 页面导航

                使用 `NavigationHelper` 进行页面导航：

                ```kotlin
                // 打开指定页面
                NavigationHelper.openPage("pageId", JSONObject())

                // 关闭当前页面
                NavigationHelper.closePage()

                // 返回首页
                NavigationHelper.backToHome()
                ```

                ### 数据模型

                在 `core/src/commonMain/kotlin/models/` 定义数据模型：

                ```kotlin
                @Serializable
                data class User(
                    val id: String,
                    val name: String,
                    val email: String? = null
                )
                ```

                ## 常见组件

                ### Text - 文本组件

                ```kotlin
                Text {
                    attr {
                        text("Hello Kuikly")
                        fontSize(16f)
                        color(Color.BLACK)
                        fontWeightBold()
                    }
                }
                ```

                ### Button - 按钮组件

                ```kotlin
                Button {
                    attr {
                        titleAttr {
                            text("点击我")
                        }
                        size(200f, 44f)
                        cornerRadius(8f)
                    }

                    event {
                        onClick {
                            NavigationHelper.openPage("detail", JSONObject())
                        }
                    }
                }
                ```

                ### View - 容器组件

                ```kotlin
                View {
                    attr {
                        size(100f, 100f)
                        backgroundColor(Color.BLUE)
                        cornerRadius(10f)
                    }
                }
                ```

                ### Column/Row - 布局组件

                ```kotlin
                Column {
                    attr {
                        size(300f, 400f)
                        padding(16f)
                    }

                    // 子组件
                }

                Row {
                    attr {
                        size(300f, 60f)
                        spaceBetween()
                    }

                    // 子组件
                }
                ```

                ### Image - 图片组件

                ```kotlin
                Image {
                    attr {
                        imageUrl("https://example.com/image.png")
                        size(100f, 100f)
                        scaleType(ImageView.ScaleType.CENTER_CROP)
                        cornerRadius(8f)
                    }
                }
                ```

                ### InputView - 输入框组件

                ```kotlin
                InputView {
                    attr {
                        placeholder("请输入内容")
                        size(300f, 40f)
                        fontSize(14f)
                    }
                }
                ```

                ## 调试技巧

                ### Android调试

                在Android Studio中使用Logcat查看日志：

                ```kotlin
                println("Debug: value = $value")
                ```

                ### iOS调试

                在Xcode中使用Console查看日志：

                ```kotlin
                println("Debug: value = $value")
                ```

                ## 常见问题

                ### Q: 如何添加新的依赖？

                在 `core/build.gradle.kts` 的 `commonMain` 依赖中添加：

                ```kotlin
                val commonMain by getting {
                    dependencies {
                        implementation("com.example:library:1.0.0")
                    }
                }
                ```

                ### Q: 如何配置不同平台的特定代码？

                在对应的 sourceSet 中添加代码：
                - Android: `androidMain`
                - iOS: `iosMain`
                - JS: `jsMain`

                ### Q: 构建失败怎么办？

                1. 清理构建缓存：`./gradlew clean`
                2. 重新同步Gradle：IDE中点击 "Sync Project with Gradle Files"
                3. 检查网络连接（依赖下载需要网络）
                4. 查看错误日志，定位具体问题

                ## 更多资源

                - [Kotlin Multiplatform官方文档](https://kotlinlang.org/docs/multiplatform.html)
                - [KuiklyUI框架文档](https://github.com/KuiklyUI/KuiklyUI)
                - [Ingenio平台文档](https://ingenio.dev/docs)

                ## 技术支持

                如遇到问题，请联系：
                - Email: support@ingenio.dev
                - GitHub Issues: https://github.com/ingenio/issues

                ---

                Generated by **Ingenio Platform** with ❤️
                """, appName, appName, pagesTable.toString());
    }

    /**
     * 生成gradle.properties配置
     */
    private String generateGradleProperties() {
        return """
                # Kotlin设置
                kotlin.code.style=official
                kotlin.mpp.enableGranularSourceSetsMetadata=true
                kotlin.native.enableDependencyPropagation=false
                kotlin.js.generate.executable.default=false

                # Android设置
                android.useAndroidX=true
                android.enableJetifier=false

                # Gradle设置
                org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
                org.gradle.parallel=true
                org.gradle.caching=true
                """;
    }

    /**
     * 映射Kotlin类型
     */
    private String mapKotlinType(String type) {
        return switch (type.toLowerCase()) {
            case "number", "integer", "int" -> "Int";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            case "boolean", "bool" -> "Boolean";
            case "date" -> "String"; // ISO格式日期字符串
            case "array", "list" -> "List<Any>";
            case "object", "map" -> "Map<String, Any>";
            default -> "String";
        };
    }

    /**
     * 转义Kotlin字符串
     */
    private String escapeKotlinString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 获取整数值（安全类型转换）
     */
    private Integer getIntValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
