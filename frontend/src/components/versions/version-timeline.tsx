/**
 * 版本时间线组件
 * 垂直时间轴展示所有版本，类似Git历史
 */
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { VersionTimelineItem, VERSION_TYPE_INFO } from '@/types/version';
import { VersionNode } from './version-node';

/**
 * 版本时间线组件Props
 */
interface VersionTimelineProps {
  /** 版本列表（按时间倒序） */
  versions: VersionTimelineItem[];
  /** 选中的版本ID */
  selectedVersionId?: string;
  /** 当前版本ID */
  currentVersionId?: string;
  /** 版本点击回调 */
  onVersionClick?: (version: VersionTimelineItem) => void;
  /** 类名 */
  className?: string;
}

/**
 * 版本时间线组件
 */
export const VersionTimeline: React.FC<VersionTimelineProps> = ({
  versions,
  selectedVersionId,
  currentVersionId,
  onVersionClick,
  className,
}) => {
  if (versions.length === 0) {
    return (
      <div className={cn("flex items-center justify-center h-full", className)}>
        <div className="text-center text-muted-foreground">
          <p className="text-sm">暂无版本历史</p>
        </div>
      </div>
    );
  }

  return (
    <div className={cn("relative py-4", className)}>
      {versions.map((version, index) => {
        const isLast = index === versions.length - 1;
        const typeInfo = VERSION_TYPE_INFO[version.versionType];

        return (
          <div key={version.versionId} className="relative flex gap-4 mb-4">
            {/* 时间线连接线 */}
            {!isLast && (
              <div
                className={cn(
                  "absolute left-5 top-16 w-0.5 h-full -ml-px bg-border"
                )}
              />
            )}

            {/* 时间线节点（圆点） */}
            <div className="relative z-10 flex items-start pt-5">
              <div
                className={cn(
                  "w-3 h-3 rounded-full border-4 border-background",
                  typeInfo.bgColor,
                  version.versionId === selectedVersionId && "ring-2 ring-primary"
                )}
              />
            </div>

            {/* 版本卡片 */}
            <div className="flex-1">
              <VersionNode
                version={version}
                isSelected={version.versionId === selectedVersionId}
                isCurrent={version.versionId === currentVersionId}
                onClick={() => onVersionClick?.(version)}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
};
