/**
 * 发布页面
 * 用于将生成的AppSpec应用发布到各种平台
 * 基于整体方案.md中的"发布（最小可用形态）"需求设计
 */
'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Separator } from '@/components/ui/separator';
import {
  ArrowLeft,
  Rocket,
  Globe,
  Smartphone,
  CheckCircle,
  AlertCircle,
  Loader2,
  ExternalLink,
  Copy,
  Settings,
  Zap,
  Shield
} from 'lucide-react';

// 发布平台类型（与后端API一致）
type PublishPlatform = 'android' | 'ios' | 'h5' | 'miniapp';

// 发布状态类型
type PublishStatus = 'draft' | 'building' | 'deploying' | 'published' | 'failed';

// AppSpec数据结构（临时定义，等待后端接口）
interface AppSpec {
  id: string;
  version: string;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
  pages: Array<{
    id: string;
    name: string;
    path: string;
    components: string[];
  }>;
  dataModels: Array<{
    id: string;
    name: string;
    fields: string[];
  }>;
}

// 自定义设置接口
interface CustomSettings {
  [key: string]: string | number | boolean;
}

// 发布配置接口
interface PublishConfig {
  platform: PublishPlatform;
  domain?: string;
  name: string;
  description: string;
  enableAnalytics: boolean;
  enableAuth: boolean;
  customSettings: CustomSettings;
}

export default function PublishPage() {
  const params = useParams();
  const router = useRouter();
  const appSpecId = params.id as string;

  const [appSpec, setAppSpec] = useState<AppSpec | null>(null);
  const [loading, setLoading] = useState(true);
  const [publishing, setPublishing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [publishStatus, setPublishStatus] = useState<PublishStatus>('draft');
  const [publishedUrl, setPublishedUrl] = useState<string | null>(null);

  // 发布配置表单
  const [config, setConfig] = useState<PublishConfig>({
    platform: 'h5',
    domain: '',
    name: '',
    description: '',
    enableAnalytics: true,
    enableAuth: false,
    customSettings: {}
  });

  // 发布平台配置
  const platforms = {
    android: {
      icon: Smartphone,
      label: 'Android应用',
      description: '打包为Android APK应用',
      features: ['原生体验', '离线支持', '推送通知'],
      defaultDomain: null
    },
    ios: {
      icon: Smartphone,
      label: 'iOS应用',
      description: '打包为iOS IPA应用',
      features: ['原生体验', '离线支持', '推送通知'],
      defaultDomain: null
    },
    h5: {
      icon: Globe,
      label: 'H5应用',
      description: '发布为响应式网站，支持桌面和移动端',
      features: ['HTTPS自动配置', 'CDN加速', '自定义域名'],
      defaultDomain: `${appSpecId}.app.ingenio.dev`
    },
    miniapp: {
      icon: Smartphone,
      label: '小程序',
      description: '打包为微信/支付宝小程序',
      features: ['小程序体验', '快速加载', '微信生态'],
      defaultDomain: null
    }
  };

  useEffect(() => {
    if (!appSpecId) return;

    const fetchAppSpec = async () => {
      try {
        setLoading(true);
        setError(null);

        // 调用真实API获取AppSpec数据
        const { getAppSpec } = await import('@/lib/api/appspec');
        const response = await getAppSpec(appSpecId);

        if (response.success && response.data) {
          const backendAppSpec = response.data;

          // 解析specContent JSON字符串
          let parsedContent: {
            pages?: Array<{ id: string; name: string; path: string; components: string[] }>;
            dataModels?: Array<{ id: string; name: string; fields: string[] }>;
          } = {};

          try {
            parsedContent = typeof backendAppSpec.specContent === 'string'
              ? JSON.parse(backendAppSpec.specContent)
              : backendAppSpec.specContent || {};
          } catch (parseErr) {
            console.warn('Failed to parse specContent:', parseErr);
          }

          const appSpecData: AppSpec = {
            id: backendAppSpec.id,
            version: backendAppSpec.version || '1.0.0',
            tenantId: backendAppSpec.tenantId,
            createdAt: backendAppSpec.createdAt,
            updatedAt: backendAppSpec.updatedAt,
            pages: parsedContent.pages || [],
            dataModels: parsedContent.dataModels || [],
          };

          setAppSpec(appSpecData);

          // 初始化发布配置
          setConfig(prev => ({
            ...prev,
            name: `${parsedContent.pages?.[0]?.name || '应用'} - ${backendAppSpec.version || '1.0.0'}`,
            description: `基于秒构AI生成的应用，包含${parsedContent.pages?.length || 0}个页面`,
            domain: platforms.h5.defaultDomain
          }));
        } else {
          throw new Error(response.error || '获取AppSpec失败');
        }
      } catch (err) {
        console.error('Failed to fetch AppSpec:', err);
        setError(err instanceof Error ? err.message : '加载失败');
      } finally {
        setLoading(false);
      }
    };

    fetchAppSpec();
  }, [appSpecId]);

  const handlePublish = async () => {
    if (!appSpec) return;

    try {
      setPublishing(true);
      setPublishStatus('building');
      setError(null);

      // 导入发布API
      const { createPublishTask, getBuildStatus } = await import('@/lib/api/publish');

      // 构建发布请求参数
      const platformType = config.platform;
      const publishRequest = {
        projectId: appSpec.id,
        platforms: [platformType],
        platformConfigs: {
          [platformType]: {
            appName: config.name,
            description: config.description,
            ...(config.platform === 'h5' && { domain: config.domain || platforms.h5.defaultDomain }),
          }
        },
        parallelBuild: true,
        priority: 'NORMAL' as const,
      };

      // 调用真实API创建发布任务
      const publishResponse = await createPublishTask(publishRequest);
      const buildId = publishResponse.buildId;

      // 轮询构建状态
      const pollBuildStatus = async () => {
        try {
          const statusResponse = await getBuildStatus(buildId);

          // 更新发布状态
          if (statusResponse.status === 'IN_PROGRESS') {
            setPublishStatus('building');
            // 继续轮询
            setTimeout(pollBuildStatus, 2000);
          } else if (statusResponse.status === 'SUCCESS') {
            setPublishStatus('published');

            // 获取下载URL
            const platformResult = statusResponse.platformResults[platformType];
            const downloadUrl = platformResult?.downloadUrl;

            if (downloadUrl) {
              setPublishedUrl(downloadUrl);
            } else {
              // 如果没有下载URL，使用默认URL
              const url = config.platform === 'h5'
                ? `https://${config.domain || platforms.h5.defaultDomain}`
                : `https://app.ingenio.dev/${appSpecId}`;
              setPublishedUrl(url);
            }

            setPublishing(false);
          } else if (statusResponse.status === 'FAILED') {
            setPublishStatus('failed');
            const platformResult = statusResponse.platformResults[platformType];
            setError(platformResult?.errorMessage || '发布失败');
            setPublishing(false);
          }
        } catch (pollErr) {
          console.error('Failed to poll build status:', pollErr);
          setPublishStatus('failed');
          setError(pollErr instanceof Error ? pollErr.message : '查询构建状态失败');
          setPublishing(false);
        }
      };

      // 开始轮询
      setTimeout(pollBuildStatus, 2000);

    } catch (err) {
      console.error('Failed to publish:', err);
      setPublishStatus('failed');
      setError(err instanceof Error ? err.message : '发布失败');
      setPublishing(false);
    }
  };

  const handleCopyUrl = () => {
    if (publishedUrl) {
      navigator.clipboard.writeText(publishedUrl);
      // TODO: 添加Toast提示
    }
  };

  const handlePreview = () => {
    router.push(`/preview/${appSpecId}`);
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <Loader2 className="w-8 h-8 animate-spin mx-auto mb-4 text-primary" />
          <p className="text-gray-600">正在加载应用信息...</p>
        </div>
      </div>
    );
  }

  if (error || !appSpec) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <Card className="max-w-md w-full mx-4">
          <CardContent className="pt-6">
            <div className="text-center">
              <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
              <h3 className="text-lg font-semibold mb-2">加载失败</h3>
              <p className="text-gray-600 mb-4">{error || '未找到应用规格'}</p>
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

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 顶部导航栏 */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
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
              <div>
                <h1 className="text-lg font-semibold">发布应用</h1>
                <p className="text-sm text-gray-500">
                  {appSpec.pages.length} 个页面 · 版本 {appSpec.version}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={handlePreview}
              >
                <ExternalLink className="w-4 h-4 mr-2" />
                预览
              </Button>

              {publishStatus === 'published' && publishedUrl && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleCopyUrl}
                >
                  <Copy className="w-4 h-4 mr-2" />
                  复制链接
                </Button>
              )}
            </div>
          </div>
        </div>
      </header>

      {/* 主要内容区域 */}
      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* 发布状态指示器 */}
        {publishStatus !== 'draft' && (
          <Card className="mb-8">
            <CardContent className="pt-6">
              <div className="flex items-center gap-4">
                {publishStatus === 'building' && (
                  <>
                    <Loader2 className="w-6 h-6 animate-spin text-blue-500" />
                    <div>
                      <p className="font-medium">正在构建应用...</p>
                      <p className="text-sm text-gray-500">正在编译代码和优化资源</p>
                    </div>
                  </>
                )}
                {publishStatus === 'deploying' && (
                  <>
                    <Rocket className="w-6 h-6 text-orange-500" />
                    <div>
                      <p className="font-medium">正在部署到服务器...</p>
                      <p className="text-sm text-gray-500">正在将应用部署到云端</p>
                    </div>
                  </>
                )}
                {publishStatus === 'published' && (
                  <>
                    <CheckCircle className="w-6 h-6 text-green-500" />
                    <div className="flex-1">
                      <p className="font-medium">发布成功！</p>
                      <p className="text-sm text-gray-500">
                        应用已成功发布到：
                        <a
                          href={publishedUrl || '#'}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-blue-600 hover:underline ml-1"
                        >
                          {publishedUrl}
                        </a>
                      </p>
                    </div>
                    <Button onClick={handleCopyUrl} size="sm">
                      <Copy className="w-4 h-4 mr-2" />
                      访问应用
                    </Button>
                  </>
                )}
                {publishStatus === 'failed' && (
                  <>
                    <AlertCircle className="w-6 h-6 text-red-500" />
                    <div>
                      <p className="font-medium">发布失败</p>
                      <p className="text-sm text-gray-500">{error}</p>
                    </div>
                  </>
                )}
              </div>
            </CardContent>
          </Card>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* 左侧配置面板 */}
          <div className="lg:col-span-2 space-y-6">
            {/* 平台选择 */}
            <Card>
              <CardHeader>
                <CardTitle>选择发布平台</CardTitle>
                <CardDescription>
                  选择目标平台，我们将为您优化配置
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {Object.entries(platforms).map(([key, platform]) => {
                  const Icon = platform.icon;
                  const isSelected = config.platform === key;

                  return (
                    <div
                      key={key}
                      data-platform={key}
                      className={`p-4 border rounded-lg cursor-pointer transition-colors ${
                        isSelected
                          ? 'border-primary bg-primary/5'
                          : 'border-gray-200 hover:border-gray-300'
                      }`}
                      onClick={() => setConfig(prev => ({ ...prev, platform: key as PublishPlatform }))}
                    >
                      <div className="flex items-start gap-3">
                        <Icon className={`w-6 h-6 mt-1 ${isSelected ? 'text-primary' : 'text-gray-500'}`} />
                        <div className="flex-1">
                          <h3 className="font-medium mb-1">{platform.label}</h3>
                          <p className="text-sm text-gray-600 mb-3">{platform.description}</p>
                          <div className="flex flex-wrap gap-2">
                            {platform.features.map((feature) => (
                              <Badge key={feature} variant="secondary" className="text-xs">
                                {feature}
                              </Badge>
                            ))}
                          </div>
                        </div>
                        {isSelected && (
                          <CheckCircle className="w-5 h-5 text-primary" />
                        )}
                      </div>
                    </div>
                  );
                })}
              </CardContent>
            </Card>

            {/* 基础配置 */}
            <Card>
              <CardHeader>
                <CardTitle>基础配置</CardTitle>
                <CardDescription>
                  配置应用的基本信息
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <label htmlFor="app-name" className="block text-sm font-medium mb-2">应用名称</label>
                  <Input
                    id="app-name"
                    value={config.name}
                    onChange={(e) => setConfig(prev => ({ ...prev, name: e.target.value }))}
                    placeholder="输入应用名称"
                  />
                </div>

                <div>
                  <label htmlFor="app-description" className="block text-sm font-medium mb-2">应用描述</label>
                  <Textarea
                    id="app-description"
                    value={config.description}
                    onChange={(e) => setConfig(prev => ({ ...prev, description: e.target.value }))}
                    placeholder="描述应用的功能和用途"
                    rows={3}
                  />
                </div>

                {config.platform === 'h5' && (
                  <div>
                    <label htmlFor="custom-domain" className="block text-sm font-medium mb-2">自定义域名（可选）</label>
                    <Input
                      id="custom-domain"
                      value={config.domain}
                      onChange={(e) => setConfig(prev => ({ ...prev, domain: e.target.value }))}
                      placeholder={platforms.h5.defaultDomain}
                    />
                    <p className="text-xs text-gray-500 mt-1">
                      留空将使用默认域名：{platforms.h5.defaultDomain}
                    </p>
                  </div>
                )}
              </CardContent>
            </Card>

            {/* 高级选项 */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Settings className="w-5 h-5" />
                  高级选项
                </CardTitle>
                <CardDescription>
                  根据需要配置高级功能
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">启用数据分析</p>
                    <p className="text-sm text-gray-500">收集用户使用数据，优化应用体验</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={config.enableAnalytics}
                    onChange={(e) => setConfig(prev => ({ ...prev, enableAnalytics: e.target.checked }))}
                    className="w-4 h-4 text-primary"
                  />
                </div>

                <Separator />

                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">启用用户认证</p>
                    <p className="text-sm text-gray-500">要求用户登录后才能访问应用</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={config.enableAuth}
                    onChange={(e) => setConfig(prev => ({ ...prev, enableAuth: e.target.checked }))}
                    className="w-4 h-4 text-primary"
                  />
                </div>

                <Separator />

                <div>
                  <p className="font-medium mb-2">自定义设置</p>
                  <Textarea
                    placeholder="JSON格式的自定义配置（可选）"
                    rows={4}
                    className="font-mono text-sm"
                  />
                </div>
              </CardContent>
            </Card>
          </div>

          {/* 右侧信息面板 */}
          <div className="space-y-6">
            {/* 应用信息 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">应用信息</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <p className="text-sm font-medium text-gray-900">应用ID</p>
                  <p className="text-sm text-gray-500 font-mono">{appSpec.id}</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-900">页面数量</p>
                  <p className="text-sm text-gray-500">{appSpec.pages.length} 个页面</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-900">数据模型</p>
                  <p className="text-sm text-gray-500">{appSpec.dataModels.length} 个模型</p>
                </div>
                <div>
                  <p className="text-sm font-medium text-gray-900">最后更新</p>
                  <p className="text-sm text-gray-500">
                    {new Date(appSpec.updatedAt).toLocaleString('zh-CN')}
                  </p>
                </div>
              </CardContent>
            </Card>

            {/* 发布计划对比 */}
            <Card>
              <CardHeader>
                <CardTitle className="text-base">发布计划</CardTitle>
                <CardDescription>
                  当前为免费发布版本
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="p-3 border rounded-lg bg-green-50 border-green-200">
                  <div className="flex items-center gap-2 mb-2">
                    <Zap className="w-4 h-4 text-green-600" />
                    <span className="font-medium text-green-900">免费版</span>
                  </div>
                  <ul className="text-sm text-green-800 space-y-1">
                    <li>✓ 基础托管</li>
                    <li>✓ 域名支持</li>
                    <li>✓ SSL证书</li>
                    <li>✓ 基础分析</li>
                  </ul>
                </div>

                <div className="p-3 border rounded-lg opacity-60">
                  <div className="flex items-center gap-2 mb-2">
                    <Shield className="w-4 h-4 text-gray-600" />
                    <span className="font-medium text-gray-900">专业版（即将推出）</span>
                  </div>
                  <ul className="text-sm text-gray-600 space-y-1">
                    <li>• 自定义域名</li>
                    <li>• 高级分析</li>
                    <li>• 优先支持</li>
                    <li>• 团队协作</li>
                  </ul>
                </div>
              </CardContent>
            </Card>

            {/* 发布按钮 */}
            <Card>
              <CardContent className="pt-6">
                <Button
                  onClick={handlePublish}
                  disabled={publishing || !config.name.trim()}
                  className="w-full"
                  size="lg"
                >
                  {publishing ? (
                    <>
                      <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                      {publishStatus === 'building' && '构建中...'}
                      {publishStatus === 'deploying' && '部署中...'}
                    </>
                  ) : (
                    <>
                      <Rocket className="w-4 h-4 mr-2" />
                      立即发布
                    </>
                  )}
                </Button>

                {!config.name.trim() && (
                  <p className="text-xs text-red-600 mt-2">
                    请填写应用名称后再发布
                  </p>
                )}

                {publishStatus === 'published' && (
                  <div className="mt-4 p-3 bg-green-50 rounded-lg">
                    <div className="flex items-center gap-2 text-green-800">
                      <CheckCircle className="w-4 h-4" />
                      <span className="font-medium">发布成功！</span>
                    </div>
                    <p className="text-sm text-green-700 mt-1">
                      您的应用现在可以通过以下地址访问：
                    </p>
                    <a
                      href={publishedUrl || '#'}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-sm text-blue-600 hover:underline break-all"
                    >
                      {publishedUrl}
                    </a>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </main>
    </div>
  );
}
