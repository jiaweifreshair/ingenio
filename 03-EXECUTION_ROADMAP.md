# Ingenio Execution Roadmap

> **聚焦目标**: ProductShot AI 试点上线
> **当前阶段**: Phase 1 (Prototyping)

---

## ✅ Phase 0: 基础设施 (Completed)
- [x] **JeecgBoot 集成**: 后端环境搭建完成，Stripe 支付模块集成完毕。
- [x] **Next.js 迁移**: 前端架构升级为 Next.js 15 App Router。
- [x] **G3 引擎原型**: 
    - [x] Step 1: 基础类型定义 (`g3.ts`)
    - [x] Step 2: 内存级 TypeScript 裁判 (`typescript-check.ts`)
    - [x] Step 3: Mock Agents (Player/Coach 模拟)
    - [x] Step 4: 调度循环 (`g3-engine.ts`)
    - [x] Step 5: SSE API 网关
    - [x] Step 6: 可视化监控组件 (`G3LogViewer.tsx`)

---

## 🚧 Phase 1: 试点构建 (ProductShot AI Pilot) - [IN PROGRESS]

**目标**: 让 Ingenio "生产" 出 ProductShot AI 的第一个可用版本。

### Step 1: 启动工厂监控 (Visual Confirmation)
- [ ] **Action**: 创建 `frontend/src/app/lab/page.tsx`。
- [ ] **Goal**: 在浏览器中看到 "ProductImageUploader" 组件被自动生成和修复的全过程。

### Step 2: 生产核心组件 (The "Upload & Mask" Module)
- [ ] **Action**: 将 Mock 的逻辑替换为真实的 `replicate` API 调用代码生成。
- [ ] **Goal**: 生成一个真正能用的 React 组件，支持拖拽上传并调用 remove.bg (或类似库) 去背。

### Step 3: 生产图像生成服务 (The "Generation" Module)
- [ ] **Action**: 让 Player Agent 编写调用 Flux.1-Pro 的 Spring Boot Service 代码。
- [ ] **Goal**: 后端能够接收前端传来的 Mask 图，返回 AI 生成的场景图。

---

## 📅 Phase 2: 商业化封装 (SaaS-ification)

- [ ] **支付墙集成**: 生成 "只有付费用户才能下载高清图" 的逻辑代码。
- [ ] **部署**: 将生成的 Next.js + Spring Boot 应用部署到 Vercel/Railway。

---

## 🔮 Phase 3: 扩展 (Scale)

- [ ] **模版化**: 将 ProductShot AI 的构建逻辑提取为 "Image Gen SaaS Template"。
- [ ] **新赛道**: 启动 "房产虚拟软装" 项目，复用 80% 的代码基建。
