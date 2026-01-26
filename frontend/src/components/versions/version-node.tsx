/**
 * 版本节点组件
 * 显示单个版本的卡片，支持点击选中
 */
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { CheckCircle2, XCircle, GitCommit } from 'lucide-react';
import { VersionTimelineItem, VersionType, VERSION_TYPE_INFO } from '@/types/version';

/**
 * 版本节点组件Props
 */
interface VersionNodeProps {
  /** 版本信息 */
  version: VersionTimelineItem;
  /** 是否选中 */
  isSelected: boolean;
  /** 是否为当前版本 */
  isCurrent?: boolean;
  /** 点击回调 */
  onClick?: () => void;
  /** 类名 */
  className?: string;
}

/**
 * 获取版本类型图标
 */
const getVersionIcon = (type: VersionType) => {
  const iconClass = "h-5 w-5";

  switch (type) {
    case 'DESIGN_CONFIRM':
    case 'CODE_GENERATION':
      return <CheckCircle2 className={cn(iconClass, "text-green-600")} />;
    case 'ROLLBACK':
      return <XCircle className={cn(iconClass, "text-red-600")} />;
    default:
      return <GitCommit className={cn(iconClass, VERSION_TYPE_INFO[type].color)} />;
  }
};

/**
 * 格式化时间
 */
const formatTime = (isoString: string): string => {
  const date = new Date(isoString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return '刚刚';
  if (diffMins < 60) return `${diffMins}分钟前`;
  if (diffHours < 24) return `${diffHours}小时前`;
  if (diffDays < 7) return `${diffDays}天前`;

  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
};

/**
 * 版本节点组件
 */
export const VersionNode: React.FC<VersionNodeProps> = ({
  version,
  isSelected,
  isCurrent = false,
  onClick,
  className,
}) => {
  const typeInfo = VERSION_TYPE_INFO[version.versionType];

  return (
    <Card
      className={cn(
        "cursor-pointer transition-all hover:shadow-md",
        isSelected && "ring-2 ring-primary",
        isCurrent && "border-primary border-2",
        className
      )}
      onClick={onClick}
    >
      <CardContent className="p-4">
        <div className="flex items-start gap-3">
          {/* 版本类型图标 */}
          <div className={cn(
            "flex items-center justify-center w-10 h-10 rounded-full",
            typeInfo.bgColor
          )}>
            {getVersionIcon(version.versionType)}
          </div>

          {/* 版本信息 */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <h4 className="font-semibold text-sm truncate">
                {typeInfo.displayName}
              </h4>
              <Badge variant="outline" className="text-xs">
                {version.versionNumber}
              </Badge>
              {isCurrent && (
                <Badge variant="default" className="text-xs bg-primary">
                  当前版本
                </Badge>
              )}
            </div>

            <p className="text-xs text-muted-foreground mb-2 line-clamp-2">
              {version.summary || typeInfo.description}
            </p>

            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>{formatTime(version.createdAt)}</span>
              {version.createdBy && (
                <span className="truncate ml-2">by {version.createdBy}</span>
              )}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};
