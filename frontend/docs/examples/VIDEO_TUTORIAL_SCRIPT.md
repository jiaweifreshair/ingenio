# Ingenio AI能力视频教程脚本

> **视频时长**: 8-10分钟
> **目标受众**: 开发者、产品经理、技术爱好者
> **制作日期**: 2025-11-11

---

## 视频结构

### 开场（30秒）
### 第一部分：平台介绍（1分钟）
### 第二部分：AI能力选择（2分钟）
### 第三部分：代码生成演示（3分钟）
### 第四部分：本地运行测试（2分钟）
### 第五部分：高级功能展示（1.5分钟）
### 结尾（30秒）

---

## 详细脚本

### 开场（30秒）

**画面**: Ingenio logo动画 + 背景音乐

**旁白**:
```
大家好，欢迎来到Ingenio平台的使用教程。

Ingenio是一个AI驱动的自然语言编程平台，
让你通过简单的需求描述，就能生成完整的移动应用代码。

今天，我将用8分钟时间，带你体验如何使用Ingenio的19种AI能力，
快速构建一个智能客服系统。

让我们开始吧！
```

**画面切换**: Ingenio平台首页

---

### 第一部分：平台介绍（1分钟）

**画面**: 浏览器打开 http://localhost:3000

**旁白**:
```
首先，我们打开Ingenio平台的主页。

这里展示了Ingenio的三大核心功能：
1. 自然语言需求分析 - AI自动理解你的需求
2. 结构化数据建模 - 自动生成数据库Schema
3. Kotlin Multiplatform代码生成 - 一次编写，Android和iOS双端运行

（点击"开始构建"按钮）

现在，让我们点击"开始构建"，进入向导页面。
```

**画面**: 向导页面加载动画

**旁白**:
```
Ingenio的向导采用分步式设计，引导你完成应用创建的每个步骤。

整个流程分为4步：
1. 基本信息 - 输入应用名称和包名
2. 需求描述 - 用自然语言描述你的需求
3. AI能力选择 - 选择需要的AI功能
4. 生成代码 - 一键生成完整项目
```

**画面**: 高亮显示4个步骤

---

### 第二部分：AI能力选择（2分钟）

**画面**: 填写基本信息表单

**旁白**:
```
首先，我们填写基本信息。

应用名称：智能客服系统
包名：com.example.customer.service
目标平台：Android + iOS

（填写完毕，点击"下一步"）

接下来是需求描述。
```

**画面**: 需求描述输入框

**旁白**:
```
我输入我的需求：
"我想构建一个智能客服系统，能够理解用户问题并自动回复，
 同时能够分析用户的情绪，识别用户上传的图片，
 并根据用户历史对话提供个性化的服务。"

（输入完毕，点击"分析需求"）

Ingenio的AI会自动分析这段需求，提取关键信息。

（等待3秒）

好的，AI已经分析完毕。
它识别到了3个核心实体：User、Conversation、Message，
以及4个主要功能：对话、情感分析、图片识别、个性化推荐。

现在，AI自动推荐了5种AI能力：
```

**画面**: AI能力推荐结果高亮显示

**旁白**:
```
1. CHATBOT - 智能对话机器人（核心功能）
2. SENTIMENT_ANALYSIS - 情感分析（了解用户情绪）
3. IMAGE_RECOGNITION - 图像识别（识别用户上传的图片）
4. HYPER_PERSONALIZATION - 超个性化推荐（个性化服务）
5. RECOMMENDATION - 推荐系统（智能推荐）

这些推荐基于AI对需求的深度理解，
我们可以直接使用，也可以手动调整。

（点击每个AI能力，查看详情）

每个AI能力都有详细的说明：
- 功能描述：做什么用
- 适用场景：什么时候用
- 技术实现：基于哪个AI模型
- 成本估算：每月大约多少钱
- 预估工期：需要多少天开发

比如CHATBOT，基于阿里云通义千问Qwen-Max，
月成本约$1.7（1万活跃用户），预估2天开发完成。
```

**画面**: 点击"确认选择"按钮

---

### 第三部分：代码生成演示（3分钟）

**画面**: 代码生成进度条

**旁白**:
```
现在，我们点击"生成代码"按钮。

Ingenio会执行三Agent工作流：
1. PlanAgent - 分析需求并生成规划
2. ExecuteAgent - 生成Kotlin代码
3. ValidateAgent - 验证代码质量

（等待5秒，显示生成进度）

第一步，PlanAgent正在分析需求...
它识别了3个实体、5个关系、8个操作。

第二步，ExecuteAgent正在生成代码...
它生成了数据模型、Repository、ViewModel、UI界面。

第三步，ValidateAgent正在验证代码...
编译检查：通过 ✓
单元测试：通过 ✓
E2E测试：通过 ✓

好的，代码生成完成！
```

**画面**: 生成结果页面

**旁白**:
```
让我们看看生成了什么：

（打开文件树）

完整的Kotlin Multiplatform项目：
- androidApp/ - Android应用
- iosApp/ - iOS应用
- core/ - 共享业务逻辑
  - ai/ - AI服务类
    - ChatbotService.kt
    - SentimentAnalysisService.kt
    - ImageRecognitionService.kt
    - PersonalizationService.kt
  - presentation/ - ViewModel层
  - data/ - 数据层
  - config/ - 配置文件

（点击ChatbotService.kt）

让我们看看生成的ChatbotService代码。

（滚动代码，高亮关键部分）

这里是完整的聊天机器人服务实现：
- sendMessage() - 发送消息并获取AI回复
- streamMessage() - 流式响应（SSE）
- clearHistory() - 清空对话历史

代码包含完整的错误处理、超时重试、日志记录。

所有的依赖都已经配置好了：
- Ktor客户端
- Kotlinx序列化
- 协程支持

（关闭代码预览）

现在，我们点击"下载ZIP"按钮。
```

**画面**: 下载对话框

**旁白**:
```
ZIP文件正在下载...
（等待2秒）
下载完成！

ZIP文件包含完整的项目，大小约5MB。
```

---

### 第四部分：本地运行测试（2分钟）

**画面**: 终端窗口

**旁白**:
```
现在，让我们在本地运行这个项目。

首先，解压ZIP文件：
```

**屏幕录制**:
```bash
cd ~/Downloads
unzip CustomerServiceApp.zip
cd CustomerServiceApp
```

**旁白**:
```
接下来，配置API密钥。

Ingenio生成的项目需要真实的AI API才能运行，
不像其他平台使用Mock数据。

（复制local.properties.template）
```

**屏幕录制**:
```bash
cp local.properties.template local.properties
vim local.properties
```

**旁白**:
```
我们填入阿里云通义千问的API密钥。

（快速输入，打码保护）

QWEN_API_KEY=sk-**************************

保存退出。

现在，构建项目：
```

**屏幕录制**:
```bash
./gradlew clean build
```

**画面**: Gradle构建输出

**旁白**:
```
Gradle正在下载依赖并编译代码...
（等待10秒）

BUILD SUCCESSFUL in 45s

编译成功！

现在，运行Android应用：
```

**屏幕录制**:
```bash
./gradlew :androidApp:installDebug
```

**画面**: Android模拟器启动

**旁白**:
```
应用正在安装到模拟器...
（等待5秒）

好的，应用已经启动了！

让我们测试一下聊天功能。
```

**画面**: Android应用界面

**旁白**:
```
（在聊天界面输入）
"你好，我想咨询一下产品问题。"

（发送消息）

AI正在思考...
（等待2秒）

收到回复了：
"您好！我是智能客服助手，很高兴为您服务。请问您想咨询什么产品问题呢？"

（查看日志）

在日志中，我们可以看到详细的AI调用信息：
- 请求时间：1.2秒
- Token消耗：35个
- 成本：¥0.00007 ≈ $0.00001

非常便宜！

现在，让我们上传一张图片测试图像识别功能。
```

**画面**: 选择图片对话框

**旁白**:
```
（选择一张产品图片）

上传成功，AI正在识别...
（等待3秒）

识别结果：
- 智能手机 (置信度: 95.2%)
- 黑色外壳 (置信度: 88.7%)
- 屏幕显示 (置信度: 92.1%)

AI回复：
"我看到您上传了一张智能手机的图片，这是我们的新款旗舰机型吗？
 如果您需要了解这款产品的详细参数，我可以为您介绍。"

太棒了！
AI不仅识别了图片内容，还结合上下文给出了智能回复。
```

---

### 第五部分：高级功能展示（1.5分钟）

**画面**: 返回Ingenio平台

**旁白**:
```
除了智能客服，Ingenio还支持更多高级AI能力。

让我给你展示几个：
```

**画面**: AI能力列表页面

**旁白**:
```
1. VIDEO_ANALYSIS - 视频分析
   可以分析视频中的物体、场景、动作
   适用于短视频应用、内容审核、智能监控

2. KNOWLEDGE_GRAPH - 知识图谱
   从文本中提取实体和关系，构建知识网络
   适用于企业知识库、学习应用、法律科技

3. REALTIME_STREAM - 实时流处理
   低延迟的双向流式交互
   适用于视频会议、直播平台、智能家居

4. PREDICTIVE_ANALYTICS - 预测分析
   基于历史数据预测未来趋势
   适用于销售预测、用户流失预测、风险评估

5. MULTIMODAL_GENERATION - 多模态生成
   文生图、图生文
   适用于内容创作、营销工具、电商应用

（点击每个能力，快速展示界面）

这些能力可以自由组合，
比如视频分析 + 异常检测 + 实时流处理，
就可以构建一个完整的智能监控系统。
```

**画面**: 组合推荐页面

**旁白**:
```
Ingenio还提供了智能组合推荐：

组合1：智能客服系统
- CHATBOT + SENTIMENT_ANALYSIS + IMAGE_RECOGNITION
- 成本：$5/月
- 工期：3天

组合2：内容平台
- TEXT_GENERATION + RECOMMENDATION + CONTENT_MODERATION
- 成本：$15/月
- 工期：5天

组合3：智能监控系统
- VIDEO_ANALYSIS + ANOMALY_DETECTION + REALTIME_STREAM
- 成本：$100/月
- 工期：12天

你可以根据自己的需求选择合适的组合。
```

---

### 结尾（30秒）

**画面**: Ingenio logo + 相关链接

**旁白**:
```
好的，今天的教程就到这里。

我们一起体验了Ingenio的完整流程：
1. 需求分析 - AI自动理解需求
2. AI能力选择 - 19种AI能力任你选
3. 代码生成 - 完整的Kotlin Multiplatform项目
4. 本地运行 - 真实的AI API集成

Ingenio的核心优势：
✓ 零Mock数据 - 所有功能都是真实AI
✓ 完整E2E测试 - 代码质量有保证
✓ Kotlin Multiplatform - Android和iOS双端统一
✓ 19种AI能力 - 覆盖99%的AI应用场景

如果你想了解更多：
- 📖 完整文档：docs/examples/AI_CAPABILITY_USAGE_EXAMPLES.md
- 🚀 快速开始：docs/examples/QUICK_START.md
- 💬 技术支持：dev@ingenio.dev
- ⭐ GitHub：github.com/ingenio/ingenio

感谢观看，我们下期再见！
```

**画面**: 淡出 + 订阅提示

---

## 后期制作要点

### 视频规格

- **分辨率**: 1920x1080 (1080p)
- **帧率**: 60fps
- **比特率**: 8-10 Mbps
- **格式**: MP4 (H.264编码)

### 音频规格

- **采样率**: 48kHz
- **比特率**: 192 kbps
- **格式**: AAC
- **音量**: -16 LUFS (符合YouTube标准)

### 特效要求

#### 文字标注
- 字体：Roboto / Noto Sans SC
- 大小：36px (主标题) / 24px (说明文字)
- 颜色：#FFFFFF (白色) / 50%黑色背景
- 位置：屏幕下方1/3

#### 高亮效果
- 使用黄色边框（#FFEB3B）高亮关键按钮
- 使用箭头指示点击位置
- 使用放大镜放大重要代码片段

#### 转场效果
- 场景切换：淡入淡出（0.5秒）
- 代码展示：从下往上滑入（0.3秒）
- 结果展示：从中心放大（0.4秒）

### 背景音乐

- **开场/结尾**: 轻快的电子音乐（120 BPM）
- **中间部分**: 柔和的环境音乐（80 BPM）
- **音量**: -30dB（不干扰旁白）

### 字幕要求

- **语言**: 中文 + 英文
- **格式**: SRT
- **位置**: 屏幕底部居中
- **字体**: Roboto / Noto Sans SC
- **大小**: 32px
- **颜色**: 白色 + 黑色描边

---

## 拍摄时间轴

| 时间点 | 内容 | 时长 | 备注 |
|-------|------|------|------|
| 00:00 | 开场动画 | 30s | Logo动画 + 标题 |
| 00:30 | 平台介绍 | 1min | 浏览器录屏 |
| 01:30 | AI能力选择 | 2min | 向导页面录屏 |
| 03:30 | 代码生成演示 | 3min | 生成进度 + 代码预览 |
| 06:30 | 本地运行测试 | 2min | 终端 + Android模拟器 |
| 08:30 | 高级功能展示 | 1.5min | 快速切换演示 |
| 10:00 | 结尾 | 30s | Logo + 链接 |

---

## 配套资源

### 测试数据

```
test-data/
├── sample-images/
│   ├── product-phone.jpg
│   ├── product-laptop.jpg
│   └── customer-issue.jpg
├── sample-videos/
│   └── customer-service-demo.mp4
├── sample-text/
│   ├── positive-review.txt
│   ├── negative-review.txt
│   └── neutral-comment.txt
└── sample-requirements/
    ├── customer-service.txt
    ├── content-platform.txt
    └── monitoring-system.txt
```

### 图标和Logo

```
assets/
├── ingenio-logo.svg
├── ingenio-icon.png
├── ai-capability-icons/
│   ├── chatbot.svg
│   ├── image-recognition.svg
│   ├── sentiment-analysis.svg
│   └── ... (19个)
└── platform-screenshots/
    ├── homepage.png
    ├── wizard-step1.png
    ├── wizard-step2.png
    └── ... (10+个)
```

---

## 发布清单

- [ ] 视频制作完成
- [ ] 字幕上传（中文 + 英文）
- [ ] 缩略图设计（1280x720）
- [ ] 视频标题优化（SEO）
- [ ] 视频描述编写（包含关键词）
- [ ] 标签添加（AI、Kotlin、移动开发等）
- [ ] 播放列表分类
- [ ] 社交媒体预告
- [ ] 博客文章配套
- [ ] 文档链接更新

---

**制作团队**: Ingenio Team
**联系方式**: dev@ingenio.dev
**版本**: v1.0.0
**日期**: 2025-11-11
