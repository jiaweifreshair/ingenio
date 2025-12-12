# SuperDesign - AI设计方案选择页面

## 功能概述

SuperDesign是Ingenio平台的AI设计生成模块，提供以下核心功能：

1. **AI多方案生成**：并行生成7个不同风格的UI设计方案
2. **方案预览**：卡片式展示每个方案的视觉风格和特点
3. **详情查看**：弹窗展示完整的设计说明、颜色方案、布局特点
4. **方案对比**：左右并排对比2个方案的差异
5. **方案选择**：选择最终使用的方案并跳转到代码生成

## 路由说明

### 主页面
- **路由**: `/superdesign/[appId]`
- **参数**: `appId` - 应用ID/任务ID
- **功能**: 展示AI生成的7个设计方案

### 跳转流程
```
/wizard/[appId] (需求填写)
  ↓
/superdesign/[appId] (设计选择) ← 当前页面
  ↓
/wizard/[appId]?design=[variantId] (代码生成)
```

## 7种设计风格

### 传统风格（A/B/C）
- **方案A：现代极简** - Material Design 3，大留白，卡片式布局
- **方案B：活力时尚** - 渐变色彩，网格布局，圆角设计
- **方案C：经典专业** - 信息密集，列表布局，商务配色

### 创新风格（D/E/F/G）
- **方案D：未来科技** - 赛博朋克，霓虹色，毛玻璃效果
- **方案E：沉浸式3D** - 卡片翻转，视差滚动，立体阴影
- **方案F：游戏化** - 关卡系统，徽章奖励，粒子特效
- **方案G：自然流动** - 有机曲线，流体动画，波浪效果

## 组件结构

```
frontend/src/
├── app/superdesign/[appId]/
│   ├── page.tsx                   # 主页面
│   └── README.md                  # 本文档
├── components/superdesign/
│   ├── design-card.tsx            # 方案卡片
│   ├── design-detail-panel.tsx    # 详情面板
│   ├── design-compare-view.tsx    # 对比视图
│   └── index.ts                   # 组件导出
├── types/design.ts                # 类型定义
└── lib/api/superdesign.ts         # API客户端
```

## API接口

### 生成设计方案
```typescript
POST /v1/superdesign/generate
Request:
{
  "taskId": "uuid",
  "userPrompt": "构建图书管理系统",
  "entities": [
    {
      "name": "book",
      "displayName": "图书",
      "primaryFields": ["title", "author"],
      "viewType": "list"
    }
  ],
  "targetPlatform": "android",
  "uiFramework": "compose_multiplatform",
  "colorScheme": "light",
  "includeAssets": true
}

Response:
{
  "code": 200,
  "success": true,
  "data": [
    {
      "variantId": "A",
      "style": "现代极简",
      "styleKeywords": ["现代", "极简", "卡片式"],
      "code": "...",
      "preview": "...",
      "colorTheme": {
        "primaryColor": "#6200EE",
        "secondaryColor": "#03DAC6",
        ...
      },
      "layoutType": "card",
      "componentLibrary": "kuiklyui",
      "features": ["大留白", "流畅动效"],
      "generationTimeMs": 5000
    },
    // ... 其他6个方案
  ]
}
```

### 获取示例请求
```typescript
GET /v1/superdesign/example
Response:
{
  "code": 200,
  "success": true,
  "data": {
    "userPrompt": "构建一个图书管理系统...",
    "entities": [...],
    ...
  }
}
```

## 使用示例

### 1. 访问页面
```bash
# 开发环境
http://localhost:3000/superdesign/550e8400-e29b-41d4-a716-446655440000

# 生产环境
https://ingenio.dev/superdesign/[appId]
```

### 2. 查看方案详情
点击任意方案卡片，将弹出详情面板，展示完整信息：
- UI预览图
- 设计说明
- 风格关键词
- 颜色方案（5种颜色）
- 布局特点
- 设计特点列表
- 技术信息

### 3. 对比方案
点击"方案对比"按钮，进入全屏对比视图：
- 左右并排展示2个方案
- 支持切换对比的方案
- 显示多个对比维度：视觉风格、颜色方案、布局特点等

### 4. 选择方案
点击"使用此方案"按钮：
1. 弹出确认对话框
2. 确认后跳转到代码生成页面
3. URL携带design参数：`/wizard/[appId]?design=A`

### 5. 重新生成
点击"重新生成"按钮：
1. 弹出确认对话框
2. 确认后重新调用API生成7个新方案
3. 生成时间：5-10秒（并行执行）

## 响应式布局

### 桌面端（≥1024px）
- 3列网格布局
- 卡片宽度自适应
- 详情面板最大宽度1200px

### 平板端（768px-1023px）
- 2列网格布局
- 详情面板最大宽度900px

### 移动端（<768px）
- 1列布局
- 详情面板全屏
- 对比视图垂直排列

## 状态管理

页面使用React Hooks管理以下状态：
- `designs` - 设计方案列表（7个）
- `loading` - 加载状态
- `error` - 错误信息
- `selectedDesign` - 当前选中的方案
- `detailDesign` - 详情面板显示的方案
- `showDetail` - 是否显示详情面板
- `showCompare` - 是否显示对比视图

## 错误处理

### API调用失败
显示错误页面，提供"重试"按钮

### 后端服务未运行
错误提示包含：
1. 后端服务地址
2. 可能的原因
3. 解决方案

### 生成超时
后端设置超时时间为60秒，超时后返回错误

## 性能优化

### 并行生成
- 使用CompletableFuture并行调用7次AI API
- 总耗时约等于单次最慢的请求时间（5-10秒）

### 图片懒加载
- 使用Next.js Image组件
- 自动优化图片格式和尺寸
- 懒加载预览图

### 代码分割
- 详情面板和对比视图按需加载
- 减少初始包大小

## 开发调试

### 本地开发
```bash
# 启动前端
cd frontend
pnpm dev

# 启动后端
cd backend
mvn spring-boot:run
```

### 环境变量
```bash
# 前端 .env.local
NEXT_PUBLIC_API_BASE_URL=http://localhost:3000
```

### Mock数据（仅开发阶段）
如果后端未启动，可以临时使用Mock数据：
```typescript
// 在page.tsx中注释掉API调用
// const result = await generateDesigns(mockRequest);

// 使用Mock数据
const mockDesigns: DesignScheme[] = [...];
setDesigns(mockDesigns);
```

注意：**生产环境严格禁止Mock数据！**

## 测试

### 单元测试
```bash
cd frontend
pnpm test components/superdesign
```

### E2E测试
```bash
cd frontend
pnpm test:e2e superdesign
```

### 后端集成测试
```bash
cd backend
mvn test -Dtest=SuperDesignE2ETest
```

## 已知问题

1. **预览图生成**：当前版本预览图字段为空，需要集成截图服务
2. **方案持久化**：设计方案暂未保存到数据库，刷新页面会重新生成
3. **自定义编辑**：自定义编辑器功能待实现

## 后续优化

- [ ] 集成UI截图服务生成预览图
- [ ] 实现设计方案持久化存储
- [ ] 添加方案收藏功能
- [ ] 实现在线自定义编辑器
- [ ] 支持方案分享和导出
- [ ] 添加方案评分和反馈机制

## 参考文档

- [SuperDesign API文档](../../../backend/docs/api/SUPERDESIGN_API.md)
- [设计类型定义](../../types/design.ts)
- [Ingenio项目文档](../../../docs/)
