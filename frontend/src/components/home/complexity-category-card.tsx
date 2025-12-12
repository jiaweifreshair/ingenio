"use client";

import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import {
  Zap,
  Layers,
  Box,
  MessageSquare,
  type LucideIcon,
} from "lucide-react";

/**
 * 复杂度分类枚举
 *
 * 对应后端的 ComplexityLevel 和技术栈选择规则
 */
export type ComplexityCategory = 'SIMPLE' | 'MEDIUM' | 'COMPLEX' | 'NEEDS_CONFIRMATION';

/**
 * 复杂度分类配置
 */
export interface ComplexityCategoryConfig {
  /** 分类ID */
  id: ComplexityCategory;
  /** 分类标题 */
  title: string;
  /** 分类描述 */
  description: string;
  /** 技术栈标签 */
  techStack: string;
  /** 示例提示词 */
  examplePrompt: string;
  /** 示例应用列表 */
  examples: string[];
  /** 图标组件 */
  icon: LucideIcon;
  /** 渐变色类名 */
  color: string;
  /** 预计开发时间 */
  estimatedDays: string;
  /** 支持的平台列表（可选） */
  platforms?: string[];
}

/**
 * 4种技术方案分类配置
 *
 * 按代码生成技术栈分类（对应后端 NLRequirementAnalyzer 的分析规则）：
 * 1. H5+WebView（套壳）- 普通多端应用，无原生需求
 * 2. React + Supabase - 纯Web应用
 * 3. React + Spring Boot - 复杂企业应用
 * 4. Kuikly - 需要原生功能（相机、GPS、蓝牙等）
 */
export const COMPLEXITY_CATEGORIES: ComplexityCategoryConfig[] = [
  {
    id: 'SIMPLE',
    title: '多端套壳应用',
    description: '内容展示、表单、列表类，无原生需求',
    techStack: 'H5 + WebView',
    examplePrompt: '做一个简单的待办事项应用，可以添加、编辑、删除任务，设置优先级和截止日期',
    examples: ['待办清单', '新闻阅读', '商品展示', '问卷表单'],
    icon: Zap,
    color: 'from-green-500 to-emerald-500',
    estimatedDays: '1-3天',
  },
  {
    id: 'MEDIUM',
    title: '纯Web应用',
    description: '仅浏览器运行，SaaS或Dashboard',
    techStack: 'React + Supabase',
    examplePrompt: '做一个博客系统，可以发布文章、添加评论、管理分类标签，支持用户注册登录',
    examples: ['博客系统', '管理后台', '数据看板', '预约系统'],
    icon: Layers,
    color: 'from-blue-500 to-cyan-500',
    estimatedDays: '3-7天',
  },
  {
    id: 'COMPLEX',
    title: '企业级应用',
    description: '复杂业务逻辑，多实体关联系统',
    techStack: 'React + Spring Boot',
    examplePrompt: '做一个完整的电商平台，包含商品管理、购物车、订单系统、支付对接、用户管理、权限控制',
    examples: ['电商平台', '企业ERP', '在线教育', '多租户SaaS'],
    icon: Box,
    color: 'from-purple-500 to-violet-500',
    estimatedDays: '7-14天',
  },
  {
    id: 'NEEDS_CONFIRMATION',
    title: '原生跨端应用',
    description: '相机/GPS/蓝牙等原生能力',
    techStack: 'Kuikly',
    examplePrompt: '做一个户外运动打卡应用，用相机拍照记录运动轨迹，使用GPS追踪跑步路线，支持离线模式',
    examples: ['运动打卡', '扫码工具', '地图导航'],
    icon: MessageSquare,
    color: 'from-orange-500 to-amber-500',
    estimatedDays: '5-10天',
    platforms: ['Android', 'iOS', 'HarmonyOS', 'Web', '小程序'],
  },
];

/**
 * 复杂度分类卡片组件Props
 */
export interface ComplexityCategoryCardProps {
  /** 分类配置 */
  category: ComplexityCategoryConfig;
  /** 点击回调 */
  onClick: (category: ComplexityCategoryConfig) => void;
}

/**
 * 复杂度分类卡片组件
 *
 * 用于首页展示4种复杂度分类，点击后填充对应的示例提示词
 */
export function ComplexityCategoryCard({
  category,
  onClick,
}: ComplexityCategoryCardProps): React.ReactElement {
  const Icon = category.icon;

  return (
    <Card
      onClick={() => onClick(category)}
      className={cn(
        "group w-[240px] shrink-0 cursor-pointer transition-all duration-300",
        "hover:shadow-xl hover:-translate-y-2 hover:scale-[1.02]",
        "border-border/50 bg-card/60 backdrop-blur-sm",
        "relative overflow-hidden"
      )}
    >
      {/* 背景渐变装饰 */}
      <div
        className={cn(
          "absolute inset-0 opacity-0 group-hover:opacity-10 transition-opacity duration-300",
          "bg-gradient-to-br",
          category.color
        )}
      />

      <CardContent className="relative p-4 space-y-3">
        {/* 顶部：图标 + 标题 */}
        <div className="flex items-start gap-3">
          <div
            className={cn(
              "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl",
              "bg-gradient-to-br text-white shadow-lg",
              "transition-all duration-300 group-hover:scale-110 group-hover:shadow-xl",
              category.color
            )}
          >
            <Icon className="h-5 w-5" />
          </div>

          <div className="flex-1 min-w-0">
            <h3 className="font-bold text-sm mb-0.5 truncate">{category.title}</h3>
            <p className="text-[11px] text-muted-foreground leading-relaxed line-clamp-2">
              {category.description}
            </p>
          </div>
        </div>

        {/* 技术栈标签 */}
        <div className="flex items-center">
          <Badge
            variant="secondary"
            className="text-[10px] font-medium bg-secondary/80 truncate max-w-[140px]"
          >
            {category.techStack}
          </Badge>
        </div>

        {/* 支持平台（仅Kuikly显示） */}
        {category.platforms && (
          <div className="flex flex-wrap gap-1">
            {category.platforms.map((platform) => (
              <span
                key={platform}
                className="inline-block px-1.5 py-0.5 rounded text-[9px] bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-300"
              >
                {platform}
              </span>
            ))}
          </div>
        )}

        {/* 示例标签 */}
        <div className="flex flex-wrap gap-1">
          {category.examples.map((example) => (
            <span
              key={example}
              className={cn(
                "inline-block px-1.5 py-0.5 rounded-full text-[10px]",
                "bg-muted/50 text-muted-foreground",
                "transition-colors duration-200",
                "group-hover:bg-muted group-hover:text-foreground"
              )}
            >
              {example}
            </span>
          ))}
        </div>

        {/* 悬浮提示 */}
        <div className="text-[9px] text-muted-foreground/70 text-center opacity-0 group-hover:opacity-100 transition-opacity">
          点击使用示例模板
        </div>
      </CardContent>
    </Card>
  );
}
