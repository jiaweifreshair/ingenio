'use client';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import type { ProjectStats } from '@/types/project';
import { BarChart3, FileText, Loader2, CheckCircle2 } from 'lucide-react';

/**
 * 统计卡片组件
 * 展示4个关键指标
 *
 * @param stats 统计数据
 */
export function StatsCards({ stats }: { stats: ProjectStats }): React.ReactElement {
  const cards = [
    {
      title: '总应用数',
      value: stats.totalProjects,
      icon: BarChart3,
      description: `本月新增 ${stats.monthlyNewProjects} 个`,
      color: 'text-blue-600',
      bgColor: 'bg-blue-50',
    },
    {
      title: '生成中',
      value: stats.generatingTasks,
      icon: Loader2,
      description: '正在生成的任务',
      color: 'text-yellow-600',
      bgColor: 'bg-yellow-50',
    },
    {
      title: '生成完成',
      value: stats.publishedProjects,
      icon: CheckCircle2,
      description: '代码生成完成的应用',
      color: 'text-green-600',
      bgColor: 'bg-green-50',
    },
    {
      title: '草稿',
      value: stats.draftProjects,
      icon: FileText,
      description: '未发布的草稿',
      color: 'text-gray-600',
      bgColor: 'bg-gray-50',
    },
  ];

  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
      {cards.map((card) => (
        <Card key={card.title} className="transition-all hover:shadow-lg">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              {card.title}
            </CardTitle>
            <div className={`rounded-full p-2 ${card.bgColor}`}>
              <card.icon className={`h-4 w-4 ${card.color}`} />
            </div>
          </CardHeader>
          <CardContent>
            <div className={`text-2xl font-bold ${card.color}`}>
              {card.value}
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {card.description}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
