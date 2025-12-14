"use client";

import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import Link from "next/link";

/**
 * 模板卡片组件
 * 用于首页快捷入口，展示预设应用模板
 *
 * 支持两种模式：
 * 1. 导航模式（默认）：点击跳转到/create?template=:id
 * 2. 回调模式：点击触发onClick回调（用于表单填充）
 */

export interface TemplateCardProps {
  /** 模板ID */
  id: string;
  /** 模板标题 */
  title: string;
  /** 模板描述 */
  description: string;
  /** 图标组件 */
  icon: React.ReactNode;
  /** Tailwind渐变色类 */
  color: string;
  /** 点击回调（可选，用于表单填充） */
  onClick?: (template: { id: string; title: string; description: string }) => void;
}

/**
 * 模板卡片内容组件
 */
function TemplateCardContent({
  title,
  description,
  icon,
  color,
}: Pick<TemplateCardProps, "title" | "description" | "icon" | "color">): React.ReactElement {
  return (
    <Card
      className={cn(
        "group w-[240px] shrink-0 cursor-pointer transition-all hover:shadow-lg hover:-translate-y-1",
        "border-border/50 bg-card/50 backdrop-blur-sm"
      )}
    >
      <CardContent className="flex items-center gap-4 p-5">
        {/* 图标容器 */}
        <div
          className={cn(
            "flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-white shadow-md",
            "transition-transform group-hover:scale-110",
            color
          )}
        >
          {icon}
        </div>

        {/* 文字内容 */}
        <div className="flex-1 overflow-hidden">
          <h3 className="truncate text-sm font-semibold">{title}</h3>
          <p className="truncate text-xs text-muted-foreground">
            {description}
          </p>
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * TemplateCard组件
 * 根据是否提供onClick回调选择渲染模式
 */
export function TemplateCard(props: TemplateCardProps): React.ReactElement {
  const { id, title, description, icon, color, onClick } = props;

  // 如果提供了onClick，使用按钮模式
  if (onClick) {
    return (
      <div onClick={() => onClick({ id, title, description })}>
        <TemplateCardContent
          title={title}
          description={description}
          icon={icon}
          color={color}
        />
      </div>
    );
  }

  // 否则使用导航模式 - V2.0入口
  return (
    <Link href={`/?template=${id}`}>
      <TemplateCardContent
        title={title}
        description={description}
        icon={icon}
        color={color}
      />
    </Link>
  );
}
