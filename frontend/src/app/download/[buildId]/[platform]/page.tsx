/**
 * 应用下载页面
 *
 * 功能：
 * - 展示平台构建产物下载链接
 * - 显示二维码供移动端扫码下载
 * - 提供直接下载按钮
 * - 显示应用信息和版本详情
 *
 * 路由：/download/[buildId]/[platform]
 */

'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import Image from 'next/image';
import { Download, Share2, CheckCircle2, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { useToast } from '@/hooks/use-toast';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { getDownloadUrl, getBuildStatus, type PublishResponse, type PlatformType, type PlatformBuildResult } from '@/lib/api/publish';

/**
 * 平台信息配置
 */
const PLATFORM_INFO: Record<string, {
  name: string;
  icon: string;
  color: string;
  fileExt: string;
  description: string;
}> = {
  android: {
    name: 'Android',
    icon: '🤖',
    color: 'bg-green-500',
    fileExt: '.apk',
    description: 'Android 应用安装包，支持 Android 5.0+',
  },
  ios: {
    name: 'iOS',
    icon: '🍎',
    color: 'bg-blue-500',
    fileExt: '.ipa',
    description: 'iOS 应用安装包，支持 iOS 13.0+',
  },
  h5: {
    name: 'H5网页',
    icon: '🌐',
    color: 'bg-purple-500',
    fileExt: '.zip',
    description: '移动端Web应用，直接访问即可使用',
  },
  miniapp: {
    name: '小程序',
    icon: '📱',
    color: 'bg-yellow-500',
    fileExt: '.zip',
    description: '微信/支付宝/字节跳动小程序代码包',
  },
  desktop: {
    name: '桌面端',
    icon: '💻',
    color: 'bg-gray-500',
    fileExt: '.exe/.dmg',
    description: 'Windows/macOS/Linux 桌面应用',
  },
};

export default function DownloadPage() {
  const params = useParams();
  const { toast } = useToast();
  const buildId = params.buildId as string;
  const platform = params.platform as PlatformType;

  const [buildStatus, setBuildStatus] = useState<PublishResponse | null>(null);
  const [downloadUrl, setDownloadUrl] = useState<string>('');
  const [qrCodeUrl, setQrCodeUrl] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [copying, setCopying] = useState(false);

  const platformInfo = PLATFORM_INFO[platform] || {
    name: platform,
    icon: '📦',
    color: 'bg-gray-500',
    fileExt: '',
    description: '应用安装包',
  };

  useEffect(() => {
    fetchBuildInfo();
  }, [buildId, platform]);

  /**
   * 获取构建信息
   */
  const fetchBuildInfo = async () => {
    try {
      setLoading(true);

      // 获取构建状态
      const status = await getBuildStatus(buildId);
      setBuildStatus(status);

      // 获取下载链接
      const url = await getDownloadUrl(buildId, platform);
      setDownloadUrl(url);

      // 构造二维码图片URL
      const baseUrl = getApiBaseUrl();
      const qrUrl = `${baseUrl}/v1/publish/qrcode/${buildId}/${platform}`;
      setQrCodeUrl(qrUrl);

    } catch (err) {
      toast({
        title: '❌ 加载失败',
        description: err instanceof Error ? err.message : '获取下载信息失败',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  /**
   * 处理下载
   */
  const handleDownload = () => {
    if (!downloadUrl) {
      toast({
        title: '⚠️ 下载链接不可用',
        description: '请稍后重试',
        variant: 'destructive',
      });
      return;
    }

    // 打开新标签页下载
    window.open(downloadUrl, '_blank');

    toast({
      title: '✅ 开始下载',
      description: `正在下载 ${platformInfo.name} 应用`,
    });
  };

  /**
   * 复制下载链接
   */
  const handleCopyLink = async () => {
    if (!downloadUrl) return;

    try {
      setCopying(true);
      await navigator.clipboard.writeText(downloadUrl);
      toast({
        title: '✅ 复制成功',
        description: '下载链接已复制到剪贴板',
      });
    } catch {
      toast({
        title: '❌ 复制失败',
        description: '请手动复制下载链接',
        variant: 'destructive',
      });
    } finally {
      setTimeout(() => setCopying(false), 2000);
    }
  };

  /**
   * 获取平台构建状态
   */
  const getPlatformStatus = (): Partial<PlatformBuildResult> & { status: string; progress: number } => {
    if (!buildStatus || !buildStatus.platformResults) {
      return { status: 'PENDING', progress: 0 };
    }
    return buildStatus.platformResults[platform] || { status: 'PENDING', progress: 0 };
  };

  const platformResult = getPlatformStatus();
  const isSuccess = platformResult.status === 'SUCCESS';
  const isFailed = platformResult.status === 'FAILED';

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mx-auto mb-4" />
          <p className="text-muted-foreground">加载下载信息...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50 p-4 md:p-8">
      <div className="max-w-4xl mx-auto">
        {/* 页面标题 */}
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold mb-2">
            {platformInfo.icon} {platformInfo.name} 应用下载
          </h1>
          <p className="text-muted-foreground">
            扫描二维码或点击按钮下载应用
          </p>
        </div>

        {/* 状态卡片 */}
        <Card className="mb-6">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>构建状态</CardTitle>
              <Badge
                variant={isSuccess ? 'default' : isFailed ? 'destructive' : 'secondary'}
                className="flex items-center gap-1"
              >
                {isSuccess && <CheckCircle2 className="w-4 h-4" />}
                {isFailed && <XCircle className="w-4 h-4" />}
                {platformResult.status}
              </Badge>
            </div>
            <CardDescription>
              构建ID: {buildId}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">平台</span>
                <span className="font-medium">{platformInfo.name}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">文件格式</span>
                <span className="font-medium">{platformInfo.fileExt}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">构建进度</span>
                <span className="font-medium">{platformResult.progress}%</span>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* 主要内容区域 */}
        <div className="grid md:grid-cols-2 gap-6">
          {/* 二维码区域 */}
          <Card>
            <CardHeader>
              <CardTitle>扫码下载</CardTitle>
              <CardDescription>
                使用手机扫描二维码直接下载
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col items-center">
              {qrCodeUrl && isSuccess ? (
                <div className="bg-white p-4 rounded-lg border-2 border-gray-200">
                  <Image
                    src={qrCodeUrl}
                    alt="下载二维码"
                    width={300}
                    height={300}
                    className="rounded"
                  />
                </div>
              ) : (
                <div className="w-[300px] h-[300px] bg-gray-100 rounded-lg flex items-center justify-center">
                  <p className="text-muted-foreground text-center">
                    {isFailed ? '构建失败' : '构建中...'}
                  </p>
                </div>
              )}
              <p className="text-sm text-muted-foreground mt-4 text-center">
                {platformInfo.description}
              </p>
            </CardContent>
          </Card>

          {/* 下载操作区域 */}
          <Card>
            <CardHeader>
              <CardTitle>直接下载</CardTitle>
              <CardDescription>
                点击按钮开始下载应用
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* 下载按钮 */}
              <Button
                onClick={handleDownload}
                disabled={!isSuccess}
                className="w-full h-12 text-lg"
                size="lg"
              >
                <Download className="w-5 h-5 mr-2" />
                下载 {platformInfo.name} 应用
              </Button>

              {/* 复制链接按钮 */}
              <Button
                onClick={handleCopyLink}
                disabled={!isSuccess || copying}
                variant="outline"
                className="w-full"
              >
                <Share2 className="w-4 h-4 mr-2" />
                {copying ? '已复制' : '复制下载链接'}
              </Button>

              <Separator />

              {/* 下载提示 */}
              <div className="space-y-2 text-sm text-muted-foreground">
                <h4 className="font-medium text-foreground">下载须知</h4>
                <ul className="list-disc list-inside space-y-1">
                  {platform === 'android' && (
                    <>
                      <li>需要在设置中允许安装未知来源应用</li>
                      <li>支持 Android 5.0 及以上版本</li>
                    </>
                  )}
                  {platform === 'ios' && (
                    <>
                      <li>需要使用 TestFlight 或企业证书安装</li>
                      <li>支持 iOS 13.0 及以上版本</li>
                    </>
                  )}
                  {platform === 'h5' && (
                    <>
                      <li>解压后部署到Web服务器即可</li>
                      <li>支持所有现代浏览器</li>
                    </>
                  )}
                  {platform === 'miniapp' && (
                    <>
                      <li>上传到对应小程序平台审核</li>
                      <li>需要小程序开发者账号</li>
                    </>
                  )}
                </ul>
              </div>

              {/* 构建信息 */}
              {buildStatus && (
                <div className="pt-4 border-t">
                  <h4 className="font-medium text-sm mb-2">构建信息</h4>
                  <div className="space-y-1 text-xs text-muted-foreground">
                    <p>构建时间: {new Date(buildStatus.createdAt).toLocaleString('zh-CN')}</p>
                    {platformResult.completedAt && (
                      <p>完成时间: {new Date(platformResult.completedAt).toLocaleString('zh-CN')}</p>
                    )}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* 页脚 */}
        <div className="mt-8 text-center text-sm text-muted-foreground">
          <p>Powered by Ingenio - AI驱动的应用构建平台</p>
        </div>
      </div>
    </div>
  );
}
