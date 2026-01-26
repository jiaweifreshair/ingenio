'use client';

/**
 * 时光机版本管理入口页面
 *
 * 功能：
 * - 展示用户所有项目列表
 * - 点击项目进入该项目的版本历史页面
 * - 支持搜索和筛选
 */

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { TopNav } from '@/components/layout/top-nav';
import { Footer } from '@/components/layout/footer';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Alert, AlertDescription } from '@/components/ui/alert';
import {
  Clock,
  Search,
  ArrowRight,
  Inbox,
  AlertCircle,
  GitBranch,
  Calendar,
  FileCode
} from 'lucide-react';
import { listProjects } from '@/lib/api/projects';
import { hasToken } from '@/lib/auth/token';
import type { Project } from '@/types/project';

/**
 * 时光机版本管理入口页面
 */
export default function VersionsPage() {
  const router = useRouter();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchKeyword, setSearchKeyword] = useState('');

  /**
   * 加载项目列表
   */
  const loadProjects = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const data = await listProjects({
        current: 1,
        size: 100, // 加载所有项目
        keyword: searchKeyword || undefined,
      });

      setProjects(data.records);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '加载项目列表失败';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  }, [searchKeyword]);

  /**
   * 初始化加载
   */
  useEffect(() => {
    if (!hasToken()) {
      router.replace('/login');
      return;
    }
    loadProjects();
  }, [loadProjects, router]);

  /**
   * 处理搜索
   */
  const handleSearch = () => {
    loadProjects();
  };

  /**
   * 处理项目点击，进入版本历史页面
   * 优先使用 appSpecId 进入版本历史页面
   */
  const handleProjectClick = (project: Project) => {
    // 优先使用 appSpecId（如果存在），否则使用 project.id
    const targetId = project.appSpecId || project.id;
    router.push(`/versions/${targetId}`);
  };

  /**
   * 格式化日期
   */
  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  /**
   * 获取状态标签颜色
   */
  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'completed':
        return <Badge className="bg-green-500">生成完成</Badge>;
      case 'generating':
        return <Badge className="bg-yellow-500">生成中</Badge>;
      case 'draft':
        return <Badge variant="secondary">草稿</Badge>;
      case 'archived':
        return <Badge variant="outline">已归档</Badge>;
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-gradient-to-b from-gray-50 to-white dark:from-gray-900 dark:to-gray-950">
      {/* 顶部导航 */}
      <TopNav />

      {/* 主内容区 */}
      <main className="flex-1 container mx-auto px-4 py-8 max-w-6xl">
        {/* 页面标题 */}
        <div className="mb-8">
          <div className="flex items-center gap-3 mb-2">
            <Clock className="h-8 w-8 text-orange-500" />
            <h1 className="text-3xl font-bold">时光机版本管理</h1>
          </div>
          <p className="text-muted-foreground">
            选择一个项目查看其版本历史，支持版本对比、回滚等操作
          </p>
        </div>

        {/* 搜索栏 */}
        <div className="mb-6 flex gap-3">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="搜索项目名称..."
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              className="pl-10"
            />
          </div>
          <Button onClick={handleSearch}>
            <Search className="h-4 w-4 mr-2" />
            搜索
          </Button>
        </div>

        {/* 错误提示 */}
        {error && (
          <Alert variant="destructive" className="mb-6">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {/* 项目列表 */}
        {loading ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {[...Array(6)].map((_, i) => (
              <Skeleton key={i} className="h-48 rounded-xl" />
            ))}
          </div>
        ) : projects.length > 0 ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {projects.map((project) => (
              <Card
                key={project.id}
                className="group cursor-pointer transition-all hover:shadow-lg hover:border-orange-300 dark:hover:border-orange-700"
                onClick={() => handleProjectClick(project)}
              >
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between">
                    <div className="flex-1 min-w-0">
                      <CardTitle className="text-lg truncate group-hover:text-orange-600 transition-colors">
                        {project.name || '未命名项目'}
                      </CardTitle>
                      <CardDescription className="line-clamp-2 mt-1">
                        {project.description || '暂无描述'}
                      </CardDescription>
                    </div>
                    <ArrowRight className="h-5 w-5 text-muted-foreground group-hover:text-orange-500 group-hover:translate-x-1 transition-all flex-shrink-0" />
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="space-y-3">
                    {/* 状态标签 */}
                    <div className="flex items-center gap-2">
                      {getStatusBadge(project.status)}
                    </div>

                    {/* 元数据 */}
                    <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                      <div className="flex items-center gap-1">
                        <Calendar className="h-3.5 w-3.5" />
                        <span>{formatDate(project.createdAt)}</span>
                      </div>
                      {project.appSpecId && (
                        <div className="flex items-center gap-1">
                          <GitBranch className="h-3.5 w-3.5" />
                          <span>有版本历史</span>
                        </div>
                      )}
                    </div>

                    {/* 快捷操作提示 */}
                    <div className="pt-2 border-t">
                      <p className="text-xs text-muted-foreground flex items-center gap-1">
                        <FileCode className="h-3 w-3" />
                        点击查看版本历史和代码快照
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : (
          // 空状态
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <Inbox className="h-24 w-24 text-gray-300 mb-4" />
            <h3 className="text-xl font-semibold mb-2">
              {searchKeyword ? '没有找到匹配的项目' : '还没有创建任何项目'}
            </h3>
            <p className="text-muted-foreground mb-6">
              {searchKeyword
                ? '尝试调整搜索关键词'
                : '创建项目后，可以在这里管理版本历史'}
            </p>
            {!searchKeyword && (
              <Button onClick={() => router.push('/wizard/ai-capabilities')}>
                创建新项目
              </Button>
            )}
          </div>
        )}
      </main>

      {/* 页脚 */}
      <Footer />
    </div>
  );
}
