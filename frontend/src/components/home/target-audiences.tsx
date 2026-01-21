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
import { useLanguage } from "@/contexts/LanguageContext";

/**
 * 组别配置类型
 */
interface AudienceGroup {
  id: string;
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
  const { t } = useLanguage();

  /**
   * 四个组别的配置
   */
  const groups: AudienceGroup[] = [
    {
      id: "primary",
      icon: <Sparkles className="h-6 w-6" />,
      color: "text-pink-600 dark:text-pink-400",
      bgColor: "bg-pink-100 dark:bg-pink-900/30",
      focus: t('audiences.groups.primary.focus'),
      capabilities: [
        t('audiences.groups.primary.cap1'),
        t('audiences.groups.primary.cap2'),
        t('audiences.groups.primary.cap3'),
        t('audiences.groups.primary.cap4'),
      ],
      suitable: [t('audiences.groups.primary.suit1'), t('audiences.groups.primary.suit2'), t('audiences.groups.primary.suit3')],
    },
    {
      id: "middle",
      icon: <Lightbulb className="h-6 w-6" />,
      color: "text-blue-600 dark:text-blue-400",
      bgColor: "bg-blue-100 dark:bg-blue-900/30",
      focus: t('audiences.groups.middle.focus'),
      capabilities: [
        t('audiences.groups.middle.cap1'),
        t('audiences.groups.middle.cap2'),
        t('audiences.groups.middle.cap3'),
        t('audiences.groups.middle.cap4'),
      ],
      suitable: [t('audiences.groups.middle.suit1'), t('audiences.groups.middle.suit2'), t('audiences.groups.middle.suit3')],
    },
    {
      id: "high",
      icon: <Rocket className="h-6 w-6" />,
      color: "text-purple-600 dark:text-purple-400",
      bgColor: "bg-purple-100 dark:bg-purple-900/30",
      focus: t('audiences.groups.high.focus'),
      capabilities: [
        t('audiences.groups.high.cap1'),
        t('audiences.groups.high.cap2'),
        t('audiences.groups.high.cap3'),
        t('audiences.groups.high.cap4'),
      ],
      suitable: [t('audiences.groups.high.suit1'), t('audiences.groups.high.suit2'), t('audiences.groups.high.suit3')],
    },
    {
      id: "vocational",
      icon: <Briefcase className="h-6 w-6" />,
      color: "text-orange-600 dark:text-orange-400",
      bgColor: "bg-orange-100 dark:bg-orange-900/30",
      focus: t('audiences.groups.vocational.focus'),
      capabilities: [
        t('audiences.groups.vocational.cap1'),
        t('audiences.groups.vocational.cap2'),
        t('audiences.groups.vocational.cap3'),
        t('audiences.groups.vocational.cap4'),
      ],
      suitable: [t('audiences.groups.vocational.suit1'), t('audiences.groups.vocational.suit2'), t('audiences.groups.vocational.suit3')],
    },
  ];

  return (
    <section className="w-full py-12 bg-slate-50/50 dark:bg-slate-950/50">
      <div className="container mx-auto px-6">
        {/* 区域标题 */}
        <div className="mb-10 text-center">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground mb-6 tracking-tight">
            {t('audiences.title')}
            <span className="block bg-gradient-to-r from-pink-500 to-violet-500 bg-clip-text text-transparent mt-2">
              {t('audiences.title_highlight')}
            </span>
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto font-medium">
            {t('audiences.subtitle')}
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
                  {group.focus}
                </h3>
              </div>

              {/* 核心能力 */}
              <div className="flex-1 mb-8">
                <p className="mb-3 text-xs font-bold text-muted-foreground uppercase tracking-wider">
                  {t('audiences.core_capabilities')}
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
