/**
 * 项目设置页面
 * Tab导航：基本信息、高级设置、集成设置、成员管理
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import { useParams, useRouter } from "next/navigation"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Button } from "@/components/ui/button"
import { ArrowLeft, Settings, Shield, Plug, Users } from "lucide-react"
import { BasicSettings } from "@/components/settings/basic-settings"
import { AdvancedSettings } from "@/components/settings/advanced-settings"
import { IntegrationSettings } from "@/components/settings/integration-settings"
import { MemberSettings } from "@/components/settings/member-settings"
import { getProjectById } from "@/lib/api/projects"
import { getProjectMembers, updateProjectSettings } from "@/lib/api/settings"
import type { Project } from "@/types/project"
import type { ProjectMember, UpdateProjectSettingsRequest } from "@/types/settings"

/**
 * 项目设置页面
 */
export default function ProjectSettingsPage(): React.ReactElement {
  const params = useParams()
  const router = useRouter()
  const projectId = params.projectId as string

  const [project, setProject] = React.useState<Project | null>(null)
  const [members, setMembers] = React.useState<ProjectMember[]>([])
  const [isLoading, setIsLoading] = React.useState(true)

  /**
   * 加载项目数据
   */
  const loadProject = React.useCallback(async () => {
    try {
      const data = await getProjectById(projectId)
      setProject(data)
    } catch (error) {
      console.error("加载项目失败:", error)
      alert("加载项目失败")
    }
  }, [projectId])

  /**
   * 加载成员列表
   */
  const loadMembers = React.useCallback(async () => {
    try {
      const data = await getProjectMembers(projectId)
      setMembers(data)
    } catch (error) {
      console.error("加载成员列表失败:", error)
      // 成员列表加载失败不阻塞页面
    }
  }, [projectId])

  /**
   * 初始化加载
   */
  React.useEffect(() => {
    const init = async () => {
      setIsLoading(true)
      await Promise.all([loadProject(), loadMembers()])
      setIsLoading(false)
    }
    init()
  }, [loadProject, loadMembers])

  /**
   * 保存基本设置
   */
  const handleSaveBasicSettings = async (data: UpdateProjectSettingsRequest) => {
    await updateProjectSettings(projectId, data)
    await loadProject()
  }

  /**
   * 保存集成设置
   */
  const handleSaveIntegrationSettings = async (data: {
    githubEnabled: boolean;
    githubRepo?: string;
    customDomain?: string;
    webhookUrl?: string;
  }) => {
    // 将集成设置保存到metadata
    await updateProjectSettings(projectId, {
      metadata: {
        ...project?.metadata,
        integrations: data,
      },
    })
    await loadProject()
  }

  /**
   * 返回项目详情
   */
  const handleBack = () => {
    router.push(`/dashboard`)
  }

  // 加载中状态
  if (isLoading || !project) {
    return (
      <div className="container max-w-6xl py-8">
        <div className="flex items-center justify-center min-h-[400px]">
          <p className="text-muted-foreground">加载中...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="container max-w-6xl py-8">
      {/* 页面头部 */}
      <div className="mb-8">
        <Button variant="ghost" onClick={handleBack} className="mb-4">
          <ArrowLeft className="mr-2 h-4 w-4" />
          返回项目
        </Button>
        <h1 className="text-3xl font-bold">{project.name}</h1>
        <p className="text-muted-foreground mt-1">项目设置</p>
      </div>

      {/* Tab导航 */}
      <Tabs defaultValue="basic" className="space-y-6">
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="basic">
            <Settings className="mr-2 h-4 w-4" />
            基本信息
          </TabsTrigger>
          <TabsTrigger value="advanced">
            <Shield className="mr-2 h-4 w-4" />
            高级设置
          </TabsTrigger>
          <TabsTrigger value="integrations">
            <Plug className="mr-2 h-4 w-4" />
            集成设置
          </TabsTrigger>
          <TabsTrigger value="members">
            <Users className="mr-2 h-4 w-4" />
            成员管理
          </TabsTrigger>
        </TabsList>

        {/* 基本信息 */}
        <TabsContent value="basic">
          <BasicSettings
            projectId={projectId}
            initialValues={{
              name: project.name,
              description: project.description,
              coverImageUrl: project.coverImageUrl,
              visibility: project.visibility as 'public' | 'private' | 'unlisted',
              tags: project.tags,
            }}
            onSave={handleSaveBasicSettings}
          />
        </TabsContent>

        {/* 高级设置 */}
        <TabsContent value="advanced">
          <AdvancedSettings
            projectId={projectId}
            projectName={project.name}
            isArchived={project.status === 'archived'}
          />
        </TabsContent>

        {/* 集成设置 */}
        <TabsContent value="integrations">
          <IntegrationSettings
            projectId={projectId}
            initialValues={{
              githubEnabled: project.metadata?.integrations?.githubEnabled || false,
              githubRepo: project.metadata?.integrations?.githubRepo,
              customDomain: project.metadata?.integrations?.customDomain,
              webhookUrl: project.metadata?.integrations?.webhookUrl,
            }}
            onSave={handleSaveIntegrationSettings}
          />
        </TabsContent>

        {/* 成员管理 */}
        <TabsContent value="members">
          <MemberSettings
            projectId={projectId}
            members={members}
            onRefresh={loadMembers}
          />
        </TabsContent>
      </Tabs>
    </div>
  )
}
