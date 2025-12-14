/**
 * 三Agent智能工作流展示组件
 *
 * 展示秒构AI的核心工作流：
 * - PlanAgent（规划层）
 * - ExecuteAgent（执行层）
 * - ValidateAgent（校验层）
 */
"use client";

import { Brain, Code, CheckCircle2 } from "lucide-react";

/**
 * Agent配置类型
 */
interface AgentConfig {
  id: string;
  name: string;
  title: string;
  description: string;
  features: string[];
  icon: React.ReactNode;
  color: string;
  bgColor: string;
}

/**
 * 三Agent工作流组件
 */
export function ThreeAgentWorkflow(): React.ReactElement {
  /**
   * 三个Agent的配置
   */
  const agents: AgentConfig[] = [
    {
      id: "plan",
      name: "规划智脑",
      title: "智能需求分析",
      description: "深度理解自然语言需求，自动生成专业架构设计",
      features: [
        "秒级理解复杂业务逻辑",
        "智能拆解功能模块",
        "自动推荐最优技术栈",
        "精准评估开发复杂度",
      ],
      icon: <Brain className="h-8 w-8" />,
      color: "text-purple-600 dark:text-purple-400",
      bgColor: "bg-purple-100 dark:bg-purple-900/30",
    },
    {
      id: "execute",
      name: "代码引擎",
      title: "全栈代码生成",
      description: "一键生成企业级全栈应用，支持多端部署",
      features: [
        "Android + iOS 双端原生代码",
        "自动生成数据库模型",
        "精美UI界面自适应设计",
        "完整API接口开箱即用",
      ],
      icon: <Code className="h-8 w-8" />,
      color: "text-blue-600 dark:text-blue-400",
      bgColor: "bg-blue-100 dark:bg-blue-900/30",
    },
    {
      id: "validate",
      name: "质量卫士",
      title: "智能质量保障",
      description: "全方位自动化测试，确保代码零缺陷交付",
      features: [
        "编译验证 - 100%成功率",
        "智能单元测试 - 覆盖率≥85%",
        "端到端场景测试验证",
        "性能优化与瓶颈检测",
      ],
      icon: <CheckCircle2 className="h-8 w-8" />,
      color: "text-green-600 dark:text-green-400",
      bgColor: "bg-green-100 dark:bg-green-900/30",
    },
  ];

  return (
    <section className="py-12" id="features">
      <div className="container mx-auto px-6">
        {/* 区域标题 */}
        <div className="mb-10 text-center">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground mb-6 tracking-tight">
            <span className="bg-gradient-to-r from-purple-600 via-blue-600 to-green-600 bg-clip-text text-transparent">
              三大 AI 智脑
            </span>
            <span className="block mt-2">让开发全程自动化</span>
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto font-medium">
            从需求理解到代码生成，再到质量保障 —— <span className="text-foreground font-semibold">AI 全程接管</span>
          </p>
        </div>

        {/* Agent卡片网格 */}
        <div className="grid gap-8 md:grid-cols-3 mb-10">
          {agents.map((agent, index) => (
            <div
              key={agent.id}
              className="group relative bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-slate-800 rounded-[2.5rem] p-10 shadow-lg transition-all duration-300 hover:shadow-2xl hover:-translate-y-2 flex flex-col items-center text-center overflow-hidden"
              style={{
                animationDelay: `${index * 150}ms`,
              }}
            >
              {/* 顶部微光装饰 */}
              <div className={`absolute top-0 left-0 w-full h-1 bg-gradient-to-r ${agent.color.replace('text-', 'from-').replace(' dark:text-', ' to-').split(' ')[0]} opacity-50`} />
              
              {/* 背景装饰 (极淡) */}
              <div className={`absolute inset-0 bg-gradient-to-br ${agent.bgColor} opacity-0 group-hover:opacity-30 transition-opacity duration-500`} />

              {/* Agent图标 */}
              <div className={`relative mb-8 inline-flex items-center justify-center w-20 h-20 rounded-3xl ${agent.bgColor} ${agent.color} shadow-inner`}>
                {agent.icon}
              </div>

              {/* Agent信息 */}
              <div className="relative z-10 mb-6">
                <h3 className="mb-3 text-2xl font-bold text-foreground">
                  {agent.name}
                </h3>
                <p className="mb-2 text-sm font-semibold tracking-wide text-muted-foreground uppercase">
                  {agent.title}
                </p>
                <p className="text-muted-foreground leading-relaxed">
                  {agent.description}
                </p>
              </div>

              {/* 功能特性列表 */}
              <ul className="relative z-10 space-y-3 w-full text-left bg-slate-50 dark:bg-slate-800/50 rounded-2xl p-6 mt-auto">
                {agent.features.map((feature, featureIndex) => (
                  <li
                    key={featureIndex}
                    className="flex items-start gap-3 text-sm"
                  >
                    <div className={`mt-1 h-1.5 w-1.5 rounded-full flex-shrink-0 ${agent.color.replace('text-', 'bg-').split(' ')[0]}`} />
                    <span className="text-muted-foreground font-medium">{feature}</span>
                  </li>
                ))}
              </ul>

              {/* 步骤编号 (水印) */}
              <div className="absolute -bottom-4 -right-4 text-[8rem] font-bold opacity-[0.03] select-none pointer-events-none">
                {index + 1}
              </div>
            </div>
          ))}
        </div>

        {/* 工作流程连接线 */}
        <div className="hidden md:flex items-center justify-center gap-4 text-sm font-medium text-muted-foreground bg-slate-100/50 dark:bg-slate-800/30 rounded-full py-4 px-8 w-fit mx-auto backdrop-blur-sm border border-slate-200/50 dark:border-slate-700/50">
          <div className="flex items-center gap-2">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-purple-100 dark:bg-purple-900/50 text-purple-600 text-xs">1</span>
            <span>智能理解</span>
          </div>
          <div className="h-px w-12 bg-gradient-to-r from-purple-200 to-blue-200 dark:from-purple-800 dark:to-blue-800" />
          
          <div className="flex items-center gap-2">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-900/50 text-blue-600 text-xs">2</span>
            <span>代码生成</span>
          </div>
          <div className="h-px w-12 bg-gradient-to-r from-blue-200 to-green-200 dark:from-blue-800 dark:to-green-800" />

          <div className="flex items-center gap-2">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/50 text-green-600 text-xs">3</span>
            <span>质量保障</span>
          </div>
        </div>
      </div>
    </section>
  );
}
