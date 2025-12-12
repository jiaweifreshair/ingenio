/**
 * AI模型选择器组件
 * 支持选择Uniaix提供的多种AI模型
 */
"use client";

import * as React from "react";
import { Star } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import {
  MODEL_CONFIGS,
  type UniaixModel,
  type ModelConfig,
} from "@/lib/api/uniaix";
import { cn } from "@/lib/utils";

export interface ModelSelectorProps {
  /** 当前选中的模型 */
  value?: UniaixModel;
  /** 模型变化回调 */
  onValueChange?: (model: UniaixModel) => void;
  /** 是否禁用 */
  disabled?: boolean;
  /** 自定义类名 */
  className?: string;
  /** 是否显示标签 */
  showLabel?: boolean;
  /** 是否显示描述 */
  showDescription?: boolean;
}

/**
 * 按提供商分组模型
 */
function groupModelsByProvider(models: ModelConfig[]): Map<string, ModelConfig[]> {
  const groups = new Map<string, ModelConfig[]>();

  models.forEach(model => {
    const provider = model.provider;
    if (!groups.has(provider)) {
      groups.set(provider, []);
    }
    groups.get(provider)!.push(model);
  });

  return groups;
}

/**
 * 获取模型显示名称（带推荐标记）
 */
function getModelDisplayName(model: ModelConfig): React.ReactNode {
  return (
    <div className="flex items-center justify-between w-full">
      <span className="flex-1">{model.name}</span>
      {model.recommended && (
        <Star className="h-3 w-3 fill-yellow-400 text-yellow-400 ml-2 flex-shrink-0" />
      )}
    </div>
  );
}

/**
 * AI模型选择器
 */
export function ModelSelector({
  value,
  onValueChange,
  disabled = false,
  className,
  showLabel = true,
  showDescription = true,
}: ModelSelectorProps) {
  const groupedModels = React.useMemo(() => {
    return groupModelsByProvider(MODEL_CONFIGS);
  }, []);

  const selectedModel = React.useMemo(() => {
    return MODEL_CONFIGS.find(m => m.id === value);
  }, [value]);

  return (
    <div className={cn("space-y-2", className)}>
      {showLabel && (
        <Label htmlFor="model-select">
          AI模型
          <span className="text-xs text-muted-foreground ml-2">
            (选择用于生成的AI模型)
          </span>
        </Label>
      )}

      <Select value={value} onValueChange={onValueChange} disabled={disabled}>
        <SelectTrigger id="model-select" className="w-full">
          <SelectValue placeholder="选择AI模型">
            {selectedModel ? (
              <div className="flex items-center gap-2">
                <span>{selectedModel.name}</span>
                {selectedModel.recommended && (
                  <Star className="h-3 w-3 fill-yellow-400 text-yellow-400" />
                )}
              </div>
            ) : (
              '选择AI模型'
            )}
          </SelectValue>
        </SelectTrigger>

        <SelectContent>
          {Array.from(groupedModels.entries()).map(([provider, models]) => (
            <SelectGroup key={provider}>
              <SelectLabel>{provider}</SelectLabel>
              {models.map(model => (
                <SelectItem key={model.id} value={model.id}>
                  {getModelDisplayName(model)}
                </SelectItem>
              ))}
            </SelectGroup>
          ))}
        </SelectContent>
      </Select>

      {showDescription && selectedModel && (
        <div className="text-xs text-muted-foreground space-y-1 bg-muted/30 p-3 rounded-md">
          <p className="font-medium">{selectedModel.description}</p>
          <div className="flex items-center gap-4 mt-1">
            <span>上下文窗口: {selectedModel.contextWindow.toLocaleString()} tokens</span>
            <span>提供商: {selectedModel.provider}</span>
            {selectedModel.recommended && (
              <span className="flex items-center gap-1 text-yellow-600">
                <Star className="h-3 w-3 fill-yellow-400" />
                推荐
              </span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * 紧凑版模型选择器（不显示描述）
 */
export function CompactModelSelector(props: Omit<ModelSelectorProps, 'showDescription'>) {
  return <ModelSelector {...props} showDescription={false} />;
}

/**
 * 内联模型选择器（不显示标签和描述）
 */
export function InlineModelSelector(props: Omit<ModelSelectorProps, 'showLabel' | 'showDescription'>) {
  return <ModelSelector {...props} showLabel={false} showDescription={false} />;
}
