/**
 * 高级设置Section
 * 项目归档、删除、转移
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Archive, Trash2, ArrowRightLeft, AlertTriangle } from "lucide-react"
import { deleteProject, archiveProject } from "@/lib/api/projects"
import { transferProject } from "@/lib/api/settings"
import { useRouter } from "next/navigation"

/**
 * 高级设置Props
 */
interface AdvancedSettingsProps {
  /** 项目ID */
  projectId: string;
  /** 项目名称 */
  projectName: string;
  /** 是否已归档 */
  isArchived: boolean;
}

/**
 * 高级设置Section
 */
export function AdvancedSettings({
  projectId,
  projectName,
  isArchived
}: AdvancedSettingsProps): React.ReactElement {
  const router = useRouter()
  const [isDeleting, setIsDeleting] = React.useState(false)
  const [isArchiving, setIsArchiving] = React.useState(false)
  const [isTransferring, setIsTransferring] = React.useState(false)
  const [deleteConfirmation, setDeleteConfirmation] = React.useState('')
  const [transferUserId, setTransferUserId] = React.useState('')

  /**
   * 归档项目
   */
  const handleArchive = async () => {
    setIsArchiving(true)
    try {
      await archiveProject(projectId)
      alert("项目已归档")
      router.push('/dashboard')
    } catch (error) {
      console.error("归档失败:", error)
      alert("归档失败，请重试")
    } finally {
      setIsArchiving(false)
    }
  }

  /**
   * 删除项目
   */
  const handleDelete = async () => {
    if (deleteConfirmation !== projectName) {
      alert("项目名称不匹配")
      return
    }

    setIsDeleting(true)
    try {
      await deleteProject(projectId)
      alert("项目已删除")
      router.push('/dashboard')
    } catch (error) {
      console.error("删除失败:", error)
      alert("删除失败，请重试")
    } finally {
      setIsDeleting(false)
    }
  }

  /**
   * 转移项目
   */
  const handleTransfer = async () => {
    if (!transferUserId.trim()) {
      alert("请输入目标用户ID")
      return
    }

    setIsTransferring(true)
    try {
      await transferProject(projectId, { targetUserId: transferUserId })
      alert("项目已转移")
      router.push('/dashboard')
    } catch (error) {
      console.error("转移失败:", error)
      alert("转移失败，请重试")
    } finally {
      setIsTransferring(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* 归档项目 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Archive className="h-5 w-5" />
            归档项目
          </CardTitle>
          <CardDescription>
            归档后项目将不再显示在项目列表中，但不会删除数据
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Dialog>
            <DialogTrigger asChild>
              <Button variant="outline" disabled={isArchived}>
                <Archive className="mr-2 h-4 w-4" />
                {isArchived ? "已归档" : "归档项目"}
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>确认归档项目</DialogTitle>
                <DialogDescription>
                  归档后项目将不再显示在项目列表中。您可以随时从归档中恢复项目。
                </DialogDescription>
              </DialogHeader>
              <Alert>
                <AlertTriangle className="h-4 w-4" />
                <AlertTitle>注意</AlertTitle>
                <AlertDescription>
                  归档不会删除项目数据，只是将项目移出活跃列表。
                </AlertDescription>
              </Alert>
              <DialogFooter>
                <Button variant="outline" onClick={() => {}}>
                  取消
                </Button>
                <Button onClick={handleArchive} disabled={isArchiving}>
                  {isArchiving ? "归档中..." : "确认归档"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </CardContent>
      </Card>

      {/* 转移项目 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ArrowRightLeft className="h-5 w-5" />
            转移项目
          </CardTitle>
          <CardDescription>
            将项目所有权转移给其他用户
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Dialog>
            <DialogTrigger asChild>
              <Button variant="outline">
                <ArrowRightLeft className="mr-2 h-4 w-4" />
                转移项目
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>转移项目</DialogTitle>
                <DialogDescription>
                  将项目所有权转移给其他用户。转移后您将失去对项目的所有权。
                </DialogDescription>
              </DialogHeader>
              <Alert>
                <AlertTriangle className="h-4 w-4" />
                <AlertTitle>警告</AlertTitle>
                <AlertDescription>
                  转移项目是不可逆操作。请确保目标用户接受此转移。
                </AlertDescription>
              </Alert>
              <div className="space-y-2">
                <Label htmlFor="transfer-user-id">目标用户ID</Label>
                <Input
                  id="transfer-user-id"
                  value={transferUserId}
                  onChange={(e) => setTransferUserId(e.target.value)}
                  placeholder="请输入目标用户的ID"
                />
                <p className="text-xs text-muted-foreground">
                  请输入要转移到的用户ID，转移后将无法撤销
                </p>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setTransferUserId('')}>
                  取消
                </Button>
                <Button
                  onClick={handleTransfer}
                  disabled={isTransferring || !transferUserId.trim()}
                >
                  {isTransferring ? "转移中..." : "确认转移"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </CardContent>
      </Card>

      {/* 删除项目 */}
      <Card className="border-destructive">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-destructive">
            <Trash2 className="h-5 w-5" />
            删除项目
          </CardTitle>
          <CardDescription>
            永久删除项目及所有相关数据。此操作不可撤销。
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Dialog>
            <DialogTrigger asChild>
              <Button variant="destructive" data-testid="delete-project-button">
                <Trash2 className="mr-2 h-4 w-4" />
                删除项目
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle className="text-destructive">删除项目</DialogTitle>
                <DialogDescription>
                  此操作将永久删除项目及所有相关数据，包括代码、配置、历史版本等。
                </DialogDescription>
              </DialogHeader>
              <Alert variant="destructive">
                <AlertTriangle className="h-4 w-4" />
                <AlertTitle>危险操作</AlertTitle>
                <AlertDescription>
                  此操作不可撤销。删除后所有数据将无法恢复。
                </AlertDescription>
              </Alert>
              <div className="space-y-2">
                <Label htmlFor="delete-confirmation">
                  请输入项目名称 <span className="font-bold">{projectName}</span> 以确认删除
                </Label>
                <Input
                  id="delete-confirmation"
                  data-testid="delete-confirm-input"
                  value={deleteConfirmation}
                  onChange={(e) => setDeleteConfirmation(e.target.value)}
                  placeholder={projectName}
                />
              </div>
              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={() => setDeleteConfirmation('')}
                >
                  取消
                </Button>
                <Button
                  variant="destructive"
                  onClick={handleDelete}
                  disabled={isDeleting || deleteConfirmation !== projectName}
                >
                  {isDeleting ? "删除中..." : "确认删除"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </CardContent>
      </Card>
    </div>
  )
}
