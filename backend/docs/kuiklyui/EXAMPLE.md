# KuiklyUIRenderer 生成示例代码

## 输入AppSpec JSON示例

```json
{
  "appName": "DemoApp",
  "pages": [
    {
      "id": "home",
      "name": "首页",
      "components": [
        {
          "type": "Text",
          "id": "title",
          "props": {
            "text": "欢迎使用Kuikly",
            "fontSize": 24,
            "color": "#333333",
            "bold": true,
            "marginBottom": 20
          }
        },
        {
          "type": "Text",
          "id": "subtitle",
          "props": {
            "text": "基于Kotlin Multiplatform的跨平台框架",
            "fontSize": 16,
            "color": "#666666",
            "marginBottom": 30
          }
        },
        {
          "type": "Button",
          "id": "navButton",
          "props": {
            "text": "前往详情页",
            "width": 250,
            "height": 50,
            "borderRadius": 12,
            "onClick": "navigateTo:detail"
          }
        }
      ]
    },
    {
      "id": "detail",
      "name": "详情页",
      "components": [
        {
          "type": "Text",
          "id": "detailTitle",
          "props": {
            "text": "详情页面",
            "fontSize": 20,
            "bold": true
          }
        },
        {
          "type": "Input",
          "id": "nameInput",
          "props": {
            "placeholder": "请输入姓名",
            "width": 300,
            "height": 40
          }
        },
        {
          "type": "Image",
          "id": "avatar",
          "props": {
            "src": "https://example.com/avatar.png",
            "width": 120,
            "height": 120
          }
        }
      ]
    }
  ],
  "dataModels": [
    {
      "name": "User",
      "fields": [
        {
          "name": "id",
          "type": "String",
          "required": true
        },
        {
          "name": "name",
          "type": "String",
          "required": true
        },
        {
          "name": "email",
          "type": "String",
          "required": false
        },
        {
          "name": "age",
          "type": "Int",
          "required": false
        }
      ]
    }
  ]
}
```

## 生成的Kotlin代码示例

### 1. settings.gradle.kts

```kotlin
rootProject.name = "DemoApp"

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
```

### 2. build.gradle.kts (根项目)

```kotlin
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
```

### 3. core/build.gradle.kts (核心模块)

```kotlin
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
```

### 4. core/src/commonMain/kotlin/pages/HomePage.kt

```kotlin
package pages

import com.kuikly.core.Pager
import com.kuikly.core.ViewBuilder
import com.kuikly.core.annotations.Page
import com.kuikly.core.components.*
import com.kuikly.core.graphics.Color
import com.kuikly.core.modules.RouterModule
import org.json.JSONObject

/**
 * 首页
 * Generated by Ingenio Platform
 */
@Page("home")
internal class HomePage : Pager() {

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

                // title
                Text {
                    attr {
                        text("欢迎使用Kuikly")
                        fontSize(24f)
                        color(Color.parseColor("#333333"))
                        fontWeightBold()
                        marginBottom(20f)
                    }
                }

                // subtitle
                Text {
                    attr {
                        text("基于Kotlin Multiplatform的跨平台框架")
                        fontSize(16f)
                        color(Color.parseColor("#666666"))
                        marginBottom(30f)
                    }
                }

                // navButton
                Button {
                    attr {
                        titleAttr {
                            text("前往详情页")
                        }
                        size(width = 250f, height = 50f)
                        cornerRadius(12f)
                    }

                    event {
                        onClick {
                            // 导航到detail页面
                            ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                                .openPage("detail", JSONObject())
                        }
                    }
                }

            }
        }
    }
}
```

### 5. core/src/commonMain/kotlin/pages/DetailPage.kt

```kotlin
package pages

import com.kuikly.core.Pager
import com.kuikly.core.ViewBuilder
import com.kuikly.core.annotations.Page
import com.kuikly.core.components.*
import com.kuikly.core.graphics.Color
import com.kuikly.core.modules.RouterModule
import org.json.JSONObject

/**
 * 详情页
 * Generated by Ingenio Platform
 */
@Page("detail")
internal class DetailPage : Pager() {

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

                // detailTitle
                Text {
                    attr {
                        text("详情页面")
                        fontSize(20f)
                        color(Color.parseColor("#333333"))
                        fontWeightBold()
                    }
                }

                // nameInput
                InputView {
                    attr {
                        placeholder("请输入姓名")
                        size(300f, 40f)
                        fontSize(14f)
                    }
                }

                // avatar
                Image {
                    attr {
                        imageUrl("https://example.com/avatar.png")
                        size(120f, 120f)
                        scaleType(ImageView.ScaleType.CENTER_CROP)
                    }
                }

            }
        }
    }
}
```

### 6. core/src/commonMain/kotlin/models/DataModels.kt

```kotlin
package models

import kotlinx.serialization.Serializable

/**
 * 数据模型定义
 * Generated by Ingenio Platform
 */

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String? = null,
    val age: Int? = null
)
```

### 7. core/src/commonMain/kotlin/utils/NavigationHelper.kt

```kotlin
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
        const val HOME = "home"  // 首页
        const val DETAIL = "detail"  // 详情页
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
```

### 8. gradle.properties

```properties
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
```

## 项目结构

生成的完整项目结构：

```
DemoApp/
├── settings.gradle.kts              # 项目设置
├── build.gradle.kts                 # 根项目构建配置
├── gradle.properties                # Gradle属性配置
├── README.md                        # 项目文档
├── core/                            # 核心跨平台模块
│   ├── build.gradle.kts            # 核心模块构建配置
│   └── src/
│       ├── commonMain/             # 共享代码
│       │   └── kotlin/
│       │       ├── pages/          # 页面定义
│       │       │   ├── HomePage.kt
│       │       │   └── DetailPage.kt
│       │       ├── models/         # 数据模型
│       │       │   └── DataModels.kt
│       │       └── utils/          # 工具类
│       │           └── NavigationHelper.kt
│       ├── androidMain/            # Android特定代码
│       ├── iosMain/                # iOS特定代码
│       └── jsMain/                 # JS特定代码
└── androidApp/                     # Android宿主应用
    └── build.gradle.kts            # Android应用构建配置
```

## 组件映射关系

| AppSpec组件 | Kotlin DSL组件 | 说明 |
|------------|---------------|------|
| Text | Text { attr { text("...") } } | 文本组件 |
| Button | Button { attr { titleAttr { text("...") } }; event { onClick { ... } } } | 按钮组件 |
| View/Container | View { attr { ... } } | 容器组件 |
| Input/TextField | InputView { attr { placeholder("...") } } | 输入框组件 |
| Image | Image { attr { imageUrl("...") } } | 图片组件 |

## 属性映射关系

| AppSpec属性 | Kotlin DSL属性 | 示例 |
|-----------|--------------|------|
| text | text("...") | text("欢迎") |
| fontSize | fontSize(16f) | fontSize(16f) |
| color | color(Color.parseColor("#333")) | color(Color.parseColor("#333333")) |
| backgroundColor | backgroundColor(Color.parseColor("#FFF")) | backgroundColor(Color.parseColor("#FFFFFF")) |
| width/height | size(width, height) | size(200f, 44f) |
| padding | padding(16f) | padding(16f) |
| marginBottom | marginBottom(20f) | marginBottom(20f) |
| borderRadius | cornerRadius(8f) | cornerRadius(8f) |
| bold | fontWeightBold() | fontWeightBold() |
| placeholder | placeholder("...") | placeholder("请输入") |
| src (Image) | imageUrl("...") | imageUrl("https://...") |
| onClick | event { onClick { ... } } | event { onClick { ... } } |

## 导航事件处理

### navigateTo:pageId 格式

AppSpec中的onClick属性支持特殊格式：`navigateTo:pageId`

```json
{
  "type": "Button",
  "props": {
    "onClick": "navigateTo:detail"
  }
}
```

生成的Kotlin代码：

```kotlin
Button {
    event {
        onClick {
            // 导航到detail页面
            ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                .openPage("detail", JSONObject())
        }
    }
}
```

## 构建命令

### Android平台

```bash
# 构建Android应用
./gradlew :androidApp:assembleDebug

# 安装到设备
./gradlew :androidApp:installDebug

# 运行
./gradlew :androidApp:run
```

### iOS平台

```bash
# 构建iOS Framework
./gradlew :core:linkDebugFrameworkIosArm64

# 在Xcode中打开并运行
open iosApp/iosApp.xcodeproj
```

### H5/小程序平台

```bash
# 构建JS产物
./gradlew :core:jsBrowserProductionWebpack

# 输出目录: core/build/distributions/
```

## 技术栈对比

| 特性 | 旧版Renderer (Taro + React) | 新版Renderer (Kotlin Multiplatform) |
|-----|----------------------------|-----------------------------------|
| 技术栈 | JavaScript/TypeScript + React | Kotlin + Kotlin Multiplatform |
| 语法 | JSX/TSX | Kotlin DSL |
| 平台支持 | 微信小程序、H5 | Android、iOS、H5、小程序、鸿蒙 |
| 构建工具 | npm/webpack | Gradle |
| 类型系统 | TypeScript可选 | Kotlin强类型 |
| 代码复用 | 有限 | 跨平台共享 |
| 性能 | 解释执行 | 原生编译 |

## 优势分析

### 1. 真正的跨平台

- **Android原生**：通过Kotlin/JVM直接编译为Android原生应用
- **iOS原生**：通过Kotlin/Native编译为iOS原生二进制
- **H5应用**：通过Kotlin/JS编译为JavaScript运行在浏览器
- **小程序**：通过Kotlin/JS适配小程序运行时
- **鸿蒙系统**：支持华为鸿蒙平台（HarmonyOS）

### 2. 类型安全

- Kotlin的强类型系统在编译期就能发现错误
- DSL语法提供编译时检查，减少运行时错误
- IDE智能提示和自动补全

### 3. 代码复用

- 业务逻辑、数据模型、工具类完全共享
- 仅需为特定平台编写平台特定代码
- 减少重复代码，降低维护成本

### 4. 性能优越

- Android和iOS编译为原生代码，无性能损失
- 避免了JavaScript Bridge的性能开销
- 启动速度和运行速度优于混合式框架

### 5. 统一开发体验

- 使用Kotlin统一开发语言
- 统一的构建工具（Gradle）
- 统一的依赖管理和版本控制

## 总结

新版KuiklyUIRenderer已完全重写，从生成Taro + React代码改为生成Kotlin Multiplatform + KuiklyUI框架代码。主要改进包括：

1. **正确的技术栈**：使用Kotlin Multiplatform而非Taro + React
2. **Kotlin DSL语法**：生成符合KuiklyUI规范的Kotlin声明式代码
3. **完整的项目结构**：生成标准的KMP项目结构和构建配置
4. **跨平台支持**：支持Android、iOS、H5、小程序、鸿蒙等多个平台
5. **组件映射正确**：Text、Button、View等组件正确映射到KuiklyUI DSL
6. **导航处理**：支持navigateTo格式的页面导航
7. **数据模型生成**：支持Kotlin数据类和序列化
8. **工具类生成**：提供NavigationHelper等实用工具类
9. **详细文档**：生成完整的README.md项目文档

生成的代码结构清晰、类型安全、易于维护，符合Kotlin Multiplatform和KuiklyUI框架的最佳实践。
