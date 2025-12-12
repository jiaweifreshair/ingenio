/**
 * 通知筛选栏组件
 * 分类筛选和操作按钮
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import {
  Bell,
  Mail,
  MailOpen,
  MessageSquare,
  Heart,
  GitFork,
  Package,
  AtSign,
  Settings,
  CheckCheck,
} from "lucide-react"
import type { NotificationType, NotificationFilters } from "@/types/notification"

/**
 * 通知筛选栏Props
 */
interface NotificationFilterProps {
  /** 当前筛选条件 */
  filters: NotificationFilters;
  /** 未读数量 */
  unreadCount: number;
  /** 筛选变更回调 */
  onFiltersChange: (filters: NotificationFilters) => void;
  /** 全部标记已读回调 */
  onMarkAllAsRead: () => void;
  /** 打开设置回调 */
  onOpenSettings: () => void;
}

/**
 * 筛选选项类型
 */
interface FilterOption {
  key: string;
  label: string;
  icon: React.ReactNode;
  type?: NotificationType;
  unreadOnly?: boolean;
}

/**
 * 通知筛选栏组件
 */
export function NotificationFilter({
  filters,
  unreadCount,
  onFiltersChange,
  onMarkAllAsRead,
  onOpenSettings,
}: NotificationFilterProps): React.ReactElement {
  /**
   * 筛选选项列表
   */
  const filterOptions: FilterOption[] = [
    {
      key: 'all',
      label: '全部通知',
      icon: <Bell className="h-4 w-4" />,
    },
    {
      key: 'unread',
      label: '未读',
      icon: <Mail className="h-4 w-4" />,
      unreadOnly: true,
    },
    {
      key: 'read',
      label: '已读',
      icon: <MailOpen className="h-4 w-4" />,
    },
  ]

  /**
   * 类型筛选选项
   */
  const typeOptions: FilterOption[] = [
    {
      key: 'system',
      label: '系统',
      icon: <Bell className="h-4 w-4" />,
      type: 'system' as NotificationType,
    },
    {
      key: 'comment',
      label: '评论',
      icon: <MessageSquare className="h-4 w-4" />,
      type: 'comment' as NotificationType,
    },
    {
      key: 'like',
      label: '点赞',
      icon: <Heart className="h-4 w-4" />,
      type: 'like' as NotificationType,
    },
    {
      key: 'fork',
      label: '派生',
      icon: <GitFork className="h-4 w-4" />,
      type: 'fork' as NotificationType,
    },
    {
      key: 'build',
      label: '构建',
      icon: <Package className="h-4 w-4" />,
      type: 'build' as NotificationType,
    },
    {
      key: 'mention',
      label: '提及',
      icon: <AtSign className="h-4 w-4" />,
      type: 'mention' as NotificationType,
    },
  ]

  /**
   * 获取当前选中的筛选选项
   */
  const getSelectedKey = (): string => {
    if (filters.unreadOnly) {
      return 'unread'
    }
    if (filters.type) {
      return filters.type
    }
    return 'all'
  }

  /**
   * 处理筛选选项点击
   */
  const handleFilterClick = (option: FilterOption) => {
    const newFilters: NotificationFilters = {
      current: 1, // 重置页码
    }

    if (option.unreadOnly) {
      newFilters.unreadOnly = true
    } else if (option.type) {
      newFilters.type = option.type
    }

    onFiltersChange(newFilters)
  }

  const selectedKey = getSelectedKey()

  return (
    <div className="w-64 border-r bg-card h-full flex flex-col">
      {/* 顶部操作栏 */}
      <div className="p-4 space-y-2">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Bell className="h-5 w-5" />
            通知中心
          </h2>
          <Button variant="ghost" size="icon" onClick={onOpenSettings}>
            <Settings className="h-4 w-4" />
          </Button>
        </div>

        {/* 未读数量 */}
        {unreadCount > 0 && (
          <div className="flex items-center justify-between p-3 rounded-lg bg-accent">
            <div className="flex items-center gap-2">
              <Mail className="h-4 w-4" />
              <span className="text-sm font-medium" data-testid="unread-badge">
                {unreadCount} 条未读
              </span>
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={onMarkAllAsRead}
              className="h-8 text-xs"
              data-testid="mark-all-read-button"
            >
              <CheckCheck className="mr-1 h-3 w-3" />
              全部已读
            </Button>
          </div>
        )}
      </div>

      <Separator />

      {/* 筛选选项 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* 基础筛选 */}
        <div className="space-y-1">
          <h3 className="text-xs font-medium text-muted-foreground mb-2 px-2">筛选</h3>
          {filterOptions.map((option) => (
            <Button
              key={option.key}
              variant={selectedKey === option.key ? 'secondary' : 'ghost'}
              className="w-full justify-start"
              onClick={() => handleFilterClick(option)}
            >
              {option.icon}
              <span className="ml-2">{option.label}</span>
              {option.key === 'unread' && unreadCount > 0 && (
                <Badge variant="destructive" className="ml-auto">
                  {unreadCount}
                </Badge>
              )}
            </Button>
          ))}
        </div>

        <Separator />

        {/* 类型筛选 */}
        <div className="space-y-1">
          <h3 className="text-xs font-medium text-muted-foreground mb-2 px-2">按类型</h3>
          {typeOptions.map((option) => (
            <Button
              key={option.key}
              variant={selectedKey === option.key ? 'secondary' : 'ghost'}
              className="w-full justify-start"
              onClick={() => handleFilterClick(option)}
            >
              {option.icon}
              <span className="ml-2">{option.label}</span>
            </Button>
          ))}
        </div>
      </div>
    </div>
  )
}
