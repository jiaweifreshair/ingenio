/**
 * 我的应用Section
 * 应用列表、快速操作、筛选和搜索
 *
 * 功能：
 * - 获取应用列表（真实API，复用projects API）
 * - 删除应用（真实API）
 * - 搜索应用（真实API）
 * - 乐观UI更新
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Skeleton } from "@/components/ui/skeleton"
import { Search, Edit, Trash2, Eye, Plus, Calendar, Smartphone, AlertCircle } from "lucide-react"
import { useToast } from "@/hooks/use-toast"
import { listProjects, deleteProject } from "@/lib/api/projects"
import { ProjectStatus, type Project } from "@/types/project"

/**
 * 我的应用Section
 */
export function AppsSection(): React.ReactElement {
  const { toast } = useToast()

  // 状态管理
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [apps, setApps] = React.useState<Project[]>([])
  const [searchQuery, setSearchQuery] = React.useState("")
  const [deleteDialogOpen, setDeleteDialogOpen] = React.useState(false)
  const [selectedApp, setSelectedApp] = React.useState<Project | null>(null)
  const [isDeleting, setIsDeleting] = React.useState(false)

  /**
   * 加载应用列表
   */
  const loadApps = React.useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const data = await listProjects({
        keyword: searchQuery,
        current: 1,
        size: 100, // 加载更多数据
      })

      setApps(data.records)
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "加载失败"
      setError(errorMessage)
      toast({
        title: "加载失败",
        description: errorMessage,
        variant: "destructive",
      })
    } finally {
      setLoading(false)
    }
  }, [searchQuery, toast])

  /**
   * 初始化加载
   */
  React.useEffect(() => {
    loadApps()
  }, [loadApps])

  /**
   * 筛选应用
   */
  const filteredApps = React.useMemo(() => {
    if (!searchQuery.trim()) return apps

    const query = searchQuery.toLowerCase()
    return apps.filter((app) =>
      app.name.toLowerCase().includes(query) ||
      app.description.toLowerCase().includes(query)
    )
  }, [apps, searchQuery])

  /**
   * 删除应用
   */
  const handleDelete = async () => {
    if (!selectedApp) return

    setIsDeleting(true)
    try {
      // 乐观UI更新：先从列表中移除
      setApps(apps.filter((app) => app.id !== selectedApp.id))
      setDeleteDialogOpen(false)
      setSelectedApp(null)

      // 调用API删除
      await deleteProject(selectedApp.id)

      toast({
        title: "删除成功",
        description: `应用 "${selectedApp.name}" 已删除`,
      })
    } catch (err) {
      // 删除失败，回滚UI
      await loadApps()

      const errorMessage = err instanceof Error ? err.message : "删除失败"
      toast({
        title: "删除失败",
        description: errorMessage,
        variant: "destructive",
      })
    } finally {
      setIsDeleting(false)
    }
  }

  /**
   * 获取状态标签
   */
  const getStatusBadge = (status: ProjectStatus) => {
    const statusConfig: Record<ProjectStatus, { variant: "default" | "secondary" | "destructive" | "outline"; text: string }> = {
      [ProjectStatus.DRAFT]: { variant: "outline", text: "草稿" },
      [ProjectStatus.GENERATING]: { variant: "default", text: "生成中" },
      [ProjectStatus.COMPLETED]: { variant: "default", text: "生成完成" },
      [ProjectStatus.ARCHIVED]: { variant: "secondary", text: "已归档" },
    }
    const config = statusConfig[status]
    return <Badge variant={config.variant}>{config.text}</Badge>
  }

  /**
   * 获取平台图标
   */
  const getPlatformIcon = () => {
    return <Smartphone className="h-4 w-4" />
  }

  /**
   * 格式化日期
   */
  const formatDate = (dateString: string) => {
    const date = new Date(dateString)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    })
  }

  // 加载中状态
  if (loading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Skeleton className="h-10 flex-1 max-w-md" />
          <Skeleton className="h-10 w-32" />
        </div>
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {[...Array(6)].map((_, i) => (
            <Skeleton key={i} className="h-64 rounded-lg" />
          ))}
        </div>
      </div>
    )
  }

  // 错误状态
  if (error) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertDescription>
          {error}
          <Button
            variant="link"
            className="ml-2 h-auto p-0"
            onClick={loadApps}
          >
            重试
          </Button>
        </AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="space-y-8">
      {/* 顶部操作栏 */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        {/* 搜索框 */}
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="搜索应用名称或描述..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="pl-11 h-12 rounded-xl bg-white/50 dark:bg-[#1C1C1E] border-transparent shadow-sm focus:bg-background focus:border-primary/50 transition-all"
          />
        </div>

        {/* 创建新应用按钮 */}
        <Button asChild className="h-12 rounded-full px-6 shadow-sm hover:shadow-md transition-all">
          <Link href="/">
            <Plus className="mr-2 h-4 w-4" />
            创建新应用
          </Link>
        </Button>
      </div>

      {/* 应用列表 */}
      {filteredApps.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 rounded-[32px] bg-white/50 dark:bg-[#1C1C1E]/50 border border-dashed border-muted-foreground/20 text-center">
          <div className="w-16 h-16 rounded-full bg-muted/30 flex items-center justify-center mb-4">
            <Smartphone className="h-8 w-8 text-muted-foreground/50" />
          </div>
          <h3 className="text-lg font-medium text-foreground">
            {searchQuery ? "未找到匹配的应用" : "暂无应用"}
          </h3>
          <p className="text-sm text-muted-foreground mt-1 max-w-xs mx-auto">
            {searchQuery ? "请尝试更换关键词搜索" : "您还没有创建任何应用，立即开始您的第一个创意吧"}
          </p>
          {!searchQuery && (
            <Button asChild className="mt-6 rounded-full">
              <Link href="/">
                <Plus className="mr-2 h-4 w-4" />
                立即创建
              </Link>
            </Button>
          )}
        </div>
      ) : (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {filteredApps.map((app) => (
            <div 
              key={app.id} 
              className="group flex flex-col rounded-[24px] bg-white dark:bg-[#1C1C1E] p-6 shadow-sm border border-black/5 dark:border-white/5 transition-all duration-300 hover:shadow-md hover:-translate-y-1"
            >
              <div className="flex items-start justify-between gap-3 mb-4">
                <div className="flex-1 min-w-0">
                  <h3 className="font-semibold text-lg truncate text-foreground group-hover:text-primary transition-colors">
                    {app.name}
                  </h3>
                  <p className="text-sm text-muted-foreground mt-1 line-clamp-2 h-10 leading-relaxed">
                    {app.description || "暂无描述"}
                  </p>
                </div>
                {getStatusBadge(app.status)}
              </div>

              <div className="flex-1">
                <div className="space-y-3 text-xs font-medium text-muted-foreground/80 bg-muted/30 rounded-xl p-4">
                  {/* 平台 */}
                  <div className="flex items-center gap-2">
                    {getPlatformIcon()}
                    <span>Kotlin Multiplatform</span>
                  </div>

                  {/* 创建时间 */}
                  <div className="flex items-center gap-2">
                    <Calendar className="h-3.5 w-3.5" />
                    <span>{formatDate(app.createdAt)}</span>
                  </div>

                  {/* 统计信息 */}
                  <div className="flex items-center gap-3 pt-1 border-t border-border/50">
                    <span className="flex items-center gap-1">
                      <Eye className="h-3 w-3" /> {app.viewCount}
                    </span>
                    <span className="flex items-center gap-1">
                      <Smartphone className="h-3 w-3" /> {app.forkCount}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2 mt-6 pt-4 border-t border-border/40">
                {/* 查看详情 */}
                <Button variant="outline" size="sm" asChild className="flex-1 rounded-full h-9 text-xs border-border/50 hover:bg-primary/5 hover:text-primary hover:border-primary/20">
                  <Link href={`/wizard/${app.appSpecId || app.id}`}>
                    查看结果
                  </Link>
                </Button>

                {/* 编辑 */}
                <Button variant="ghost" size="icon" asChild className="rounded-full h-9 w-9 hover:bg-muted/80">
                  <Link href={`/wizard/${app.id}`}>
                    <Edit className="h-4 w-4 text-muted-foreground" />
                  </Link>
                </Button>

                {/* 删除 */}
                <Button
                  variant="ghost"
                  size="icon"
                  className="rounded-full h-9 w-9 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20"
                  onClick={() => {
                    setSelectedApp(app)
                    setDeleteDialogOpen(true)
                  }}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 删除确认对话框 */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>删除应用</DialogTitle>
            <DialogDescription>
              确定要删除应用 <strong>{selectedApp?.name}</strong> 吗？
              此操作无法撤销。
            </DialogDescription>
          </DialogHeader>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setDeleteDialogOpen(false)
                setSelectedApp(null)
              }}
              disabled={isDeleting}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={isDeleting}
            >
              {isDeleting ? "删除中..." : "确认删除"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
