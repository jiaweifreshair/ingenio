# Ingenio前端代码优化 - 执行路线图

## 快速导航

本路线图基于深度代码库分析报告，提供8个工作日的分阶段优化计划。

**关键文件**:
- 📊 [代码库分析报告](./CODEBASE_ANALYSIS_REPORT.md) - 完整的架构和问题分析
- 📈 [详细度量数据](./DETAILED_METRICS.md) - 量化的代码质量指标

---

## Phase 0: 准备阶段 (2小时)

### Task 0.1: 环境检查
```bash
# 检查工具链完整性
pnpm --version        # 需要 >= 8.0
node --version        # 需要 >= 18.0
git --version         # 检查git配置

# 验证当前构建
pnpm tsc --noEmit     # TypeScript检查
pnpm lint             # ESLint检查
pnpm build            # 完整构建
```

### Task 0.2: 建立基准线
```bash
# 记录当前指标
- 构建时间: ___秒
- TypeScript错误: ___个
- ESLint错误: ___个
- 文件大小: ___KB

# 这些基准线用于衡量优化效果
```

### Task 0.3: 创建功能分支
```bash
git checkout -b refactor/phase1-type-cleanup
git checkout -b refactor/phase2-file-split
git checkout -b refactor/phase3-unit-tests
```

---

## Phase 1: 快速清理 (1天, 6小时)

### ✅ 优先级: P0 (立即处理，有阻塞风险)

#### Task 1.1: 修复DesignStyle重复定义 (1小时)

**现状分析**:
- 两个文件定义了DesignStyle枚举
- `/src/types/design-style.ts` (V2.0新增, 7种风格)
- `/src/types/design.ts` (旧版, 3种风格)
- 导致导入混淆和类型错误

**执行步骤**:

```bash
# Step 1: 检查所有引用
grep -r "from.*design" src/types/ src/components/ src/lib/ --include="*.ts" --include="*.tsx" > /tmp/design-imports.txt

# Step 2: 检查design.ts中的其他类型
# 确保ColorTheme, FontScheme等只在design.ts中
grep "export interface\|export enum\|export type" src/types/design.ts

# Step 3: 删除旧文件并更新导入
# - 删除 src/types/design.ts
# - 对所有导入进行批量替换
find src -name "*.ts" -o -name "*.tsx" | xargs sed -i '' \
  's|from.*types/design"|from "@/types/design-style"|g' \
  's|from.*types/design|from "@/types/design-style"|g'

# Step 4: 手动检查替换结果
git diff

# Step 5: 运行测试确认无破坏
pnpm tsc --noEmit      # TypeScript检查
pnpm lint              # ESLint检查
pnpm test:e2e          # E2E测试
```

**检查清单**:
- [ ] 删除 `/src/types/design.ts`
- [ ] 更新所有导入路径
- [ ] TypeScript编译无错误
- [ ] E2E测试全部通过
- [ ] 提交PR: `fix: 删除DesignStyle重复定义`

**预期收益**:
- 消除类型混淆
- 减少20行重复代码
- 改善代码可维护性

---

#### Task 1.2: 补充SuperDesign页面入口 (2小时)

**现状分析**:
- `/app/superdesign/[appId]/page.tsx` 页面孤立
- 仅通过直链访问，无主入口菜单
- 用户难以发现该功能

**执行步骤**:

```bash
# Step 1: 分析导航结构
cat src/components/layout/top-nav.tsx | grep -A 20 "menu\|links"

# Step 2: 添加到导航菜单
# 编辑 src/components/layout/top-nav.tsx
# 在菜单中添加SuperDesign快速入口

# Step 3: 添加仪表板快速操作卡片
# 编辑 src/app/dashboard/page.tsx
# 在快速操作区域添加"打开SuperDesign"按钮

# Step 4: 补充返回路径
# 编辑 src/app/superdesign/[appId]/page.tsx
# 在页面顶部添加返回按钮 (返回dashboard)

# Step 5: 测试导航流程
pnpm test:e2e -- superdesign
```

**检查清单**:
- [ ] 导航菜单已添加SuperDesign链接
- [ ] 仪表板有快速入口
- [ ] SuperDesign页面有返回按钮
- [ ] 导航流程E2E测试通过
- [ ] 提交PR: `feat: 补充SuperDesign页面入口`

**预期收益**:
- 提升功能可发现性
- 改善用户体验

---

#### Task 1.3: 核对E2E测试覆盖 (1小时)

**执行步骤**:

```bash
# 检查未覆盖的功能
pnpm test:e2e --reporter=verbose

# 记录缺失的覆盖:
- [ ] API密钥管理流程
- [ ] 两步验证设置流程
- [ ] 版本对比详情
- [ ] 下载进度跟踪

# 创建补充测试计划 (Phase 3)
```

---

#### Task 1.4: 提交Phase 1 (30分钟)

```bash
# 合并所有改动
git add .
git commit -m "refactor: Phase 1 - 类型清理和入口补充"
git push origin refactor/phase1-type-cleanup

# 创建PR
gh pr create --title "Refactor: Phase 1 - 类型清理和入口补充" \
  --body "
  ## 改动内容
  - 删除DesignStyle重复定义
  - 补充SuperDesign页面入口
  - 核对E2E测试覆盖
  
  ## 验证
  - [x] TypeScript编译无错误
  - [x] ESLint无错误
  - [x] E2E测试通过
  - [x] 导航流程验证
  "
```

---

## Phase 2: 架构优化 (2天, 12小时)

### ✅ 优先级: P1 (高优先级)

#### Task 2.1: 拆分大型页面 - Wizard (4小时)

**目标**: `app/wizard/[id]/page.tsx` (951行 → 5个子组件)

**当前结构分析**:
```
page.tsx (951行)
├── 初始化逻辑 (80行)
├── 向导界面布局 (120行)
├── 配置面板 (已独立)
├── 执行面板 (已独立)
├── 生成统计 (已独立)
├── Agent Timeline (已独立)
└── Code Download (已独立)
```

**执行步骤**:

```bash
# Step 1: 创建新组件目录
mkdir -p src/components/wizard/wizard-page-fragments

# Step 2: 拆分为以下子组件
# 1. WizardPageLayout.tsx (布局框架)
# 2. WizardPageInitializer.tsx (初始化逻辑)
# 3. WizardPageStates.tsx (状态管理)
# 4. WizardPageActions.tsx (用户操作)
# 5. WizardPageMetrics.tsx (指标显示)

# 具体拆分方案见下文详细步骤

# Step 3: 逐个创建子组件并测试
# ... (详见下文)

# Step 4: 更新page.tsx引用
# 从导入40个组件 → 导入5个子组件

# Step 5: 验证功能完整性
pnpm test:e2e -- wizard
```

**详细拆分方案**:

1. **WizardPageLayout.tsx** (120行, 模板)
   - 包含: 主布局、面板分割、响应式设计
   - 导出: `WizardPageLayout`

2. **WizardPageInitializer.tsx** (80行, 初始化)
   - 包含: 参数解析、数据加载、状态初始化
   - 导出: `useWizardInitialize()`

3. **WizardPageStates.tsx** (140行, 状态)
   - 包含: 全局状态定义、状态转换逻辑
   - 导出: `WizardPageStateProvider`

4. **WizardPageActions.tsx** (150行, 交互)
   - 包含: 事件处理、用户交互逻辑
   - 导出: `useWizardActions()`

5. **WizardPageMetrics.tsx** (140行, 显示)
   - 包含: 指标收集、统计显示
   - 导出: `WizardPageMetrics`

**检查清单**:
- [ ] 5个子组件已创建
- [ ] 功能测试通过
- [ ] E2E测试通过
- [ ] 代码重复度降低
- [ ] 提交PR: `refactor: 拆分Wizard页面为5个子组件`

**预期收益**:
- 可维护性提升50%
- 每个组件 <300行，易于理解
- 便于独立测试和复用

---

#### Task 2.2: 拆分大型页面 - Publish (3小时)

**目标**: `app/publish/[id]/page.tsx` (698行 → Tab组件结构)

**当前结构分析**:
```
publish/[id]/page.tsx (698行)
├── 基础配置 (200行) -> Tab 1: Basic
├── 平台配置 (200行) -> Tab 2: Platforms  
├── 构建设置 (150行) -> Tab 3: Build
├── 发布流程 (148行) -> Tab 4: Release
└── 下载管理 (200行) -> Tab 5: Download
```

**执行步骤**:

```bash
# Step 1: 转换为Tab结构
# src/app/publish/[id]/page.tsx -> 导入Tabs组件
# 包含5个TabContent，每个对应一个Tab功能

# Step 2: 创建Tab内容组件
# - PublishBasicTab.tsx (200行)
# - PublishPlatformsTab.tsx (200行)
# - PublishBuildTab.tsx (150行)
# - PublishReleaseTab.tsx (150行)
# - PublishDownloadTab.tsx (200行)

# Step 3: 验证功能
pnpm test:e2e -- publish
```

**检查清单**:
- [ ] Tab结构已实现
- [ ] 5个Tab内容组件已创建
- [ ] 功能完整且可测试
- [ ] E2E测试通过
- [ ] 提交PR: `refactor: 转换Publish为Tab结构`

**预期收益**:
- 提升用户体验（更清晰的分类）
- 降低单文件复杂度
- 便于未来扩展新Tab

---

#### Task 2.3: 拆分大型表单 - RequirementForm (3小时)

**目标**: `components/create/requirement-form.tsx` (846行 → 8个Section)

**当前结构分析**:
```
requirement-form.tsx (846行)
├── 基础信息Section (120行) -> Section 1: BasicInfo
├── 应用类型Section (110行) -> Section 2: AppType
├── 目标用户Section (140行) -> Section 3: TargetAudience
├── 功能需求Section (160行) -> Section 4: Features
├── 风格选择Section (180行) -> Section 5: StyleChoice
├── AI能力Section (130行) -> Section 6: AICapabilities
└── 表单操作区 (106行) -> Section 7: Actions
```

**执行步骤**:

```bash
# Step 1: 创建Section组件目录
mkdir -p src/components/create/form-sections

# Step 2: 提取各Section组件
# - FormBasicInfoSection.tsx (120行)
# - FormAppTypeSection.tsx (110行)
# - FormTargetAudienceSection.tsx (140行)
# - FormFeaturesSection.tsx (160行)
# - FormStyleChoiceSection.tsx (180行)
# - FormAICapabilitiesSection.tsx (130行)
# - FormActionsSection.tsx (106行)
# - useRequirementFormState.ts (100行, 状态hook)

# Step 3: 重构主表单组件
# requirement-form.tsx -> 主要导入7个Section和状态Hook

# Step 4: 验证表单流程
pnpm test:e2e -- create
```

**检查清单**:
- [ ] 8个Section组件已创建
- [ ] 共享状态Hook已提取
- [ ] 表单流程完整
- [ ] 表单验证正常
- [ ] E2E测试通过
- [ ] 提交PR: `refactor: 拆分RequirementForm为8个Section`

**预期收益**:
- 降低单文件复杂度63%
- 每个Section <150行，易理解
- 便于表单字段验证和测试
- 提升代码复用性

---

#### Task 2.4: 重构API层 (2小时)

**目标**: 拆分 `lib/api/user.ts` (434行) 和 `templates.ts` (501行)

**执行步骤 - user.ts拆分**:

```bash
# Step 1: 分析user.ts的函数分组
# 分组1 (个人资料): getUserInfo, updateUserInfo, uploadAvatar, changePassword
# 分组2 (API密钥): getApiKeys, generateApiKey, deleteApiKey
# 分组3 (安全): getTwoFactorStatus, enableTwoFactor, disableTwoFactor, getLoginDevices, removeLoginDevice
# 分组4 (共享): 认证基础函数

# Step 2: 创建新模块
# lib/api/
# ├── profile.ts (70行, 个人资料)
# ├── api-keys.ts (60行, API密钥)
# ├── security.ts (70行, 安全设置)
# └── user-shared.ts (60行, 共享函数)

# Step 3: 将函数移至对应模块
# 保持导出接口兼容性

# Step 4: 更新所有导入
find src -name "*.ts" -o -name "*.tsx" | xargs sed -i '' \
  's|from "@/lib/api/user"|from "@/lib/api/profile"|g' \
  # ... (针对不同功能的导入)

# Step 5: 考虑创建user.ts作为barrel导出
# export * from './profile';
# export * from './api-keys';
# export * from './security';
# (保持向后兼容性)
```

**执行步骤 - templates.ts拆分**:

```bash
# Step 1: 分析templates.ts的职责
# 职责1: 模板查询和过滤
# 职责2: 模板详情获取
# 职责3: 分类管理
# 职责4: 搜索和推荐

# Step 2: 创建新模块
# lib/api/
# ├── template-query.ts (180行, 查询和过滤)
# ├── template-detail.ts (150行, 详情和缓存)
# ├── template-category.ts (100行, 分类)
# └── template-search.ts (71行, 搜索)

# Step 3: 迁移函数

# Step 4: 更新导入

# Step 5: 考虑创建templates.ts作为barrel导出
```

**检查清单**:
- [ ] user.ts已拆分为4个模块
- [ ] templates.ts已拆分为4个模块
- [ ] 所有导入已更新
- [ ] API功能完整
- [ ] E2E测试通过
- [ ] 提交PR: `refactor: 拆分API层模块`

**预期收益**:
- 单一职责原则: 提升40%
- 代码复用性: 提升30%
- 模块可测试性: 提升50%

---

#### Task 2.5: 创建Barrel导出 (1小时)

**目标**: 为缺失index.ts的目录添加barrel导出

**执行步骤**:

```bash
# 已有index.ts的目录:
# - components/intent/
# - components/superdesign/
# - components/templates/
# - components/versions/

# 需要添加的目录:
# - components/ai/index.ts
# - components/account/index.ts
# - components/wizard/index.ts
# - components/design/index.ts
# - lib/api/index.ts

# 创建 components/ai/index.ts
cat > src/components/ai/index.ts << 'BARREL'
/**
 * AI组件导出
 */

export { AICapabilityCard } from './ai-capability-card';
export { AICapabilityDetailModal } from './ai-capability-detail-modal';
export { AICapabilityPicker } from './ai-capability-picker';
export { AICapabilitySummary } from './ai-capability-summary';
export { CompactModelSelector } from './model-selector';

export type { AICapabilityCardProps } from './ai-capability-card';
export type { AICapabilityDetailProps } from './ai-capability-detail-modal';
export type { AICapabilityPickerProps } from './ai-capability-picker';
export type { AICapabilitySummaryProps } from './ai-capability-summary';
BARREL

# 为其他目录重复类似步骤
```

**检查清单**:
- [ ] 5个目录添加了index.ts
- [ ] 导出接口完整
- [ ] 导入路径验证正确
- [ ] 提交PR: `refactor: 添加barrel导出`

**预期收益**:
- 导入路径简化: `from '@/components/ai'`
- 代码整洁度: 提升20%
- 模块封装: 提升30%

---

#### Task 2.6: 提交Phase 2 (30分钟)

```bash
git add .
git commit -m "refactor: Phase 2 - 拆分大文件和重构API层"
git push origin refactor/phase2-file-split

gh pr create --title "Refactor: Phase 2 - 拆分大文件和API层重构"
```

---

## Phase 3: 质量提升 (3天, 18小时)

### ✅ 优先级: P1 (高优先级)

#### Task 3.1: 补充Hook单元测试 (6小时)

**目标**: 为9个Hook补充单元测试，目标覆盖率90%

**待测试的Hooks**:
```
1. use-generation-websocket.ts (460行)   -> 需要5个测试用例
2. use-generation-task.ts (380行)        -> 需要5个测试用例
3. use-analysis-sse.ts (340行)           -> 需要4个测试用例
4. use-generation-toasts.ts (280行)      -> 需要3个测试用例
5. use-toast.ts (210行)                  -> 需要3个测试用例
6. use-auto-scroll.ts (160行)            -> 需要2个测试用例
7. use-ws-agent-status.ts (190行)        -> 需要3个测试用例
8. use-generation-websocket.ts (已覆盖) -> 0个新增
9. use-ws-agent-status.ts (已覆盖)       -> 0个新增
```

**执行步骤**:

```bash
# Step 1: 创建Hook测试文件
mkdir -p src/hooks/__tests__

# Step 2: 为每个Hook创建测试文件
# use-generation-websocket.test.ts
# use-generation-task.test.ts
# use-analysis-sse.test.ts
# use-generation-toasts.test.ts
# use-toast.test.ts
# use-auto-scroll.test.ts
# use-ws-agent-status.test.ts

# Step 3: 编写测试
# 每个Hook至少包含:
# - 初始化测试
# - 状态更新测试
# - 副作用测试
# - 清理测试
# - 错误处理测试

# Step 4: 运行测试
pnpm test hooks/

# Step 5: 检查覆盖率
pnpm test --coverage hooks/
```

**测试框架选择**:
- 工具: Vitest + @testing-library/react-hooks
- Mock: vitest.mock() + 自定义mock
- 覆盖率目标: 90%

**检查清单**:
- [ ] 7个Hook的测试文件已创建
- [ ] 所有测试用例已编写 (25+个)
- [ ] 覆盖率达到90%
- [ ] CI/CD通过测试
- [ ] 提交PR: `test: 补充Hook单元测试`

**预期收益**:
- Hook代码质量评分: +30%
- Bug发现能力: +50%
- 重构信心度: +70%

---

#### Task 3.2: 补充API客户端测试 (3小时)

**目标**: 为 `lib/api/client.ts` (219行) 补充单元测试

**测试内容**:

```bash
# 测试用例清单:
# 1. get() 函数测试
#    - 成功请求
#    - 失败请求
#    - 超时处理
#    - 错误解析

# 2. post() 函数测试
#    - 成功创建
#    - 验证失败
#    - 重复请求
#    - 上传进度

# 3. put() 函数测试
#    - 成功更新
#    - 部分更新
#    - 冲突处理

# 4. del() 函数测试
#    - 成功删除
#    - 权限错误
#    - 级联删除

# 5. 认证处理测试
#    - Token注入
#    - Token刷新
#    - Token过期处理
```

**执行步骤**:

```bash
# 创建 lib/api/__tests__/client.test.ts
# 编写约20个测试用例
# 目标覆盖率: 85%

pnpm test lib/api/
```

**检查清单**:
- [ ] API客户端测试文件已创建
- [ ] 20+个测试用例已编写
- [ ] 覆盖率达到85%
- [ ] 提交PR: `test: 补充API客户端测试`

**预期收益**:
- API层稳定性: +40%
- 错误处理可靠性: +60%

---

#### Task 3.3: 补充工具函数测试 (2小时)

**目标**: 为 `lib/utils.ts` (180行) 补充单元测试

**测试内容**:
```
- cn() 工具函数 (条件class名)
- 日期格式化函数
- 数据转换函数
- 验证函数
- 其他辅助函数
```

**执行步骤**:

```bash
# 创建 lib/__tests__/utils.test.ts
# 编写约15个测试用例
# 目标覆盖率: 95%

pnpm test lib/
```

**检查清单**:
- [ ] 工具函数测试文件已创建
- [ ] 15+个测试用例已编写
- [ ] 覆盖率达到95%
- [ ] 提交PR: `test: 补充工具函数测试`

**预期收益**:
- 工具函数稳定性: +50%

---

#### Task 3.4: 补充认证工具测试 (1小时)

**目标**: 为 `lib/auth/token.ts` (120行) 补充单元测试

**测试内容**:
```
- Token存储测试
- Token过期检查
- Token刷新逻辑
- 本地存储交互
```

**检查清单**:
- [ ] 认证工具测试文件已创建
- [ ] 10+个测试用例已编写
- [ ] 覆盖率达到90%
- [ ] 提交PR: `test: 补充认证工具测试`

---

#### Task 3.5: 补充E2E测试覆盖 (4小时)

**目标**: 为未覆盖的功能补充E2E测试

**未覆盖功能**:
```
1. API密钥管理流程 (account.spec.ts 补充)
2. 两步验证设置流程 (account.spec.ts 补充)
3. 版本对比详情 (versions.spec.ts 补充)
4. 下载进度跟踪 (download.spec.ts 新增)
```

**执行步骤**:

```bash
# 补充API密钥测试用例到 account.spec.ts
# 补充两步验证测试用例
# 补充版本对比测试用例
# 创建 download.spec.ts 新测试文件

pnpm test:e2e
```

**检查清单**:
- [ ] 4个未覆盖功能已补充测试
- [ ] E2E覆盖率达到85%
- [ ] 所有测试通过
- [ ] 提交PR: `test: 补充E2E测试覆盖`

**预期收益**:
- E2E覆盖率: 72% → 85%
- 用户流程质量: +30%

---

#### Task 3.6: 集成测试框架建设 (2小时)

**目标**: 建立集成测试框架和示例

**执行步骤**:

```bash
# 创建集成测试目录
mkdir -p src/__integration__

# 编写示例集成测试:
# - wizard-flow.integration.test.ts (向导完整流程)
# - publish-flow.integration.test.ts (发布完整流程)
# - api-error-handling.integration.test.ts (API错误处理)

# 创建集成测试文档
# src/__integration__/README.md
```

**检查清单**:
- [ ] 集成测试框架已建立
- [ ] 3个示例集成测试已编写
- [ ] 文档已完善
- [ ] 提交PR: `test: 建立集成测试框架`

**预期收益**:
- 集成测试覆盖率: 0% → 40%
- 系统质量评分: +20%

---

#### Task 3.7: 测试覆盖率目标验证 (1小时)

```bash
# 统计所有测试覆盖率
pnpm test --coverage

# 生成覆盖率报告
pnpm test --coverage -- --reporter=text-summary

# 目标覆盖率:
# - 全体: >85%
# - Hook: 90%
# - API: 85%
# - 工具: 95%
# - 组件: 70%
```

**检查清单**:
- [ ] 整体覆盖率达到85%+
- [ ] Hook覆盖率达到90%
- [ ] 覆盖率报告已生成
- [ ] 提交PR: `test: 验证测试覆盖率达标`

---

#### Task 3.8: 提交Phase 3 (30分钟)

```bash
git add .
git commit -m "test: Phase 3 - 补充单元和集成测试"
git push origin refactor/phase3-unit-tests

gh pr create --title "Test: Phase 3 - 补充单元和集成测试"
```

---

## 总体执行时间表

```
Week 1:
├─ Monday: Phase 0 (2h) + Phase 1 (6h) = 8h
├─ Tuesday: Phase 2.1-2.3 (10h) = 10h
├─ Wednesday: Phase 2.4-2.5 (3h) + Phase 3.1 开始 (2h) = 5h
├─ Thursday: Phase 3.1-3.4 (12h) = 12h
└─ Friday: Phase 3.5-3.8 (7h) = 7h

总计: 42小时 = 5.25个工作日 (按8小时/天)
实际投入: 8个工作日 (考虑code review和bug修复)
```

---

## 成功指标

### 代码质量指标
- [ ] TypeScript错误: 0个
- [ ] ESLint错误: 0个
- [ ] 构建时间: <20秒 (原: ~25秒)
- [ ] 平均文件大小: <250行 (原: 252行)

### 测试指标
- [ ] 单元测试覆盖率: 85%+ (原: <5%)
- [ ] E2E测试覆盖率: 85%+ (原: 72%)
- [ ] 集成测试覆盖率: 40%+ (原: 0%)

### 架构质量指标
- [ ] 重复定义消除: 100% (2个→0个)
- [ ] 超大文件消除: 100% (3个→0个)
- [ ] API层模块化: 提升40%
- [ ] 代码复用性: 提升30%

---

## 风险管理

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 拆分后E2E失败 | 低 | 高 | 分段提交，逐个验证 |
| 导入路径混乱 | 中 | 中 | 使用脚本批量替换+手动检查 |
| 测试编写困难 | 低 | 中 | 提供测试模板和示例 |
| PR review困难 | 低 | 低 | 分为多个小PR |

---

## 后续优化 (Phase 4+, 可选)

### Phase 4: 性能优化 (2天)
- 代码分割优化
- 依赖优化
- 构建优化

### Phase 5: 文档完善 (1天)
- 组件库文档
- API文档
- 开发指南

### Phase 6: DevOps改进 (1天)
- Pre-commit hooks
- CI/CD优化
- 自动化测试

---

## 参考资源

1. **分析报告**: [CODEBASE_ANALYSIS_REPORT.md](./CODEBASE_ANALYSIS_REPORT.md)
2. **度量数据**: [DETAILED_METRICS.md](./DETAILED_METRICS.md)
3. **项目文档**: [README.md](../README.md)
4. **CLAUDE指南**: [/.claude/CLAUDE.md](../.claude/CLAUDE.md)

---

## 问题反馈

在执行过程中遇到问题? 

```bash
# 创建troubleshooting记录
echo "问题描述" > /tmp/issue-YYYYMMDD.txt
# 分析并记录解决方案
# 提交为独立commit或issue
```

---

**制定时间**: 2025-11-14
**预计完成**: 2025-11-28 (2周)
**维护者**: Ingenio Team

