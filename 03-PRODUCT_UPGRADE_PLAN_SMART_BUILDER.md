# Ingenio 产品升级方案：智能化应用构建平台 (Smart App Builder)

**版本**: v2.1-Draft
**日期**: 2025-12-09
**状态**: 规划中 (Planning)

---

## 1. 愿景与定位升级

**从 "代码生成器" 升级为 "AI 原生应用 (AI-Native Apps) 孵化器"**

目前的 Ingenio 擅长生成通用的 CRUD 应用，但缺乏业务深度。本次升级的核心在于引入 **"AI 能力 + 行业模板"** 的双轮驱动模式，帮助用户（特别是学生和初创团队）构建具有智能化“大脑”的现代应用。

*   **旧定位**：一句话生成全栈代码（强调开发效率）。
*   **新定位**：积木式组装行业应用，内置 AI 核心能力（强调应用价值）。

---

## 2. 核心产品模型：The "AI Trinity" (三位一体)

我们将应用拆解为三个核心层级，用户通过自然语言或可视化界面进行组装：

### 层级 1：行业骨架 (Industry Structure) - "Body"
*   **定义**：特定行业的标准业务流、数据模型和 UI 规范。
*   **价值**：解决"冷启动"难题，提供行业 Best Practice。
*   **示例**：
    *   **校园活动版**：报名表单、签到二维码、社团管理。
    *   **电商零售版**：商品展示、购物车、订单流、支付接口。
    *   **企业内训版**：课程播放、考试题库、学习进度追踪。

### 层级 2：AI 能力积木 (AI Capability Blocks) - "Brain"
*   **定义**：封装好的原子化 AI 技术组件，即插即用。
*   **分类**：
    *   👁️ **Vision (视觉)**：OCR 文字提取、物体检测、人脸识别。
    *   🧠 **Language (语言)**：智能润色、自动摘要、RAG 知识库问答、情感分析。
    *   🎙️ **Voice (语音)**：实时语音转写 (ASR)、语音合成 (TTS)。
    *   ⚙️ **Logic (逻辑)**：智能意图路由、自动化工作流。

### 层级 3：预设 Prompt 工程 (Pre-baked Prompts) - "Soul"
*   **定义**：连接业务与 AI 的桥梁。模板中内置调试好的 Prompt，无需用户掌握 Prompt Engineering。
*   **示例**：
    *   *招聘场景*："请提取这份简历 PDF 中的[姓名, 技能, 年限]，并输出为 JSON 格式。"
    *   *客服场景*："根据知识库回答用户关于[退货政策]的问题，语气要亲切。"

---

## 3. UI/UX 改版方案 (Frontend First)

首页将进行重大重构，从"通用介绍"转变为"构建工场"。

### 3.1 Hero Section (意图引导区)
*   **改造点**：保留 Apple 风格 Spotlight 输入框，但在下方增加 **"热门构建场景 (Quick Starters)"** 胶囊。
*   **交互**：点击胶囊（如 `🛒 智能二手交易`、`📄 论文润色助手`），自动填充复杂的 Prompt 组合。
*   **视觉**：增加动态光晕，暗示 AI 就绪状态。

### 3.2 Industry Template Gallery (行业模板长廊)
*   **组件**：`IndustryTemplateGrid` (Bento Grid 布局)。
*   **展示内容**：
    *   行业卡片（教育、电商、生活服务）。
    *   **⚡ AI 能力角标**：在卡片上高亮该模板集成的 AI 能力（如：该模板使用了 `OCR` + `LLM`）。
*   **早期切入点（针对学生）**：
    *   **校园二手街** (图片识别发布商品)。
    *   **智能社团助手** (活动海报生成、报名统计)。
    *   **兼职/实习匹配** (简历解析匹配)。

### 3.3 AI Capability Periodic Table (AI 能力元素表)
*   **组件**：`AICapabilityShowcase`。
*   **设计**：类"元素周期表"或"芯片架构图"风格，展示所有可调用的 AI 原子能力。
*   **目的**：可视化展示平台的技术深度，让用户感知到"积木"的存在。

---

## 4. 技术架构实施路径

### Phase 1: 前端重构与展示 (Current Focus)
*   [ ] **重构首页**：实现新的 Hero, Template Gallery, Capability Showcase 组件。
*   [ ] **数据策略**：优先接入真实 API + 数据库 seed；静态 Mock 仅允许用于设计演示且必须可开关，禁止进入联调/E2E/生产构建。
*   [ ] **交互演示**：点击模板可进入预览模式（暂不连接真实生成）。

### Phase 2: 后端模型与元数据 (Backend)
*   [ ] **元数据存储**：在数据库中建立 `templates`、`capabilities`、`prompt_packs`（或等价命名）等资产表，并具备 `draft/reviewing/published/deprecated` 发布态。
*   [ ] **关联关系**：建立 `TemplateCapabilityRelation`，定义模板默认开启的 AI 能力。
*   [ ] **控制面写入**：资产的编辑/审批/发布由 JeecgBoot 控制面完成；执行面仅提供只读查询（消费 `published`）。
*   [ ] **Spring AI 集成**：引入 Spring AI 或 LangChain4j，构建统一的 `AIService` 接口层。

### Phase 3: AI Agent 智能化组装 (Core Logic)
*   [ ] **Plan 阶段升级**：Agent 不仅生成代码计划，还要检索最匹配的行业模板 ID。
*   [ ] **Execute 阶段升级**：
    *   动态注入 AI SDK 依赖（如 OpenAI Client）。
    *   自动生成调用 `AIService` 的业务代码（如 `resumeService.parse(file)`）。
    *   自动注入预设 Prompt。

---

## 5. 早期验证 Demo：智能招聘助手

为验证此闭环，我们将优先构建一个端到端 Demo：

1.  **用户输入**："我想做一个帮学生修改简历的应用。"
2.  **系统匹配**：选中 `Template: Document_Analysis` + `Capability: LLM_Refine`。
3.  **生成结果**：
    *   前端：包含文件上传区、"一键润色"按钮、修改前后对比视图。
    *   后端：包含 `POST /api/resume/refine` 接口，内部调用 LLM 进行文本优化。

---

## 6. 出海与国内“胶水层”抽象实施（同一套抽象 + 两套实现）

> 目标：在不牺牲交付速度的前提下，同时支持 **国内学生 ToC** 与 **海外出海 ToC** 的关键三方能力（登录/支付/通知/存储/模型/安全等），并保持工程可维护、可审计、可演进。
>
> 约束（已确认/沿用）：JeecgBoot 作为控制面治理资产；执行面只读消费；鉴权 Sa-Token，C 端 JWT=RS256（Bearer）；tenant_id=UUID；Service JWT；短期不引 MQ；Redis + PostgreSQL；敏感字段加密/脱敏与操作留痕。

### 6.1 核心思路：能力抽象（Capability）+ 提供方实现（Provider）

#### 6.1.1 “胶水层”要解决什么问题

把第三方差异（国内 vs 海外）从业务域中剥离，统一为一套可编排的能力接口：

- **身份/登录**：微信生态 / Google-Apple-Microsoft / 邮箱 Magic Link / 账密
- **支付/订阅**：微信支付 / Stripe（Checkout+Subscription+Webhook）
- **通知触达**：小程序订阅消息/站内消息 / Email（SendGrid/SES）
- **存储与分发**：MinIO/S3 兼容 / S3+CDN
- **模型与安全**：国内可用模型路由 / 海外模型；内容安全/敏感词/PII 脱敏
- **可观测与审计**：traceId、结构化日志、字段级脱敏、操作留痕（谁在何时做了什么）

#### 6.1.2 “同一套抽象 + 两套实现”是什么意思

- **同一套抽象**：对上层业务暴露稳定接口（例如 `AuthProvider`、`PaymentProvider`）。
- **两套实现**：按 `region`（或 `market`）选择不同 Provider（例如 CN 用 WeChat；GLOBAL 用 Google+Stripe）。
- **可演进**：后续新增地区/渠道，不改业务域，只新增 Provider 并配置路由。

### 6.2 系统落位（与 JeecgBoot × Ingenio 的边界对齐）

#### 6.2.1 控制面（JeecgBoot）：治理“配置与资产”

JeecgBoot 管的不是业务执行，而是**可发布的能力配置与版本**：

- Provider 类型与版本（例如 `auth.wechat_miniapp@v1`、`payment.stripe@v1`）
- 每个 region/环境对应的 Provider 绑定关系（路由表）
- 第三方密钥引用（不存明文，存 `secretRef`）
- 模板/Prompt/评测集（你已确认全局共享）

> 说明：运营台暂不需要多租户，但“配置/资产”建议仍然按“发布态”管理，便于灰度与回滚。

#### 6.2.2 执行面（Ingenio，Nest BFF）：只读消费 + 执行适配

Nest BFF 负责：

- 根据请求上下文解析 `tenantId` 与 `region`
- 拉取/缓存（Redis）“已发布配置”（从 JeecgBoot Admin API，使用 Service JWT）
- 按路由表选择 Provider 执行
- 写审计日志/脱敏日志（执行面侧落库/落日志）

> 建议：短期胶水层**先落在 Nest BFF 内**，等能力稳定后再抽成独立 `integration-gateway` 服务。

### 6.3 关键抽象设计（接口、路由、配置、密钥）

#### 6.3.1 Region / Market 的确定方式（先定一个口径）

可选策略（推荐按演进顺序）：

1) **按部署区分**（最简单）：
   - CN 部署一套、GLOBAL 部署一套；`REGION=CN|GLOBAL` 由环境变量决定。
2) **按租户区分**（更灵活）：
   - `tenant.region` 写入 DB（UUID tenant_id 不变），BFF 用 tenantId 查 region 决定 Provider。
3) **按域名区分**（体验好）：
   - `cn.ingenio.xxx` / `global.ingenio.xxx` 映射到 region（服务端决定，避免客户端伪造）。

> 建议 Phase 0 先用 #1 或 #3，等多区域多租户需求明确后再上 #2。

#### 6.3.2 Capability 接口（建议先 P0 定义）

以 TypeScript `interface` + Nest DI token 方式实现：

- `AuthProvider`
  - `loginWithWechat(code)`
  - `loginWithOAuth(provider, code)`
  - `issueToken(userIdentity)` → Sa-Token RS256 JWT（统一 claim）
- `PaymentProvider`
  - `createCheckoutSession(...)`
  - `handleWebhook(rawBody, signature)`（必须幂等、可重放）
- `NotificationProvider`
  - `sendTemplateMessage(...)`（CN）
  - `sendEmail(...)`（GLOBAL）
- `StorageProvider`
  - `putObject` / `presignGetUrl`
- `ModelProvider` / `SafetyProvider`
  - `chat(...)` / `moderate(...)` / `redact(...)`

> 输出应统一错误码与可恢复性（例如 `PROVIDER_NOT_CONFIGURED`、`PROVIDER_TEMP_UNAVAILABLE`）。

#### 6.3.3 Provider 路由表（配置驱动）

建议路由键：`{ capability, region, environment }` → `{ providerKey, providerVersion, secretRef }`

示例（逻辑概念）：

- `auth` + `CN` → `wechat_miniapp@v1`
- `auth` + `GLOBAL` → `google_oauth@v1` + `apple_oauth@v1`（可多 Provider）
- `payment` + `CN` → `wechat_pay@v1`
- `payment` + `GLOBAL` → `stripe@v1`

#### 6.3.4 Secret 管理（你已接受“文件托管过渡”）

短期可用 `secretRef=file:/path/to/xxx.json`，并约束：

- 配置中不出现明文 key
- 读取时只在执行面内存存在
- 审计日志禁止输出 secret/敏感字段（统一脱敏器）

中期演进：迁移到 KMS/Secrets（支持轮换），`secretRef=kms://...`。

### 6.4 分阶段实施（建议 4 个 Phase）

#### Phase 0（1~2 周）：抽象落地 + 配置发布闭环

- 产出：
  - `shared/` 里的 Capability 类型定义（契约先行）
  - Nest 侧 `integration` 模块：Provider 接口、路由选择、统一错误码、审计/脱敏拦截器
  - Jeecg 控制面：最小“Provider 路由配置”发布接口（可先用简单表+发布态）
- 验收：
  - CN/GLOBAL 在同一套代码下可切换 Provider（哪怕 Provider 先返回占位错误）
  - 审计日志包含 `requestId/tenantId/region/capability/providerKey`

#### Phase 1（2~3 周）：登录聚合（国内/海外各 1 条通路）

- CN：微信小程序登录（`code2session`）→ 统一身份 → 发放 Bearer JWT
- GLOBAL：Google 登录（OAuth）→ 统一身份 → 发放 Bearer JWT
- 验收：同一用户模型、同一 claim、同一权限模型；可绑定/解绑第三方身份

#### Phase 2（2~4 周）：通知触达 + 存储

- CN：小程序订阅消息（必要时加站内消息兜底）
- GLOBAL：Email（SendGrid/SES）+ 站内消息
- 存储统一：MinIO/S3 兼容接口 + CDN 预留

#### Phase 3（3~6 周）：支付/订阅（强幂等 + 可重放）

- GLOBAL：Stripe（订阅优先，Webhook 幂等、签名校验、重放）
- CN：微信支付（视商业化节奏决定是否进入 P0/P1）
- 验收：订单状态机、对账字段、审计留痕；灰度开关可回滚 Provider

### 6.5 与工程化智能体（AgentScope + Goose）的关系（加速集成质量）

- Provider 接入最容易出错的是：签名校验、回调幂等、类型契约、边界条件。
- 建议把每个 Provider 的“验收脚本集合”纳入工程闭环：
  - `pnpm typecheck` / `pnpm test` / `mvn test` / 关键 E2E
  - 失败后 Goose 负责最小 patch 修复并重跑，产出可审计报告

### 6.6 需要你确认的 3 个关键决策（决定 Phase 0 的实现形态）

1) `region` 的确定方式：按 **部署**（CN/GLOBAL 两套部署）还是按 **租户**（同一部署多 region）？
2) 海外首发登录：优先 **Google** 还是 **Email Magic Link**（会影响 Phase 1 的主路径）？
3) 国内学生是否近期商业化：微信支付是否进入 Phase 2/3，还是后置？

---

✦ 平台就从一个普通的“代码生成器”升级为了“AI 原生应用（AI-Native Apps）孵化器”。

  这直接击中了当前市场的痛点：很多企业懂行业，也知道 AI 厉害，但不知道怎么把 AI 能力“缝合”进自己的业务流里。

  如果你能把“AI 技术能力”封装成标准化的积木（Building Blocks），并预置在行业模板里，这将是降维打击。

  我为你构思了一个 “三层架构” 的产品模型，来看看是否符合你的设想：

  1. 产品模型：AI + 行业 + 能力 (The "AI Trinity")

  我们不仅是生成界面，而是生成带“大脑”的应用。

   * 层级 1：行业骨架 (Industry Structure)
       * 提供该行业标准的 CRUD（增删改查）界面、数据库结构、权限管理。
       * 例如：招聘系统的“候选人列表”、“面试安排表”。
   * 层级 2：AI 能力积木 (AI Capability Blocks)
       * 预封装好的原子化技术组件，用户无需懂代码，只需填 Key 或配置参数。
       * LLM 组件：文本生成、摘要、润色、RAG（知识库问答）。
       * 视觉组件：OCR（文字识别）、物体检测、人脸识别。
       * 语音组件：ASR（语音转文字）、TTS（文字转语音）。
   * 层级 3：预设 Prompt 工程 (Pre-baked Prompts)
       * 这是最核心的“软资产”。模板里不仅有调用 AI 的代码，还有调试好的 Prompt。

  ---

  2. 场景示例：这些模板会有多强？

  让我们看看加入 AI 技术能力后，行业模板会发生什么质变：

  场景 A：法律/咨询行业模板 (Document Intelligence)
   * 传统模板：只是一个文件上传 + 列表展示页面。
   * Ingenio AI 模板：
       * 功能：上传合同 PDF。
       * 集成 AI 能力：调用 LLM (GPT-4/Claude) + OCR。
       * 预设效果：自动提取合同金额、签署日期、风险条款，并生成一段 200 字的摘要填入数据库。
       * 用户价值：不是“存文件”，而是“读文件”。

  场景 B：保险/售后行业模板 (Visual Inspection)
   * 传统模板：用户提交报修单，填一堆文字描述。
   * Ingenio AI 模板：
       * 功能：用户上传受损物品照片。
       * 集成 AI 能力：图像识别 (Vision Model)。
       * 预设效果：AI 自动识别照片内容（如“汽车保险杠刮擦”），初步判断受损程度，自动归类工单优先级。
       * 用户价值：大幅减少人工预审成本。

  场景 C：会议/访谈记录模板 (Voice Productivity)
   * 传统模板：一个富文本编辑器，手动打字。
   * Ingenio AI 模板：
       * 功能：点击录音按钮。
       * 集成 AI 能力：语音转文字 (Whisper) + LLM 总结。
       * 预设效果：实时转录语音，并在结束后自动生成“Action Items（待办事项）”和“会议纪要”。

  ---

  3. 技术实现思路 (Ingenio 架构适配)

  要在现有的 Ingenio 架构中实现这个，我们需要做以下扩展：

   1. 后端 (Spring Boot)：
       * 引入 Spring AI 或 LangChain4j。
       * 建立一个 AI Service Abstraction Layer（AI 服务抽象层），统一接口调用 OpenAI、Google Gemini、Azure 等，方便用户切换模型提供商。
   2. 前端 (Next.js)：
       * 开发一套 "AI 组件库"。例如 <SmartTextArea /> (带 AI 润色功能的文本框)、<MagicUpload /> (带图像分析的上传控件)。
   3. 模板定义：
       * 模板不仅包含代码结构，还要包含 Flow 定义。例如：Input(Image) -> VisionAPI -> Extract JSON -> Save to DB。

  4. 总结

  “AI 生成” + “行业 Know-how” + “AI 能力集成”，这确实是一个非常完美的闭环。

   * 特色：你卖的不是“空房子”（纯软件），而是“带管家和保姆的房子”（智能化软件）。
   * 门槛：用户不需要懂怎么调 API，怎么写 Prompt，你都把这些封装在模板里了。
