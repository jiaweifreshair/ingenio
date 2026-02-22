/**
 * 步骤结果展示组件
 *
 * 根据不同步骤展示对应的分析结果
 */
'use client';

import React from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  ArrowRight,
  Edit2,
  CheckCircle2,
  FileText,
  Database,
  Cpu,
  Layout,
  Palette
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type {
  Step1Result,
  Step2Result,
  Step3Result,
  Step4Result,
  StepConfirmPayload,
  Step5Result,
  StepResult
} from '@/types/analysis-step-results';

export interface StepResultDisplayProps {
  /** 步骤结果数据 */
  result: StepResult;
  /** 确认回调 */
  onConfirm: (payload?: StepConfirmPayload) => void;
  /** 修改回调 */
  onModify: () => void;
  /** 是否正在加载 */
  loading?: boolean;
  /** 确认按钮文案（可选，用于“查看模式”等场景） */
  confirmLabel?: string;
  /** 修改按钮文案（可选，用于“查看模式”等场景） */
  modifyLabel?: string;
  /** 是否显示确认按钮（默认显示） */
  showConfirmButton?: boolean;
  /** 是否显示修改按钮（默认显示） */
  showModifyButton?: boolean;
}

/**
 * 提取 Step1「关键实体」的展示文本
 *
 * 是什么：面向 UI 的实体展示文本提取器。
 * 做什么：当实体是 JSON/类 JSON 字符串时，优先提取 `description` 字段用于页面展示。
 * 为什么：上下文需要保留完整 JSON 以便后续推理，但页面仅展示中文描述以提升可读性。
 */
function getStep1EntityDisplayText(entity: string): string {
  const trimmed = entity.trim();
  if (!trimmed) return '';

  // 1) 优先尝试解析严格 JSON（若后端直接返回可解析对象字符串）
  if (trimmed.startsWith('{') && trimmed.endsWith('}')) {
    try {
      const parsed: unknown = JSON.parse(trimmed);
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        const record = parsed as Record<string, unknown>;
        const description = typeof record.description === 'string' ? record.description.trim() : '';
        if (description) return description;
      }
    } catch {
      // 忽略：很多模型会返回“类 JSON”（例如 attributes 使用 {a,b,c}），无法直接 JSON.parse
    }

    // 2) 兼容“类 JSON”兜底：用正则提取 description 字段
    const match = trimmed.match(/"description"\s*:\s*"([^"]+)"/);
    if (match?.[1]) return match[1].trim();
  }

  return trimmed;
}

/**
 * Step 1: 需求语义解析结果展示
 */
function Step1Display({
  data,
  onConfirm,
  onModify,
  loading,
  confirmLabel = '确认，继续分析',
  modifyLabel = '修改需求',
  showConfirmButton = true,
  showModifyButton = true,
}: {
  data: Step1Result;
  onConfirm: (payload?: StepConfirmPayload) => void;
  onModify: () => void;
  loading?: boolean;
  confirmLabel?: string;
  modifyLabel?: string;
  showConfirmButton?: boolean;
  showModifyButton?: boolean;
}) {
  return (
    <Card className="p-6 space-y-6 border-2 border-blue-200 dark:border-blue-800 bg-blue-50/30 dark:bg-blue-900/10">
      <div className="flex items-center gap-3 pb-4 border-b border-blue-200 dark:border-blue-800">
        <FileText className="h-6 w-6 text-blue-600" />
        <h3 className="text-xl font-semibold">需求语义解析结果</h3>
      </div>

      {/* 核心需求摘要 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">📋 核心需求摘要</h4>
        <p className="text-base leading-relaxed">{data.summary}</p>
      </div>

      {/* 关键实体 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">🎯 关键实体</h4>
        <div className="flex flex-wrap gap-2">
          {data.entities.map((entity, index) => (
            <Badge key={index} variant="secondary" className="text-sm">
              {getStep1EntityDisplayText(entity)}
            </Badge>
          ))}
        </div>
      </div>

      {/* 关键动作 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">⚡ 关键动作</h4>
        <ul className="list-disc list-inside space-y-1">
          {data.actions.map((action, index) => (
            <li key={index} className="text-sm">{action}</li>
          ))}
        </ul>
      </div>

      {/* 业务场景 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">💼 业务场景</h4>
        <p className="text-sm">{data.businessScenario}</p>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-3 pt-4 border-t">
        {showModifyButton && (
          <Button
            variant="ghost"
            onClick={onModify}
            disabled={loading}
            className="text-muted-foreground hover:text-foreground"
          >
            <Edit2 className="w-4 h-4 mr-2" />
            {modifyLabel}
          </Button>
        )}
        {showConfirmButton && (
          <Button
            onClick={() => onConfirm()}
            disabled={loading}
            className={showModifyButton ? 'flex-1' : 'w-full'}
          >
            <CheckCircle2 className="w-4 h-4 mr-2" />
            {confirmLabel}
            <ArrowRight className="w-4 h-4 ml-2" />
          </Button>
        )}
      </div>
    </Card>
  );
}

/**
 * Step 2: 实体关系建模结果展示
 */
function Step2Display({
  data,
  onConfirm,
  onModify,
  loading,
  confirmLabel = '确认，继续分析',
  modifyLabel = '修改实体',
  showConfirmButton = true,
  showModifyButton = true,
}: {
  data: Step2Result;
  onConfirm: (payload?: StepConfirmPayload) => void;
  onModify: () => void;
  loading?: boolean;
  confirmLabel?: string;
  modifyLabel?: string;
  showConfirmButton?: boolean;
  showModifyButton?: boolean;
}) {
  // 检测是否为空结果
  const isEmpty = data.entities.length === 0 && data.relationships.length === 0;
  /**
   * 兜底提示开关
   *
   * 是什么：标记是否展示“兜底实体”提示。
   * 做什么：当后端返回兜底假设时展示说明。
   * 为什么：避免用户误以为实体结果是完整且精确的。
   */
  const showFallbackHint = Boolean(data.usedFallback || (data.assumptions && data.assumptions.length > 0));

  return (
    <Card className="p-6 space-y-6 border-2 border-purple-200 dark:border-purple-800 bg-purple-50/30 dark:bg-purple-900/10">
      <div className="flex items-center gap-3 pb-4 border-b border-purple-200 dark:border-purple-800">
        <Database className="h-6 w-6 text-purple-600" />
        <h3 className="text-xl font-semibold">实体关系建模结果</h3>
      </div>

      {/* ⚠️ 空结果警告提示 */}
      {isEmpty && (
        <div className="p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg">
          <div className="flex items-start gap-3">
            <span className="text-2xl">⚠️</span>
            <div className="flex-1 space-y-2">
              <h4 className="font-semibold text-yellow-800 dark:text-yellow-200">
                未识别到数据实体
              </h4>
              <p className="text-sm text-yellow-700 dark:text-yellow-300">
                AI未能从需求中识别出数据实体。可能的原因：
              </p>
              <ul className="text-sm text-yellow-700 dark:text-yellow-300 list-disc list-inside space-y-1">
                <li>需求描述过于简单或模糊</li>
                <li>AI模型返回格式异常（已记录日志）</li>
                <li>需求中缺少明确的业务对象</li>
              </ul>
              <p className="text-sm text-yellow-700 dark:text-yellow-300 mt-2">
                建议：点击&ldquo;修改实体&rdquo;重新描述需求，或联系技术支持查看后端日志。
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ℹ️ 兜底实体提示 */}
      {showFallbackHint && !isEmpty && (
        <div className="p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg">
          <div className="flex items-start gap-3">
            <span className="text-xl">ℹ️</span>
            <div className="flex-1 space-y-2">
              <h4 className="font-semibold text-blue-800 dark:text-blue-200">已自动补充最小实体</h4>
              <p className="text-sm text-blue-700 dark:text-blue-300">
                当前需求描述偏概括，为保证分析连续性，系统自动补充了最小可用数据实体。
              </p>
              {data.assumptions && data.assumptions.length > 0 && (
                <ul className="text-sm text-blue-700 dark:text-blue-300 list-disc list-inside space-y-1">
                  {data.assumptions.map((assumption, index) => (
                    <li key={index}>{assumption}</li>
                  ))}
                </ul>
              )}
              <p className="text-sm text-blue-700 dark:text-blue-300 mt-2">
                建议在“修改实体”中补充更具体的业务对象与字段，以获得更精准的数据模型。
              </p>
            </div>
          </div>
        </div>
      )}

      {/* 核心实体列表 */}
      {!isEmpty && (
        <div className="space-y-3">
          <h4 className="text-sm font-medium text-muted-foreground">📊 核心实体列表</h4>
          <div className="space-y-3">
            {data.entities.map((entity, index) => (
              <Card key={index} className="p-4 bg-background">
                <div className="flex items-center justify-between mb-2">
                  <h5 className="font-semibold">{entity.displayName} ({entity.name})</h5>
                </div>
                <div className="space-y-1">
                  {entity.fields.map((field, fieldIndex) => (
                    <div key={fieldIndex} className="text-sm text-muted-foreground flex items-center gap-2">
                      <span className="font-mono text-xs">•</span>
                      <span className="font-mono">{field.name}</span>
                      <span>:</span>
                      <span className="text-blue-600 dark:text-blue-400">{field.type}</span>
                      {field.description && (
                        <span className="text-xs">({field.description})</span>
                      )}
                    </div>
                  ))}
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* 实体关系图 */}
      {!isEmpty && data.relationships.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-muted-foreground">🔗 实体关系</h4>
          <div className="space-y-2">
            {data.relationships.map((rel, index) => (
              <div key={index} className="flex items-center gap-2 text-sm">
                <span className="font-semibold">{rel.from}</span>
                <span className="text-muted-foreground">
                  {rel.type === 'ONE_TO_ONE' && '(1) ─ (1)'}
                  {rel.type === 'ONE_TO_MANY' && '(1) ──< (N)'}
                  {rel.type === 'MANY_TO_MANY' && '(N) ──< (N)'}
                </span>
                <span className="font-semibold">{rel.to}</span>
                {rel.description && (
                  <span className="text-xs text-muted-foreground">- {rel.description}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 操作按钮 */}
      <div className="flex items-center gap-3 pt-4 border-t">
        {showModifyButton && (
          <Button
            variant="ghost"
            onClick={onModify}
            disabled={loading}
            className="text-muted-foreground hover:text-foreground"
          >
            <Edit2 className="w-4 h-4 mr-2" />
            {modifyLabel}
          </Button>
        )}
        {showConfirmButton && (
          <Button
            onClick={() => onConfirm()}
            disabled={loading}
            className={showModifyButton ? 'flex-1' : 'w-full'}
          >
            <CheckCircle2 className="w-4 h-4 mr-2" />
            {confirmLabel}
            <ArrowRight className="w-4 h-4 ml-2" />
          </Button>
        )}
      </div>
    </Card>
  );
}

/**
 * Step 3: 功能意图识别结果展示
 */
function Step3Display({
  data,
  onConfirm,
  onModify,
  loading,
  confirmLabel = '确认，继续分析',
  modifyLabel = '修改功能',
  showConfirmButton = true,
  showModifyButton = true,
}: {
  data: Step3Result;
  onConfirm: (payload?: StepConfirmPayload) => void;
  onModify: () => void;
  loading?: boolean;
  confirmLabel?: string;
  modifyLabel?: string;
  showConfirmButton?: boolean;
  showModifyButton?: boolean;
}) {
  const intentDisplayMap = {
    CLONE: { label: '克隆现有应用', color: 'bg-green-500' },
    DESIGN: { label: '设计新应用', color: 'bg-blue-500' },
    HYBRID: { label: '混合模式', color: 'bg-purple-500' }
  };

  const intentInfo = intentDisplayMap[data.intent];

  return (
    <Card className="p-6 space-y-6 border-2 border-green-200 dark:border-green-800 bg-green-50/30 dark:bg-green-900/10">
      <div className="flex items-center gap-3 pb-4 border-b border-green-200 dark:border-green-800">
        <Cpu className="h-6 w-6 text-green-600" />
        <h3 className="text-xl font-semibold">功能意图识别结果</h3>
      </div>

      {/* 意图类型和置信度 */}
      <div className="flex items-center justify-between">
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-muted-foreground">🎯 识别意图</h4>
          <Badge className={cn("text-white", intentInfo.color)}>
            {intentInfo.label}
          </Badge>
        </div>
        <div className="text-right">
          <h4 className="text-sm font-medium text-muted-foreground">📊 置信度</h4>
          <div className="text-2xl font-bold">{(data.confidence * 100).toFixed(0)}%</div>
        </div>
      </div>

      {/* 关键词 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">🔑 关键词</h4>
        <div className="flex flex-wrap gap-2">
          {data.keywords.map((keyword, index) => (
            <Badge key={index} variant="secondary">
              {keyword}
            </Badge>
          ))}
        </div>
      </div>

      {/* 定制需求 */}
      {data.customizationRequirement && (
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-muted-foreground">📝 定制需求</h4>
          <p className="text-sm">{data.customizationRequirement}</p>
        </div>
      )}

      {/* 核心功能模块 */}
      <div className="space-y-3">
        <h4 className="text-sm font-medium text-muted-foreground">📦 核心功能模块</h4>
        <div className="space-y-3">
          {data.modules.map((module, index) => (
            <Card key={index} className="p-4 bg-background">
              <div className="flex items-center gap-2 mb-2">
                <CheckCircle2 className="h-4 w-4 text-green-600" />
                <h5 className="font-semibold">{module.displayName} ({module.name})</h5>
              </div>
              <p className="text-sm text-muted-foreground mb-2">{module.description}</p>
              <ul className="space-y-1">
                {module.features.map((feature, featureIndex) => (
                  <li key={featureIndex} className="text-sm flex items-center gap-2">
                    <span className="text-muted-foreground">-</span>
                    <span>{feature}</span>
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </div>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-3 pt-4 border-t">
        {showModifyButton && (
          <Button
            variant="ghost"
            onClick={onModify}
            disabled={loading}
            className="text-muted-foreground hover:text-foreground"
          >
            <Edit2 className="w-4 h-4 mr-2" />
            {modifyLabel}
          </Button>
        )}
        {showConfirmButton && (
          <Button
            onClick={() => onConfirm()}
            disabled={loading}
            className={showModifyButton ? 'flex-1' : 'w-full'}
          >
            <CheckCircle2 className="w-4 h-4 mr-2" />
            {confirmLabel}
            <ArrowRight className="w-4 h-4 ml-2" />
          </Button>
        )}
      </div>
    </Card>
  );
}

/**
 * Step 4: 技术架构选型结果展示
 */
function Step4Display({
  data,
  onConfirm,
  onModify,
  loading,
  confirmLabel = '确认，继续分析',
  modifyLabel = '修改技术栈',
  showConfirmButton = true,
  showModifyButton = true,
}: {
  data: Step4Result;
  onConfirm: (payload?: StepConfirmPayload) => void;
  onModify: () => void;
  loading?: boolean;
  confirmLabel?: string;
  modifyLabel?: string;
  showConfirmButton?: boolean;
  showModifyButton?: boolean;
}) {
  return (
    <Card className="p-6 space-y-6 border-2 border-orange-200 dark:border-orange-800 bg-orange-50/30 dark:bg-orange-900/10">
      <div className="flex items-center gap-3 pb-4 border-b border-orange-200 dark:border-orange-800">
        <Layout className="h-6 w-6 text-orange-600" />
        <h3 className="text-xl font-semibold">技术架构选型结果</h3>
      </div>

      {/* 前端技术栈 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">🎨 前端技术栈</h4>
        <ul className="space-y-1">
          {data.frontend.map((tech, index) => (
            <li key={index} className="text-sm flex items-center gap-2">
              <span>•</span>
              <span className="font-semibold">{tech.name}</span>
              {tech.version && <span className="text-muted-foreground">v{tech.version}</span>}
              {tech.description && <span className="text-xs text-muted-foreground">- {tech.description}</span>}
            </li>
          ))}
        </ul>
      </div>

      {/* 后端技术栈 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">⚙️ 后端技术栈</h4>
        <ul className="space-y-1">
          {data.backend.map((tech, index) => (
            <li key={index} className="text-sm flex items-center gap-2">
              <span>•</span>
              <span className="font-semibold">{tech.name}</span>
              {tech.version && <span className="text-muted-foreground">v{tech.version}</span>}
              {tech.description && <span className="text-xs text-muted-foreground">- {tech.description}</span>}
            </li>
          ))}
        </ul>
      </div>

      {/* 架构模式 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">🏗️ 架构模式</h4>
        <div className="flex flex-wrap gap-2">
          {data.architecturePatterns.map((pattern, index) => (
            <Badge key={index} variant="outline">
              {pattern}
            </Badge>
          ))}
        </div>
      </div>

      {/* 第三方服务 */}
      {data.thirdPartyServices.length > 0 && (
        <div className="space-y-2">
          <h4 className="text-sm font-medium text-muted-foreground">🔌 第三方服务</h4>
          <ul className="space-y-1">
            {data.thirdPartyServices.map((service, index) => (
              <li key={index} className="text-sm flex items-center gap-2">
                <span>•</span>
                <span className="font-semibold">{service.name}</span>
                <span className="text-muted-foreground">- {service.purpose}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 选型理由 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">💡 选型理由</h4>
        <p className="text-sm leading-relaxed">{data.reasoning}</p>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-3 pt-4 border-t">
        {showModifyButton && (
          <Button
            variant="ghost"
            onClick={onModify}
            disabled={loading}
            className="text-muted-foreground hover:text-foreground"
          >
            <Edit2 className="w-4 h-4 mr-2" />
            {modifyLabel}
          </Button>
        )}
        {showConfirmButton && (
          <Button
            onClick={() => onConfirm()}
            disabled={loading}
            className={showModifyButton ? 'flex-1' : 'w-full'}
          >
            <CheckCircle2 className="w-4 h-4 mr-2" />
            {confirmLabel}
            <ArrowRight className="w-4 h-4 ml-2" />
          </Button>
        )}
      </div>
    </Card>
  );
}

/**
 * Step 5: 复杂度与风险评估结果展示
 */
/**
 * Step 5: 交互设计与体验评估结果展示
 */
/**
 * Step 5: 交互设计与体验评估结果展示
 */
function Step5Display({
  data,
  onConfirm,
  onModify,
  loading,
  confirmLabel = '确认，进入深度规划',
  modifyLabel = '修改评估',
  showConfirmButton = true,
  showModifyButton = true,
}: {
  data: Step5Result;
  onConfirm: (payload?: StepConfirmPayload) => void;
  onModify: () => void;
  loading?: boolean;
  confirmLabel?: string;
  modifyLabel?: string;
  showConfirmButton?: boolean;
  showModifyButton?: boolean;
}) {
  const [selectedStyleId, setSelectedStyleId] = React.useState<string | null>(
    data.selectedStyleId ?? data.styleVariants?.[0]?.styleId ?? null
  );

  // 如果有设计风格变体，展示风格选择界面
  if (data.styleVariants && data.styleVariants.length > 0) {
    return (
      <Card className="p-6 space-y-6 border-2 border-purple-200 dark:border-purple-800 bg-purple-50/30 dark:bg-purple-900/10">
        <div className="flex items-center gap-3 pb-4 border-b border-purple-200 dark:border-purple-800">
          <Palette className="h-6 w-6 text-purple-600" />
          <div>
            <h3 className="text-xl font-semibold">交互设计与风格决策</h3>
            {data.designConfidence && (
              <div className="text-xs text-muted-foreground mt-1">
                AI 置信度: {Math.round(data.designConfidence * 100)}% | 意图: {data.designIntent}
              </div>
            )}
            {data.selectedStyleReason && (
              <div className="text-xs text-muted-foreground mt-1">
                推荐理由: {data.selectedStyleReason}
              </div>
            )}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {data.styleVariants.map((variant) => {
            const isSelected = selectedStyleId === variant.styleId;
            return (
              <div
                key={variant.styleId}
                onClick={() => setSelectedStyleId(variant.styleId)}
                className={cn(
                  "cursor-pointer rounded-lg border-2 p-4 transition-all hover:scale-105",
                  isSelected
                    ? "border-purple-600 bg-purple-100/50 dark:bg-purple-900/30 shadow-md"
                    : "border-transparent bg-background/50 hover:border-purple-300"
                )}
              >
                <div className="aspect-video w-full rounded-md bg-gradient-to-br from-gray-100 to-gray-200 dark:from-gray-800 dark:to-gray-900 mb-3 flex items-center justify-center overflow-hidden relative">
                   {/* 简单的色块模拟预览 */}
                   <div className={cn("w-full h-full opacity-60", 
                      variant.styleCode.includes('minimal') ? "bg-slate-200" :
                      variant.styleCode.includes('vibrant') ? "bg-orange-200" :
                      "bg-blue-200"
                   )} />
                   <div className="absolute inset-0 flex items-center justify-center font-mono text-xs opacity-50">
                      {variant.styleCode}
                   </div>
                </div>
                <div className="flex items-center justify-between">
                  <h4 className="font-semibold text-sm">{variant.styleName}</h4>
                  {isSelected && <CheckCircle2 className="w-4 h-4 text-purple-600" />}
                </div>
              </div>
            );
          })}
        </div>

        <div className="flex items-center gap-3 pt-4 border-t border-purple-200 dark:border-purple-800">
           {showModifyButton && (
            <Button variant="ghost" onClick={onModify} disabled={loading}>
              <Edit2 className="w-4 h-4 mr-2" />
              调整需求
            </Button>
          )}
          {showConfirmButton && (
            <Button
              onClick={() =>
                onConfirm(
                  selectedStyleId
                    ? { selectedStyleId }
                    : undefined
                )
              }
              disabled={loading}
              className="flex-1 bg-purple-600 hover:bg-purple-700"
            >
              <CheckCircle2 className="w-4 h-4 mr-2" />
              确认使用此风格
            </Button>
          )}
        </div>
      </Card>
    );
  }

  // 默认 fallback：原有体验评估视图
  const getRiskColor = (level: 'HIGH' | 'MEDIUM' | 'LOW') => {
    switch (level) {
      case 'HIGH': return 'text-purple-600 dark:text-purple-400';
      case 'MEDIUM': return 'text-pink-600 dark:text-pink-400';
      case 'LOW': return 'text-blue-600 dark:text-blue-400';
    }
  };

  const getRiskIcon = (level: 'HIGH' | 'MEDIUM' | 'LOW') => {
    switch (level) {
      case 'HIGH': return '⚡';
      case 'MEDIUM': return '🎨';
      case 'LOW': return '✨';
    }
  };

  return (
    <Card className="p-6 space-y-6 border-2 border-purple-200 dark:border-purple-800 bg-purple-50/30 dark:bg-purple-900/10">
      <div className="flex items-center gap-3 pb-4 border-b border-purple-200 dark:border-purple-800">
        <Palette className="h-6 w-6 text-purple-600" />
        <h3 className="text-xl font-semibold">交互设计与体验评估</h3>
      </div>

      {/* 交互体感评分 */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">✨ 交互体感评分</h4>
        <div className="flex items-center gap-4">
          <div className="text-4xl font-bold text-purple-600">{data.complexityScore}/10</div>
          <div className="text-sm text-muted-foreground">
            {data.complexityScore >= 8 ? '极致体验' : data.complexityScore >= 5 ? '标准体验' : '基础体验'}
          </div>
        </div>
      </div>

      {/* 设计维度拆解 (原复杂度拆解) */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">📐 设计维度模型</h4>
        <div className="space-y-2">
          {Object.entries(data.complexityBreakdown).map(([key, value]) => (
            <div key={key} className="flex items-center gap-2">
              <span className="text-sm w-24">
                {key === 'frontend' ? '界面交互' : 
                 key === 'backend' ? '流程逻辑' : 
                 key === 'database' ? '信息架构' : '视觉表现'}
              </span>
              <div className="flex-1 h-2 bg-secondary rounded-full overflow-hidden">
                <div
                  className="h-full bg-purple-500 transition-all duration-500"
                  style={{ width: `${value * 10}%` }}
                />
              </div>
              <span className="text-sm font-mono w-12 text-right">{value}/10</span>
            </div>
          ))}
        </div>
      </div>

      {/* 体验痛点与挑战 (原技术风险) */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">💡 体验痛点与挑战</h4>
        <div className="space-y-2">
          {['HIGH', 'MEDIUM', 'LOW'].map((level) => {
            const risks = data.risks.filter(r => r.level === level);
            if (risks.length === 0) return null;

            return (
              <div key={level} className="space-y-1">
                <h5 className={cn("text-sm font-semibold", getRiskColor(level as 'HIGH' | 'MEDIUM' | 'LOW'))}>
                  {getRiskIcon(level as 'HIGH' | 'MEDIUM' | 'LOW')} {
                    level === 'HIGH' ? '核心痛点' : 
                    level === 'MEDIUM' ? '体验提升点' : '优化建议'
                  }
                </h5>
                <ul className="space-y-1 ml-6">
                  {risks.map((risk, index) => (
                    <li key={index} className="text-sm text-foreground/80">• {risk.description}</li>
                  ))}
                </ul>
              </div>
            );
          })}
        </div>
      </div>

      {/* 设计资源预估 (原工作量预估) */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">🎨 设计资源预估</h4>
        <div className="grid grid-cols-3 gap-4">
          <div>
            <div className="text-xs text-muted-foreground">关键页面</div>
            <div className="text-lg font-semibold">{data.estimatedWorkload.featureCount}个</div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">设计周期</div>
            <div className="text-lg font-semibold">{data.estimatedWorkload.estimatedWeeks}</div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">设计师</div>
            <div className="text-lg font-semibold">{data.estimatedWorkload.teamSize}</div>
          </div>
        </div>
      </div>

      {/* 设计优化策略 (原缓解措施) */}
      <div className="space-y-2">
        <h4 className="text-sm font-medium text-muted-foreground">🚀 设计优化策略</h4>
        <ul className="space-y-1">
          {data.mitigations.map((mitigation, index) => (
            <li key={index} className="text-sm flex items-start gap-2">
              <span className="text-purple-600 mt-0.5">✓</span>
              <span>{mitigation}</span>
            </li>
          ))}
        </ul>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-3 pt-4 border-t">
        {showModifyButton && (
          <Button
            variant="ghost"
            onClick={onModify}
            disabled={loading}
            className="text-muted-foreground hover:text-foreground"
          >
            <Edit2 className="w-4 h-4 mr-2" />
            {modifyLabel}
          </Button>
        )}
        {showConfirmButton && (
          <Button
            onClick={() => onConfirm()}
            disabled={loading}
            className={showModifyButton ? 'flex-1' : 'w-full'}
          >
            <CheckCircle2 className="w-4 h-4 mr-2" />
            {confirmLabel}
            <ArrowRight className="w-4 h-4 ml-2" />
          </Button>
        )}
      </div>
    </Card>
  );
}

/**
 * 步骤结果展示组件（主组件）
 */
export function StepResultDisplay({
  result,
  onConfirm,
  onModify,
  loading = false,
  confirmLabel,
  modifyLabel,
  showConfirmButton = true,
  showModifyButton = true,
}: StepResultDisplayProps) {
  switch (result.step) {
    case 1:
      return (
        <Step1Display
          data={result.data}
          onConfirm={onConfirm}
          onModify={onModify}
          loading={loading}
          confirmLabel={confirmLabel}
          modifyLabel={modifyLabel}
          showConfirmButton={showConfirmButton}
          showModifyButton={showModifyButton}
        />
      );
    case 2:
      return (
        <Step2Display
          data={result.data}
          onConfirm={onConfirm}
          onModify={onModify}
          loading={loading}
          confirmLabel={confirmLabel}
          modifyLabel={modifyLabel}
          showConfirmButton={showConfirmButton}
          showModifyButton={showModifyButton}
        />
      );
    case 3:
      return (
        <Step3Display
          data={result.data}
          onConfirm={onConfirm}
          onModify={onModify}
          loading={loading}
          confirmLabel={confirmLabel}
          modifyLabel={modifyLabel}
          showConfirmButton={showConfirmButton}
          showModifyButton={showModifyButton}
        />
      );
    case 4:
      return (
        <Step4Display
          data={result.data}
          onConfirm={onConfirm}
          onModify={onModify}
          loading={loading}
          confirmLabel={confirmLabel}
          modifyLabel={modifyLabel}
          showConfirmButton={showConfirmButton}
          showModifyButton={showModifyButton}
        />
      );
    case 5:
      return (
        <Step5Display
          data={result.data}
          onConfirm={onConfirm}
          onModify={onModify}
          loading={loading}
          confirmLabel={confirmLabel}
          modifyLabel={modifyLabel}
          showConfirmButton={showConfirmButton}
          showModifyButton={showModifyButton}
        />
      );
    default:
      return null;
  }
}
