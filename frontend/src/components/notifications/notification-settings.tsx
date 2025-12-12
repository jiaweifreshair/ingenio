/**
 * 通知设置Dialog
 * 配置通知偏好
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Save } from "lucide-react"
import type { NotificationSettings } from "@/types/notification"
import { updateNotificationSettings } from "@/lib/api/notifications"

/**
 * 通知设置Props
 */
interface NotificationSettingsDialogProps {
  /** 是否打开 */
  open: boolean;
  /** 关闭回调 */
  onOpenChange: (open: boolean) => void;
  /** 当前设置 */
  settings: NotificationSettings;
  /** 刷新回调 */
  onRefresh: () => void;
}

/**
 * 通知设置Dialog
 */
export function NotificationSettingsDialog({
  open,
  onOpenChange,
  settings,
  onRefresh,
}: NotificationSettingsDialogProps): React.ReactElement {
  const [isSaving, setIsSaving] = React.useState(false)
  const [formData, setFormData] = React.useState(settings)

  /**
   * 当外部设置更新时同步
   */
  React.useEffect(() => {
    setFormData(settings)
  }, [settings])

  /**
   * 保存设置
   */
  const handleSave = async () => {
    setIsSaving(true)
    try {
      await updateNotificationSettings(formData)
      alert("通知设置已保存")
      onRefresh()
      onOpenChange(false)
    } catch (error) {
      console.error("保存失败:", error)
      alert("保存失败，请重试")
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>通知设置</DialogTitle>
          <DialogDescription>
            管理您的通知偏好设置
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6 py-4">
          {/* 通知渠道 */}
          <div className="space-y-4">
            <h3 className="text-sm font-medium">通知渠道</h3>

            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <Label>邮件通知</Label>
                <p className="text-sm text-muted-foreground">
                  通过邮件接收通知
                </p>
              </div>
              <Switch
                checked={formData.emailEnabled}
                onCheckedChange={(checked) =>
                  setFormData({ ...formData, emailEnabled: checked })
                }
              />
            </div>

            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <Label>推送通知</Label>
                <p className="text-sm text-muted-foreground">
                  通过浏览器推送接收通知
                </p>
              </div>
              <Switch
                checked={formData.pushEnabled}
                onCheckedChange={(checked) =>
                  setFormData({ ...formData, pushEnabled: checked })
                }
              />
            </div>
          </div>

          <Separator />

          {/* 通知频率 */}
          <div className="space-y-2">
            <Label>通知频率</Label>
            <Select
              value={formData.frequency}
              onValueChange={(value: 'realtime' | 'daily') =>
                setFormData({ ...formData, frequency: value })
              }
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="realtime">实时通知</SelectItem>
                <SelectItem value="daily">每日汇总</SelectItem>
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">
              选择每日汇总将在每天早上发送一次通知摘要
            </p>
          </div>

          <Separator />

          {/* 通知类型订阅 */}
          <div className="space-y-4">
            <h3 className="text-sm font-medium">通知类型</h3>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label>系统通知</Label>
                <Switch
                  checked={formData.systemNotifications}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, systemNotifications: checked })
                  }
                />
              </div>

              <div className="flex items-center justify-between">
                <Label>评论通知</Label>
                <Switch
                  checked={formData.commentNotifications}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, commentNotifications: checked })
                  }
                />
              </div>

              <div className="flex items-center justify-between">
                <Label>点赞通知</Label>
                <Switch
                  checked={formData.likeNotifications}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, likeNotifications: checked })
                  }
                />
              </div>

              <div className="flex items-center justify-between">
                <Label>派生通知</Label>
                <Switch
                  checked={formData.forkNotifications}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, forkNotifications: checked })
                  }
                />
              </div>

              <div className="flex items-center justify-between">
                <Label>构建通知</Label>
                <Switch
                  checked={formData.buildNotifications}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, buildNotifications: checked })
                  }
                />
              </div>

              <div className="flex items-center justify-between">
                <Label>@提醒通知</Label>
                <Switch
                  checked={formData.mentionNotifications}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, mentionNotifications: checked })
                  }
                />
              </div>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSave} disabled={isSaving}>
            <Save className="mr-2 h-4 w-4" />
            {isSaving ? "保存中..." : "保存设置"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
