/**
 * 成员管理Section
 * 成员列表、邀请成员、权限设置
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
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { UserPlus, MoreVertical, Crown, Edit, Eye, Trash2 } from "lucide-react"
import type { ProjectMember } from "@/types/settings"
import { inviteMember, removeMember, updateMemberRole } from "@/lib/api/settings"

/**
 * 成员管理Props
 */
interface MemberSettingsProps {
  /** 项目ID */
  projectId: string;
  /** 成员列表 */
  members: ProjectMember[];
  /** 刷新回调 */
  onRefresh: () => void;
}

/**
 * 成员管理Section
 */
export function MemberSettings({
  projectId,
  members,
  onRefresh
}: MemberSettingsProps): React.ReactElement {
  const [isInviting, setIsInviting] = React.useState(false)
  const [inviteEmail, setInviteEmail] = React.useState('')
  const [inviteRole, setInviteRole] = React.useState<'editor' | 'viewer'>('editor')

  /**
   * 邀请成员
   */
  const handleInvite = async () => {
    if (!inviteEmail.trim()) {
      alert("请输入邮箱地址")
      return
    }

    setIsInviting(true)
    try {
      await inviteMember(projectId, { email: inviteEmail, role: inviteRole })
      alert("邀请已发送")
      setInviteEmail('')
      onRefresh()
    } catch (error) {
      console.error("邀请失败:", error)
      alert("邀请失败，请重试")
    } finally {
      setIsInviting(false)
    }
  }

  /**
   * 移除成员
   */
  const handleRemove = async (memberId: string) => {
    if (!confirm("确定要移除此成员吗？")) {
      return
    }

    try {
      await removeMember(projectId, memberId)
      alert("成员已移除")
      onRefresh()
    } catch (error) {
      console.error("移除失败:", error)
      alert("移除失败，请重试")
    }
  }

  /**
   * 更新成员角色
   */
  const handleUpdateRole = async (memberId: string, newRole: 'editor' | 'viewer') => {
    try {
      await updateMemberRole(projectId, memberId, newRole)
      alert("角色已更新")
      onRefresh()
    } catch (error) {
      console.error("更新失败:", error)
      alert("更新失败，请重试")
    }
  }

  /**
   * 获取角色图标
   */
  const getRoleIcon = (role: string) => {
    switch (role) {
      case 'owner':
        return <Crown className="h-3 w-3" />
      case 'editor':
        return <Edit className="h-3 w-3" />
      case 'viewer':
        return <Eye className="h-3 w-3" />
      default:
        return null
    }
  }

  /**
   * 获取角色徽章颜色
   */
  const getRoleBadgeVariant = (role: string): "default" | "secondary" | "outline" => {
    switch (role) {
      case 'owner':
        return "default"
      case 'editor':
        return "secondary"
      case 'viewer':
        return "outline"
      default:
        return "outline"
    }
  }

  /**
   * 获取用户名首字母
   */
  const getInitials = (name: string): string => {
    return name.split(' ').map(n => n.charAt(0)).join('').toUpperCase().slice(0, 2)
  }

  return (
    <div className="space-y-6">
      {/* 邀请成员 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <UserPlus className="h-5 w-5" />
            邀请成员
          </CardTitle>
          <CardDescription>
            邀请团队成员协作编辑项目
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Dialog>
            <DialogTrigger asChild>
              <Button data-testid="add-collaborator-button">
                <UserPlus className="mr-2 h-4 w-4" />
                邀请成员
              </Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>邀请成员</DialogTitle>
                <DialogDescription>
                  输入成员的邮箱地址并选择权限
                </DialogDescription>
              </DialogHeader>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="invite-email">邮箱地址</Label>
                  <Input
                    id="invite-email"
                    data-testid="collaborator-email-input"
                    type="email"
                    value={inviteEmail}
                    onChange={(e) => setInviteEmail(e.target.value)}
                    placeholder="member@example.com"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="invite-role">权限</Label>
                  <Select
                    value={inviteRole}
                    onValueChange={(value: 'editor' | 'viewer') => setInviteRole(value)}
                  >
                    <SelectTrigger id="invite-role" data-testid="collaborator-role-select">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="editor">
                        <div className="flex items-center gap-2">
                          <Edit className="h-4 w-4" />
                          <div>
                            <p className="font-medium">编辑者</p>
                            <p className="text-xs text-muted-foreground">可以查看和编辑项目</p>
                          </div>
                        </div>
                      </SelectItem>
                      <SelectItem value="viewer">
                        <div className="flex items-center gap-2">
                          <Eye className="h-4 w-4" />
                          <div>
                            <p className="font-medium">查看者</p>
                            <p className="text-xs text-muted-foreground">只能查看项目，无法编辑</p>
                          </div>
                        </div>
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setInviteEmail('')}>
                  取消
                </Button>
                <Button onClick={handleInvite} disabled={isInviting || !inviteEmail.trim()} data-testid="invite-collaborator-button">
                  {isInviting ? "发送中..." : "发送邀请"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </CardContent>
      </Card>

      {/* 成员列表 */}
      <Card>
        <CardHeader>
          <CardTitle>成员列表</CardTitle>
          <CardDescription>
            {members.length} 位成员
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {members.map((member) => (
              <div
                key={member.id}
                data-testid="collaborator-item"
                className="flex items-center justify-between p-4 rounded-lg border bg-card"
              >
                <div className="flex items-center gap-4">
                  <Avatar>
                    <AvatarImage src={member.avatarUrl} alt={member.username} />
                    <AvatarFallback>{getInitials(member.username)}</AvatarFallback>
                  </Avatar>
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="font-medium">{member.username}</p>
                      <Badge variant={getRoleBadgeVariant(member.role)}>
                        <span className="flex items-center gap-1">
                          {getRoleIcon(member.role)}
                          {member.role === 'owner' ? '所有者' : member.role === 'editor' ? '编辑者' : '查看者'}
                        </span>
                      </Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">{member.email}</p>
                    <p className="text-xs text-muted-foreground">
                      加入于 {new Date(member.joinedAt).toLocaleDateString('zh-CN')}
                    </p>
                  </div>
                </div>

                {member.role !== 'owner' && (
                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="icon">
                        <MoreVertical className="h-4 w-4" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem onClick={() => handleUpdateRole(member.id, 'editor')}>
                        <Edit className="mr-2 h-4 w-4" />
                        设为编辑者
                      </DropdownMenuItem>
                      <DropdownMenuItem onClick={() => handleUpdateRole(member.id, 'viewer')}>
                        <Eye className="mr-2 h-4 w-4" />
                        设为查看者
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                      <DropdownMenuItem
                        onClick={() => handleRemove(member.id)}
                        className="text-destructive"
                        data-testid="delete-collaborator-button"
                      >
                        <Trash2 className="mr-2 h-4 w-4" />
                        移除成员
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                )}
              </div>
            ))}

            {members.length === 0 && (
              <div className="text-center py-8 text-muted-foreground">
                <UserPlus className="h-12 w-12 mx-auto mb-2 opacity-50" />
                <p>暂无成员</p>
                <p className="text-sm">邀请团队成员开始协作</p>
              </div>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
