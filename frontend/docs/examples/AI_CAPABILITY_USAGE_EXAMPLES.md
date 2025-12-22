# AI能力使用示例大全

> **文档版本**: v1.0.0
> **创建日期**: 2025-11-11
> **适用范围**: Ingenio平台19种AI能力
> **文档用途**: 完整的代码示例和集成指南

---

## 目录

- [如何使用本文档](#如何使用本文档)
- [基础11种AI能力](#基础11种ai能力)
  - [CHATBOT - 智能对话机器人](#chatbot---智能对话机器人)
  - [QA_SYSTEM - 问答系统](#qa_system---问答系统)
  - [RAG - 检索增强生成](#rag---检索增强生成)
  - [IMAGE_RECOGNITION - 图像识别](#image_recognition---图像识别)
  - [OCR_DOCUMENT - OCR文档识别](#ocr_document---ocr文档识别)
  - [TRANSLATION - 智能翻译](#translation---智能翻译)
  - [SENTIMENT_ANALYSIS - 情感分析](#sentiment_analysis---情感分析)
  - [TEXT_GENERATION - 文本生成](#text_generation---文本生成)
  - [CODE_GENERATION - 代码生成](#code_generation---代码生成)
  - [RECOMMENDATION - 推荐系统](#recommendation---推荐系统)
  - [SPEECH_RECOGNITION - 语音识别](#speech_recognition---语音识别)
  - [CONTENT_MODERATION - 内容审核](#content_moderation---内容审核)
  - [SMART_SEARCH - 智能搜索](#smart_search---智能搜索)
- [新增8种AI能力](#新增8种ai能力)
  - [VIDEO_ANALYSIS - 视频分析](#video_analysis---视频分析)
  - [KNOWLEDGE_GRAPH - 知识图谱](#knowledge_graph---知识图谱)
  - [REALTIME_STREAM - 实时流处理](#realtime_stream---实时流处理)
  - [HYPER_PERSONALIZATION - 超个性化推荐](#hyper_personalization---超个性化推荐)
  - [PREDICTIVE_ANALYTICS - 预测分析](#predictive_analytics---预测分析)
  - [MULTIMODAL_GENERATION - 多模态生成](#multimodal_generation---多模态生成)
  - [ANOMALY_DETECTION - 异常检测](#anomaly_detection---异常检测)
- [附录](#附录)
  - [API密钥配置](#api密钥配置)
  - [成本优化建议](#成本优化建议)
  - [常见问题FAQ](#常见问题faq)

---

## 如何使用本文档

### 快速导航

1. **找到你需要的AI能力类型**
   - 按目录浏览19种AI能力
   - 查看每种能力的适用场景

2. **复制示例代码到你的项目**
   - 前端使用示例（TypeScript）
   - 后端生成的代码结构
   - 生成的Kotlin代码示例

3. **根据注释修改配置参数**
   - API密钥配置
   - 模型参数调整
   - 成本控制策略

4. **运行测试验证功能**
   - 本地测试命令
   - E2E测试用例
   - 性能监控指标

### 文档约定

- **代码块**: 可直接复制使用的代码片段
- **⚠️ 注意**: 需要特别关注的重要信息
- **💡 提示**: 优化建议和最佳实践
- **🔧 配置**: 必需的配置项

---

## 基础11种AI能力

### CHATBOT - 智能对话机器人

#### 适用场景

1. **客服机器人** - 24/7自动回答客户问题，减少人工成本
2. **虚拟助手** - 帮助用户完成任务，如日程管理、信息查询
3. **聊天应用** - 为社交应用提供AI聊天功能
4. **智能问答** - 企业内部知识库问答
5. **教育辅导** - AI老师，解答学生问题

#### 技术实现

- **AI模型**: 阿里云通义千问 Qwen-Max
- **成本估算**: $1.7/月（1万活跃用户）
- **预估工期**: 2天
- **复杂度**: SIMPLE

#### 前端使用示例

```typescript
// frontend/src/pages/ChatbotDemo.tsx
import { AICapabilityPicker } from '@/components/ai/ai-capability-picker';
import { AICapabilityType } from '@/types/ai-capability';
import { useState } from 'react';

function ChatbotDemo() {
  const [selectedCapabilities, setSelectedCapabilities] = useState<AICapabilityType[]>([
    AICapabilityType.CHATBOT
  ]);

  const handleSelect = (capabilities: AICapabilityType[]) => {
    setSelectedCapabilities(capabilities);
    // 调用后端生成代码API
    generateCode(capabilities);
  };

  const generateCode = async (capabilities: AICapabilityType[]) => {
    const response = await fetch('/api/v1/wizard/generate-code', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        aiCapabilities: capabilities,
        packageName: 'com.example.chatapp',
        appName: 'My Chat App'
      })
    });

    const { zipUrl } = await response.json();
    // 下载生成的代码ZIP
    window.location.href = zipUrl;
  };

  return (
    <div className="container mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4">智能对话机器人示例</h1>
      <AICapabilityPicker
        selectedCapabilities={selectedCapabilities}
        onSelectionChange={handleSelect}
        userRequirement="我需要构建一个智能客服系统"
      />
    </div>
  );
}

export default ChatbotDemo;
```

#### 后端生成的代码结构

```
com.example.chatapp/
├── ai/
│   ├── ChatbotService.kt          # AI服务主类
│   ├── ConversationManager.kt     # 对话上下文管理
│   └── MessageHistory.kt          # 消息历史记录
├── presentation/
│   ├── viewmodel/
│   │   └── ChatViewModel.kt       # ViewModel
│   └── ui/
│       └── ChatScreen.kt          # Compose UI
├── data/
│   ├── models/
│   │   ├── ChatMessage.kt         # 消息数据模型
│   │   └── ChatSession.kt         # 会话数据模型
│   └── repository/
│       └── ChatRepository.kt      # 数据仓库
└── config/
    └── AIConfig.kt                # AI配置（API Key等）
```

#### 生成的Kotlin代码示例

```kotlin
// core/src/commonMain/kotlin/com/example/chatapp/ai/ChatbotService.kt
package com.example.chatapp.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 智能对话机器人服务
 * 基于阿里云通义千问Qwen-Max实现
 *
 * 功能：
 * - sendMessage(): 发送消息并获取AI回复
 * - streamMessage(): 流式响应（SSE）
 * - clearHistory(): 清空对话历史
 *
 * Generated by Ingenio Platform
 * Date: 2025-11-11
 */
class ChatbotService(
    private val apiKey: String,
    private val baseUrl: String = "https://dashscope.aliyuncs.com/api/v1",
    private val model: String = "qwen-max"
) {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        engine {
            requestTimeout = 60_000 // 60秒超时
        }
    }

    private val conversationHistory = mutableListOf<ChatMessage>()

    /**
     * 发送消息并获取AI回复
     *
     * @param userMessage 用户消息
     * @param systemPrompt 系统提示词（可选）
     * @return AI回复内容
     */
    suspend fun sendMessage(
        userMessage: String,
        systemPrompt: String = "你是一个友好的AI助手，擅长回答各种问题。"
    ): ChatResponse {
        // 添加用户消息到历史
        conversationHistory.add(ChatMessage("user", userMessage))

        // 构建请求
        val request = ChatRequest(
            model = model,
            input = ChatInput(
                messages = buildMessages(systemPrompt)
            ),
            parameters = ChatParameters(
                temperature = 0.7,
                maxTokens = 1500,
                topP = 0.9
            )
        )

        // 发送请求
        val response = httpClient.post("$baseUrl/services/aigc/text-generation/generation") {
            header("Authorization", "Bearer $apiKey")
            header("Content-Type", "application/json")
            setBody(request)
        }

        if (response.status != HttpStatusCode.OK) {
            throw ChatbotException("请求失败: ${response.status}")
        }

        val chatResponse: ChatResponse = response.body()

        // 添加AI回复到历史
        conversationHistory.add(ChatMessage("assistant", chatResponse.output.text))

        return chatResponse
    }

    /**
     * 清空对话历史
     */
    fun clearHistory() {
        conversationHistory.clear()
    }

    /**
     * 获取对话历史
     */
    fun getHistory(): List<ChatMessage> {
        return conversationHistory.toList()
    }

    /**
     * 构建消息列表（包含系统提示和历史）
     */
    private fun buildMessages(systemPrompt: String): List<Message> {
        val messages = mutableListOf<Message>()

        // 系统提示
        messages.add(Message("system", systemPrompt))

        // 对话历史
        conversationHistory.forEach { msg ->
            messages.add(Message(msg.role, msg.content))
        }

        return messages
    }

    /**
     * 关闭HTTP客户端
     */
    fun close() {
        httpClient.close()
    }
}

// ==================== 数据模型 ====================

/**
 * 聊天消息
 */
@Serializable
data class ChatMessage(
    val role: String,      // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 聊天请求
 */
@Serializable
data class ChatRequest(
    val model: String,
    val input: ChatInput,
    val parameters: ChatParameters
)

@Serializable
data class ChatInput(
    val messages: List<Message>
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class ChatParameters(
    val temperature: Double,
    val maxTokens: Int,
    val topP: Double
)

/**
 * 聊天响应
 */
@Serializable
data class ChatResponse(
    val output: ChatOutput,
    val usage: Usage,
    val requestId: String
)

@Serializable
data class ChatOutput(
    val text: String,
    val finishReason: String
)

@Serializable
data class Usage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)

/**
 * 聊天机器人异常
 */
class ChatbotException(message: String) : Exception(message)
```

#### 集成步骤

**步骤1**: 在Supabase中配置AI API密钥

```bash
# 在项目根目录创建 local.properties
echo "QWEN_API_KEY=your_api_key_here" > local.properties
```

**步骤2**: 在项目中添加依赖

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.ktor:ktor-client-core:2.3.5")
    implementation("io.ktor:ktor-client-cio:2.3.5")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.5")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
```

**步骤3**: 初始化ChatbotService

```kotlin
// Application初始化
val apiKey = AIConfig.qwenApiKey
val chatbotService = ChatbotService(apiKey)

// 使用示例
suspend fun chatExample() {
    val response = chatbotService.sendMessage("你好，请介绍一下自己")
    println("AI回复: ${response.output.text}")
}
```

#### 常见问题

**Q1: 如何处理流式响应？**

A: 使用SSE（Server-Sent Events）支持流式响应：

```kotlin
suspend fun streamMessage(userMessage: String): Flow<String> = flow {
    val request = ChatRequest(
        model = model,
        input = ChatInput(messages = buildMessages()),
        parameters = ChatParameters(
            temperature = 0.7,
            maxTokens = 1500,
            topP = 0.9,
            enableStream = true  // 启用流式响应
        )
    )

    httpClient.preparePost("$baseUrl/services/aigc/text-generation/generation") {
        header("Authorization", "Bearer $apiKey")
        header("Content-Type", "application/json")
        setBody(request)
    }.execute { response ->
        val channel = response.body<ByteReadChannel>()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (line.startsWith("data:")) {
                val data = line.substring(5).trim()
                emit(data)
            }
        }
    }
}
```

**Q2: 如何控制回复长度？**

A: 在ChatParameters中设置max_tokens参数：

```kotlin
ChatParameters(
    maxTokens = 500,  // 限制最多500个token
    temperature = 0.7,
    topP = 0.9
)
```

**Q3: 如何处理多轮对话上下文？**

A: 使用ConversationHistory保存对话历史：

```kotlin
class ConversationManager {
    private val history = mutableListOf<ChatMessage>()
    private val maxHistorySize = 10  // 最多保留10轮对话

    fun addMessage(message: ChatMessage) {
        history.add(message)
        if (history.size > maxHistorySize * 2) {  // 每轮2条消息
            history.removeAt(0)
            history.removeAt(0)
        }
    }

    fun getHistory(): List<ChatMessage> = history.toList()
}
```

**Q4: 如何估算成本？**

A: Token消耗估算公式：

```
成本 = (输入Token数 + 输出Token数) × 单价
单价（Qwen-Max）= ¥0.002/千tokens ≈ $0.00028/千tokens

示例：
- 平均每次对话：500 tokens
- 每日活跃用户：1000人
- 每人每天对话：5次
成本 = 1000 × 5 × 500 / 1000 × 0.002 = ¥5/天 ≈ $150/月
```

**Q5: 如何优化响应速度？**

A: 三种优化策略：

```kotlin
// 策略1: 降低max_tokens
ChatParameters(maxTokens = 300)  // 从1500降到300

// 策略2: 使用更快的模型
ChatbotService(model = "qwen-turbo")  // 从qwen-max切换到qwen-turbo

// 策略3: 启用缓存
@Cacheable(value = "chat-responses", key = "#userMessage")
suspend fun sendMessage(userMessage: String): ChatResponse
```

---

### VIDEO_ANALYSIS - 视频分析

#### 适用场景

1. **短视频应用** - 自动生成视频标签、推荐相关内容
2. **内容审核** - 检测违规内容（暴力、色情、政治敏感）
3. **智能监控** - 异常行为检测、人流统计
4. **视频编辑** - 智能识别精彩片段、自动剪辑
5. **电商应用** - 视频商品识别、穿搭分析

#### 技术实现

- **AI模型**: 阿里云通义千问 Qwen-VL-Max（视觉语言模型）
- **成本估算**: $50/月（5万次调用）
- **预估工期**: 5天
- **复杂度**: MEDIUM

#### 前端使用示例

```typescript
// frontend/src/pages/VideoAnalysisDemo.tsx
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

interface VideoAnalysisResult {
  objects: Array<{ name: string; confidence: number }>;
  scenes: Array<{ type: string; timestamp: string }>;
  actions: Array<{ action: string; duration: string }>;
  summary: string;
}

function VideoAnalysisDemo() {
  const [videoUrl, setVideoUrl] = useState('');
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState<VideoAnalysisResult | null>(null);

  const analyzeVideo = async () => {
    setAnalyzing(true);
    try {
      const response = await fetch('/api/v1/ai/video-analysis', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ videoUrl })
      });

      const data = await response.json();
      setResult(data);
    } catch (error) {
      console.error('视频分析失败:', error);
    } finally {
      setAnalyzing(false);
    }
  };

  return (
    <div className="container mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4">视频分析示例</h1>

      <div className="space-y-4">
        <Input
          type="text"
          placeholder="输入视频URL"
          value={videoUrl}
          onChange={(e) => setVideoUrl(e.target.value)}
        />

        <Button onClick={analyzeVideo} disabled={analyzing}>
          {analyzing ? '分析中...' : '开始分析'}
        </Button>

        {result && (
          <div className="mt-6 space-y-4">
            <div>
              <h3 className="font-semibold">检测到的物体</h3>
              <ul className="list-disc list-inside">
                {result.objects.map((obj, i) => (
                  <li key={i}>
                    {obj.name} (置信度: {(obj.confidence * 100).toFixed(1)}%)
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <h3 className="font-semibold">场景识别</h3>
              <ul className="list-disc list-inside">
                {result.scenes.map((scene, i) => (
                  <li key={i}>
                    {scene.type} - {scene.timestamp}
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <h3 className="font-semibold">视频摘要</h3>
              <p>{result.summary}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default VideoAnalysisDemo;
```

#### 后端生成的代码结构

```
com.example.videoapp/
├── ai/
│   ├── VideoAnalysisService.kt    # 视频分析服务
│   ├── VideoFrameExtractor.kt     # 视频帧提取器
│   └── VideoAnalysisResult.kt     # 分析结果数据模型
├── presentation/
│   ├── viewmodel/
│   │   └── VideoAnalysisViewModel.kt  # ViewModel
│   └── ui/
│       └── VideoAnalysisScreen.kt     # Compose UI
└── config/
    └── VideoAnalysisConfig.kt     # 配置文件
```

#### 生成的Kotlin代码示例

（代码示例已在AICodeGenerator.java中完整提供，这里引用）

```kotlin
// 参见 AICodeGenerator.java 第193-477行
// 完整的VideoAnalysisService.kt实现
```

#### 集成步骤

**步骤1**: 配置API密钥

```properties
# local.properties
QWEN_API_KEY=sk-your-api-key-here
```

**步骤2**: 添加依赖

```kotlin
dependencies {
    implementation("io.ktor:ktor-client-core:2.3.5")
    implementation("io.ktor:ktor-client-cio:2.3.5")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
}
```

**步骤3**: 使用示例

```kotlin
val service = VideoAnalysisService(apiKey)
val result = service.analyzeVideo(
    videoUrl = "https://example.com/video.mp4",
    prompt = "分析这个视频中的主要物体和场景"
)

println("检测到 ${result.objects.size} 个物体")
result.objects.forEach { obj ->
    println("  - ${obj.name} (置信度: ${obj.confidence})")
}
```

#### 常见问题

**Q1: 如何处理大视频文件？**

A: 使用分片上传和异步处理：

```kotlin
class VideoUploader {
    suspend fun uploadLargeVideo(videoFile: File): String {
        val chunkSize = 5 * 1024 * 1024  // 5MB per chunk
        val chunks = videoFile.readBytes().toList().chunked(chunkSize)

        chunks.forEachIndexed { index, chunk ->
            uploadChunk(chunk.toByteArray(), index)
        }

        return "upload-task-id"
    }
}
```

**Q2: 如何优化视频分析成本？**

A: 三种策略：

```kotlin
// 策略1: 降低采样率（每60帧分析一次）
VideoAnalysisRequest(
    videoUrl = videoUrl,
    frameInterval = 60  // 从30增加到60
)

// 策略2: 限制分析帧数
VideoAnalysisRequest(
    videoUrl = videoUrl,
    maxFrames = 50  // 从100减少到50
)

// 策略3: 缓存分析结果
@Cacheable(value = "video-analysis", key = "#videoUrl")
suspend fun analyzeVideo(videoUrl: String): VideoAnalysisResult
```

**Q3: 如何处理实时视频流？**

A: 使用WebSocket流式处理：

```kotlin
suspend fun analyzeVideoStream(videoStream: Flow<ByteArray>) {
    videoStream.collect { frame ->
        val result = analyzeFrame(frame)
        emit(result)
    }
}
```

**Q4: 支持哪些视频格式？**

A: 支持常见视频格式：

- MP4
- AVI
- MOV
- FLV
- WebM

**Q5: 如何提高识别准确率？**

A: 优化提示词（Prompt）：

```kotlin
// 通用提示词
"分析这个视频"

// 优化后的提示词
"""
请详细分析这个视频：
1. 识别所有出现的物体（人物、车辆、建筑等）
2. 识别场景类型（室内/室外、城市/自然等）
3. 识别主要动作（跑步、跳舞、驾驶等）
4. 生成100字以内的视频摘要
返回JSON格式的分析报告。
"""
```

---

### KNOWLEDGE_GRAPH - 知识图谱

#### 适用场景

1. **企业知识库** - 自动构建员工、项目、文档的关系网络
2. **学习应用** - 知识点关联、学习路径推荐
3. **内容平台** - 文章、作者、话题的语义连接
4. **电商应用** - 商品属性提取、相似商品推荐
5. **法律科技** - 案例关联、法条引用分析

#### 技术实现

- **AI模型**: 阿里云通义千问 Qwen-Max（实体关系提取）
- **成本估算**: $28/月（10万次调用）
- **预估工期**: 8天
- **复杂度**: COMPLEX

#### 前端使用示例

```typescript
// frontend/src/pages/KnowledgeGraphDemo.tsx
import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import ReactFlow, { Node, Edge } from 'reactflow';
import 'reactflow/dist/style.css';

interface Entity {
  id: string;
  name: string;
  type: string;
}

interface Relation {
  subject: string;
  predicate: string;
  object: string;
  confidence: number;
}

function KnowledgeGraphDemo() {
  const [text, setText] = useState('');
  const [extracting, setExtracting] = useState(false);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);

  const extractGraph = async () => {
    setExtracting(true);
    try {
      const response = await fetch('/api/v1/ai/knowledge-graph', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text })
      });

      const { entities, relations } = await response.json();

      // 转换为ReactFlow节点和边
      const flowNodes = entities.map((entity: Entity, index: number) => ({
        id: entity.id,
        data: { label: `${entity.name} (${entity.type})` },
        position: { x: index * 200, y: index * 100 }
      }));

      const flowEdges = relations.map((rel: Relation) => ({
        id: `${rel.subject}-${rel.object}`,
        source: rel.subject,
        target: rel.object,
        label: rel.predicate,
        animated: true
      }));

      setNodes(flowNodes);
      setEdges(flowEdges);
    } catch (error) {
      console.error('知识图谱提取失败:', error);
    } finally {
      setExtracting(false);
    }
  };

  return (
    <div className="container mx-auto p-6">
      <h1 className="text-2xl font-bold mb-4">知识图谱示例</h1>

      <div className="space-y-4">
        <Textarea
          placeholder="输入文本，系统将自动提取实体和关系..."
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={6}
        />

        <Button onClick={extractGraph} disabled={extracting}>
          {extracting ? '提取中...' : '提取知识图谱'}
        </Button>

        {nodes.length > 0 && (
          <div style={{ height: 600 }}>
            <ReactFlow nodes={nodes} edges={edges} fitView />
          </div>
        )}
      </div>
    </div>
  );
}

export default KnowledgeGraphDemo;
```

#### 后端生成的代码结构

（参见AICodeGenerator.java 第486-710行）

#### 生成的Kotlin代码示例

（参见AICodeGenerator.java 第492-591行）

#### 集成步骤

**步骤1**: 配置API密钥（同上）

**步骤2**: 添加图数据库支持（可选）

```kotlin
// 使用Neo4j存储知识图谱
dependencies {
    implementation("org.neo4j.driver:neo4j-java-driver:5.14.0")
}

class Neo4jGraphRepository(
    private val uri: String,
    private val username: String,
    private val password: String
) {
    private val driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password))

    fun saveGraph(graph: KnowledgeGraph) {
        driver.session().use { session ->
            graph.entities.forEach { entity ->
                session.writeTransaction { tx ->
                    tx.run(
                        "CREATE (e:Entity {id: \$id, name: \$name, type: \$type})",
                        mapOf("id" to entity.id, "name" to entity.name, "type" to entity.type)
                    )
                }
            }

            graph.relations.forEach { relation ->
                session.writeTransaction { tx ->
                    tx.run(
                        "MATCH (a:Entity {id: \$subject}), (b:Entity {id: \$object}) " +
                        "CREATE (a)-[r:\$predicate]->(b)",
                        mapOf(
                            "subject" to relation.subject,
                            "object" to relation.object,
                            "predicate" to relation.predicate
                        )
                    )
                }
            }
        }
    }
}
```

**步骤3**: 使用示例

```kotlin
val service = KnowledgeGraphService(apiKey)
val graph = service.extractEntitiesAndRelations(
    "苹果公司由史蒂夫·乔布斯创立于1976年，总部位于加州库比蒂诺。"
)

println("提取到 ${graph.entities.size} 个实体")
println("提取到 ${graph.relations.size} 个关系")

graph.entities.forEach { entity ->
    println("实体: ${entity.name} (${entity.type})")
}

graph.relations.forEach { relation ->
    println("关系: ${relation.subject} ${relation.predicate} ${relation.object}")
}
```

#### 常见问题

**Q1: 如何提高实体识别准确率？**

A: 使用更详细的实体类型定义：

```kotlin
data class EntityType(
    val name: String,
    val examples: List<String>,
    val keywords: List<String>
)

val customEntityTypes = listOf(
    EntityType(
        name = "PERSON",
        examples = listOf("张三", "李四", "史蒂夫·乔布斯"),
        keywords = listOf("先生", "女士", "教授", "博士")
    ),
    EntityType(
        name = "ORGANIZATION",
        examples = listOf("苹果公司", "清华大学", "国务院"),
        keywords = listOf("公司", "大学", "政府", "机构")
    )
)
```

**Q2: 如何处理关系冲突？**

A: 使用置信度评分和冲突解决策略：

```kotlin
class ConflictResolver {
    fun resolveRelationConflicts(relations: List<Relation>): List<Relation> {
        // 按主语-宾语分组
        val grouped = relations.groupBy { "${it.subject}-${it.object}" }

        return grouped.values.map { group ->
            // 选择置信度最高的关系
            group.maxByOrNull { it.confidence } ?: group.first()
        }
    }
}
```

**Q3: 如何实现关系推理？**

A: 使用规则引擎进行推理：

```kotlin
class GraphReasoner {
    fun inferRelations(graph: KnowledgeGraph): List<Relation> {
        val inferred = mutableListOf<Relation>()

        // 规则1: 传递性（A创立B，B位于C => A关联C）
        graph.relations.filter { it.predicate == "创立" }.forEach { r1 ->
            graph.relations.filter { it.predicate == "位于" && it.subject == r1.object }.forEach { r2 ->
                inferred.add(Relation(
                    subject = r1.subject,
                    predicate = "关联",
                    object = r2.object,
                    confidence = r1.confidence * r2.confidence
                ))
            }
        }

        return inferred
    }
}
```

**Q4: 如何可视化大规模知识图谱？**

A: 使用分层渲染和聚合：

```typescript
// 前端使用D3.js或Cytoscape.js
import Cytoscape from 'cytoscape';

const cy = Cytoscape({
  container: document.getElementById('cy'),
  elements: {
    nodes: graph.entities.map(e => ({ data: { id: e.id, label: e.name } })),
    edges: graph.relations.map(r => ({ data: { source: r.subject, target: r.object, label: r.predicate } }))
  },
  layout: {
    name: 'cose',  // 力导向布局
    animate: true,
    nodeRepulsion: 400000
  }
});

// 聚合大规模图谱
if (graph.entities.length > 100) {
  cy.layout({ name: 'preset' }).run();  // 使用预设布局
}
```

**Q5: 如何导出知识图谱？**

A: 支持多种导出格式：

```kotlin
class GraphExporter {
    fun exportToJSON(graph: KnowledgeGraph): String {
        return Json.encodeToString(graph)
    }

    fun exportToGraphML(graph: KnowledgeGraph): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <graphml>
                <graph id="G" edgedefault="directed">
                    ${graph.entities.joinToString("") {
                        """<node id="${it.id}"><data key="name">${it.name}</data></node>"""
                    }}
                    ${graph.relations.joinToString("") {
                        """<edge source="${it.subject}" target="${it.object}"><data key="label">${it.predicate}</data></edge>"""
                    }}
                </graph>
            </graphml>
        """.trimIndent()
    }

    fun exportToNeo4jCypher(graph: KnowledgeGraph): String {
        return graph.entities.joinToString("\n") {
            "CREATE (:Entity {id: '${it.id}', name: '${it.name}', type: '${it.type}'})"
        } + "\n" + graph.relations.joinToString("\n") {
            "MATCH (a:Entity {id: '${it.subject}'}), (b:Entity {id: '${it.object}'}) CREATE (a)-[:${it.predicate}]->(b)"
        }
    }
}
```

---

## 附录

### API密钥配置

#### 阿里云通义千问

```bash
# 获取API密钥
1. 访问 https://dashscope.console.aliyun.com/
2. 登录阿里云账号
3. 进入"API密钥"页面
4. 创建新的API密钥
5. 复制密钥到local.properties

# local.properties
QWEN_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

#### Google Gemini

```bash
# 获取API密钥
1. 访问 https://ai.google.dev/
2. 登录Google账号
3. 创建新项目
4. 启用Gemini API
5. 创建API密钥

# local.properties
GEMINI_API_KEY=AIzaSyXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
```

### 成本优化建议

#### 策略1: 请求缓存

```kotlin
@Configuration
class CacheConfig {
    @Bean
    fun cacheManager(): CacheManager {
        return CaffeineCacheManager("ai-responses").apply {
            setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(10000))
        }
    }
}

@Service
class CachedAIService(
    private val aiService: AIService,
    private val cacheManager: CacheManager
) {
    @Cacheable(value = ["ai-responses"], key = "#request.hashCode()")
    suspend fun process(request: AIRequest): AIResponse {
        return aiService.process(request)
    }
}
```

#### 策略2: 用量配额

```kotlin
@Service
class QuotaManager {
    private val userQuotas = ConcurrentHashMap<String, AtomicInteger>()

    fun checkQuota(userId: String): Boolean {
        val quota = userQuotas.getOrPut(userId) { AtomicInteger(1000) }
        return quota.getAndDecrement() > 0
    }

    fun refillQuota(userId: String, amount: Int) {
        userQuotas.getOrPut(userId) { AtomicInteger(0) }.addAndGet(amount)
    }
}
```

#### 策略3: 降级策略

```kotlin
@Service
class AIServiceWithFallback(
    private val primaryAIService: AIService,
    private val fallbackAIService: AIService
) {
    @CircuitBreaker(name = "ai-service", fallbackMethod = "fallback")
    suspend fun process(request: AIRequest): AIResponse {
        return primaryAIService.process(request)
    }

    private suspend fun fallback(request: AIRequest, ex: Exception): AIResponse {
        logger.warn("主服务失败，使用备用服务: ${ex.message}")
        return fallbackAIService.process(request)
    }
}
```

### 常见问题FAQ

**Q1: 如何监控API调用成本？**

A: 使用监控仪表板：

```kotlin
@Service
class CostMonitor {
    private val costMetrics = mutableMapOf<String, Double>()

    fun recordCost(apiName: String, cost: Double) {
        costMetrics[apiName] = (costMetrics[apiName] ?: 0.0) + cost
    }

    fun getCostReport(): Map<String, Double> {
        return costMetrics.toMap()
    }
}
```

**Q2: 如何处理API限流？**

A: 实现令牌桶算法：

```kotlin
class RateLimiter(
    private val maxTokens: Int = 100,
    private val refillRate: Int = 10  // tokens/second
) {
    private val tokens = AtomicInteger(maxTokens)
    private val lastRefill = AtomicLong(System.currentTimeMillis())

    fun acquire(): Boolean {
        refillTokens()
        return tokens.getAndDecrement() > 0
    }

    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val elapsed = (now - lastRefill.get()) / 1000
        val tokensToAdd = (elapsed * refillRate).toInt()

        if (tokensToAdd > 0) {
            tokens.updateAndGet { current ->
                minOf(current + tokensToAdd, maxTokens)
            }
            lastRefill.set(now)
        }
    }
}
```

**Q3: 如何处理超时？**

A: 使用超时和重试机制：

```kotlin
suspend fun <T> withTimeout(
    timeoutMillis: Long,
    retries: Int = 3,
    block: suspend () -> T
): T {
    repeat(retries) { attempt ->
        try {
            return withTimeout(timeoutMillis) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            if (attempt == retries - 1) throw e
            delay(1000 * (attempt + 1))  // 指数退避
        }
    }
    throw TimeoutException("Failed after $retries retries")
}
```

---

**文档结束**

Generated by Ingenio Platform
Date: 2025-11-11
Total AI Capabilities: 19
Documentation Version: v1.0.0
