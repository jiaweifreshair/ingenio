# KuiklyUIRenderer 技术文档

## 概述

本文档描述了KuiklyUIRenderer的完全重写工作，将其从生成Taro + React代码改为生成Kotlin Multiplatform + KuiklyUI框架代码。

## 变更摘要

### 核心变更

| 方面 | 旧版实现 | 新版实现 |
|-----|---------|---------|
| **目标框架** | Taro + React | Kotlin Multiplatform + KuiklyUI |
| **编程语言** | JavaScript/TypeScript | Kotlin |
| **语法风格** | JSX/TSX | Kotlin DSL |
| **构建工具** | npm/webpack | Gradle |
| **平台支持** | 微信小程序、H5 | Android、iOS、H5、小程序、鸿蒙 |
| **类型系统** | TypeScript可选 | Kotlin强类型 |

## 架构设计

### 1. 类结构

```java
public class KuiklyUIRenderer implements IRenderer {
    // 核心渲染方法
    public Map<String, String> render(Map<String, Object> appSpec)

    // Gradle配置生成
    private String generateSettingsGradle(Map<String, Object> appSpec)
    private String generateRootBuildGradle(Map<String, Object> appSpec)
    private String generateCoreBuildGradle(Map<String, Object> appSpec)
    private String generateAndroidAppBuildGradle(Map<String, Object> appSpec)

    // Kotlin代码生成
    private String generateKotlinPage(Map<String, Object> page, boolean isHomePage)
    private String generateComponentKotlin(Map<String, Object> component, int indent)
    private String generateDataModelsKotlin(List<Map<String, Object>> dataModels)
    private String generateNavigationHelper(Map<String, Object> appSpec)

    // 文档和配置生成
    private String generateReadmeKotlin(Map<String, Object> appSpec)
    private String generateGradleProperties()

    // 工具方法
    private String mapKotlinType(String type)
    private String escapeKotlinString(String str)
    private Integer getIntValue(Map<String, Object> map, String key, Integer defaultValue)
    private String capitalize(String str)
}
```

### 2. 文件生成流程

```
render(appSpec)
    │
    ├─> generateSettingsGradle()         → settings.gradle.kts
    ├─> generateRootBuildGradle()        → build.gradle.kts
    ├─> generateCoreBuildGradle()        → core/build.gradle.kts
    ├─> generateAndroidAppBuildGradle()  → androidApp/build.gradle.kts
    │
    ├─> generateKotlinPage() (首页)      → core/src/commonMain/kotlin/pages/HomePage.kt
    ├─> generateKotlinPage() (其他页面)  → core/src/commonMain/kotlin/pages/*Page.kt
    │
    ├─> generateDataModelsKotlin()       → core/src/commonMain/kotlin/models/DataModels.kt
    ├─> generateNavigationHelper()       → core/src/commonMain/kotlin/utils/NavigationHelper.kt
    │
    ├─> generateReadmeKotlin()           → README.md
    └─> generateGradleProperties()       → gradle.properties
```

### 3. 生成的项目结构

```
生成的Kotlin项目/
├── settings.gradle.kts              # 项目设置
├── build.gradle.kts                 # 根项目构建配置
├── gradle.properties                # Gradle属性
├── README.md                        # 项目文档
├── core/                            # 核心跨平台模块
│   ├── build.gradle.kts            # 核心模块配置（KMP）
│   └── src/
│       ├── commonMain/             # 共享代码
│       │   └── kotlin/
│       │       ├── pages/          # Kotlin页面
│       │       ├── models/         # 数据模型
│       │       └── utils/          # 工具类
│       ├── androidMain/            # Android特定代码
│       ├── iosMain/                # iOS特定代码
│       └── jsMain/                 # JS特定代码
└── androidApp/                     # Android宿主应用
    └── build.gradle.kts            # Android应用配置
```

## 设计决策

### 决策1: 采用Kotlin Multiplatform

**背景**：
- KuiklyUI框架基于Kotlin Multiplatform
- 需要支持多平台原生性能

**决策**：
- 使用Kotlin Multiplatform作为核心技术栈
- 生成标准的KMP项目结构

**优势**：
- 真正的跨平台原生代码
- 类型安全的编译期检查
- 代码复用率高（业务逻辑共享）
- 性能优于JavaScript混合式方案

### 决策2: 使用Kotlin DSL声明式语法

**背景**：
- KuiklyUI使用Kotlin DSL进行UI声明
- 需要生成符合框架规范的代码

**决策**：
- 生成Kotlin DSL语法的页面代码
- 使用`attr {}`块配置属性
- 使用`event {}`块处理事件

**优势**：
- 类型安全的UI构建
- IDE智能提示支持
- 编译期错误检查
- 代码可读性高

### 决策3: Gradle构建系统

**背景**：
- Kotlin Multiplatform标准构建工具
- Android/iOS开发生态支持

**决策**：
- 生成`.gradle.kts`配置文件
- 使用Kotlin DSL编写构建脚本

**优势**：
- 类型安全的构建配置
- 统一的依赖管理
- 强大的插件生态
- 增量编译优化

### 决策4: 模块化架构

**背景**：
- 需要支持多平台构建
- 需要代码复用和隔离

**决策**：
- 采用`:core`核心模块存放共享代码
- 采用`:androidApp`等宿主应用模块

**优势**：
- 清晰的职责划分
- 共享代码与平台特定代码分离
- 便于团队协作和维护
- 支持按需构建

## 组件映射实现

### Text组件

**AppSpec定义**：
```json
{
  "type": "Text",
  "props": {
    "text": "Hello",
    "fontSize": 16,
    "color": "#333333",
    "bold": true
  }
}
```

**生成的Kotlin代码**：
```kotlin
Text {
    attr {
        text("Hello")
        fontSize(16f)
        color(Color.parseColor("#333333"))
        fontWeightBold()
    }
}
```

**实现方法**：
```java
case "text" -> {
    String text = (String) props.getOrDefault("text", "Text");
    Integer fontSize = getIntValue(props, "fontSize", 16);
    String color = (String) props.getOrDefault("color", "#333333");

    code.append(String.format("%sText {\n", indentStr));
    code.append(String.format("%s    attr {\n", indentStr));
    code.append(String.format("%s        text(\"%s\")\n", indentStr, escapeKotlinString(text)));
    code.append(String.format("%s        fontSize(%df)\n", indentStr, fontSize));
    code.append(String.format("%s        color(Color.parseColor(\"%s\"))\n", indentStr, color));

    Boolean bold = (Boolean) props.getOrDefault("bold", false);
    if (bold) {
        code.append(String.format("%s        fontWeightBold()\n", indentStr));
    }

    code.append(String.format("%s    }\n", indentStr));
    code.append(String.format("%s}\n", indentStr));
}
```

### Button组件

**AppSpec定义**：
```json
{
  "type": "Button",
  "props": {
    "text": "Click Me",
    "onClick": "navigateTo:detail"
  }
}
```

**生成的Kotlin代码**：
```kotlin
Button {
    attr {
        titleAttr {
            text("Click Me")
        }
        size(width = 200f, height = 44f)
        cornerRadius(8f)
    }

    event {
        onClick {
            // 导航到detail页面
            ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)
                .openPage("detail", JSONObject())
        }
    }
}
```

**实现方法**：
```java
case "button" -> {
    String text = (String) props.getOrDefault("text", "Button");
    String onClick = (String) props.get("onClick");

    code.append(String.format("%sButton {\n", indentStr));
    code.append(String.format("%s    attr {\n", indentStr));
    code.append(String.format("%s        titleAttr {\n", indentStr));
    code.append(String.format("%s            text(\"%s\")\n", indentStr, escapeKotlinString(text)));
    code.append(String.format("%s        }\n", indentStr));
    code.append(String.format("%s    }\n", indentStr));

    if (onClick != null && !onClick.isEmpty()) {
        code.append(String.format("%s    event {\n", indentStr));
        code.append(String.format("%s        onClick {\n", indentStr));

        if (onClick.startsWith("navigateTo:")) {
            String targetPage = onClick.substring("navigateTo:".length());
            code.append(String.format("%s            ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)\n", indentStr));
            code.append(String.format("%s                .openPage(\"%s\", JSONObject())\n", indentStr, targetPage));
        }

        code.append(String.format("%s        }\n", indentStr));
        code.append(String.format("%s    }\n", indentStr));
    }

    code.append(String.format("%s}\n", indentStr));
}
```

### 导航处理

**navigateTo格式解析**：

1. 检测`onClick`属性以`navigateTo:`开头
2. 提取目标页面ID
3. 生成RouterModule导航代码

**实现代码**：
```java
if (onClick.startsWith("navigateTo:")) {
    String targetPage = onClick.substring("navigateTo:".length());
    code.append(String.format("%s            // 导航到%s页面\n", indentStr, targetPage));
    code.append(String.format("%s            ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME)\n", indentStr));
    code.append(String.format("%s                .openPage(\"%s\", JSONObject())\n", indentStr, targetPage));
}
```

## 类型映射

### AppSpec类型 → Kotlin类型

| AppSpec类型 | Kotlin类型 | 说明 |
|-----------|-----------|------|
| String | String | 字符串 |
| Number/Integer/Int | Int | 整数 |
| Long | Long | 长整型 |
| Float | Float | 浮点数 |
| Double | Double | 双精度浮点数 |
| Boolean/Bool | Boolean | 布尔值 |
| Date | String | ISO格式日期字符串 |
| Array/List | List&lt;Any&gt; | 列表 |
| Object/Map | Map&lt;String, Any&gt; | 映射 |

**实现方法**：
```java
private String mapKotlinType(String type) {
    return switch (type.toLowerCase()) {
        case "number", "integer", "int" -> "Int";
        case "long" -> "Long";
        case "float" -> "Float";
        case "double" -> "Double";
        case "boolean", "bool" -> "Boolean";
        case "date" -> "String";
        case "array", "list" -> "List<Any>";
        case "object", "map" -> "Map<String, Any>";
        default -> "String";
    };
}
```

## 错误处理

### 1. 输入验证

```java
if (appSpec == null || appSpec.isEmpty()) {
    log.error("渲染失败: AppSpec不能为空");
    throw new BusinessException(ErrorCode.PARAM_ERROR, "AppSpec不能为空");
}
```

### 2. 异常捕获

```java
try {
    // 代码生成逻辑
    log.info("开始渲染AppSpec为Kotlin Multiplatform项目: appName={}", appSpec.get("appName"));
    // ...
    log.info("渲染完成: 共生成{}个文件", generatedFiles.size());
    return generatedFiles;
} catch (Exception e) {
    log.error("渲染失败: error={}", e.getMessage(), e);
    throw new BusinessException(ErrorCode.CODEGEN_FAILED, "Kotlin代码生成失败: " + e.getMessage());
}
```

### 3. 类型安全转换

```java
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
```

## 字符串转义

为防止Kotlin代码注入和语法错误，对所有用户输入的字符串进行转义：

```java
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
```

## 日志记录

### 日志级别

- **INFO**: 正常流程（开始渲染、完成渲染、文件数量）
- **ERROR**: 错误情况（参数错误、代码生成失败）

### 日志示例

```java
log.info("开始渲染AppSpec为Kotlin Multiplatform项目: appName={}", appSpec.get("appName"));
log.info("渲染完成: 共生成{}个文件", generatedFiles.size());
log.error("渲染失败: AppSpec不能为空");
log.error("渲染失败: error={}", e.getMessage(), e);
```

## 性能优化

### 1. 使用StringBuilder

在生成长字符串时使用`StringBuilder`避免字符串拼接性能损耗：

```java
StringBuilder code = new StringBuilder();
code.append(String.format("%sText {\n", indentStr));
code.append(String.format("%s    attr {\n", indentStr));
// ...
return code.toString();
```

### 2. LinkedHashMap保序

使用`LinkedHashMap`保证生成文件的顺序性，便于调试和阅读：

```java
Map<String, String> generatedFiles = new LinkedHashMap<>();
```

### 3. 条件生成

仅在需要时生成可选文件（如数据模型）：

```java
if (!dataModels.isEmpty()) {
    generatedFiles.put("core/src/commonMain/kotlin/models/DataModels.kt",
                       generateDataModelsKotlin(dataModels));
}
```

## 测试建议

### 1. 单元测试

测试各个生成方法的正确性：

```java
@Test
public void testGenerateKotlinPage() {
    Map<String, Object> page = new HashMap<>();
    page.put("id", "home");
    page.put("name", "首页");
    // ...

    String result = renderer.generateKotlinPage(page, true);

    assertTrue(result.contains("@Page(\"home\")"));
    assertTrue(result.contains("internal class HomePage : Pager()"));
}
```

### 2. 集成测试

测试完整的渲染流程：

```java
@Test
public void testRender() {
    Map<String, Object> appSpec = loadAppSpecFromJson();

    Map<String, String> result = renderer.render(appSpec);

    assertTrue(result.containsKey("settings.gradle.kts"));
    assertTrue(result.containsKey("build.gradle.kts"));
    assertTrue(result.containsKey("core/build.gradle.kts"));
}
```

### 3. 生成代码验证

验证生成的Kotlin代码能通过编译：

```bash
# 将生成的代码写入临时目录
# 运行Gradle编译检查
./gradlew :core:compileKotlinMetadata
```

## 维护指南

### 添加新组件支持

1. 在`generateComponentKotlin()`方法的switch语句中添加新case
2. 实现组件的DSL代码生成逻辑
3. 更新组件映射文档

示例：

```java
case "newComponent" -> {
    // 1. 提取props
    String prop1 = (String) props.get("prop1");

    // 2. 生成DSL代码
    code.append(String.format("%sNewComponent {\n", indentStr));
    code.append(String.format("%s    attr {\n", indentStr));
    code.append(String.format("%s        prop1(\"%s\")\n", indentStr, prop1));
    code.append(String.format("%s    }\n", indentStr));
    code.append(String.format("%s}\n", indentStr));
}
```

### 更新KuiklyUI版本

1. 修改`generateCoreBuildGradle()`中的依赖版本
2. 更新组件DSL语法（如果有变化）
3. 更新文档和示例

### 扩展平台支持

1. 在`generateCoreBuildGradle()`中添加新平台target
2. 添加新平台的sourceSet配置
3. 更新README文档

## 未来改进方向

### 1. 组件样式优化

- 支持更多CSS样式属性映射
- 支持主题和设计系统
- 支持响应式布局

### 2. 状态管理集成

- 生成ViewModel代码
- 支持状态绑定和事件处理
- 集成Kotlin Coroutines

### 3. 网络请求生成

- 根据API定义生成网络请求代码
- 集成Ktor客户端
- 支持序列化和反序列化

### 4. 数据持久化

- 生成数据库模型（SQLDelight）
- 支持本地存储（DataStore）
- 缓存策略生成

### 5. 测试代码生成

- 生成单元测试模板
- 生成UI测试代码
- 集成测试覆盖率工具

## 相关资源

- [Kotlin Multiplatform官方文档](https://kotlinlang.org/docs/multiplatform.html)
- [KuiklyUI GitHub仓库](https://github.com/KuiklyUI/KuiklyUI)
- [Gradle Kotlin DSL文档](https://docs.gradle.org/current/userguide/kotlin_dsl.html)
- [Android开发文档](https://developer.android.com/)
- [Kotlin协程文档](https://kotlinlang.org/docs/coroutines-overview.html)

## 附录

### A. 完整的生成文件清单

| 序号 | 文件路径 | 说明 |
|-----|---------|------|
| 1 | settings.gradle.kts | 项目设置 |
| 2 | build.gradle.kts | 根项目构建配置 |
| 3 | gradle.properties | Gradle属性 |
| 4 | core/build.gradle.kts | 核心模块KMP配置 |
| 5 | androidApp/build.gradle.kts | Android应用配置 |
| 6 | core/src/commonMain/kotlin/pages/HomePage.kt | 首页 |
| 7 | core/src/commonMain/kotlin/pages/*Page.kt | 其他页面 |
| 8 | core/src/commonMain/kotlin/models/DataModels.kt | 数据模型 |
| 9 | core/src/commonMain/kotlin/utils/NavigationHelper.kt | 导航工具类 |
| 10 | README.md | 项目文档 |

### B. 支持的组件列表

| 组件名称 | AppSpec类型 | Kotlin DSL组件 |
|---------|-----------|--------------|
| 文本 | Text | Text { } |
| 按钮 | Button | Button { } |
| 容器 | View/Container | View { } |
| 输入框 | Input/TextField | InputView { } |
| 图片 | Image | Image { } |
| 列布局 | Column | Column { } |
| 行布局 | Row | Row { } |

### C. 支持的属性列表

| 属性名称 | AppSpec属性 | Kotlin DSL属性 |
|---------|-----------|--------------|
| 文本内容 | text | text("...") |
| 字体大小 | fontSize | fontSize(16f) |
| 颜色 | color | color(Color.parseColor("...")) |
| 背景色 | backgroundColor | backgroundColor(Color.parseColor("...")) |
| 宽高 | width/height | size(width, height) |
| 内边距 | padding | padding(16f) |
| 外边距 | marginTop/marginBottom/... | marginTop()/marginBottom()/... |
| 圆角 | borderRadius | cornerRadius(8f) |
| 粗体 | bold | fontWeightBold() |
| 占位符 | placeholder | placeholder("...") |
| 图片URL | src | imageUrl("...") |

---

**文档版本**: 1.0
**最后更新**: 2025-11-04
**作者**: Claude Code + Ingenio Team
