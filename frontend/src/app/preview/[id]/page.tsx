/**
 * 预览页面 - 简化版（符合E2E测试要求）
 *
 * 核心功能：
 * - 三个设备切换按钮（手机/平板/桌面）
 * - 应用信息侧边栏
 * - 页面结构信息
 * - 预览框架
 * - 操作按钮（返回、发布、刷新、分享、下载）
 */
'use client';

import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { useToast } from '@/hooks/use-toast';
import {
  ArrowLeft,
  Download,
  Loader2,
  AlertCircle,
  RefreshCw,
  Share,
  Rocket,
  Smartphone,
  Tablet,
  Monitor,
} from 'lucide-react';

import { cn } from '@/lib/utils';
import { getToken } from '@/lib/auth/token';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { getG3Artifacts, getG3JobStatus } from '@/lib/api/g3';
import type { G3ArtifactSummary, G3JobStatus } from '@/types/g3';
import { G3PreviewPanel } from '@/components/g3/g3-preview-panel';

// 设备类型定义（简化版）
type DeviceType = 'mobile' | 'tablet' | 'desktop';

import { LivePreviewIframe } from '@/components/code-generation/live-preview-iframe';
import type { AppSpec } from '@/lib/api/appspec';
import { getAppSpec } from '@/lib/api/appspec';

/**
 * 预览页使用的 AppSpec 内容结构（从 specContent 解析而来）
 *
 * 说明：
 * - 后端经常以 JSON 字符串返回 `specContent`，若不解析会导致页面数/数据模型为空，进而出现“页面结构渲染异常”。
 * - 这里仅声明预览页需要的最小字段，避免与后端完整 schema 强耦合。
 */
type AppSpecContent = {
  pages?: Array<{ id: string; name: string; path: string; components: string[] }>;
  dataModels?: Array<{ id: string; name: string; fields: string[] }>;
  flows?: unknown[];
};

/**
 * 解析后端返回的 specContent（兼容 JSON 字符串 / 对象）
 */
function parseSpecContent(specContent: AppSpec['specContent']): AppSpecContent | null {
  if (!specContent) return null;

  if (typeof specContent === 'string') {
    try {
      return JSON.parse(specContent) as AppSpecContent;
    } catch (e) {
      console.warn('[PreviewPage] specContent JSON 解析失败:', e);
      return null;
    }
  }

  return specContent as AppSpecContent;
}

/**
 * 规范化 AppSpec：将 specContent 统一为对象，避免渲染层反复判断
 */
function normalizeAppSpec(appSpec: AppSpec): AppSpec {
  const parsed = parseSpecContent(appSpec.specContent);
  if (!parsed) return appSpec;
  return { ...appSpec, specContent: parsed };
}

/**
 * 从 AppSpec.metadata 提取最新的 G3 任务ID
 *
 * 说明：
 * - 预览页通过该任务ID拉取最新产物，用于代码面板展示；
 * - 若缺失则返回 null，面板进入“未启动任务”的空态。
 */
function getLatestG3JobId(metadata?: Record<string, unknown>): string | null {
  if (!metadata) return null;
  const value = metadata['latestGenerationTaskId'];
  if (typeof value !== 'string') return null;
  return value.trim() ? value : null;
}

export default function PreviewPage() {
  const params = useParams();
  const router = useRouter();
  const appSpecId = params.id as string;

  const [appSpec, setAppSpec] = useState<AppSpec | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDevice, setSelectedDevice] = useState<DeviceType>('desktop');
  const [isRefreshing, setIsRefreshing] = useState(false);

  const { toast } = useToast();
  // 最新 G3 任务ID：用于绑定产物列表与运行状态
  const g3JobId = useMemo(() => getLatestG3JobId(appSpec?.metadata), [appSpec?.metadata]);

  // G3 产物预览状态：用于展示最新任务的代码面板
  const [g3Artifacts, setG3Artifacts] = useState<G3ArtifactSummary[]>([]);
  const [g3Status, setG3Status] = useState<G3JobStatus | null>(null);

  /**
   * 刷新 G3 产物列表与任务状态
   *
   * 说明：
   * - 面板首次加载、用户点击刷新时调用；
   * - 仅在存在最新任务ID时触发。
   */
  const refreshG3Artifacts = useCallback(async () => {
    if (!g3JobId) {
      setG3Artifacts([]);
      setG3Status(null);
      return;
    }

    try {
      const [artifactResp, statusResp] = await Promise.all([
        getG3Artifacts(g3JobId),
        getG3JobStatus(g3JobId),
      ]);

      setG3Artifacts(artifactResp.data ?? []);
      if (statusResp.data?.status) {
        setG3Status(statusResp.data.status as G3JobStatus);
      } else {
        setG3Status(null);
      }
    } catch (err) {
      console.error('[PreviewPage] 获取G3产物失败:', err);
      setG3Artifacts([]);
      setG3Status(null);
      toast({
        variant: 'destructive',
        title: '产物加载失败',
        description: '无法获取最新的 G3 产物，请稍后重试',
      });
    }
  }, [g3JobId, toast]);

  /**
   * 计算 G3 是否仍在生成中
   *
   * 说明：
   * - 用于面板显示“生成中”状态与布局切换；
   * - 完成/失败/取消视为非运行中。
   */
  const isG3Running = useMemo(() => {
    if (!g3Status) return false;
    return !['COMPLETED', 'FAILED', 'CANCELLED'].includes(g3Status);
  }, [g3Status]);

  const fetchAppSpec = useCallback(async () => {
    try {
      // Don't set loading to true on refresh to avoid flashing if data exists
      if (!appSpec) setLoading(true);
      setError(null);

      // Handle demo IDs with static data
      if (appSpecId.startsWith('demo-')) {
        await new Promise(resolve => setTimeout(resolve, 500));
        
        const demoData: Record<string, AppSpec> = {
            'demo-signup': {
              id: 'demo-signup',
              version: '1.0.0',
              tenantId: 'demo',
              userId: 'demo-user',
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              userRequirement: 'Demo Signup',
              status: 'published',
              isValid: true,
              qualityScore: 100,
              generatedAt: new Date().toISOString(),
              durationMs: 0,
              specContent: {
                pages: [
                  { id: 'p1', name: '活动列表', path: '/activities', components: ['ActivityList', 'SearchBar'] },
                  { id: 'p2', name: '报名详情', path: '/signup/:id', components: ['DetailView', 'SignupForm'] },
                  { id: 'p3', name: '我的报名', path: '/my-signups', components: ['UserDashboard'] },
                ],
                dataModels: [
                  { id: 'm1', name: 'Activity', fields: ['title', 'date', 'location'] },
                  { id: 'm2', name: 'Registration', fields: ['userId', 'activityId', 'status'] },
                ],
                flows: [],
              },
              planResult: { modules: [], complexityScore: 0, reasoning: '', suggestedTechStack: [], estimatedHours: 0, recommendations: '' },
              validateResult: { isValid: true, qualityScore: 100, issues: [], suggestions: [] },
            },
            'demo-survey': {
              id: 'demo-survey',
              version: '1.0.0',
              tenantId: 'demo',
              userId: 'demo-user',
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              userRequirement: 'Demo Survey',
              status: 'published',
              isValid: true,
              qualityScore: 100,
              generatedAt: new Date().toISOString(),
              durationMs: 0,
              specContent: {
                pages: [
                  { id: 'p1', name: '问卷列表', path: '/surveys', components: ['SurveyList'] },
                  { id: 'p2', name: '填写问卷', path: '/fill/:id', components: ['Questionnaire'] },
                  { id: 'p3', name: '结果统计', path: '/stats/:id', components: ['ChartDashboard'] },
                ],
                dataModels: [
                  { id: 'm1', name: 'Survey', fields: ['title', 'description', 'questions'] },
                  { id: 'm2', name: 'Response', fields: ['surveyId', 'answers'] },
                ],
                flows: [],
              },
              planResult: { modules: [], complexityScore: 0, reasoning: '', suggestedTechStack: [], estimatedHours: 0, recommendations: '' },
              validateResult: { isValid: true, qualityScore: 100, issues: [], suggestions: [] },
            },
            'demo-shop': {
              id: 'demo-shop',
              version: '1.0.0',
              tenantId: 'demo',
              userId: 'demo-user',
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              userRequirement: 'Demo Shop',
              status: 'published',
              isValid: true,
              qualityScore: 100,
              generatedAt: new Date().toISOString(),
              durationMs: 0,
              specContent: {
                 pages: [
                { id: 'p1', name: '商品首页', path: '/home', components: ['Banner', 'ProductGrid'] },
                { id: 'p2', name: '商品详情', path: '/product/:id', components: ['ProductDetail', 'AddToCart'] },
                { id: 'p3', name: '购物车', path: '/cart', components: ['CartList', 'Checkout'] },
              ],
              dataModels: [
                { id: 'm1', name: 'Product', fields: ['name', 'price', 'stock', 'image'] },
                { id: 'm2', name: 'Order', fields: ['userId', 'items', 'total', 'status'] },
              ],
              flows: [],
              },
              planResult: { modules: [], complexityScore: 0, reasoning: '', suggestedTechStack: [], estimatedHours: 0, recommendations: '' },
              validateResult: { isValid: true, qualityScore: 100, issues: [], suggestions: [] },
            },
        };

        const data = demoData[appSpecId];
        if (data) {
          setAppSpec(data);
          return;
        }
      }

      // 调用真实API获取AppSpec数据
      const response = await getAppSpec(appSpecId);

      if (response.success && response.data) {
        setAppSpec(normalizeAppSpec(response.data));
      } else {
        // Only throw if we don't have data yet (on initial load)
        if (!appSpec) throw new Error(response.error || '获取AppSpec失败');
      }
    } catch (err) {
      console.error('Failed to fetch AppSpec:', err);
      if (!appSpec) setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setLoading(false);
    }
  }, [appSpecId, appSpec]);

  // Initial load
  useEffect(() => {
    fetchAppSpec();
  }, [fetchAppSpec]);

  // 当 AppSpec 变化时同步刷新 G3 产物
  useEffect(() => {
    void refreshG3Artifacts();
  }, [refreshG3Artifacts]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await fetchAppSpec();
    await refreshG3Artifacts();
    setIsRefreshing(false);
    toast({
      title: '刷新成功',
      description: '预览内容已更新',
    });
  };


  const handlePublish = () => {
    // 直接导航到发布页面，不弹窗
    router.push(`/publish/${appSpecId}`);
  };

  const handleDownload = async () => {
    try {
      toast({
        title: '正在打包下载...',
        description: '请稍候，正在生成ZIP文件',
      });

      const token = getToken();
      const baseUrl = getApiBaseUrl();
      const url = `${baseUrl}/v1/timemachine/appspec/${appSpecId}/download-latest`;

      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Authorization': token || '',
        },
      });

      if (!response.ok) {
        // Try to read error message
        const text = await response.text();
        console.error('Download failed:', text);
        throw new Error(response.statusText || '下载失败');
      }

      const blob = await response.blob();
      const downloadUrl = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = downloadUrl;
      // Get filename from header if possible, or use default
      const disposition = response.headers.get('Content-Disposition');
      let filename = `ingenio-app-${appSpecId}.zip`;
      if (disposition && disposition.indexOf('attachment') !== -1) {
          const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
          const matches = filenameRegex.exec(disposition);
          if (matches != null && matches[1]) {
              filename = matches[1].replace(/['"]/g, '');
          }
      }
      
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(downloadUrl);
      document.body.removeChild(a);

      toast({
        title: '下载成功',
        description: '文件已开始下载',
      });
    } catch (e) {
      console.error(e);
      toast({
        variant: "destructive",
        title: '下载失败',
        description: '无法下载应用代码，请稍后重试',
      });
    }
  };

  const handleShare = () => {
    navigator.clipboard.writeText(window.location.href);
    toast({
      title: '分享链接已复制',
      description: '已复制到剪贴板',
    });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="text-center">
          <Loader2 className="w-8 h-8 animate-spin mx-auto mb-4 text-primary" />
          <p className="text-muted-foreground">正在加载预览...</p>
        </div>
      </div>
    );
  }

  if (error || !appSpec) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <Card className="max-w-md w-full mx-4">
          <CardContent className="pt-6">
            <div className="text-center">
              <AlertCircle className="w-12 h-12 text-destructive mx-auto mb-4" />
              <h3 className="text-lg font-semibold mb-2">加载失败</h3>
              <p className="text-muted-foreground mb-4">{error || '未找到应用规格'}</p>
              <Button onClick={() => router.back()} variant="outline">
                <ArrowLeft className="w-4 h-4 mr-2" />
                返回
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  // 安全获取页面/数据模型：优先使用解析后的 specContent，其次兼容旧字段（极少数历史数据）
  const getPages = (): NonNullable<AppSpecContent['pages']> => {
    if (!appSpec) return [];
    const content = parseSpecContent(appSpec.specContent);
    if (Array.isArray(content?.pages)) return content.pages;
    const legacy = appSpec as unknown as { pages?: AppSpecContent['pages'] };
    return Array.isArray(legacy.pages) ? legacy.pages : [];
  };

  const getDataModels = (): NonNullable<AppSpecContent['dataModels']> => {
    if (!appSpec) return [];
    const content = parseSpecContent(appSpec.specContent);
    if (Array.isArray(content?.dataModels)) return content.dataModels;
    const legacy = appSpec as unknown as { dataModels?: AppSpecContent['dataModels'] };
    return Array.isArray(legacy.dataModels) ? legacy.dataModels : [];
  };

  const pages = getPages();
  const dataModels = getDataModels();

  // 应用内容（被预览框架包裹的部分）
  // 如果有真实预览URL，则显示iframe，否则显示原来的mock内容
  const appContent = appSpec.frontendPrototypeUrl ? (
    <LivePreviewIframe 
       previewUrl={appSpec.frontendPrototypeUrl}
       isGenerating={false}
       showDeviceSwitcher={false} // Switcher is handled by parent
       showRefreshButton={false}
       className="w-full h-full border-0"
       initialDevice={selectedDevice === 'mobile' ? 'mobile' : selectedDevice === 'tablet' ? 'tablet' : 'desktop'}
    />
  ) : (
    <div className="h-full flex flex-col bg-background">
      {/* 模拟Header */}
      <header className="bg-primary text-primary-foreground p-4">
        <div className="flex items-center justify-between">
          <h2 className="font-semibold">生成的应用</h2>
          <nav className="flex gap-4">
            <span className="text-sm">首页</span>
            <span className="text-sm">问卷</span>
          </nav>
        </div>
      </header>

      {/* 模拟内容区域 */}
      <main className="flex-1 p-6 overflow-auto bg-background">
        <div className="space-y-6">
          <section className="text-center py-12 bg-gradient-to-r from-primary/10 to-secondary/10 rounded-lg">
            <h3 className="text-2xl font-bold mb-4">欢迎使用生成的应用</h3>
            <p className="text-muted-foreground mb-6">这是一个基于AppSpec生成的应用预览</p>
            <Button>开始使用</Button>
          </section>

          <section className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {pages.length > 0 ? (
              pages.map((page) => (
                <div key={page.id} className="p-4 border rounded-lg">
                  <h4 className="font-semibold mb-2">{page.name}</h4>
                  <p className="text-sm text-muted-foreground">路径: {page.path}</p>
                  <p className="text-sm text-muted-foreground">
                    组件: {page.components.join(', ')}
                  </p>
                </div>
              ))
            ) : (
              <div className="col-span-full p-6 border rounded-lg text-center text-sm text-muted-foreground">
                暂无页面结构数据（可能是 AppSpec 的 specContent 为空或解析失败）
              </div>
            )}
          </section>
        </div>
      </main>

      {/* 模拟Footer */}
      <footer className="bg-muted p-4 text-center text-sm text-muted-foreground">
        <p>© 2024 生成的应用 | 由 Ingenio 妙构 驱动</p>
      </footer>
    </div>
  );

  return (
    <div className="h-screen flex flex-col bg-background">
      {/* 顶部导航栏 */}
      <header className="border-b border-border bg-background z-10">
        <div className="px-4 sm:px-6">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-4">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => router.back()}
              >
                <ArrowLeft className="w-4 h-4 mr-2" />
                返回
              </Button>
              <Separator orientation="vertical" className="h-6" />
              <div>
                <h1 className="text-lg font-semibold">应用预览</h1>
                <p className="text-sm text-muted-foreground">{appSpecId}</p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {/* 操作按钮 */}
              <Button
                variant="outline"
                size="sm"
                onClick={handleRefresh}
                disabled={isRefreshing}
              >
                <RefreshCw className={cn('w-4 h-4 mr-2', isRefreshing && 'animate-spin')} />
                刷新
              </Button>

              <Button
                variant="outline"
                size="sm"
                onClick={handleShare}
              >
                <Share className="w-4 h-4 mr-2" />
                分享
              </Button>

              <Button
                variant="outline"
                size="sm"
                onClick={handleDownload}
              >
                <Download className="w-4 h-4 mr-2" />
                下载
              </Button>

              <Button
                size="sm"
                onClick={handlePublish}
                className="bg-gradient-to-r from-purple-600 to-pink-600 hover:from-purple-700 hover:to-pink-700"
              >
                <Rocket className="w-4 h-4 mr-2" />
                发布
              </Button>
            </div>
          </div>
        </div>
      </header>

      {/* 主要内容区域 */}
      <main className="flex-1 overflow-hidden">
        <div className="flex h-full flex-col gap-4 p-4 overflow-auto">
          <div className="grid grid-cols-12 gap-4">
            {/* 左侧：应用信息侧边栏 */}
            <div className="col-span-3">
              <Card className="h-full">
                <CardContent className="pt-6">
                  <h3 className="font-semibold mb-4">应用信息</h3>
                  <div className="space-y-3 text-sm">
                    <div>
                      <span className="text-muted-foreground">版本:</span>
                      <span className="ml-2 font-medium">{appSpec.version}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">创建时间:</span>
                      <span className="ml-2 font-medium">
                        {new Date(appSpec.createdAt).toLocaleString('zh-CN')}
                      </span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">更新时间:</span>
                      <span className="ml-2 font-medium">
                        {new Date(appSpec.updatedAt).toLocaleString('zh-CN')}
                      </span>
                    </div>
                    <Separator />
                    <div>
                      <span className="text-muted-foreground">页面数:</span>
                      <span className="ml-2 font-medium">{pages.length}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">数据模型:</span>
                      <span className="ml-2 font-medium">{dataModels.length}</span>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>

            {/* 中间：预览框架 */}
            <div className="col-span-6 flex flex-col">
              {/* 设备切换按钮 */}
              <div className="mb-4 flex justify-center gap-2">
                <Button
                  variant={selectedDevice === 'mobile' ? 'default' : 'outline'}
                  onClick={() => setSelectedDevice('mobile')}
                  className="gap-2"
                >
                  <Smartphone className="w-4 h-4" />
                  手机
                </Button>
                <Button
                  variant={selectedDevice === 'tablet' ? 'default' : 'outline'}
                  onClick={() => setSelectedDevice('tablet')}
                  className="gap-2"
                >
                  <Tablet className="w-4 h-4" />
                  平板
                </Button>
                <Button
                  variant={selectedDevice === 'desktop' ? 'default' : 'outline'}
                  onClick={() => setSelectedDevice('desktop')}
                  className="gap-2"
                >
                  <Monitor className="w-4 h-4" />
                  桌面
                </Button>
              </div>

              {/* 预览容器 */}
              <div className={cn(
                 "flex-1 flex justify-center overflow-auto",
                 // Only align items-start if we have a fixed height container, otherwise stretch
                 appSpec.frontendPrototypeUrl ? "items-stretch" : "items-start"
              )}>
                <div
                  data-device-preview
                  className={cn(
                    'border rounded-lg overflow-hidden transition-all shadow-lg bg-white',
                    'h-[600px]', // 固定高度
                    {
                      'w-[375px]': selectedDevice === 'mobile',
                      'w-[768px]': selectedDevice === 'tablet',
                      'w-full max-w-[1200px]': selectedDevice === 'desktop',
                      // 如果是真实预览，允许在桌面模式下更灵活
                      'h-[800px]': !!appSpec.frontendPrototypeUrl && selectedDevice === 'desktop'
                    }
                  )}
                >
                  {appContent}
                </div>
              </div>
            </div>

            {/* 右侧：页面结构信息 */}
            <div className="col-span-3">
              <Card className="h-full">
                <CardContent className="pt-6">
                  <h3 className="font-semibold mb-4">页面结构</h3>
                  <div className="space-y-2">
                    {pages.length > 0 ? (
                      pages.map((page) => (
                        <div
                          key={page.id}
                          data-page-item
                          className="p-3 border rounded-lg hover:bg-muted/50 transition-colors cursor-pointer"
                        >
                          <div className="font-medium text-sm mb-1">{page.name}</div>
                          <div className="text-xs text-muted-foreground mb-2">{page.path}</div>
                          <div className="text-xs text-muted-foreground">
                            {page.components.length} 个组件
                          </div>
                        </div>
                      ))
                    ) : (
                      <div className="p-3 border rounded-lg text-xs text-muted-foreground">
                        暂无页面结构
                      </div>
                    )}
                  </div>

                  <Separator className="my-4" />

                  <div>
                    <h4 className="font-semibold text-sm mb-2">数据模型</h4>
                    <div className="space-y-1">
                      {dataModels.length > 0 ? (
                        dataModels.map((model) => (
                          <div key={model.id} className="text-xs text-muted-foreground">
                            • {model.name} ({model.fields.length} 字段)
                          </div>
                        ))
                      ) : (
                        <div className="text-xs text-muted-foreground">暂无数据模型</div>
                      )}
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>

          {/* G3 产物/代码预览面板（全宽） */}
          <div className="h-[520px]">
            <G3PreviewPanel
              jobId={g3JobId}
              artifacts={g3Artifacts}
              isRunning={isG3Running}
              onRefreshArtifacts={refreshG3Artifacts}
              className="h-full"
            />
          </div>
        </div>
      </main>
    </div>
  );
}
