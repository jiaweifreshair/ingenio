# AI功能使用文档

> **项目名称**: {{APP_NAME}}
> **生成日期**: {{GENERATION_DATE}}
> **框架**: Kotlin Multiplatform + KuiklyUI
> **AI服务**: 七牛云通义千问（Qwen-Max）

---

## 📋 目录

- [功能概述](#功能概述)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API使用](#api使用)
- [UI组件](#ui组件)
- [错误处理](#错误处理)
- [最佳实践](#最佳实践)
- [故障排查](#故障排查)
- [API参考](#api参考)

---

## 功能概述

本项目集成了七牛云AI服务（通义千问 Qwen-Max），提供以下AI能力：

### ✨ 核心功能

- **智能对话**：支持多轮对话，上下文理解
- **流式响应**：实时显示AI回复内容，提升用户体验
- **消息历史**：自动保存对话记录，支持上下文连续
- **错误处理**：友好的错误提示和自动重试机制
- **配置灵活**：支持多种配置方式（环境变量、配置文件）

### 🎯 技术特性

- **KuiklyUI框架**：声明式UI，跨平台支持（Android、iOS、H5、小程序、鸿蒙）
- **Kotlin协程**：异步处理AI请求，不阻塞UI线程
- **Ktor客户端**：高性能HTTP客户端，支持SSE流式响应
- **SSE流式传输**：Server-Sent Events实时推送AI生成内容
- **类型安全**：完整的Kotlin类型系统，编译期错误检查

---

## 快速开始

### 1. 配置API密钥

#### 方式A：使用 local.properties（开发环境推荐）

```bash
# 1. 复制模板文件
cp local.properties.template local.properties

# 2. 编辑 local.properties，填写API密钥
QINIU_API_KEY=your_actual_api_key_here

# 3. 确保 .gitignore 已包含 local.properties
```

#### 方式B：使用环境变量（生产环境推荐）

```bash
# Linux/macOS
export QINIU_API_KEY=your_actual_api_key_here
export AI_MODEL=qwen-max

# Windows (PowerShell)
$env:QINIU_API_KEY="your_actual_api_key_here"
$env:AI_MODEL="qwen-max"
```

### 2. 获取七牛云API密钥

1. 访问 [七牛云官网](https://www.qiniu.com)
2. 注册/登录账号
3. 进入 **"AI服务"** -> **"API密钥管理"**
4. 创建新密钥或复制现有密钥
5. 确保密钥有AI服务的访问权限

### 3. 运行应用

```bash
# 编译项目
./gradlew build

# 运行Android应用
./gradlew :androidApp:installDebug

# 或使用IDE运行
# 打开项目 -> 选择 androidApp -> 点击运行
```

### 4. 验证配置

启动应用后，检查日志是否显示：

```
✅ AI配置加载成功
- API密钥: sk-abc****xyz
- 基础URL: https://api.qnaigc.com/v1
- 模型: qwen-max
- 温度: 0.7
```

---

## 配置说明

### 配置参数完整列表

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|-----|------|------|--------|------|
| `QINIU_API_KEY` | String | ✅ | - | 七牛云API密钥 |
| `AI_BASE_URL` | String | ❌ | `https://api.qnaigc.com/v1` | API基础URL |
| `AI_MODEL` | String | ❌ | `qwen-max` | AI模型名称 |
| `AI_TEMPERATURE` | Double | ❌ | `0.7` | 温度参数（0.0-2.0） |
| `AI_MAX_TOKENS` | Int | ❌ | `2000` | 最大Token数 |
| `AI_TIMEOUT` | Long | ❌ | `60000` | 请求超时（毫秒） |
| `AI_DEBUG` | Boolean | ❌ | `false` | 调试模式 |

### 模型选择指南

| 模型 | 性能 | 速度 | 成本 | 适用场景 |
|-----|------|------|------|---------|
| **qwen-max** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | 高 | 复杂任务、创意生成 |
| **qwen-turbo** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | 中 | 通用对话、问答 |
| **qwen-plus** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | 低 | 简单任务、高频调用 |

### 温度参数设置

```kotlin
// 事实性回答（适合客服、问答系统）
AI_TEMPERATURE=0.0

// 平衡模式（推荐，适合大多数场景）
AI_TEMPERATURE=0.7

// 创意生成（适合文案、故事创作）
AI_TEMPERATURE=1.5
```

---

## API使用

### 基础用法：普通对话

```kotlin
import {{PACKAGE_NAME}}.ai.AIService
import {{PACKAGE_NAME}}.config.AIConfig

// 创建AI服务实例
val aiService = AIService(AIConfig.apiKey)

// 构建消息历史
val messages = listOf(
    ChatMessage(role = "user", content = "你好，请介绍一下Kotlin")
)

// 发送请求（协程）
val response = aiService.chat(messages)
println("AI回复: $response")

// 清理资源
aiService.close()
```

### 高级用法：流式响应

```kotlin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

// 在协程作用域中使用
pagerScope.launch {
    val messages = listOf(
        ChatMessage(role = "user", content = "写一篇关于AI的文章")
    )

    // 流式响应
    var fullText = ""
    aiService.chatStream(messages).collect { chunk ->
        fullText += chunk
        // 实时更新UI
        updateUI(fullText)
    }

    println("完整回复: $fullText")
}
```

### 多轮对话示例

```kotlin
// 保存对话历史
val conversationHistory = mutableListOf<ChatMessage>()

// 第一轮对话
conversationHistory.add(ChatMessage(role = "user", content = "Kotlin有什么优势？"))
val response1 = aiService.chat(conversationHistory)
conversationHistory.add(ChatMessage(role = "assistant", content = response1))

// 第二轮对话（AI会记住上下文）
conversationHistory.add(ChatMessage(role = "user", content = "举个例子说明"))
val response2 = aiService.chat(conversationHistory)
conversationHistory.add(ChatMessage(role = "assistant", content = response2))

// 限制历史记录长度（避免Token超限）
val recentHistory = conversationHistory.takeLast(10)
```

---

## UI组件

### AIServicePager页面

已自动生成的AI聊天页面，包含以下组件：

#### 1. TitleBar（标题栏）

```kotlin
private fun TitleBar(): ViewBuilder = {
    View {
        attr {
            size(pagerData.pageViewWidth, 60f)
            backgroundColor(Color.parseColor("#6366F1"))
            padding(16f)
        }
        Text {
            attr {
                text("AI助手")
                fontSize(20f)
                color(Color.WHITE)
            }
        }
    }
}
```

#### 2. MessageList（消息列表）

- 自动滚动到最新消息
- 支持用户和AI消息的不同样式
- 实时显示流式响应

#### 3. MessageBubble（消息气泡）

- 用户消息：右对齐，蓝色背景
- AI消息：左对齐，灰色背景
- 流式响应时显示光标动画

#### 4. InputBar（输入栏）

- 文本输入框
- 发送按钮（加载时禁用）
- 自动清空输入框

#### 5. ErrorBanner（错误提示）

- 友好的错误消息
- 可关闭的横幅
- 自动消失（可配置）

### 自定义UI

```kotlin
// 修改主题色
backgroundColor(Color.parseColor("#YOUR_COLOR"))

// 调整字体大小
fontSize(18f)

// 修改圆角半径
cornerRadius(16f)

// 添加阴影效果
shadowColor(Color.BLACK)
shadowRadius(4f)
```

---

## 错误处理

### 常见错误及解决方案

#### 1. API密钥未配置

**错误信息**:
```
⚠️ 七牛云API密钥未配置！
```

**解决方法**:
- 检查 `local.properties` 是否存在
- 确认 `QINIU_API_KEY` 已填写
- 验证密钥格式是否正确（通常以 `sk-` 开头）

#### 2. API请求失败

**错误信息**:
```
AI请求失败: 401 Unauthorized
```

**解决方法**:
- 验证API密钥是否有效
- 检查密钥权限是否包含AI服务
- 确认账户余额充足

#### 3. 网络连接超时

**错误信息**:
```
AI请求失败: Read timed out
```

**解决方法**:
- 检查网络连接
- 增加超时时间：`AI_TIMEOUT=120000`
- 确认能访问 `https://api.qnaigc.com`

#### 4. Token超限

**错误信息**:
```
AI请求失败: Token limit exceeded
```

**解决方法**:
- 减少消息历史长度：`conversationHistory.takeLast(5)`
- 增加最大Token数：`AI_MAX_TOKENS=3000`
- 简化用户输入

### 错误处理最佳实践

```kotlin
try {
    val response = aiService.chat(messages)
    // 处理成功响应
} catch (e: AIException) {
    when {
        e.message?.contains("401") == true -> {
            // API密钥错误
            showError("API密钥验证失败，请检查配置")
        }
        e.message?.contains("timeout") == true -> {
            // 网络超时
            showError("网络请求超时，请稍后重试")
        }
        else -> {
            // 其他错误
            showError("AI服务暂时不可用: ${e.message}")
        }
    }
} catch (e: Exception) {
    // 未知错误
    log.error("AI请求异常", e)
    showError("系统错误，请联系技术支持")
}
```

---

## 最佳实践

### 1. 性能优化

#### 控制消息历史长度

```kotlin
// ❌ 错误：无限制保存历史
val allMessages = mutableListOf<ChatMessage>()
allMessages.addAll(newMessages)

// ✅ 正确：限制历史长度
val recentMessages = allMessages.takeLast(10)
val response = aiService.chat(recentMessages)
```

#### 使用流式响应提升体验

```kotlin
// ❌ 错误：等待完整响应（用户需等待5-10秒）
val response = aiService.chat(messages)
updateUI(response)

// ✅ 正确：流式响应（实时显示）
aiService.chatStream(messages).collect { chunk ->
    streamingText += chunk
    updateUI(streamingText) // 每收到一个chunk立即更新
}
```

#### 异步处理避免阻塞UI

```kotlin
// ❌ 错误：主线程调用（会冻结UI）
val response = aiService.chat(messages) // 阻塞

// ✅ 正确：协程异步调用
pagerScope.launch {
    val response = aiService.chat(messages)
    updateUI(response)
}
```

### 2. 安全性

#### API密钥保护

```kotlin
// ❌ 错误：硬编码敏感凭证
val credential = "hardcoded-sensitive-value"

// ✅ 正确：从配置读取
val apiKey = AIConfig.apiKey
```

#### 日志脱敏

```kotlin
// ❌ 错误：记录完整密钥
log.info("API Key: $apiKey")

// ✅ 正确：脱敏显示
log.info("API Key: ${AIConfig.maskApiKey(apiKey)}")
```

### 3. 用户体验

#### 提供加载指示

```kotlin
isLoading = true
refresh() // 显示加载动画

try {
    val response = aiService.chat(messages)
    // 处理响应
} finally {
    isLoading = false
    refresh() // 隐藏加载动画
}
```

#### 友好的错误提示

```kotlin
// ❌ 错误：技术性错误消息
errorMessage = "IOException: Connection reset by peer"

// ✅ 正确：用户友好消息
errorMessage = "网络连接中断，请检查网络后重试"
```

---

## 故障排查

### 调试模式

启用调试模式查看详细日志：

```properties
# local.properties
AI_DEBUG=true
```

日志输出示例：

```
[DEBUG] AI请求参数:
{
  "model": "qwen-max",
  "messages": [...],
  "temperature": 0.7,
  "max_tokens": 2000
}

[DEBUG] AI响应:
{
  "id": "chatcmpl-xxx",
  "choices": [...]
}
```

### 常见问题检查清单

- [ ] API密钥已配置且有效
- [ ] 网络连接正常，能访问 https://api.qnaigc.com
- [ ] 账户余额充足
- [ ] 防火墙/代理未阻止API请求
- [ ] 消息历史未超过Token限制
- [ ] 应用有网络权限（Android需在Manifest声明）

### 日志文件位置

- **Android**: `/sdcard/Android/data/{{PACKAGE_NAME}}/files/logs/`
- **iOS**: `Documents/logs/`
- **开发环境**: `build/logs/`

---

## API参考

### AIService类

#### 构造函数

```kotlin
AIService(
    apiKey: String,
    baseUrl: String = "https://api.qnaigc.com/v1",
    model: String = "qwen-max",
    temperature: Double = 0.7,
    maxTokens: Int = 2000
)
```

#### 方法

##### chat()

普通对话（一次性返回完整响应）

```kotlin
suspend fun chat(
    messages: List<ChatMessage>,
    systemPrompt: String? = null
): String
```

**参数**：
- `messages`: 消息历史列表
- `systemPrompt`: 系统提示词（可选）

**返回**：AI响应文本

**异常**：`AIException` 当AI请求失败时抛出

##### chatStream()

流式对话（SSE实时返回）

```kotlin
suspend fun chatStream(
    messages: List<ChatMessage>,
    systemPrompt: String? = null
): Flow<String>
```

**参数**：同 `chat()`

**返回**：`Flow<String>` 流式文本

**使用示例**：

```kotlin
aiService.chatStream(messages).collect { chunk ->
    println(chunk) // 实时打印每个chunk
}
```

##### close()

关闭HTTP客户端，释放资源

```kotlin
fun close()
```

### AIConfig对象

#### 属性

```kotlin
val apiKey: String        // 七牛云API密钥（必填）
val baseUrl: String       // API基础URL
val model: String         // AI模型名称
val temperature: Double   // 温度参数
val maxTokens: Int        // 最大Token数
val timeout: Long         // 请求超时时间
val debug: Boolean        // 调试模式
```

#### 方法

```kotlin
fun validate()                      // 验证配置完整性
fun maskApiKey(key: String): String // 脱敏显示API密钥
```

### ChatMessage数据类

```kotlin
data class ChatMessage(
    val role: String,      // "user" 或 "assistant"
    val content: String    // 消息内容
)
```

---

## 支持与反馈

### 获取帮助

- **七牛云文档**: https://developer.qiniu.com/ai
- **技术支持**: https://www.qiniu.com/support
- **KuiklyUI文档**: （待补充）

### 问题反馈

如遇到问题，请提供以下信息：

1. 错误日志（启用 `AI_DEBUG=true`）
2. 请求参数（脱敏后）
3. 环境信息（Android版本、设备型号）
4. 复现步骤

---

## 更新日志

### v1.0.0 ({{GENERATION_DATE}})

- ✅ 集成七牛云通义千问API
- ✅ 实现流式响应
- ✅ 完整的错误处理
- ✅ 多源配置支持
- ✅ KuiklyUI聊天界面

---

**Generated by Ingenio Platform**
**Powered by 七牛云 & KuiklyUI**
