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

import { useState, useEffect, useCallback } from 'react';
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

// 设备类型定义（简化版）
type DeviceType = 'mobile' | 'tablet' | 'desktop';

import { LivePreviewIframe } from '@/components/code-generation/live-preview-iframe';
import type { AppSpec } from '@/lib/api/appspec';
import { getAppSpec } from '@/lib/api/appspec';


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
        setAppSpec(response.data);
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

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await fetchAppSpec();
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

  const handleDownload = () => {
    toast({
      title: '下载功能',
      description: '准备下载应用代码',
    });
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

  // Helper to safely access pages from specContent or older flat structure
  const getPages = () => {
     if (!appSpec) return [];
     // @ts-expect-error - Handle legacy structure if needed, or structured specContent
     return (appSpec.specContent?.pages || appSpec.pages || []) as Array<{ id: string; name: string; path: string; components: string[] }>;
  };
  
   const getDataModels = () => {
     if (!appSpec) return [];
     // @ts-expect-error - Handle legacy structure if needed, or structured specContent
     return (appSpec.specContent?.dataModels || appSpec.dataModels || []) as Array<{ id: string; name: string; fields: string[] }>;
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
            {pages.map((page) => (
              <div key={page.id} className="p-4 border rounded-lg">
                <h4 className="font-semibold mb-2">{page.name}</h4>
                <p className="text-sm text-muted-foreground">路径: {page.path}</p>
                <p className="text-sm text-muted-foreground">
                  组件: {page.components.join(', ')}
                </p>
              </div>
            ))}
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
        <div className="grid grid-cols-12 gap-4 h-full p-4">
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
                  {pages.map((page) => (
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
                  ))}
                </div>

                <Separator className="my-4" />

                <div>
                  <h4 className="font-semibold text-sm mb-2">数据模型</h4>
                  <div className="space-y-1">
                    {dataModels.map((model) => (
                      <div key={model.id} className="text-xs text-muted-foreground">
                        • {model.name} ({model.fields.length} 字段)
                      </div>
                    ))}
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </main>
    </div>
  );
}
