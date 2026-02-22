/**
 * 版本控制页面 - 时光机版本历史管理
 *
 * 功能：
 * - 版本时间线展示（垂直时间轴，类似Git历史）
 * - 版本详情查看（JSON格式化展示快照数据）
 * - 版本对比功能（左右并排展示差异）
 * - 版本回滚功能（创建新的ROLLBACK版本）
 *
 * 布局：
 * - 左侧：版本时间线（可滚动）
 * - 右侧：版本详情/对比视图（可切换）
 * - 顶部：操作栏（对比、回滚、导出）
 */
'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Loader2,
  AlertCircle,
  GitBranch,
  GitCompare,
  RotateCcw,
  ArrowLeft,
  CheckSquare,
  XSquare
} from 'lucide-react';
import {
  getVersionTimeline,
  getVersionTimelineByAppSpec,
  getVersionDetail,
  compareVersions as compareVersionsAPI,
  rollbackToVersion
} from '@/lib/api/timemachine';
import {
  VersionTimelineItem,
  VersionDetail,
  VersionDiff
} from '@/types/version';
import { VersionTimeline } from '@/components/versions/version-timeline';
import { VersionDetailView } from '@/components/versions/version-detail';
import { VersionDiffView } from '@/components/versions/version-diff';

/**
 * 视图模式
 */
type ViewMode = 'detail' | 'diff';

/**
 * 版本控制页面
 */
export default function VersionsPage() {
  const params = useParams();
  const router = useRouter();
  const appId = params.appId as string;

  // 状态管理
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [versions, setVersions] = useState<VersionTimelineItem[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<VersionTimelineItem | null>(null);
  const [versionDetail, setVersionDetail] = useState<VersionDetail | null>(null);
  const [viewMode, setViewMode] = useState<ViewMode>('detail');
  const [diffData, setDiffData] = useState<VersionDiff | null>(null);

  // 对比模式选择
  const [compareMode, setCompareMode] = useState(false);
  const [compareVersions, setCompareVersions] = useState<string[]>([]);

  // 加载版本历史时间线
  useEffect(() => {
    const fetchTimeline = async () => {
      try {
        setLoading(true);
        setError(null);

        // 优先尝试通过 appSpecId 获取版本时间线
        let response = await getVersionTimelineByAppSpec(appId);

        // 如果 appSpecId 未找到版本，尝试直接使用 taskId
        if (response.success && (!response.data || response.data.length === 0)) {
          response = await getVersionTimeline(appId);
        }

        if (response.success && response.data) {
          setVersions(response.data);

          // 默认选中第一个版本（最新版本）
          if (response.data.length > 0) {
            setSelectedVersion(response.data[0]);
          }
        } else {
          throw new Error(response.error || '获取版本历史失败');
        }
      } catch (err) {
        console.error('获取版本历史失败:', err);
        setError(err instanceof Error ? err.message : '获取版本历史失败');
      } finally {
        setLoading(false);
      }
    };

    if (appId) {
      fetchTimeline();
    }
  }, [appId]);

  // 加载选中版本的详情
  useEffect(() => {
    const fetchDetail = async () => {
      if (!selectedVersion) return;

      try {
        const response = await getVersionDetail(selectedVersion.versionId);

        if (response.success && response.data) {
          setVersionDetail(response.data);
        } else {
          console.error('获取版本详情失败:', response.error);
        }
      } catch (err) {
        console.error('获取版本详情失败:', err);
      }
    };

    if (viewMode === 'detail') {
      fetchDetail();
    }
  }, [selectedVersion, viewMode]);

  // 处理版本点击
  const handleVersionClick = useCallback((version: VersionTimelineItem) => {
    if (compareMode) {
      // 对比模式：选择两个版本进行对比
      if (compareVersions.includes(version.versionId)) {
        setCompareVersions(compareVersions.filter(id => id !== version.versionId));
      } else if (compareVersions.length < 2) {
        setCompareVersions([...compareVersions, version.versionId]);
      }
    } else {
      // 普通模式：查看版本详情
      setSelectedVersion(version);
      setViewMode('detail');
      setDiffData(null);
    }
  }, [compareMode, compareVersions]);

  // 开始对比
  const handleStartCompare = useCallback(() => {
    setCompareMode(true);
    setCompareVersions([]);
  }, []);

  // 取消对比
  const handleCancelCompare = useCallback(() => {
    setCompareMode(false);
    setCompareVersions([]);
  }, []);

  // 执行对比
  const handleExecuteCompare = useCallback(async () => {
    if (compareVersions.length !== 2) {
      alert('请选择两个版本进行对比');
      return;
    }

    try {
      const response = await compareVersionsAPI(compareVersions[0], compareVersions[1]);

      if (response.success && response.data) {
        setDiffData(response.data);
        setViewMode('diff');
        setCompareMode(false);
      } else {
        throw new Error(response.error || '版本对比失败');
      }
    } catch (err) {
      console.error('版本对比失败:', err);
      alert(err instanceof Error ? err.message : '版本对比失败');
    }
  }, [compareVersions]);

  // 处理版本回滚
  const handleRollback = useCallback(async () => {
    if (!selectedVersion) return;

    const confirmed = window.confirm(
      `确定要回滚到版本 ${selectedVersion.versionNumber} (${selectedVersion.versionType}) 吗？\n\n` +
      `这将创建一个新的ROLLBACK类型版本，复制该版本的快照数据。\n` +
      `原有版本不会被删除。`
    );

    if (!confirmed) return;

    try {
      const response = await rollbackToVersion(selectedVersion.versionId);

      if (response.success && response.data) {
        alert('版本回滚成功！已创建新的ROLLBACK版本');

        // 重新加载版本历史
        const timelineResponse = await getVersionTimeline(appId);
        if (timelineResponse.success && timelineResponse.data) {
          setVersions(timelineResponse.data);

          // 选中新创建的ROLLBACK版本
          const rollbackVersion = timelineResponse.data.find(
            v => v.versionType === 'ROLLBACK' && v.versionId === response.data!.id
          );
          if (rollbackVersion) {
            setSelectedVersion(rollbackVersion);
          }
        }
      } else {
        throw new Error(response.error || '版本回滚失败');
      }
    } catch (err) {
      console.error('版本回滚失败:', err);
      alert(err instanceof Error ? err.message : '版本回滚失败');
    }
  }, [selectedVersion, appId]);

  // 加载中状态
  if (loading) {
    return (
      <div className="h-screen bg-background">
        <div className="border-b bg-background px-6 py-4">
          <div className="flex items-center gap-4">
            <h1 className="text-xl font-semibold">版本控制</h1>
            <Loader2 className="w-4 h-4 animate-spin text-primary" />
            <Badge variant="outline" className="text-sm">加载中</Badge>
          </div>
        </div>

        <div className="flex items-center justify-center h-[calc(100vh-73px)]">
          <div className="text-center">
            <Loader2 className="w-8 h-8 animate-spin mx-auto mb-4 text-primary" />
            <p className="text-muted-foreground">正在加载版本历史...</p>
          </div>
        </div>
      </div>
    );
  }

  // 错误状态
  if (error) {
    return (
      <div className="h-screen bg-background">
        <div className="border-b bg-background px-6 py-4">
          <div className="flex items-center gap-4">
            <h1 className="text-xl font-semibold">版本控制</h1>
            <Badge variant="destructive" className="text-sm">加载失败</Badge>
          </div>
        </div>

        <div className="flex items-center justify-center h-[calc(100vh-73px)] p-4">
          <Card className="max-w-md w-full">
            <CardContent className="pt-6">
              <div className="text-center">
                <AlertCircle className="w-12 h-12 text-destructive mx-auto mb-4" />
                <h3 className="text-lg font-semibold mb-2">加载失败</h3>
                <p className="text-muted-foreground mb-4">{error}</p>
                <div className="flex gap-3 justify-center">
                  <Button onClick={() => window.location.reload()}>重试</Button>
                  <Button variant="outline" onClick={() => router.back()}>
                    返回
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  // 主界面
  return (
    <div className="h-screen bg-background flex flex-col">
      {/* 顶部标题栏 */}
      <div className="border-b bg-background px-6 py-4 flex-shrink-0">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => router.back()}
            >
              <ArrowLeft className="w-4 h-4 mr-2" />
              返回
            </Button>
            <h1 className="text-xl font-semibold">版本控制</h1>
            <Badge variant="outline" className="text-sm">
              {versions.length} 个版本
            </Badge>
          </div>

          {/* 操作按钮 */}
          <div className="flex items-center gap-2">
            {compareMode ? (
              <>
                <Badge variant="secondary" className="text-sm">
                  已选择 {compareVersions.length}/2
                </Badge>
                <Button
                  variant="default"
                  size="sm"
                  onClick={handleExecuteCompare}
                  disabled={compareVersions.length !== 2}
                >
                  <GitCompare className="w-4 h-4 mr-2" />
                  执行对比
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleCancelCompare}
                >
                  <XSquare className="w-4 h-4 mr-2" />
                  取消
                </Button>
              </>
            ) : (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleStartCompare}
                  disabled={versions.length < 2}
                >
                  <CheckSquare className="w-4 h-4 mr-2" />
                  选择对比
                </Button>
                {selectedVersion && selectedVersion.canRollback && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleRollback}
                  >
                    <RotateCcw className="w-4 h-4 mr-2" />
                    回滚版本
                  </Button>
                )}
              </>
            )}
          </div>
        </div>
      </div>

      {/* 对比模式提示 */}
      {compareMode && (
        <Alert className="m-6 mb-0">
          <CheckSquare className="h-4 w-4" />
          <AlertDescription>
            对比模式已开启，请在时间线中选择两个版本进行对比
          </AlertDescription>
        </Alert>
      )}

      {/* 主内容区 - 分屏布局 */}
      <div className="flex-1 flex overflow-hidden">
        {/* 左侧：版本时间线 */}
        <div className="w-96 border-r bg-background overflow-hidden flex flex-col">
          <div className="px-6 py-4 border-b">
            <h2 className="font-semibold flex items-center gap-2">
              <GitBranch className="w-4 h-4" />
              版本历史
            </h2>
          </div>
          <ScrollArea className="flex-1 px-6">
            <VersionTimeline
              versions={versions}
              selectedVersionId={
                compareMode
                  ? undefined
                  : selectedVersion?.versionId
              }
              currentVersionId={versions[0]?.versionId}
              onVersionClick={handleVersionClick}
            />
          </ScrollArea>

          {/* 对比模式选择状态 */}
          {compareMode && (
            <div className="px-6 py-4 border-t bg-muted/50">
              <div className="space-y-2">
                {compareVersions.map((versionId, index) => {
                  const version = versions.find(v => v.versionId === versionId);
                  if (!version) return null;

                  return (
                    <div
                      key={versionId}
                      className="flex items-center justify-between text-sm"
                    >
                      <span className="text-muted-foreground">
                        版本 {index + 1}:
                      </span>
                      <Badge variant="outline" className="text-xs">
                        {version.versionNumber}
                      </Badge>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* 右侧：版本详情/对比视图 */}
        <div className="flex-1 bg-background overflow-hidden">
          <ScrollArea className="h-full px-8 py-6">
            {viewMode === 'detail' && versionDetail && (
              <VersionDetailView
                version={versionDetail}
                canRollback={selectedVersion?.canRollback}
                onRollback={handleRollback}
              />
            )}

            {viewMode === 'diff' && diffData && (
              <VersionDiffView diff={diffData} />
            )}

            {!versionDetail && !diffData && (
              <div className="flex items-center justify-center h-full">
                <div className="text-center text-muted-foreground">
                  <p className="text-sm">请选择一个版本查看详情</p>
                </div>
              </div>
            )}
          </ScrollArea>
        </div>
      </div>
    </div>
  );
}
