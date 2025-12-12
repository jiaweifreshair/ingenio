"use client";

import { Card, CardContent, CardFooter } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  Template,
  TemplateDifficulty,
  TargetPlatform,
} from "@/types/template";
import { Star, Users, Smartphone, Globe, Zap } from "lucide-react";

/**
 * 模板卡片组件属性
 */
export interface TemplateCardProps {
  /** 模板数据 */
  template: Template;
  /** 点击回调 */
  onClick?: (template: Template) => void;
}

/**
 * 获取难度显示配置
 */
function getDifficultyConfig(difficulty: TemplateDifficulty): {
  label: string;
  color: string;
} {
  switch (difficulty) {
    case TemplateDifficulty.SIMPLE:
      return { label: "简单", color: "bg-green-100 text-green-700" };
    case TemplateDifficulty.MEDIUM:
      return { label: "中等", color: "bg-yellow-100 text-yellow-700" };
    case TemplateDifficulty.COMPLEX:
      return { label: "复杂", color: "bg-red-100 text-red-700" };
  }
}

/**
 * 获取平台图标
 */
function getPlatformIcon(platform: TargetPlatform): React.ReactNode {
  switch (platform) {
    case TargetPlatform.ANDROID:
    case TargetPlatform.IOS:
    case TargetPlatform.HARMONY:
      return <Smartphone className="h-3 w-3" />;
    case TargetPlatform.WEB:
    case TargetPlatform.WECHAT:
      return <Globe className="h-3 w-3" />;
  }
}

/**
 * 模板卡片组件
 * 用于模板库页面的模板展示
 */
export function TemplateCard({
  template,
  onClick,
}: TemplateCardProps): React.ReactElement {
  const difficultyConfig = getDifficultyConfig(template.difficulty);

  return (
    <Card
      data-testid="template-card"
      className={cn(
        "group relative h-full cursor-pointer overflow-hidden",
        "transition-all duration-300 hover:shadow-xl hover:-translate-y-1",
        "border-border/50 bg-card/50 backdrop-blur-sm"
      )}
      onClick={() => onClick?.(template)}
    >
      {/* 封面图区域 */}
      <div className="relative h-48 w-full overflow-hidden bg-gradient-to-br from-primary/10 to-primary/5">
        {template.coverImage ? (
          <img
            src={template.coverImage}
            alt={template.name}
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-110"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center">
            <Zap className="h-16 w-16 text-primary/30" />
          </div>
        )}
        {/* 难度徽章 */}
        <div className="absolute right-3 top-3">
          <Badge className={cn("font-medium", difficultyConfig.color)}>
            {difficultyConfig.label}
          </Badge>
        </div>
      </div>

      {/* 内容区域 */}
      <CardContent className="p-5">
        {/* 标题 */}
        <h3 className="mb-2 line-clamp-1 text-lg font-semibold">
          {template.name}
        </h3>

        {/* 描述 */}
        <p className="mb-4 line-clamp-2 text-sm text-muted-foreground">
          {template.description}
        </p>

        {/* 标签 */}
        <div className="mb-4 flex flex-wrap gap-2">
          {template.tags.slice(0, 3).map((tag) => (
            <Badge
              key={tag}
              variant="secondary"
              className="text-xs font-normal badge"
            >
              {tag}
            </Badge>
          ))}
        </div>

        {/* 平台图标 */}
        <div className="mb-4 flex items-center gap-2">
          <span className="text-xs text-muted-foreground">支持平台:</span>
          <div className="flex gap-1">
            {template.platforms.slice(0, 4).map((platform) => (
              <div
                key={platform}
                className="flex items-center justify-center rounded-md bg-muted p-1.5"
                title={platform}
              >
                {getPlatformIcon(platform)}
              </div>
            ))}
            {template.platforms.length > 4 && (
              <div className="flex items-center justify-center rounded-md bg-muted px-2 text-xs">
                +{template.platforms.length - 4}
              </div>
            )}
          </div>
        </div>

        {/* 统计信息 */}
        <div className="flex items-center gap-4 text-sm text-muted-foreground">
          <div className="flex items-center gap-1">
            <Star className="h-4 w-4 fill-yellow-400 text-yellow-400" />
            <span>{template.rating.toFixed(1)}</span>
          </div>
          <div className="flex items-center gap-1">
            <Users className="h-4 w-4" />
            <span>{template.usageCount.toLocaleString()}</span>
          </div>
        </div>
      </CardContent>

      {/* 底部操作按钮 */}
      <CardFooter className="p-5 pt-0">
        <Button className="w-full" variant="default" size="sm">
          使用此模板
        </Button>
      </CardFooter>
    </Card>
  );
}
