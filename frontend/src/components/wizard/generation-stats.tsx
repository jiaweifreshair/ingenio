/**
 * GenerationStats组件
 * 显示应用生成统计数据
 *
 * 展示5个关键指标：
 * 1. 页面数量 - 生成的页面总数
 * 2. API端点 - 生成的API接口数量
 * 3. 数据库表 - 数据库表数量
 * 4. 代码行数 - 生成的代码总行数
 * 5. 生成耗时 - 完整生成流程耗时
 *
 * @author Ingenio Team
 * @since V2.0
 */
'use client';

import { FileText, Zap, Database, Code, Clock } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * 生成统计数据结构
 */
export interface GenerationStats {
  pagesCount: number;         // 页面数量
  apiCount: number;            // API端点数量
  tablesCount: number;         // 数据库表数量
  linesOfCode: number;         // 代码行数
  generationTime: number;      // 生成耗时（秒）
}

/**
 * GenerationStats组件Props
 */
interface GenerationStatsProps {
  stats: GenerationStats;
  className?: string;
}

/**
 * 单个统计项配置
 */
interface StatItem {
  label: string;
  value: string | number;
  icon: LucideIcon;
  color: string;
}

/**
 * 格式化生成耗时
 * @param seconds 总秒数
 * @returns 格式化后的时间字符串（如 "2分35秒"）
 */
function formatGenerationTime(seconds: number): string {
  if (seconds < 60) {
    return `${seconds}秒`;
  }

  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;

  if (remainingSeconds === 0) {
    return `${minutes}分`;
  }

  return `${minutes}分${remainingSeconds}秒`;
}

/**
 * GenerationStats主组件
 */
export function GenerationStats({ stats, className }: GenerationStatsProps) {
  const items: StatItem[] = [
    {
      label: '页面数量',
      value: stats.pagesCount,
      icon: FileText,
      color: 'text-blue-600'
    },
    {
      label: 'API端点',
      value: stats.apiCount,
      icon: Zap,
      color: 'text-yellow-600'
    },
    {
      label: '数据库表',
      value: stats.tablesCount,
      icon: Database,
      color: 'text-green-600'
    },
    {
      label: '代码行数',
      value: stats.linesOfCode.toLocaleString(),
      icon: Code,
      color: 'text-purple-600'
    },
    {
      label: '生成耗时',
      value: formatGenerationTime(stats.generationTime),
      icon: Clock,
      color: 'text-orange-600'
    }
  ];

  return (
    <div className={cn('grid grid-cols-2 md:grid-cols-5 gap-4 p-6 bg-muted/50 rounded-lg', className)}>
      {items.map((item) => {
        const Icon = item.icon;
        return (
          <div key={item.label} className="text-center">
            <Icon className={cn('h-6 w-6 mx-auto mb-2', item.color)} />
            <div className="text-2xl font-bold">{item.value}</div>
            <div className="text-sm text-muted-foreground">{item.label}</div>
          </div>
        );
      })}
    </div>
  );
}
