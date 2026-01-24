/**
 * QuickActionCards组件
 * Wizard完成页快速操作卡片
 *
 * 提供5个快速操作入口：
 * 1. 下载代码 - 下载全栈代码
 * 2. SuperDesign方案 - 查看7种设计风格
 * 3. 配置发布 - 多端发布配置
 * 4. 应用设置 - 管理应用配置
 * 5. 分享应用 - 生成分享链接
 *
 * @author Ingenio Team
 * @since V2.0
 */
'use client';

import { Card, CardContent, CardDescription, CardTitle } from '@/components/ui/card';
import { cn } from '@/lib/utils';
import {
  Download,
  Palette,
  Rocket,
  Settings,
  Share2,
} from 'lucide-react';
import Link from 'next/link';
import type { LucideIcon } from 'lucide-react';

/**
 * 快速操作卡片配置
 */
interface QuickActionCard {
  title: string;
  description: string;
  icon: LucideIcon;
  href?: string;
  onClick?: () => void;
  variant: 'default' | 'primary' | 'success';
}

/**
 * QuickActionCards组件Props
 */
interface QuickActionCardsProps {
  appId: string;
  projectId: string;
  onDownload: () => void;
  onShare: () => void;
  className?: string;
}

/**
 * 单个快速操作卡片组件
 */
function ActionCard({
  title,
  description,
  icon: Icon,
  href,
  onClick,
  variant
}: QuickActionCard) {
  const variantStyles = {
    default: 'hover:border-gray-300 dark:hover:border-gray-600',
    primary: 'border-blue-200 hover:border-blue-400 dark:border-blue-800 dark:hover:border-blue-600',
    success: 'border-green-200 hover:border-green-400 dark:border-green-800 dark:hover:border-green-600'
  };

  const content = (
    <>
      <div className="flex items-start justify-between">
        <Icon className="h-8 w-8 text-primary" />
      </div>
      <CardTitle className="mt-4 text-lg">{title}</CardTitle>
      <CardDescription className="mt-2">{description}</CardDescription>
    </>
  );

  if (href) {
    return (
      <Link href={href}>
        <Card
          className={cn(
            'cursor-pointer transition-all hover:shadow-md',
            variantStyles[variant]
          )}
        >
          <CardContent className="p-6">{content}</CardContent>
        </Card>
      </Link>
    );
  }

  return (
    <Card
      className={cn(
        'cursor-pointer transition-all hover:shadow-md',
        variantStyles[variant]
      )}
      onClick={onClick}
    >
      <CardContent className="p-6">{content}</CardContent>
    </Card>
  );
}

/**
 * QuickActionCards主组件
 */
export function QuickActionCards({
  appId,
  projectId,
  onDownload,
  onShare,
  className
}: QuickActionCardsProps) {
  const cards: QuickActionCard[] = [
    {
      title: '下载代码',
      description: '下载全栈代码到本地',
      icon: Download,
      onClick: onDownload,
      variant: 'success'
    },
    {
      title: 'SuperDesign方案',
      description: '查看7种设计风格方案',
      icon: Palette,
      href: `/superdesign/${appId}`,
      variant: 'default'
    },
    {
      title: '配置发布',
      description: '配置多端发布参数',
      icon: Rocket,
      href: `/publish/${appId}`,
      variant: 'default'
    },
    {
      title: '应用设置',
      description: '管理应用基础配置',
      icon: Settings,
      href: `/settings/${projectId}`,
      variant: 'default'
    },
    {
      title: '分享应用',
      description: '生成分享链接',
      icon: Share2,
      onClick: onShare,
      variant: 'default'
    }
  ];

  return (
    <div className={cn('grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4', className)}>
      {cards.map((card) => (
        <ActionCard key={card.title} {...card} />
      ))}
    </div>
  );
}
