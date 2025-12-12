/**
 * 目标用户组别展示组件
 *
 * 展示秒构智能生成式应用程序设计挑战赛的四个参赛组别：
 * - 小学组
 * - 初中组
 * - 高中组
 * - 中职组
 */
"use client";

import { Sparkles, Lightbulb, Rocket, Briefcase } from "lucide-react";

/**
 * 组别配置类型
 */
interface AudienceGroup {
  id: string;
  name: string;
  icon: React.ReactNode;
  color: string;
  bgColor: string;
  focus: string;
  capabilities: string[];
  suitable: string[];
}

/**
 * 目标用户组别组件
 */
export function TargetAudiences(): React.ReactElement {
  /**
   * 四个组别的配置
   */
  const groups: AudienceGroup[] = [
    {
      id: "primary",
      name: "小学组",
      icon: <Sparkles className="h-6 w-6" />,
      color: "text-pink-600 dark:text-pink-400",
      bgColor: "bg-pink-100 dark:bg-pink-900/30",
      focus: "创意思维与需求表达",
      capabilities: [
        "用自然语言清晰描述问题",
        "理解应用的基本功能",
        "生成简单应用",
        "学习使用时光机版本管理",
      ],
      suitable: ["学习工具", "趣味游戏", "创意应用"],
    },
    {
      id: "middle",
      name: "初中组",
      icon: <Lightbulb className="h-6 w-6" />,
      color: "text-blue-600 dark:text-blue-400",
      bgColor: "bg-blue-100 dark:bg-blue-900/30",
      focus: "问题分解与逻辑思维",
      capabilities: [
        "将复杂需求拆解为功能模块",
        "理解应用的数据流和逻辑",
        "生成中等复杂度应用",
        "理解AI Agent三层架构",
      ],
      suitable: ["校园助手", "学习管理", "团队协作"],
    },
    {
      id: "high",
      name: "高中组",
      icon: <Rocket className="h-6 w-6" />,
      color: "text-purple-600 dark:text-purple-400",
      bgColor: "bg-purple-100 dark:bg-purple-900/30",
      focus: "系统架构与创新设计",
      capabilities: [
        "完整的系统架构设计思维",
        "深度利用三Agent协作机制",
        "设计创新性应用架构",
        "使用SuperDesign等高级特性",
      ],
      suitable: ["智能系统", "数据分析", "创新应用"],
    },
    {
      id: "vocational",
      name: "中职组",
      icon: <Briefcase className="h-6 w-6" />,
      color: "text-orange-600 dark:text-orange-400",
      bgColor: "bg-orange-100 dark:bg-orange-900/30",
      focus: "场景洞察与专业融合",
      capabilities: [
        "结合专业（电商、设计等）",
        "深入理解行业痛点",
        "快速迭代验证商业想法",
        "注重实用性和商业价值",
      ],
      suitable: ["行业应用", "商业工具", "专业系统"],
    },
  ];

  return (
    <section className="w-full py-24 bg-slate-50/50 dark:bg-slate-950/50">
      <div className="container mx-auto px-6">
        {/* 区域标题 */}
        <div className="mb-16 text-center">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground mb-6 tracking-tight">
            适合所有年龄段的
            <span className="block bg-gradient-to-r from-pink-500 to-violet-500 bg-clip-text text-transparent mt-2">
              学习者与创新者
            </span>
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto font-medium">
            从基础启蒙到专业实践，每个阶段都有专属成长路径
          </p>
        </div>

        {/* 组别卡片网格 */}
        <div className="grid gap-8 md:grid-cols-2 lg:grid-cols-4">
          {groups.map((group, index) => (
            <div
              key={group.id}
              className="group relative flex flex-col bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-[2.5rem] p-8 shadow-sm transition-all duration-300 hover:shadow-xl hover:-translate-y-1 overflow-hidden"
              style={{
                animationDelay: `${index * 100}ms`,
              }}
            >
              {/* 顶部彩色线条装饰 */}
              <div className={`absolute top-0 left-0 w-full h-1.5 bg-gradient-to-r ${group.color.replace('text-', 'from-').replace(' dark:text-', ' to-').split(' ')[0]} opacity-80`} />

              {/* 组别图标和名称 */}
              <div className="mb-6">
                <div className={`inline-flex items-center justify-center w-14 h-14 rounded-2xl ${group.bgColor} ${group.color} mb-4 shadow-inner`}>
                  {group.icon}
                </div>
                <h3 className="text-2xl font-bold text-foreground mb-1">
                  {group.name}
                </h3>
                <p className={`text-sm font-semibold ${group.color} tracking-wide uppercase`}>
                  {group.focus}
                </p>
              </div>

              {/* 核心能力 */}
              <div className="flex-1 mb-8">
                <p className="mb-3 text-xs font-bold text-muted-foreground uppercase tracking-wider">
                  核心能力
                </p>
                <ul className="space-y-3">
                  {group.capabilities.map((capability, capIndex) => (
                    <li
                      key={capIndex}
                      className="flex items-start gap-2 text-sm text-muted-foreground leading-snug"
                    >
                      <div className={`mt-1.5 h-1.5 w-1.5 rounded-full flex-shrink-0 ${group.color.replace('text-', 'bg-').split(' ')[0]}`} />
                      <span>{capability}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {/* 适合场景标签 */}
              <div className="mt-auto">
                <div className="flex flex-wrap gap-2">
                  {group.suitable.map((scene, sceneIndex) => (
                    <span
                      key={sceneIndex}
                      className="inline-flex items-center px-3 py-1 rounded-full bg-slate-100 dark:bg-slate-800 text-xs font-medium text-slate-600 dark:text-slate-300"
                    >
                      {scene}
                    </span>
                  ))}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
