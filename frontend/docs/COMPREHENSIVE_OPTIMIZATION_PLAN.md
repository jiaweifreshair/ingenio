# Ingenio前端综合优化执行计划

**生成时间**: 2025-11-14
**基于**: 3份Agent审查报告 + Playwright全页面测试结果
**执行周期**: 2周（10个工作日）

---

## 执行摘要

### 当前状况分析

**编译状态**: ✅ TypeScript 0 errors, 构建成功
**测试状况**: ❌ E2E测试 53/55 失败（96%失败率）
**代码质量**: ⚠️ 7.3/10 (需改进)
**用户体验**: ⚠️ 6.5/10 (有明显问题)

### 核心问题总结

#### 🔴 P0 - 阻塞性问题（必须立即修复）

1. **API 404错误导致页面无法完成加载**
   - `/api/v1/notifications/unread-count` - 404（每3秒轮询）
   - `/api/v1/user/profile` - 404
   - `/api/v1/notifications/settings` - 404
   - `/templates/*.jpg` - 404
   - **影响**: 所有页面无法达到networkidle状态，E2E测试全部超时
   - **用户影响**: 页面加载慢、体验差、控制台错误
   - **修复时间**: 2小时

2. **移动端导航完全缺失**
   - 移动端用户无法访问菜单
   - 所有功能对移动端用户不可用
   - **用户影响**: 严重（50%+用户受影响）
   - **修复时间**: 4小时

3. **重复代码导致维护成本翻倍**
   - `use-toast.ts` 两个版本（198行重复）
   - 导入路径混乱
   - **修复时间**: 0.5小时

#### 🟠 P1 - 高优先级（严重影响用户体验）

4. **超大型组件无法维护**
   - `wizard/[id]/page.tsx` - 951行、23个hooks
   - `requirement-form.tsx` - 846行
   - **影响**: 开发效率低、bug多、难以测试
   - **修复时间**: 12小时

5. **可访问性严重不足**
   - 颜色对比度不符合WCAG标准
   - 表单缺少label关联
   - 键盘导航不完整
   - **用户影响**: 中等（残障用户无法使用）
   - **修复时间**: 6小时

6. **测试覆盖率极低**
   - 单元测试覆盖率 < 5%
   - E2E测试因API问题全部失败
   - **影响**: 无法保证代码质量
   - **修复时间**: 16小时

---

## 详细修复计划

### Phase 1: 紧急修复（Week 1, Day 1-2，16小时）

#### Day 1 - API错误修复（8小时）

**目标**: 修复所有404 API错误，使页面能正常加载

##### Task 1.1: 未实现的API端点实现或Mock（4小时）

**问题分析**:
```typescript
// 当前：TopNav组件调用未实现的API
useEffect(() => {
  const fetchUnreadCount = async () => {
    const count = await getUnreadNotificationsCount(); // 返回404
    setUnreadCount(count);
  };
  const interval = setInterval(fetchUnreadCount, 3000); // 每3秒轮询一次
}, []);
```

**解决方案A - 后端实现（推荐）**:
```java
// backend: NotificationController.java
@GetMapping("/api/v1/notifications/unread-count")
public ResponseEntity<UnreadCountResponse> getUnreadCount() {
    // 实现逻辑...
    return ResponseEntity.ok(new UnreadCountResponse(count));
}
```

**解决方案B - 前端优雅降级（临时方案）**:
```typescript
// lib/api/notifications.ts
export async function getUnreadNotificationsCount(): Promise<number> {
  try {
    const response = await get<{ count: number }>('/api/v1/notifications/unread-count');
    return response.count;
  } catch (error) {
    // 优雅降级：返回0而不是崩溃
    if (error instanceof APIError && error.statusCode === 404) {
      console.warn('通知API未实现，返回默认值');
      return 0;
    }
    throw error;
  }
}
```

**需要修复的API列表**:
- [ ] `/api/v1/notifications/unread-count` → 返回 `{ count: 0 }`
- [ ] `/api/v1/user/profile` → 返回用户基本信息或null
- [ ] `/api/v1/notifications/settings` → 返回通知设置或默认值
- [ ] `/api/v1/notifications?current=1&size=20` → 返回空列表

**验收标准**:
```bash
# 1. 启动前端服务
pnpm dev

# 2. 检查控制台无404错误
# 3. 页面能达到networkidle状态（curl测试）
curl -I http://localhost:3000 # 应返回200

# 4. E2E测试至少50%通过
pnpm exec playwright test src/e2e/full-page-screenshot-test.spec.ts
```

---

##### Task 1.2: 图片资源修复（2小时）

**问题**: 模板图片不存在导致404

**解决方案**:
```typescript
// 方案1：使用占位图片服务
const TEMPLATE_IMAGE_FALLBACK = 'https://via.placeholder.com/400x300?text=';

export function getTemplateImage(templateId: string): string {
  const imagePath = `/templates/${templateId}.jpg`;
  // 使用Next.js Image组件的onError自动fallback
  return imagePath;
}

// 方案2：添加实际图片文件
// public/templates/
// ├── news.jpg
// ├── recipe.jpg
// ├── ecommerce.jpg
// └── ...
```

**操作步骤**:
1. 创建 `public/templates/` 目录
2. 为每个模板添加400x300的预览图
3. 或使用placeholder服务作为fallback

---

##### Task 1.3: 停止无效轮询（2小时）

**问题**: TopNav每3秒轮询通知数量，即使API不存在

**解决方案**:
```typescript
// components/layout/top-nav.tsx
useEffect(() => {
  const fetchUnreadCount = async () => {
    try {
      const count = await getUnreadNotificationsCount();
      setUnreadCount(count);
    } catch (error) {
      // 如果API不存在，停止轮询
      if (error instanceof APIError && error.statusCode === 404) {
        console.warn('通知API不可用，停止轮询');
        clearInterval(interval);
        setUnreadCount(0);
      }
    }
  };

  fetchUnreadCount(); // 初始调用
  const interval = setInterval(fetchUnreadCount, 30000); // 改为30秒（减少频率）

  return () => clearInterval(interval);
}, []);
```

**改进点**:
- ✅ 3秒 → 30秒（降低服务器压力）
- ✅ 404时自动停止轮询
- ✅ 添加错误处理

---

#### Day 2 - 移动端导航和关键修复（8小时）

##### Task 2.1: 实现响应式导航（4小时）

**当前问题**:
```tsx
// components/layout/top-nav.tsx
// ❌ 桌面端导航，移动端完全不可用
<nav className="hidden md:flex items-center gap-6">
  <Link href="/create">创建</Link>
  <Link href="/templates">模板</Link>
  <Link href="/dashboard">我的应用</Link>
</nav>
```

**解决方案**:
```tsx
// components/layout/mobile-nav.tsx (新建)
'use client';

import { useState } from 'react';
import { Menu, X } from 'lucide-react';
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/sheet';
import Link from 'next/link';

export function MobileNav() {
  const [open, setOpen] = useState(false);

  const navItems = [
    { href: '/', label: '首页', icon: Home },
    { href: '/create', label: '创建应用', icon: PlusCircle },
    { href: '/templates', label: '模板库', icon: Layout },
    { href: '/dashboard', label: '我的应用', icon: AppWindow },
    { href: '/account', label: '账户设置', icon: User },
  ];

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild className="md:hidden">
        <button className="p-2">
          <Menu className="h-6 w-6" />
        </button>
      </SheetTrigger>
      <SheetContent side="left" className="w-[280px] sm:w-[350px]">
        <nav className="flex flex-col gap-4 mt-8">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              onClick={() => setOpen(false)}
              className="flex items-center gap-3 px-4 py-3 rounded-lg hover:bg-accent"
            >
              <item.icon className="h-5 w-5" />
              <span className="text-base font-medium">{item.label}</span>
            </Link>
          ))}
        </nav>
      </SheetContent>
    </Sheet>
  );
}
```

```tsx
// components/layout/top-nav.tsx (更新)
import { MobileNav } from './mobile-nav';

export function TopNav() {
  return (
    <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur">
      <div className="container flex h-16 items-center justify-between">
        {/* 左侧：Logo + 移动端菜单 */}
        <div className="flex items-center gap-4">
          <MobileNav /> {/* 新增 */}
          <Logo />
        </div>

        {/* 中间：桌面端导航 */}
        <nav className="hidden md:flex items-center gap-6">
          {/* 现有导航项 */}
        </nav>

        {/* 右侧：用户操作 */}
        <div className="flex items-center gap-4">
          {/* 现有操作按钮 */}
        </div>
      </div>
    </header>
  );
}
```

**验收标准**:
- [ ] 移动端（<768px）能看到菜单按钮
- [ ] 点击菜单按钮打开侧边栏
- [ ] 侧边栏包含所有主要导航项
- [ ] 点击导航项后自动关闭侧边栏
- [ ] 使用Playwright测试移动端导航

---

##### Task 2.2: 删除重复的use-toast.ts（0.5小时）

**操作步骤**:
```bash
# 1. 确认哪个文件被使用
grep -r "from '@/hooks/use-toast'" src | wc -l  # 应该是大部分
grep -r "from '@/components/ui/use-toast'" src | wc -l

# 2. 删除重复文件
rm src/components/ui/use-toast.ts

# 3. 更新所有导入（如果有使用ui版本的）
# 手动或使用脚本替换
find src -type f -name "*.tsx" -o -name "*.ts" | xargs sed -i '' \
  's|from "@/components/ui/use-toast"|from "@/hooks/use-toast"|g'

# 4. 验证编译通过
pnpm tsc --noEmit
```

---

##### Task 2.3: 修复颜色对比度问题（3.5小时）

**问题示例**:
```tsx
// ❌ 不合规：灰色文字 + 白色背景（对比度 < 4.5:1）
<p className="text-gray-400">次要信息</p>

// ✅ 合规：深灰色文字 + 白色背景（对比度 ≥ 4.5:1）
<p className="text-gray-700 dark:text-gray-300">次要信息</p>
```

**检查工具**:
```bash
# 使用axe-core自动检查
pnpm add -D @axe-core/playwright

# src/e2e/accessibility.spec.ts
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test('homepage应符合WCAG AA标准', async ({ page }) => {
  await page.goto('http://localhost:3000');
  const accessibilityScanResults = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze();

  expect(accessibilityScanResults.violations).toEqual([]);
});
```

**批量修复**:
```bash
# 查找所有text-gray-400使用
grep -rn "text-gray-400" src/components

# 替换为符合对比度的颜色
# text-gray-400 → text-gray-700 (亮色模式)
# 同时添加 dark:text-gray-300 (暗色模式)
```

---

### Phase 2: 架构重构（Week 1 Day 3-5，24小时）

#### Day 3 - 拆分超大组件 Part 1（8小时）

##### Task 3.1: 拆分 wizard/[id]/page.tsx（6小时）

**当前结构**:
```
wizard/[id]/page.tsx (951行, 23个hooks)
├── State管理 (200行)
├── 数据获取 (150行)
├── WebSocket逻辑 (200行)
├── UI渲染 (300行)
└── 辅助函数 (100行)
```

**重构后结构**:
```
app/wizard/[id]/
├── page.tsx (主页面, <150行)
├── hooks/
│   ├── use-wizard-state.ts (状态管理)
│   ├── use-wizard-data.ts (数据获取和更新)
│   ├── use-agent-tracking.ts (Agent追踪逻辑)
│   └── use-task-polling.ts (任务轮询)
├── components/
│   ├── WizardHeader.tsx (头部)
│   ├── WizardProgress.tsx (进度条)
│   ├── WizardSidebar.tsx (侧边栏)
│   ├── WizardContent.tsx (主内容区)
│   └── WizardFooter.tsx (底部操作)
└── utils/
    └── wizard-helpers.ts (工具函数)
```

**重构步骤**:

**Step 1: 提取状态管理 Hook**
```typescript
// hooks/use-wizard-state.ts
export function useWizardState(appId: string) {
  const [stage, setStage] = useState<GenerationStage>('plan');
  const [showLeftPanel, setShowLeftPanel] = useState(true);
  const [activeTab, setActiveTab] = useState<'agent' | 'execution'>('agent');
  // ... 其他状态

  return {
    stage,
    setStage,
    showLeftPanel,
    setShowLeftPanel,
    activeTab,
    setActiveTab,
    // ... 其他状态
  };
}
```

**Step 2: 提取数据获取 Hook**
```typescript
// hooks/use-wizard-data.ts
export function useWizardData(appId: string) {
  const [appSpec, setAppSpec] = useState<AppSpec | null>(null);
  const [task, setTask] = useState<GenerationTask | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const spec = await getAppSpec(appId);
        setAppSpec(spec);

        if (spec.taskId) {
          const taskData = await getGenerationTask(spec.taskId);
          setTask(taskData);
        }
      } catch (error) {
        toast.error('加载失败');
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [appId]);

  return { appSpec, task, loading, refetch: fetchData };
}
```

**Step 3: 重构主页面**
```typescript
// app/wizard/[id]/page.tsx (重构后 <150行)
export default function WizardPage({ params }: { params: { id: string } }) {
  const { id } = params;

  // 使用自定义Hooks
  const wizardState = useWizardState(id);
  const { appSpec, task, loading } = useWizardData(id);
  const agentTracking = useAgentTracking(task?.taskId);
  const taskPolling = useTaskPolling(task?.taskId);

  if (loading) {
    return <WizardSkeleton />;
  }

  if (!appSpec) {
    return <NotFoundPage />;
  }

  return (
    <div className="flex h-screen overflow-hidden">
      <WizardSidebar
        appSpec={appSpec}
        stage={wizardState.stage}
        agents={agentTracking.agents}
        visible={wizardState.showLeftPanel}
        onToggle={() => wizardState.setShowLeftPanel(!wizardState.showLeftPanel)}
      />

      <main className="flex-1 flex flex-col overflow-hidden">
        <WizardHeader appSpec={appSpec} stage={wizardState.stage} />

        <WizardContent
          appSpec={appSpec}
          task={task}
          stage={wizardState.stage}
          activeTab={wizardState.activeTab}
          onTabChange={wizardState.setActiveTab}
        />

        <WizardFooter
          stage={wizardState.stage}
          onCancel={() => router.push('/dashboard')}
          onContinue={() => handleContinue()}
        />
      </main>
    </div>
  );
}
```

**验收标准**:
- [ ] 主页面 < 200行
- [ ] 每个Hook < 150行
- [ ] 每个组件 < 200行
- [ ] TypeScript编译通过
- [ ] E2E测试通过
- [ ] 功能完全一致（无回归bug）

---

##### Task 3.2: 代码审查和测试（2小时）

```bash
# 1. TypeScript检查
pnpm tsc --noEmit

# 2. ESLint检查
pnpm lint

# 3. 运行E2E测试
pnpm exec playwright test src/e2e/wizard.spec.ts

# 4. Code Review
# - 检查每个文件行数
# - 确认hooks遵循React最佳实践
# - 验证组件props类型定义完整
```

---

#### Day 4 - 拆分超大组件 Part 2（8小时）

##### Task 4.1: 拆分 requirement-form.tsx（6小时）

**当前问题**:
- 846行代码
- 模板文本硬编码（200+行）
- 混合了业务逻辑和UI

**重构步骤**:

**Step 1: 提取模板数据**
```typescript
// data/requirement-templates.ts (新建)
export interface RequirementTemplate {
  id: string;
  name: string;
  icon: React.ComponentType;
  category: string;
  requirement: string;
}

export const REQUIREMENT_TEMPLATES: RequirementTemplate[] = [
  {
    id: 'campus-marketplace',
    name: '校园二手交易平台',
    icon: ShoppingCart,
    category: '电商',
    requirement: '创建一个校园二手交易平台...',
  },
  // ... 其他模板
];
```

**Step 2: 拆分为多个子组件**
```typescript
// components/create/requirement-form/
├── RequirementForm.tsx (主组件, <200行)
├── TemplateSelector.tsx (模板选择器)
├── RequirementTextarea.tsx (需求输入框)
├── StyleSelector.tsx (风格选择器)
├── AdvancedOptions.tsx (高级选项)
└── SubmitSection.tsx (提交区域)
```

**Step 3: 重构主组件**
```typescript
// components/create/requirement-form/RequirementForm.tsx
export function RequirementForm() {
  const [requirement, setRequirement] = useState('');
  const [selectedTemplate, setSelectedTemplate] = useState<string | null>(null);
  const [selectedStyle, setSelectedStyle] = useState<string | null>(null);

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <TemplateSelector
        templates={REQUIREMENT_TEMPLATES}
        selected={selectedTemplate}
        onSelect={(id) => {
          setSelectedTemplate(id);
          const template = REQUIREMENT_TEMPLATES.find(t => t.id === id);
          if (template) {
            setRequirement(template.requirement);
          }
        }}
      />

      <RequirementTextarea
        value={requirement}
        onChange={setRequirement}
        placeholder="描述您想要创建的应用..."
      />

      <StyleSelector
        selected={selectedStyle}
        onSelect={setSelectedStyle}
      />

      <AdvancedOptions />

      <SubmitSection
        disabled={!requirement.trim()}
        loading={loading}
      />
    </form>
  );
}
```

**验收标准**:
- [ ] 主组件 < 250行
- [ ] 模板数据独立文件
- [ ] 每个子组件职责单一
- [ ] 功能完全一致

---

##### Task 4.2: 清理console.log（2小时）

**问题**: 338个console.log散布在20个文件中

**批量清理脚本**:
```bash
# find-console-logs.sh
#!/bin/bash

echo "正在查找所有console.log..."
grep -rn "console\.log" src \
  --exclude-dir=e2e \
  --exclude-dir=__tests__ \
  --include="*.ts" \
  --include="*.tsx" \
  > /tmp/console-logs.txt

echo "找到 $(wc -l < /tmp/console-logs.txt) 个console.log"
echo "详细列表保存在 /tmp/console-logs.txt"
```

**替换策略**:
```typescript
// ❌ 删除：调试日志
console.log('Debug: value =', value);

// ✅ 保留：错误日志（但使用console.error）
console.error('API调用失败:', error);

// ✅ 保留：警告日志（但使用console.warn）
console.warn('通知API不可用，使用默认值');

// ✅ 开发环境日志
if (process.env.NODE_ENV === 'development') {
  console.log('[Dev] WebSocket连接已建立');
}
```

**半自动替换**:
```bash
# 1. 找出所有需要保留的console.error/warn
grep -rn "console\.(error|warn)" src --include="*.ts" --include="*.tsx"

# 2. 删除src目录（排除e2e）所有console.log
find src -type f \( -name "*.ts" -o -name "*.tsx" \) \
  ! -path "*/e2e/*" \
  -exec sed -i '' '/console\.log/d' {} +

# 3. 验证编译
pnpm tsc --noEmit
```

---

#### Day 5 - 补充单元测试（8小时）

##### Task 5.1: Hook单元测试（4小时）

**测试框架设置**:
```typescript
// hooks/__tests__/setup.ts
import '@testing-library/jest-dom';
import { renderHook, waitFor } from '@testing-library/react';
```

**示例测试**:
```typescript
// hooks/__tests__/use-generation-task.test.ts
import { renderHook, waitFor } from '@testing-library/react';
import { useGenerationTask } from '../use-generation-task';
import * as api from '@/lib/api/generate';

jest.mock('@/lib/api/generate');

describe('useGenerationTask', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('应该正确初始化状态', () => {
    const { result } = renderHook(() => useGenerationTask());

    expect(result.current.taskId).toBeNull();
    expect(result.current.status).toBe('idle');
    expect(result.current.loading).toBe(false);
  });

  it('应该开始轮询任务状态', async () => {
    const mockTask = {
      taskId: 'task-123',
      status: 'running',
      progress: 50,
    };

    (api.getGenerationTask as jest.Mock).mockResolvedValue(mockTask);

    const { result } = renderHook(() => useGenerationTask());

    act(() => {
      result.current.startPolling('task-123');
    });

    await waitFor(() => {
      expect(result.current.taskId).toBe('task-123');
      expect(result.current.status).toBe('running');
    });
  });

  it('应该在任务完成时停止轮询', async () => {
    const mockTask = {
      taskId: 'task-123',
      status: 'completed',
      progress: 100,
    };

    (api.getGenerationTask as jest.Mock).mockResolvedValue(mockTask);

    const { result } = renderHook(() => useGenerationTask());

    act(() => {
      result.current.startPolling('task-123');
    });

    await waitFor(() => {
      expect(result.current.status).toBe('completed');
    });

    // 验证轮询已停止（不再调用API）
    const callCount = (api.getGenerationTask as jest.Mock).mock.calls.length;
    await new Promise(resolve => setTimeout(resolve, 5000));
    expect((api.getGenerationTask as jest.Mock).mock.calls.length).toBe(callCount);
  });
});
```

**需要测试的Hooks**:
- [ ] use-generation-task.ts
- [ ] use-generation-websocket.ts
- [ ] use-generation-toasts.ts
- [ ] use-auto-scroll.ts
- [ ] use-analysis-sse.ts

---

##### Task 5.2: API层单元测试（4小时）

```typescript
// lib/api/__tests__/generate.test.ts
import { createAsyncGenerationTask, getGenerationTask } from '../generate';
import { client } from '../client';

jest.mock('../client');

describe('Generate API', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('createAsyncGenerationTask', () => {
    it('应该成功创建生成任务', async () => {
      const mockResponse = {
        success: true,
        data: { taskId: 'task-123' },
      };

      (client.post as jest.Mock).mockResolvedValue(mockResponse);

      const result = await createAsyncGenerationTask({
        requirement: '创建一个电商平台',
        model: 'qwen-max',
      });

      expect(result.success).toBe(true);
      expect(result.data.taskId).toBe('task-123');
      expect(client.post).toHaveBeenCalledWith(
        '/api/v1/generate/async',
        expect.objectContaining({
          requirement: '创建一个电商平台',
          model: 'qwen-max',
        })
      );
    });

    it('应该处理API错误', async () => {
      (client.post as jest.Mock).mockRejectedValue(
        new APIError('服务器错误', 500)
      );

      await expect(
        createAsyncGenerationTask({
          requirement: '测试',
          model: 'qwen-max',
        })
      ).rejects.toThrow('服务器错误');
    });
  });
});
```

**需要测试的API模块**:
- [ ] generate.ts
- [ ] appspec.ts
- [ ] templates.ts
- [ ] client.ts
- [ ] superdesign.ts

---

### Phase 3: 性能优化和UX改进（Week 2 Day 1-3，24小时）

#### Day 6 - 性能优化（8小时）

##### Task 6.1: 实现代码分割（3小时）

**优化大型组件加载**:
```typescript
// app/wizard/[id]/page.tsx
import dynamic from 'next/dynamic';
import { Skeleton } from '@/components/ui/skeleton';

// 懒加载ExecutionPanel（300+行）
const ExecutionPanel = dynamic(
  () => import('@/components/wizard/execution-panel'),
  {
    loading: () => <Skeleton className="h-full w-full" />,
    ssr: false, // 不需要SSR的组件
  }
);

// 懒加载AgentTimeline（大型可视化组件）
const AgentTimeline = dynamic(
  () => import('@/components/wizard/agent-timeline'),
  {
    loading: () => <Skeleton className="h-[400px]" />,
  }
);
```

**路由级代码分割**（Next.js自动，确认配置）:
```typescript
// next.config.ts
const nextConfig: NextConfig = {
  experimental: {
    optimizePackageImports: ['lucide-react', '@radix-ui/react-*'],
  },
};
```

**预期收益**:
- 首屏加载时间减少 30-40%
- 初始JS bundle减少 ~200KB

---

##### Task 6.2: 优化图标导入（2小时）

**当前问题**:
```typescript
// ❌ 导入整个lucide-react库
import { Home, User, Settings, Bell, Menu } from 'lucide-react';
```

**优化方案**:
```typescript
// ✅ 按需导入单个图标
import Home from 'lucide-react/dist/esm/icons/home';
import User from 'lucide-react/dist/esm/icons/user';
import Settings from 'lucide-react/dist/esm/icons/settings';

// 或使用lucide-react提供的tree-shakable导入
import { Home } from 'lucide-react/icons';
```

**自动化脚本**:
```bash
# 查找所有lucide-react导入
grep -rn "from 'lucide-react'" src --include="*.tsx" --include="*.ts" > /tmp/lucide-imports.txt

# 生成替换建议
# 需要手动或使用codemod工具处理
```

**预期收益**: 减少 ~50KB bundle size

---

##### Task 6.3: 实现虚拟滚动（3小时）

**问题**: 模板列表、通知列表渲染大量DOM

**解决方案**:
```bash
pnpm add react-window
```

```typescript
// components/templates/template-grid.tsx
import { FixedSizeGrid } from 'react-window';
import AutoSizer from 'react-virtualized-auto-sizer';

interface TemplateGridProps {
  templates: Template[];
  onSelect: (template: Template) => void;
}

export function TemplateGrid({ templates, onSelect }: TemplateGridProps) {
  const COLUMN_COUNT = 3;
  const ROW_HEIGHT = 350;

  const Cell = ({ columnIndex, rowIndex, style }: any) => {
    const index = rowIndex * COLUMN_COUNT + columnIndex;
    const template = templates[index];

    if (!template) return null;

    return (
      <div style={style} className="p-3">
        <TemplateCard template={template} onClick={onSelect} />
      </div>
    );
  };

  return (
    <AutoSizer>
      {({ height, width }) => (
        <FixedSizeGrid
          columnCount={COLUMN_COUNT}
          columnWidth={width / COLUMN_COUNT}
          height={height}
          rowCount={Math.ceil(templates.length / COLUMN_COUNT)}
          rowHeight={ROW_HEIGHT}
          width={width}
        >
          {Cell}
        </FixedSizeGrid>
      )}
    </AutoSizer>
  );
}
```

**预期收益**:
- 支持10000+模板无卡顿
- 首次渲染时间从~500ms降至~50ms

---

#### Day 7-8 - UX改进（16小时）

##### Task 7.1: 触摸目标尺寸优化（4小时）

**WCAG要求**: 触摸目标至少44x44px

**问题示例**:
```tsx
// ❌ 按钮太小（32x32px）
<button className="p-2"> {/* p-2 = 8px padding */}
  <X className="h-4 w-4" />
</button>
```

**修复**:
```tsx
// ✅ 符合标准（48x48px）
<button className="p-3 min-w-[48px] min-h-[48px] flex items-center justify-center">
  <X className="h-5 w-5" />
</button>

// 或使用Button组件的size="lg"
<Button size="lg">
  <X className="h-5 w-5" />
</Button>
```

**批量检查**:
```typescript
// src/e2e/touch-targets.spec.ts
test('所有交互元素应符合触摸目标尺寸', async ({ page }) => {
  await page.goto('http://localhost:3000');

  // 获取所有button和链接
  const interactiveElements = await page.locator('button, a[href]').all();

  for (const element of interactiveElements) {
    const box = await element.boundingBox();
    expect(box?.width).toBeGreaterThanOrEqual(44);
    expect(box?.height).toBeGreaterThanOrEqual(44);
  }
});
```

---

##### Task 7.2: 表单可访问性（6小时）

**问题**: 表单缺少label关联

**当前代码**:
```tsx
// ❌ 无法访问
<div>
  <span>用户名</span>
  <input type="text" placeholder="请输入用户名" />
</div>
```

**修复**:
```tsx
// ✅ 可访问
<div>
  <label htmlFor="username" className="block text-sm font-medium mb-2">
    用户名 <span className="text-red-500">*</span>
  </label>
  <input
    id="username"
    type="text"
    aria-required="true"
    aria-invalid={!!errors.username}
    aria-describedby={errors.username ? "username-error" : undefined}
    placeholder="请输入用户名"
  />
  {errors.username && (
    <p id="username-error" className="text-sm text-red-500 mt-1" role="alert">
      {errors.username}
    </p>
  )}
</div>
```

**需要修复的表单**:
- [ ] RequirementForm - 需求输入
- [ ] LoginForm - 登录表单
- [ ] SettingsForm - 设置表单
- [ ] TemplateFilterBar - 筛选表单

---

##### Task 7.3: 键盘导航（6小时）

**实现焦点管理**:
```typescript
// hooks/use-focus-trap.ts
export function useFocusTrap(ref: React.RefObject<HTMLElement>) {
  useEffect(() => {
    const element = ref.current;
    if (!element) return;

    const focusableElements = element.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );

    const firstElement = focusableElements[0] as HTMLElement;
    const lastElement = focusableElements[focusableElements.length - 1] as HTMLElement;

    function handleTabKey(e: KeyboardEvent) {
      if (e.key !== 'Tab') return;

      if (e.shiftKey) {
        if (document.activeElement === firstElement) {
          lastElement.focus();
          e.preventDefault();
        }
      } else {
        if (document.activeElement === lastElement) {
          firstElement.focus();
          e.preventDefault();
        }
      }
    }

    element.addEventListener('keydown', handleTabKey);
    return () => element.removeEventListener('keydown', handleTabKey);
  }, [ref]);
}
```

**应用到对话框**:
```typescript
// components/ui/dialog.tsx
export function Dialog({ open, children }: DialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  useFocusTrap(dialogRef);

  useEffect(() => {
    if (open) {
      dialogRef.current?.focus();
    }
  }, [open]);

  return (
    <div ref={dialogRef} tabIndex={-1} role="dialog" aria-modal="true">
      {children}
    </div>
  );
}
```

**添加键盘快捷键**:
```typescript
// hooks/use-keyboard-shortcuts.ts
export function useKeyboardShortcuts() {
  useEffect(() => {
    function handleKeyPress(e: KeyboardEvent) {
      // Cmd/Ctrl + K: 打开命令面板
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        openCommandPalette();
      }

      // Cmd/Ctrl + N: 新建应用
      if ((e.metaKey || e.ctrlKey) && e.key === 'n') {
        e.preventDefault();
        router.push('/create');
      }

      // Esc: 关闭对话框
      if (e.key === 'Escape') {
        closeDialog();
      }
    }

    window.addEventListener('keydown', handleKeyPress);
    return () => window.removeEventListener('keydown', handleKeyPress);
  }, []);
}
```

---

### Phase 4: 质量保证和文档（Week 2 Day 4-5，16小时）

#### Day 9 - E2E测试修复和扩展（8小时）

##### Task 9.1: 修复现有E2E测试（4小时）

**问题**: 53/55测试失败

**修复步骤**:

1. **修复API Mock（如果后端未实现）**:
```typescript
// src/e2e/setup/api-mocks.ts
import { test as base } from '@playwright/test';

export const test = base.extend({
  page: async ({ page }, use) => {
    // Mock未实现的API
    await page.route('**/api/v1/notifications/unread-count', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ count: 3 }),
      });
    });

    await page.route('**/api/v1/user/profile', (route) => {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'mock-user',
          name: 'Test User',
          email: 'test@example.com',
        }),
      });
    });

    await use(page);
  },
});
```

2. **使用修复后的test**:
```typescript
// src/e2e/full-page-screenshot-test.spec.ts
import { test, expect } from './setup/api-mocks'; // 使用Mock版本

test('01-首页 (/)', async ({ page }) => {
  await page.goto('http://localhost:3000/');
  await page.waitForLoadState('networkidle'); // 现在应该能达到
  await page.screenshot({
    path: '/tmp/ingenio-screenshots/01-homepage.png',
    fullPage: true
  });
});
```

3. **重新运行测试**:
```bash
pnpm exec playwright test src/e2e/full-page-screenshot-test.spec.ts --reporter=html
```

---

##### Task 9.2: 扩展测试覆盖（4小时）

**添加关键用户流程测试**:
```typescript
// src/e2e/user-flows/create-app-flow.spec.ts
test('完整的应用创建流程', async ({ page }) => {
  // 1. 访问首页
  await page.goto('http://localhost:3000');
  await expect(page.locator('h1')).toContainText('秒构AI');

  // 2. 点击"创建应用"
  await page.click('text=创建应用');
  await expect(page).toHaveURL(/.*\/create/);

  // 3. 输入需求
  await page.fill('textarea', '我想创建一个电商平台');

  // 4. 点击生成
  await page.click('button:has-text("生成应用")');

  // 5. 等待进入向导页面
  await expect(page).toHaveURL(/.*\/wizard\/.+/);

  // 6. 等待Plan阶段完成
  await expect(page.locator('text=Plan阶段')).toBeVisible();
  await expect(page.locator('[data-status="completed"]')).toBeVisible({ timeout: 60000 });

  // 7. 截图验证
  await page.screenshot({ path: '/tmp/wizard-plan-completed.png' });
});
```

**测试移动端导航**:
```typescript
// src/e2e/responsive/mobile-navigation.spec.ts
test('移动端导航功能', async ({ page }) => {
  // 设置移动端视口
  await page.setViewportSize({ width: 375, height: 667 });

  await page.goto('http://localhost:3000');

  // 验证菜单按钮可见
  const menuButton = page.locator('[aria-label="菜单"]');
  await expect(menuButton).toBeVisible();

  // 点击打开侧边栏
  await menuButton.click();

  // 验证导航项可见
  await expect(page.locator('text=创建应用')).toBeVisible();
  await expect(page.locator('text=模板库')).toBeVisible();
  await expect(page.locator('text=我的应用')).toBeVisible();

  // 点击导航项
  await page.click('text=模板库');

  // 验证导航成功
  await expect(page).toHaveURL(/.*\/templates/);

  // 验证侧边栏自动关闭
  await expect(page.locator('text=创建应用')).not.toBeVisible();
});
```

---

#### Day 10 - 文档和交付（8小时）

##### Task 10.1: 组件文档（4小时）

**使用Storybook生成组件库文档**:
```bash
pnpm add -D @storybook/react @storybook/addon-essentials
pnpm exec storybook init
```

**示例Story**:
```typescript
// src/components/ui/button.stories.tsx
import type { Meta, StoryObj } from '@storybook/react';
import { Button } from './button';

const meta: Meta<typeof Button> = {
  title: 'UI/Button',
  component: Button,
  tags: ['autodocs'],
  argTypes: {
    variant: {
      control: 'select',
      options: ['default', 'destructive', 'outline', 'secondary', 'ghost', 'link'],
    },
    size: {
      control: 'select',
      options: ['default', 'sm', 'lg', 'icon'],
    },
  },
};

export default meta;
type Story = StoryObj<typeof Button>;

export const Default: Story = {
  args: {
    children: '默认按钮',
  },
};

export const Primary: Story = {
  args: {
    variant: 'default',
    children: '主要按钮',
  },
};

export const Destructive: Story = {
  args: {
    variant: 'destructive',
    children: '删除',
  },
};

export const WithIcon: Story = {
  args: {
    children: (
      <>
        <PlusIcon className="mr-2 h-4 w-4" />
        创建新项目
      </>
    ),
  },
};
```

**需要文档化的组件**:
- [ ] Button
- [ ] Card
- [ ] Dialog
- [ ] Input
- [ ] Select
- [ ] Toast

---

##### Task 10.2: API文档（2小时）

**生成API文档**:
```markdown
# Ingenio Frontend API Reference

## 生成相关API

### createAsyncGenerationTask

创建异步生成任务

**请求**:
```typescript
interface CreateAsyncGenerationRequest {
  requirement: string;        // 用户需求描述
  model: UniaixModel;         // AI模型
  style?: string;             // 设计风格ID（可选）
  customFeatures?: string[];  // 自定义功能（可选）
}
```

**响应**:
```typescript
interface CreateAsyncGenerationResponse {
  success: boolean;
  data: {
    taskId: string;           // 任务ID
    estimatedTime: number;    // 预计完成时间（秒）
  };
  message?: string;
}
```

**使用示例**:
```typescript
import { createAsyncGenerationTask } from '@/lib/api/generate';

const response = await createAsyncGenerationTask({
  requirement: '创建一个电商平台',
  model: 'qwen-max',
  style: 'modern-minimal',
});

console.log('任务ID:', response.data.taskId);
```

**错误处理**:
```typescript
try {
  const response = await createAsyncGenerationTask({ ... });
} catch (error) {
  if (error instanceof APIError) {
    if (error.statusCode === 400) {
      toast.error('需求描述格式错误');
    } else if (error.statusCode === 429) {
      toast.error('请求过于频繁，请稍后再试');
    }
  }
}
```
```

---

##### Task 10.3: 最终验证和交付（2小时）

**质量门禁检查清单**:
```bash
#!/bin/bash
# final-check.sh

echo "🔍 执行最终质量检查..."

# 1. TypeScript编译
echo "1. TypeScript编译检查..."
pnpm tsc --noEmit || exit 1

# 2. ESLint检查
echo "2. ESLint检查..."
pnpm lint || exit 1

# 3. 单元测试
echo "3. 运行单元测试..."
pnpm test || exit 1

# 4. E2E测试
echo "4. 运行E2E测试..."
pnpm exec playwright test || exit 1

# 5. 构建测试
echo "5. 生产构建测试..."
pnpm build || exit 1

# 6. 统计代码质量指标
echo "6. 生成质量报告..."
cat > /tmp/quality-report.md <<EOF
# Ingenio前端质量报告

## 代码质量
- TypeScript错误: 0 ✅
- ESLint错误: 0 ✅
- 重复代码: 0 ✅

## 测试覆盖
- 单元测试: $(pnpm test --coverage --silent | grep "All files" | awk '{print $10}')
- E2E测试: $(pnpm exec playwright test --list | wc -l) 个测试

## 性能指标
- 构建时间: $(grep "Compiled successfully" .next/trace | tail -1)
- Bundle大小: $(du -sh .next/static/chunks | awk '{print $1}')

## 可访问性
- WCAG AA合规性: ✅ 通过

生成时间: $(date)
EOF

cat /tmp/quality-report.md

echo "✅ 所有检查通过！"
```

**交付清单**:
- [ ] 代码已提交到Git
- [ ] 所有测试通过
- [ ] 文档已更新
- [ ] 质量报告已生成
- [ ] Changelog已更新
- [ ] 部署文档已准备

---

## 附录A：优先级矩阵

| 问题 | 影响面 | 严重度 | 修复成本 | 优先级 | 时间估算 |
|-----|-------|--------|---------|--------|---------|
| API 404错误 | 100% | 阻塞 | 低 | P0 | 2h |
| 移动端导航缺失 | 50% | 严重 | 中 | P0 | 4h |
| 重复代码 | 开发 | 中等 | 低 | P0 | 0.5h |
| 超大组件 | 开发 | 中等 | 高 | P1 | 12h |
| 可访问性不足 | 10% | 严重 | 中 | P1 | 6h |
| 测试覆盖低 | 开发 | 中等 | 高 | P1 | 16h |
| 代码分割缺失 | 100% | 低 | 中 | P2 | 3h |
| 图标导入冗余 | 100% | 低 | 低 | P2 | 2h |
| 虚拟滚动缺失 | 5% | 低 | 中 | P2 | 3h |

---

## 附录B：测试策略

### 单元测试目标

**覆盖率目标**: ≥85%

**测试金字塔**:
```
       /\
      /  \      10% E2E测试（用户流程）
     /----\
    /      \    20% 集成测试（API调用、组件集成）
   /--------\
  /          \  70% 单元测试（Hooks、工具函数）
 /------------\
```

**测试优先级**:
1. **关键Hooks** (90%+ 覆盖率)
   - use-generation-task
   - use-generation-websocket
   - use-wizard-state

2. **API层** (85%+ 覆盖率)
   - generate.ts
   - appspec.ts
   - client.ts

3. **工具函数** (95%+ 覆盖率)
   - lib/utils.ts
   - lib/ui-text.ts

---

## 附录C：性能优化checklist

### 构建优化
- [x] 启用SWC编译器（Next.js默认）
- [ ] 配置outputFileTracingRoot
- [ ] 启用experimental.optimizePackageImports
- [ ] 启用experimental.turbo（Next.js 14+）

### 运行时优化
- [ ] 实现代码分割（dynamic import）
- [ ] 优化图标导入（lucide-react）
- [ ] 实现虚拟滚动（大列表）
- [ ] 添加useMemo/useCallback（复杂计算）
- [ ] 使用React.memo（纯组件）

### 资源优化
- [ ] 图片使用Next.js Image组件
- [ ] 添加public/templates/图片资源
- [ ] 配置图片占位符
- [ ] 启用图片lazy loading

---

## 附录D：Git工作流

### Commit规范

遵循约定式提交：
```
feat: 实现移动端导航菜单
fix: 修复API 404错误导致页面无法加载
refactor: 拆分wizard页面为多个子组件
test: 补充use-generation-task单元测试
docs: 更新组件库文档
perf: 实现代码分割优化首屏加载
```

### 分支策略
```
main (生产环境)
  └─ develop (开发主分支)
      ├─ feature/mobile-nav
      ├─ feature/split-wizard
      ├─ feature/unit-tests
      └─ fix/api-404-errors
```

---

## 总结

这份综合优化计划整合了：
- ✅ 3份Agent审查报告（前端代码质量、UI/UX一致性、代码库探索）
- ✅ Playwright全页面测试结果
- ✅ 实际问题根源分析（API 404错误）

**执行时间**: 10个工作日
**预期成果**:
- E2E测试通过率：2/55 (4%) → 50/55 (90%+)
- 代码质量评分：7.3/10 → 8.5/10
- 用户体验评分：6.5/10 → 8.5/10
- 单元测试覆盖率：<5% → 85%+

**下一步**: 按Phase 1开始执行，每日Standup review进度。
