/**
 * API密钥Section
 * 密钥展示、生成、复制、删除
 *
 * 功能：
 * - 获取API密钥列表（真实API）
 * - 生成新密钥（真实API）
 * - 删除密钥（真实API）
 * - 复制密钥到剪贴板
 * - 乐观UI更新
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
"use client"

import * as React from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Skeleton } from "@/components/ui/skeleton"
import { Copy, Plus, Trash2, Eye, EyeOff, Check, AlertCircle } from "lucide-react"
import { useToast } from "@/hooks/use-toast"
import {
  listApiKeys,
  createApiKey,
  deleteApiKey,
  type ApiKey,
} from "@/lib/api/user"

/**
 * API密钥Section
 */
export function ApiKeysSection(): React.ReactElement {
  const { toast } = useToast()

  // 状态管理
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string | null>(null)
  const [apiKeys, setApiKeys] = React.useState<ApiKey[]>([])
  const [createDialogOpen, setCreateDialogOpen] = React.useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = React.useState(false)
  const [selectedKey, setSelectedKey] = React.useState<ApiKey | null>(null)
  const [isCreating, setIsCreating] = React.useState(false)
  const [isDeleting, setIsDeleting] = React.useState(false)
  const [newKeyName, setNewKeyName] = React.useState("")
  const [visibleKeys, setVisibleKeys] = React.useState<Set<string>>(new Set())
  const [copiedKeys, setCopiedKeys] = React.useState<Set<string>>(new Set())

  /**
   * 加载API密钥列表
   */
  const loadKeys = React.useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const data = await listApiKeys()
      setApiKeys(data)
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
    loadKeys()
  }, [loadKeys])

  /**
   * 生成新密钥
   */
  const handleCreateKey = async () => {
    if (!newKeyName.trim()) {
      toast({
        title: "验证失败",
        description: "请输入密钥名称",
        variant: "destructive",
      })
      return
    }

    setIsCreating(true)
    try {
      const newKey = await createApiKey({
        name: newKeyName.trim(),
      })

      // 添加到列表
      setApiKeys([...apiKeys, newKey])
      setNewKeyName("")
      setCreateDialogOpen(false)

      toast({
        title: "生成成功",
        description: "新密钥已生成，请妥善保管",
      })
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "生成失败"
      toast({
        title: "生成失败",
        description: errorMessage,
        variant: "destructive",
      })
    } finally {
      setIsCreating(false)
    }
  }

  /**
   * 删除密钥
   */
  const handleDeleteKey = async () => {
    if (!selectedKey) return

    setIsDeleting(true)
    try {
      // 乐观UI更新
      setApiKeys(apiKeys.filter((key) => key.id !== selectedKey.id))
      setDeleteDialogOpen(false)
      setSelectedKey(null)

      // 调用API删除
      await deleteApiKey(selectedKey.id)

      toast({
        title: "删除成功",
        description: `密钥 "${selectedKey.name}" 已删除`,
      })
    } catch (err) {
      // 删除失败，回滚UI
      await loadKeys()

      const errorMessage = err instanceof Error ? err.message : "删除失败"
      toast({
        title: "删除失败",
        description: errorMessage,
        variant: "destructive",
      })
    } finally {
      setIsDeleting(false)
    }
  }

  /**
   * 复制密钥到剪贴板
   */
  const handleCopyKey = async (key: ApiKey) => {
    try {
      // 优先复制完整密钥（仅创建时有），否则复制前缀
      const keyToCopy = key.fullKey || key.keyPrefix
      await navigator.clipboard.writeText(keyToCopy)
      setCopiedKeys(new Set([...copiedKeys, key.id]))

      toast({
        title: "复制成功",
        description: "密钥已复制到剪贴板",
      })

      setTimeout(() => {
        setCopiedKeys((prev) => {
          const newSet = new Set(prev)
          newSet.delete(key.id)
          return newSet
        })
      }, 2000)
    } catch {
      toast({
        title: "复制失败",
        description: "无法复制到剪贴板",
        variant: "destructive",
      })
    }
  }

  /**
   * 切换密钥可见性
   */
  const toggleKeyVisibility = (keyId: string) => {
    setVisibleKeys((prev) => {
      const newSet = new Set(prev)
      if (newSet.has(keyId)) {
        newSet.delete(keyId)
      } else {
        newSet.add(keyId)
      }
      return newSet
    })
  }

  /**
   * 格式化密钥显示（部分隐藏）
   * @param keyPrefix 密钥前缀
   * @param fullKey 完整密钥（可选，仅创建时有）
   * @param isVisible 是否显示完整密钥
   */
  const formatKey = (keyPrefix: string, fullKey: string | undefined, isVisible: boolean): string => {
    if (isVisible && fullKey) return fullKey
    // 显示前缀 + 星号
    return `${keyPrefix}${"*".repeat(20)}`
  }

  /**
   * 格式化日期
   */
  const formatDate = (dateString: string) => {
    const date = new Date(dateString)
    return date.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    })
  }

  // 加载中状态
  if (loading) {
    return (
      <div className="space-y-8">
        <div className="p-8 rounded-[32px] bg-white dark:bg-[#1C1C1E] shadow-sm border border-black/5 dark:border-white/5">
          <div className="flex justify-between">
            <div className="space-y-2">
              <Skeleton className="h-8 w-32" />
              <Skeleton className="h-4 w-64" />
            </div>
            <Skeleton className="h-11 w-32 rounded-full" />
          </div>
          <div className="mt-6">
            <Skeleton className="h-16 w-full rounded-xl" />
          </div>
        </div>
        <div className="space-y-4">
          {[...Array(2)].map((_, i) => (
            <Skeleton key={i} className="h-32 w-full rounded-[24px]" />
          ))}
        </div>
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
            onClick={loadKeys}
          >
            重试
          </Button>
        </AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="space-y-8">
      {/* 顶部说明和创建按钮 */}
      <div className="flex flex-col md:flex-row md:items-start justify-between gap-6 p-8 rounded-[32px] bg-white dark:bg-[#1C1C1E] shadow-sm border border-black/5 dark:border-white/5">
        <div className="flex-1">
          <h3 className="text-xl font-semibold">API密钥管理</h3>
          <p className="text-sm text-muted-foreground mt-2 leading-relaxed max-w-2xl">
            管理您的API密钥，用于调用秒构AI的API服务。请妥善保管您的密钥，不要在公开的代码仓库或客户端代码中暴露。
          </p>
          
          <div className="mt-4 inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400 text-xs font-medium">
            <AlertCircle className="h-3.5 w-3.5" />
            <span>密钥一旦泄露，请立即删除并重新生成</span>
          </div>
        </div>

        <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
          <DialogTrigger asChild>
            <Button className="rounded-full px-6 h-11 shadow-sm hover:shadow-md transition-all">
              <Plus className="mr-2 h-4 w-4" />
              生成新密钥
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[425px] rounded-3xl">
            <DialogHeader>
              <DialogTitle>生成新密钥</DialogTitle>
              <DialogDescription>
                为您的应用生成一个新的API密钥
              </DialogDescription>
            </DialogHeader>

            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="key-name">密钥名称</Label>
                <Input
                  id="key-name"
                  placeholder="例如：开发环境、生产环境"
                  value={newKeyName}
                  onChange={(e) => setNewKeyName(e.target.value)}
                  className="h-11 rounded-xl"
                />
                <p className="text-xs text-muted-foreground px-1">
                  用于标识密钥的用途，方便管理
                </p>
              </div>
            </div>

            <DialogFooter>
              <Button variant="outline" onClick={() => setCreateDialogOpen(false)} className="rounded-full">
                取消
              </Button>
              <Button onClick={handleCreateKey} disabled={isCreating} className="rounded-full">
                {isCreating ? "生成中..." : "生成密钥"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {/* 密钥列表 */}
      {apiKeys.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 rounded-[32px] bg-white/50 dark:bg-[#1C1C1E]/50 border border-dashed border-muted-foreground/20 text-center">
          <div className="w-14 h-14 rounded-full bg-muted/30 flex items-center justify-center mb-4">
            <div className="text-2xl">🔑</div>
          </div>
          <p className="text-muted-foreground font-medium">您还没有创建任何API密钥</p>
          <Button
            onClick={() => setCreateDialogOpen(true)}
            className="mt-6 rounded-full"
            variant="secondary"
          >
            <Plus className="mr-2 h-4 w-4" />
            生成第一个密钥
          </Button>
        </div>
      ) : (
        <div className="grid gap-4">
          {apiKeys.map((apiKey) => {
            const isVisible = visibleKeys.has(apiKey.id)
            const isCopied = copiedKeys.has(apiKey.id)

            return (
              <div 
                key={apiKey.id}
                className="group flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-6 rounded-[24px] bg-white dark:bg-[#1C1C1E] shadow-sm border border-black/5 dark:border-white/5 transition-all hover:shadow-md"
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-3 mb-2">
                    <h4 className="font-semibold text-base text-foreground">{apiKey.name}</h4>
                    <span className="px-2 py-0.5 rounded-md bg-muted text-[10px] font-mono text-muted-foreground">
                      {formatDate(apiKey.createdAt)}
                    </span>
                  </div>
                  
                  <div className="flex items-center gap-3 max-w-md">
                    <div className="flex-1 h-10 flex items-center px-3 rounded-lg bg-muted/50 font-mono text-sm text-foreground/80 select-all border border-transparent group-hover:border-border/50 transition-colors">
                      {formatKey(apiKey.keyPrefix, apiKey.fullKey, isVisible)}
                    </div>
                    
                    <div className="flex items-center gap-1">
                      {/* 只有当有完整密钥时才显示切换可见性按钮 */}
                      {apiKey.fullKey && (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-9 w-9 rounded-full hover:bg-muted"
                          onClick={() => toggleKeyVisibility(apiKey.id)}
                          title={isVisible ? "隐藏密钥" : "显示密钥"}
                        >
                          {isVisible ? (
                            <EyeOff className="h-4 w-4 text-muted-foreground" />
                          ) : (
                            <Eye className="h-4 w-4 text-muted-foreground" />
                          )}
                        </Button>
                      )}

                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-9 w-9 rounded-full hover:bg-muted"
                        onClick={() => handleCopyKey(apiKey)}
                        title="复制密钥"
                      >
                        {isCopied ? (
                          <Check className="h-4 w-4 text-green-600" />
                        ) : (
                          <Copy className="h-4 w-4 text-muted-foreground" />
                        )}
                      </Button>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-4 pl-0 sm:pl-6 sm:border-l border-border/40">
                  <div className="hidden sm:block text-xs text-right text-muted-foreground">
                    <div className="mb-1">最后使用</div>
                    <div className="font-medium">{apiKey.lastUsedAt ? formatDate(apiKey.lastUsedAt) : '未使用'}</div>
                  </div>
                  
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-9 w-9 rounded-full text-muted-foreground hover:text-destructive hover:bg-destructive/10"
                    onClick={() => {
                      setSelectedKey(apiKey)
                      setDeleteDialogOpen(true)
                    }}
                    title="删除密钥"
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* 删除确认对话框 */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>删除API密钥</DialogTitle>
            <DialogDescription>
              确定要删除密钥 <strong>{selectedKey?.name}</strong> 吗？
              使用此密钥的应用将无法继续访问API。
              此操作无法撤销。
            </DialogDescription>
          </DialogHeader>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setDeleteDialogOpen(false)
                setSelectedKey(null)
              }}
              disabled={isDeleting}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeleteKey}
              disabled={isDeleting}
            >
              {isDeleting ? "删除中..." : "确认删除"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
