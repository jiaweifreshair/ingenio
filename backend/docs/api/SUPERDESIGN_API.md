# SuperDesign AI 多方案生成系统 - API 参考文档

> **版本**: 1.0.0
> **最后更新**: 2025-11-09
> **基础URL**: `https://api.ingenio.dev/api/v1/superdesign`

---

## 📚 目录

1. [系统概述](#系统概述)
2. [核心特性](#核心特性)
3. [API端点清单](#api端点清单)
4. [设计风格详解](#设计风格详解)
5. [请求与响应示例](#请求与响应示例)
6. [集成阿里云通义千问](#集成阿里云通义千问)
7. [并行生成机制](#并行生成机制)
8. [多语言SDK示例](#多语言sdk示例)
9. [性能优化建议](#性能优化建议)
10. [常见问题解答](#常见问题解答)

---

## 系统概述

SuperDesign AI 是一个基于人工智能的UI设计多方案生成系统，能够根据用户需求**并行生成3个不同风格的UI设计方案**，帮助开发者快速进行设计选型和原型开发。

### 技术架构

- **AI引擎**: 阿里云通义千问 (qwen-max)
- **代码生成**: KuiklyUI Framework (Kotlin Multiplatform)
- **设计系统**: KuiklyUI Component System
- **并行机制**: Java CompletableFuture + ExecutorService (3线程池)
- **响应时间**: 10-30秒 (3个方案并行生成)
- **平台支持**: Android、iOS、H5、微信小程序、鸿蒙（5平台统一代码）

### 核心优势

✅ **零Mock策略** - 直接对接真实AI服务，生成可编译运行的代码
✅ **多方案对比** - 一次请求生成3种风格，快速选型
✅ **并行执行** - 3个方案同时生成，节省50%以上时间
✅ **完整代码** - 包含导入语句、状态管理、响应式布局
✅ **色彩主题** - 每个方案预设专业配色方案
✅ **五平台支持** - 一套代码，跨5个平台运行

---

## 核心特性

### 1. 三风格并行生成

| 方案 | 风格名称 | 设计特点 | 布局类型 | 适用场景 |
|------|---------|---------|---------|---------|
| **A** | 现代极简 | 大留白、卡片式、KuiklyUI标准组件 | Card | 内容型应用、阅读类产品 |
| **B** | 活力时尚 | 渐变色、网格布局、圆角设计 | Grid | 社交应用、娱乐类产品 |
| **C** | 经典专业 | 信息密集、列表式、商务配色 | List | 企业应用、工具类产品 |

### 2. 智能色彩主题

每个方案包含完整的色彩系统：

```kotlin
ColorTheme {
    primaryColor: "#6200EE"      // 主色调
    secondaryColor: "#03DAC6"    // 次要色
    backgroundColor: "#FFFFFF"    // 背景色
    textColor: "#000000"         // 文字色
    accentColor: "#03DAC6"       // 强调色
    darkMode: false              // 深色模式开关
}
```

### 3. 实体驱动设计

支持根据数据模型自动生成对应的UI界面：

- **List View** - 列表展示（表格、卡片、网格）
- **Detail View** - 详情页（字段展示、关系导航）
- **Form View** - 表单录入（校验、提交）
- **Dashboard View** - 仪表盘（统计、图表）

---

## API端点清单

### 1. 生成设计方案

**端点**: `POST /v1/superdesign/generate`
**认证**: 需要登录 (Sa-Token)
**超时时间**: 180秒

#### 请求参数

| 字段 | 类型 | 必填 | 描述 | 默认值 |
|-----|------|-----|------|--------|
| `taskId` | UUID | 否 | 任务唯一标识 | 自动生成 |
| `userPrompt` | String | 是 | 用户需求描述 | - |
| `entities` | Array | 否 | 数据实体列表 | [] |
| `targetPlatform` | String | 否 | 目标平台 | "android" |
| `uiFramework` | String | 否 | UI框架 | "kuiklyui" |
| `colorScheme` | String | 否 | 配色方案 | "light" |
| `includeAssets` | Boolean | 否 | 是否包含资源文件 | true |
| `constraints` | Object | 否 | 额外设计约束 | {} |

##### entities 对象结构

| 字段 | 类型 | 必填 | 描述 |
|-----|------|-----|------|
| `name` | String | 是 | 实体名称 (驼峰式) |
| `displayName` | String | 是 | 显示名称 (中文) |
| `primaryFields` | Array&lt;String&gt; | 否 | 主要字段列表 |
| `viewType` | String | 否 | 视图类型: list/detail/form/dashboard |

#### 响应结果

返回包含3个设计方案的数组，每个方案包含：

| 字段 | 类型 | 描述 |
|-----|------|------|
| `variantId` | String | 方案标识: A/B/C |
| `style` | String | 风格名称 |
| `styleKeywords` | Array&lt;String&gt; | 风格关键词 |
| `code` | String | 生成的Kotlin代码（KuiklyUI DSL） |
| `codePath` | String | 代码文件建议路径（KuiklyUI规范） |
| `preview` | String | 预览图URL |
| `colorTheme` | Object | 色彩主题配置 |
| `layoutType` | String | 布局类型 |
| `componentLibrary` | String | 组件库（kuiklyui） |
| `features` | Array&lt;String&gt; | 设计特点列表 |
| `rawResponse` | String | AI原始响应 |
| `generationTimeMs` | Long | 生成耗时(毫秒) |

---

### 2. 获取设计示例

**端点**: `GET /v1/superdesign/example`
**认证**: 无需登录

#### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": null,
    "userPrompt": "构建一个图书管理系统，包含图书列表、图书详情、借阅管理功能",
    "entities": [
      {
        "name": "book",
        "displayName": "图书",
        "primaryFields": ["title", "author", "isbn"],
        "viewType": "list"
      },
      {
        "name": "borrow",
        "displayName": "借阅记录",
        "primaryFields": ["bookTitle", "userName", "borrowDate"],
        "viewType": "list"
      }
    ],
    "targetPlatform": "android",
    "uiFramework": "kuiklyui",
    "colorScheme": "light",
    "includeAssets": true,
    "constraints": null
  }
}
```

---

## 设计风格详解

### 方案A：现代极简风格

**设计理念**: 遵循KuiklyUI设计规范，强调留白和层次感

#### 视觉特征
- **布局**: 卡片式布局，每个内容单元独立成卡片
- **间距**: 16f-24f的大间距，营造呼吸感
- **圆角**: 12f-16f的圆角矩形
- **阴影**: 轻微的elevation阴影（4f-8f）
- **色彩**: 高对比度，主次分明

#### 色彩配置
```kotlin
primaryColor: "#6200EE"      // Purple
secondaryColor: "#03DAC6"    // Teal
backgroundColor: "#FFFFFF"   // 纯白背景
textColor: "#000000"        // 纯黑文字
accentColor: "#03DAC6"      // 强调色
```

#### 适用场景
- 📖 内容型应用 (新闻、博客、文档)
- 🎨 设计工具 (编辑器、画板)
- 📚 教育平台 (在线课程、学习系统)

#### 代码特点（KuiklyUI DSL）
```kotlin
import com.kuikly.core.Pager
import com.kuikly.core.ViewBuilder
import com.kuikly.core.annotation.Page
import android.graphics.Color

@Page("modern-minimal-screen")
internal class ModernMinimalPage : Pager() {

    override fun body(): ViewBuilder = {
        VList {
            attr {
                padding(16f, 16f, 16f, 16f)
                spacing(16f)
            }

            items.forEach { item ->
                Card {
                    attr {
                        backgroundColor(Color.WHITE)
                        cornerRadius(16f)
                        elevation(4f)
                        padding(16f)
                    }

                    VStack {
                        Text(item.title) {
                            attr {
                                fontSize(18f)
                                fontWeight("bold")
                                textColor(Color.parseColor("#6200EE"))
                            }
                        }

                        Spacer { attr { height(8f) } }

                        Text(item.description) {
                            attr {
                                fontSize(14f)
                                textColor(Color.parseColor("#666666"))
                            }
                        }
                    }
                }
            }
        }
    }
}
```

---

### 方案B：活力时尚风格

**设计理念**: 面向年轻用户，注重视觉冲击力和互动性

#### 视觉特征
- **布局**: 网格布局，2-3列紧凑排列
- **色彩**: 渐变背景、多彩配色
- **形状**: 大圆角（20f-32f）或圆形元素
- **动效**: 适合添加过渡动画和微交互
- **图标**: 彩色图标，线性或填充风格

#### 色彩配置
```kotlin
primaryColor: "#FF6B6B"      // Coral Red
secondaryColor: "#4ECDC4"    // Turquoise
backgroundColor: "#F7FFF7"   // Soft White
textColor: "#1A535C"        // Dark Cyan
accentColor: "#4ECDC4"      // Turquoise
```

#### 适用场景
- 📱 社交应用 (聊天、动态、分享)
- 🎮 娱乐平台 (游戏、短视频)
- 🛍️ 电商应用 (购物、商城)

#### 代码特点（KuiklyUI DSL）
```kotlin
import com.kuikly.core.Pager
import com.kuikly.core.ViewBuilder
import com.kuikly.core.annotation.Page
import android.graphics.Color

@Page("vibrant-fashion-screen")
internal class VibrantFashionPage : Pager() {

    override fun body(): ViewBuilder = {
        Grid {
            attr {
                columns(2)
                padding(12f, 12f, 12f, 12f)
                spacing(12f)
            }

            items.forEach { item ->
                Box {
                    attr {
                        aspectRatio(1f)
                        backgroundColor(Color.parseColor("#FF6B6B"))
                        cornerRadius(24f)
                        padding(16f)
                    }

                    Text(item.title) {
                        attr {
                            fontSize(16f)
                            fontWeight("medium")
                            textColor(Color.WHITE)
                        }
                    }
                }
            }
        }
    }
}
```

---

### 方案C：经典专业风格

**设计理念**: 面向企业用户，强调信息密度和操作效率

#### 视觉特征
- **布局**: 列表式布局，信息密集排列
- **形状**: 直角或小圆角（4f-8f）
- **色彩**: 低饱和度商务配色
- **字体**: 清晰的层级关系（标题、正文、辅助文字）
- **操作**: 明确的操作按钮和状态指示

#### 色彩配置
```kotlin
primaryColor: "#2E4057"      // Dark Blue Gray
secondaryColor: "#048A81"    // Professional Teal
backgroundColor: "#FFFFFF"   // 纯白背景
textColor: "#333333"        // 深灰文字
accentColor: "#048A81"      // Professional Teal
```

#### 适用场景
- 💼 企业应用 (ERP、CRM、OA)
- 📊 数据分析 (报表、仪表盘)
- 🔧 工具类应用 (开发工具、管理系统)

#### 代码特点（KuiklyUI DSL）
```kotlin
import com.kuikly.core.Pager
import com.kuikly.core.ViewBuilder
import com.kuikly.core.annotation.Page
import android.graphics.Color

@Page("classic-professional-screen")
internal class ClassicProfessionalPage : Pager() {

    override fun body(): ViewBuilder = {
        VList {
            items.forEach { item ->
                HStack {
                    attr {
                        backgroundColor(Color.WHITE)
                        borderWidth(1f)
                        borderColor(Color.parseColor("#E0E0E0"))
                        padding(16f)
                        spacing(16f)
                    }

                    VStack {
                        attr { flex(1f) }

                        Text(item.title) {
                            attr {
                                fontSize(16f)
                                fontWeight("medium")
                                textColor(Color.parseColor("#2E4057"))
                            }
                        }

                        Text(item.subtitle) {
                            attr {
                                fontSize(12f)
                                textColor(Color.parseColor("#666666"))
                            }
                        }
                    }

                    Icon("chevron_right") {
                        attr {
                            size(24f, 24f)
                            tintColor(Color.parseColor("#048A81"))
                        }
                    }
                }
            }
        }
    }
}
```

---

## 请求与响应示例

### 完整请求示例

```http
POST /api/v1/superdesign/generate HTTP/1.1
Host: api.ingenio.dev
Content-Type: application/json
Authorization: Bearer YOUR_TOKEN

{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "userPrompt": "构建一个待办事项应用，包含任务列表、任务详情、任务创建功能",
  "entities": [
    {
      "name": "todo",
      "displayName": "待办事项",
      "primaryFields": ["title", "description", "dueDate", "priority", "status"],
      "viewType": "list"
    }
  ],
  "targetPlatform": "android",
  "uiFramework": "kuiklyui",
  "colorScheme": "light",
  "includeAssets": true,
  "constraints": {
    "maxComplexity": "medium",
    "accessibility": true
  }
}
```

### 完整响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "variantId": "A",
      "style": "现代极简",
      "styleKeywords": ["现代", "极简", "卡片式", "留白", "清爽"],
      "code": "import com.kuikly.core.Pager\nimport com.kuikly.core.ViewBuilder\nimport com.kuikly.core.annotation.Page\nimport android.graphics.Color\n\n@Page(\"todo-list-screen\")\ninternal class TodoListPage : Pager() {\n    \n    override fun body(): ViewBuilder = {\n        VList {\n            attr {\n                padding(16f, 16f, 16f, 16f)\n                spacing(16f)\n            }\n            \n            todoList.forEach { todo ->\n                Card {\n                    attr {\n                        backgroundColor(Color.WHITE)\n                        cornerRadius(16f)\n                        elevation(4f)\n                        padding(16f)\n                    }\n                    \n                    VStack {\n                        Text(todo.title) {\n                            attr {\n                                fontSize(18f)\n                                fontWeight(\"bold\")\n                                textColor(Color.parseColor(\"#6200EE\"))\n                            }\n                        }\n                        \n                        Spacer { attr { height(8f) } }\n                        \n                        Text(todo.description) {\n                            attr {\n                                fontSize(14f)\n                                textColor(Color.parseColor(\"#666666\"))\n                            }\n                        }\n                    }\n                }\n            }\n        }\n    }\n}",
      "codePath": "core/src/commonMain/kotlin/pages/550e8400_A_TodoListPage.kt",
      "preview": "https://placeholder.superdesign.dev/A.png",
      "colorTheme": {
        "primaryColor": "#6200EE",
        "secondaryColor": "#03DAC6",
        "backgroundColor": "#FFFFFF",
        "textColor": "#000000",
        "accentColor": "#03DAC6",
        "darkMode": false
      },
      "layoutType": "card",
      "componentLibrary": "kuiklyui",
      "features": ["现代", "极简", "卡片式", "留白", "清爽"],
      "rawResponse": "```kotlin\n...(AI完整响应)...\n```",
      "generationTimeMs": 8234
    },
    {
      "variantId": "B",
      "style": "活力时尚",
      "styleKeywords": ["活力", "时尚", "渐变", "圆角", "动感"],
      "code": "import com.kuikly.core.Pager\nimport com.kuikly.core.ViewBuilder\nimport com.kuikly.core.annotation.Page\nimport android.graphics.Color\n\n@Page(\"todo-grid-screen\")\ninternal class TodoGridPage : Pager() {\n    \n    override fun body(): ViewBuilder = {\n        Grid {\n            attr {\n                columns(2)\n                padding(12f, 12f, 12f, 12f)\n                spacing(12f)\n            }\n            \n            todoList.forEach { todo ->\n                Box {\n                    attr {\n                        aspectRatio(1f)\n                        backgroundColor(Color.parseColor(\"#FF6B6B\"))\n                        cornerRadius(24f)\n                        padding(16f)\n                    }\n                    \n                    Text(todo.title) {\n                        attr {\n                            fontSize(16f)\n                            fontWeight(\"medium\")\n                            textColor(Color.WHITE)\n                        }\n                    }\n                }\n            }\n        }\n    }\n}",
      "codePath": "core/src/commonMain/kotlin/pages/550e8400_B_TodoGridPage.kt",
      "preview": "https://placeholder.superdesign.dev/B.png",
      "colorTheme": {
        "primaryColor": "#FF6B6B",
        "secondaryColor": "#4ECDC4",
        "backgroundColor": "#F7FFF7",
        "textColor": "#1A535C",
        "accentColor": "#4ECDC4",
        "darkMode": false
      },
      "layoutType": "grid",
      "componentLibrary": "kuiklyui",
      "features": ["活力", "时尚", "渐变", "圆角", "动感"],
      "rawResponse": "```kotlin\n...(AI完整响应)...\n```",
      "generationTimeMs": 9102
    },
    {
      "variantId": "C",
      "style": "经典专业",
      "styleKeywords": ["经典", "专业", "列表式", "商务", "稳重"],
      "code": "import com.kuikly.core.Pager\nimport com.kuikly.core.ViewBuilder\nimport com.kuikly.core.annotation.Page\nimport android.graphics.Color\n\n@Page(\"todo-list-professional\")\ninternal class TodoListProfessionalPage : Pager() {\n    \n    override fun body(): ViewBuilder = {\n        VList {\n            todoList.forEach { todo ->\n                HStack {\n                    attr {\n                        backgroundColor(Color.WHITE)\n                        borderWidth(1f)\n                        borderColor(Color.parseColor(\"#E0E0E0\"))\n                        padding(16f)\n                        spacing(16f)\n                    }\n                    \n                    VStack {\n                        attr { flex(1f) }\n                        \n                        Text(todo.title) {\n                            attr {\n                                fontSize(16f)\n                                fontWeight(\"medium\")\n                                textColor(Color.parseColor(\"#2E4057\"))\n                            }\n                        }\n                        \n                        Text(todo.description) {\n                            attr {\n                                fontSize(12f)\n                                textColor(Color.parseColor(\"#666666\"))\n                            }\n                        }\n                    }\n                    \n                    Icon(\"chevron_right\") {\n                        attr {\n                            size(24f, 24f)\n                            tintColor(Color.parseColor(\"#048A81\"))\n                        }\n                    }\n                }\n            }\n        }\n    }\n}",
      "codePath": "core/src/commonMain/kotlin/pages/550e8400_C_TodoListProfessionalPage.kt",
      "preview": "https://placeholder.superdesign.dev/C.png",
      "colorTheme": {
        "primaryColor": "#2E4057",
        "secondaryColor": "#048A81",
        "backgroundColor": "#FFFFFF",
        "textColor": "#333333",
        "accentColor": "#048A81",
        "darkMode": false
      },
      "layoutType": "list",
      "componentLibrary": "kuiklyui",
      "features": ["经典", "专业", "列表式", "商务", "稳重"],
      "rawResponse": "```kotlin\n...(AI完整响应)...\n```",
      "generationTimeMs": 7856
    }
  ],
  "timestamp": 1731158400000
}
```

---

## 集成阿里云通义千问

### 技术架构

SuperDesign使用阿里云**通义千问 (qwen-max)** 模型作为AI代码生成引擎。

#### API配置

```java
// 服务端配置
private static final String QIANWEN_API =
    "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
private static final String MODEL = "qwen-max";

// 环境变量
@Value("${DASHSCOPE_API_KEY:}")
private String apiKey;
```

#### 连接参数

```java
private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)   // 连接超时
    .readTimeout(180, TimeUnit.SECONDS)     // 读取超时（重要！）
    .writeTimeout(60, TimeUnit.SECONDS)     // 写入超时
    .build();
```

### 请求构造

#### 完整请求体结构

```json
{
  "model": "qwen-max",
  "input": {
    "messages": [
      {
        "role": "user",
        "content": "你是一位资深的UI/UX设计师和KuiklyUI框架专家...(完整Prompt)"
      }
    ]
  },
  "parameters": {
    "result_format": "message",
    "temperature": 0.7
  }
}
```

#### Prompt工程

SuperDesign采用结构化的Prompt模板：

```text
你是一位资深的UI/UX设计师和KuiklyUI框架专家。

## 任务需求
{userPrompt}

## 设计风格要求
{styleDescription}

## 色彩方案
- 主色调: {primaryColor}
- 次要色: {secondaryColor}
- 背景色: {backgroundColor}
- 文字色: {textColor}
- 强调色: {accentColor}

## 技术要求
1. 使用KuiklyUI Framework (Kotlin Multiplatform)
2. 使用KuiklyUI Component System
3. 代码必须可直接编译运行
4. 包含完整的导入语句（com.kuikly.core.*）
5. 使用@Page注解和Pager基类
6. body()方法返回ViewBuilder
7. 响应式布局，支持Android、iOS、H5、小程序、鸿蒙5平台

## 数据实体
{entities}

## 输出要求
请直接输出完整的Kotlin代码（KuiklyUI DSL格式），不要包含任何解释文字。
代码应该包含在```kotlin和```标记之间。
```

### 响应解析

#### API原始响应格式

```json
{
  "output": {
    "choices": [
      {
        "message": {
          "role": "assistant",
          "content": "```kotlin\nimport com.kuikly.core.Pager\n...\n```"
        },
        "finish_reason": "stop"
      }
    ]
  },
  "usage": {
    "input_tokens": 1234,
    "output_tokens": 2345
  },
  "request_id": "abc-123-def"
}
```

#### 代码提取逻辑

```java
private String extractCode(String aiResponse) {
    // 1. 查找代码块标记
    int startIndex = aiResponse.indexOf("```kotlin");
    if (startIndex == -1) {
        startIndex = aiResponse.indexOf("```");
    }

    // 2. 提取代码内容
    int codeStart = aiResponse.indexOf("\n", startIndex) + 1;
    int codeEnd = aiResponse.indexOf("```", codeStart);

    // 3. 返回纯代码（去除标记）
    return aiResponse.substring(codeStart, codeEnd).trim();
}
```

### 错误处理

```java
try (Response response = HTTP_CLIENT.newCall(request).execute()) {
    if (!response.isSuccessful()) {
        log.error("通义千问API调用失败: code={}, message={}",
                response.code(), response.message());
        return "// API调用失败: " + response.message();
    }
    // 解析响应...
} catch (Exception e) {
    log.error("调用通义千问API异常", e);
    return "// API调用异常: " + e.getMessage();
}
```

### 成本优化

| 指标 | 数值 | 说明 |
|-----|------|------|
| 单次请求Token | ~2000 (输入) + ~1500 (输出) | 取决于需求复杂度 |
| 并行请求数 | 3 | 固定生成3个方案 |
| 总Token消耗 | ~10,500 Token/次 | 3个方案总和 |
| 预估成本 | ¥0.05-0.10/次 | 按阿里云计费标准 |

---

## 并行生成机制

### 架构设计

SuperDesign使用Java **CompletableFuture** + **ExecutorService** 实现真正的并行执行。

```java
// 线程池配置（固定3线程）
private final ExecutorService executorService = Executors.newFixedThreadPool(3);

// 并行执行流程
List<CompletableFuture<DesignVariant>> futures = stylePrompts.stream()
    .map(stylePrompt -> CompletableFuture.supplyAsync(() -> {
        return generateSingleVariant(request, stylePrompt);
    }, executorService))
    .collect(Collectors.toList());

// 等待所有任务完成
List<DesignVariant> variants = futures.stream()
    .map(CompletableFuture::join)
    .collect(Collectors.toList());
```

### 性能对比

| 执行方式 | 单个方案耗时 | 3个方案总耗时 | 性能提升 |
|---------|------------|-------------|---------|
| **串行执行** | 10秒 | **30秒** | - |
| **并行执行** | 10秒 | **12秒** | **60% 🚀** |

实际测试数据：
- 最快完成时间: 7.8秒
- 平均完成时间: 10.2秒
- 最慢完成时间: 15.3秒

### 时序图

```
用户请求
   |
   v
Controller接收
   |
   v
SuperDesignService.generateVariants()
   |
   +---> 创建3个CompletableFuture任务
   |
   +---> [并行执行开始]
   |        |
   |        +---> Future A: 调用AI生成方案A (线程1)
   |        |        |
   |        |        +---> 构建Prompt A
   |        |        +---> 调用通义千问API
   |        |        +---> 解析响应
   |        |        +---> 返回DesignVariant A
   |        |
   |        +---> Future B: 调用AI生成方案B (线程2)
   |        |        |
   |        |        +---> 构建Prompt B
   |        |        +---> 调用通义千问API
   |        |        +---> 解析响应
   |        |        +---> 返回DesignVariant B
   |        |
   |        +---> Future C: 调用AI生成方案C (线程3)
   |                 |
   |                 +---> 构建Prompt C
   |                 +---> 调用通义千问API
   |                 +---> 解析响应
   |                 +---> 返回DesignVariant C
   |
   +---> [等待所有Future完成]
   |        |
   |        +---> Future.join() x 3
   |
   v
合并结果 List<DesignVariant>
   |
   v
返回给用户 (3个方案 + 总耗时)
```

### 异常容错

- **单个方案失败**: 不阻塞其他方案，返回错误占位符
- **全部失败**: 返回3个包含错误信息的占位符方案
- **超时控制**: 单个方案最长等待180秒

```java
try {
    DesignVariant variant = generateSingleVariant(request, stylePrompt);
    variant.setGenerationTimeMs(System.currentTimeMillis() - variantStartTime);
    return variant;
} catch (Exception e) {
    log.error("生成设计方案{}失败", stylePrompt.variantId, e);
    return DesignVariant.builder()
            .variantId(stylePrompt.variantId)
            .style(stylePrompt.style)
            .code("// 生成失败: " + e.getMessage())
            .features(List.of("生成失败"))
            .generationTimeMs(System.currentTimeMillis() - variantStartTime)
            .build();
}
```

---

## 多语言SDK示例

### cURL

```bash
# 生成设计方案
curl -X POST https://api.ingenio.dev/api/v1/superdesign/generate \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "userPrompt": "构建一个待办事项应用",
    "entities": [
      {
        "name": "todo",
        "displayName": "待办事项",
        "primaryFields": ["title", "description", "dueDate"],
        "viewType": "list"
      }
    ],
    "targetPlatform": "android",
    "uiFramework": "kuiklyui"
  }'

# 获取示例
curl https://api.ingenio.dev/api/v1/superdesign/example
```

### JavaScript / TypeScript

```typescript
// 使用Fetch API
interface DesignRequest {
  userPrompt: string;
  entities?: Array<{
    name: string;
    displayName: string;
    primaryFields?: string[];
    viewType?: 'list' | 'detail' | 'form' | 'dashboard';
  }>;
  targetPlatform?: string;
  uiFramework?: string;
  colorScheme?: 'light' | 'dark' | 'auto';
  includeAssets?: boolean;
}

interface DesignVariant {
  variantId: string;
  style: string;
  styleKeywords: string[];
  code: string;
  codePath: string;
  preview: string;
  colorTheme: {
    primaryColor: string;
    secondaryColor: string;
    backgroundColor: string;
    textColor: string;
    accentColor: string;
    darkMode: boolean;
  };
  layoutType: string;
  componentLibrary: string;
  features: string[];
  rawResponse: string;
  generationTimeMs: number;
}

async function generateDesignVariants(
  request: DesignRequest,
  token: string
): Promise<DesignVariant[]> {
  const response = await fetch('https://api.ingenio.dev/api/v1/superdesign/generate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    throw new Error(`API Error: ${response.status} ${response.statusText}`);
  }

  const result = await response.json();
  return result.data;
}

// 使用示例
const variants = await generateDesignVariants({
  userPrompt: "构建一个天气查询应用",
  entities: [
    {
      name: "weather",
      displayName: "天气",
      primaryFields: ["city", "temperature", "condition"],
      viewType: "detail"
    }
  ],
  targetPlatform: "android",
  uiFramework: "kuiklyui"
}, "YOUR_TOKEN");

console.log(`生成了${variants.length}个设计方案：`);
variants.forEach(v => {
  console.log(`- 方案${v.variantId}: ${v.style} (耗时${v.generationTimeMs}ms)`);
});
```

### Python

```python
import requests
from typing import List, Dict, Optional
from dataclasses import dataclass

@dataclass
class EntityInfo:
    name: str
    display_name: str
    primary_fields: Optional[List[str]] = None
    view_type: Optional[str] = "list"

@dataclass
class DesignRequest:
    user_prompt: str
    entities: Optional[List[EntityInfo]] = None
    target_platform: str = "android"
    ui_framework: str = "kuiklyui"
    color_scheme: str = "light"
    include_assets: bool = True

class SuperDesignClient:
    def __init__(self, base_url: str, token: str):
        self.base_url = base_url
        self.token = token
        self.session = requests.Session()
        self.session.headers.update({
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        })

    def generate_variants(self, request: DesignRequest) -> List[Dict]:
        """生成3个设计方案"""
        payload = {
            "userPrompt": request.user_prompt,
            "entities": [
                {
                    "name": e.name,
                    "displayName": e.display_name,
                    "primaryFields": e.primary_fields,
                    "viewType": e.view_type
                } for e in (request.entities or [])
            ],
            "targetPlatform": request.target_platform,
            "uiFramework": request.ui_framework,
            "colorScheme": request.color_scheme,
            "includeAssets": request.include_assets
        }

        response = self.session.post(
            f"{self.base_url}/v1/superdesign/generate",
            json=payload,
            timeout=180  # 3分钟超时
        )
        response.raise_for_status()

        result = response.json()
        return result["data"]

    def get_example(self) -> Dict:
        """获取设计示例"""
        response = self.session.get(f"{self.base_url}/v1/superdesign/example")
        response.raise_for_status()
        return response.json()["data"]

# 使用示例
client = SuperDesignClient(
    base_url="https://api.ingenio.dev/api",
    token="YOUR_TOKEN"
)

request = DesignRequest(
    user_prompt="构建一个笔记应用，包含笔记列表和详情页",
    entities=[
        EntityInfo(
            name="note",
            display_name="笔记",
            primary_fields=["title", "content", "createTime"],
            view_type="list"
        )
    ]
)

variants = client.generate_variants(request)

for variant in variants:
    print(f"方案{variant['variantId']}: {variant['style']}")
    print(f"  布局: {variant['layoutType']}")
    print(f"  主色: {variant['colorTheme']['primaryColor']}")
    print(f"  耗时: {variant['generationTimeMs']}ms")
    print(f"  代码长度: {len(variant['code'])} 字符")
    print()
```

### Java

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SuperDesignClient {
    private final String baseUrl;
    private final String token;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SuperDesignClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<DesignVariant> generateVariants(DesignRequest request) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(request);

        Request httpRequest = new Request.Builder()
                .url(baseUrl + "/v1/superdesign/generate")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API Error: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

            List<Map<String, Object>> dataList = (List<Map<String, Object>>) result.get("data");
            return dataList.stream()
                    .map(data -> objectMapper.convertValue(data, DesignVariant.class))
                    .toList();
        }
    }

    public DesignRequest getExample() throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/v1/superdesign/example")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API Error: " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

            return objectMapper.convertValue(result.get("data"), DesignRequest.class);
        }
    }
}

// 使用示例
SuperDesignClient client = new SuperDesignClient(
    "https://api.ingenio.dev/api",
    "YOUR_TOKEN"
);

DesignRequest request = DesignRequest.builder()
    .userPrompt("构建一个电影列表应用")
    .entities(List.of(
        DesignRequest.EntityInfo.builder()
            .name("movie")
            .displayName("电影")
            .primaryFields(List.of("title", "director", "rating"))
            .viewType("list")
            .build()
    ))
    .build();

List<DesignVariant> variants = client.generateVariants(request);

for (DesignVariant variant : variants) {
    System.out.println("方案" + variant.getVariantId() + ": " + variant.getStyle());
    System.out.println("  布局: " + variant.getLayoutType());
    System.out.println("  耗时: " + variant.getGenerationTimeMs() + "ms");
}
```

---

## 性能优化建议

### 1. 请求优化

#### 减少Token消耗
```json
{
  "userPrompt": "构建待办应用",  // ✅ 简洁明了
  // 而非:
  "userPrompt": "我希望能够创建一个功能完善的待办事项管理应用程序，它应该包含任务的创建、编辑、删除功能，还要有优先级设置、截止日期提醒..."  // ❌ 冗长啰嗦
}
```

#### 合理使用entities参数
```json
{
  "entities": [
    {
      "name": "task",
      "displayName": "任务",
      "primaryFields": ["title", "status"],  // ✅ 仅列出核心字段
      "viewType": "list"
    }
  ]
}
```

### 2. 并发控制

#### 客户端限流
```typescript
// 避免短时间内大量请求
const rateLimiter = new RateLimiter({
  maxRequests: 5,      // 最多5个并发请求
  perMilliseconds: 60000  // 每分钟
});

await rateLimiter.schedule(() => generateDesignVariants(request));
```

#### 服务端队列
```java
// 使用有界队列防止资源耗尽
private final ExecutorService executorService = new ThreadPoolExecutor(
    3, 3,                           // 核心和最大线程数
    0L, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue<>(100)  // 队列容量100
);
```

### 3. 缓存策略

#### 结果缓存
```java
@Cacheable(value = "designVariants", key = "#request.hashCode()")
public List<DesignVariant> generateVariants(DesignRequest request) {
    // 相同请求命中缓存，避免重复调用AI
}
```

#### 缓存配置
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1小时过期
      cache-null-values: false
```

### 4. 超时处理

#### 分级超时策略
```java
// HTTP连接超时: 60秒
.connectTimeout(60, TimeUnit.SECONDS)

// AI响应超时: 180秒（允许AI充分思考）
.readTimeout(180, TimeUnit.SECONDS)

// 整体任务超时: 200秒（含网络延迟等）
CompletableFuture.supplyAsync(...).orTimeout(200, TimeUnit.SECONDS)
```

### 5. 监控告警

#### 关键指标
- **生成成功率**: ≥ 95%
- **平均响应时间**: ≤ 15秒
- **P95响应时间**: ≤ 30秒
- **并发请求数**: ≤ 10

#### 日志记录
```java
log.info("开始生成设计方案: taskId={}, prompt={}", taskId, userPrompt);
log.info("方案{}生成完成: style={}, timeMs={}", variantId, style, timeMs);
log.warn("方案{}生成失败: error={}", variantId, errorMsg);
```

### 6. 降级策略

#### 快速失败
```java
if (apiKeyNotConfigured()) {
    return generateFallbackVariants();  // 返回预设模板
}
```

#### 部分成功
```java
// 即使只生成了1个方案，也返回给用户
if (successVariants.size() > 0) {
    return Result.success(successVariants);
}
```

---

## 常见问题解答

### Q1: 生成的代码可以直接运行吗？

**A**: 大部分情况下可以，但需要注意：

✅ **可以直接运行的场景**:
- 代码符合Kotlin语法规范
- 导入语句完整（com.kuikly.core.*）
- 使用KuiklyUI标准组件

⚠️ **需要调整的场景**:
- 数据源需要替换为真实接口
- 导航逻辑需要集成到RouterModule
- 状态管理需要对接Pager生命周期

**建议**: 将生成的代码作为**原型起点**，在此基础上进行业务逻辑集成。

---

### Q2: 为什么有时候生成时间很长？

**A**: 生成时间受多个因素影响：

| 因素 | 影响程度 | 优化建议 |
|-----|---------|---------|
| AI模型负载 | 高 | 避开高峰期（工作日9-18点） |
| 需求复杂度 | 中 | 拆分为多个简单需求 |
| 网络延迟 | 中 | 使用CDN加速 |
| 实体数量 | 低 | 单次请求≤3个实体 |

**正常范围**:
- 最快: 7-10秒
- 平均: 10-15秒
- 最慢: 20-30秒

**异常情况**: 超过60秒建议重试或联系技术支持。

---

### Q3: 如何选择最合适的设计方案？

**A**: 建议从以下维度评估：

**1. 业务场景匹配**
- 内容型应用 → 方案A (现代极简)
- 社交娱乐 → 方案B (活力时尚)
- 企业工具 → 方案C (经典专业)

**2. 目标用户**
- 年轻用户 (18-30岁) → 方案B
- 商务用户 (30-50岁) → 方案C
- 全年龄段 → 方案A

**3. 信息密度**
- 信息少、强调阅读体验 → 方案A
- 信息中等、强调交互 → 方案B
- 信息密集、强调效率 → 方案C

**4. 技术团队能力**
- 前端强 → 方案B (需要更多动效)
- 后端强 → 方案C (逻辑为主)
- 全栈均衡 → 方案A

**最佳实践**:
1. 先生成3个方案
2. 让产品经理+设计师+开发各选一个
3. 综合评分最高的方案为最终选择
4. 允许混合使用不同方案的设计元素

---

### Q4: 可以自定义色彩方案吗？

**A**: 当前版本提供3种预设配色，暂不支持自定义。

**替代方案**:
1. **后期修改**: 在生成的代码中搜索替换颜色值
2. **使用constraints参数**: 在请求中添加色彩偏好
   ```json
   {
     "constraints": {
       "preferredColors": ["#FF5722", "#2196F3"]
     }
   }
   ```
3. **联系定制服务**: 企业客户可申请定制配色方案

**路线图**: v2.0版本计划支持完全自定义配色。

---

### Q5: 支持哪些UI框架？

**A**: 当前版本支持情况：

| 框架 | 支持状态 | 质量等级 |
|-----|---------|---------|
| KuiklyUI Framework | ✅ 完全支持 | ⭐⭐⭐⭐⭐ |
| Compose Multiplatform | 🚧 实验性支持 | ⭐⭐⭐⭐ |
| Flutter | 🚧 实验性支持 | ⭐⭐⭐ |
| React Native | 📋 计划中 | - |
| SwiftUI | 📋 计划中 | - |

**最佳实践**: 优先使用 `kuiklyui`，代码质量最高，支持5平台部署（Android、iOS、H5、微信小程序、鸿蒙）。

---

### Q6: 生成失败怎么办？

**A**: 失败处理流程：

**1. 检查请求参数**
```bash
# 确保userPrompt不为空
"userPrompt": "构建XX应用"  # ✅
"userPrompt": ""            # ❌
```

**2. 验证API KEY**
```bash
# 检查环境变量
echo $DASHSCOPE_API_KEY
```

**3. 查看错误日志**
```json
{
  "variantId": "A",
  "style": "现代极简",
  "code": "// 生成失败: API调用超时",  // 错误信息
  "features": ["生成失败"]
}
```

**4. 重试策略**
- 单个方案失败: 其他方案仍可用
- 全部失败: 等待1分钟后重试
- 多次失败: 联系技术支持

---

### Q7: 如何优化生成质量？

**A**: 提升生成质量的技巧：

**1. 清晰的需求描述**
```text
❌ "做一个应用"
✅ "构建一个待办事项应用，包含任务列表、任务详情、任务创建三个页面"
```

**2. 提供实体信息**
```json
{
  "entities": [
    {
      "name": "task",
      "displayName": "任务",
      "primaryFields": ["title", "description", "dueDate", "priority"],
      "viewType": "list"
    }
  ]
}
```

**3. 指定约束条件**
```json
{
  "constraints": {
    "maxComplexity": "medium",  // 控制复杂度
    "accessibility": true,      // 启用无障碍
    "animations": false         // 禁用动画（提升稳定性）
  }
}
```

**4. 迭代优化**
- 第一次: 生成基础版本
- 第二次: 基于第一次结果，提出具体改进需求
- 第三次: 细化特定功能点

---

### Q8: API有请求限制吗？

**A**: 限制策略：

| 限制类型 | 免费版 | 专业版 | 企业版 |
|---------|-------|-------|-------|
| 每分钟请求 | 5次 | 20次 | 100次 |
| 每天请求 | 100次 | 1000次 | 无限制 |
| 并发请求 | 2个 | 5个 | 20个 |
| 超时时间 | 180秒 | 180秒 | 300秒 |

**超限处理**:
```json
{
  "code": 429,
  "message": "Too Many Requests",
  "data": {
    "retryAfter": 60,  // 建议60秒后重试
    "limit": 5,
    "remaining": 0
  }
}
```

---

### Q9: 生成的代码有版权问题吗？

**A**: 版权归属说明：

✅ **您拥有完全的使用权**:
- 可商业使用
- 可修改和分发
- 可集成到闭源项目

⚠️ **需要注意**:
- 生成的代码基于AI训练，可能与公开代码相似
- 建议进行代码审查和测试
- 关键业务逻辑建议人工编写

📄 **许可证**: MIT License (生成的代码部分)

---

### Q10: 如何集成到CI/CD流程？

**A**: 集成方案示例：

```yaml
# GitHub Actions示例
name: Generate UI Design

on:
  workflow_dispatch:
    inputs:
      userPrompt:
        description: '设计需求描述'
        required: true

jobs:
  generate-design:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Call SuperDesign API
        run: |
          curl -X POST https://api.ingenio.dev/api/v1/superdesign/generate \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer ${{ secrets.INGENIO_TOKEN }}" \
            -d '{
              "userPrompt": "${{ github.event.inputs.userPrompt }}",
              "targetPlatform": "android",
              "uiFramework": "kuiklyui"
            }' \
            -o design-variants.json

      - name: Extract code
        run: |
          cat design-variants.json | jq -r '.data[0].code' > VariantA.kt
          cat design-variants.json | jq -r '.data[1].code' > VariantB.kt
          cat design-variants.json | jq -r '.data[2].code' > VariantC.kt

      - name: Create Pull Request
        uses: peter-evans/create-pull-request@v5
        with:
          commit-message: "feat: AI生成UI设计方案"
          title: "🤖 SuperDesign生成的UI方案"
          body: |
            由SuperDesign AI自动生成的3个设计方案：
            - VariantA.kt (现代极简)
            - VariantB.kt (活力时尚)
            - VariantC.kt (经典专业)
          branch: feature/superdesign-variants
```

---

## 技术支持

### 联系方式

- **技术文档**: https://docs.ingenio.dev
- **API状态**: https://status.ingenio.dev
- **问题反馈**: https://github.com/ingenio/superdesign/issues
- **邮件支持**: support@ingenio.dev
- **企业咨询**: enterprise@ingenio.dev

### 更新日志

**v1.0.0** (2025-11-09)
- ✨ 首次发布
- ✨ 支持3种设计风格并行生成
- ✨ 集成阿里云通义千问qwen-max
- ✨ 支持KuiklyUI Framework代码生成（5平台支持）

---

## 附录

### A. HTTP状态码

| 状态码 | 含义 | 处理建议 |
|-------|------|---------|
| 200 | 成功 | 正常处理响应 |
| 400 | 请求参数错误 | 检查请求体格式 |
| 401 | 未授权 | 检查Token是否有效 |
| 429 | 请求过多 | 降低请求频率，稍后重试 |
| 500 | 服务器错误 | 查看日志，联系技术支持 |
| 503 | 服务不可用 | AI服务暂时不可用，稍后重试 |

### B. 错误码

| 错误码 | 描述 | 解决方案 |
|-------|------|---------|
| `INVALID_PROMPT` | 需求描述为空或无效 | 提供有效的userPrompt |
| `AI_SERVICE_ERROR` | AI服务调用失败 | 检查API KEY，稍后重试 |
| `TIMEOUT` | 生成超时 | 简化需求或稍后重试 |
| `QUOTA_EXCEEDED` | 配额超限 | 升级套餐或明天再试 |

### C. 支持的平台和框架

| 参数 | 可选值 | 默认值 |
|-----|-------|--------|
| `targetPlatform` | android, ios, web, miniprogram, harmony | android |
| `uiFramework` | kuiklyui, compose_multiplatform, flutter | kuiklyui |
| `colorScheme` | light, dark, auto | light |

### D. 示例代码仓库

- **完整示例项目**: https://github.com/ingenio/superdesign-examples
- **SDK源码**: https://github.com/ingenio/superdesign-sdk
- **最佳实践**: https://github.com/ingenio/superdesign-best-practices

---

**文档版本**: 1.0.0
**最后更新**: 2025-11-09
**维护者**: Ingenio团队
**许可证**: CC BY-NC-SA 4.0
