/**
 * 版本详情组件
 * 显示版本的完整快照数据，使用JSON格式化展示
 */
'use client';

import React from 'react';
import { cn } from '@/lib/utils';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  FileJson,
  Copy,
  Check,
  Calendar,
  GitBranch,
  RotateCcw
} from 'lucide-react';
import { VersionDetail, VERSION_TYPE_INFO } from '@/types/version';

/**
 * 版本详情组件Props
 */
interface VersionDetailProps {
  /** 版本详情 */
  version: VersionDetail;
  /** 是否可以回滚 */
  canRollback?: boolean;
  /** 回滚回调 */
  onRollback?: () => void;
  /** 类名 */
  className?: string;
}

/**
 * JSON格式化组件
 */
const JsonViewer: React.FC<{ data: Record<string, unknown> }> = ({ data }) => {
  const [copied, setCopied] = React.useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(JSON.stringify(data, null, 2));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('复制失败:', err);
    }
  };

  return (
    <div className="relative">
      <Button
        variant="ghost"
        size="sm"
        className="absolute top-2 right-2 z-10"
        onClick={handleCopy}
      >
        {copied ? (
          <>
            <Check className="w-4 h-4 mr-1" />
            已复制
          </>
        ) : (
          <>
            <Copy className="w-4 h-4 mr-1" />
            复制
          </>
        )}
      </Button>
      <ScrollArea className="h-[400px]">
        <pre className="text-xs bg-muted p-4 rounded-md overflow-x-auto">
          <code>{JSON.stringify(data, null, 2)}</code>
        </pre>
      </ScrollArea>
    </div>
  );
};

/**
 * 格式化时间
 */
const formatDateTime = (isoString: string): string => {
  const date = new Date(isoString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
};

/**
 * 版本详情组件
 */
export const VersionDetailView: React.FC<VersionDetailProps> = ({
  version,
  canRollback = true,
  onRollback,
  className,
}) => {
  const typeInfo = VERSION_TYPE_INFO[version.versionType as keyof typeof VERSION_TYPE_INFO];

  return (
    <div className={cn("space-y-4", className)}>
      {/* 版本头部信息 */}
      <Card>
        <CardHeader>
          <div className="flex items-start justify-between">
            <div>
              <CardTitle className="flex items-center gap-2 mb-2">
                <GitBranch className="w-5 h-5" />
                版本详情
              </CardTitle>
              <div className="flex items-center gap-2">
                <Badge className={cn(typeInfo?.bgColor, typeInfo?.color)}>
                  {typeInfo?.displayName || version.versionType}
                </Badge>
                <Badge variant="outline">v{version.versionNumber}</Badge>
              </div>
            </div>

            {canRollback && version.versionType !== 'ROLLBACK' && onRollback && (
              <Button onClick={onRollback} size="sm">
                <RotateCcw className="w-4 h-4 mr-2" />
                回滚到此版本
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="flex items-center gap-2 text-sm">
              <Calendar className="w-4 h-4 text-muted-foreground" />
              <span className="text-muted-foreground">创建时间:</span>
              <span className="font-medium">{formatDateTime(version.createdAt)}</span>
            </div>

            {version.description && (
              <div className="flex items-center gap-2 text-sm col-span-2">
                <FileJson className="w-4 h-4 text-muted-foreground" />
                <span className="text-muted-foreground">描述:</span>
                <span className="font-medium">{version.description}</span>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* 快照数据 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <FileJson className="w-5 h-5" />
            快照数据
          </CardTitle>
        </CardHeader>
        <CardContent>
          <JsonViewer data={version.snapshot || {}} />
        </CardContent>
      </Card>
    </div>
  );
};
