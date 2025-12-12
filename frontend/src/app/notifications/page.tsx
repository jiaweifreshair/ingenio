/**
 * 通知中心页面
 * 展示通知列表、筛选和设置
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import { NotificationFilter } from "@/components/notifications/notification-filter"
import { NotificationList } from "@/components/notifications/notification-list"
import { NotificationSettingsDialog } from "@/components/notifications/notification-settings"
import type { Notification, NotificationFilters, NotificationSettings } from "@/types/notification"
import type { PageResult } from "@/types/project"
import {
  listNotifications,
  getUnreadCount,
  markAllAsRead,
  getNotificationSettings,
} from "@/lib/api/notifications"

/**
 * 通知中心页面
 */
export default function NotificationsPage(): React.ReactElement {
  const [notifications, setNotifications] = React.useState<PageResult<Notification>>({
    records: [],
    total: 0,
    size: 20,
    current: 1,
    pages: 0,
    hasNext: false,
    hasPrevious: false,
  })
  const [unreadCount, setUnreadCount] = React.useState(0)
  const [isLoading, setIsLoading] = React.useState(false)
  const [filters, setFilters] = React.useState<NotificationFilters>({
    current: 1,
    size: 20,
  })
  const [settingsOpen, setSettingsOpen] = React.useState(false)
  const [settings, setSettings] = React.useState<NotificationSettings>({
    emailEnabled: true,
    pushEnabled: true,
    frequency: 'realtime',
    systemNotifications: true,
    commentNotifications: true,
    likeNotifications: true,
    forkNotifications: true,
    buildNotifications: true,
    mentionNotifications: true,
  })

  /**
   * 加载通知列表
   */
  const loadNotifications = React.useCallback(async (loadMore = false) => {
    setIsLoading(true)
    try {
      const data = await listNotifications(filters)
      if (loadMore) {
        // 加载更多时追加数据
        setNotifications((prev) => ({
          ...data,
          records: [...prev.records, ...data.records],
        }))
      } else {
        // 首次加载或刷新时替换数据
        setNotifications(data)
      }
    } catch (error) {
      console.error("加载通知失败:", error)
    } finally {
      setIsLoading(false)
    }
  }, [filters])

  /**
   * 加载未读数量
   */
  const loadUnreadCount = React.useCallback(async () => {
    try {
      const count = await getUnreadCount()
      setUnreadCount(count)
    } catch (error) {
      console.error("加载未读数量失败:", error)
    }
  }, [])

  /**
   * 加载通知设置
   */
  const loadSettings = React.useCallback(async () => {
    try {
      const data = await getNotificationSettings()
      setSettings(data)
    } catch (error) {
      console.error("加载通知设置失败:", error)
    }
  }, [])

  /**
   * 初始化加载
   */
  React.useEffect(() => {
    const init = async () => {
      await Promise.all([
        loadNotifications(),
        loadUnreadCount(),
        loadSettings(),
      ])
    }
    init()
  }, [loadNotifications, loadUnreadCount, loadSettings])

  /**
   * 筛选条件变更时重新加载
   */
  React.useEffect(() => {
    loadNotifications()
  }, [loadNotifications])

  /**
   * 处理筛选条件变更
   */
  const handleFiltersChange = (newFilters: NotificationFilters) => {
    setFilters({
      ...newFilters,
      size: filters.size,
    })
  }

  /**
   * 加载更多
   */
  const handleLoadMore = () => {
    setFilters((prev) => ({
      ...prev,
      current: (prev.current || 1) + 1,
    }))
    loadNotifications(true)
  }

  /**
   * 全部标记已读
   */
  const handleMarkAllAsRead = async () => {
    try {
      await markAllAsRead()
      await loadNotifications()
      await loadUnreadCount()
    } catch (error) {
      console.error("标记全部已读失败:", error)
      alert("操作失败，请重试")
    }
  }

  /**
   * 刷新数据
   */
  const handleRefresh = async () => {
    await Promise.all([
      loadNotifications(),
      loadUnreadCount(),
    ])
  }

  /**
   * 打开设置
   */
  const handleOpenSettings = () => {
    setSettingsOpen(true)
  }

  /**
   * 实时更新（轮询）
   */
  React.useEffect(() => {
    // 每30秒轮询一次未读数量
    const interval = setInterval(() => {
      loadUnreadCount()
    }, 30000)

    return () => clearInterval(interval)
  }, [loadUnreadCount])

  return (
    <div className="h-screen flex">
      {/* 左侧筛选栏 */}
      <NotificationFilter
        filters={filters}
        unreadCount={unreadCount}
        onFiltersChange={handleFiltersChange}
        onMarkAllAsRead={handleMarkAllAsRead}
        onOpenSettings={handleOpenSettings}
      />

      {/* 右侧通知列表 */}
      <div className="flex-1 overflow-y-auto">
        <div className="container max-w-4xl py-8">
          <NotificationList
            notifications={notifications}
            isLoading={isLoading}
            filters={filters}
            onRefresh={handleRefresh}
            onLoadMore={handleLoadMore}
          />
        </div>
      </div>

      {/* 通知设置Dialog */}
      <NotificationSettingsDialog
        open={settingsOpen}
        onOpenChange={setSettingsOpen}
        settings={settings}
        onRefresh={loadSettings}
      />
    </div>
  )
}
