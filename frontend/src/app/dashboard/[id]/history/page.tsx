'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { TopNav } from '@/components/layout/top-nav';
import { Footer } from '@/components/layout/footer';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { getProjectExecutionHistory } from '@/lib/api/projects';
import type { GenerationTask } from '@/types/project';
import { GenerationTaskStatus, AgentType } from '@/types/project';
import { ArrowLeft, Clock, CheckCircle, XCircle, Loader2, AlertCircle } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { zhCN } from 'date-fns/locale/zh-CN';

/**
 * 执行历史回放页面
 * 展示项目的所有生成任务执行历史
 */
export default function ExecutionHistoryPage(): React.ReactElement {
  const params = useParams();
  const router = useRouter();
  const projectId = params.id as string;

  const [tasks, setTasks] = useState<GenerationTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  /**
   * 加载执行历史
   */
  useEffect(() => {
    const loadHistory = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await getProjectExecutionHistory(projectId);
        setTasks(data);
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : '加载执行历史失败';
        setError(errorMessage);
      } finally {
        setLoading(false);
      }
    };

    loadHistory();
  }, [projectId]);

  /**
   * 获取状态标签
   */
  const getStatusBadge = (status: GenerationTaskStatus) => {
    switch (status) {
      case GenerationTaskStatus.COMPLETED:
        return (
          <Badge variant="default" className="bg-green-500">
            <CheckCircle className="w-3 h-3 mr-1" />
            已完成
          </Badge>
        );
      case GenerationTaskStatus.FAILED:
        return (
          <Badge variant="destructive">
            <XCircle className="w-3 h-3 mr-1" />
            失败
          </Badge>
        );
      case GenerationTaskStatus.PLANNING:
      case GenerationTaskStatus.EXECUTING:
      case GenerationTaskStatus.VALIDATING:
      case GenerationTaskStatus.GENERATING:
        return (
          <Badge variant="secondary">
            <Loader2 className="w-3 h-3 mr-1 animate-spin" />
            进行中
          </Badge>
        );
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
  };

  /**
   * 获取Agent标签
   */
  const getAgentBadge = (agent: AgentType) => {
    const agentLabels = {
      [AgentType.PLAN]: '规划Agent',
      [AgentType.EXECUTE]: '执行Agent',
      [AgentType.VALIDATE]: '验证Agent',
      [AgentType.GENERATE]: '生成Agent',
    };
    return <Badge variant="outline">{agentLabels[agent] || agent}</Badge>;
  };

  /**
   * 格式化时间
   */
  const formatTime = (dateString: string) => {
    try {
      const date = new Date(dateString);
      return formatDistanceToNow(date, { addSuffix: true, locale: zhCN });
    } catch {
      return dateString;
    }
  };

  /**
   * 计算执行时长
   */
  const calculateDuration = (task: GenerationTask): string => {
    if (!task.startedAt) return '-';
    const start = new Date(task.startedAt);
    const end = task.completedAt ? new Date(task.completedAt) : new Date();
    const durationMs = end.getTime() - start.getTime();
    const minutes = Math.floor(durationMs / 60000);
    const seconds = Math.floor((durationMs % 60000) / 1000);
    return `${minutes}分${seconds}秒`;
  };

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <TopNav />

      <main className="flex-1 container mx-auto px-4 py-8 max-w-6xl">
        {/* 页面标题 */}
        <div className="flex items-center gap-4 mb-8">
          <Button variant="ghost" size="icon" onClick={() => router.back()}>
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-3xl font-bold">执行历史</h1>
            <p className="text-muted-foreground mt-1">查看项目的所有生成任务执行记录</p>
          </div>
        </div>

        {/* 错误提示 */}
        {error && (
          <Alert variant="destructive" className="mb-6">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {/* 加载状态 */}
        {loading ? (
          <div className="space-y-4">
            {[...Array(3)].map((_, i) => (
              <Skeleton key={i} className="h-48 rounded-2xl" />
            ))}
          </div>
        ) : tasks.length > 0 ? (
          <div className="space-y-6">
            {tasks.map((task) => (
              <Card key={task.id} className="overflow-hidden">
                <CardHeader className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-950 dark:to-indigo-950">
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <CardTitle className="text-lg">{task.taskName}</CardTitle>
                      <p className="text-sm text-muted-foreground mt-1">
                        {task.userRequirement}
                      </p>
                    </div>
                    {getStatusBadge(task.status as GenerationTaskStatus)}
                  </div>
                </CardHeader>

                <CardContent className="pt-6">
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">当前Agent</div>
                      {getAgentBadge(task.currentAgent as AgentType)}
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">进度</div>
                      <div className="flex items-center gap-2">
                        <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-blue-500 transition-all"
                            style={{ width: `${task.progress}%` }}
                          />
                        </div>
                        <span className="text-sm font-medium">{task.progress}%</span>
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">执行时长</div>
                      <div className="flex items-center gap-1 text-sm">
                        <Clock className="w-3 h-3" />
                        {calculateDuration(task)}
                      </div>
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground mb-1">创建时间</div>
                      <div className="text-sm">{formatTime(task.createdAt)}</div>
                    </div>
                  </div>

                  {task.errorMessage && (
                    <Alert variant="destructive" className="mt-4">
                      <AlertCircle className="h-4 w-4" />
                      <AlertDescription>{task.errorMessage}</AlertDescription>
                    </Alert>
                  )}

                  {task.qualityScore !== undefined && (
                    <div className="mt-4 p-4 bg-green-50 dark:bg-green-950 rounded-lg">
                      <div className="text-sm font-medium text-green-900 dark:text-green-100">
                        质量评分: {task.qualityScore}/100
                      </div>
                    </div>
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <Clock className="h-24 w-24 text-gray-300 mb-4" />
            <h3 className="text-xl font-semibold mb-2">暂无执行历史</h3>
            <p className="text-muted-foreground">该项目还没有生成任务执行记录</p>
          </div>
        )}
      </main>

      <Footer />
    </div>
  );
}
