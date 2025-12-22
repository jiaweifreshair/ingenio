# AI能力选择器使用指南

> **文档版本**: v1.0.0
> **创建时间**: 2025-11-11
> **适用场景**: 在创建应用时让用户选择需要的AI能力

---

## 快速开始

### 1. 导入组件

```tsx
import { AICapabilityPicker } from '@/components/ai/ai-capability-picker';
import { AICapabilityType } from '@/types/ai-capability';
```

### 2. 基础用法

```tsx
'use client';

import { useState } from 'react';
import { AICapabilityPicker } from '@/components/ai/ai-capability-picker';
import { AICapabilityType } from '@/types/ai-capability';

export default function CreateAppPage() {
  const [selectedCapabilities, setSelectedCapabilities] = useState<AICapabilityType[]>([]);

  return (
    <div className="container mx-auto p-6">
      <AICapabilityPicker
        selectedCapabilities={selectedCapabilities}
        onSelectionChange={setSelectedCapabilities}
      />
    </div>
  );
}
```

### 3. 带智能推荐的用法

```tsx
'use client';

import { useState } from 'react';
import { AICapabilityPicker } from '@/components/ai/ai-capability-picker';
import { AICapabilityType } from '@/types/ai-capability';

export default function CreateAppPage() {
  const [userRequirement, setUserRequirement] = useState('');
  const [selectedCapabilities, setSelectedCapabilities] = useState<AICapabilityType[]>([]);

  return (
    <div className="container mx-auto p-6 space-y-6">
      {/* 需求输入框 */}
      <div>
        <label className="block text-sm font-medium mb-2">
          描述你的应用需求
        </label>
        <textarea
          className="w-full p-3 border rounded-lg"
          rows={4}
          placeholder="例如：我想做一个带语音输入和视频分析的智能客服系统"
          value={userRequirement}
          onChange={(e) => setUserRequirement(e.target.value)}
        />
      </div>

      {/* AI能力选择器（含智能推荐） */}
      <AICapabilityPicker
        selectedCapabilities={selectedCapabilities}
        onSelectionChange={setSelectedCapabilities}
        userRequirement={userRequirement}
        showRecommendations={true}
      />

      {/* 提交按钮 */}
      <button
        className="px-6 py-2 bg-purple-500 text-white rounded-lg"
        disabled={selectedCapabilities.length === 0}
      >
        下一步
      </button>
    </div>
  );
}
```

---

## Props详解

### AICapabilityPickerProps

| 属性名称 | 类型 | 必填 | 默认值 | 说明 |
|---------|------|------|--------|------|
| **selectedCapabilities** | `AICapabilityType[]` | ✅ | - | 已选中的AI能力类型数组 |
| **onSelectionChange** | `(capabilities: AICapabilityType[]) => void` | ✅ | - | 选择变化回调函数 |
| **userRequirement** | `string` | ❌ | - | 用户需求文本（用于智能推荐） |
| **maxSelection** | `number` | ❌ | `10` | 最大可选数量 |
| **disabled** | `boolean` | ❌ | `false` | 是否禁用所有交互 |
| **showCostEstimate** | `boolean` | ❌ | `true` | 是否显示成本估算 |
| **showRecommendations** | `boolean` | ❌ | `true` | 是否显示智能推荐 |
| **className** | `string` | ❌ | - | 自定义CSS类名 |

---

## 高级用法

### 1. 限制最大选择数量

```tsx
<AICapabilityPicker
  selectedCapabilities={selectedCapabilities}
  onSelectionChange={setSelectedCapabilities}
  maxSelection={5} // 最多选5个
/>
```

### 2. 禁用状态

```tsx
<AICapabilityPicker
  selectedCapabilities={selectedCapabilities}
  onSelectionChange={setSelectedCapabilities}
  disabled={isSubmitting} // 提交时禁用
/>
```

### 3. 隐藏成本估算

```tsx
<AICapabilityPicker
  selectedCapabilities={selectedCapabilities}
  onSelectionChange={setSelectedCapabilities}
  showCostEstimate={false} // 不显示成本
/>
```

### 4. 获取选中AI能力的详细信息

```tsx
import { getAICapability } from '@/data/ai-capabilities';

// 获取选中AI能力的详细信息
const selectedDetails = selectedCapabilities
  .map(type => getAICapability(type))
  .filter(c => c !== undefined);

console.log(selectedDetails);
// [
//   { type: 'chatbot', name: '对话机器人', ... },
//   { type: 'video_analysis', name: '视频分析', ... }
// ]
```

### 5. 计算总成本和工期

```tsx
import { calculateStats } from '@/data/ai-capabilities';

const stats = calculateStats(selectedCapabilities);

console.log(stats);
// {
//   total: 19,
//   selected: 3,
//   totalCost: 79.7,
//   totalDays: 12,
//   avgComplexity: 2.0
// }
```

---

## 智能推荐规则

### 推荐关键词映射表

| 用户输入 | 推荐AI能力 | 权重 |
|---------|----------|------|
| "聊天"、"对话"、"客服" | 对话机器人（CHATBOT） | 1.0 |
| "视频"、"直播"、"监控" | 视频分析（VIDEO_ANALYSIS） | 1.0 |
| "文档"、"PDF"、"OCR" | OCR文档识别（OCR_DOCUMENT） | 1.0 |
| "推荐"、"个性化" | 超个性化推荐（HYPER_PERSONALIZATION） | 0.9 |
| "分析"、"预测"、"趋势" | 预测分析（PREDICTIVE_ANALYTICS） | 0.8 |
| "搜索"、"查询" | 智能搜索（SMART_SEARCH） | 0.8 |
| "知识"、"图谱"、"关系" | 知识图谱（KNOWLEDGE_GRAPH） | 0.7 |

### 推荐算法逻辑

1. **分词和匹配**：将用户需求文本分词，匹配关键词表
2. **权重计算**：匹配数量 × 关键词权重 = 推荐分数
3. **排序和截取**：按分数降序排序，取前5个作为推荐项

### 示例

```tsx
// 用户输入: "我想做一个带视频分析和智能搜索的应用"
// 推荐结果:
// 1. VIDEO_ANALYSIS (权重1.0, 匹配"视频"、"分析")
// 2. SMART_SEARCH (权重0.8, 匹配"搜索")
// 3. PREDICTIVE_ANALYTICS (权重0.8, 匹配"分析")
```

---

## 筛选功能

### 1. 类别筛选

支持按以下类别筛选：
- **全部** - 显示所有19种AI能力
- **对话** - 对话机器人、问答系统、检索增强生成
- **视觉** - 图像识别、视频分析、内容审核
- **文档** - OCR文档识别、智能翻译
- **分析** - 情感分析、预测分析、知识图谱、推荐系统、智能搜索
- **生成** - 文本生成、代码生成、多模态融合
- **音频** - 语音识别
- **实时** - 实时流处理

### 2. 搜索功能

支持搜索以下字段：
- AI能力名称（中文、英文）
- 描述文本
- 使用场景

**搜索匹配规则**：
- 不区分大小写
- 支持部分匹配
- 支持多个关键词（空格分隔）

**示例**：
```
搜索 "视频" → 匹配到 "视频分析"
搜索 "chatbot" → 匹配到 "对话机器人"
搜索 "客服" → 匹配到 "对话机器人"（使用场景匹配）
```

---

## 19种AI能力详解

### 对话交互类（3种）

| AI能力 | 成本 | 复杂度 | 工期 | 使用场景 |
|-------|------|--------|------|---------|
| **对话机器人** | $1.7/月 | 简单 | 2天 | 客服机器人、AI助手 |
| **问答系统** | $1.2/月 | 简单 | 2天 | FAQ系统、知识库问答 |
| **检索增强生成** | $3.5/月 | 中等 | 4天 | 企业知识库、文档问答 |

### 视觉识别类（2种）

| AI能力 | 成本 | 复杂度 | 工期 | 使用场景 |
|-------|------|--------|------|---------|
| **图像识别** | $15.0/月 | 中等 | 3天 | 智能相册、商品识别 |
| **视频分析** | $50.0/月 | 中等 | 5天 | 视频监控、内容审核 |

### 文档处理类（2种）

| AI能力 | 成本 | 复杂度 | 工期 | 使用场景 |
|-------|------|--------|------|---------|
| **OCR文档识别** | $12.0/月 | 中等 | 4天 | 发票识别、合同解析 |
| **智能翻译** | $2.0/月 | 简单 | 2天 | 多语言应用、跨境电商 |

### 数据分析类（5种）

| AI能力 | 成本 | 复杂度 | 工期 | 使用场景 |
|-------|------|--------|------|---------|
| **情感分析** | $1.0/月 | 简单 | 2天 | 舆情监测、用户反馈 |
| **预测分析** | $35.0/月 | 复杂 | 7天 | 销售预测、风险评估 |
| **知识图谱** | $28.0/月 | 复杂 | 8天 | 企业知识管理、关系挖掘 |
| **推荐系统** | $5.0/月 | 中等 | 4天 | 电商推荐、内容推荐 |
| **智能搜索** | $4.5/月 | 中等 | 4天 | 电商搜索、知识库搜索 |

### 内容生成类（3种）

| AI能力 | 成本 | 复杂度 | 工期 | 使用场景 |
|-------|------|--------|------|---------|
| **文本生成** | $2.5/月 | 简单 | 2天 | 内容营销、文案生成 |
| **代码生成** | $8.0/月 | 中等 | 4天 | 代码助手、IDE插件 |
| **多模态融合** | $45.0/月 | 复杂 | 9天 | 内容创作、智能助手 |

### 其他类（4种）

| AI能力 | 成本 | 复杂度 | 工期 | 使用场景 |
|-------|------|--------|------|---------|
| **超个性化推荐** | $38.0/月 | 复杂 | 8天 | 高端电商、内容平台 |
| **语音识别** | $3.0/月 | 中等 | 3天 | 语音输入、会议记录 |
| **实时流处理** | $42.0/月 | 复杂 | 9天 | 实时监控、实时推荐 |
| **内容审核** | $6.0/月 | 中等 | 3天 | 社交平台、UGC社区 |

---

## 样式定制

### 自定义样式

```tsx
<AICapabilityPicker
  className="my-custom-picker"
  selectedCapabilities={selectedCapabilities}
  onSelectionChange={setSelectedCapabilities}
/>
```

```css
/* 自定义样式 */
.my-custom-picker {
  background: linear-gradient(to bottom, #f3e8ff, #ffffff);
  padding: 2rem;
  border-radius: 1rem;
}
```

---

## 最佳实践

### 1. 需求引导

在AI能力选择器上方添加需求输入框，引导用户描述需求：

```tsx
<div className="space-y-6">
  {/* 需求引导 */}
  <div className="bg-purple-50 p-6 rounded-lg border border-purple-200">
    <h3 className="text-lg font-semibold mb-2">
      💡 描述你的应用需求
    </h3>
    <p className="text-sm text-muted-foreground mb-4">
      告诉我们你想做什么，我们会智能推荐适合的AI能力
    </p>
    <textarea
      className="w-full p-3 border rounded-lg"
      rows={4}
      placeholder="例如：我想做一个智能客服系统，支持语音输入和多语言翻译"
      value={userRequirement}
      onChange={(e) => setUserRequirement(e.target.value)}
    />
  </div>

  {/* AI能力选择器 */}
  <AICapabilityPicker
    selectedCapabilities={selectedCapabilities}
    onSelectionChange={setSelectedCapabilities}
    userRequirement={userRequirement}
  />
</div>
```

### 2. 验证和提示

确保用户至少选择一个AI能力：

```tsx
const handleNext = () => {
  if (selectedCapabilities.length === 0) {
    alert('请至少选择一个AI能力');
    return;
  }

  // 继续下一步
  navigate('/configure');
};
```

### 3. 保存和恢复

将用户的选择保存到localStorage或后端：

```tsx
// 保存到localStorage
useEffect(() => {
  if (selectedCapabilities.length > 0) {
    localStorage.setItem(
      'selected-ai-capabilities',
      JSON.stringify(selectedCapabilities)
    );
  }
}, [selectedCapabilities]);

// 从localStorage恢复
useEffect(() => {
  const saved = localStorage.getItem('selected-ai-capabilities');
  if (saved) {
    setSelectedCapabilities(JSON.parse(saved));
  }
}, []);
```

---

## 常见问题

### Q1: 如何自定义推荐算法？

修改 `ai-capability-picker.tsx` 中的 `RECOMMENDATION_KEYWORDS_MAP` 数组：

```tsx
const RECOMMENDATION_KEYWORDS_MAP = [
  {
    keywords: ['自定义关键词1', '自定义关键词2'],
    capabilityType: AICapabilityType.CHATBOT,
    weight: 1.0,
  },
  // ... 添加更多规则
];
```

### Q2: 如何添加新的AI能力？

修改 `frontend/src/data/ai-capabilities.ts` 中的 `AI_CAPABILITIES` 数组：

```tsx
export const AI_CAPABILITIES: AICapability[] = [
  // ... 现有能力
  {
    type: AICapabilityType.NEW_CAPABILITY,
    name: '新AI能力',
    nameEn: 'New AI Capability',
    description: '描述...',
    // ... 其他字段
  },
];
```

### Q3: 如何集成到向导页面？

参考 `frontend/src/app/wizard/[id]/page.tsx` 集成：

```tsx
import { AICapabilityPicker } from '@/components/ai/ai-capability-picker';

// 在ConfigurationPanel中添加
<AICapabilityPicker
  selectedCapabilities={config.aiCapabilities}
  onSelectionChange={(capabilities) =>
    onConfigChange({ ...config, aiCapabilities: capabilities })
  }
  userRequirement={config.requirement}
/>
```

---

## 性能优化建议

### 1. 虚拟滚动

如果AI能力数量超过50个，建议使用虚拟滚动：

```tsx
import { FixedSizeGrid } from 'react-window';

<FixedSizeGrid
  columnCount={3}
  columnWidth={320}
  height={600}
  rowCount={Math.ceil(filteredCapabilities.length / 3)}
  rowHeight={280}
  width={1000}
>
  {({ columnIndex, rowIndex, style }) => {
    const index = rowIndex * 3 + columnIndex;
    const capability = filteredCapabilities[index];
    return capability ? (
      <div style={style}>
        <AICapabilityCard capability={capability} {...} />
      </div>
    ) : null;
  }}
</FixedSizeGrid>
```

### 2. 搜索防抖

使用 `useDebouncedValue` 优化搜索性能：

```tsx
import { useDebouncedValue } from '@/hooks/use-debounced-value';

const debouncedQuery = useDebouncedValue(searchQuery, 300);
```

---

## 相关资源

- [设计文档](./AI_CAPABILITY_PICKER_DESIGN.md)
- [TypeScript类型定义](/Users/apus/Documents/UGit/Ingenio/frontend/src/types/ai-capability.ts)
- [AI能力数据](/Users/apus/Documents/UGit/Ingenio/frontend/src/data/ai-capabilities.ts)
- [主组件代码](/Users/apus/Documents/UGit/Ingenio/frontend/src/components/ai/ai-capability-picker.tsx)

---

**Made with ❤️ by Ingenio Team**
