'use client';

import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type { Project } from '@/types/project';
import { ProjectStatus } from '@/types/project';
import {
  MoreVertical,
  Eye,
  Edit,
  Copy,
  Trash2,
  Clock,
  ExternalLink,
  Settings,
  History,
  Palette,
  Rocket,
  Share2,
} from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { zhCN } from 'date-fns/locale/zh-CN';
import Image from 'next/image';
import { useRouter } from 'next/navigation';
import { useCallback } from 'react';

/**
 * 应用卡片组件
 * 展示单个应用的信息和操作
 *
 * V2.0增强功能：
 * - 新增"应用设置"菜单项，导航到 /settings/[projectId]
 * - 新增"版本历史"菜单项，导航到 /versions/[appId]
 * - 新增"SuperDesign方案"菜单项，导航到 /superdesign/[appId]
 * - 新增"发布应用"菜单项，导航到 /publish/[id]
 * - 新增"分享应用"功能（暂时保留，待后续实现）
 * - 优化菜单结构和视觉层次
 */
interface AppCardProps {
  project: Project;
  onView: (id: string) => void;
  onEdit: (id: string) => void;
  onCopy: (id: string) => void;
  onDelete: (id: string) => void;
}

export function AppCard({
  project,
  onView,
  onEdit,
  onCopy,
  onDelete,
}: AppCardProps): React.ReactElement {
  const router = useRouter();

  /**
   * 获取状态标签样式
   */
  const getStatusBadge = (status: string) => {
    switch (status) {
      case ProjectStatus.PUBLISHED:
        return (
          <Badge variant="default" className="bg-green-500">
            已发布
          </Badge>
        );
      case ProjectStatus.DRAFT:
        return <Badge variant="secondary">草稿</Badge>;
      case ProjectStatus.ARCHIVED:
        return <Badge variant="outline">已归档</Badge>;
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
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
   * 处理应用设置导航
   * 导航到项目设置页面
   */
  const handleSettings = useCallback(() => {
    router.push(`/settings/${project.id}`);
  }, [router, project.id]);

  /**
   * 处理版本历史导航
   * 导航到版本历史页面（使用appSpecId）
   */
  const handleVersionHistory = useCallback(() => {
    const targetId = project.appSpecId || project.id;
    router.push(`/versions/${targetId}`);
  }, [router, project.appSpecId, project.id]);

  /**
   * 处理SuperDesign方案导航
   * 导航到SuperDesign页面（使用appSpecId）
   */
  const handleSuperDesign = useCallback(() => {
    const targetId = project.appSpecId || project.id;
    router.push(`/superdesign/${targetId}`);
  }, [router, project.appSpecId, project.id]);

  /**
   * 处理发布应用导航
   * 导航到发布配置页面
   */
  const handlePublish = useCallback(() => {
    router.push(`/publish/${project.id}`);
  }, [router, project.id]);

  /**
   * 处理分享应用
   * TODO: 实现分享弹窗功能
   */
  const handleShare = useCallback(() => {
    // 临时实现：复制分享链接到剪贴板
    const shareUrl = `${window.location.origin}/wizard/${project.appSpecId || project.id}`;
    navigator.clipboard.writeText(shareUrl).then(() => {
      alert('分享链接已复制到剪贴板');
    }).catch(() => {
      alert('复制失败，请手动复制链接');
    });
  }, [project.appSpecId, project.id]);

  /**
   * 处理执行历史导航
   * 导航到执行历史回放页面
   */
  const handleExecutionHistory = useCallback(() => {
    router.push(`/dashboard/${project.id}/history`);
  }, [router, project.id]);

  return (
    <Card className="group relative overflow-hidden transition-all hover:shadow-xl">
      {/* 封面图片 */}
      <div className="relative h-48 w-full overflow-hidden bg-gradient-to-br from-blue-50 to-indigo-100">
        {project.coverImageUrl ? (
          <Image
            src={project.coverImageUrl}
            alt={project.name}
            fill
            className="object-cover transition-transform group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full items-center justify-center">
            <span className="text-4xl font-bold text-gray-300">
              {project.name.charAt(0).toUpperCase()}
            </span>
          </div>
        )}

        {/* 状态标签 */}
        <div className="absolute top-3 left-3">{getStatusBadge(project.status)}</div>

        {/* 操作菜单 */}
        <div className="absolute top-3 right-3">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                size="icon"
                variant="secondary"
                className="h-8 w-8 rounded-full bg-white/90 hover:bg-white"
              >
                <MoreVertical className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-48">
              {/* 主要操作 */}
              <DropdownMenuItem onClick={() => onEdit(project.id)}>
                <Edit className="mr-2 h-4 w-4" />
                继续编辑
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onView(project.appSpecId || project.id)}>
                <Eye className="mr-2 h-4 w-4" />
                查看结果
              </DropdownMenuItem>

              {/* 新增菜单项 - 解决孤岛页面问题 */}
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={handleSettings}>
                <Settings className="mr-2 h-4 w-4" />
                应用设置
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleVersionHistory}>
                <History className="mr-2 h-4 w-4" />
                版本历史
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleExecutionHistory}>
                <Clock className="mr-2 h-4 w-4" />
                执行历史
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleSuperDesign}>
                <Palette className="mr-2 h-4 w-4" />
                SuperDesign方案
              </DropdownMenuItem>

              {/* 操作菜单 */}
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={handlePublish}>
                <Rocket className="mr-2 h-4 w-4" />
                发布应用
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onCopy(project.id)}>
                <Copy className="mr-2 h-4 w-4" />
                复制应用
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleShare}>
                <Share2 className="mr-2 h-4 w-4" />
                分享应用
              </DropdownMenuItem>

              {/* 危险操作 */}
              <DropdownMenuSeparator />
              <DropdownMenuItem
                onClick={() => onDelete(project.id)}
                className="text-red-600 focus:text-red-600"
              >
                <Trash2 className="mr-2 h-4 w-4" />
                删除应用
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* 卡片内容 */}
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between">
          <div className="flex-1">
            <h3 className="font-semibold text-lg line-clamp-1 group-hover:text-primary transition-colors">
              {project.name}
            </h3>
            <p className="text-sm text-muted-foreground line-clamp-2 mt-1">
              {project.description || '暂无描述'}
            </p>
          </div>
        </div>
      </CardHeader>

      <CardContent className="pb-3">
        {/* 标签 */}
        {project.tags && project.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {project.tags.slice(0, 3).map((tag, index) => (
              <Badge key={index} variant="outline" className="text-xs">
                {tag}
              </Badge>
            ))}
            {project.tags.length > 3 && (
              <Badge variant="outline" className="text-xs">
                +{project.tags.length - 3}
              </Badge>
            )}
          </div>
        )}
      </CardContent>

      <CardFooter className="flex items-center justify-between text-xs text-muted-foreground border-t pt-3">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-1">
            <Eye className="h-3 w-3" />
            <span>{project.viewCount}</span>
          </div>
          <div className="flex items-center gap-1">
            <Clock className="h-3 w-3" />
            <span>{formatTime(project.updatedAt)}</span>
          </div>
        </div>

        <Button
          size="sm"
          variant="ghost"
          className="h-7 text-xs"
          onClick={() => onView(project.appSpecId || project.id)}
        >
          查看
          <ExternalLink className="ml-1 h-3 w-3" />
        </Button>
      </CardFooter>
    </Card>
  );
}
