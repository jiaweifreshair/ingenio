/**
 * AI能力选择页面
 * 允许用户选择需要集成的AI功能，并配置AI网关
 *
 * 功能：
 * - AI能力选择（19种AI能力）
 * - AI代码生成
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
'use client';

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { AICapabilityPicker } from '@/components/ai/ai-capability-picker';
import { generateAICode, recommendComplexity } from '@/lib/api/ai-code-generator';
import { AICapabilityType } from '@/types/ai-capability';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loader2, AlertCircle, CheckCircle2, ArrowLeft } from 'lucide-react';

export default function AICapabilitiesPage() {
  const router = useRouter();

  const [selectedCapabilities, setSelectedCapabilities] = useState<AICapabilityType[]>([]);
  const [packageName, setPackageName] = useState('com.example.myapp');
  const [appName, setAppName] = useState('My AI App');
  const [userRequirement, setUserRequirement] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleGenerate = useCallback(async () => {
    if (selectedCapabilities.length === 0) {
      setError('请至少选择一个AI能力');
      return;
    }

    if (!packageName || !packageName.match(/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/)) {
      setError('包名格式不正确，示例：com.example.myapp');
      return;
    }

    if (!appName || appName.trim().length === 0) {
      setError('请输入应用名称');
      return;
    }

    setIsGenerating(true);
    setError(null);
    setSuccess(false);

    try {
      const complexity = recommendComplexity(selectedCapabilities);

      const result = await generateAICode({
        capabilities: selectedCapabilities,
        packageName: packageName.trim(),
        appName: appName.trim(),
        complexity,
        userRequirement: userRequirement.trim() || undefined,
      });

      if (result.success && result.data) {
        setSuccess(true);
        setTimeout(() => {
          router.push(`/wizard/ai-result?taskId=${result.data!.taskId}`);
        }, 2000);
      } else {
        setError(result.error || '生成失败');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '未知错误');
    } finally {
      setIsGenerating(false);
    }
  }, [selectedCapabilities, packageName, appName, userRequirement, router]);

  const handleBack = useCallback(() => {
    router.back();
  }, [router]);

  return (
    <div className="min-h-screen bg-background">
      {/* 顶部标题栏 */}
      <div className="border-b bg-background px-6 py-4 mb-8">
        <div className="max-w-7xl mx-auto">
          <div className="flex items-center gap-4">
            <Button
              variant="ghost"
              size="sm"
              onClick={handleBack}
              className="gap-2"
              data-testid="back-button"
            >
              <ArrowLeft className="w-4 h-4" />
              返回
            </Button>
            <h1 className="text-xl font-semibold">选择AI能力</h1>
            {isGenerating && <Loader2 className="w-4 h-4 animate-spin text-primary" />}
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-6 pb-12">
        {/* 基础配置 */}
        <Card className="mb-8" data-testid="config-card">
          <CardHeader>
            <CardTitle>应用配置</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label htmlFor="appName">应用名称</Label>
                <Input
                  id="appName"
                  type="text"
                  placeholder="例如：我的AI助手"
                  value={appName}
                  onChange={(e) => setAppName(e.target.value)}
                  disabled={isGenerating}
                  data-testid="app-name-input"
                />
              </div>
              <div>
                <Label htmlFor="packageName">包名</Label>
                <Input
                  id="packageName"
                  type="text"
                  placeholder="例如：com.example.myapp"
                  value={packageName}
                  onChange={(e) => setPackageName(e.target.value)}
                  disabled={isGenerating}
                  data-testid="package-name-input"
                />
                <p className="text-xs text-muted-foreground mt-1">
                  格式：小写字母和点号，如 com.example.app
                </p>
              </div>
            </div>
            <div>
              <Label htmlFor="userRequirement">需求描述（可选）</Label>
              <Input
                id="userRequirement"
                type="text"
                placeholder="例如：构建智能客服聊天机器人"
                value={userRequirement}
                onChange={(e) => setUserRequirement(e.target.value)}
                disabled={isGenerating}
                data-testid="user-requirement-input"
              />
              <p className="text-xs text-muted-foreground mt-1">
                输入需求描述可获得AI能力推荐
              </p>
            </div>
          </CardContent>
        </Card>

        {/* AI能力选择器 */}
        <div data-testid="ai-capability-picker">
          <AICapabilityPicker
            selectedCapabilities={selectedCapabilities}
            onSelectionChange={setSelectedCapabilities}
            userRequirement={userRequirement}
            disabled={isGenerating}
            maxSelection={5}
            showCostEstimate={true}
            showRecommendations={true}
          />
        </div>

        {/* 错误提示 */}
        {error && (
          <Alert variant="destructive" className="mt-6" data-testid="error-alert">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {/* 成功提示 */}
        {success && (
          <Alert className="mt-6 bg-green-50 border-green-200" data-testid="success-alert">
            <CheckCircle2 className="h-4 w-4 text-green-600" />
            <AlertDescription className="text-green-800">
              代码生成成功！正在跳转到结果页面...
            </AlertDescription>
          </Alert>
        )}

        {/* 生成按钮 */}
        <div className="mt-8 flex justify-end">
          <Button
            onClick={handleGenerate}
            disabled={isGenerating || selectedCapabilities.length === 0}
            size="lg"
            className="gap-2"
            data-testid="generate-btn"
          >
            {isGenerating && <Loader2 className="w-4 h-4 animate-spin" />}
            {isGenerating ? '正在生成代码...' : '生成AI代码'}
          </Button>
        </div>
      </div>
    </div>
  );
}
