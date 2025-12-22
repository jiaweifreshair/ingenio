# AI能力类型快速参考指南

> **最后更新**: 2025-11-11
> **适用版本**: Ingenio v1.0.0+
> **文档类型**: 快速参考

---

## 19种AI能力类型总览

### 基础11种（已有）

| # | 标识符 | 中文名称 | 复杂度 | 应用场景 |
|---|--------|---------|--------|---------|
| 1 | CHATBOT | 聊天机器人 | SIMPLE | 智能客服、对话系统 |
| 2 | QA_SYSTEM | 问答系统 | SIMPLE | 知识库问答、FAQ |
| 3 | RAG | 检索增强生成 | MEDIUM | 文档问答、企业知识库 |
| 4 | SUMMARIZATION | 文本摘要 | SIMPLE | 新闻摘要、会议纪要 |
| 5 | IMAGE_RECOGNITION | 图片识别 | SIMPLE | 物体识别、场景分析 |
| 6 | SPEECH_TO_TEXT | 语音识别 | SIMPLE | 语音转文字、语音输入 |
| 7 | TEXT_TO_SPEECH | 语音合成 | SIMPLE | 文字转语音、有声阅读 |
| 8 | CONTENT_GENERATION | 内容生成 | MEDIUM | 文案生成、创意写作 |
| 9 | SENTIMENT_ANALYSIS | 情感分析 | SIMPLE | 舆情监控、用户反馈分析 |
| 10 | TRANSLATION | 智能翻译 | SIMPLE | 多语言翻译、国际化 |
| 11 | CODE_COMPLETION | 代码补全 | MEDIUM | 代码助手、智能提示 |

### 新增8种（2025-11-11）

| # | 标识符 | 中文名称 | 复杂度 | 首选模型 | 月成本 |
|---|--------|---------|--------|---------|--------|
| 12 | VIDEO_ANALYSIS | 视频分析 | MEDIUM | Qwen-VL-Max | $50 |
| 13 | KNOWLEDGE_GRAPH | 知识图谱 | COMPLEX | Qwen-Max | $28 |
| 14 | OCR_DOCUMENT | 智能文档识别 | SIMPLE | Qwen-VL-Max | $11 |
| 15 | REALTIME_STREAM | 实时流分析 | COMPLEX | Gemini 2.0 | $42 |
| 16 | HYPER_PERSONALIZATION | 超个性化引擎 | MEDIUM | Qwen-Max | $84 |
| 17 | PREDICTIVE_ANALYTICS | 预测分析 | MEDIUM | Qwen-Max | $17 |
| 18 | MULTIMODAL_GENERATION | 多模态生成 | COMPLEX | 通义万相 | $168 |
| 19 | ANOMALY_DETECTION | 异常检测 | MEDIUM | Qwen-Max | $14 |

---

## 按复杂度分类

### SIMPLE（简单）- 4种

适合快速开发，2-3天完成

| 标识符 | 中文名称 | 典型场景 |
|--------|---------|---------|
| OCR_DOCUMENT | 智能文档识别 | 发票识别、身份证识别 |
| CHATBOT | 聊天机器人 | 客服对话、FAQ |
| SUMMARIZATION | 文本摘要 | 新闻摘要、会议纪要 |
| IMAGE_RECOGNITION | 图片识别 | 物体识别、场景分析 |

### MEDIUM（中等）- 10种

需要多个AI能力组合，5-7天完成

| 标识符 | 中文名称 | 典型场景 |
|--------|---------|---------|
| VIDEO_ANALYSIS | 视频分析 | 短视频打标签、内容审核 |
| HYPER_PERSONALIZATION | 超个性化引擎 | 精准推荐、个性化内容 |
| PREDICTIVE_ANALYTICS | 预测分析 | 用户流失预测、销售预测 |
| ANOMALY_DETECTION | 异常检测 | 欺诈检测、异常行为识别 |
| RAG | 检索增强生成 | 企业知识库、文档问答 |
| CONTENT_GENERATION | 内容生成 | 文案生成、创意写作 |
| CODE_COMPLETION | 代码补全 | 代码助手、智能提示 |

### COMPLEX（复杂）- 5种

需要自定义Agent和Memory，10-14天完成

| 标识符 | 中文名称 | 典型场景 |
|--------|---------|---------|
| KNOWLEDGE_GRAPH | 知识图谱 | 实体关系提取、语义搜索 |
| REALTIME_STREAM | 实时流分析 | 实时字幕、直播互动 |
| MULTIMODAL_GENERATION | 多模态生成 | 文生图、图生视频 |

---

## 按应用领域分类

### 内容创作类（6种）

| 标识符 | 适用场景 |
|--------|---------|
| CONTENT_GENERATION | 博客文章、社交媒体文案、广告创意 |
| MULTIMODAL_GENERATION | AI头像生成、海报设计、商品图 |
| VIDEO_ANALYSIS | 视频标签生成、精彩片段提取 |
| SUMMARIZATION | 长文摘要、视频总结 |
| TRANSLATION | 多语言内容翻译 |
| TEXT_TO_SPEECH | 有声内容生成 |

### 企业应用类（5种）

| 标识符 | 适用场景 |
|--------|---------|
| KNOWLEDGE_GRAPH | 企业知识库、文档管理 |
| OCR_DOCUMENT | 发票识别、合同解析 |
| PREDICTIVE_ANALYTICS | 销售预测、库存优化 |
| ANOMALY_DETECTION | 风险控制、质量监控 |
| QA_SYSTEM | 企业FAQ、内部问答 |

### 用户体验类（4种）

| 标识符 | 适用场景 |
|--------|---------|
| HYPER_PERSONALIZATION | 个性化推荐、内容定制 |
| CHATBOT | 智能客服、对话助手 |
| SENTIMENT_ANALYSIS | 用户反馈分析、舆情监控 |
| SPEECH_TO_TEXT | 语音输入、语音搜索 |

### 技术开发类（2种）

| 标识符 | 适用场景 |
|--------|---------|
| CODE_COMPLETION | 代码补全、代码生成 |
| RAG | 技术文档问答、API查询 |

### 实时交互类（2种）

| 标识符 | 适用场景 |
|--------|---------|
| REALTIME_STREAM | 视频会议字幕、直播互动 |
| IMAGE_RECOGNITION | 实时物体识别、AR应用 |

---

## 技术选型速查

### 阿里云通义千问系列（主力）

| API | 适用AI能力 | 特点 |
|-----|-----------|------|
| Qwen-Max | 文本理解、生成 | KNOWLEDGE_GRAPH, HYPER_PERSONALIZATION, PREDICTIVE_ANALYTICS, ANOMALY_DETECTION |
| Qwen-VL-Max | 多模态理解 | VIDEO_ANALYSIS, OCR_DOCUMENT, IMAGE_RECOGNITION |
| 通义万相（Wanx） | 图像生成 | MULTIMODAL_GENERATION |

### Google Gemini系列（备选）

| API | 适用AI能力 | 特点 |
|-----|-----------|------|
| Gemini 2.0 Multimodal Live | 实时流处理 | REALTIME_STREAM |

### 成本对比（1万DAU/月）

| 提供商 | 月成本 | 覆盖能力数 | 性价比 |
|--------|--------|-----------|--------|
| 阿里云通义千问 | $372 | 15种 | ⭐⭐⭐⭐⭐ |
| Google Gemini | $42 | 1种 | ⭐⭐⭐⭐ |
| OpenAI GPT-4 | $850+ | 11种 | ⭐⭐⭐ |

---

## 实现优先级建议

### P0（高优先级）- 立即开发

| 标识符 | 理由 |
|--------|------|
| OCR_DOCUMENT | 用户需求明确，技术成熟，成本低 |
| HYPER_PERSONALIZATION | 直接提升用户留存率（+40%） |
| VIDEO_ANALYSIS | 短视频应用刚需，市场规模大 |

### P1（中优先级）- 2-3个月内

| 标识符 | 理由 |
|--------|------|
| PREDICTIVE_ANALYTICS | 帮助业务决策，ROI高 |
| ANOMALY_DETECTION | 安全刚需，风险控制 |
| KNOWLEDGE_GRAPH | 企业客户青睐，客单价高 |

### P2（低优先级）- 长期规划

| 标识符 | 理由 |
|--------|------|
| REALTIME_STREAM | 技术复杂度高，成本高 |
| MULTIMODAL_GENERATION | 成本高（$168/月），需求相对小众 |

---

## 成本速查表（1万DAU/月）

| AI能力 | 调用量 | 单次成本 | 月成本 |
|--------|--------|---------|--------|
| VIDEO_ANALYSIS | 50,000次 | ¥0.007 | $50 |
| KNOWLEDGE_GRAPH | 100,000次 | ¥0.002 | $28 |
| OCR_DOCUMENT | 80,000次 | ¥0.001 | $11 |
| REALTIME_STREAM | 10,000分钟 | ¥0.003/分钟 | $42 |
| HYPER_PERSONALIZATION | 200,000次 | ¥0.003 | $84 |
| PREDICTIVE_ANALYTICS | 30,000次 | ¥0.004 | $17 |
| MULTIMODAL_GENERATION | 20,000次 | ¥0.06 | $168 |
| ANOMALY_DETECTION | 50,000次 | ¥0.002 | $14 |
| **总计** | - | - | **$414** |

---

## 代码示例速查

### 使用OCR_DOCUMENT识别身份证

```kotlin
val ocrService = OCRDocumentService()
val result = ocrService.recognizeIDCard("https://example.com/idcard.jpg")

println("姓名: ${result.fields["name"]}")
println("身份证号: ${result.fields["idNumber"]}")
println("置信度: ${result.confidence}")
```

### 使用HYPER_PERSONALIZATION推荐内容

```kotlin
val personalizationService = PersonalizationService()
val recommendations = personalizationService.getRecommendations(
    userId = "user123",
    candidateItems = listOf(item1, item2, item3),
    limit = 3
)

recommendations.forEach { rec ->
    println("推荐: ${rec.title}")
    println("评分: ${rec.score}")
    println("理由: ${rec.reason}")
}
```

### 使用VIDEO_ANALYSIS分析视频

```kotlin
val videoService = VideoAnalysisService()
val analysis = videoService.analyze(
    videoUrl = "https://example.com/video.mp4",
    analysisType = "object_detection"
)

println("检测到的物体:")
analysis.objects.forEach { obj ->
    println("- ${obj.name} (置信度: ${obj.confidence})")
}
```

---

## 常见问题FAQ

### Q1: 如何选择合适的AI能力类型？

**A**: 根据应用场景选择：
1. 文档处理 → OCR_DOCUMENT
2. 视频应用 → VIDEO_ANALYSIS
3. 推荐系统 → HYPER_PERSONALIZATION
4. 风控安全 → ANOMALY_DETECTION
5. 企业知识库 → KNOWLEDGE_GRAPH

### Q2: 为什么选择阿里云通义千问而不是OpenAI？

**A**: 三大原因：
1. **成本**：阿里云成本仅为GPT-4的1/5
2. **速度**：国内访问延迟<50ms，GPT-4常被墙
3. **中文**：中文理解能力强，文档友好

### Q3: SIMPLE、MEDIUM、COMPLEX复杂度如何区分？

**A**:
- **SIMPLE**: 单一AI能力，直接调用API，2-3天完成
- **MEDIUM**: 多个AI能力组合，需要编排，5-7天完成
- **COMPLEX**: 需要自定义Agent和Memory，10-14天完成

### Q4: 如何降低API调用成本？

**A**: 三大优化策略：
1. **请求缓存**: 节省30-40%成本
2. **用量配额**: 节省20-30%成本
3. **降级策略**: 节省10-20%成本

### Q5: 如何验证AI能力的效果？

**A**: 四步验证流程：
1. 单元测试：验证代码逻辑
2. E2E测试：验证端到端流程
3. 真实数据测试：验证准确性
4. A/B测试：验证用户价值

---

## 相关文档链接

- [完整设计文档](./NEW_AI_CAPABILITIES_DESIGN.md) - 26,000+字详细设计
- [完成总结](./NEW_AI_CAPABILITIES_SUMMARY.md) - 执行摘要和关键成果
- [检查清单](../../TASK_2_1_CHECKLIST.md) - 提交前检查清单
- [阿里云DashScope文档](https://help.aliyun.com/zh/dashscope/)
- [Google Gemini API文档](https://ai.google.dev/gemini-api/docs)

---

## 更新日志

| 版本 | 日期 | 更新内容 |
|-----|------|---------|
| v1.0.0 | 2025-11-11 | 初始版本，新增8种AI能力类型 |

---

**Made with ❤️ by Ingenio Team**
