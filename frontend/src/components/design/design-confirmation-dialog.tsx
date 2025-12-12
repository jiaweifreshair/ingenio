/**
 * 设计确认对话框组件
 *
 * 功能说明：
 * - V2.0核心组件：用户确认设计后才能进入Execute阶段
 * - 展示原型预览URL和关键设计信息
 * - 调用后端API完成设计确认
 * - 提供清晰的用户引导和交互反馈
 *
 * 使用场景：
 * - Plan阶段完成后，用户确认设计方案
 * - 风格选择后的最终确认
 * - 预览原型后的批准流程
 */
'use client';

import React, { useState } from 'react';
import { cn } from '@/lib/utils';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import {
  CheckCircle2,
  AlertCircle,
  ExternalLink,
  Palette,
  Code,
  Eye,
  Loader2,
  Info,
  Sparkles,
} from 'lucide-react';

/**
 * 设计确认信息接口
 */
export interface DesignConfirmationInfo {
  /** AppSpec ID */
  appSpecId: string;
  /** 原型预览URL */
  prototypeUrl: string;
  /** 选择的风格ID */
  selectedStyleId: string;
  /** 风格显示名称 */
  styleName: string;
  /** 用户需求描述 */
  userRequirement: string;
  /** 意图类型 */
  intentType: 'CLONE_EXISTING_WEBSITE' | 'DESIGN_FROM_SCRATCH' | 'HYBRID_CLONE_AND_CUSTOMIZE';
}

/**
 * 对话框属性接口
 */
export interface DesignConfirmationDialogProps {
  /** 是否显示对话框 */
  open: boolean;
  /** 关闭对话框回调 */
  onOpenChange: (open: boolean) => void;
  /** 设计确认信息 */
  designInfo: DesignConfirmationInfo;
  /** 确认成功回调 */
  onConfirmSuccess?: () => void;
  /** 确认失败回调 */
  onConfirmError?: (error: string) => void;
}

/**
 * 意图类型显示名称映射
 */
const INTENT_DISPLAY_NAMES: Record<string, string> = {
  CLONE_EXISTING_WEBSITE: '克隆已有网站',
  DESIGN_FROM_SCRATCH: '从零设计',
  HYBRID_CLONE_AND_CUSTOMIZE: '混合模式（克隆+定制）',
};

/**
 * 设计确认对话框组件
 *
 * 核心功能：
 * 1. 展示原型预览信息
 * 2. 显示选择的设计风格
 * 3. 提供确认/取消操作
 * 4. 调用后端API完成确认
 *
 * @param props 组件属性
 */
export function DesignConfirmationDialog({
  open,
  onOpenChange,
  designInfo,
  onConfirmSuccess,
  onConfirmError,
}: DesignConfirmationDialogProps) {
  const [isConfirming, setIsConfirming] = useState(false);
  const [confirmError, setConfirmError] = useState<string | null>(null);

  /**
   * 处理确认设计操作
   * 调用后端API完成设计确认
   */
  const handleConfirmDesign = async () => {
    setIsConfirming(true);
    setConfirmError(null);

    try {
      const response = await fetch(
        `/api/v2/plan-routing/${designInfo.appSpecId}/confirm-design`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
        }
      );

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || '确认设计失败');
      }

      // 确认成功
      onConfirmSuccess?.();
      onOpenChange(false);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '未知错误';
      setConfirmError(errorMessage);
      onConfirmError?.(errorMessage);
    } finally {
      setIsConfirming(false);
    }
  };

  /**
   * 打开原型预览页面
   */
  const handleOpenPreview = () => {
    window.open(designInfo.prototypeUrl, '_blank', 'noopener,noreferrer');
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-primary" />
            确认设计方案
          </DialogTitle>
          <DialogDescription>
            请仔细检查您的设计方案，确认后将开始生成完整的后端代码和数据库结构。
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {/* 需求摘要 */}
          <div className="rounded-lg border p-4 bg-muted/50">
            <div className="flex items-center gap-2 mb-2">
              <Info className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium">您的需求</span>
            </div>
            <p className="text-sm text-muted-foreground line-clamp-3">
              {designInfo.userRequirement}
            </p>
          </div>

          {/* 设计信息 */}
          <div className="grid grid-cols-2 gap-4">
            {/* 意图类型 */}
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Code className="h-4 w-4 text-blue-500" />
                <span className="text-sm font-medium">生成模式</span>
              </div>
              <Badge variant="secondary">
                {INTENT_DISPLAY_NAMES[designInfo.intentType] || designInfo.intentType}
              </Badge>
            </div>

            {/* 设计风格 */}
            <div className="space-y-1">
              <div className="flex items-center gap-2">
                <Palette className="h-4 w-4 text-purple-500" />
                <span className="text-sm font-medium">设计风格</span>
              </div>
              <Badge variant="outline" className="bg-purple-50">
                {designInfo.styleName}
              </Badge>
            </div>
          </div>

          {/* 原型预览 */}
          <div className="rounded-lg border p-4">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <Eye className="h-4 w-4 text-green-500" />
                <span className="text-sm font-medium">原型预览</span>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={handleOpenPreview}
                className="gap-1"
              >
                <ExternalLink className="h-3 w-3" />
                在新窗口打开
              </Button>
            </div>
            <div className="rounded border bg-white h-40 flex items-center justify-center overflow-hidden">
              <iframe
                src={designInfo.prototypeUrl}
                className="w-full h-full border-0"
                title="原型预览"
                sandbox="allow-scripts allow-same-origin"
              />
            </div>
            <p className="text-xs text-muted-foreground mt-2">
              预览URL: {designInfo.prototypeUrl}
            </p>
          </div>

          {/* 确认提示 */}
          <Alert>
            <CheckCircle2 className="h-4 w-4" />
            <AlertDescription>
              确认后，系统将自动：
              <ul className="list-disc list-inside mt-1 text-sm">
                <li>生成完整的数据库Schema</li>
                <li>创建Spring Boot后端代码</li>
                <li>集成AI能力（如适用）</li>
                <li>生成多端适配代码</li>
              </ul>
            </AlertDescription>
          </Alert>

          {/* 错误提示 */}
          {confirmError && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertDescription>{confirmError}</AlertDescription>
            </Alert>
          )}
        </div>

        <DialogFooter className="gap-2">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isConfirming}
          >
            取消
          </Button>
          <Button
            onClick={handleConfirmDesign}
            disabled={isConfirming}
            className={cn(
              'gap-2',
              isConfirming && 'cursor-not-allowed opacity-70'
            )}
          >
            {isConfirming ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                确认中...
              </>
            ) : (
              <>
                <CheckCircle2 className="h-4 w-4" />
                确认设计并生成代码
              </>
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export default DesignConfirmationDialog;
