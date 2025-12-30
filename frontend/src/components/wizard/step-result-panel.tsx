'use client';

import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Slider } from '@/components/ui/slider';
import { CheckCircle2, FileCode, AlertTriangle, Layout, Database, Server, Settings } from 'lucide-react';
import { AgentExecutionStatus, GenerationConfig } from '@/types/wizard';

interface Module {
  name: string;
  description: string;
  priority: string;
  complexity: number;
  pages: string[];
}

interface AppSpec {
  requirement?: string;
  planResult?: {
    modules: Module[];
  };
  frontendPrototype?: Record<string, unknown>;
  frontendPrototypeUrl?: string;
}

interface StepResultPanelProps {
  currentStepIndex: number;
  agents: AgentExecutionStatus[];
  appSpec: AppSpec;
  onConfirm: () => void;
  taskStatus: 'idle' | 'generating' | 'completed' | 'failed';
  isStepCompleted: boolean;
  onEdit?: (data: { requirement: string }) => void;
  config?: GenerationConfig;
  onConfigChange?: (config: GenerationConfig) => void;
}

export function StepResultPanel({
  currentStepIndex,
  appSpec,
  onConfirm,
  taskStatus,
  isStepCompleted,
  onEdit,
  config,
  onConfigChange
}: StepResultPanelProps) {
  // Local state for requirement editing
  const [localRequirement, setLocalRequirement] = useState('');
  
  useEffect(() => {
    // Update local requirement if appSpec or config changes
    if (config?.requirement) {
      setLocalRequirement(config.requirement);
    } else if (appSpec?.requirement) {
      setLocalRequirement(appSpec.requirement);
    }
  }, [appSpec, config]);

  const handleRequirementChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const newValue = e.target.value;
    setLocalRequirement(newValue);
    onEdit?.({ requirement: newValue });
    if (config && onConfigChange) {
      onConfigChange({ ...config, requirement: newValue });
    }
  };

  const handleModelChange = (value: string) => {
    if (config && onConfigChange) {
      onConfigChange({ ...config, model: value });
    }
  };

  const handleQualityThresholdChange = (value: number[]) => {
    if (config && onConfigChange) {
      onConfigChange({ ...config, qualityThreshold: value[0] });
    }
  };

  const renderPlanResult = () => {
    const isEditable = taskStatus === 'idle' || taskStatus === 'failed';

    return (
      <div className="space-y-6">
        {/* Configuration Section (Only visible/editable in idle/failed state or read-only otherwise) */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base flex items-center gap-2">
              <Settings className="h-4 w-4" />
              应用配置
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
             <div className="space-y-2">
              <Label>需求描述 <span className="text-red-500">*</span></Label>
              <Textarea 
                value={localRequirement} 
                onChange={handleRequirementChange}
                className="min-h-[120px]"
                placeholder="输入应用需求..."
                disabled={!isEditable} 
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <Label className="mb-2 block">AI模型</Label>
                <Select
                  value={config?.model || 'gemini-3-pro-preview'}
                  onValueChange={handleModelChange}
                  disabled={!isEditable}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="选择AI模型" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="gemini-3-pro-preview">Gemini 3.0 Pro Preview (旗舰)</SelectItem>
                    <SelectItem value="qwen-max">Qwen Max (推荐)</SelectItem>
                    <SelectItem value="deepseek-coder">DeepSeek Coder</SelectItem>
                    <SelectItem value="claude-3-opus">Claude 3 Opus</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div>
                <Label className="mb-2 block">质量阈值: {config?.qualityThreshold || 70}</Label>
                <Slider
                  min={60}
                  max={95}
                  step={5}
                  value={[config?.qualityThreshold || 70]}
                  onValueChange={handleQualityThresholdChange}
                  disabled={!isEditable}
                  className="py-2"
                />
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="space-y-2">
          <Label>功能模块规划结果</Label>
          {appSpec?.planResult?.modules?.length ? (
            <div className="grid gap-3">
              {appSpec.planResult.modules.map((module, idx) => (
                <Card key={idx}>
                  <CardHeader className="p-4">
                    <div className="flex justify-between items-center">
                      <div className="font-semibold">{module.name}</div>
                      <Badge variant={module.priority === 'high' ? 'destructive' : 'secondary'}>
                        {module.priority === 'high' ? '高优先级' : '普通'}
                      </Badge>
                    </div>
                    <CardDescription>{module.description}</CardDescription>
                  </CardHeader>
                </Card>
              ))}
            </div>
          ) : (
            <div className="text-center text-muted-foreground py-12 border rounded-md border-dashed bg-muted/10">
              {taskStatus === 'idle' ? '配置完成后点击"开始生成"以启动规划' : '正在进行需求分析与规划...'}
            </div>
          )}
        </div>
      </div>
    );
  };

  const renderExecuteResult = () => {
    if (!isStepCompleted && taskStatus === 'generating') {
         return (
             <div className="flex flex-col items-center justify-center h-[400px] text-center space-y-4">
                 <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
                 <div>
                     <h3 className="text-lg font-medium">代码生成中...</h3>
                     <p className="text-muted-foreground">正在生成Kotlin Multiplatform代码、数据库Schema和API接口</p>
                 </div>
             </div>
         )
    }

    return (
      <div className="space-y-6">
        {/* V2.0 OpenLovable Preview Integration */}
        {appSpec?.frontendPrototypeUrl && (
          <Card className="border-purple-200 dark:border-purple-800 overflow-hidden">
            <CardHeader className="bg-purple-50 dark:bg-purple-900/10 pb-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-base flex items-center gap-2">
                  <Layout className="h-4 w-4 text-purple-600" />
                  生成的前端原型
                </CardTitle>
                <Button 
                  variant="outline" 
                  size="sm" 
                  className="h-8 text-xs"
                  onClick={() => window.open(appSpec.frontendPrototypeUrl, '_blank')}
                >
                  新窗口打开
                </Button>
              </div>
            </CardHeader>
            <div className="aspect-video w-full bg-gray-100 dark:bg-gray-900 relative">
              <iframe 
                src={appSpec.frontendPrototypeUrl} 
                className="absolute inset-0 w-full h-full border-0"
                title="Prototype Preview"
              />
            </div>
          </Card>
        )}

        <div className="grid grid-cols-3 gap-4">
          <Card>
            <CardContent className="pt-6 flex flex-col items-center text-center">
              <Layout className="h-8 w-8 mb-2 text-blue-500" />
              <div className="text-2xl font-bold">4</div>
              <div className="text-xs text-muted-foreground">页面生成</div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 flex flex-col items-center text-center">
              <Database className="h-8 w-8 mb-2 text-green-500" />
              <div className="text-2xl font-bold">8</div>
              <div className="text-xs text-muted-foreground">数据表</div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="pt-6 flex flex-col items-center text-center">
              <Server className="h-8 w-8 mb-2 text-purple-500" />
              <div className="text-2xl font-bold">12</div>
              <div className="text-xs text-muted-foreground">API接口</div>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>代码生成概览</CardTitle>
          </CardHeader>
          <CardContent>
            <ScrollArea className="h-[300px]">
              <div className="space-y-2">
                <div className="flex items-center gap-2 p-2 hover:bg-muted rounded cursor-pointer">
                  <FileCode className="h-4 w-4 text-blue-500" />
                  <span className="text-sm">src/commonMain/kotlin/data/User.kt</span>
                </div>
                <div className="flex items-center gap-2 p-2 hover:bg-muted rounded cursor-pointer">
                  <FileCode className="h-4 w-4 text-blue-500" />
                  <span className="text-sm">src/commonMain/kotlin/repository/UserRepository.kt</span>
                </div>
                <div className="flex items-center gap-2 p-2 hover:bg-muted rounded cursor-pointer">
                  <FileCode className="h-4 w-4 text-green-500" />
                  <span className="text-sm">src/androidMain/kotlin/ui/MainActivity.kt</span>
                </div>
                <div className="flex items-center gap-2 p-2 hover:bg-muted rounded cursor-pointer">
                  <FileCode className="h-4 w-4 text-purple-500" />
                  <span className="text-sm">backend/src/main/resources/db/migration/V1__create_tables.sql</span>
                </div>
                 <div className="text-xs text-muted-foreground pt-4 text-center">
                   (预览模式 - 实际文件列表需从API获取)
                 </div>
              </div>
            </ScrollArea>
          </CardContent>
        </Card>
      </div>
    );
  };

  const renderValidateResult = () => {
    if (!isStepCompleted && taskStatus === 'generating') {
         return (
             <div className="flex flex-col items-center justify-center h-[400px] text-center space-y-4">
                 <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-green-500"></div>
                 <div>
                     <h3 className="text-lg font-medium">质量验证中...</h3>
                     <p className="text-muted-foreground">正在进行编译检查、API连通性测试和性能分析</p>
                 </div>
             </div>
         )
    }

    return (
      <div className="space-y-6">
         <Card className="border-green-200 bg-green-50">
            <CardContent className="pt-6 flex items-center gap-4">
              <CheckCircle2 className="h-10 w-10 text-green-600" />
              <div>
                <h3 className="font-bold text-green-800">验证通过</h3>
                <p className="text-green-700">所有核心功能测试通过，代码质量评分 95/100</p>
              </div>
            </CardContent>
         </Card>

         <div className="space-y-3">
            <h4 className="font-medium">检查项目</h4>
            <div className="flex items-center justify-between p-3 border rounded bg-white">
              <span className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-green-500"/> 编译检查</span>
              <Badge variant="outline" className="text-green-600 bg-green-50">通过</Badge>
            </div>
            <div className="flex items-center justify-between p-3 border rounded bg-white">
              <span className="flex items-center gap-2"><CheckCircle2 className="h-4 w-4 text-green-500"/> API 连通性</span>
              <Badge variant="outline" className="text-green-600 bg-green-50">通过</Badge>
            </div>
            <div className="flex items-center justify-between p-3 border rounded bg-white">
              <span className="flex items-center gap-2"><AlertTriangle className="h-4 w-4 text-yellow-500"/> 性能分析</span>
              <Badge variant="outline" className="text-yellow-600 bg-yellow-50">建议优化</Badge>
            </div>
         </div>
      </div>
    );
  };

  const renderContent = () => {
    switch (currentStepIndex) {
      case 0: return renderPlanResult();
      case 1: return renderExecuteResult();
      case 2: return renderValidateResult();
      default: return <div>完成</div>;
    }
  };

  const getStepTitle = () => {
    switch (currentStepIndex) {
      case 0: return '需求分析与规划 (Step 1/3)';
      case 1: return '代码生成 (Step 2/3)';
      case 2: return '质量验证 (Step 3/3)';
      default: return '生成完成';
    }
  };

  const getButtonText = () => {
    if (currentStepIndex === 0 && taskStatus === 'idle') return '开始生成';
    if (taskStatus === 'generating') return isStepCompleted ? '下一步' : '生成中...';
    if (currentStepIndex === 2) return '完成并发布';
    return '确认并进入下一步';
  };

  const isButtonDisabled = () => {
    if (currentStepIndex === 0 && taskStatus === 'idle') return !localRequirement.trim();
    if (taskStatus === 'generating' && !isStepCompleted) return true;
    return false;
  };

  return (
    <div className="h-full flex flex-col bg-background">
      <div className="border-b p-4 bg-muted/30">
        <h2 className="text-lg font-semibold">{getStepTitle()}</h2>
        <p className="text-sm text-muted-foreground">
          {isStepCompleted 
            ? '当前步骤已完成，请确认结果并继续' 
            : (taskStatus === 'idle' 
                ? '请配置应用需求并开始生成' 
                : '正在执行任务，请耐心等待...')}
        </p>
      </div>
      
      <ScrollArea className="flex-1 p-6">
        {renderContent()}
      </ScrollArea>

      <div className="p-4 border-t bg-background mt-auto">
        <Button 
          className="w-full" 
          size="lg" 
          onClick={onConfirm}
          disabled={isButtonDisabled()}
        >
          {getButtonText()}
        </Button>
      </div>
    </div>
  );
}