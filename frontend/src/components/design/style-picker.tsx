"use client";

import { useState, useEffect } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useToast } from "@/hooks/use-toast";
import { generate7StylePreviews, getStyleDisplayInfo } from "@/lib/api/superdesign";
import type {
  Generate7StylesRequest,
  StylePreviewResponse,
  DesignStyle,
} from "@/types/design-style";
import { Check, Loader2, AlertCircle, Eye, Sparkles } from "lucide-react";

/**
 * StylePicker组件Props
 */
export interface StylePickerProps {
  /** 用户需求描述 */
  userRequirement: string;

  /** 应用类型（可选） */
  appType?: string;

  /** 目标平台（可选） */
  targetPlatform?: string;

  /** 是否使用AI定制（默认false） */
  useAICustomization?: boolean;

  /** 选择风格回调 */
  onStyleSelected: (style: DesignStyle, htmlContent: string) => void;

  /** 取消回调 */
  onCancel?: () => void;
}

/**
 * StylePicker - 7种风格选择器
 *
 * 功能：
 * 1. 自动调用后端API生成7种风格预览
 * 2. 网格布局展示7个风格卡片
 * 3. 每个卡片显示风格信息和HTML预览
 * 4. 用户选择风格后触发回调
 * 5. 支持加载状态、错误处理、重试机制
 *
 * 使用示例：
 * ```tsx
 * <StylePicker
 *   userRequirement="创建一个民宿预订平台"
 *   appType="生活服务"
 *   targetPlatform="web"
 *   onStyleSelected={(style, html) => {
 *     console.log('用户选择风格:', style);
 *     // 继续下一步流程
 *   }}
 * />
 * ```
 */
export function StylePicker({
  userRequirement,
  appType,
  targetPlatform,
  useAICustomization = false,
  onStyleSelected,
  onCancel,
}: StylePickerProps): React.ReactElement {
  const { toast } = useToast();

  // 状态管理
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [styles, setStyles] = useState<StylePreviewResponse[]>([]);
  const [selectedStyle, setSelectedStyle] = useState<DesignStyle | null>(null);
  const [totalTime, setTotalTime] = useState<number>(0);

  /**
   * 组件挂载时自动生成7种风格预览
   */
  useEffect(() => {
    async function loadStyles() {
      console.log('[StylePicker] 开始生成7种风格预览');
      setLoading(true);
      setError(null);

      const request: Generate7StylesRequest = {
        userRequirement,
        appType,
        targetPlatform,
        useAICustomization,
      };

      try {
        const response = await generate7StylePreviews(request);

        if (response.success && response.data) {
          console.log('[StylePicker] ✅ 生成成功');
          setStyles(response.data.styles);
          setTotalTime(response.data.totalGenerationTime);

          // 显示成功提示
          toast({
            title: "风格生成成功 ✨",
            description: `已生成${response.data.styles.length}种风格预览 (${response.data.totalGenerationTime}ms)`,
          });

          // 警告提示
          if (response.data.warnings && response.data.warnings.length > 0) {
            response.data.warnings.forEach((warning) => {
              toast({
                title: "警告",
                description: warning,
                variant: "default",
              });
            });
          }
        } else {
          console.error('[StylePicker] ❌ 生成失败:', response.error);
          setError(response.error || "生成失败");

          toast({
            title: "生成失败",
            description: response.error || "无法生成风格预览，请稍后重试",
            variant: "destructive",
          });
        }
      } catch (err) {
        console.error('[StylePicker] ❌ 生成异常:', err);
        const errorMessage = err instanceof Error ? err.message : "未知错误";
        setError(errorMessage);

        toast({
          title: "系统错误",
          description: errorMessage,
          variant: "destructive",
        });
      } finally {
        setLoading(false);
      }
    }

    loadStyles();
  }, [userRequirement, appType, targetPlatform, useAICustomization, toast]);

  /**
   * 处理风格选择
   */
  function handleSelectStyle(stylePreview: StylePreviewResponse) {
    console.log('[StylePicker] 用户选择风格:', stylePreview.style);
    setSelectedStyle(stylePreview.style as DesignStyle);

    // 触发回调
    onStyleSelected(stylePreview.style as DesignStyle, stylePreview.htmlContent);
  }

  /**
   * 重试加载
   */
  function handleRetry() {
    window.location.reload();
  }

  // 加载中状态
  if (loading) {
    return (
      <div className="flex min-h-[400px] flex-col items-center justify-center space-y-4">
        <Loader2 className="h-12 w-12 animate-spin text-primary" />
        <div className="text-center">
          <p className="text-lg font-semibold">AI正在生成7种风格预览...</p>
          <p className="text-sm text-muted-foreground mt-2">
            这可能需要15-20秒，请稍候
          </p>
        </div>
      </div>
    );
  }

  // 错误状态
  if (error || styles.length === 0) {
    return (
      <div className="flex min-h-[400px] flex-col items-center justify-center space-y-4">
        <AlertCircle className="h-12 w-12 text-destructive" />
        <div className="text-center">
          <p className="text-lg font-semibold">生成失败</p>
          <p className="text-sm text-muted-foreground mt-2">
            {error || "无法生成风格预览"}
          </p>
        </div>
        <div className="flex gap-4">
          <Button onClick={handleRetry} variant="default">
            重试
          </Button>
          {onCancel && (
            <Button onClick={onCancel} variant="outline">
              返回
            </Button>
          )}
        </div>
      </div>
    );
  }

  // 成功状态：显示7种风格卡片
  return (
    <div className="space-y-6">
      {/* 标题和说明 */}
      <div className="text-center space-y-2">
        <h2 className="text-2xl font-bold flex items-center justify-center gap-2">
          <Sparkles className="h-6 w-6 text-primary" />
          选择你喜欢的设计风格
        </h2>
        <p className="text-sm text-muted-foreground">
          为您生成了7种不同风格的设计预览，点击查看详情并选择
        </p>
        <p className="text-xs text-muted-foreground">
          生成耗时: {totalTime}ms
        </p>
      </div>

      {/* 风格卡片网格 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {styles.map((stylePreview) => {
          const styleInfo = getStyleDisplayInfo(stylePreview.style);
          const isSelected = selectedStyle === stylePreview.style;

          return (
            <StylePreviewCard
              key={stylePreview.style}
              stylePreview={stylePreview}
              styleInfo={styleInfo}
              isSelected={isSelected}
              onSelect={() => handleSelectStyle(stylePreview)}
            />
          );
        })}
      </div>

      {/* 底部操作按钮 */}
      <div className="flex justify-center gap-4 pt-4">
        {onCancel && (
          <Button onClick={onCancel} variant="outline" size="lg">
            返回修改需求
          </Button>
        )}
      </div>
    </div>
  );
}

/**
 * StylePreviewCard - 单个风格预览卡片
 */
interface StylePreviewCardProps {
  stylePreview: StylePreviewResponse;
  styleInfo: ReturnType<typeof getStyleDisplayInfo>;
  isSelected: boolean;
  onSelect: () => void;
}

function StylePreviewCard({
  stylePreview,
  styleInfo,
  isSelected,
  onSelect,
}: StylePreviewCardProps): React.ReactElement {
  const [showPreview, setShowPreview] = useState(false);

  return (
    <Card
      className={cn(
        "group relative cursor-pointer transition-all hover:shadow-xl",
        "border-2",
        isSelected
          ? "border-primary ring-4 ring-primary/20 shadow-xl"
          : "border-border/50 hover:border-primary/50"
      )}
      onClick={onSelect}
    >
      <CardContent className="p-6 space-y-4">
        {/* 选中标识 */}
        {isSelected && (
          <div className="absolute top-3 right-3 h-8 w-8 rounded-full bg-primary flex items-center justify-center">
            <Check className="h-5 w-5 text-white" />
          </div>
        )}

        {/* 风格标识和图标 */}
        <div className="flex items-center gap-3">
          <div
            className={cn(
              "flex h-12 w-12 shrink-0 items-center justify-center rounded-lg text-2xl",
              "bg-gradient-to-br shadow-md",
              styleInfo.colorClass
            )}
          >
            {styleInfo.icon}
          </div>
          <div className="flex-1 overflow-hidden">
            <h3 className="font-semibold text-base truncate">
              {styleInfo.identifier}. {styleInfo.displayName}
            </h3>
            <p className="text-xs text-muted-foreground truncate">
              {styleInfo.displayNameEn}
            </p>
          </div>
        </div>

        {/* 风格描述 */}
        <p className="text-sm text-muted-foreground line-clamp-2">
          {styleInfo.description}
        </p>

        {/* 核心特征标签 */}
        <div className="flex flex-wrap gap-2">
          {styleInfo.features.slice(0, 3).map((feature) => (
            <span
              key={feature}
              className="inline-flex items-center rounded-md bg-muted px-2 py-1 text-xs font-medium"
            >
              {feature}
            </span>
          ))}
        </div>

        {/* 适用场景 */}
        <div className="space-y-1">
          <p className="text-xs font-medium text-muted-foreground">适用场景：</p>
          <p className="text-xs text-muted-foreground line-clamp-2">
            {styleInfo.suitableFor.join("、")}
          </p>
        </div>

        {/* HTML预览缩略图 */}
        <div className="relative aspect-video rounded-md border bg-muted overflow-hidden">
          {showPreview ? (
            <iframe
              srcDoc={stylePreview.htmlContent}
              className="w-full h-full scale-50 origin-top-left"
              style={{ width: "200%", height: "200%" }}
              title={`${styleInfo.displayName}预览`}
              sandbox="allow-same-origin"
            />
          ) : (
            <div className="flex items-center justify-center h-full">
              <Button
                variant="outline"
                size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowPreview(true);
                }}
                className="gap-2"
              >
                <Eye className="h-4 w-4" />
                预览设计
              </Button>
            </div>
          )}
        </div>

        {/* 生成信息 */}
        <div className="flex items-center justify-between text-xs text-muted-foreground">
          <span>
            {stylePreview.aiGenerated ? "🤖 AI生成" : "📐 模板生成"}
          </span>
          <span>{stylePreview.generationTime}ms</span>
        </div>

        {/* 选择按钮 */}
        <Button
          variant={isSelected ? "default" : "outline"}
          size="sm"
          className="w-full"
          onClick={(e) => {
            e.stopPropagation();
            onSelect();
          }}
        >
          {isSelected ? (
            <>
              <Check className="mr-2 h-4 w-4" />
              已选择
            </>
          ) : (
            "选择此风格"
          )}
        </Button>
      </CardContent>
    </Card>
  );
}
