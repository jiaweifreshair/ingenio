# 秒构AI (Ingenio) - 产品与技术设计方案

> **定位**：面向大学生、创作者、小团队的AI原生应用生成平台
> **目标**：30分钟创建可发布的多端应用
> **核心理念**：透明、可控、多模态、真正一码多端

> ⚠️ 文档状态：**历史方案参考**（包含 KuiklyUI/KMP/Supabase 等口径），与当前“Smart Builder（行业模板 + AI 能力积木 + PromptPack）资产闭环、Jeecg 控制面治理”的方案可能存在冲突；短期实施以 `03-PRODUCT_UPGRADE_PLAN_SMART_BUILDER.md` 为准。

---

## 📊 竞品分析：Ingenio vs CodeFlying

| 维度 | CodeFlying（码上飞） | **Ingenio（秒构AI）** | **优势** |
|------|---------------------|----------------------|----------|
| **生成目标** | 微信小程序/安卓APP/鸿蒙APP/H5 | **Kotlin Multiplatform** (Android/iOS/H5/小程序/鸿蒙) | ✅ **真正一码多端**，共享业务逻辑 |
| **AI架构** | Multi-Agent黑盒（用户看不到决策） | **三Agent透明链 + 时光机** (PlanAgent → ExecuteAgent → ValidateAgent) | ✅ **决策过程可观测**，任意版本回滚对比 |
| **多模态输入** | 仅文本 | **文本 + 语音 + 图像** (QianwenVL视觉理解) | ✅ **截图即可生成**，语音描述需求 |
| **设计能力** | 固定模板库 | **SuperDesign AI多方案并行** (同时生成3个设计方案) | ✅ **AI对比选择**，设计质量更优 |
| **后端方案** | 自有后端（需运维） | **Supabase托管** (商用版→开源版渐进式) | ✅ **零后端代码**，PostgREST自动生成API |
| **爬虫能力** | 无 | **open-lovable-cn ** | ✅ **模板网站抓取**，快速克隆 |
| **调试能力** | 黑盒生成 | **时光机调试** (查看每步AI决策，版本对比) | ✅ **问题可追溯**，优化可验证 |
| **代码质量** | 未知 | **零Mock + E2E测试强制** | ✅ **生产级代码**，真实集成 |
| **开发成本** | 50%传统开发成本 | **30%传统开发成本** (目标) | ✅ **更低成本**，更快交付 |

---

## 🎯 核心创新功能

### 1. ⏮️ 时光机调试（Time Machine Debugger）

**问题**：传统AI生成工具的黑盒问题
- 用户不知道AI为什么做这个决策
- 出错后无法定位问题节点
- 无法对比不同版本的优劣

**解决方案**：三Agent透明决策链 + 版本快照

```
用户需求：构建图书管理系统
    ↓
┌─────────────────────────────────────────────────┐
│ PlanAgent (规划者) - 2分钟                       │
├─────────────────────────────────────────────────┤
│ 输入: "构建图书管理系统，支持借阅和归还"          │
│ 输出:                                            │
│   ✅ 实体设计: Book, User, BorrowRecord          │
│   ✅ 关系设计: User 1:N BorrowRecord N:1 Book    │
│   ✅ 操作设计: 借书、还书、查询、用户注册          │
│   ✅ 技术选型: Kotlin Multiplatform + Supabase   │
│   ✅ UI框架: Compose Multiplatform               │
│ 💾 快照: Plan_v1_20250109_143022                │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│ ExecuteAgent (执行者) - 15分钟                   │
├─────────────────────────────────────────────────┤
│ 步骤1: Supabase Schema生成 (3分钟)               │
│   → CREATE TABLE books (id, title, author...)   │
│   → CREATE TABLE users (id, email, name...)     │
│   → CREATE TABLE borrow_records (...)           │
│   💾 快照: Schema_v1                             │
│                                                  │
│ 步骤2: Kotlin共享层代码生成 (5分钟)              │
│   → data class Book/User/BorrowRecord           │
│   → SupabaseRepository (CRUD operations)        │
│   💾 快照: SharedCode_v1                         │
│                                                  │
│ 步骤3: UI代码生成 (7分钟)                        │
│   → BookListScreen                              │
│   → BorrowScreen                                │
│   → ProfileScreen                               │
│   💾 快照: UI_v1                                 │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│ ValidateAgent (验证者) - 13分钟                  │
├─────────────────────────────────────────────────┤
│ ✅ 编译检查: Kotlin编译通过                       │
│ ✅ 类型检查: 类型安全，无any                      │
│ ⚠️  单元测试: 覆盖率78% (目标85%)                │
│ ✅ E2E测试: 借书流程通过                         │
│ ❌ E2E测试: 还书流程失败 (状态更新错误)           │
│                                                  │
│ 建议: 修复还书流程的状态更新逻辑                  │
│ 💾 快照: Validation_v1_Failed                    │
└─────────────────────────────────────────────────┘
    ↓ (自动修复)
┌─────────────────────────────────────────────────┐
│ ExecuteAgent (二次执行) - 3分钟                  │
├─────────────────────────────────────────────────┤
│ 修复: BorrowRepository.returnBook()             │
│   - 更新book.status = 'available'               │
│   - 更新borrow_record.returned_at = now()       │
│ 💾 快照: Fix_v1                                  │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│ ValidateAgent (二次验证) - 5分钟                 │
├─────────────────────────────────────────────────┤
│ ✅ 编译检查: 通过                                │
│ ✅ 单元测试: 覆盖率87%                           │
│ ✅ E2E测试: 全部通过 (借书+还书+查询)            │
│ 💾 快照: Validation_v2_Success                   │
└─────────────────────────────────────────────────┘
    ↓
✅ 总耗时: 2 + 15 + 13 + 3 + 5 = 38分钟
📦 可发布: Android APK + iOS App + H5 Web
```

**时光机UI界面**

```
┌─────────────────────────────────────────────────┐
│ 🕐 时光机 - 项目版本历史                         │
├─────────────────────────────────────────────────┤
│                                                  │
│  [20:45] ✅ Validation_v2_Success               │
│           ├─ 所有测试通过                        │
│           └─ 代码覆盖率: 87%                     │
│              👁️ 查看详情  🔄 回滚到此版本         │
│                                                  │
│  [20:42] 🔧 Fix_v1                              │
│           └─ 修复还书流程状态更新                 │
│              👁️ 查看代码差异                      │
│                                                  │
│  [20:39] ❌ Validation_v1_Failed                │
│           ├─ E2E测试失败: 还书流程               │
│           └─ 根因: status未更新                  │
│              👁️ 查看失败日志                      │
│                                                  │
│  [20:32] 📝 UI_v1                               │
│           ├─ 生成3个页面                         │
│           └─ 组件数: 12                          │
│              👁️ 预览UI  📊 对比v2差异             │
│                                                  │
│  [20:25] 💾 SharedCode_v1                       │
│           └─ Kotlin共享层                        │
│                                                  │
│  [20:20] 🗄️ Schema_v1                           │
│           └─ 3个表创建成功                       │
│                                                  │
│  [20:18] 📋 Plan_v1                             │
│           └─ 架构规划完成                        │
│                                                  │
└─────────────────────────────────────────────────┘
```

**核心价值**
- ✅ **决策透明**：用户清楚看到每一步AI做了什么
- ✅ **问题定位**：失败节点一目了然，根因分析自动生成
- ✅ **版本对比**：任意两个版本代码对比、性能对比
- ✅ **学习价值**：大学生可以学习AI的决策思路
- ✅ **信任建立**：透明度提升用户对AI的信任

---

### 2. 🎨 SuperDesign AI多方案并行

**问题**：单一设计方案可能不符合用户审美

**解决方案**：AI并行生成3个设计方案，用户对比选择

```
用户输入: "图书管理系统首页"
    ↓
┌─────────────────────────────────────────────────┐
│ SuperDesign AI引擎 - 同时生成3个方案             │
└─────────────────────────────────────────────────┘
    ↓
┌────────────┐  ┌────────────┐  ┌────────────┐
│ 方案A      │  │ 方案B      │  │ 方案C      │
│ 现代极简风  │  │ 卡片网格风  │  │ 列表经典风  │
├────────────┤  ├────────────┤  ├────────────┤
│ 🎨 白色背景 │  │ 🎨 渐变背景 │  │ 🎨 浅灰背景 │
│ 📱 顶部搜索 │  │ 📱 悬浮搜索 │  │ 📱 固定搜索 │
│ 📚 大图封面 │  │ 📚 小卡片   │  │ 📚 列表项   │
│ ⭐ 评分星星 │  │ ⭐ 数字评分 │  │ ⭐ 文字评价 │
│            │  │            │  │            │
│ 😊 清爽    │  │ 😎 时尚    │  │ 🤓 专业    │
│ 📊 用户友好 │  │ 📊 视觉吸引 │  │ 📊 信息密集 │
│            │  │            │  │            │
│ [选择方案A] │  │ [选择方案B] │  │ [选择方案C] │
│ [预览]     │  │ [预览]     │  │ [预览]     │
└────────────┘  └────────────┘  └────────────┘
```

**实现原理**
1. **并行提示词工程**：3个不同的设计风格提示词
   - Prompt A: "现代极简风，大留白，卡片式布局"
   - Prompt B: "活力时尚风，渐变色彩，网格布局"
   - Prompt C: "经典专业风，信息密集，列表布局"

2. **SuperDesign CLI集成**
   ```typescript
   // 调用SuperDesign API并行生成
   const designs = await Promise.all([
     superdesign.generate({ prompt: promptA, style: 'modern' }),
     superdesign.generate({ prompt: promptB, style: 'vibrant' }),
     superdesign.generate({ prompt: promptC, style: 'classic' })
   ]);
   ```

3. **用户反馈学习**
   - 记录用户选择偏好
   - 优化后续生成的风格倾向

---

### 3. 🗣️ 多模态输入（文本 + 语音 + 图像）

**已实现**（Phase 3完成）
- ✅ 文本输入：自然语言描述
- ✅ 语音输入：DashScope Paraformer转文字
- ✅ 图像输入：QianwenVL视觉理解（OCR + UI检测 + 场景理解）

**新增能力**：图像驱动设计复制

```
用户上传截图: 某热门App的首页
    ↓
┌─────────────────────────────────────────────────┐
│ QianwenVL图像分析 (ImageAnalysisService)        │
├─────────────────────────────────────────────────┤
│ 🔍 OCR文本提取:                                  │
│   - "图书推荐"                                   │
│   - "热门借阅排行"                               │
│   - "我的书架"                                   │
│                                                  │
│ 🎨 UI元素检测:                                   │
│   - NavigationBar: 顶部固定，高度56dp            │
│   - SearchBar: 圆角16dp，位置(24, 72)           │
│   - CardView: 卡片列表，间距12dp                 │
│   - Button: "借阅" 按钮，主色调蓝色#2196F3        │
│                                                  │
│ 🏞️ 场景理解:                                     │
│   - 设计风格: 现代Material Design                │
│   - 配色方案: 蓝白主调，温馨友好                  │
│   - 布局模式: 垂直滚动列表 + 顶部固定导航         │
│                                                  │
│ 💡 生成建议:                                     │
│   "检测到图书类App界面，建议使用卡片式布局..."    │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│ ExecuteAgent自动生成相似UI                       │
├─────────────────────────────────────────────────┤
│ 生成Kotlin Compose代码:                          │
│                                                  │
│ @Composable                                      │
│ fun BookListScreen() {                           │
│   Scaffold(                                      │
│     topBar = {                                   │
│       TopAppBar(                                 │
│         title = { Text("图书推荐") }             │
│       )                                          │
│     }                                            │
│   ) {                                            │
│     LazyColumn {                                 │
│       items(books) { book ->                     │
│         BookCard(book)  // 卡片组件              │
│       }                                          │
│     }                                            │
│   }                                              │
│ }                                                │
└─────────────────────────────────────────────────┘
```

---

### 4. 🚀 Kotlin Multiplatform - 真正一码多端

**问题**：CodeFlying虽支持多平台，但每个平台独立代码库

**Ingenio方案**：Kotlin Multiplatform共享业务逻辑

```
┌─────────────────────────────────────────────────┐
│ 共享层 (commonMain)                              │
│ ✅ 数据模型: data class Book/User               │
│ ✅ 业务逻辑: Repository/UseCase                  │
│ ✅ 网络层: Supabase API调用                      │
│ ✅ 数据库: SQLDelight本地缓存                    │
│ ✅ ViewModel: 状态管理                           │
└─────────────────────────────────────────────────┘
           │
    ┌──────┴───────┬───────────┬─────────┐
    │              │           │         │
    ↓              ↓           ↓         ↓
┌────────┐  ┌──────────┐  ┌────────┐  ┌────────┐
│Android │  │   iOS    │  │  H5    │  │ 小程序  │
├────────┤  ├──────────┤  ├────────┤  ├────────┤
│Compose │  │ SwiftUI  │  │ React  │  │ WeChat │
│Material│  │ UIKit    │  │ Web    │  │  MiniP │
└────────┘  └──────────┘  └────────┘  └────────┘
```

**代码复用率**
- ✅ 业务逻辑: **100%共享** (Repository/UseCase/ViewModel)
- ✅ 数据模型: **100%共享** (data class)
- ✅ 网络层: **100%共享** (Supabase Kotlin SDK)
- ⚠️  UI层: **部分共享** (Compose Multiplatform在Android/iOS/Desktop)
- ❌ 平台特性: **0%共享** (推送通知、支付、分享需平台特定实现)

**优势对比**

| 指标 | CodeFlying (多平台独立) | Ingenio (KMP) |
|-----|----------------------|---------------|
| 代码维护成本 | 100% × N个平台 | 60% (共享40%) |
| Bug修复效率 | 需要在N个平台分别修复 | 修复1次，N个平台生效 |
| 功能迭代速度 | N倍工作量 | 1倍工作量 |
| 一致性保证 | 难以保证跨平台一致 | 自动保证业务逻辑一致 |

---

## 🏗️ 技术架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                    用户交互层                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ Web UI   │  │ 语音输入  │  │ 图像上传  │              │
│  │ (Next.js)│  │ (录音)   │  │ (拖拽)   │              │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘              │
└───────┼─────────────┼─────────────┼────────────────────┘
        │             │             │
        ↓             ↓             ↓
┌─────────────────────────────────────────────────────────┐
│              多模态处理层 (已实现Phase 3)                 │
│  ┌──────────────────────────────────────────────────┐   │
│  │ MultimodalInputController                        │   │
│  ├──────────────────────────────────────────────────┤   │
│  │ - textInput(TextInputRequest)                    │   │
│  │ - voiceInput(VoiceInputRequest) → Paraformer    │   │
│  │ - imageInput(ImageInputRequest) → QianwenVL     │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
        │
        ↓ 统一转换为文本需求
┌─────────────────────────────────────────────────────────┐
│                  三Agent决策引擎                         │
│                                                          │
│  ┌─────────────────────────────────────────────┐        │
│  │ PlanAgent (规划者) - Qianwen-Max           │        │
│  ├─────────────────────────────────────────────┤        │
│  │ 输入: 用户需求文本                           │        │
│  │ 输出:                                        │        │
│  │   - 实体设计 (Entities)                     │        │
│  │   - 关系设计 (Relationships)                │        │
│  │   - 操作设计 (Operations)                   │        │
│  │   - 技术选型 (Tech Stack)                   │        │
│  │   - UI框架 (Compose MP/React)              │        │
│  │ 💾 快照: GenerationTask + StructuredReq     │        │
│  └─────────────────────────────────────────────┘        │
│                       ↓                                  │
│  ┌─────────────────────────────────────────────┐        │
│  │ ExecuteAgent (执行者) - Claude Sonnet 4    │        │
│  ├─────────────────────────────────────────────┤        │
│  │ 步骤1: Supabase Schema生成                  │        │
│  │   - DatabaseSchemaGenerator                 │        │
│  │   - 生成DDL SQL                             │        │
│  │   - SupabaseDatabaseService执行             │        │
│  │                                             │        │
│  │ 步骤2: Kotlin共享层代码                     │        │
│  │   - Data Models                             │        │
│  │   - Repository (Supabase SDK)               │        │
│  │   - UseCase业务逻辑                         │        │
│  │                                             │        │
│  │ 步骤3: UI代码生成                           │        │
│  │   Option A: SuperDesign多方案               │        │
│  │   Option B: 爬虫模板克隆                    │        │
│  │   - Compose Multiplatform                   │        │
│  │   - React (H5)                              │        │
│  │                                             │        │
│  │ 💾 快照: GeneratedCodeFiles                 │        │
│  └─────────────────────────────────────────────┘        │
│                       ↓                                  │
│  ┌─────────────────────────────────────────────┐        │
│  │ ValidateAgent (验证者) - 自动化测试         │        │
│  ├─────────────────────────────────────────────┤        │
│  │ ✅ 编译检查: Kotlin Compiler                │        │
│  │ ✅ 类型检查: 无any，严格类型                │        │
│  │ ✅ 单元测试: Kotest + JUnit                 │        │
│  │ ✅ E2E测试: Playwright                      │        │
│  │ ✅ 性能测试: 响应时间 < 3s                  │        │
│  │                                             │        │
│  │ 失败 → 反馈ExecuteAgent修复                 │        │
│  │ 成功 → 标记为可发布版本                     │        │
│  │                                             │        │
│  │ 💾 快照: TestResults + Metrics              │        │
│  └─────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────┘
        │
        ↓
┌─────────────────────────────────────────────────────────┐
│              后端服务层 (Supabase)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ PostgreSQL   │  │ Auth         │  │ Storage      │  │
│  │ + PostgREST  │  │ (JWT)        │  │ (文件)       │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐                     │
│  │ Realtime     │  │ Edge Fns     │                     │
│  │ (WebSocket)  │  │ (Serverless) │                     │
│  └──────────────┘  └──────────────┘                     │
└─────────────────────────────────────────────────────────┘
```

### 核心技术栈

**前端平台**
- ✅ Web: Next.js 15 + React 19 + TypeScript
- ✅ Android/iOS: Kotlin Multiplatform + Compose Multiplatform
- ✅ H5: React + Vite
- ✅ 小程序: 通过Taro框架转译

**后端服务**
- ✅ Spring Boot 3.4 + Java 17 (Ingenio管理平台)
- ✅ Supabase (生成应用的后端托管)
  - PostgreSQL 15 + PostgREST
  - Auth (JWT + OAuth)
  - Storage (MinIO兼容)
  - Realtime (WebSocket)

**AI服务**
- ✅ PlanAgent: Qianwen-Max (需求分析)
- ✅ ExecuteAgent: Claude Sonnet 4 (代码生成)
- ✅ SuperDesign: Claude 4 Sonnet (UI设计)
- ✅ 语音转文字: DashScope Paraformer
- ✅ 图像理解: QianwenVL (多模态)

**沙箱环境**
- ✅ E2B (云端代码执行)
- ✅ Vercel Sandbox (备选方案)

**数据存储**
- ✅ PostgreSQL 15 (Ingenio平台数据)
- ✅ Redis 7 (缓存 + 任务队列)
- ✅ MinIO (文件存储)

**测试框架**
- ✅ 单元测试: JUnit 5 + Mockito (Java) / Kotest (Kotlin)
- ✅ E2E测试: Playwright
- ✅ API测试: RestAssured
- ✅ 性能测试: JMeter

---

## 🔄 零Mock策略 + E2E测试强制

**原则**：所有外部服务集成必须使用真实API，禁止Mock

### 外部服务集成清单

| 服务 | 真实集成方式 | Mock禁止 | E2E测试 |
|-----|------------|---------|---------|
| **Supabase** | PostgreSQL连接 + PostgREST API | ❌ 禁止Mock | ✅ TestContainers |
| **DashScope** | 真实API Key调用 | ❌ 禁止Mock | ✅ 录制回放 |
| **QianwenVL** | 真实图像分析API | ❌ 禁止Mock | ✅ 固定测试图像 |
| **MinIO** | 真实S3兼容存储 | ❌ 禁止Mock | ✅ TestContainers |
| **Redis** | 真实Redis实例 | ❌ 禁止Mock | ✅ TestContainers |
| **E2B Sandbox** | 真实沙箱实例 | ❌ 禁止Mock | ✅ 真实环境 |
| **Firecrawl** | 真实爬虫API | ❌ 禁止Mock | ✅ 录制回放 |

### E2E测试示例

```kotlin
@E2ETest
class BookManagementE2ETest {

    @Test
    fun `完整借阅流程测试`() {
        // 1. 用户注册 (真实Supabase Auth)
        val user = authService.register(
            email = "test@ingenio.ai",
            password = "SecurePass123"
        )
        assertThat(user).isNotNull()

        // 2. 浏览图书列表 (真实PostgreSQL查询)
        val books = bookRepository.findAll()
        assertThat(books).hasSize(10)

        // 3. 借阅图书 (真实业务逻辑 + DB事务)
        val borrowResult = borrowService.borrowBook(
            userId = user.id,
            bookId = books[0].id
        )
        assertThat(borrowResult.success).isTrue()

        // 4. 验证状态更新 (真实DB查询)
        val updatedBook = bookRepository.findById(books[0].id)
        assertThat(updatedBook.status).isEqualTo("borrowed")

        // 5. 归还图书
        val returnResult = borrowService.returnBook(
            borrowRecordId = borrowResult.recordId
        )
        assertThat(returnResult.success).isTrue()

        // 6. 验证最终状态
        val finalBook = bookRepository.findById(books[0].id)
        assertThat(finalBook.status).isEqualTo("available")
    }
}
```

---

## 📱 爬虫能力集成 (OpenLovable)

**场景**：用户提供参考网站，快速生成相似应用

### 爬虫架构（已实现）

```typescript
// lib/scraper/scraper-router.ts
interface ScraperRouter {
  // 优先使用Firecrawl（500%性能提升 + 缓存）
  // 失败时自动降级到Playwright
  scrape(url: string, options: ScraperOptions): Promise<RouterResult>
}

// 使用示例
const router = createScraperRouter({
  preferredScraper: 'firecrawl',
  enableFallback: true,
  firecrawlApiKey: process.env.FIRECRAWL_API_KEY
});

const result = await router.scrape('https://example-book-app.com', {
  waitFor: 3000,
  timeout: 30000,
  blockAds: true,
  fullPageScreenshot: true
});

// 输出
{
  url: 'https://example-book-app.com',
  title: '在线图书馆',
  content: '格式化内容（供AI理解）',
  markdown: '# 在线图书馆\n\n...',
  screenshot: 'data:image/png;base64,...',
  scraper: 'firecrawl',
  fallbackUsed: false
}
```

### 集成到生成流程

```
用户输入: "参考这个网站做一个图书管理系统 https://example.com"
    ↓
┌─────────────────────────────────────────────────┐
│ Step 1: 爬虫抓取                                 │
├─────────────────────────────────────────────────┤
│ ScraperRouter.scrape(url)                       │
│   → HTML结构                                     │
│   → 截图                                         │
│   → Markdown内容                                 │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│ Step 2: QianwenVL图像分析                       │
├─────────────────────────────────────────────────┤
│ ImageAnalysisService.analyze(screenshot)        │
│   → UI元素: Button, Card, List...              │
│   → 布局: 网格/列表/Tab...                      │
│   → 配色: 主色调、辅助色                         │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│ Step 3: PlanAgent需求分析                       │
├─────────────────────────────────────────────────┤
│ 输入:                                            │
│   - 用户文本: "做一个图书管理系统"               │
│   - 爬虫内容: Markdown + HTML结构                │
│   - 图像分析: UI元素 + 布局模式                  │
│                                                  │
│ 输出:                                            │
│   - 实体设计: Book, BorrowRecord...             │
│   - UI设计: 参考原网站的卡片布局                 │
│   - 配色方案: 复用原网站的蓝白主调               │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│ Step 4: ExecuteAgent代码生成                    │
├─────────────────────────────────────────────────┤
│ 生成与原网站相似度90%的UI代码                    │
└─────────────────────────────────────────────────┘
```

---

## 🎯 30分钟生成流程拆解

**目标**：从需求到可发布应用 ≤ 30分钟

| 阶段 | 时间 | 操作 | 自动化程度 |
|-----|------|------|-----------|
| **需求输入** | 2分钟 | 用户文本/语音/图像描述 | 人工 |
| **PlanAgent规划** | 3分钟 | 实体设计+关系+技术选型 | 100%自动 |
| **SuperDesign多方案** | 5分钟 | 并行生成3个UI设计 | 100%自动 |
| **用户选择** | 2分钟 | 对比选择最优设计 | 人工 |
| **Schema生成** | 2分钟 | DDL生成+Supabase执行 | 100%自动 |
| **代码生成** | 8分钟 | Kotlin共享层+UI层 | 100%自动 |
| **自动测试** | 5分钟 | 编译+单元测试+E2E | 100%自动 |
| **修复迭代** | 3分钟 | ValidateAgent反馈修复 | 80%自动 |
| **发布打包** | 0分钟 | 后台自动构建APK/IPA | 100%自动 |
| **总计** | **30分钟** | | **人工参与4分钟** |

---

## 📊 成本对比

### 传统开发 vs Ingenio

| 项目规模 | 传统开发成本 | Ingenio成本 | 节省 |
|---------|------------|------------|------|
| **简单应用** (图书管理) | 30人天 × ¥800 = ¥24,000 | 8人天 × ¥800 = ¥6,400 | **73%** |
| **中等应用** (电商系统) | 90人天 × ¥800 = ¥72,000 | 25人天 × ¥800 = ¥20,000 | **72%** |
| **复杂应用** (社交平台) | 180人天 × ¥800 = ¥144,000 | 50人天 × ¥800 = ¥40,000 | **72%** |

**节省来源**
1. ✅ 后端零代码（Supabase托管）: -40%
2. ✅ Kotlin Multiplatform共享: -20%
3. ✅ AI自动生成UI: -30%
4. ✅ 自动化测试: -10%

### 运营成本对比（月成本）

| 用户规模 | Supabase商用版 | 传统自建后端 | 节省 |
|---------|---------------|------------|------|
| 0-5万 MAU | Free (¥0) | ¥3,000 (服务器+运维) | 100% |
| 5-10万 MAU | ¥180 ($25) | ¥8,000 | 98% |
| 10-50万 MAU | ¥4,300 ($599) | ¥30,000 | 86% |

---

## 🚀 下一步实施计划

### Phase 1: 核心Agent引擎 (2周)
- [ ] PlanAgent需求分析引擎
- [ ] ExecuteAgent代码生成引擎
- [ ] ValidateAgent测试验证引擎
- [ ] 时光机版本快照系统

### Phase 2: SuperDesign集成 (1周)
- [ ] SuperDesign API封装
- [ ] 多方案并行生成
- [ ] 用户选择UI界面

### Phase 3: Kotlin Multiplatform模板 (2周)
- [ ] KMP项目脚手架
- [ ] Compose Multiplatform组件库
- [ ] Supabase Kotlin SDK集成

### Phase 4: 爬虫能力增强 (1周)
- [ ] Firecrawl集成优化
- [ ] 图像驱动设计复制
- [ ] 模板库建设

### Phase 5: 测试与优化 (1周)
- [ ] E2E测试套件
- [ ] 性能基准测试
- [ ] 用户体验优化

### Phase 6: Beta发布 (1周)
- [ ] 大学生内测
- [ ] 收集反馈迭代
- [ ] 正式发布

**总计**：8周MVP上线

---

## 📝 总结

**Ingenio核心竞争力**
1. ✅ **透明可控**：时光机调试，AI决策过程可视化
2. ✅ **真正一码多端**：Kotlin Multiplatform，业务逻辑100%共享
3. ✅ **多模态输入**：文本+语音+图像，降低使用门槛
4. ✅ **AI设计多方案**：SuperDesign并行3个方案，质量更优
5. ✅ **零后端成本**：Supabase托管，PostgREST自动生成API
6. ✅ **爬虫克隆**：快速复制参考网站
7. ✅ **生产级代码**：零Mock + E2E测试强制

**目标用户价值**
- 🎓 **大学生**：30分钟做出毕设项目
- 🎨 **创作者**：快速验证创意想法
- 👥 **小团队**：70%成本节省，快速MVP

**vs CodeFlying优势**
- ✅ 更透明（时光机 vs 黑盒）
- ✅ 更智能（多模态 vs 纯文本）
- ✅ 更优质（AI多方案 vs 固定模板）
- ✅ 更高效（KMP共享 vs 多平台独立）
- ✅ 更省钱（Supabase vs 自建后端）
