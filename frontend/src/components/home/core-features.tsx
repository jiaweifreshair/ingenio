'use client';

import { useRouter } from 'next/navigation';
import { Sparkles, Clock, ArrowRight } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useLanguage } from "@/contexts/LanguageContext";

/**
 * 核心功能展示组件（双卡片布局）
 *
 * 仅保留并突出右侧的两个模块：
 * - AI 能力矩阵
 * - 时光机回溯
 *
 * 桌面端双列展示、移动端纵向堆叠，保持 Apple Bento 质感与微交互。
 */
export function CoreFeatures(): React.ReactElement {
  const router = useRouter();
  const { t } = useLanguage();

  return (
    <section className="py-12 relative overflow-hidden">
      {/* 背景装饰 */}
      <div className="absolute inset-0 -z-10 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-100 via-background to-background dark:from-slate-900 dark:via-background dark:to-background" />

      <div className="container mx-auto px-6 relative z-10">
        {/* 标题区域 */}
        <div className="text-center mb-10">
          <h2 className="text-4xl md:text-5xl font-bold text-foreground mb-6 tracking-tight">
            {t('features.title')}
          </h2>
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto font-medium">
            {t('features.subtitle')}
          </p>
        </div>

        {/* 功能卡片布局 */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 max-w-5xl mx-auto">

          {/* 1. AI 能力矩阵 */}
          <div className="group relative bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-slate-800 rounded-[2rem] shadow-xl p-10 md:p-12 overflow-hidden hover:shadow-2xl transition-all duration-300 min-h-[280px] flex flex-col">
            <div className="absolute inset-0 bg-gradient-to-br from-purple-50/60 via-transparent to-transparent dark:from-purple-900/10 opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
            <div className="absolute top-0 right-0 p-6 opacity-10 group-hover:opacity-20 transition-opacity">
              <Sparkles className="w-24 h-24 text-purple-500" />
            </div>

            <div className="relative z-10">
              <div className="w-12 h-12 rounded-2xl bg-purple-100 dark:bg-purple-900/30 flex items-center justify-center mb-6 text-purple-600">
                <Sparkles className="w-6 h-6" />
              </div>
              <h3 className="text-2xl font-bold text-foreground mb-3">{t('features.card_ai_title')}</h3>
              <p className="text-muted-foreground leading-relaxed mb-6">
                {t('features.card_ai_desc')}
              </p>
            </div>

            <Button
              variant="ghost"
              onClick={() => router.push('/wizard/ai-capabilities')}
              className="mt-auto p-0 h-auto text-purple-600 hover:text-purple-700 hover:bg-transparent group-hover:translate-x-1 transition-transform font-medium"
            >
              {t('features.card_ai_btn')} <ArrowRight className="ml-1 w-3 h-3" />
            </Button>
          </div>

          {/* 2. 时光机回溯 */}
          <div className="group relative bg-white dark:bg-slate-900/50 border border-slate-200 dark:border-slate-800 rounded-[2rem] shadow-xl p-10 md:p-12 overflow-hidden hover:shadow-2xl transition-all duration-300 min-h-[280px] flex flex-col">
            <div className="absolute inset-0 bg-gradient-to-br from-orange-50/60 via-transparent to-transparent dark:from-orange-900/10 opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
            <div className="absolute top-0 right-0 p-6 opacity-10 group-hover:opacity-20 transition-opacity">
              <Clock className="w-24 h-24 text-orange-500" />
            </div>

            <div className="relative z-10">
              <div className="w-12 h-12 rounded-2xl bg-orange-100 dark:bg-orange-900/30 flex items-center justify-center mb-6 text-orange-600">
                <Clock className="w-6 h-6" />
              </div>
              <h3 className="text-2xl font-bold text-foreground mb-3">{t('features.card_time_title')}</h3>
              <p className="text-muted-foreground leading-relaxed mb-6">
                {t('features.card_time_desc')}
              </p>
            </div>

            <Button
              variant="ghost"
              onClick={() => router.push('/dashboard')}
              className="mt-auto p-0 h-auto text-orange-600 hover:text-orange-700 hover:bg-transparent group-hover:translate-x-1 transition-transform font-medium"
            >
              {t('features.card_time_btn')} <ArrowRight className="ml-1 w-3 h-3" />
            </Button>
          </div>
        </div>
      </div>
    </section>
  );
}
