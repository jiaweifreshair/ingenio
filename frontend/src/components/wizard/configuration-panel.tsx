/**
 * 配置面板组件
 * 左侧面板，包含需求输入、模型选择、参数配置和操作按钮
 *
 * 性能优化说明：
 * - 使用React.memo防止不必要的重渲染
 * - 使用useCallback稳定回调引用
 * - 避免在渲染过程中创建新对象/函数引用
 */
'use client';

import React, { useState, useCallback, memo } from 'react';
import { cn } from '@/lib/utils';
import { GenerationConfig } from '@/types/wizard';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Slider } from '@/components/ui/slider';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Play, Pause, RotateCcw, Settings, AlertCircle } from 'lucide-react';

/**
 * 配置面板Props
 */
interface ConfigurationPanelProps {
  /** 配置数据 */
  config: GenerationConfig;
  /** 配置更新回调 */
  onConfigChange: (config: GenerationConfig) => void;
  /** 开始生成回调 */
  onStartGeneration: () => void;
  /** 暂停生成回调 */
  onPauseGeneration: () => void;
  /** 重试回调 */
  onRetry: () => void;
  /** 重置回调 */
  onReset: () => void;
  /** 是否正在生成 */
  isGenerating: boolean;
  /** 是否可编辑 */
  editable: boolean;
  /** 错误信息 */
  error?: string;
  /** 是否连接 */
  isConnected?: boolean;
  /** 重连回调 */
  onReconnect?: () => void;
  /** 类名 */
  className?: string;
}

/**
 * 配置面板组件（使用memo优化性能）
 *
 * 修复说明：
 * 1. 使用React.memo包裹组件，避免父组件状态更新导致的不必要重渲染
 * 2. 使用useCallback缓存所有事件处理函数，确保引用稳定
 * 3. 在onChange回调中避免创建新对象，使用对象展开语法保持浅比较优化
 */
const ConfigurationPanelComponent: React.FC<ConfigurationPanelProps> = ({
  config,
  onConfigChange,
  onStartGeneration,
  onPauseGeneration,
  onRetry,
  onReset,
  isGenerating,
  editable,
  error,
  isConnected,
  onReconnect,
  className,
}) => {
  const [showAdvanced, setShowAdvanced] = useState(false);

  // 检查配置是否有效
  const isConfigValid = config.requirement && config.requirement.trim().length > 10;

  // 使用useCallback稳定回调引用，避免子组件无限重渲染
  const handleRequirementChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
    onConfigChange({ ...config, requirement: e.target.value });
  }, [config, onConfigChange]);

  const handleModelChange = useCallback((value: string) => {
    onConfigChange({ ...config, model: value });
  }, [config, onConfigChange]);

  const handleQualityThresholdChange = useCallback((value: number[]) => {
    onConfigChange({ ...config, qualityThreshold: value[0] });
  }, [config, onConfigChange]);

  const handleSkipValidationChange = useCallback((checked: boolean) => {
    onConfigChange({ ...config, skipValidation: checked });
  }, [config, onConfigChange]);

  const handleGeneratePreviewChange = useCallback((checked: boolean) => {
    onConfigChange({ ...config, generatePreview: checked });
  }, [config, onConfigChange]);

  const toggleAdvanced = useCallback(() => {
    setShowAdvanced(prev => !prev);
  }, []);

  return (
    <div className={cn("h-full flex flex-col", className)}>
      <div className="flex-1 overflow-auto p-4 space-y-4">
        {/* 头部信息 */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-lg flex items-center gap-2">
              <Settings className="h-5 w-5" />
              应用配置
            </CardTitle>
          </CardHeader>
        </Card>

        {/* 错误提示 */}
        {error && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        {/* 连接状态提示 */}
        {!isConnected && onReconnect && (
          <Alert>
            <AlertCircle className="h-4 w-4" />
            <AlertDescription className="flex items-center justify-between">
              <span>连接已断开</span>
              <Button size="sm" variant="outline" onClick={onReconnect}>
                重新连接
              </Button>
            </AlertDescription>
          </Alert>
        )}

        {/* 需求描述 */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">需求描述</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <div>
              <Label htmlFor="requirement">
                描述您想要创建的应用
                <span className="text-red-500 ml-1">*</span>
              </Label>
              <Textarea
                id="requirement"
                placeholder="请详细描述您想要创建的应用功能、特性和需求..."
                value={config.requirement || ''}
                onChange={handleRequirementChange}
                disabled={!editable || isGenerating}
                rows={4}
                className="mt-2"
              />
              <div className="text-xs text-muted-foreground mt-1">
                {config.requirement?.length || 0}/500 字符
              </div>
            </div>
          </CardContent>
        </Card>

        {/* AI模型选择 */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">AI模型配置</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <Label htmlFor="model">选择AI模型</Label>
              <Select
                value={config.model || 'qwen-max'}
                onValueChange={handleModelChange}
                disabled={!editable || isGenerating}
              >
                <SelectTrigger className="mt-2">
                  <SelectValue placeholder="选择AI模型" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="qwen-max">
                    <div className="flex items-center justify-between w-full">
                      <span>Qwen Max</span>
                      <Badge variant="secondary" className="ml-2">推荐</Badge>
                    </div>
                  </SelectItem>
                  <SelectItem value="deepseek-coder">
                    <div className="flex items-center justify-between w-full">
                      <span>DeepSeek Coder</span>
                      <Badge variant="outline" className="ml-2">代码</Badge>
                    </div>
                  </SelectItem>
                  <SelectItem value="claude-3-opus">
                    <div className="flex items-center justify-between w-full">
                      <span>Claude 3 Opus</span>
                      <Badge variant="outline" className="ml-2">高质量</Badge>
                    </div>
                  </SelectItem>
                  <SelectItem value="gpt-4-turbo">
                    <div className="flex items-center justify-between w-full">
                      <span>GPT-4 Turbo</span>
                      <Badge variant="outline" className="ml-2">快速</Badge>
                    </div>
                  </SelectItem>
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground mt-1">
                不同模型在代码质量和响应速度上有所差异
              </p>
            </div>
          </CardContent>
        </Card>

        {/* 生成参数 */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">生成参数</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* 质量阈值 */}
            <div>
              <Label htmlFor="quality-threshold">
                质量阈值: {config.qualityThreshold || 70}
              </Label>
              <Slider
                id="quality-threshold"
                min={60}
                max={95}
                step={5}
                value={[config.qualityThreshold || 70]}
                onValueChange={handleQualityThresholdChange}
                disabled={!editable || isGenerating}
                className="mt-2"
              />
              <div className="flex justify-between text-xs text-muted-foreground mt-1">
                <span>快速生成</span>
                <span>高质量</span>
              </div>
            </div>

            {/* 开关选项 */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <Label htmlFor="skip-validation" className="text-sm">
                  跳过质量验证
                </Label>
                <Switch
                  id="skip-validation"
                  checked={config.skipValidation || false}
                  onCheckedChange={handleSkipValidationChange}
                  disabled={!editable || isGenerating}
                />
              </div>
              <p className="text-xs text-muted-foreground">
                跳过验证可加快生成速度，但可能影响质量
              </p>

              <div className="flex items-center justify-between">
                <Label htmlFor="generate-preview" className="text-sm">
                  生成预览版本
                </Label>
                <Switch
                  id="generate-preview"
                  checked={config.generatePreview || false}
                  onCheckedChange={handleGeneratePreviewChange}
                  disabled={!editable || isGenerating}
                />
              </div>
              <p className="text-xs text-muted-foreground">
                生成后立即创建可预览的版本
              </p>
            </div>
          </CardContent>
        </Card>

        {/* 高级设置 */}
        <Card>
          <CardHeader
            className="pb-3 cursor-pointer"
            onClick={toggleAdvanced}
          >
            <CardTitle className="text-base flex items-center justify-between">
              高级设置
              <span className="text-sm text-muted-foreground">
                {showAdvanced ? '收起' : '展开'}
              </span>
            </CardTitle>
          </CardHeader>
          {showAdvanced && (
            <CardContent className="space-y-4">
              <div className="text-sm text-muted-foreground">
                <p>• 自定义提示词模板</p>
                <p>• 特定技术栈限制</p>
                <p>• 性能优化选项</p>
                <p>• 部署环境配置</p>
              </div>
              <Alert>
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>
                  高级设置功能正在开发中，敬请期待！
                </AlertDescription>
              </Alert>
            </CardContent>
          )}
        </Card>
      </div>

      {/* 底部操作按钮 */}
      <div className="border-t bg-background p-4">
        <div className="space-y-2">
          {/* 主要操作按钮 */}
          {!isGenerating ? (
            <Button
              onClick={onStartGeneration}
              disabled={!isConfigValid || !editable}
              className="w-full"
              size="lg"
            >
              <Play className="h-4 w-4 mr-2" />
              开始生成应用
            </Button>
          ) : (
            <div className="grid grid-cols-2 gap-2">
              <Button
                onClick={onPauseGeneration}
                disabled={!editable}
                variant="outline"
                size="lg"
              >
                <Pause className="h-4 w-4 mr-2" />
                暂停
              </Button>
              <Button
                onClick={onReset}
                disabled={!editable}
                variant="outline"
                size="lg"
              >
                <RotateCcw className="h-4 w-4 mr-2" />
                重置
              </Button>
            </div>
          )}

          {/* 重试按钮 */}
          {error && !isGenerating && (
            <Button
              onClick={onRetry}
              variant="outline"
              className="w-full"
            >
              <RotateCcw className="h-4 w-4 mr-2" />
              重试生成
            </Button>
          )}

          {/* 配置提示 */}
          {!isConfigValid && !isGenerating && (
            <div className="text-xs text-muted-foreground text-center">
              请填写完整的应用需求描述后开始生成
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

/**
 * 自定义比较函数，优化React.memo的性能
 * 只在关键props变化时才重新渲染
 */
const arePropsEqual = (
  prevProps: ConfigurationPanelProps,
  nextProps: ConfigurationPanelProps
): boolean => {
  // 比较配置对象的关键字段
  const configEqual =
    prevProps.config.requirement === nextProps.config.requirement &&
    prevProps.config.model === nextProps.config.model &&
    prevProps.config.qualityThreshold === nextProps.config.qualityThreshold &&
    prevProps.config.skipValidation === nextProps.config.skipValidation &&
    prevProps.config.generatePreview === nextProps.config.generatePreview;

  // 比较其他关键props
  const otherPropsEqual =
    prevProps.isGenerating === nextProps.isGenerating &&
    prevProps.editable === nextProps.editable &&
    prevProps.error === nextProps.error &&
    prevProps.isConnected === nextProps.isConnected;

  return configEqual && otherPropsEqual;
};

/**
 * 导出使用React.memo优化的组件
 * 使用自定义比较函数进行更精确的控制
 */
export const ConfigurationPanel = memo(ConfigurationPanelComponent, arePropsEqual);

// 设置displayName便于调试
ConfigurationPanel.displayName = 'ConfigurationPanel';
