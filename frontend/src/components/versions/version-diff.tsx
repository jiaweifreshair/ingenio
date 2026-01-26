/**
 * 版本对比组件
 * 左右并排展示两个版本的差异，高亮变化部分
 */
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  GitCompare,
  Plus,
  Minus,
  Edit,
  TrendingUp,
  ArrowRight
} from 'lucide-react';
import { VersionDiff, VersionChange, VERSION_TYPE_INFO } from '@/types/version';

/**
 * 版本对比组件Props
 */
interface VersionDiffProps {
  /** 版本差异数据 */
  diff: VersionDiff;
  /** 类名 */
  className?: string;
}

/**
 * 变更类型图标
 */
const getChangeIcon = (type: VersionChange['type']) => {
  const iconClass = "w-4 h-4";

  switch (type) {
    case 'addition':
      return <Plus className={cn(iconClass, "text-green-600")} />;
    case 'deletion':
      return <Minus className={cn(iconClass, "text-red-600")} />;
    case 'modification':
      return <Edit className={cn(iconClass, "text-blue-600")} />;
  }
};

/**
 * 变更类型标签
 */
const getChangeLabel = (type: VersionChange['type']) => {
  switch (type) {
    case 'addition':
      return { text: '新增', className: 'bg-green-100 text-green-800 border-green-300' };
    case 'deletion':
      return { text: '删除', className: 'bg-red-100 text-red-800 border-red-300' };
    case 'modification':
      return { text: '修改', className: 'bg-blue-100 text-blue-800 border-blue-300' };
    default:
      return { text: '变更', className: 'bg-gray-100 text-gray-800 border-gray-300' };
  }
};

/**
 * 格式化JSON值
 */
const formatValue = (value: unknown): string => {
  if (value === null) return 'null';
  if (value === undefined) return 'undefined';
  if (typeof value === 'object') {
    return JSON.stringify(value, null, 2);
  }
  return String(value);
};

/**
 * 变更项组件
 */
const ChangeItem: React.FC<{ change: VersionChange }> = ({ change }) => {
  const label = getChangeLabel(change.type);

  return (
    <div className="border rounded-lg p-4 space-y-2">
      <div className="flex items-start gap-2">
        {getChangeIcon(change.type)}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <Badge variant="outline" className={cn("text-xs", label.className)}>
              {label.text}
            </Badge>
            <span className="text-sm font-medium text-muted-foreground truncate">
              {change.path}
            </span>
          </div>
          <p className="text-sm text-muted-foreground mb-2">
            {change.description}
          </p>

          {/* 值对比 */}
          {change.type === 'modification' ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-2 mt-2">
              <div className="bg-red-50 border border-red-200 rounded p-2">
                <div className="text-xs text-red-600 font-medium mb-1">旧值:</div>
                <pre className="text-xs overflow-x-auto">
                  {formatValue(change.oldValue)}
                </pre>
              </div>
              <div className="bg-green-50 border border-green-200 rounded p-2">
                <div className="text-xs text-green-600 font-medium mb-1">新值:</div>
                <pre className="text-xs overflow-x-auto">
                  {formatValue(change.newValue)}
                </pre>
              </div>
            </div>
          ) : null}

          {change.type === 'addition' && change.newValue ? (
            <div className="bg-green-50 border border-green-200 rounded p-2 mt-2">
              <div className="text-xs text-green-600 font-medium mb-1">新增值:</div>
              <pre className="text-xs overflow-x-auto">
                {formatValue(change.newValue)}
              </pre>
            </div>
          ) : null}

          {change.type === 'deletion' && change.oldValue ? (
            <div className="bg-red-50 border border-red-200 rounded p-2 mt-2">
              <div className="text-xs text-red-600 font-medium mb-1">删除值:</div>
              <pre className="text-xs overflow-x-auto">
                {formatValue(change.oldValue)}
              </pre>
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
};

/**
 * 版本对比组件
 */
export const VersionDiffView: React.FC<VersionDiffProps> = ({
  diff,
  className,
}) => {
  if (!diff.version1 || !diff.version2) {
    return <div>版本数据不完整</div>;
  }

  const v1TypeInfo = VERSION_TYPE_INFO[diff.version1.versionType];
  const v2TypeInfo = VERSION_TYPE_INFO[diff.version2.versionType];

  return (
    <div className={cn("space-y-4", className)}>
      {/* 版本对比头部 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <GitCompare className="w-5 h-5" />
            版本对比
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between">
            {/* 版本1 */}
            <div className="flex items-center gap-2">
              <Badge className={cn(v1TypeInfo.bgColor, v1TypeInfo.color)}>
                {v1TypeInfo.displayName}
              </Badge>
              <Badge variant="outline">{diff.version1.versionNumber}</Badge>
            </div>

            <ArrowRight className="w-5 h-5 text-muted-foreground" />

            {/* 版本2 */}
            <div className="flex items-center gap-2">
              <Badge className={cn(v2TypeInfo.bgColor, v2TypeInfo.color)}>
                {v2TypeInfo.displayName}
              </Badge>
              <Badge variant="outline">{diff.version2.versionNumber}</Badge>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 统计信息 */}
      {diff.stats && (
        <Card>
          <CardContent className="pt-6">
            <div className="grid grid-cols-3 gap-4 text-center">
              <div className="space-y-1">
                <div className="text-2xl font-bold text-green-600">
                  +{diff.stats.additions}
              </div>
              <div className="text-sm text-muted-foreground">新增</div>
            </div>
            <div className="space-y-1">
              <div className="text-2xl font-bold text-red-600">
                -{diff.stats.deletions}
              </div>
              <div className="text-sm text-muted-foreground">删除</div>
            </div>
            <div className="space-y-1">
              <div className="text-2xl font-bold text-blue-600">
                ~{diff.stats.modifications}
              </div>
              <div className="text-sm text-muted-foreground">修改</div>
            </div>
          </div>
        </CardContent>
      </Card>
      )}

      {/* 变更列表 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <TrendingUp className="w-5 h-5" />
            变更详情 ({diff.changes.length})
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ScrollArea className="h-[500px]">
            <div className="space-y-3">
              {diff.changes.length === 0 ? (
                <div className="text-center text-muted-foreground py-8">
                  <p className="text-sm">两个版本没有差异</p>
                </div>
              ) : (
                diff.changes.map((change, index) => (
                  <ChangeItem key={index} change={change} />
                ))
              )}
            </div>
          </ScrollArea>
        </CardContent>
      </Card>
    </div>
  );
};
