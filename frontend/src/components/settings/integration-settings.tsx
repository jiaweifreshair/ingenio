/**
 * 集成设置Section
 * GitHub、自定义域名、Webhook
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
import { Switch } from "@/components/ui/switch"
import { Github, Globe, Webhook, Save, ExternalLink } from "lucide-react"

/**
 * 集成设置Props
 */
interface IntegrationSettingsProps {
  /** 项目ID（保留用于后续扩展） */
  projectId: string;
  /** 初始值 */
  initialValues: {
    githubEnabled: boolean;
    githubRepo?: string;
    customDomain?: string;
    webhookUrl?: string;
  };
  /** 保存回调 */
  onSave: (data: {
    githubEnabled: boolean;
    githubRepo?: string;
    customDomain?: string;
    webhookUrl?: string;
  }) => Promise<void>;
}

/**
 * 集成设置Section
 */
export function IntegrationSettings({
  initialValues,
  onSave
}: IntegrationSettingsProps): React.ReactElement {
  const [isEditing, setIsEditing] = React.useState(false)
  const [isSaving, setIsSaving] = React.useState(false)

  const [formData, setFormData] = React.useState({
    githubEnabled: initialValues.githubEnabled,
    githubRepo: initialValues.githubRepo || '',
    customDomain: initialValues.customDomain || '',
    webhookUrl: initialValues.webhookUrl || '',
  })

  /**
   * 保存设置
   */
  const handleSave = async () => {
    setIsSaving(true)
    try {
      await onSave(formData)
      setIsEditing(false)
      alert("集成设置已保存")
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
      githubEnabled: initialValues.githubEnabled,
      githubRepo: initialValues.githubRepo || '',
      customDomain: initialValues.customDomain || '',
      webhookUrl: initialValues.webhookUrl || '',
    })
  }

  return (
    <div className="space-y-6">
      {/* GitHub集成 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Github className="h-5 w-5" />
            GitHub集成
          </CardTitle>
          <CardDescription>
            将项目代码同步到GitHub仓库
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label>启用GitHub集成</Label>
              <p className="text-sm text-muted-foreground">
                自动将生成的代码推送到GitHub
              </p>
            </div>
            <Switch
              checked={formData.githubEnabled}
              onCheckedChange={(checked) =>
                setFormData({ ...formData, githubEnabled: checked })
              }
              disabled={!isEditing}
            />
          </div>

          {formData.githubEnabled && (
            <div className="space-y-2">
              <Label htmlFor="github-repo">GitHub仓库</Label>
              <div className="flex gap-2">
                <Input
                  id="github-repo"
                  value={formData.githubRepo}
                  onChange={(e) => setFormData({ ...formData, githubRepo: e.target.value })}
                  disabled={!isEditing}
                  placeholder="username/repository"
                />
                {formData.githubRepo && (
                  <Button variant="outline" size="icon" asChild>
                    <a
                      href={`https://github.com/${formData.githubRepo}`}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <ExternalLink className="h-4 w-4" />
                    </a>
                  </Button>
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                格式：用户名/仓库名，例如：ingenio/my-app
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 自定义域名 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Globe className="h-5 w-5" />
            自定义域名
          </CardTitle>
          <CardDescription>
            为您的项目配置自定义域名
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="custom-domain">域名</Label>
            <Input
              id="custom-domain"
              value={formData.customDomain}
              onChange={(e) => setFormData({ ...formData, customDomain: e.target.value })}
              disabled={!isEditing}
              placeholder="example.com"
            />
            <p className="text-xs text-muted-foreground">
              请先在域名提供商处添加CNAME记录指向：ingenio.dev
            </p>
          </div>

          {formData.customDomain && (
            <div className="rounded-md bg-muted p-3 text-sm">
              <p className="font-medium mb-2">DNS配置说明</p>
              <div className="space-y-1 font-mono text-xs">
                <p>类型：CNAME</p>
                <p>名称：{formData.customDomain}</p>
                <p>值：ingenio.dev</p>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Webhook配置 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Webhook className="h-5 w-5" />
            Webhook
          </CardTitle>
          <CardDescription>
            配置项目事件的Webhook通知
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="webhook-url">Webhook URL</Label>
            <Input
              id="webhook-url"
              value={formData.webhookUrl}
              onChange={(e) => setFormData({ ...formData, webhookUrl: e.target.value })}
              disabled={!isEditing}
              placeholder="https://example.com/webhook"
            />
            <p className="text-xs text-muted-foreground">
              当项目发生重要事件时（如构建完成、发布等），会向此URL发送POST请求
            </p>
          </div>

          {formData.webhookUrl && (
            <div className="rounded-md bg-muted p-3 text-sm">
              <p className="font-medium mb-2">事件类型</p>
              <ul className="space-y-1 text-xs list-disc list-inside">
                <li>项目创建</li>
                <li>代码生成完成</li>
                <li>构建完成</li>
                <li>项目发布</li>
                <li>错误发生</li>
              </ul>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 操作按钮 */}
      <div className="flex gap-3">
        {isEditing ? (
          <>
            <Button onClick={handleSave} disabled={isSaving}>
              <Save className="mr-2 h-4 w-4" />
              {isSaving ? "保存中..." : "保存"}
            </Button>
            <Button variant="outline" onClick={handleCancel} disabled={isSaving}>
              取消
            </Button>
          </>
        ) : (
          <Button onClick={() => setIsEditing(true)}>编辑集成设置</Button>
        )}
      </div>
    </div>
  )
}
