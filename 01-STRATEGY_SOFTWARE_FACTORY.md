# Ingenio Strategy: The Software Factory

> **版本**: 2.0 (Re-baselined for Factory Pivot)
> **日期**: 2025-12-31
> **核心变更**: 从 "辅助编程 Copilot" 转型为 "垂直 SaaS 孵化工厂"。

---

## 1. 核心愿景 (Vision)

**Ingenio (妙构)** 是一个**软件工厂 (Software Factory)**。
我们的核心价值不仅仅是生成代码，而是**生成产品 (Generating Products)**。

通过 **G3 引擎 (Game/Generator/Guard)** 的红蓝博弈机制，Ingenio 能够自动化生产高质量、可运营的垂直 SaaS 应用，并确保其代码安全、逻辑闭环、体验优秀。

---

## 2. 试点项目: ProductShot AI (电商 AI 摄影师)

为了验证工厂能力，我们选择了一个离钱最近、需求最刚的场景进行 "压力测试"。

### 2.1 产品定义
*   **名称**: ProductShot AI
*   **Slogan**: "From White Background to Viral Ad in Seconds."
*   **功能**: 跨境电商卖家上传白底产品图，AI 自动生成精美的高转化率场景图（如放在巴黎咖啡馆桌上的香水）。
*   **商业模式**: 免费试用 + 订阅制 ($29/mo)。

### 2.2 为什么选择它验证 Ingenio?
1.  **全链路验证**: 需要生成前端 (Next.js)、后端 (Spring Boot)、AI 图像管线 (Flux/Replicate)、支付 (Stripe)。这是一个完整的 SaaS 麻雀。
2.  **红蓝博弈场景**: 
    *   **生成 (Player)**: 产出图片。
    *   **质检 (Executor)**: 必须识别 "Logo 变形"、"透视错误"、"光影冲突"。如果 Ingenio 能自动拦截并修复这些 AI 幻觉，就证明了 G3 引擎的价值。

---

## 3. 商业目标

1.  **短期 (Pilot)**: 1 个月内上线 ProductShot AI，获得首批 100 个注册用户。
2.  **中期 (Factory)**: 将 ProductShot AI 的构建流程 "模版化"，支持快速复制到 "房产虚拟软装"、"Logo 生成" 等领域。
3.  **长期 (Ecosystem)**: 开放 Ingenio 平台，让用户通过自然语言 "描述" 就能创建自己的 SaaS 业务。

---

## 4. 关键成功指标 (KPIs)

*   **自动化率**: ProductShot AI 的核心代码（上传组件、支付对接、API 调用）有多少是由 Ingenio 自动生成的？(目标 > 80%)
*   **良品率**: 最终交付给用户的图片，无需人工修图的比例。(目标 > 90%)
