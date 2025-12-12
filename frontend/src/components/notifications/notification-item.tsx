/**
 * 通知项组件
 * 单个通知的展示和操作
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Bell,
  MessageSquare,
  Heart,
  GitFork,
  Package,
  AtSign,
  MoreVertical,
  Check,
  Trash2,
  ExternalLink,
} from "lucide-react"
import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import type { Notification, NotificationType } from "@/types/notification"
import { markAsRead, deleteNotification } from "@/lib/api/notifications"
import Link from "next/link"

/**
 * 通知项Props
 */
interface NotificationItemProps {
  /** 通知数据 */
  notification: Notification;
  /** 刷新回调 */
  onRefresh: () => void;
}

/**
 * 通知项组件
 */
export function NotificationItem({ notification, onRefresh }: NotificationItemProps): React.ReactElement {
  /**
   * 获取通知类型图标
   */
  const getTypeIcon = (type: NotificationType) => {
    const iconClass = "h-5 w-5"
    switch (type) {
      case 'system':
        return <Bell className={iconClass} />
      case 'comment':
        return <MessageSquare className={iconClass} />
      case 'like':
        return <Heart className={iconClass} />
      case 'fork':
        return <GitFork className={iconClass} />
      case 'build':
        return <Package className={iconClass} />
      case 'mention':
        return <AtSign className={iconClass} />
      default:
        return <Bell className={iconClass} />
    }
  }

  /**
   * 获取通知类型颜色
   */
  const getTypeColor = (type: NotificationType) => {
    switch (type) {
      case 'system':
        return 'text-blue-600 bg-blue-50'
      case 'comment':
        return 'text-green-600 bg-green-50'
      case 'like':
        return 'text-pink-600 bg-pink-50'
      case 'fork':
        return 'text-purple-600 bg-purple-50'
      case 'build':
        return 'text-orange-600 bg-orange-50'
      case 'mention':
        return 'text-yellow-600 bg-yellow-50'
      default:
        return 'text-gray-600 bg-gray-50'
    }
  }

  /**
   * 获取通知类型名称
   */
  const getTypeName = (type: NotificationType) => {
    switch (type) {
      case 'system':
        return '系统'
      case 'comment':
        return '评论'
      case 'like':
        return '点赞'
      case 'fork':
        return '派生'
      case 'build':
        return '构建'
      case 'mention':
        return '提及'
      default:
        return '通知'
    }
  }

  /**
   * 标记为已读
   */
  const handleMarkAsRead = async () => {
    try {
      await markAsRead(notification.id)
      onRefresh()
    } catch (error) {
      console.error("标记已读失败:", error)
    }
  }

  /**
   * 删除通知
   */
  const handleDelete = async () => {
    if (!confirm("确定要删除此通知吗？")) {
      return
    }

    try {
      await deleteNotification(notification.id)
      onRefresh()
    } catch (error) {
      console.error("删除通知失败:", error)
      alert("删除失败，请重试")
    }
  }

  /**
   * 获取用户名首字母
   */
  const getInitials = (name: string): string => {
    return name.split(' ').map(n => n.charAt(0)).join('').toUpperCase().slice(0, 2)
  }

  /**
   * 格式化相对时间
   */
  const formatRelativeTime = (dateString: string): string => {
    try {
      return formatDistanceToNow(new Date(dateString), {
        addSuffix: true,
        locale: zhCN,
      })
    } catch {
      return '刚刚'
    }
  }

  const contentElement = (
    <div className="flex gap-4 pl-4">
      {/* 图标或头像 */}
      <div className={`flex-shrink-0 h-10 w-10 rounded-full flex items-center justify-center ${getTypeColor(notification.type)}`}>
        {notification.sender ? (
          <Avatar className="h-10 w-10">
            <AvatarImage src={notification.sender.avatar} alt={notification.sender.name} />
            <AvatarFallback>{getInitials(notification.sender.name)}</AvatarFallback>
          </Avatar>
        ) : (
          getTypeIcon(notification.type)
        )}
      </div>

      {/* 内容 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <div className="flex-1">
            <div className="flex items-center gap-2 mb-1">
              <Badge variant="secondary" className="text-xs">
                {getTypeName(notification.type)}
              </Badge>
              <p className="font-medium text-sm">{notification.title}</p>
            </div>
            <p className="text-sm text-muted-foreground line-clamp-2">
              {notification.content}
            </p>
            <div className="flex items-center gap-2 mt-2">
              {notification.sender && (
                <span className="text-xs text-muted-foreground">
                  来自 {notification.sender.name}
                </span>
              )}
              <span className="text-xs text-muted-foreground">
                {formatRelativeTime(notification.createdAt)}
              </span>
            </div>
          </div>

          {/* 操作菜单 */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8">
                <MoreVertical className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {!notification.isRead && (
                <DropdownMenuItem onClick={handleMarkAsRead}>
                  <Check className="mr-2 h-4 w-4" />
                  标记为已读
                </DropdownMenuItem>
              )}
              {notification.linkUrl && (
                <DropdownMenuItem asChild>
                  <a href={notification.linkUrl} target="_blank" rel="noopener noreferrer">
                    <ExternalLink className="mr-2 h-4 w-4" />
                    查看详情
                  </a>
                </DropdownMenuItem>
              )}
              <DropdownMenuItem
                onClick={handleDelete}
                className="text-destructive"
                data-testid="delete-notification-button"
              >
                <Trash2 className="mr-2 h-4 w-4" />
                删除
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </div>
  )

  return (
    <div
      data-testid="notification-item"
      className={`relative p-4 rounded-lg border transition-colors ${
        notification.isRead
          ? 'bg-background border-border'
          : 'bg-accent/50 border-accent-foreground/20 unread'
      } hover:bg-accent`}
    >
      {/* 未读标记 */}
      {!notification.isRead && (
        <div className="absolute left-2 top-1/2 -translate-y-1/2 h-2 w-2 rounded-full bg-primary" />
      )}

      {notification.linkUrl ? (
        <Link href={notification.linkUrl} className="block">
          {contentElement}
        </Link>
      ) : (
        contentElement
      )}
    </div>
  )
}
