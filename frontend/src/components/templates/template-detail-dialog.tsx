"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Template,
  TemplateDifficulty,
  TargetPlatform,
} from "@/types/template";
import {
  Star,
  Users,
  Smartphone,
  Globe,
  Calendar,
  ExternalLink,
  Heart,
  CheckCircle,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useRouter } from "next/navigation";

/**
 * 模板详情弹窗组件属性
 */
export interface TemplateDetailDialogProps {
  /** 是否打开 */
  open: boolean;
  /** 关闭回调 */
  onClose: () => void;
  /** 模板数据 */
  template: Template | null;
  /** 使用模板回调 */
  onUseTemplate?: (template: Template) => void;
  /** 收藏模板回调 */
  onFavoriteTemplate?: (template: Template) => void;
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
 * 获取平台显示名称
 */
function getPlatformLabel(platform: TargetPlatform): string {
  const labels: Record<TargetPlatform, string> = {
    [TargetPlatform.ANDROID]: "Android",
    [TargetPlatform.IOS]: "iOS",
    [TargetPlatform.WEB]: "Web",
    [TargetPlatform.WECHAT]: "微信小程序",
    [TargetPlatform.HARMONY]: "鸿蒙",
  };
  return labels[platform];
}

/**
 * 模板详情弹窗组件
 * 显示模板的完整信息，包括描述、功能、技术栈、截图等
 */
export function TemplateDetailDialog({
  open,
  onClose,
  template,
  onUseTemplate,
  onFavoriteTemplate,
}: TemplateDetailDialogProps): React.ReactElement {
  const router = useRouter();

  if (!template) {
    return <></>;
  }

  const difficultyConfig = getDifficultyConfig(template.difficulty);

  const handleUseTemplate = () => {
    onUseTemplate?.(template);
    // V2.0: 使用意图识别+双重选择机制的创建流程
    router.push(`/?template=${template.id}`);
    onClose();
  };

  const handlePreview = () => {
    if (template.demoUrl) {
      window.open(template.demoUrl, "_blank");
    }
  };

  const handleFavorite = () => {
    onFavoriteTemplate?.(template);
  };

  return (
    <Dialog open={open} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-4xl max-h-[90vh]">
        <DialogHeader>
          <DialogTitle className="text-2xl">{template.name}</DialogTitle>
          <DialogDescription>{template.description}</DialogDescription>
        </DialogHeader>

        <ScrollArea className="max-h-[60vh] pr-4">
          <div className="space-y-6">
            {/* 基本信息 */}
            <div className="flex flex-wrap items-center gap-4">
              {/* 难度 */}
              <Badge className={cn("font-medium", difficultyConfig.color)}>
                {difficultyConfig.label}
              </Badge>

              {/* 统计 */}
              <div className="flex items-center gap-1 text-sm">
                <Star className="h-4 w-4 fill-yellow-400 text-yellow-400" />
                <span>{template.rating.toFixed(1)}</span>
              </div>
              <div className="flex items-center gap-1 text-sm">
                <Users className="h-4 w-4" />
                <span>{template.usageCount.toLocaleString()} 次使用</span>
              </div>
              <div className="flex items-center gap-1 text-sm">
                <Calendar className="h-4 w-4" />
                <span>{template.createdAt}</span>
              </div>
            </div>

            <Separator />

            {/* 详细描述 */}
            {template.detailedDescription && (
              <div>
                <h3 className="mb-2 font-semibold">详细介绍</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  {template.detailedDescription}
                </p>
              </div>
            )}

            {/* 支持平台 */}
            <div>
              <h3 className="mb-3 font-semibold">支持平台</h3>
              <div className="flex flex-wrap gap-2">
                {template.platforms.map((platform) => (
                  <Badge key={platform} variant="secondary" className="gap-2">
                    {[
                      TargetPlatform.ANDROID,
                      TargetPlatform.IOS,
                      TargetPlatform.HARMONY,
                    ].includes(platform) ? (
                      <Smartphone className="h-3 w-3" />
                    ) : (
                      <Globe className="h-3 w-3" />
                    )}
                    {getPlatformLabel(platform)}
                  </Badge>
                ))}
              </div>
            </div>

            {/* 核心功能 */}
            <div>
              <h3 className="mb-3 font-semibold">核心功能</h3>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {template.features.map((feature) => (
                  <div key={feature} className="flex items-start gap-2">
                    <CheckCircle className="h-4 w-4 mt-0.5 text-green-600 shrink-0" />
                    <span className="text-sm">{feature}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* 技术栈 */}
            <div>
              <h3 className="mb-3 font-semibold">技术栈</h3>
              <div className="flex flex-wrap gap-2">
                {template.techStack.map((tech) => (
                  <Badge key={tech} variant="outline">
                    {tech}
                  </Badge>
                ))}
              </div>
            </div>

            {/* 标签 */}
            <div>
              <h3 className="mb-3 font-semibold">标签</h3>
              <div className="flex flex-wrap gap-2">
                {template.tags.map((tag) => (
                  <Badge key={tag} variant="secondary">
                    {tag}
                  </Badge>
                ))}
              </div>
            </div>

            {/* 预览截图 */}
            {template.screenshots.length > 0 && (
              <div>
                <h3 className="mb-3 font-semibold">预览截图</h3>
                <div className="grid grid-cols-2 gap-4">
                  {template.screenshots.map((screenshot, index) => (
                    <img
                      key={index}
                      src={screenshot}
                      alt={`${template.name} 截图 ${index + 1}`}
                      className="rounded-lg border"
                    />
                  ))}
                </div>
              </div>
            )}
          </div>
        </ScrollArea>

        <DialogFooter className="gap-2 sm:gap-0">
          <Button
            variant="outline"
            onClick={handleFavorite}
            className="gap-2"
          >
            <Heart className="h-4 w-4" />
            收藏
          </Button>
          {template.demoUrl && (
            <Button
              variant="outline"
              onClick={handlePreview}
              className="gap-2"
            >
              <ExternalLink className="h-4 w-4" />
              预览
            </Button>
          )}
          <Button onClick={handleUseTemplate} className="gap-2">
            使用此模板
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
