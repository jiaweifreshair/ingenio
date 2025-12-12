/**
 * 安全设置Section
 * 两步验证、登录设备管理、操作日志
 *
 * 功能：
 * - 两步验证设置（真实API）
 * - 登录设备管理（真实API）
 * - 操作日志查看（真实API）
 * - 移除登录设备（真实API）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Skeleton } from "@/components/ui/skeleton"
import { Shield, Smartphone, Monitor, Trash2, Calendar, MapPin, Clock, AlertCircle } from "lucide-react"
import { useToast } from "@/hooks/use-toast"
import {
  getTwoFactorStatus,
  toggleTwoFactor,
  listLoginDevices,
  removeLoginDevice,
  listActivityLogs,
  type LoginDevice,
  type ActivityLog,
} from "@/lib/api/user"

/**
 * 安全设置Section
 */
export function SecuritySection(): React.ReactElement {
  const { toast } = useToast()

  // 状态管理
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [twoFactorEnabled, setTwoFactorEnabled] = React.useState(false)
  const [devices, setDevices] = React.useState<LoginDevice[]>([])
  const [activityLogs, setActivityLogs] = React.useState<ActivityLog[]>([])
  const [deleteDeviceDialogOpen, setDeleteDeviceDialogOpen] = React.useState(false)
  const [selectedDevice, setSelectedDevice] = React.useState<LoginDevice | null>(null)
  const [isDeleting, setIsDeleting] = React.useState(false)
  const [isTogglingTwoFactor, setIsTogglingTwoFactor] = React.useState(false)

  /**
   * 加载所有数据
   */
  const loadData = React.useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      // 并行加载所有数据
      const [twoFactorStatus, devicesData, logsData] = await Promise.all([
        getTwoFactorStatus(),
        listLoginDevices(),
        listActivityLogs({ current: 1, size: 5 }), // 只显示最近5条
      ])

      setTwoFactorEnabled(twoFactorStatus)
      setDevices(devicesData)
      setActivityLogs(logsData.records)
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
  }, [toast])

  /**
   * 初始化加载
   */
  React.useEffect(() => {
    loadData()
  }, [loadData])

  /**
   * 切换两步验证
   */
  const handleToggleTwoFactor = async (enabled: boolean) => {
    setIsTogglingTwoFactor(true)
    try {
      await toggleTwoFactor(enabled)
      setTwoFactorEnabled(enabled)

      toast({
        title: enabled ? "两步验证已启用" : "两步验证已关闭",
        description: enabled ? "登录时将需要验证码" : "已关闭两步验证",
      })
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "操作失败"
      toast({
        title: "操作失败",
        description: errorMessage,
        variant: "destructive",
      })
      // 回滚状态
      setTwoFactorEnabled(!enabled)
    } finally {
      setIsTogglingTwoFactor(false)
    }
  }

  /**
   * 删除登录设备
   */
  const handleDeleteDevice = async () => {
    if (!selectedDevice) return

    setIsDeleting(true)
    try {
      // 乐观UI更新
      setDevices(devices.filter((device) => device.id !== selectedDevice.id))
      setDeleteDeviceDialogOpen(false)
      setSelectedDevice(null)

      // 调用API删除
      await removeLoginDevice(selectedDevice.id)

      toast({
        title: "移除成功",
        description: `设备 "${selectedDevice.deviceName}" 已被移除`,
      })
    } catch (err) {
      // 删除失败，回滚UI
      await loadData()

      const errorMessage = err instanceof Error ? err.message : "删除失败"
      toast({
        title: "移除失败",
        description: errorMessage,
        variant: "destructive",
      })
    } finally {
      setIsDeleting(false)
    }
  }

  /**
   * 获取设备图标
   */
  const getDeviceIcon = (deviceType: LoginDevice["deviceType"]) => {
    switch (deviceType) {
      case "Desktop":
        return <Monitor className="h-5 w-5" />
      case "Mobile":
      case "Tablet":
        return <Smartphone className="h-5 w-5" />
    }
  }

  /**
   * 获取状态标签
   */
  const getStatusBadge = (status: ActivityLog["status"]) => {
    return status === "Success" ? (
      <Badge variant="default">成功</Badge>
    ) : (
      <Badge variant="destructive">失败</Badge>
    )
  }

  /**
   * 格式化时间
   */
  const formatTime = (dateString: string) => {
    const date = new Date(dateString)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  // 加载中状态
  if (loading) {
    return (
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <Skeleton className="h-6 w-32" />
          </CardHeader>
          <CardContent>
            <Skeleton className="h-10 w-full" />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <Skeleton className="h-6 w-32" />
          </CardHeader>
          <CardContent>
            <Skeleton className="h-32 w-full" />
          </CardContent>
        </Card>
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
            onClick={loadData}
          >
            重试
          </Button>
        </AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="space-y-6">
      {/* 两步验证 */}
      <Card>
        <CardHeader>
          <div className="flex items-start justify-between">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Shield className="h-5 w-5" />
                两步验证
              </CardTitle>
              <CardDescription className="mt-2">
                为您的账户添加额外的安全保护层
              </CardDescription>
            </div>
            <Switch
              checked={twoFactorEnabled}
              onCheckedChange={handleToggleTwoFactor}
              disabled={isTogglingTwoFactor}
            />
          </div>
        </CardHeader>

        {twoFactorEnabled && (
          <CardContent>
            <div className="rounded-lg border border-green-200 bg-green-50 p-4 dark:border-green-900 dark:bg-green-950">
              <p className="text-sm text-green-900 dark:text-green-100">
                两步验证已启用。登录时需要输入验证码。
              </p>
            </div>
          </CardContent>
        )}
      </Card>

      {/* 登录设备管理 */}
      <Card>
        <CardHeader>
          <CardTitle>登录设备</CardTitle>
          <CardDescription>
            管理已登录此账户的设备
          </CardDescription>
        </CardHeader>

        <CardContent>
          {devices.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-6">
              当前没有登录设备
            </p>
          ) : (
            <div className="space-y-4">
              {devices.map((device) => (
                <div
                  key={device.id}
                  className="flex items-start justify-between gap-4 rounded-lg border p-4"
                >
                  <div className="flex gap-4">
                    {/* 设备图标 */}
                    <div className="flex h-12 w-12 items-center justify-center rounded-lg bg-muted">
                      {getDeviceIcon(device.deviceType)}
                    </div>

                    {/* 设备信息 */}
                    <div className="flex-1 space-y-1">
                      <div className="flex items-center gap-2">
                        <p className="font-medium">{device.deviceName}</p>
                        {device.isCurrent && (
                          <Badge variant="secondary">当前设备</Badge>
                        )}
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {device.browser} • {device.os}
                      </p>
                      <div className="flex items-center gap-4 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <MapPin className="h-3 w-3" />
                          {device.location}
                        </span>
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3" />
                          {formatTime(device.lastActive)}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* 删除按钮 */}
                  {!device.isCurrent && (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        setSelectedDevice(device)
                        setDeleteDeviceDialogOpen(true)
                      }}
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  )}
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* 操作日志 */}
      <Card>
        <CardHeader>
          <CardTitle>操作日志</CardTitle>
          <CardDescription>
            查看最近的账户操作记录
          </CardDescription>
        </CardHeader>

        <CardContent>
          {activityLogs.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-6">
              暂无操作日志
            </p>
          ) : (
            <div className="space-y-4">
              {activityLogs.map((log) => (
                <div
                  key={log.id}
                  className="flex items-start justify-between gap-4 rounded-lg border p-4"
                >
                  <div className="flex-1 space-y-1">
                    <div className="flex items-center gap-2">
                      <p className="font-medium">{log.action}</p>
                      {getStatusBadge(log.status)}
                    </div>
                    <p className="text-sm text-muted-foreground">
                      {log.description}
                    </p>
                    <div className="flex items-center gap-4 text-xs text-muted-foreground">
                      <span className="flex items-center gap-1">
                        <Calendar className="h-3 w-3" />
                        {formatTime(log.timestamp)}
                      </span>
                      <span>IP: {log.ipAddress}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* 查看更多按钮 */}
          {activityLogs.length > 0 && (
            <div className="mt-4 text-center">
              <Button variant="outline" size="sm">
                查看全部日志
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 删除设备确认对话框 */}
      <Dialog open={deleteDeviceDialogOpen} onOpenChange={setDeleteDeviceDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>移除登录设备</DialogTitle>
            <DialogDescription>
              确定要移除设备 <strong>{selectedDevice?.deviceName}</strong> 吗？
              该设备将被强制退出登录。
            </DialogDescription>
          </DialogHeader>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setDeleteDeviceDialogOpen(false)
                setSelectedDevice(null)
              }}
              disabled={isDeleting}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteDevice}
              disabled={isDeleting}
            >
              {isDeleting ? "移除中..." : "确认移除"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
