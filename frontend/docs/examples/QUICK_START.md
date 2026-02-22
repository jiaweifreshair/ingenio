# AI能力快速开始指南

> **5分钟快速体验Ingenio平台的19种AI能力**

---

## 目录

- [Step 1: 选择AI能力](#step-1-选择ai能力)
- [Step 2: 生成项目](#step-2-生成项目)
- [Step 3: 本地运行](#step-3-本地运行)
- [Step 4: 测试AI功能](#step-4-测试ai功能)
- [推荐AI能力组合](#推荐ai能力组合)
- [常见问题](#常见问题)

---

## Step 1: 选择AI能力

### 访问Ingenio平台

1. 打开浏览器访问: `http://localhost:3000/wizard/new`
2. 填写基本信息:
   - 应用名称: `My AI App`
   - 包名: `com.example.myapp`
   - 目标平台: `Android + iOS`

### 选择AI能力类型

在向导页面中，点击"AI能力"标签，你将看到19种可选的AI能力：

**基础11种**（快速上手）：
- ✅ **CHATBOT** - 智能对话机器人（最热门）
- ✅ **IMAGE_RECOGNITION** - 图像识别
- ✅ **TEXT_GENERATION** - 文本生成
- ✅ **SENTIMENT_ANALYSIS** - 情感分析
- ✅ **RECOMMENDATION** - 推荐系统

**新增8种**（高级功能）：
- 🆕 **VIDEO_ANALYSIS** - 视频分析
- 🆕 **KNOWLEDGE_GRAPH** - 知识图谱
- 🆕 **OCR_DOCUMENT** - 智能文档识别
- 🆕 **HYPER_PERSONALIZATION** - 超个性化推荐

### 智能推荐功能

输入你的需求描述，AI会自动推荐合适的能力组合：

```
示例需求：
"我想构建一个智能客服系统，能够识别用户上传的图片，分析用户情绪，并给出个性化的回复。"

AI推荐：
1. CHATBOT - 智能对话机器人
2. IMAGE_RECOGNITION - 图像识别
3. SENTIMENT_ANALYSIS - 情感分析
4. HYPER_PERSONALIZATION - 超个性化推荐
```

---

## Step 2: 生成项目

### 点击"生成代码"按钮

系统将自动执行以下步骤：

1. **需求分析** - PlanAgent分析你的需求
2. **Schema设计** - 生成数据库表结构
3. **代码生成** - ExecuteAgent生成Kotlin Multiplatform代码
4. **AI集成** - 自动集成选择的AI能力
5. **打包下载** - 生成ZIP压缩包

### 生成内容包括

```
MyAIApp.zip
├── androidApp/                    # Android应用
├── iosApp/                        # iOS应用
├── core/                          # 共享业务逻辑
│   └── src/commonMain/kotlin/
│       ├── ai/                    # AI服务类
│       │   ├── ChatbotService.kt
│       │   ├── ImageRecognitionService.kt
│       │   └── SentimentAnalysisService.kt
│       ├── presentation/          # ViewModel
│       ├── data/                  # 数据模型
│       └── config/                # 配置文件
├── build.gradle.kts               # 构建配置
├── local.properties.template      # API密钥模板
├── .env.template                  # 环境变量模板
└── README.md                      # 项目说明
```

### 下载ZIP文件

生成完成后，点击"下载"按钮，保存到本地：

```bash
# 下载到Downloads目录
~/Downloads/MyAIApp.zip
```

---

## Step 3: 本地运行

### 解压ZIP文件

```bash
cd ~/Downloads
unzip MyAIApp.zip
cd MyAIApp
```

### 配置API密钥

**重要**：在运行前必须配置AI API密钥

```bash
# 复制模板文件
cp local.properties.template local.properties

# 编辑local.properties，填入你的API密钥
vim local.properties
```

**local.properties 示例**：

```properties
# 阿里云通义千问API密钥（必需）
QWEN_API_KEY=your_qwen_api_key_here

# Google Gemini API密钥（可选，用于实时流处理）
GEMINI_API_KEY=your_gemini_api_key_here

# Supabase配置（可选，用于数据存储）
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**如何获取API密钥？**

1. **阿里云通义千问**:
   - 访问: https://dashscope.console.aliyun.com/
   - 登录阿里云账号
   - 进入"API密钥"页面
   - 创建并复制密钥

2. **Google Gemini**:
   - 访问: https://ai.google.dev/
   - 登录Google账号
   - 创建新项目并启用Gemini API
   - 生成API密钥

### 安装依赖

```bash
# 确保已安装JDK 17+
java -version

# 安装Android SDK（Android开发必需）
# 下载Android Studio: https://developer.android.com/studio

# iOS开发需要macOS和Xcode
# 安装Xcode: https://apps.apple.com/app/xcode/id497799835

# 同步Gradle依赖
./gradlew clean build
```

### 运行Android应用

```bash
# 方法1: 使用Gradle命令
./gradlew :androidApp:installDebug

# 方法2: 使用Android Studio
1. 用Android Studio打开项目
2. 连接Android设备或启动模拟器
3. 点击"Run"按钮
```

### 运行iOS应用（macOS专用）

```bash
cd iosApp

# 安装CocoaPods依赖
pod install

# 打开Xcode项目
open iosApp.xcworkspace

# 在Xcode中选择模拟器并运行
```

---

## Step 4: 测试AI功能

### 测试CHATBOT（对话机器人）

**Android应用界面**：

1. 打开应用，进入"聊天"页面
2. 输入消息: `你好，请介绍一下自己`
3. AI回复: `你好！我是基于阿里云通义千问的AI助手...`

**代码位置**：

```
core/src/commonMain/kotlin/com/example/myapp/
├── ai/ChatbotService.kt           # AI服务逻辑
├── presentation/viewmodel/ChatViewModel.kt  # ViewModel
└── ui/ChatScreen.kt               # UI界面
```

**日志输出**：

```
2025-11-11 10:30:00 [INFO] ChatbotService - 发送消息: 你好，请介绍一下自己
2025-11-11 10:30:02 [INFO] ChatbotService - 收到回复: 你好！我是基于阿里云通义千问的AI助手...
2025-11-11 10:30:02 [INFO] ChatbotService - Token消耗: 输入12 + 输出45 = 57 tokens
2025-11-11 10:30:02 [INFO] ChatbotService - 成本: ¥0.000114 ≈ $0.000016
```

### 测试IMAGE_RECOGNITION（图像识别）

**Android应用界面**：

1. 进入"图像识别"页面
2. 点击"选择图片"或"拍照"
3. 选择一张图片（如：猫的照片）
4. AI识别结果:
   ```
   检测到的物体:
   - 猫 (置信度: 95.3%)
   - 沙发 (置信度: 87.1%)
   - 室内场景 (置信度: 92.5%)
   ```

**测试图片推荐**：

```
使用项目内置的测试图片:
androidApp/src/main/res/drawable/test_image_cat.jpg
androidApp/src/main/res/drawable/test_image_car.jpg
```

### 测试SENTIMENT_ANALYSIS（情感分析）

**Android应用界面**：

1. 进入"情感分析"页面
2. 输入文本: `这个产品真的太棒了，我非常喜欢！`
3. AI分析结果:
   ```
   情感倾向: 正面
   置信度: 98.5%
   情绪强度: 高
   关键词: ["棒", "喜欢"]
   ```

### 测试VIDEO_ANALYSIS（视频分析）

**Android应用界面**：

1. 进入"视频分析"页面
2. 输入视频URL或选择本地视频
3. 点击"开始分析"
4. AI分析结果:
   ```
   检测到的物体:
   - 人物 (置信度: 95%)
   - 汽车 (置信度: 89%)

   场景识别:
   - 城市街道 (00:00:05)

   视频摘要:
   视频展示了一个人在城市街道上跑步的场景，背景中有多辆汽车经过。
   ```

### 性能监控

查看AI功能的性能指标：

```bash
# 查看日志
adb logcat | grep "AIService"

# 示例输出
2025-11-11 10:30:00 [INFO] ChatbotService - 响应时间: 1.2s
2025-11-11 10:30:00 [INFO] ChatbotService - Token消耗: 57 tokens
2025-11-11 10:30:00 [INFO] ImageRecognitionService - 响应时间: 2.5s
2025-11-11 10:30:00 [INFO] SentimentAnalysisService - 响应时间: 0.8s
```

---

## 推荐AI能力组合

### 组合1: 智能客服系统

**适用场景**: 在线客服、电商咨询、技术支持

**推荐能力**:
- ✅ CHATBOT - 智能对话机器人
- ✅ SENTIMENT_ANALYSIS - 情感分析
- ✅ IMAGE_RECOGNITION - 图像识别（用于识别用户上传的图片）

**成本估算**: $5/月（1000活跃用户）

**开发时间**: 3天

**示例代码**:

```kotlin
class CustomerServiceBot(
    private val chatbot: ChatbotService,
    private val sentiment: SentimentAnalysisService,
    private val imageRecognition: ImageRecognitionService
) {
    suspend fun handleMessage(message: UserMessage): BotResponse {
        // 1. 分析用户情绪
        val sentimentResult = sentiment.analyze(message.text)

        // 2. 如果有图片，识别图片内容
        val imageInfo = message.imageUrl?.let {
            imageRecognition.recognize(it)
        }

        // 3. 根据情绪和图片内容生成回复
        val context = buildContext(sentimentResult, imageInfo)
        val response = chatbot.sendMessage(message.text, context)

        return BotResponse(
            text = response.output.text,
            sentiment = sentimentResult.sentiment,
            imageAnalysis = imageInfo
        )
    }
}
```

### 组合2: 内容平台

**适用场景**: 新闻应用、博客平台、社交媒体

**推荐能力**:
- ✅ TEXT_GENERATION - 文本生成（自动生成摘要）
- ✅ RECOMMENDATION - 推荐系统（个性化内容推荐）
- ✅ CONTENT_MODERATION - 内容审核（过滤违规内容）

**成本估算**: $15/月（5000活跃用户）

**开发时间**: 5天

### 组合3: 电商应用

**适用场景**: 在线商城、二手交易、跨境电商

**推荐能力**:
- ✅ IMAGE_RECOGNITION - 图像识别（商品识别）
- ✅ RECOMMENDATION - 推荐系统（商品推荐）
- ✅ TRANSLATION - 智能翻译（多语言支持）
- ✅ OCR_DOCUMENT - 智能文档识别（发票识别）

**成本估算**: $25/月（10000活跃用户）

**开发时间**: 7天

### 组合4: 智能监控系统

**适用场景**: 安防监控、智慧城市、物联网

**推荐能力**:
- ✅ VIDEO_ANALYSIS - 视频分析（异常检测）
- ✅ ANOMALY_DETECTION - 异常检测（数据分析）
- ✅ REALTIME_STREAM - 实时流处理（实时监控）

**成本估算**: $100/月（实时处理）

**开发时间**: 12天

### 组合5: 企业知识管理

**适用场景**: 企业内部知识库、文档管理、协作平台

**推荐能力**:
- ✅ QA_SYSTEM - 问答系统（知识库问答）
- ✅ RAG - 检索增强生成（文档搜索）
- ✅ KNOWLEDGE_GRAPH - 知识图谱（关系挖掘）
- ✅ SMART_SEARCH - 智能搜索（语义搜索）

**成本估算**: $40/月（1000员工）

**开发时间**: 10天

---

## 常见问题

### Q1: 生成的代码可以商用吗？

A: **可以**。Ingenio生成的代码采用MIT许可证，你可以：
- ✅ 商业使用
- ✅ 修改代码
- ✅ 分发代码
- ✅ 私有使用

但需要保留Ingenio的版权声明。

### Q2: 如何更新AI模型版本？

A: 修改配置文件中的模型参数：

```kotlin
// core/src/commonMain/kotlin/config/AIConfig.kt
object AIConfig {
    const val QWEN_MODEL = "qwen-max"  // 改为 "qwen-turbo" 或 "qwen-plus"
    const val GEMINI_MODEL = "gemini-2.0-flash-exp"  // 改为 "gemini-1.5-pro"
}
```

### Q3: 如何减少API调用成本？

A: 三种策略：

**策略1: 启用缓存**

```kotlin
@Cacheable(value = "ai-responses", key = "#userMessage")
suspend fun sendMessage(userMessage: String): ChatResponse
```

**策略2: 降低温度和Token数**

```kotlin
ChatParameters(
    temperature = 0.3,  // 从0.7降到0.3
    maxTokens = 300     // 从1500降到300
)
```

**策略3: 使用更便宜的模型**

```kotlin
// 从qwen-max切换到qwen-turbo
ChatbotService(model = "qwen-turbo")  // 成本降低60%
```

### Q4: 如何处理网络超时？

A: 增加超时时间和重试机制：

```kotlin
HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 120_000  // 120秒
        connectTimeoutMillis = 30_000   // 30秒
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        exponentialDelay()
    }
}
```

### Q5: 如何支持离线模式？

A: 实现本地缓存和降级策略：

```kotlin
class OfflineAIService(
    private val onlineService: AIService,
    private val localCache: LocalCache
) {
    suspend fun process(request: AIRequest): AIResponse {
        return try {
            onlineService.process(request).also {
                localCache.save(request, it)  // 保存到本地缓存
            }
        } catch (e: NetworkException) {
            // 网络失败时使用缓存
            localCache.get(request) ?: throw e
        }
    }
}
```

### Q6: 如何监控API使用情况？

A: 使用内置的监控服务：

```kotlin
@Service
class AIMonitorService {
    private val metrics = mutableMapOf<String, MetricData>()

    fun recordAPICall(apiName: String, duration: Long, cost: Double) {
        metrics.getOrPut(apiName) { MetricData() }.apply {
            callCount++
            totalDuration += duration
            totalCost += cost
        }
    }

    fun getReport(): Map<String, MetricData> {
        return metrics.toMap()
    }
}

data class MetricData(
    var callCount: Int = 0,
    var totalDuration: Long = 0,
    var totalCost: Double = 0.0
) {
    val avgDuration: Double get() = totalDuration.toDouble() / callCount
    val avgCost: Double get() = totalCost / callCount
}
```

### Q7: 如何集成自定义AI模型？

A: 实现AIService接口：

```kotlin
class CustomAIService(
    private val modelEndpoint: String,
    private val apiKey: String
) : AIService {
    override suspend fun process(request: AIRequest): AIResponse {
        // 调用你自己的AI模型API
        val response = httpClient.post(modelEndpoint) {
            header("Authorization", "Bearer $apiKey")
            setBody(request)
        }
        return response.body()
    }
}
```

### Q8: 如何测试AI功能？

A: 使用E2E测试框架：

```kotlin
@Test
fun `test chatbot responds correctly`() = runTest {
    // Given
    val chatbot = ChatbotService(apiKey = testApiKey)

    // When
    val response = chatbot.sendMessage("Hello")

    // Then
    assertTrue(response.output.text.isNotEmpty())
    assertTrue(response.usage.totalTokens > 0)
}
```

### Q9: 如何处理并发请求？

A: 使用协程和并发控制：

```kotlin
class ConcurrentAIService(
    private val aiService: AIService,
    private val maxConcurrent: Int = 10
) {
    private val semaphore = Semaphore(maxConcurrent)

    suspend fun process(request: AIRequest): AIResponse {
        semaphore.withPermit {
            return aiService.process(request)
        }
    }
}
```

### Q10: 如何获取技术支持？

A: 多种方式：

- 📧 **邮箱**: dev@ingenio.dev
- 💬 **GitHub Issues**: https://github.com/ingenio/ingenio/issues
- 📚 **文档**: [AI能力使用示例大全](./AI_CAPABILITY_USAGE_EXAMPLES.md)
- 🎥 **视频教程**: Coming soon...

---

## 下一步

### 深入学习

- 📖 阅读完整的[AI能力使用示例大全](./AI_CAPABILITY_USAGE_EXAMPLES.md)
- 🎯 查看各AI能力的详细代码示例
- 🔧 了解成本优化和性能调优技巧

### 扩展功能

- 🆕 尝试新增的8种高级AI能力
- 🔗 集成多个AI能力实现复杂场景
- 🎨 自定义UI界面和交互逻辑

### 参与社区

- ⭐ 在GitHub上给项目Star
- 🐛 报告Bug和提交Issue
- 🤝 贡献代码和文档

---

**Made with ❤️ by Ingenio Team**

Generated by Ingenio Platform
Date: 2025-11-11
