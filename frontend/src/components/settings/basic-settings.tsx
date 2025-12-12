/**
 * 基本设置Section
 * 项目名称、描述、图标、可见性
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
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Save, Upload, Eye, EyeOff, Globe } from "lucide-react"
import type { UpdateProjectSettingsRequest } from "@/types/settings"

/**
 * 基本设置Props
 */
interface BasicSettingsProps {
  /** 项目ID（保留用于后续扩展） */
  projectId: string;
  /** 初始值 */
  initialValues: {
    name: string;
    description: string;
    coverImageUrl?: string;
    visibility: 'public' | 'private' | 'unlisted';
    tags?: string[];
  };
  /** 保存回调 */
  onSave: (data: UpdateProjectSettingsRequest) => Promise<void>;
}

/**
 * 基本设置Section
 */
export function BasicSettings({ initialValues, onSave }: BasicSettingsProps): React.ReactElement {
  const [isEditing, setIsEditing] = React.useState(false)
  const [isSaving, setIsSaving] = React.useState(false)

  const [formData, setFormData] = React.useState({
    name: initialValues.name,
    description: initialValues.description,
    coverImageUrl: initialValues.coverImageUrl || '',
    visibility: initialValues.visibility,
    tags: initialValues.tags?.join(', ') || '',
  })

  /**
   * 处理图标上传
   */
  const handleCoverImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      // TODO: 上传到MinIO
      const reader = new FileReader()
      reader.onloadend = () => {
        setFormData({ ...formData, coverImageUrl: reader.result as string })
      }
      reader.readAsDataURL(file)
    }
  }

  /**
   * 保存设置
   */
  const handleSave = async () => {
    setIsSaving(true)
    try {
      await onSave({
        name: formData.name,
        description: formData.description,
        coverImageUrl: formData.coverImageUrl,
        visibility: formData.visibility,
        tags: formData.tags.split(',').map(t => t.trim()).filter(Boolean),
      })
      setIsEditing(false)
    } catch (error) {
      console.error("保存失败:", error)
      alert("保存失败，请重试")
    } finally {
      setIsSaving(false)
    }
  }

  /**
   * 取消编辑
   */
  const handleCancel = () => {
    setIsEditing(false)
    setFormData({
      name: initialValues.name,
      description: initialValues.description,
      coverImageUrl: initialValues.coverImageUrl || '',
      visibility: initialValues.visibility,
      tags: initialValues.tags?.join(', ') || '',
    })
  }

  /**
   * 获取可见性图标
   */
  const getVisibilityIcon = (visibility: string) => {
    switch (visibility) {
      case 'public':
        return <Globe className="h-4 w-4" />
      case 'private':
        return <EyeOff className="h-4 w-4" />
      case 'unlisted':
        return <Eye className="h-4 w-4" />
      default:
        return null
    }
  }

  /**
   * 获取可见性说明
   */
  const getVisibilityDescription = (visibility: string) => {
    switch (visibility) {
      case 'public':
        return '所有人可见，会出现在社区广场'
      case 'private':
        return '仅自己和团队成员可见'
      case 'unlisted':
        return '有链接的人可见，不会出现在社区广场'
      default:
        return ''
    }
  }

  return (
    <div className="space-y-6">
      {/* 项目图标 */}
      <Card>
        <CardHeader>
          <CardTitle>项目图标</CardTitle>
          <CardDescription>上传项目的封面图片</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center gap-6">
          {/* 图标预览 */}
          <div className="relative h-24 w-24 rounded-lg bg-muted flex items-center justify-center overflow-hidden">
            {formData.coverImageUrl ? (
              <img
                src={formData.coverImageUrl}
                alt="项目图标"
                className="h-full w-full object-cover"
              />
            ) : (
              <span className="text-4xl font-bold text-muted-foreground">
                {formData.name.charAt(0).toUpperCase()}
              </span>
            )}
            {/* 上传按钮 */}
            <label
              htmlFor="cover-upload"
              className="absolute bottom-0 right-0 flex h-8 w-8 cursor-pointer items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-transform hover:scale-110"
            >
              <Upload className="h-4 w-4" />
              <input
                id="cover-upload"
                type="file"
                accept="image/*"
                className="hidden"
                onChange={handleCoverImageUpload}
              />
            </label>
          </div>

          {/* 上传说明 */}
          <div className="flex-1">
            <p className="text-sm text-muted-foreground">
              支持JPG、PNG格式，文件大小不超过5MB
            </p>
            <p className="text-sm text-muted-foreground mt-1">
              建议尺寸：1200x630像素（适配社交媒体分享）
            </p>
          </div>
        </CardContent>
      </Card>

      {/* 基本信息 */}
      <Card>
        <CardHeader>
          <CardTitle>基本信息</CardTitle>
          <CardDescription>管理项目的基本信息</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* 项目名称 */}
          <div className="space-y-2">
            <Label htmlFor="name">项目名称</Label>
            <Input
              id="name"
              data-testid="project-name-input"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              disabled={!isEditing}
              placeholder="请输入项目名称"
            />
          </div>

          {/* 项目描述 */}
          <div className="space-y-2">
            <Label htmlFor="description">项目描述</Label>
            <Textarea
              id="description"
              data-testid="project-description-input"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              disabled={!isEditing}
              placeholder="请输入项目描述"
              rows={4}
            />
          </div>

          {/* 项目标签 */}
          <div className="space-y-2">
            <Label htmlFor="tags">项目标签</Label>
            <Input
              id="tags"
              value={formData.tags}
              onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
              disabled={!isEditing}
              placeholder="用逗号分隔，例如：教育, 工具, AI"
            />
            <p className="text-xs text-muted-foreground">
              标签有助于其他用户发现您的项目
            </p>
          </div>

          {/* 项目可见性 */}
          <div className="space-y-2">
            <Label htmlFor="visibility">可见性</Label>
            <Select
              value={formData.visibility}
              onValueChange={(value: 'public' | 'private' | 'unlisted') =>
                setFormData({ ...formData, visibility: value })
              }
              disabled={!isEditing}
            >
              <SelectTrigger id="visibility" data-testid="visibility-select">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="public">
                  <div className="flex items-center gap-2">
                    <Globe className="h-4 w-4" />
                    <span>公开</span>
                  </div>
                </SelectItem>
                <SelectItem value="unlisted">
                  <div className="flex items-center gap-2">
                    <Eye className="h-4 w-4" />
                    <span>不公开列出</span>
                  </div>
                </SelectItem>
                <SelectItem value="private">
                  <div className="flex items-center gap-2">
                    <EyeOff className="h-4 w-4" />
                    <span>私有</span>
                  </div>
                </SelectItem>
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground flex items-center gap-2">
              {getVisibilityIcon(formData.visibility)}
              {getVisibilityDescription(formData.visibility)}
            </p>
          </div>

          {/* 操作按钮 */}
          <div className="flex gap-3 pt-4">
            {isEditing ? (
              <>
                <Button onClick={handleSave} disabled={isSaving} data-testid="save-project-button">
                  <Save className="mr-2 h-4 w-4" />
                  {isSaving ? "保存中..." : "保存"}
                </Button>
                <Button variant="outline" onClick={handleCancel} disabled={isSaving}>
                  取消
                </Button>
              </>
            ) : (
              <Button onClick={() => setIsEditing(true)} data-testid="edit-project-button">编辑信息</Button>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
