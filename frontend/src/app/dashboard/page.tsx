'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { TopNav } from '@/components/layout/top-nav';
import { Footer } from '@/components/layout/footer';
import { StatsCards } from './components/StatsCards';
import { FilterBar } from './components/FilterBar';
import { AppCard } from './components/AppCard';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import {
  getProjectStats,
  listProjects,
  deleteProject,
  forkProject,
} from '@/lib/api/projects';
import type { Project, ProjectStats, ProjectFilters } from '@/types/project';
import { ProjectStatus } from '@/types/project';
import { Plus, AlertCircle, Inbox, Sparkles, Clock } from 'lucide-react';
import { useToast } from '@/hooks/use-toast';

/**
 * 应用仪表板页面
 * 集中管理所有应用
 */
export default function DashboardPage(): React.ReactElement {
  const router = useRouter();
  const { toast } = useToast();

  // 状态管理
  const [stats, setStats] = useState<ProjectStats | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [hasMore, setHasMore] = useState(false);

  // 筛选条件
  const [filters, setFilters] = useState<ProjectFilters>({
    keyword: '',
    status: '',
    current: 1,
    size: 12,
  });

  /**
   * 加载统计数据
   */
  const loadStats = useCallback(async () => {
    try {
      const data = await getProjectStats();
      setStats(data);
    } catch (err) {
      console.error('加载统计数据失败:', err);
      // 设置默认统计数据，避免页面显示异常
      setStats({
        totalProjects: 0,
        monthlyNewProjects: 0,
        generatingTasks: 0,
        publishedProjects: 0,
        draftProjects: 0,
        archivedProjects: 0,
      });
      toast({
        title: '加载失败',
        description: '无法加载统计数据，请刷新页面重试',
        variant: 'destructive',
      });
    }
  }, [toast]);

  /**
   * 加载项目列表
   */
  const loadProjects = useCallback(
    async (page: number = 1) => {
      try {
        setLoading(true);
        setError(null);

        const data = await listProjects({
          ...filters,
          current: page,
        });

        setProjects(data.records);
        setCurrentPage(Number(data.current));
        setTotalPages(Number(data.pages));
        setHasMore(data.hasNext);
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : '加载失败';
        setError(errorMessage);
        toast({
          title: '加载失败',
          description: errorMessage,
          variant: 'destructive',
        });
      } finally {
        setLoading(false);
      }
    },
    [filters, toast]
  );

  /**
   * 初始化加载
   */
  useEffect(() => {
    loadStats();
    loadProjects();
  }, [loadStats, loadProjects]);

  /**
   * 处理搜索
   */
  const handleSearch = () => {
    setCurrentPage(1);
    loadProjects(1);
  };

  /**
   * 处理状态筛选变化
   * 将 "all" 转换为空字符串以表示全部状态
   */
  const handleStatusChange = (status: string) => {
    setFilters((prev) => ({
      ...prev,
      status: (status === 'all' ? '' : status) as ProjectStatus | '',
      current: 1
    }));
    setCurrentPage(1);
  };

  /**
   * 处理关键词变化
   */
  const handleKeywordChange = (keyword: string) => {
    setFilters((prev) => ({ ...prev, keyword }));
  };

  /**
   * 处理查看详情
   */
  const handleView = (id: string) => {
    router.push(`/preview/${id}`);
  };

  /**
   * 处理继续编辑
   */
  const handleEdit = (id: string) => {
    router.push(`/wizard/${id}`);
  };

  /**
   * 处理复制应用
   */
  const handleCopy = async (id: string) => {
    try {
      toast({
        title: '复制中...',
        description: '正在复制应用，请稍候',
      });

      await forkProject(id);

      toast({
        title: '复制成功',
        description: '应用已成功复制',
      });

      // 刷新列表
      await loadProjects(currentPage);
      await loadStats();
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '复制失败';
      toast({
        title: '复制失败',
        description: errorMessage,
        variant: 'destructive',
      });
    }
  };

  /**
   * 处理删除应用
   */
  const handleDelete = async (id: string) => {
    if (!confirm('确定要删除这个应用吗？此操作无法撤销。')) {
      return;
    }

    try {
      toast({
        title: '删除中...',
        description: '正在删除应用，请稍候',
      });

      await deleteProject(id);

      toast({
        title: '删除成功',
        description: '应用已成功删除',
      });

      // 刷新列表
      await loadProjects(currentPage);
      await loadStats();
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : '删除失败';
      toast({
        title: '删除失败',
        description: errorMessage,
        variant: 'destructive',
      });
    }
  };

  /**
   * 处理分页
   */
  const handlePageChange = (page: number) => {
    setCurrentPage(page);
    loadProjects(page);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  /**
   * 创建新应用 - V2.0意图识别+双重选择机制
   */
  const handleCreateNew = () => {
    router.push('/');
  };

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      {/* 顶部导航 */}
      <TopNav />

      {/* 主要内容 */}
      <main className="flex-1 container mx-auto px-4 py-8 max-w-7xl">
        {/* 页面标题和操作 */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-3xl font-bold">我的应用</h1>
            <p className="text-muted-foreground mt-1">
              管理您创建的所有应用
            </p>
          </div>
          <Button onClick={handleCreateNew} size="lg" className="gap-2">
            <Plus className="h-5 w-5" />
            创建新应用
          </Button>
        </div>

        {/* 统计卡片 */}
        {stats ? (
          <StatsCards stats={stats} />
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4 mb-8">
            {[...Array(4)].map((_, i) => (
              <Skeleton key={i} className="h-32 rounded-2xl" />
            ))}
          </div>
        )}

        {/* 快速访问核心功能 */}
        <div className="my-8 p-6 bg-white rounded-2xl shadow-sm border border-gray-200">
          <h2 className="text-lg font-semibold mb-4">快速访问核心功能</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <Button
              variant="outline"
              onClick={() => router.push('/wizard/ai-capabilities')}
              className="flex items-center gap-2 h-auto p-4 justify-start"
            >
              <Sparkles className="h-5 w-5 text-purple-500" />
              <div className="text-left">
                <div className="font-semibold">AI能力选择</div>
                <div className="text-xs text-muted-foreground">智能需求分析</div>
              </div>
            </Button>

            <Button
              variant="outline"
              onClick={() => router.push('/dashboard')}
              className="flex items-center gap-2 h-auto p-4 justify-start"
            >
              <Clock className="h-5 w-5 text-orange-500" />
              <div className="text-left">
                <div className="font-semibold">时光机版本</div>
                <div className="text-xs text-muted-foreground">版本管理回溯</div>
              </div>
            </Button>
          </div>
        </div>

        {/* 筛选栏 */}
        <div className="mt-8 mb-6">
          <FilterBar
            keyword={filters.keyword || ''}
            status={filters.status || 'all'}
            onKeywordChange={handleKeywordChange}
            onStatusChange={handleStatusChange}
            onSearch={handleSearch}
          />
        </div>

        {/* 错误提示 */}
        {error && (
          <Alert variant="destructive" className="mb-6">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {/* 应用列表 */}
        {loading ? (
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {[...Array(6)].map((_, i) => (
              <Skeleton key={i} className="h-96 rounded-2xl" />
            ))}
          </div>
        ) : projects.length > 0 ? (
          <>
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              {projects.map((project) => (
                <AppCard
                  key={project.id}
                  project={project}
                  onView={handleView}
                  onEdit={handleEdit}
                  onCopy={handleCopy}
                  onDelete={handleDelete}
                />
              ))}
            </div>

            {/* 分页器 */}
            {totalPages > 1 && (
              <div className="mt-8 flex items-center justify-center gap-2">
                <Button
                  variant="outline"
                  disabled={currentPage === 1}
                  onClick={() => handlePageChange(currentPage - 1)}
                >
                  上一页
                </Button>

                <div className="flex items-center gap-1">
                  {[...Array(totalPages)].map((_, i) => {
                    const page = i + 1;
                    // 只显示当前页附近的页码
                    if (
                      page === 1 ||
                      page === totalPages ||
                      (page >= currentPage - 1 && page <= currentPage + 1)
                    ) {
                      return (
                        <Button
                          key={page}
                          variant={page === currentPage ? 'default' : 'outline'}
                          size="sm"
                          onClick={() => handlePageChange(page)}
                        >
                          {page}
                        </Button>
                      );
                    } else if (
                      page === currentPage - 2 ||
                      page === currentPage + 2
                    ) {
                      return <span key={page}>...</span>;
                    }
                    return null;
                  })}
                </div>

                <Button
                  variant="outline"
                  disabled={!hasMore}
                  onClick={() => handlePageChange(currentPage + 1)}
                >
                  下一页
                </Button>
              </div>
            )}
          </>
        ) : (
          // 空状态
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <Inbox className="h-24 w-24 text-gray-300 mb-4" />
            <h3 className="text-xl font-semibold mb-2">
              {filters.keyword || filters.status
                ? '没有找到匹配的应用'
                : '还没有创建任何应用'}
            </h3>
            <p className="text-muted-foreground mb-6">
              {filters.keyword || filters.status
                ? '尝试调整筛选条件或搜索关键词'
                : '点击"创建新应用"按钮开始您的第一个项目'}
            </p>
            {!filters.keyword && !filters.status && (
              <Button onClick={handleCreateNew} size="lg" className="gap-2">
                <Plus className="h-5 w-5" />
                创建新应用
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
