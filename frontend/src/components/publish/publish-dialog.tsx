/**
 * 多端发布弹窗组件
 *
 * 设计理念：
 * - 支持5大平台发布：Android、iOS、H5、小程序、桌面端
 * - 多选模式，批量发布提升效率
 * - 显示平台特性、预计时间、技术栈
 * - 清晰的发布流程引导
 * - 优雅的视觉设计和交互反馈
 *
 * 使用场景：
 * - 预览页面的发布功能
 * - 项目管理页的批量发布
 * - 快速发布向导
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
import { Checkbox } from '@/components/ui/checkbox';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Smartphone,
  Globe,
  Cpu,
  Clock,
  CheckCircle2,
  AlertCircle,
  Zap,
  Shield,
  Cloud,
  Code,
} from 'lucide-react';

/**
 * 发布平台类型
 */
export type PlatformType = 'android' | 'ios' | 'h5' | 'miniapp';

/**
 * 平台配置接口
 */
export interface PlatformConfig {
  /** 平台ID */
  id: PlatformType;
  /** 平台名称 */
  name: string;
  /** 平台描述 */
  description: string;
  /** 平台图标 */
  icon: React.ComponentType<{ className?: string }>;
  /** 平台颜色（用于图标和标签） */
  color: string;
  /** 预计构建时间（分钟） */
  estimatedTime: number;
  /** 平台特性标签 */
  features: string[];
  /** 技术栈 */
  techStack: string;
  /** 是否推荐 */
  recommended?: boolean;
  /** 是否需要配置 */
  requiresConfig?: boolean;
}

/**
 * 预定义平台配置
 */
export const PLATFORM_CONFIGS: Record<PlatformType, PlatformConfig> = {
  android: {
    id: 'android',
    name: 'Android 应用',
    description: '生成 Android APK，支持 Android 5.0+',
    icon: Smartphone,
    color: '#3DDC84',
    estimatedTime: 8,
    features: ['原生性能', '广泛兼容', 'Google Play'],
    techStack: 'Kotlin Multiplatform + Jetpack Compose',
    recommended: true,
    requiresConfig: true,
  },
  ios: {
    id: 'ios',
    name: 'iOS 应用',
    description: '生成 iOS IPA，支持 iOS 13.0+',
    icon: Smartphone,
    color: '#007AFF',
    estimatedTime: 10,
    features: ['流畅体验', 'App Store', '高性能'],
    techStack: 'Kotlin Multiplatform + SwiftUI',
    recommended: true,
    requiresConfig: true,
  },
  h5: {
    id: 'h5',
    name: 'H5 网页',
    description: '生成响应式网页，支持所有现代浏览器',
    icon: Globe,
    color: '#FF6B35',
    estimatedTime: 3,
    features: ['快速发布', '零安装', 'SEO友好'],
    techStack: 'React + Next.js',
    recommended: false,
    requiresConfig: false,
  },
  miniapp: {
    id: 'miniapp',
    name: '小程序',
    description: '生成微信/支付宝小程序代码包',
    icon: Code,
    color: '#07C160',
    estimatedTime: 5,
    features: ['微信生态', '轻量级', '即用即走'],
    techStack: 'Taro + React',
    recommended: false,
    requiresConfig: true,
  },
};

/**
 * 组件Props
 */
interface PublishDialogProps {
  /** 是否打开 */
  open: boolean;
  /** 关闭回调 */
  onOpenChange: (open: boolean) => void;
  /** 项目ID */
  projectId?: string;
  /** 项目名称 */
  projectName?: string;
  /** 发布回调 */
  onPublish?: (platforms: PlatformType[]) => void | Promise<void>;
}

/**
 * 平台选择卡片组件
 */
const PlatformCard: React.FC<{
  config: PlatformConfig;
  selected: boolean;
  onToggle: () => void;
}> = ({ config, selected, onToggle }) => {
  const Icon = config.icon;

  return (
    <div
      className={cn(
        'relative rounded-lg border-2 p-4 transition-all cursor-pointer hover:shadow-md',
        selected
          ? 'border-primary bg-primary/5 shadow-sm'
          : 'border-border hover:border-primary/50'
      )}
      onClick={onToggle}
    >
      {/* 推荐标签 */}
      {config.recommended && (
        <div className="absolute -top-2 -right-2">
          <Badge className="bg-gradient-to-r from-orange-500 to-pink-500 text-white">
            推荐
          </Badge>
        </div>
      )}

      <div className="flex items-start gap-4">
        {/* 平台图标 */}
        <div
          className={cn(
            'flex h-12 w-12 items-center justify-center rounded-lg',
            selected ? 'bg-primary/10' : 'bg-muted'
          )}
          style={{
            backgroundColor: selected ? `${config.color}20` : undefined,
          }}
        >
          <Icon
            className="h-6 w-6"
            {...(selected && { style: { color: config.color } })}
          />
        </div>

        {/* 平台信息 */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h4 className="font-semibold text-base">{config.name}</h4>
            <Checkbox checked={selected} onCheckedChange={() => onToggle()} />
          </div>

          <p className="text-sm text-muted-foreground mb-3">
            {config.description}
          </p>

          {/* 特性标签 */}
          <div className="flex flex-wrap gap-1.5 mb-2">
            {config.features.map((feature) => (
              <Badge key={feature} variant="secondary" className="text-xs">
                {feature}
              </Badge>
            ))}
          </div>

          {/* 底部信息 */}
          <div className="flex items-center gap-4 text-xs text-muted-foreground">
            <div className="flex items-center gap-1">
              <Clock className="h-3 w-3" />
              <span>约 {config.estimatedTime} 分钟</span>
            </div>
            <div className="flex items-center gap-1">
              <Cpu className="h-3 w-3" />
              <span className="truncate">{config.techStack}</span>
            </div>
          </div>

          {/* 配置提示 */}
          {config.requiresConfig && (
            <div className="mt-2 flex items-center gap-1 text-xs text-amber-600">
              <AlertCircle className="h-3 w-3" />
              <span>需要配置平台参数</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

/**
 * 发布统计信息组件
 */
const PublishStats: React.FC<{
  selectedPlatforms: PlatformType[];
}> = ({ selectedPlatforms }) => {
  const totalTime = selectedPlatforms.reduce(
    (acc, id) => acc + PLATFORM_CONFIGS[id].estimatedTime,
    0
  );

  const stats = [
    {
      icon: CheckCircle2,
      label: '选中平台',
      value: selectedPlatforms.length,
      color: 'text-green-600',
    },
    {
      icon: Clock,
      label: '预计时间',
      value: `${totalTime} 分钟`,
      color: 'text-blue-600',
    },
    {
      icon: Zap,
      label: '并行构建',
      value: '是',
      color: 'text-purple-600',
    },
  ];

  return (
    <div className="grid grid-cols-3 gap-4 p-4 bg-muted/50 rounded-lg">
      {stats.map((stat) => {
        const Icon = stat.icon;
        return (
          <div key={stat.label} className="flex items-center gap-2">
            <Icon className={cn('h-4 w-4', stat.color)} />
            <div>
              <div className="text-xs text-muted-foreground">{stat.label}</div>
              <div className="text-sm font-semibold">{stat.value}</div>
            </div>
          </div>
        );
      })}
    </div>
  );
};

/**
 * 多端发布弹窗组件
 */
export const PublishDialog: React.FC<PublishDialogProps> = ({
  open,
  onOpenChange,
  projectId,
  projectName = '未命名项目',
  onPublish,
}) => {
  const [selectedPlatforms, setSelectedPlatforms] = useState<PlatformType[]>([
    'android',
    'ios',
  ]);
  const [isPublishing, setIsPublishing] = useState(false);

  /**
   * 切换平台选择
   */
  const togglePlatform = (platformId: PlatformType) => {
    setSelectedPlatforms((prev) =>
      prev.includes(platformId)
        ? prev.filter((id) => id !== platformId)
        : [...prev, platformId]
    );
  };

  /**
   * 全选/取消全选
   */
  const toggleAll = () => {
    if (selectedPlatforms.length === Object.keys(PLATFORM_CONFIGS).length) {
      setSelectedPlatforms([]);
    } else {
      setSelectedPlatforms(Object.keys(PLATFORM_CONFIGS) as PlatformType[]);
    }
  };

  /**
   * 处理发布
   */
  const handlePublish = async () => {
    if (selectedPlatforms.length === 0) return;

    setIsPublishing(true);
    try {
      await onPublish?.(selectedPlatforms);
    } finally {
      setIsPublishing(false);
    }
  };

  const platformList = Object.values(PLATFORM_CONFIGS);
  const allSelected = selectedPlatforms.length === platformList.length;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-4xl max-h-[90vh] p-0">
        <DialogHeader className="px-6 pt-6 pb-4 border-b">
          <DialogTitle className="text-xl">多端发布</DialogTitle>
          <DialogDescription>
            选择要发布的平台，支持同时构建多个平台应用
          </DialogDescription>
        </DialogHeader>

        <ScrollArea className="flex-1 px-6 py-4">
          {/* 项目信息 */}
          <div className="mb-6 p-4 bg-muted/30 rounded-lg">
            <div className="flex items-center gap-2 mb-2">
              <Shield className="h-4 w-4 text-primary" />
              <span className="text-sm font-medium">项目信息</span>
            </div>
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <span className="text-muted-foreground">项目名称：</span>
                <span className="font-medium">{projectName}</span>
              </div>
              <div>
                <span className="text-muted-foreground">项目ID：</span>
                <span className="font-mono text-xs">
                  {projectId || '未指定'}
                </span>
              </div>
            </div>
          </div>

          {/* 统计信息 */}
          {selectedPlatforms.length > 0 && (
            <div className="mb-6">
              <PublishStats selectedPlatforms={selectedPlatforms} />
            </div>
          )}

          {/* 快捷操作 */}
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-medium">选择发布平台</h3>
            <Button
              variant="ghost"
              size="sm"
              onClick={toggleAll}
              className="h-8 text-xs"
            >
              {allSelected ? '取消全选' : '全选'}
            </Button>
          </div>

          {/* 平台列表 */}
          <div className="space-y-3">
            {platformList.map((config) => (
              <PlatformCard
                key={config.id}
                config={config}
                selected={selectedPlatforms.includes(config.id)}
                onToggle={() => togglePlatform(config.id)}
              />
            ))}
          </div>

          {/* 提示信息 */}
          <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-950/20 rounded-lg border border-blue-200 dark:border-blue-800">
            <div className="flex gap-2">
              <Cloud className="h-5 w-5 text-blue-600 flex-shrink-0 mt-0.5" />
              <div className="text-sm">
                <p className="font-medium text-blue-900 dark:text-blue-100 mb-1">
                  云端并行构建
                </p>
                <p className="text-blue-700 dark:text-blue-300">
                  所有平台将在云端并行构建，无需等待。构建完成后会自动推送通知，您可以继续其他工作。
                </p>
              </div>
            </div>
          </div>
        </ScrollArea>

        <DialogFooter className="px-6 py-4 border-t bg-muted/30">
          <div className="flex items-center justify-between w-full">
            <div className="text-sm text-muted-foreground">
              {selectedPlatforms.length > 0 ? (
                <span>
                  已选择 <strong>{selectedPlatforms.length}</strong> 个平台
                </span>
              ) : (
                <span>请至少选择一个平台</span>
              )}
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={() => onOpenChange(false)}
                disabled={isPublishing}
              >
                取消
              </Button>
              <Button
                onClick={handlePublish}
                disabled={selectedPlatforms.length === 0 || isPublishing}
              >
                {isPublishing ? (
                  <>
                    <span className="animate-spin mr-2">⏳</span>
                    发布中...
                  </>
                ) : (
                  <>
                    <Zap className="h-4 w-4 mr-2" />
                    开始发布
                  </>
                )}
              </Button>
            </div>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
