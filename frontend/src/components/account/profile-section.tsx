/**
 * 个人信息Section
 * 头像上传、用户名、邮箱、手机号、密码修改
 *
 * 功能：
 * - 获取用户信息（真实API）
 * - 更新用户信息（真实API）
 * - 上传头像到MinIO（真实API）
 * - 修改密码（真实API）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Skeleton } from "@/components/ui/skeleton"
import { Camera, Save, Lock, AlertCircle, CheckCircle } from "lucide-react"
import { useToast } from "@/hooks/use-toast"
import {
  getUserProfile,
  updateUserProfile,
  uploadAvatar,
  changePassword,
  type UserProfile,
  type UpdateProfileRequest,
  type ChangePasswordRequest,
} from "@/lib/api/user"
import { LoginDialog } from "@/components/auth/login-dialog"

/**
 * 个人信息Section
 */
export function ProfileSection(): React.ReactElement {
  const { toast } = useToast()

  // 状态管理
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [profile, setProfile] = React.useState<UserProfile | null>(null)
  const [isEditing, setIsEditing] = React.useState(false)
  const [isSaving, setIsSaving] = React.useState(false)
  const [isChangingPassword, setIsChangingPassword] = React.useState(false)
  const [isUploadingAvatar, setIsUploadingAvatar] = React.useState(false)

  // 登录弹窗状态
  const [showLoginDialog, setShowLoginDialog] = React.useState(false)

  // 表单状态
  const [formData, setFormData] = React.useState<UpdateProfileRequest>({
    username: "",
    email: "",
    phone: "",
  })

  // 密码修改表单
  const [passwordData, setPasswordData] = React.useState<ChangePasswordRequest>({
    currentPassword: "",
    newPassword: "",
  })

  const [confirmPassword, setConfirmPassword] = React.useState("")

  /**
   * 加载用户信息
   */
  const loadProfile = React.useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const data = await getUserProfile()

      // 处理未认证的情况（返回null）
      if (data === null) {
        console.warn('[个人中心] 用户未登录或Token已过期')
        setProfile(null)
        setShowLoginDialog(true) // 自动弹出登录框
        setError("获取用户信息失败:")
        return
      }

      setProfile(data)
      setFormData({
        username: data.username,
        email: data.email,
        phone: data.phone || "",
      })
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
    loadProfile()
  }, [loadProfile])

  /**
   * 登录成功回调
   * 登录成功后重新加载用户信息
   */
  const handleLoginSuccess = React.useCallback(() => {
    setShowLoginDialog(false)
    setError(null)
    loadProfile()
  }, [loadProfile])

  /**
   * 处理头像上传
   */
  const handleAvatarUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    // 验证文件类型
    if (!file.type.startsWith("image/")) {
      toast({
        title: "文件类型错误",
        description: "请上传图片文件（JPG、PNG）",
        variant: "destructive",
      })
      return
    }

    // 验证文件大小（2MB）
    if (file.size > 2 * 1024 * 1024) {
      toast({
        title: "文件过大",
        description: "图片大小不能超过2MB",
        variant: "destructive",
      })
      return
    }

    setIsUploadingAvatar(true)
    try {
      const avatarUrl = await uploadAvatar(file)

      // 更新本地状态
      if (profile) {
        setProfile({ ...profile, avatar: avatarUrl })
      }

      toast({
        title: "上传成功",
        description: "头像已更新",
      })
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "上传失败"
      toast({
        title: "上传失败",
        description: errorMessage,
        variant: "destructive",
      })
    } finally {
      setIsUploadingAvatar(false)
    }
  }

  /**
   * 保存个人信息
   */
  const handleSave = async () => {
    // 验证必填项
    if (!formData.username?.trim()) {
      toast({
        title: "验证失败",
        description: "用户名不能为空",
        variant: "destructive",
      })
      return
    }

    if (!formData.email?.trim()) {
      toast({
        title: "验证失败",
        description: "邮箱不能为空",
        variant: "destructive",
      })
      return
    }

    setIsSaving(true)
    try {
      const updatedProfile = await updateUserProfile(formData)
      setProfile(updatedProfile)
      setIsEditing(false)

      toast({
        title: "保存成功",
        description: "个人信息已更新",
      })
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "保存失败"
      toast({
        title: "保存失败",
        description: errorMessage,
        variant: "destructive",
      })
    } finally {
      setIsSaving(false)
    }
  }

  /**
   * 修改密码
   */
  const handleChangePassword = async () => {
    // 验证密码长度
    if (passwordData.newPassword.length < 8) {
      toast({
        title: "密码过短",
        description: "密码长度至少8位",
        variant: "destructive",
      })
      return
    }

    // 验证密码一致性
    if (passwordData.newPassword !== confirmPassword) {
      toast({
        title: "密码不一致",
        description: "两次输入的密码不一致",
        variant: "destructive",
      })
      return
    }

    setIsSaving(true)
    try {
      await changePassword(passwordData)
      setIsChangingPassword(false)
      setPasswordData({
        currentPassword: "",
        newPassword: "",
      })
      setConfirmPassword("")

      toast({
        title: "修改成功",
        description: "密码已更新，请重新登录",
      })

      // TODO: 跳转到登录页面
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "修改失败"
      toast({
        title: "修改失败",
        description: errorMessage,
        variant: "destructive",
      })
    } finally {
      setIsSaving(false)
    }
  }

  /**
   * 获取用户名首字母（用于头像占位符）
   */
  const getInitials = (name: string): string => {
    return name.charAt(0).toUpperCase()
  }

  // 加载中状态
  if (loading) {
    return (
      <div className="space-y-8">
        <div className="p-8 rounded-[32px] bg-white dark:bg-[#1C1C1E] shadow-sm border border-black/5 dark:border-white/5">
          <Skeleton className="h-8 w-24 mb-6" />
          <div className="flex items-center gap-8">
            <Skeleton className="h-32 w-32 rounded-full" />
            <div className="space-y-2">
              <Skeleton className="h-5 w-32" />
              <Skeleton className="h-4 w-48" />
            </div>
          </div>
        </div>
        <div className="p-8 rounded-[32px] bg-white dark:bg-[#1C1C1E] shadow-sm border border-black/5 dark:border-white/5">
          <div className="flex justify-between mb-6">
            <div className="space-y-2">
              <Skeleton className="h-8 w-32" />
              <Skeleton className="h-4 w-24" />
            </div>
            <Skeleton className="h-10 w-20 rounded-full" />
          </div>
          <div className="space-y-6 max-w-2xl">
            <Skeleton className="h-12 w-full rounded-xl" />
            <Skeleton className="h-12 w-full rounded-xl" />
            <Skeleton className="h-12 w-full rounded-xl" />
          </div>
        </div>
      </div>
    )
  }

  // 错误状态（需要登录时显示登录弹窗）
  if (error) {
    return (
      <>
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>
            <div className="mb-3">{error}</div>
            <div className="flex gap-2">
              <Button
                size="sm"
                onClick={() => setShowLoginDialog(true)}
              >
                前往登录
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={loadProfile}
              >
                重试
              </Button>
            </div>
          </AlertDescription>
        </Alert>

        {/* 登录弹窗 - 用户未登录时自动弹出 */}
        <LoginDialog
          open={showLoginDialog}
          onOpenChange={setShowLoginDialog}
          onSuccess={handleLoginSuccess}
          title="登录以查看个人信息"
          description="您尚未登录或登录已过期，请登录后继续"
        />
      </>
    )
  }

  // 无数据状态
  if (!profile) {
    return (
      <Alert className="rounded-2xl bg-white/50 dark:bg-white/5 backdrop-blur-xl border-0 shadow-sm">
        <AlertCircle className="h-4 w-4" />
        <AlertDescription>未找到用户信息</AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="space-y-8">
      {/* 头像卡片 */}
      <div className="p-8 rounded-[32px] bg-white dark:bg-[#1C1C1E] shadow-sm border border-black/5 dark:border-white/5">
        <h3 className="text-xl font-semibold mb-6">头像</h3>
        <div className="flex flex-col sm:flex-row items-center gap-8">
          {/* 头像预览 */}
          <div className="relative group">
            <Avatar className="h-32 w-32 ring-4 ring-white dark:ring-[#1C1C1E] shadow-lg transition-transform duration-300 group-hover:scale-105">
              <AvatarImage src={profile.avatar} alt={profile.username} className="object-cover" />
              <AvatarFallback className="text-4xl bg-gradient-to-br from-blue-500 to-purple-600 text-white">
                {getInitials(profile.username)}
              </AvatarFallback>
            </Avatar>
            {/* 上传按钮 */}
            <label
              htmlFor="avatar-upload"
              className="absolute -bottom-2 -right-2 flex h-10 w-10 cursor-pointer items-center justify-center rounded-full bg-foreground text-background shadow-lg transition-all hover:scale-110 hover:bg-primary hover:text-primary-foreground"
            >
              {isUploadingAvatar ? (
                <div className="h-5 w-5 animate-spin rounded-full border-2 border-current border-t-transparent" />
              ) : (
                <Camera className="h-5 w-5" />
              )}
              <input
                id="avatar-upload"
                type="file"
                accept="image/*"
                className="hidden"
                onChange={handleAvatarUpload}
                disabled={isUploadingAvatar}
              />
            </label>
          </div>

          {/* 上传说明 */}
          <div className="flex-1 text-center sm:text-left">
            <h4 className="font-medium text-foreground mb-1">修改头像</h4>
            <p className="text-sm text-muted-foreground leading-relaxed">
              支持 JPG、PNG 格式，建议尺寸 256x256 像素。<br />
              文件大小不超过 2MB。
            </p>
          </div>
        </div>
      </div>

      {/* 个人信息卡片 */}
      <div className="p-8 rounded-[32px] bg-white dark:bg-[#1C1C1E] shadow-sm border border-black/5 dark:border-white/5">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h3 className="text-xl font-semibold">个人信息</h3>
            <p className="text-sm text-muted-foreground mt-1">管理您的基本资料</p>
          </div>
          {/* 操作按钮放在右上角 */}
          {!isEditing && (
            <Button 
              variant="outline" 
              onClick={() => setIsEditing(true)}
              className="rounded-full px-6 border-border/50 hover:bg-muted/50"
            >
              编辑
            </Button>
          )}
        </div>

        <div className="space-y-6 max-w-2xl">
          {/* 用户名 */}
          <div className="space-y-2">
            <Label htmlFor="username" className="text-sm font-medium ml-1">用户名</Label>
            <Input
              id="username"
              value={isEditing ? formData.username : profile.username}
              onChange={(e) => setFormData({ ...formData, username: e.target.value })}
              disabled={!isEditing}
              className="h-12 rounded-xl bg-muted/30 border-transparent focus:bg-background focus:border-primary/50 transition-all"
            />
          </div>

          {/* 邮箱 */}
          <div className="space-y-2">
            <Label htmlFor="email" className="text-sm font-medium ml-1">邮箱</Label>
            <Input
              id="email"
              type="email"
              value={isEditing ? formData.email : profile.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              disabled={!isEditing}
              className="h-12 rounded-xl bg-muted/30 border-transparent focus:bg-background focus:border-primary/50 transition-all"
            />
          </div>

          {/* 手机号 */}
          <div className="space-y-2">
            <Label htmlFor="phone" className="text-sm font-medium ml-1">手机号</Label>
            <Input
              id="phone"
              type="tel"
              value={isEditing ? formData.phone : profile.phone || ""}
              onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              disabled={!isEditing}
              placeholder="未设置"
              className="h-12 rounded-xl bg-muted/30 border-transparent focus:bg-background focus:border-primary/50 transition-all"
            />
          </div>

          {/* 编辑模式下的操作按钮 */}
          {isEditing && (
            <div className="flex gap-3 pt-4 animate-in fade-in slide-in-from-top-2">
              <Button 
                onClick={handleSave} 
                disabled={isSaving}
                className="rounded-full px-8 h-11"
              >
                {isSaving ? (
                  <>
                    <div className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-background border-t-transparent" />
                    保存中...
                  </>
                ) : (
                  <>
                    <Save className="mr-2 h-4 w-4" />
                    保存修改
                  </>
                )}
              </Button>
              <Button
                variant="ghost"
                onClick={() => {
                  setIsEditing(false)
                  setFormData({
                    username: profile.username,
                    email: profile.email,
                    phone: profile.phone || "",
                  })
                }}
                disabled={isSaving}
                className="rounded-full px-6 h-11 hover:bg-muted/50"
              >
                取消
              </Button>
            </div>
          )}
        </div>
      </div>

      {/* 密码修改卡片 */}
      <div className="p-8 rounded-[32px] bg-white dark:bg-[#1C1C1E] shadow-sm border border-black/5 dark:border-white/5">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-xl font-semibold">安全设置</h3>
            <p className="text-sm text-muted-foreground mt-1">定期修改密码可以保护账号安全</p>
          </div>
          
          <Dialog open={isChangingPassword} onOpenChange={setIsChangingPassword}>
            <DialogTrigger asChild>
              <Button variant="outline" className="rounded-full px-6 border-border/50 hover:bg-muted/50">
                <Lock className="mr-2 h-4 w-4" />
                修改密码
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[425px] rounded-3xl">
              <DialogHeader>
                <DialogTitle>修改密码</DialogTitle>
                <DialogDescription>
                  请输入当前密码和新密码
                </DialogDescription>
              </DialogHeader>

              <div className="space-y-5 py-4">
                {/* ... existing inputs with styles ... */}
                {/* 当前密码 */}
                <div className="space-y-2">
                  <Label htmlFor="current-password">当前密码</Label>
                  <Input
                    id="current-password"
                    type="password"
                    value={passwordData.currentPassword}
                    onChange={(e) =>
                      setPasswordData({ ...passwordData, currentPassword: e.target.value })
                    }
                    className="h-11 rounded-xl"
                  />
                </div>

                {/* 新密码 */}
                <div className="space-y-2">
                  <Label htmlFor="new-password">新密码</Label>
                  <Input
                    id="new-password"
                    type="password"
                    value={passwordData.newPassword}
                    onChange={(e) =>
                      setPasswordData({ ...passwordData, newPassword: e.target.value })
                    }
                    className="h-11 rounded-xl"
                  />
                  <p className="text-xs text-muted-foreground px-1">
                    密码长度至少8位，建议包含字母、数字和特殊字符
                  </p>
                </div>

                {/* 确认密码 */}
                <div className="space-y-2">
                  <Label htmlFor="confirm-password">确认新密码</Label>
                  <Input
                    id="confirm-password"
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    className="h-11 rounded-xl"
                  />
                </div>

                {/* 密码强度提示 */}
                {passwordData.newPassword && (
                  <div className="flex items-center gap-2 text-sm text-green-600 bg-green-50 dark:bg-green-900/20 p-3 rounded-xl">
                    <CheckCircle className="h-4 w-4" />
                    <span>
                      密码强度：{passwordData.newPassword.length >= 12 ? "强" : passwordData.newPassword.length >= 8 ? "中" : "弱"}
                    </span>
                  </div>
                )}
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={() => setIsChangingPassword(false)} className="rounded-full">
                  取消
                </Button>
                <Button onClick={handleChangePassword} disabled={isSaving} className="rounded-full">
                  {isSaving ? "修改中..." : "确认修改"}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      </div>
    </div>
  )
}
