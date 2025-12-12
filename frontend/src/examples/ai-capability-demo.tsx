/**
 * AI能力交互式Demo
 *
 * 功能：
 * - 左侧：AI能力选择器（使用AICapabilityPicker）
 * - 右侧：实时代码预览（显示生成的Kotlin代码）
 * - 底部：生成按钮和下载按钮
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

'use client';

import { useState } from 'react';
import { AICapabilityPicker } from '@/components/ai/ai-capability-picker';
import { AICapabilityType } from '@/types/ai-capability';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Download, Code, Play, AlertCircle } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';

interface CodePreview {
  fileName: string;
  language: string;
  code: string;
}

export default function AICapabilityDemo() {
  // 状态管理
  const [selectedCapabilities, setSelectedCapabilities] = useState<AICapabilityType[]>([]);
  const [packageName, setPackageName] = useState('com.example.myapp');
  const [appName, setAppName] = useState('My AI App');
  const [userRequirement, setUserRequirement] = useState('');
  const [codePreview, setCodePreview] = useState<CodePreview[]>([]);
  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 生成代码预览
  const generatePreview = async () => {
    setIsGenerating(true);
    setError(null);

    try {
      const response = await fetch('/api/v1/wizard/preview-code', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          aiCapabilities: selectedCapabilities,
          packageName,
          appName,
          userRequirement,
        }),
      });

      if (!response.ok) {
        throw new Error('代码预览生成失败');
      }

      const data = await response.json();
      setCodePreview(data.files || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : '未知错误');
    } finally {
      setIsGenerating(false);
    }
  };

  // 生成并下载完整项目
  const generateAndDownload = async () => {
    setIsGenerating(true);
    setError(null);

    try {
      const response = await fetch('/api/v1/wizard/generate-code', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          aiCapabilities: selectedCapabilities,
          packageName,
          appName,
          userRequirement,
        }),
      });

      if (!response.ok) {
        throw new Error('代码生成失败');
      }

      const data = await response.json();

      // 下载ZIP文件
      window.location.href = data.zipUrl;
    } catch (err) {
      setError(err instanceof Error ? err.message : '未知错误');
    } finally {
      setIsGenerating(false);
    }
  };

  // 渲染代码高亮
  const renderCodeBlock = (preview: CodePreview) => {
    return (
      <div className="rounded-lg bg-gray-900 p-4">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-sm font-medium text-gray-300">{preview.fileName}</span>
          <span className="text-xs text-gray-500">{preview.language}</span>
        </div>
        <ScrollArea className="h-[500px]">
          <pre className="text-sm text-gray-100">
            <code>{preview.code}</code>
          </pre>
        </ScrollArea>
      </div>
    );
  };

  return (
    <div className="container mx-auto p-6">
      <div className="mb-6">
        <h1 className="text-3xl font-bold">AI能力交互式Demo</h1>
        <p className="text-gray-600 mt-2">
          选择AI能力，实时预览生成的Kotlin代码，下载完整项目
        </p>
      </div>

      {/* 错误提示 */}
      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>错误</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 左侧：配置区域 */}
        <div className="space-y-6">
          {/* 基本信息 */}
          <div className="rounded-lg border p-4">
            <h2 className="text-lg font-semibold mb-4">基本信息</h2>
            <div className="space-y-4">
              <div>
                <Label htmlFor="appName">应用名称</Label>
                <Input
                  id="appName"
                  value={appName}
                  onChange={(e) => setAppName(e.target.value)}
                  placeholder="My AI App"
                />
              </div>
              <div>
                <Label htmlFor="packageName">包名</Label>
                <Input
                  id="packageName"
                  value={packageName}
                  onChange={(e) => setPackageName(e.target.value)}
                  placeholder="com.example.myapp"
                />
              </div>
              <div>
                <Label htmlFor="requirement">需求描述（可选）</Label>
                <Input
                  id="requirement"
                  value={userRequirement}
                  onChange={(e) => setUserRequirement(e.target.value)}
                  placeholder="描述你的需求，AI会推荐合适的能力"
                />
              </div>
            </div>
          </div>

          {/* AI能力选择器 */}
          <div className="rounded-lg border p-4">
            <h2 className="text-lg font-semibold mb-4">选择AI能力</h2>
            <AICapabilityPicker
              selectedCapabilities={selectedCapabilities}
              onSelectionChange={setSelectedCapabilities}
              userRequirement={userRequirement}
            />
          </div>

          {/* 操作按钮 */}
          <div className="flex gap-4">
            <Button
              onClick={generatePreview}
              disabled={selectedCapabilities.length === 0 || isGenerating}
              className="flex-1"
            >
              <Play className="mr-2 h-4 w-4" />
              {isGenerating ? '生成中...' : '预览代码'}
            </Button>
            <Button
              onClick={generateAndDownload}
              disabled={selectedCapabilities.length === 0 || isGenerating}
              variant="default"
              className="flex-1"
            >
              <Download className="mr-2 h-4 w-4" />
              下载ZIP
            </Button>
          </div>

          {/* 选中的AI能力统计 */}
          {selectedCapabilities.length > 0 && (
            <div className="rounded-lg bg-blue-50 p-4">
              <h3 className="font-semibold mb-2">已选择 {selectedCapabilities.length} 种AI能力</h3>
              <ul className="list-disc list-inside text-sm text-gray-700">
                {selectedCapabilities.map((cap) => (
                  <li key={cap}>{cap}</li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {/* 右侧：代码预览区域 */}
        <div className="rounded-lg border p-4">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">代码预览</h2>
            <Code className="h-5 w-5 text-gray-500" />
          </div>

          {codePreview.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-[600px] text-gray-500">
              <Code className="h-16 w-16 mb-4" />
              <p>点击&quot;预览代码&quot;按钮查看生成的代码</p>
            </div>
          ) : (
            <Tabs defaultValue={codePreview[0]?.fileName} className="w-full">
              <TabsList className="w-full overflow-x-auto flex-nowrap">
                {codePreview.map((preview) => (
                  <TabsTrigger key={preview.fileName} value={preview.fileName} className="flex-shrink-0">
                    {preview.fileName.split('/').pop()}
                  </TabsTrigger>
                ))}
              </TabsList>
              {codePreview.map((preview) => (
                <TabsContent key={preview.fileName} value={preview.fileName}>
                  {renderCodeBlock(preview)}
                </TabsContent>
              ))}
            </Tabs>
          )}
        </div>
      </div>

      {/* 底部说明 */}
      <div className="mt-8 rounded-lg bg-gray-50 p-6">
        <h3 className="font-semibold mb-2">使用说明</h3>
        <ul className="list-disc list-inside text-sm text-gray-700 space-y-1">
          <li>选择一个或多个AI能力，系统会自动生成对应的Kotlin代码</li>
          <li>点击&quot;预览代码&quot;可以实时查看生成的代码，无需下载</li>
          <li>点击&quot;下载ZIP&quot;会生成完整的Kotlin Multiplatform项目并下载</li>
          <li>生成的代码包含完整的AI集成、ViewModel、UI界面和文档</li>
          <li>下载后需要配置API密钥才能运行（参见README.md）</li>
        </ul>
      </div>

      {/* 推荐组合 */}
      <div className="mt-8">
        <h3 className="font-semibold mb-4">推荐AI能力组合</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div
            className="rounded-lg border p-4 cursor-pointer hover:bg-gray-50 transition"
            onClick={() => setSelectedCapabilities([
              AICapabilityType.CHATBOT,
              AICapabilityType.SENTIMENT_ANALYSIS,
              AICapabilityType.IMAGE_RECOGNITION,
            ])}
          >
            <h4 className="font-semibold mb-2">智能客服系统</h4>
            <ul className="text-sm text-gray-600 space-y-1">
              <li>• CHATBOT</li>
              <li>• SENTIMENT_ANALYSIS</li>
              <li>• IMAGE_RECOGNITION</li>
            </ul>
            <p className="text-xs text-gray-500 mt-2">成本: ~$5/月</p>
          </div>

          <div
            className="rounded-lg border p-4 cursor-pointer hover:bg-gray-50 transition"
            onClick={() => setSelectedCapabilities([
              AICapabilityType.TEXT_GENERATION,
              AICapabilityType.RECOMMENDATION,
              AICapabilityType.CONTENT_MODERATION,
            ])}
          >
            <h4 className="font-semibold mb-2">内容平台</h4>
            <ul className="text-sm text-gray-600 space-y-1">
              <li>• TEXT_GENERATION</li>
              <li>• RECOMMENDATION</li>
              <li>• CONTENT_MODERATION</li>
            </ul>
            <p className="text-xs text-gray-500 mt-2">成本: ~$15/月</p>
          </div>

          <div
            className="rounded-lg border p-4 cursor-pointer hover:bg-gray-50 transition"
            onClick={() => setSelectedCapabilities([
              AICapabilityType.VIDEO_ANALYSIS,
              AICapabilityType.CONTENT_MODERATION,
              AICapabilityType.REALTIME_STREAM,
            ])}
          >
            <h4 className="font-semibold mb-2">智能监控系统</h4>
            <ul className="text-sm text-gray-600 space-y-1">
              <li>• VIDEO_ANALYSIS</li>
              <li>• ANOMALY_DETECTION</li>
              <li>• REALTIME_STREAM</li>
            </ul>
            <p className="text-xs text-gray-500 mt-2">成本: ~$100/月</p>
          </div>
        </div>
      </div>
    </div>
  );
}
