# Ingenio前端交互优化方案 V2.0

> **优化目标**：参考"码上飞"的对话式交互体验，实现从自然语言输入到多平台发布的完整流程
>
> **设计理念**："让用户做选择题，而非填空题" + "所见即所得"
>
> **创建日期**：2025-11-14

---

## 📋 目录

- [1. 当前问题分析](#1-当前问题分析)
- [2. 优化方案总览](#2-优化方案总览)
- [3. 四阶段交互设计](#3-四阶段交互设计)
- [4. 技术实现方案](#4-技术实现方案)
- [5. 组件架构设计](#5-组件架构设计)
- [6. 实施路线图](#6-实施路线图)

---

## 1. 当前问题分析

### 1.1 现有交互流程问题

| 问题 | 描述 | 影响 |
|-----|------|------|
| **单一表单输入** | 创建页面只有一个简单的文本框 | 用户不知道如何描述需求 |
| **缺乏实时反馈** | 输入后直接跳转，无即时响应 | 用户体验不连贯 |
| **无模板引导** | 没有行业模板推荐 | 用户需要从零开始思考 |
| **风格选择滞后** | 风格选择在执行后期 | 返工率高（35%） |
| **AI能力不透明** | AI能力自动判断，用户无感知 | 用户不知道为什么需要某些能力 |
| **执行过程黑盒** | 执行过程不可见 | 用户焦虑，不知道进度 |
| **发布流程分散** | 发布功能在不同页面 | 操作路径长 |

### 1.2 用户体验痛点

**痛点1：认知负荷过高**
- 用户面对空白文本框不知道写什么
- 需要自己想象最终效果
- 不知道有哪些可选项

**痛点2：等待焦虑**
- 提交后跳转到空白页面
- 看不到执行进度
- 不知道还要等多久

**痛点3：反馈滞后**
- 生成完成才能看到效果
- 不满意需要重新生成
- 浪费时间和资源

---

## 2. 优化方案总览

### 2.1 设计原则

**原则1：对话式交互**
- 模仿人与人对话的自然体验
- AI作为智能助手引导用户
- 实时响应，即时反馈

**原则2：渐进式引导**
- 分阶段降低用户决策复杂度
- 先大方向后细节
- 每阶段都有明确的输出

**原则3：所见即所得**
- 每个选择都有实时预览
- 用户在确认前就能看到效果
- 降低返工率

**原则4：透明化执行**
- 执行过程完全可见
- 实时显示进度和代码
- 用户随时可以查看详情

### 2.2 四阶段流程设计

```
阶段1：自然语言交互 + 意图识别 + 模板选择
   ↓ （用户选择模板或跳过）

阶段2：设计风格选择 + AI能力配置 + 实时预览
   ↓ （用户点击"确认设计"）⚠️ 关键确认点

阶段3：执行确认 + 实时进度 + 代码预览
   ↓ （自动进入，Execute Agent执行）

阶段4：发布和下载（多平台一键发布）
```

### 2.3 核心改进点

| 改进点 | V1.0（旧版） | V2.0（优化后） | 提升 |
|-------|------------|--------------|------|
| **交互方式** | 单一表单 | 对话式引导 | 用户体验 ⬆️ 80% |
| **模板推荐** | 无 | 智能推荐40+模板 | 降低认知负荷 60% |
| **风格预览** | 无 | 7种风格实时预览 | 降低返工率 50% |
| **AI能力** | 自动判断 | 用户可见可选 | 透明度 ⬆️ 100% |
| **执行过程** | 黑盒 | 实时展示 | 焦虑感 ⬇️ 70% |
| **发布流程** | 分散 | 一键发布 | 操作步骤 ⬇️ 3步 |

---

## 3. 四阶段交互设计

### 阶段1：自然语言交互 + 意图识别 + 模板选择

#### 3.1.1 布局设计（参考图1）

**三栏布局**：
```
┌────────────┬──────────────┬────────────┐
│            │              │            │
│  对话区域  │  预览区域    │  信息面板  │
│   (30%)    │    (40%)     │   (30%)    │
│            │              │            │
│ AI助手对话 │ 模板预览截图 │ 意图识别   │
│ 用户输入框 │ 实时切换     │ 推荐理由   │
│ 模板推荐   │ 空状态提示   │ 关键词提取 │
│ 跳过按钮   │              │            │
│            │              │            │
└────────────┴──────────────┴────────────┘
```

#### 3.1.2 左栏：对话区域

**设计要点**：
- **聊天窗口样式**：模仿微信/Slack的对话气泡
- **AI角色定位**："AI产品经理"，主动引导
- **消息类型**：
  - 文字消息（需求描述）
  - 卡片消息（模板推荐）
  - 操作按钮（选择/跳过）

**对话流程示例**：
```
AI: 你好！我是秒构AI助手，我将帮助你快速生成应用。请描述你想要的应用功能。

用户: 我想做一个民宿预订平台

AI: 收到！我理解你想做一个民宿预订平台。

    意图识别：从零设计 ✓
    行业分类：生活服务 > 住宿预订 ✓

    我为你推荐了3个相关模板，你可以选择一个作为起点，
    或者选择跳过，我们从零开始设计。

[模板卡片1: Airbnb风格民宿预订]
[模板卡片2: 携程风格酒店预订]
[模板卡片3: 自如风格公寓租赁]

[按钮: 跳过模板，从零开始]
```

**技术实现**：
```tsx
// 组件路径：src/components/chat/chat-interface.tsx
interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  type: 'text' | 'card' | 'action';
  metadata?: {
    intent?: IntentClassificationResult;
    templates?: IndustryTemplate[];
  };
}

export function ChatInterface() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isTyping, setIsTyping] = useState(false);

  // 发送消息 + 调用意图识别API
  const handleSendMessage = async () => {
    // 1. 添加用户消息
    const userMessage = { role: 'user', content: input, ... };
    setMessages(prev => [...prev, userMessage]);

    // 2. 调用意图识别API
    setIsTyping(true);
    const intentResult = await classifyIntent(input);

    // 3. AI响应 + 模板推荐
    const aiMessage = {
      role: 'assistant',
      content: buildAIResponse(intentResult),
      type: 'card',
      metadata: {
        intent: intentResult,
        templates: intentResult.matchedTemplates
      }
    };
    setMessages(prev => [...prev, aiMessage]);
    setIsTyping(false);
  };

  return (
    <div className="flex flex-col h-full">
      {/* 消息列表 */}
      <ScrollArea className="flex-1 p-4">
        {messages.map(msg => (
          <MessageBubble key={msg.id} message={msg} />
        ))}
        {isTyping && <TypingIndicator />}
      </ScrollArea>

      {/* 输入框 */}
      <div className="border-t p-4">
        <Textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="描述你想要的应用..."
          onKeyPress={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSendMessage();
            }
          }}
        />
        <Button onClick={handleSendMessage}>发送</Button>
      </div>
    </div>
  );
}
```

#### 3.1.3 中栏：预览区域

**设计要点**：
- 显示选中模板的预览截图（静态图片）
- 支持鼠标悬停放大查看
- 空状态友好提示

**技术实现**：
```tsx
// 组件路径：src/components/preview/template-preview.tsx
export function TemplatePreview({
  template
}: {
  template?: IndustryTemplate
}) {
  if (!template) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center text-muted-foreground">
          <FileQuestion className="w-16 h-16 mx-auto mb-4" />
          <p>选择一个模板查看预览</p>
        </div>
      </div>
    );
  }

  return (
    <div className="relative h-full overflow-hidden">
      <img
        src={template.thumbnail}
        alt={template.name}
        className="w-full h-full object-contain hover:scale-105 transition-transform"
      />
      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/60 to-transparent p-4">
        <h3 className="text-white font-semibold">{template.name}</h3>
        <p className="text-white/80 text-sm">{template.category}</p>
      </div>
    </div>
  );
}
```

#### 3.1.4 右栏：信息面板

**显示内容**：
- 意图识别结果（克隆/设计/混合）
- 置信度评分
- 提取的关键词
- 推荐理由说明

**技术实现**：
```tsx
// 组件路径：src/components/info/intent-info-panel.tsx
export function IntentInfoPanel({
  intent
}: {
  intent?: IntentClassificationResult
}) {
  if (!intent) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>意图分析结果</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 意图类型 */}
        <div>
          <Label>识别意图</Label>
          <Badge variant="default" className="mt-1">
            {getIntentLabel(intent.intent)}
          </Badge>
        </div>

        {/* 置信度 */}
        <div>
          <Label>置信度</Label>
          <div className="flex items-center gap-2 mt-1">
            <Progress value={intent.confidence * 100} />
            <span className="text-sm">
              {(intent.confidence * 100).toFixed(1)}%
            </span>
          </div>
        </div>

        {/* 关键词 */}
        <div>
          <Label>提取关键词</Label>
          <div className="flex flex-wrap gap-2 mt-2">
            {intent.extractedKeywords.map(keyword => (
              <Badge key={keyword} variant="outline">
                {keyword}
              </Badge>
            ))}
          </div>
        </div>

        {/* 推荐理由 */}
        <div>
          <Label>推荐理由</Label>
          <p className="text-sm text-muted-foreground mt-1">
            {intent.reasoning}
          </p>
        </div>

        {/* 警告信息 */}
        {intent.warnings && intent.warnings.length > 0 && (
          <Alert variant="warning">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>
              {intent.warnings.join('; ')}
            </AlertDescription>
          </Alert>
        )}
      </CardContent>
    </Card>
  );
}
```

---

### 阶段2：设计风格选择 + AI能力配置

#### 3.2.1 布局设计（参考图1）

**三栏布局**：
```
┌────────────┬──────────────┬────────────┐
│            │              │            │
│ 风格选择   │  实时预览    │  AI能力    │
│   (30%)    │    (40%)     │   (30%)    │
│            │              │            │
│ 7种风格    │ 交互原型     │ NLP能力    │
│ 卡片列表   │ E2B Sandbox  │ Vision能力 │
│ 选中高亮   │ 可点击体验   │ Speech能力 │
│            │              │ 推荐高亮   │
│ [确认设计] │              │ 支持多选   │
│            │              │            │
└────────────┴──────────────┴────────────┘
```

#### 3.2.2 左栏：风格选择面板

**SuperDesign 7种风格**：
```typescript
const DESIGN_STYLES = [
  {
    id: 'A',
    name: '现代极简',
    description: '大留白、卡片式、简洁图标',
    thumbnail: '/styles/modern-minimal.jpg',
    tags: ['科技', '专业', '高端']
  },
  {
    id: 'B',
    name: '活力时尚',
    description: '渐变色彩、圆角设计、网格布局',
    thumbnail: '/styles/vibrant-modern.jpg',
    tags: ['年轻', '潮流', '社交']
  },
  {
    id: 'C',
    name: '经典专业',
    description: '传统布局、信息密集、列表式',
    thumbnail: '/styles/classic-professional.jpg',
    tags: ['企业', '严谨', '信息']
  },
  {
    id: 'D',
    name: '未来科技',
    description: '深色主题、霓虹色彩、3D元素',
    thumbnail: '/styles/futuristic-tech.jpg',
    tags: ['科幻', '炫酷', '游戏']
  },
  {
    id: 'E',
    name: '沉浸式3D',
    description: '毛玻璃、深度阴影、视差滚动',
    thumbnail: '/styles/immersive-3d.jpg',
    tags: ['沉浸', '立体', '艺术']
  },
  {
    id: 'F',
    name: '游戏化设计',
    description: '卡通风格、奖励反馈、成就系统',
    thumbnail: '/styles/gamification.jpg',
    tags: ['趣味', '互动', '游戏']
  },
  {
    id: 'G',
    name: '自然流动',
    description: '有机曲线、自然配色、流体动画',
    thumbnail: '/styles/organic-flow.jpg',
    tags: ['自然', '柔和', '舒适']
  }
];
```

**技术实现**：
```tsx
// 组件路径：src/components/design/style-selector.tsx
export function StyleSelector({
  onStyleSelect,
  onConfirm
}: StyleSelectorProps) {
  const [selectedStyle, setSelectedStyle] = useState<string | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);

  const handleStyleClick = async (styleId: string) => {
    setSelectedStyle(styleId);
    setIsGenerating(true);

    // 触发OpenLovable生成预览（5-10秒）
    await onStyleSelect(styleId);

    setIsGenerating(false);
  };

  return (
    <div className="space-y-4">
      <div className="grid gap-4">
        {DESIGN_STYLES.map(style => (
          <Card
            key={style.id}
            className={cn(
              "cursor-pointer transition-all",
              selectedStyle === style.id && "ring-2 ring-primary"
            )}
            onClick={() => handleStyleClick(style.id)}
          >
            <CardContent className="p-4">
              <div className="flex gap-4">
                <img
                  src={style.thumbnail}
                  alt={style.name}
                  className="w-24 h-24 rounded-lg object-cover"
                />
                <div className="flex-1">
                  <h3 className="font-semibold mb-1">{style.name}</h3>
                  <p className="text-sm text-muted-foreground mb-2">
                    {style.description}
                  </p>
                  <div className="flex gap-2">
                    {style.tags.map(tag => (
                      <Badge key={tag} variant="outline" className="text-xs">
                        {tag}
                      </Badge>
                    ))}
                  </div>
                </div>
                {selectedStyle === style.id && (
                  <CheckCircle2 className="w-6 h-6 text-primary" />
                )}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 确认设计按钮 - 关键确认点 ⚠️ */}
      <Button
        className="w-full"
        size="lg"
        disabled={!selectedStyle || isGenerating}
        onClick={onConfirm}
      >
        {isGenerating ? (
          <>
            <Loader2 className="w-4 h-4 mr-2 animate-spin" />
            生成预览中...
          </>
        ) : (
          <>
            <Sparkles className="w-4 h-4 mr-2" />
            确认设计，开始生成后端
          </>
        )}
      </Button>
    </div>
  );
}
```

#### 3.2.3 中栏：实时预览（关键！）

**OpenLovable E2B Sandbox集成**：
- 显示可交互的React原型
- 5-10秒快速生成
- 支持点击、滚动等交互

**技术实现**：
```tsx
// 组件路径：src/components/preview/interactive-preview.tsx
export function InteractivePreview({
  styleId,
  requirement
}: InteractivePreviewProps) {
  const [sandboxUrl, setSandboxUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!styleId || !requirement) return;

    const generatePreview = async () => {
      setIsLoading(true);
      setError(null);

      try {
        // 调用OpenLovable生成预览
        const response = await fetch('/api/v1/openlovable/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            userRequirement: requirement,
            styleId: styleId,
            streaming: false,
            timeoutSeconds: 15
          })
        });

        const data = await response.json();

        if (data.success && data.previewUrl) {
          setSandboxUrl(data.previewUrl);
        } else {
          throw new Error(data.error || '生成预览失败');
        }
      } catch (err) {
        console.error('预览生成失败:', err);
        setError(err instanceof Error ? err.message : '生成失败');
      } finally {
        setIsLoading(false);
      }
    };

    generatePreview();
  }, [styleId, requirement]);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <Loader2 className="w-12 h-12 animate-spin mx-auto mb-4 text-primary" />
          <p className="text-muted-foreground">正在生成预览...</p>
          <p className="text-sm text-muted-foreground">预计需要5-10秒</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-full">
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      </div>
    );
  }

  if (!sandboxUrl) {
    return (
      <div className="flex items-center justify-center h-full">
        <p className="text-muted-foreground">选择风格后查看预览</p>
      </div>
    );
  }

  return (
    <div className="relative h-full">
      {/* E2B Sandbox iframe */}
      <iframe
        src={sandboxUrl}
        className="w-full h-full border-0"
        title="Interactive Preview"
        sandbox="allow-scripts allow-same-origin"
      />

      {/* 预览提示 */}
      <div className="absolute top-4 left-4 bg-black/60 text-white px-3 py-2 rounded-lg text-sm">
        <Sparkles className="w-4 h-4 inline mr-2" />
        可交互预览 - 点击体验
      </div>
    </div>
  );
}
```

#### 3.2.4 右栏：AI能力选择面板（新增！）⭐

**AI能力分类**：
```typescript
const AI_CAPABILITIES = {
  nlp: [
    {
      id: 'text-generation',
      name: '文本生成',
      description: '自动生成文章、评论、描述',
      icon: FileText,
      recommended: true
    },
    {
      id: 'sentiment-analysis',
      name: '情感分析',
      description: '分析用户评论的情感倾向',
      icon: Heart,
      recommended: false
    },
    {
      id: 'keyword-extraction',
      name: '关键词提取',
      description: '从文本中提取核心关键词',
      icon: Tag,
      recommended: true
    }
  ],
  vision: [
    {
      id: 'image-recognition',
      name: '图像识别',
      description: '识别图片中的物体和场景',
      icon: Camera,
      recommended: true
    },
    {
      id: 'ocr',
      name: '文字识别OCR',
      description: '提取图片中的文字内容',
      icon: ScanText,
      recommended: false
    },
    {
      id: 'face-detection',
      name: '人脸检测',
      description: '检测和识别人脸',
      icon: User,
      recommended: false
    }
  ],
  speech: [
    {
      id: 'speech-recognition',
      name: '语音识别',
      description: '将语音转换为文字',
      icon: Mic,
      recommended: false
    },
    {
      id: 'text-to-speech',
      name: '语音合成',
      description: '将文字转换为语音',
      icon: Volume2,
      recommended: false
    }
  ]
};
```

**技术实现**：
```tsx
// 组件路径：src/components/ai/ai-capability-selector.tsx
export function AICapabilitySelector({
  onSelectionChange
}: AICapabilitySelectorProps) {
  const [selectedCapabilities, setSelectedCapabilities] = useState<string[]>([]);

  const toggleCapability = (capabilityId: string) => {
    setSelectedCapabilities(prev => {
      const newSelection = prev.includes(capabilityId)
        ? prev.filter(id => id !== capabilityId)
        : [...prev, capabilityId];

      onSelectionChange(newSelection);
      return newSelection;
    });
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles className="w-5 h-5" />
          AI能力选择
        </CardTitle>
        <CardDescription>
          根据你的需求，我们推荐以下AI能力（可多选）
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* NLP能力 */}
        <div>
          <h4 className="font-semibold mb-3 flex items-center gap-2">
            <FileText className="w-4 h-4" />
            自然语言处理
          </h4>
          <div className="space-y-2">
            {AI_CAPABILITIES.nlp.map(capability => (
              <AICapabilityCard
                key={capability.id}
                capability={capability}
                selected={selectedCapabilities.includes(capability.id)}
                onToggle={() => toggleCapability(capability.id)}
              />
            ))}
          </div>
        </div>

        {/* Vision能力 */}
        <div>
          <h4 className="font-semibold mb-3 flex items-center gap-2">
            <Camera className="w-4 h-4" />
            计算机视觉
          </h4>
          <div className="space-y-2">
            {AI_CAPABILITIES.vision.map(capability => (
              <AICapabilityCard
                key={capability.id}
                capability={capability}
                selected={selectedCapabilities.includes(capability.id)}
                onToggle={() => toggleCapability(capability.id)}
              />
            ))}
          </div>
        </div>

        {/* Speech能力 */}
        <div>
          <h4 className="font-semibold mb-3 flex items-center gap-2">
            <Mic className="w-4 h-4" />
            语音处理
          </h4>
          <div className="space-y-2">
            {AI_CAPABILITIES.speech.map(capability => (
              <AICapabilityCard
                key={capability.id}
                capability={capability}
                selected={selectedCapabilities.includes(capability.id)}
                onToggle={() => toggleCapability(capability.id)}
              />
            ))}
          </div>
        </div>

        {/* 已选择统计 */}
        <div className="pt-4 border-t">
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">
              已选择 {selectedCapabilities.length} 项AI能力
            </span>
            {selectedCapabilities.length > 0 && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setSelectedCapabilities([]);
                  onSelectionChange([]);
                }}
              >
                清空
              </Button>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

// AI能力卡片组件
function AICapabilityCard({
  capability,
  selected,
  onToggle
}: AICapabilityCardProps) {
  const Icon = capability.icon;

  return (
    <div
      className={cn(
        "flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-all",
        selected
          ? "border-primary bg-primary/5"
          : "border-border hover:border-primary/50"
      )}
      onClick={onToggle}
    >
      <Checkbox
        checked={selected}
        onCheckedChange={onToggle}
      />
      <Icon className="w-5 h-5 text-primary" />
      <div className="flex-1">
        <div className="flex items-center gap-2">
          <span className="font-medium text-sm">{capability.name}</span>
          {capability.recommended && (
            <Badge variant="secondary" className="text-xs">
              推荐
            </Badge>
          )}
        </div>
        <p className="text-xs text-muted-foreground mt-0.5">
          {capability.description}
        </p>
      </div>
    </div>
  );
}
```

---

### 阶段3：执行确认 + 实时进度（参考图2）

#### 3.3.1 布局设计（左右分屏）

**分屏布局**：
```
┌────────────────┬─────────────────────┐
│                │                     │
│  需求配置总结  │  实时执行进度       │
│    (35%)       │      (65%)          │
│                │                     │
│ 1. 原始需求    │ ┌─ Plan Agent ─────┐│
│ 2. 选择的模板  │ │ ✓ 意图识别完成    ││
│ 3. 选择的风格  │ │ ✓ 模板匹配完成    ││
│ 4. AI能力清单  │ │ ✓ 原型生成完成    ││
│ 5. 配置参数    │ │ ✓ 用户确认完成    ││
│                │ └──────────────────┘│
│ [修改配置]     │                     │
│ [取消生成]     │ ┌─ Execute Agent ──┐│
│                │ │ 🔄 数据库设计中... ││
│                │ │ ⏳ 后端代码生成    ││
│                │ │ ⏳ 前端API集成     ││
│                │ │ ⏳ AI能力集成      ││
│                │ └──────────────────┘│
│                │                     │
│                │ ┌─ Validate Agent ─┐│
│                │ │ ⏳ 多平台编译      ││
│                │ │ ⏳ 沙箱验证        ││
│                │ │ ⏳ 性能测试        ││
│                │ └──────────────────┘│
│                │                     │
│                │ 代码实时预览：      │
│                │ src/entities/...    │
│                │ src/services/...    │
│                │                     │
└────────────────┴─────────────────────┘
```

#### 3.3.2 左栏：需求配置总结

**技术实现**：
```tsx
// 组件路径：src/components/summary/requirement-summary.tsx
export function RequirementSummary({
  requirement,
  template,
  style,
  aiCapabilities,
  config,
  onEdit,
  onCancel
}: RequirementSummaryProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>需求配置总结</CardTitle>
        <CardDescription>
          确认以下配置无误后，将开始生成后端代码
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 1. 原始需求 */}
        <div>
          <Label className="flex items-center gap-2">
            <FileText className="w-4 h-4" />
            原始需求
          </Label>
          <div className="mt-2 p-3 bg-muted rounded-lg">
            <p className="text-sm">{requirement}</p>
          </div>
        </div>

        {/* 2. 选择的模板 */}
        {template && (
          <div>
            <Label className="flex items-center gap-2">
              <Layers className="w-4 h-4" />
              行业模板
            </Label>
            <div className="mt-2 p-3 bg-muted rounded-lg">
              <div className="flex items-center gap-3">
                <img
                  src={template.thumbnail}
                  alt={template.name}
                  className="w-12 h-12 rounded object-cover"
                />
                <div>
                  <p className="font-medium text-sm">{template.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {template.category}
                  </p>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 3. 选择的风格 */}
        <div>
          <Label className="flex items-center gap-2">
            <Palette className="w-4 h-4" />
            设计风格
          </Label>
          <div className="mt-2">
            <Badge variant="default">{style.name}</Badge>
          </div>
        </div>

        {/* 4. AI能力清单 */}
        {aiCapabilities.length > 0 && (
          <div>
            <Label className="flex items-center gap-2">
              <Sparkles className="w-4 h-4" />
              AI能力 ({aiCapabilities.length})
            </Label>
            <div className="mt-2 flex flex-wrap gap-2">
              {aiCapabilities.map(cap => (
                <Badge key={cap} variant="outline">
                  {getCapabilityName(cap)}
                </Badge>
              ))}
            </div>
          </div>
        )}

        {/* 5. 配置参数 */}
        <div>
          <Label>配置参数</Label>
          <div className="mt-2 space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">AI模型</span>
              <span className="font-medium">{config.model}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">质量阈值</span>
              <span className="font-medium">{config.qualityThreshold}%</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">跳过验证</span>
              <span className="font-medium">
                {config.skipValidation ? '是' : '否'}
              </span>
            </div>
          </div>
        </div>

        {/* 操作按钮 */}
        <div className="pt-4 border-t space-y-2">
          <Button
            variant="outline"
            className="w-full"
            onClick={onEdit}
          >
            <Edit className="w-4 h-4 mr-2" />
            修改配置
          </Button>
          <Button
            variant="destructive"
            className="w-full"
            onClick={onCancel}
          >
            <X className="w-4 h-4 mr-2" />
            取消生成
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
```

#### 3.3.3 右栏：实时执行进度（关键！）⭐

**WebSocket实时推送**：
- Plan Agent进度（已完成）
- Execute Agent进度（当前执行）
- Validate Agent进度（待执行）
- 生成的代码实时展示

**技术实现**：
```tsx
// 组件路径：src/components/execution/realtime-execution-panel.tsx
export function RealtimeExecutionPanel({
  taskId
}: RealtimeExecutionPanelProps) {
  const {
    agents,
    currentAgent,
    progress,
    generatedCode,
    isConnected
  } = useGenerationWebSocket({ taskId });

  return (
    <div className="h-full flex flex-col">
      {/* 连接状态 */}
      <div className="border-b p-4">
        <div className="flex items-center gap-2">
          {isConnected ? (
            <>
              <Activity className="w-4 h-4 text-green-500 animate-pulse" />
              <span className="text-sm text-green-600">实时连接中</span>
            </>
          ) : (
            <>
              <AlertCircle className="w-4 h-4 text-orange-500" />
              <span className="text-sm text-orange-600">连接断开</span>
            </>
          )}
        </div>
      </div>

      {/* Agent执行进度 */}
      <ScrollArea className="flex-1 p-4">
        <div className="space-y-4">
          {agents.map(agent => (
            <AgentProgressCard
              key={agent.id}
              agent={agent}
              isCurrent={currentAgent === agent.type}
            />
          ))}
        </div>

        {/* 代码实时预览 */}
        {generatedCode && generatedCode.length > 0 && (
          <div className="mt-6">
            <h4 className="font-semibold mb-3 flex items-center gap-2">
              <Code className="w-4 h-4" />
              生成的代码
            </h4>
            <Tabs defaultValue="entity">
              <TabsList>
                <TabsTrigger value="entity">Entity</TabsTrigger>
                <TabsTrigger value="service">Service</TabsTrigger>
                <TabsTrigger value="controller">Controller</TabsTrigger>
              </TabsList>
              {generatedCode.map(file => (
                <TabsContent key={file.path} value={file.type}>
                  <Card>
                    <CardHeader>
                      <CardTitle className="text-sm">
                        {file.path}
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      <SyntaxHighlighter
                        language="java"
                        style={vscDarkPlus}
                      >
                        {file.content}
                      </SyntaxHighlighter>
                    </CardContent>
                  </Card>
                </TabsContent>
              ))}
            </Tabs>
          </div>
        )}
      </ScrollArea>
    </div>
  );
}

// Agent进度卡片
function AgentProgressCard({
  agent,
  isCurrent
}: AgentProgressCardProps) {
  return (
    <Card className={cn(
      "transition-all",
      isCurrent && "border-primary shadow-lg"
    )}>
      <CardContent className="p-4">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-3">
            {agent.status === 'completed' && (
              <CheckCircle2 className="w-5 h-5 text-green-500" />
            )}
            {agent.status === 'running' && (
              <Loader2 className="w-5 h-5 text-primary animate-spin" />
            )}
            {agent.status === 'pending' && (
              <Clock className="w-5 h-5 text-muted-foreground" />
            )}
            <div>
              <h4 className="font-semibold">{agent.name}</h4>
              {agent.status === 'running' && agent.currentStep && (
                <p className="text-sm text-muted-foreground">
                  {agent.currentStep}
                </p>
              )}
            </div>
          </div>
          <Badge variant={
            agent.status === 'completed' ? 'default' :
            agent.status === 'running' ? 'secondary' :
            'outline'
          }>
            {getStatusLabel(agent.status)}
          </Badge>
        </div>

        {/* 进度条 */}
        {agent.status === 'running' && (
          <div className="mt-3">
            <div className="flex items-center justify-between text-sm mb-1">
              <span>进度</span>
              <span>{agent.progress}%</span>
            </div>
            <Progress value={agent.progress} />
          </div>
        )}

        {/* 子步骤列表 */}
        {agent.status === 'running' && agent.steps && (
          <div className="mt-3 space-y-1">
            {agent.steps.map(step => (
              <div key={step.id} className="flex items-center gap-2 text-sm">
                {step.status === 'completed' && (
                  <Check className="w-3 h-3 text-green-500" />
                )}
                {step.status === 'running' && (
                  <Loader2 className="w-3 h-3 animate-spin" />
                )}
                {step.status === 'pending' && (
                  <Circle className="w-3 h-3 text-muted-foreground" />
                )}
                <span className={cn(
                  step.status === 'completed' && "text-muted-foreground line-through",
                  step.status === 'running' && "text-foreground font-medium"
                )}>
                  {step.name}
                </span>
              </div>
            ))}
          </div>
        )}

        {/* 完成时间 */}
        {agent.status === 'completed' && agent.duration && (
          <div className="mt-3 text-xs text-muted-foreground">
            耗时: {(agent.duration / 1000).toFixed(1)}秒
          </div>
        )}
      </CardContent>
    </Card>
  );
}
```

---

### 阶段4：发布和下载

#### 3.4.1 布局设计（参考图1右侧）

**卡片网格布局**：
```
┌─────────────────────────────────┐
│  生成结果总览                    │
│  ┌───┬───┬───┬───┐              │
│  │85 │ 4 │12 │48h│              │
│  │分 │模块│组件│工时│             │
│  └───┴───┴───┴───┘              │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  发布选项（多选）                │
│                                  │
│  ☑ 发布为小程序（微信/支付宝）   │
│  ☑ 发布为安卓应用                │
│  ☑ 发布为鸿蒙应用                │
│  ☑ 发布为手机网页                │
│  ☑ 运营后台（默认选中）          │
│                                  │
│  [一键发布所有平台]              │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  快速操作                        │
│                                  │
│  [导出源代码]  [预览应用]        │
│  [生成新应用]  [查看文档]        │
└─────────────────────────────────┘
```

#### 3.4.2 发布选项组件

**技术实现**：
```tsx
// 组件路径：src/components/publish/multi-platform-publisher.tsx
const PLATFORMS = [
  {
    id: 'miniprogram',
    name: '小程序',
    description: '微信小程序 / 支付宝小程序',
    icon: Smartphone,
    defaultChecked: true
  },
  {
    id: 'android',
    name: '安卓应用',
    description: 'Android APK / Google Play',
    icon: Smartphone,
    defaultChecked: true
  },
  {
    id: 'harmonyos',
    name: '鸿蒙应用',
    description: 'HarmonyOS NEXT',
    icon: Smartphone,
    defaultChecked: true
  },
  {
    id: 'mobile-web',
    name: '手机网页',
    description: 'PWA / 响应式网页',
    icon: Globe,
    defaultChecked: true
  },
  {
    id: 'admin-panel',
    name: '运营后台',
    description: 'Web管理后台',
    icon: LayoutDashboard,
    defaultChecked: true  // 默认选中
  }
];

export function MultiPlatformPublisher({
  appSpecId
}: MultiPlatformPublisherProps) {
  const [selectedPlatforms, setSelectedPlatforms] = useState<string[]>(
    PLATFORMS.filter(p => p.defaultChecked).map(p => p.id)
  );
  const [isPublishing, setIsPublishing] = useState(false);
  const [publishResults, setPublishResults] = useState<PublishResult[]>([]);

  const togglePlatform = (platformId: string) => {
    setSelectedPlatforms(prev =>
      prev.includes(platformId)
        ? prev.filter(id => id !== platformId)
        : [...prev, platformId]
    );
  };

  const handlePublishAll = async () => {
    setIsPublishing(true);
    setPublishResults([]);

    try {
      // 并行发布所有选中的平台
      const promises = selectedPlatforms.map(platformId =>
        publishToPlatform(appSpecId, platformId)
      );

      const results = await Promise.allSettled(promises);

      const formattedResults = results.map((result, index) => {
        const platformId = selectedPlatforms[index];
        const platform = PLATFORMS.find(p => p.id === platformId)!;

        if (result.status === 'fulfilled') {
          return {
            platform: platform.name,
            success: true,
            url: result.value.url,
            message: '发布成功'
          };
        } else {
          return {
            platform: platform.name,
            success: false,
            error: result.reason?.message || '发布失败',
            message: '发布失败'
          };
        }
      });

      setPublishResults(formattedResults);
    } catch (error) {
      console.error('发布失败:', error);
    } finally {
      setIsPublishing(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* 发布选项 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Rocket className="w-5 h-5" />
            选择发布平台
          </CardTitle>
          <CardDescription>
            支持一键发布到多个平台（可多选）
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {PLATFORMS.map(platform => {
            const Icon = platform.icon;
            const isSelected = selectedPlatforms.includes(platform.id);

            return (
              <div
                key={platform.id}
                className={cn(
                  "flex items-center gap-3 p-4 rounded-lg border cursor-pointer transition-all",
                  isSelected
                    ? "border-primary bg-primary/5"
                    : "border-border hover:border-primary/50"
                )}
                onClick={() => togglePlatform(platform.id)}
              >
                <Checkbox
                  checked={isSelected}
                  onCheckedChange={() => togglePlatform(platform.id)}
                />
                <Icon className="w-5 h-5 text-primary" />
                <div className="flex-1">
                  <div className="font-medium">{platform.name}</div>
                  <div className="text-sm text-muted-foreground">
                    {platform.description}
                  </div>
                </div>
                {platform.defaultChecked && (
                  <Badge variant="secondary">推荐</Badge>
                )}
              </div>
            );
          })}
        </CardContent>
        <CardFooter>
          <Button
            className="w-full"
            size="lg"
            disabled={selectedPlatforms.length === 0 || isPublishing}
            onClick={handlePublishAll}
          >
            {isPublishing ? (
              <>
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                发布中...
              </>
            ) : (
              <>
                <Rocket className="w-4 h-4 mr-2" />
                一键发布 ({selectedPlatforms.length} 个平台)
              </>
            )}
          </Button>
        </CardFooter>
      </Card>

      {/* 发布结果 */}
      {publishResults.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>发布结果</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {publishResults.map((result, index) => (
              <div
                key={index}
                className="flex items-center justify-between p-3 bg-muted rounded-lg"
              >
                <div className="flex items-center gap-3">
                  {result.success ? (
                    <CheckCircle2 className="w-5 h-5 text-green-500" />
                  ) : (
                    <XCircle className="w-5 h-5 text-red-500" />
                  )}
                  <div>
                    <p className="font-medium">{result.platform}</p>
                    <p className="text-sm text-muted-foreground">
                      {result.message}
                    </p>
                  </div>
                </div>
                {result.success && result.url && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => window.open(result.url, '_blank')}
                  >
                    <ExternalLink className="w-4 h-4 mr-1" />
                    访问
                  </Button>
                )}
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
```

---

## 4. 技术实现方案

### 4.1 新建页面路由

**创建新的统一入口页面**：
```
src/app/studio/page.tsx (新建)
```

**替代现有的**：
- `src/app/create/page.tsx`（保留作为后备）
- `src/app/wizard/[id]/page.tsx`（保留作为子路由）

### 4.2 状态管理

**使用Zustand管理全局状态**：
```typescript
// src/store/studio-store.ts
interface StudioState {
  // 阶段1：意图识别
  requirement: string;
  intent: IntentClassificationResult | null;
  selectedTemplate: IndustryTemplate | null;

  // 阶段2：风格选择
  selectedStyle: DesignStyle | null;
  selectedAICapabilities: string[];
  previewUrl: string | null;

  // 阶段3：执行状态
  taskId: string | null;
  agents: AgentExecutionStatus[];
  generatedCode: GeneratedFile[];

  // 阶段4：发布状态
  publishResults: PublishResult[];

  // 当前阶段
  currentStage: 'intent' | 'design' | 'execute' | 'publish';

  // Actions
  setRequirement: (req: string) => void;
  setIntent: (intent: IntentClassificationResult) => void;
  selectTemplate: (template: IndustryTemplate | null) => void;
  selectStyle: (style: DesignStyle) => void;
  selectAICapability: (capabilityId: string) => void;
  confirmDesign: () => void;
  // ... 其他actions
}
```

### 4.3 API集成点

**新增/修改的API端点**：

1. **意图识别API**（已有）：
   ```
   POST /api/v1/intent/classify
   Body: { userRequirement: string }
   Response: IntentClassificationResult
   ```

2. **模板匹配API**（新增）：
   ```
   POST /api/v1/templates/match
   Body: { keywords: string[], intent: string }
   Response: IndustryTemplate[]
   ```

3. **OpenLovable预览生成API**（已有，需增强）：
   ```
   POST /api/v1/openlovable/generate
   Body: {
     userRequirement: string,
     styleId: string,
     selectedTemplate?: string
   }
   Response: { previewUrl: string, sandboxId: string }
   ```

4. **全栈生成API**（已有）：
   ```
   POST /api/v1/generate/full
   Body: {
     userRequirement: string,
     styleId: string,
     aiCapabilities: string[],
     config: GenerationConfig
   }
   Response: { taskId: string }
   ```

5. **多平台发布API**（新增）：
   ```
   POST /api/v1/publish/multi-platform
   Body: {
     appSpecId: string,
     platforms: string[]
   }
   Response: { jobs: PublishJob[] }
   ```

### 4.4 组件层次结构

```
StudioPage (主页面)
├── Stage1: IntentRecognitionStage
│   ├── ChatInterface (对话界面)
│   ├── TemplatePreview (预览区域)
│   └── IntentInfoPanel (信息面板)
│
├── Stage2: DesignSelectionStage
│   ├── StyleSelector (风格选择)
│   ├── InteractivePreview (实时预览)
│   └── AICapabilitySelector (AI能力选择)
│
├── Stage3: ExecutionStage
│   ├── RequirementSummary (配置总结)
│   └── RealtimeExecutionPanel (实时进度)
│
└── Stage4: PublishStage
    ├── ResultOverview (结果总览)
    ├── MultiPlatformPublisher (发布选项)
    └── QuickActions (快速操作)
```

---

## 5. 组件架构设计

### 5.1 核心组件清单

| 组件名称 | 文件路径 | 职责 | 状态 |
|---------|---------|------|------|
| StudioPage | `src/app/studio/page.tsx` | 主页面，管理四阶段流程 | 🆕 新建 |
| ChatInterface | `src/components/chat/chat-interface.tsx` | 对话式交互界面 | 🆕 新建 |
| MessageBubble | `src/components/chat/message-bubble.tsx` | 消息气泡组件 | 🆕 新建 |
| TemplateCard | `src/components/chat/template-card.tsx` | 模板推荐卡片 | 🆕 新建 |
| TemplatePreview | `src/components/preview/template-preview.tsx` | 模板预览组件 | 🆕 新建 |
| IntentInfoPanel | `src/components/info/intent-info-panel.tsx` | 意图信息面板 | 🆕 新建 |
| StyleSelector | `src/components/design/style-selector.tsx` | 风格选择器 | 🆕 新建 |
| InteractivePreview | `src/components/preview/interactive-preview.tsx` | 交互预览组件 | 🆕 新建 |
| AICapabilitySelector | `src/components/ai/ai-capability-selector.tsx` | AI能力选择器 | 🆕 新建 |
| AICapabilityCard | `src/components/ai/ai-capability-card.tsx` | AI能力卡片 | 🆕 新建 |
| RequirementSummary | `src/components/summary/requirement-summary.tsx` | 需求总结组件 | 🆕 新建 |
| RealtimeExecutionPanel | `src/components/execution/realtime-execution-panel.tsx` | 实时执行面板 | 🆕 新建 |
| AgentProgressCard | `src/components/execution/agent-progress-card.tsx` | Agent进度卡片 | 🆕 新建 |
| CodePreview | `src/components/execution/code-preview.tsx` | 代码预览组件 | 🆕 新建 |
| MultiPlatformPublisher | `src/components/publish/multi-platform-publisher.tsx` | 多平台发布器 | 🆕 新建 |
| PublishResult | `src/components/publish/publish-result.tsx` | 发布结果展示 | 🆕 新建 |

### 5.2 Hooks清单

| Hook名称 | 文件路径 | 职责 | 状态 |
|---------|---------|------|------|
| useStudioFlow | `src/hooks/use-studio-flow.ts` | 管理整体流程状态 | 🆕 新建 |
| useIntentClassification | `src/hooks/use-intent-classification.ts` | 意图识别Hook | 🆕 新建 |
| useTemplateMatch | `src/hooks/use-template-match.ts` | 模板匹配Hook | 🆕 新建 |
| useOpenLovablePreview | `src/hooks/use-openlovable-preview.ts` | OpenLovable预览Hook | 🆕 新建 |
| useMultiPlatformPublish | `src/hooks/use-multi-platform-publish.ts` | 多平台发布Hook | 🆕 新建 |

---

## 6. 实施路线图

### 6.1 Phase划分（遵循前端优先原则）

**Phase 1：页面和组件开发（前端优先）⭐**
- 时间：2-3天
- 产出：完整的四阶段UI + Mock数据验证
- 包含：
  - 创建`StudioPage`主页面
  - 实现四阶段布局切换
  - 创建所有核心组件
  - Mock数据验证交互逻辑
  - **输出API接口契约文档**

**Phase 2：后端API开发**
- 时间：2天
- 前置依赖：Phase 1完成
- 产出：符合前端契约的后端API
- 包含：
  - 模板匹配API（`/api/v1/templates/match`）
  - OpenLovable增强API
  - 多平台发布API（`/api/v1/publish/multi-platform`）
  - 单元测试和API文档

**Phase 3：前后端联调**
- 时间：1天
- 前置依赖：Phase 2完成
- 产出：完整功能验证
  - 移除Mock数据
  - 真实API对接
  - E2E测试编写
  - 性能和错误处理优化

**Phase 4：优化和上线**
- 时间：1天
- 产出：生产就绪版本
- 包含：
  - UI细节打磨
  - 性能优化
  - 错误处理完善
  - 文档更新

### 6.2 详细任务清单

**Phase 1: 页面和组件开发（2-3天）**

- [ ] **Day 1：阶段1和阶段2布局**
  - [ ] 创建`StudioPage`主页面框架
  - [ ] 实现阶段切换逻辑
  - [ ] 创建`ChatInterface`组件
  - [ ] 创建`MessageBubble`组件
  - [ ] 创建`TemplateCard`组件
  - [ ] 创建`TemplatePreview`组件
  - [ ] 创建`IntentInfoPanel`组件
  - [ ] 创建`StyleSelector`组件
  - [ ] 创建`InteractivePreview`组件（含iframe）
  - [ ] Mock数据验证阶段1和2交互

- [ ] **Day 2：阶段3和阶段4布局**
  - [ ] 创建`AICapabilitySelector`组件
  - [ ] 创建`AICapabilityCard`组件
  - [ ] 创建`RequirementSummary`组件
  - [ ] 创建`RealtimeExecutionPanel`组件
  - [ ] 创建`AgentProgressCard`组件
  - [ ] 创建`CodePreview`组件
  - [ ] 创建`MultiPlatformPublisher`组件
  - [ ] 创建`PublishResult`组件
  - [ ] Mock数据验证阶段3和4交互

- [ ] **Day 3：状态管理和Hooks**
  - [ ] 实现`studio-store.ts`（Zustand）
  - [ ] 创建`useStudioFlow` Hook
  - [ ] 创建`useIntentClassification` Hook
  - [ ] 创建`useTemplateMatch` Hook
  - [ ] 创建`useOpenLovablePreview` Hook
  - [ ] 创建`useMultiPlatformPublish` Hook
  - [ ] 集成WebSocket（复用现有`useGenerationWebSocket`）
  - [ ] **输出API接口契约文档**
  - [ ] 完整流程Mock数据E2E测试

**Phase 2: 后端API开发（2天）**

- [ ] **Day 1：模板和预览API**
  - [ ] 实现`/api/v1/templates/match`端点
  - [ ] 创建`IndustryTemplateService`
  - [ ] 创建`IndustryTemplateRepository`
  - [ ] 初始化40+行业模板数据
  - [ ] 增强`OpenLovableService`支持styleId参数
  - [ ] 单元测试和集成测试

- [ ] **Day 2：发布和完善**
  - [ ] 实现`/api/v1/publish/multi-platform`端点
  - [ ] 创建`MultiPlatformPublishService`
  - [ ] 实现各平台发布逻辑（小程序/安卓/鸿蒙/Web）
  - [ ] API文档更新（Swagger）
  - [ ] 单元测试和集成测试

**Phase 3: 前后端联调（1天）**

- [ ] 移除所有Mock数据
- [ ] 对接真实API
- [ ] 错误处理和边界情况测试
- [ ] E2E测试编写（Playwright）
- [ ] 性能测试和优化
- [ ] 跨浏览器兼容性测试

**Phase 4: 优化和上线（1天）**

- [ ] UI细节打磨（动画、过渡效果）
- [ ] 响应式布局优化
- [ ] 加载状态和骨架屏优化
- [ ] 错误提示和用户引导优化
- [ ] 文档更新（CLAUDE.md、用户手册）
- [ ] 部署到测试环境
- [ ] 用户验收测试

---

## 7. 质量标准

### 7.1 提交前检查清单

**编译和类型检查**：
- [ ] `pnpm tsc --noEmit` 通过（0 errors）
- [ ] `pnpm lint` 通过（0 errors）
- [ ] `pnpm build` 成功

**功能验证**：
- [ ] 四阶段流程完整可走通
- [ ] 所有交互按钮功能正常
- [ ] 实时预览正常显示
- [ ] WebSocket连接稳定

**测试覆盖**：
- [ ] 单元测试覆盖率≥85%
- [ ] 所有E2E测试通过
- [ ] 跨浏览器测试通过

### 7.2 用户体验标准

| 指标 | 目标值 | 验证方法 |
|-----|-------|---------|
| 意图识别响应时间 | <3秒 | Performance API测量 |
| 风格预览生成时间 | <10秒 | 用户感知时间测量 |
| 页面切换流畅度 | 60fps | Chrome DevTools测量 |
| 移动端适配 | 完美支持 | 真机测试 |
| 无障碍支持 | WCAG AA | Lighthouse检测 |

---

## 8. 风险和应对

### 8.1 技术风险

**风险1：OpenLovable预览生成失败**
- **应对**：添加重试机制 + 降级到静态预览图

**风险2：WebSocket连接不稳定**
- **应对**：自动重连 + 降级到轮询模式

**风险3：多平台发布失败**
- **应对**：部分失败允许继续 + 详细错误日志

### 8.2 性能风险

**风险1：四阶段页面加载慢**
- **应对**：代码分割 + 懒加载 + 预加载关键资源

**风险2：实时代码预览占用内存**
- **应对**：虚拟滚动 + 按需加载 + 限制显示行数

---

## 9. 成功指标

### 9.1 定量指标

| 指标 | V1.0基线 | V2.0目标 | 提升 |
|-----|---------|---------|------|
| 用户完成率 | 45% | 75% | +67% |
| 平均生成时间 | 8分钟 | 5分钟 | -37.5% |
| 返工率 | 35% | 17.5% | -50% |
| 用户满意度 | 3.8/5 | 4.7/5 | +24% |
| NPS评分 | 32 | 60 | +87.5% |

### 9.2 定性指标

- **用户反馈**："像聊天一样简单"
- **产品价值**："看到预览后才敢确认"
- **竞争优势**："比其他低代码平台更智能"

---

**Made with ❤️ by Ingenio Team**

> 本方案基于用户提供的两张参考图设计，遵循"让用户做选择题，而非填空题"的核心理念，
> 旨在将Ingenio打造成业内最智能、最易用的AI应用生成平台。
