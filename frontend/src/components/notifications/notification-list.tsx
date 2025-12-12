/**
 * 通知列表组件
 * 展示通知列表和分页
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import { Button } from "@/components/ui/button"
import { Bell, CheckCheck, Loader2 } from "lucide-react"
import { NotificationItem } from "./notification-item"
import type { Notification, NotificationFilters } from "@/types/notification"
import type { PageResult } from "@/types/project"

/**
 * 通知列表Props
 */
interface NotificationListProps {
  /** 通知列表数据 */
  notifications: PageResult<Notification>;
  /** 加载状态 */
  isLoading: boolean;
  /** 筛选参数 */
  filters: NotificationFilters;
  /** 刷新回调 */
  onRefresh: () => void;
  /** 加载更多回调 */
  onLoadMore: () => void;
}

/**
 * 通知列表组件
 */
export function NotificationList({
  notifications,
  isLoading,
  filters,
  onRefresh,
  onLoadMore,
}: NotificationListProps): React.ReactElement {
  /**
   * 渲染加载状态
   */
  if (isLoading && notifications.records.length === 0) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  /**
   * 渲染空状态
   */
  if (notifications.records.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center">
        {filters.unreadOnly ? (
          <>
            <CheckCheck className="h-16 w-16 text-muted-foreground mb-4 opacity-50" />
            <h3 className="text-lg font-semibold mb-2">全部已读</h3>
            <p className="text-muted-foreground">
              您暂时没有未读通知
            </p>
          </>
        ) : (
          <>
            <Bell className="h-16 w-16 text-muted-foreground mb-4 opacity-50" />
            <h3 className="text-lg font-semibold mb-2">暂无通知</h3>
            <p className="text-muted-foreground">
              您的通知将显示在这里
            </p>
          </>
        )}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* 通知列表 */}
      <div className="space-y-3">
        {notifications.records.map((notification) => (
          <NotificationItem
            key={notification.id}
            notification={notification}
            onRefresh={onRefresh}
          />
        ))}
      </div>

      {/* 分页按钮 */}
      {notifications.hasNext && (
        <div className="flex justify-center pt-4">
          <Button
            variant="outline"
            onClick={onLoadMore}
            disabled={isLoading}
            data-testid="loading-more"
          >
            {isLoading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                加载中...
              </>
            ) : (
              '加载更多'
            )}
          </Button>
        </div>
      )}

      {/* 分页信息 */}
      <div className="text-center text-sm text-muted-foreground">
        已显示 {notifications.records.length} / {notifications.total} 条通知
      </div>
    </div>
  )
}
