"use client";

import { useState, useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

import { AnalysisProgressPanel } from "@/components/analysis/AnalysisProgressPanel";
import { IntentResultPanel } from "@/components/intent/intent-result-panel";
import { TemplateSelectionPanel } from "@/components/intent/template-selection-panel";
import { StyleSelectionPanel } from "@/components/style/style-selection-panel";
import { PrototypePreviewPanel } from "@/components/prototype/prototype-preview-panel";
import type { FileNode } from "@/components/prototype/code-file-tree";
import { LoginDialog } from "@/components/auth/login-dialog";
import { useAnalysisSse } from "@/hooks/use-analysis-sse";
import type {
  IntentClassificationResult
} from "@/types/intent";
import { RequirementIntent } from "@/types/intent";
import { classifyIntentStream } from "@/lib/api/intent";
import { getMatchedTemplates } from "@/lib/api/templates";
import { applyAiCodeToSandbox, generatePrototypeStream, restartVite, type SSEProgressEvent } from "@/lib/api/prototype";
import { getApiBaseUrl } from "@/lib/api/base-url";
import { getToken } from "@/lib/auth/token";
import { normalizeApiResponse } from "@/lib/api/response";
import type { Template } from "@/types/template";
import { DesignStyle, getStyleDisplayInfo } from "@/types/design-style";
import { useToast } from "@/hooks/use-toast";
import {
  ArrowLeft,
  Sparkles,
  Type,
  Image as ImageIcon,
  Mic,
  FileText,
  CheckCircle2,
  Circle,
  Loader2,
  Cpu,
  ChevronDown,
  Link,
  Database,
  Table,
  Key,
  FileJson,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { COMPLEXITY_CATEGORIES } from "./complexity-category-card";

/**
 * 输入模式枚举
 */
type InputMode = 'TEXT' | 'IMAGE' | 'AUDIO' | 'DOC' | 'LINK';

/**
 * 输入模式配置
 */
const INPUT_MODES: { mode: InputMode; label: string; icon: React.ReactNode }[] = [
  { mode: 'TEXT', label: '创意描述', icon: <Type className="w-4 h-4" /> },
  { mode: 'IMAGE', label: '设计图', icon: <ImageIcon className="w-4 h-4" /> },
  { mode: 'AUDIO', label: '语音描述', icon: <Mic className="w-4 h-4" /> },
  { mode: 'DOC', label: 'PRD文档', icon: <FileText className="w-4 h-4" /> },
  { mode: 'LINK', label: '参考链接', icon: <Link className="w-4 h-4" /> },
];

/**
 * V2.0 向导流程步骤枚举
 *
 * 7步完整流程：
 * 1. IDLE - 初始状态（用户输入需求）
 * 2. ANALYZING - AI意图分析中
 * 3. INTENT_RESULT - 意图识别结果展示
 * 4. TEMPLATE_SELECTION - 行业模板选择（可跳过）
 * 5. STYLE_SELECTION - 7种风格选择（必选）
 * 6. PROTOTYPE_PREVIEW - 原型预览确认（阻塞确认点）
 * 7. BACKEND_GENERATION - 后端代码生成
 */
type WizardStep =
  | 'IDLE'                    // 初始状态（编辑模式）
  | 'ANALYZING'               // 意图分析中
  | 'INTENT_RESULT'           // 意图识别结果展示
  | 'TEMPLATE_SELECTION'      // 模板选择（可跳过）
  | 'STYLE_SELECTION'         // 风格选择（必选）
  | 'PROTOTYPE_PREVIEW'       // 原型预览确认
  | 'BACKEND_GENERATION';     // 后端代码生成

/**
 * 向导流程数据接口
 *
 * 存储整个向导流程中收集的所有数据
 */
interface WizardData {
  requirement: string;                                  // 用户需求描述
  intentResult: IntentClassificationResult | null;      // 意图识别结果
  matchedTemplates: Template[];                         // 匹配的模板列表
  selectedTemplate: Template | null;                    // 选中的行业模板（可选）
  selectedStyle: DesignStyle | null;                    // 选中的设计风格
  sandboxUrl: string | null;                            // 沙箱预览URL
  sandboxId: string | null;                             // 沙箱ID（用于写入代码/刷新预览）
  /** 原型生成失败的错误信息 */
  prototypeError: string | null;
  appSpecId: string | null;                             // 应用规格ID
  /** V2.0新增：用户预选的复杂度分类ID（SIMPLE/MEDIUM/COMPLEX/NEEDS_CONFIRMATION） */
  selectedComplexityHint: string | null;
  /** V2.0新增：用户预选的技术栈提示 */
  selectedTechStackHint: string | null;
}

/**
 * 详情项类型定义
 */
type TimelineDetailItem = string | { label: string; content?: React.ReactNode };

/**
 * ER图预览组件 - 用于教育展示
 */
function ERDiagramPreview({ complexityHint }: { complexityHint: string | null }) {
  // 根据复杂度/类型选择示例数据
  const getExampleData = () => {
    // 电商/复杂应用
    if (complexityHint === 'COMPLEX') {
      return {
        title: "电商系统数据模型示例",
        tables: [
          {
            name: "Users (用户表)",
            fields: [
              { name: "id", type: "PK", desc: "用户ID" },
              { name: "username", type: "Text", desc: "用户名" },
              { name: "email", type: "Text", desc: "邮箱" }
            ]
          },
          {
            name: "Orders (订单表)",
            fields: [
              { name: "id", type: "PK", desc: "订单ID" },
              { name: "user_id", type: "FK", desc: "关联用户" },
              { name: "total", type: "Money", desc: "总金额" },
              { name: "status", type: "Enum", desc: "订单状态" }
            ]
          }
        ]
      };
    }
    // 博客/Web应用
    if (complexityHint === 'MEDIUM') {
      return {
        title: "博客系统数据模型示例",
        tables: [
          {
            name: "Posts (文章表)",
            fields: [
              { name: "id", type: "PK", desc: "文章ID" },
              { name: "title", type: "Text", desc: "标题" },
              { name: "content", type: "Text", desc: "内容" },
              { name: "author_id", type: "FK", desc: "作者ID" }
            ]
          },
          {
            name: "Comments (评论表)",
            fields: [
              { name: "id", type: "PK", desc: "评论ID" },
              { name: "post_id", type: "FK", desc: "关联文章" },
              { name: "body", type: "Text", desc: "评论内容" }
            ]
          }
        ]
      };
    }
    // 默认/简单应用
    return {
      title: "待办事项数据模型示例",
      tables: [
        {
          name: "Tasks (任务表)",
          fields: [
            { name: "id", type: "PK", desc: "任务ID" },
            { name: "title", type: "Text", desc: "任务名称" },
            { name: "is_completed", type: "Bool", desc: "是否完成" },
            { name: "due_date", type: "Date", desc: "截止日期" }
          ]
        }
      ]
    };
  };

  const data = getExampleData();

  return (
    <div className="mt-3 rounded-xl border bg-muted/30 p-4 space-y-3 animate-in fade-in slide-in-from-top-2">
      <div className="flex items-center gap-2 text-xs font-semibold text-muted-foreground mb-2">
        <Database className="w-3.5 h-3.5" />
        <span>{data.title}</span>
        <Badge variant="outline" className="text-[10px] h-4 px-1 ml-auto font-normal">
          AI自动设计
        </Badge>
      </div>

      <div className="flex flex-col gap-3">
        {data.tables.map((table, idx) => (
          <div key={idx} className="bg-background rounded-lg border shadow-sm p-3">
            <div className="flex items-center gap-2 mb-2 border-b pb-2">
              <Table className="w-3.5 h-3.5 text-blue-500" />
              <span className="text-xs font-bold">{table.name}</span>
            </div>
            <div className="space-y-1.5">
              {table.fields.map((field, fIdx) => (
                <div key={fIdx} className="flex items-center text-[10px] justify-between">
                  <div className="flex items-center gap-1.5">
                    {field.type === 'PK' && <Key className="w-3 h-3 text-yellow-500 rotate-45" />}
                    {field.type === 'FK' && <Key className="w-3 h-3 text-blue-400 rotate-45" />}
                    <span className={field.type === 'PK' ? 'font-semibold text-foreground' : 'text-muted-foreground'}>
                      {field.name}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-muted-foreground/60">{field.desc}</span>
                    <Badge variant="secondary" className="text-[9px] h-4 px-1 rounded-sm font-mono text-muted-foreground">
                      {field.type}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      <div className="text-[10px] text-muted-foreground/70 flex gap-1.5 items-start bg-blue-50 dark:bg-blue-900/20 p-2 rounded-lg">
        <FileJson className="w-3 h-3 shrink-0 mt-0.5 text-blue-500" />
        <p>AI会根据您的需求自动设计最佳数据库结构。就像Excel表格一样，&quot;表&quot;用来存储数据，&quot;字段&quot;定义数据类型。</p>
      </div>
    </div>
  );
}

// Helper component for Timeline Step
function TimelineStep({
  status,
  title,
  description,
  details,
  isLast = false
}: {
  status: 'completed' | 'active' | 'pending';
  title: string;
  description: string;
  details?: TimelineDetailItem[];
  isLast?: boolean;
}) {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <div className="relative pl-8 pb-6 last:pb-0">
      {/* Line connector */}
      {!isLast && (
        <div className={`absolute left-[11px] top-8 bottom-0 w-[2px] ${status === 'completed' ? 'bg-primary' : 'bg-border'
          }`} />
      )}

      {/* Status Icon */}
      <div className={`absolute left-0 top-1 w-6 h-6 rounded-full border-2 flex items-center justify-center bg-background z-10 ${status === 'completed' ? 'border-primary text-primary' :
        status === 'active' ? 'border-blue-500 text-blue-500' :
          'border-muted-foreground/30 text-muted-foreground/30'
        }`}>
        {status === 'completed' && <CheckCircle2 className="w-3.5 h-3.5" />}
        {status === 'active' && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
        {status === 'pending' && <Circle className="w-3.5 h-3.5" />}
      </div>

      <div className="flex flex-col">
        <div
          className={`flex items-center gap-2 cursor-pointer group ${details ? 'hover:opacity-80' : ''}`}
          onClick={() => details && setIsExpanded(!isExpanded)}
        >
          <span className={`text-sm font-bold ${status === 'active' ? 'text-blue-500' :
            status === 'completed' ? 'text-foreground' : 'text-muted-foreground'
            }`}>
            {title}
          </span>
          {details && (
            <div className={`
              flex h-6 w-6 items-center justify-center rounded-full border transition-all duration-200
              ${isExpanded
                ? 'bg-primary/10 border-primary/20 text-primary rotate-180'
                : 'bg-muted/30 border-transparent text-muted-foreground hover:bg-muted hover:text-foreground'
              }
            `}>
              <ChevronDown className="w-4 h-4" />
            </div>
          )}
        </div>

        <span className="text-xs text-muted-foreground mt-1 font-medium">
          {description}
        </span>

        {/* Expandable Details */}
        {isExpanded && details && (
          <div className="mt-2 space-y-1 animate-in fade-in slide-in-from-top-1 duration-200">
            {details.map((detail, idx) => {
              const label = typeof detail === 'string' ? detail : detail.label;
              const content = typeof detail === 'string' ? null : detail.content;

              return (
                <div key={idx} className="flex flex-col gap-1 ml-1 mb-2 last:mb-0">
                  <div className="flex items-center gap-2 text-[10px] text-muted-foreground/80">
                    <div className="w-1 h-1 rounded-full bg-muted-foreground/40" />
                    <span>{label}</span>
                  </div>
                  {content && (
                    <div className="pl-3">
                      {content}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * HeroBanner组件 - 首页Hero区域
 *
 * 核心功能：
 * - 展示主标题"你的创意，AI 来实现，让每个想法都长成应用"
 * - 提供快速输入框（可直接开始生成）
 * - "免费开始"按钮导航到/create
 * - "观看1分钟示例"按钮打开演示模态框
 * - 横向滚动的模板快捷入口
 *
 * @example
 * <HeroBanner />
 */
export function HeroBanner({
  externalRequirement,
  onRequirementChange,
  externalComplexityHint,
  externalTechStackHint,
  onWizardActiveChange
}: {
  externalRequirement?: string;
  onRequirementChange?: (val: string) => void;
  externalComplexityHint?: string;
  externalTechStackHint?: string;
  onWizardActiveChange?: (isActive: boolean) => void;
} = {}): React.ReactElement {
  const { toast } = useToast();

  // V2.0 状态机：替换原有的 boolean 状态
  const [wizardStep, setWizardStep] = useState<WizardStep>('IDLE');

  // 使用 ref 跟踪当前步骤，避免 SSE 回调中的闭包陷阱
  const wizardStepRef = useRef<WizardStep>(wizardStep);
  useEffect(() => {
    wizardStepRef.current = wizardStep;
  }, [wizardStep]);

  // Notify parent when wizard state changes
  useEffect(() => {
    onWizardActiveChange?.(wizardStep !== 'IDLE');
  }, [wizardStep, onWizardActiveChange]);

  const [inputMode, setInputMode] = useState<InputMode>('TEXT');

  // V2.0 向导数据：存储整个流程中收集的数据
  const [wizardData, setWizardData] = useState<WizardData>({
    requirement: '',
    intentResult: null,
    matchedTemplates: [],
    selectedTemplate: null,
    selectedStyle: null,
    sandboxUrl: null,
    sandboxId: null,
    prototypeError: null,
    appSpecId: null,
    selectedComplexityHint: null,
    selectedTechStackHint: null,
  });

  // Sync external requirement changes
  useEffect(() => {
    setWizardData(prev => {
      const next = { ...prev };
      let changed = false;

      if (externalRequirement !== undefined && prev.requirement !== externalRequirement) {
        next.requirement = externalRequirement;
        changed = true;
      }
      if (externalComplexityHint !== undefined && prev.selectedComplexityHint !== externalComplexityHint) {
        next.selectedComplexityHint = externalComplexityHint;
        changed = true;
      }
      if (externalTechStackHint !== undefined && prev.selectedTechStackHint !== externalTechStackHint) {
        next.selectedTechStackHint = externalTechStackHint;
        changed = true;
      }

      return changed ? next : prev;
    });
  }, [externalRequirement, externalComplexityHint, externalTechStackHint]);

  // 模板加载状态
  const [loadingTemplates, setLoadingTemplates] = useState(false);

  // 原型生成加载状态
  const [loadingPrototype, setLoadingPrototype] = useState(false);

  // 代码正在生成中（沙箱已创建但代码未完成）
  const [isCodeGenerating, setIsCodeGenerating] = useState(false);

  // 登录对话框状态（未登录时弹出）
  const [loginDialogOpen, setLoginDialogOpen] = useState(false);

  // AI思考内容（流式输出）
  const [thinkingContent, setThinkingContent] = useState('');

  // 原型生成流式代码（实时显示）
  const [streamedCode, setStreamedCode] = useState('');

  // 原型生成思考过程（流式显示）
  const [prototypeThinking, setPrototypeThinking] = useState('');

  // 生成的文件列表（用于代码视图）
  const [generatedFiles, setGeneratedFiles] = useState<FileNode[]>([]);

  // 计时器状态
  const [elapsedTime, setElapsedTime] = useState(0);

  // 方案A：使用 ref 累积所有 stream 事件的文本，用于最终解析文件
  const streamAccumulatorRef = useRef<string>('');

  // 使用SSE订阅意图分析进度
  const { state, connect, disconnect: disconnectAnalysis } = useAnalysisSse({
    requirement: wizardData.requirement,
    autoConnect: false,
    onProgress: (message) => {
      console.log('[V2.0] 意图分析进度:', message);
    },
    onComplete: async () => {
      console.log('[V2.0] 意图分析步骤完成，开始流式意图识别');
      setThinkingContent('正在深入分析需求意图...\n');

      try {
        await classifyIntentStream(
          wizardData.requirement,
          {
            complexityHint: wizardData.selectedComplexityHint ?? undefined,
            techStackHint: wizardData.selectedTechStackHint ?? undefined,
          },
          {
            onThinking: (content) => {
              setThinkingContent(prev => prev + content);
            },
            onComplete: (intentResult) => {
              console.log('[V2.0] 意图识别完成:', intentResult);

              // 更新向导数据
              setWizardData(prev => ({
                ...prev,
                intentResult,
              }));

              // 切换到意图结果展示步骤
              setWizardStep('INTENT_RESULT');

              // 显示成功提示
              toast({
                title: '意图识别完成',
                description: `AI识别为${intentResult.intent}模式，置信度${(intentResult.confidence * 100).toFixed(0)}%`,
              });
            },
            onError: (error) => {
              console.error('[V2.0] 流式意图识别错误:', error);
              // 错误处理逻辑...
              const errorMessage = typeof error === 'string' ? error : '意图识别失败';
              if (errorMessage.includes('未登录')) {
                setLoginDialogOpen(true);
              } else {
                toast({
                  title: '意图识别失败',
                  description: errorMessage,
                  variant: 'destructive',
                });
              }
              // 出错也允许重试
              // setWizardStep('IDLE'); // 保持在当前页面让用户看到错误可能更好，或者提供重试按钮
            }
          }
        );
      } catch (error) {
        console.error('[V2.0] 启动流式识别失败:', error);
      }
    },
    onError: (error) => {
      console.error('[V2.0] 意图分析错误:', error);

      // 检测是否为未登录错误
      const errorMessage = typeof error === 'string' ? error : '';
      const isAuthError = errorMessage.includes('未登录') ||
        errorMessage.includes('登录已失效') ||
        errorMessage.includes('请先登录');

      if (isAuthError) {
        // 未登录：显示登录对话框而非错误toast
        console.log('[V2.0] 检测到未登录状态，弹出登录对话框');
        setLoginDialogOpen(true);
      } else {
        // 其他错误：显示错误提示
        toast({
          title: 'SSE连接失败',
          description: errorMessage || '无法连接到服务器，请检查网络或稍后重试',
          variant: 'destructive',
        });
      }

      // 仅在分析阶段才返回IDLE，避免影响后续步骤（如原型预览）
      // 使用 ref 获取最新步骤状态，避免闭包陷阱
      if (wizardStepRef.current === 'ANALYZING') {
        console.log('[V2.0] 分析阶段出错，返回IDLE状态');
        setWizardStep('IDLE');
      } else {
        console.log('[V2.0] 当前步骤为', wizardStepRef.current, '，忽略SSE错误');
      }
    }
  });

  // 计时器逻辑
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (loadingPrototype) {
      setElapsedTime(0);
      interval = setInterval(() => {
        setElapsedTime(prev => prev + 1);
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [loadingPrototype]);

  /**
   * V2.0 处理需求提交 - 开始意图识别流程
   *
   * 步骤：
   * 1. 验证用户输入
   * 2. 切换到ANALYZING状态
   * 3. 启动SSE流式分析（意图识别）
   */
  const handleSubmitRequirement = () => {
    if (!wizardData.requirement.trim()) {
      console.warn('[V2.0] 用户输入为空，无法开始分析');
      return;
    }

    console.log('[V2.0] 开始意图识别流程:', wizardData.requirement);

    // 切换到ANALYZING状态
    setWizardStep('ANALYZING');

    // 连接SSE开始意图分析
    connect();
  };

  /**
   * V2.0 返回上一步
   *
   * 根据当前步骤返回到正确的上一步
   */
  const handleBack = () => {
    console.log('[V2.0] 返回上一步，当前步骤:', wizardStep);

    switch (wizardStep) {
      case 'ANALYZING':
      case 'INTENT_RESULT':
        // 从分析或意图结果页返回到编辑模式
        setWizardStep('IDLE');
        break;
      case 'TEMPLATE_SELECTION':
        // 从模板选择返回到意图结果
        setWizardStep('INTENT_RESULT');
        break;
      case 'STYLE_SELECTION':
        // 从风格选择返回到模板选择（如果有）或意图结果
        if (wizardData.intentResult?.intent === RequirementIntent.CLONE_EXISTING_WEBSITE ||
          wizardData.intentResult?.intent === RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE) {
          setWizardStep('TEMPLATE_SELECTION');
        } else {
          setWizardStep('INTENT_RESULT');
        }
        break;
      case 'PROTOTYPE_PREVIEW':
        // 从原型预览返回到风格选择
        setWizardStep('STYLE_SELECTION');
        break;
      case 'BACKEND_GENERATION':
        // 后端生成中不允许返回
        console.warn('[V2.0] 后端生成中，无法返回');
        break;
      default:
        // IDLE状态无需返回
        break;
    }
  };

  /**
   * V2.0 确认意图识别结果 - 进入下一步
   *
   * 根据意图类型决定下一步：
   * - CLONE_EXISTING_WEBSITE/HYBRID → 先加载匹配模板，然后进入模板选择
   * - DESIGN_FROM_SCRATCH → 跳过模板选择，直接进入风格选择
   */
  const handleConfirmIntent = async () => {
    if (!wizardData.intentResult) {
      console.error('[V2.0] 意图结果为空，无法确认');
      return;
    }

    console.log('[V2.0] 确认意图结果:', wizardData.intentResult.intent);

    // 断开意图分析SSE连接，避免超时错误影响后续步骤
    disconnectAnalysis();

    // 根据意图类型路由到不同步骤
    if (wizardData.intentResult.intent === RequirementIntent.CLONE_EXISTING_WEBSITE ||
      wizardData.intentResult.intent === RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE) {
      // 克隆或混合模式 → 先加载匹配模板，然后进入模板选择
      console.log('[V2.0] 开始加载匹配的行业模板');
      setLoadingTemplates(true);

      try {
        const templates = await getMatchedTemplates(wizardData.intentResult.intent);

        console.log('[V2.0] 模板加载成功:', templates.length, '个');

        // 保存到wizardData
        setWizardData(prev => ({
          ...prev,
          matchedTemplates: templates,
        }));

        // 切换到模板选择步骤
        setWizardStep('TEMPLATE_SELECTION');

        toast({
          title: '模板加载完成',
          description: `为您推荐了 ${templates.length} 个匹配的行业模板`,
        });
      } catch (error) {
        console.error('[V2.0] 模板加载失败:', error);

        toast({
          title: '模板加载失败',
          description: error instanceof Error ? error.message : '请稍后重试',
          variant: 'destructive',
        });

        // 失败时直接跳到风格选择
        setWizardStep('STYLE_SELECTION');
      } finally {
        setLoadingTemplates(false);
      }
    } else {
      // 从零设计 → 跳过模板选择，直接进入风格选择
      setWizardStep('STYLE_SELECTION');
    }
  };

  /**
   * V2.0 修改意图 - 返回编辑模式重新输入
   */
  const handleModifyIntent = () => {
    console.log('[V2.0] 用户选择修改意图');
    setWizardStep('IDLE');
  };

  /**
   * V2.0 关键词修改 - 更新意图结果中的关键词
   */
  const handleKeywordsChange = (newKeywords: string[]) => {
    if (!wizardData.intentResult) return;

    console.log('[V2.0] 用户修改关键词:', newKeywords);

    setWizardData(prev => ({
      ...prev,
      intentResult: prev.intentResult ? {
        ...prev.intentResult,
        extractedKeywords: newKeywords
      } : null
    }));
  };

  /**
   * V2.0 选择模板 - 保存选择并进入风格选择
   */
  const handleSelectTemplate = (template: Template) => {
    console.log('[V2.0] 用户选择模板:', template.name);

    // 保存选中的模板
    setWizardData(prev => ({
      ...prev,
      selectedTemplate: template,
    }));

    // 进入风格选择步骤
    setWizardStep('STYLE_SELECTION');

    toast({
      title: '模板已选择',
      description: `使用"${template.name}"模板进入风格选择`,
    });
  };

  /**
   * V2.0 跳过模板选择 - 直接进入风格选择
   */
  const handleSkipTemplates = () => {
    console.log('[V2.0] 用户跳过模板选择');

    // 清空选中的模板
    setWizardData(prev => ({
      ...prev,
      selectedTemplate: null,
    }));

    // 进入风格选择步骤
    setWizardStep('STYLE_SELECTION');

    toast({
      title: '已跳过模板选择',
      description: '进入风格选择，从零开始设计',
    });
  };

  /**
   * V2.0 选择设计风格 - 保存选择并生成原型预览
   */
  const handleSelectStyle = async (style: DesignStyle) => {
    console.log('[V2.0] 用户选择设计风格:', style);

    const styleInfo = getStyleDisplayInfo(style);

    // 保存选中的风格，清空之前的流式代码和文件
    setWizardData(prev => ({
      ...prev,
      selectedStyle: style,
      sandboxUrl: null,
      sandboxId: null,
      prototypeError: null,
    }));
    setStreamedCode('');
    setPrototypeThinking('');
    setGeneratedFiles([]);
    streamAccumulatorRef.current = '';  // 清空累积器

    // 进入原型预览步骤
    setWizardStep('PROTOTYPE_PREVIEW');

    // 开始生成原型（异步）
    setLoadingPrototype(true);

    /**
     * 从文本内容中解析文件
     * conversation事件包含格式如：<file path="src/index.css">...</file>
     */
    const parseFilesFromText = (text: string): FileNode[] => {
      const files: FileNode[] = [];
      // 匹配 <file path="...">...</file> 格式
      const fileRegex = /<file\s+path="([^"]+)"[^>]*>([\s\S]*?)<\/file>/g;
      let match;

      while ((match = fileRegex.exec(text)) !== null) {
        const [, path, content] = match;
        if (path && content) {
          // 根据文件扩展名判断类型
          const ext = path.split('.').pop()?.toLowerCase() || '';
          let type: FileNode['type'] = 'other';
          if (ext === 'jsx' || ext === 'tsx') type = 'react';
          else if (ext === 'js') type = 'javascript';
          else if (ext === 'ts') type = 'typescript';
          else if (ext === 'css') type = 'css';
          else if (ext === 'json') type = 'json';

          files.push({
            path,
            content: content.trim(),
            type,
            completed: true,
          });
        }
      }
      return files;
    };

    try {
      console.log('[V2.0] 开始生成原型预览');

      // 定义SSE进度回调处理函数
      const handleProgress = (event: SSEProgressEvent) => {
        console.log('[V2.0] 原型生成SSE事件:', event.type, event);

        // 处理stream事件 - 实时显示生成的代码，同时累积到 ref
        if (event.type === 'stream' && event.text) {
          streamAccumulatorRef.current += event.text;

          // 分离思考过程和代码
          const fullText = streamAccumulatorRef.current;
          const thinkingRegex = /<thinking>([\s\S]*?)(?:<\/thinking>|$)/;
          const thinkingMatch = thinkingRegex.exec(fullText);

          if (thinkingMatch) {
            setPrototypeThinking(thinkingMatch[1]);
          }

          // 移除思考过程标签及其内容，只显示代码部分
          const code = fullText.replace(/<thinking>[\s\S]*?(?:<\/thinking>|$)/g, '');
          setStreamedCode(code);
        }

        // 处理conversation事件 - 解析完整的文件内容
        if (event.type === 'conversation' && event.text) {
          console.log('[V2.0] 收到conversation事件，解析文件...');
          const newFiles = parseFilesFromText(event.text);
          if (newFiles.length > 0) {
            console.log('[V2.0] 解析到文件:', newFiles.map(f => f.path));
            setGeneratedFiles(prev => {
              // 合并文件，避免重复
              const existingPaths = new Set(prev.map(f => f.path));
              const uniqueNewFiles = newFiles.filter(f => !existingPaths.has(f.path));
              return [...prev, ...uniqueNewFiles];
            });
          }
        }

        // 处理status事件 - 可以显示状态消息
        if (event.type === 'status' && event.message) {
          console.log('[V2.0] 状态更新:', event.message);
        }

        // 处理sandbox事件 - 立即更新sandboxUrl
        if (event.type === 'sandbox' && event.sandboxUrl) {
          console.log('[V2.0] 沙箱已创建:', event.sandboxUrl);
          setWizardData(prev => ({
            ...prev,
            sandboxUrl: event.sandboxUrl || null,
            sandboxId: event.sandboxId || null,
          }));
          // 沙箱创建后开始生成代码，设置生成中状态
          setIsCodeGenerating(true);
        }
      };

      const prototypeResult = await generatePrototypeStream(
        {
          userRequirement: wizardData.requirement,
          selectedTemplate: wizardData.selectedTemplate
            ? {
              id: wizardData.selectedTemplate.id,
              name: wizardData.selectedTemplate.name,
              referenceUrl: (wizardData.selectedTemplate as { referenceUrl?: string }).referenceUrl,
            }
            : undefined,
          selectedStyle: style,
          intentResult: wizardData.intentResult,
        },
        handleProgress
      );

      if (prototypeResult.success && prototypeResult.sandboxUrl) {
        console.log('[V2.0] 原型生成成功:', prototypeResult.sandboxUrl);

        // 将生成的代码写入沙箱，确保预览展示最新代码
        const rawAiResponse = streamAccumulatorRef.current;
        const sanitizedAiResponse = rawAiResponse.replace(/<thinking>[\s\S]*?(?:<\/thinking>|$)/g, '');

        if (prototypeResult.sandboxId && sanitizedAiResponse.includes('<file')) {
          try {
            console.log('[V2.0] 开始将生成代码应用到Sandbox:', prototypeResult.sandboxId);
            const applyResult = await applyAiCodeToSandbox(prototypeResult.sandboxId, sanitizedAiResponse);
            console.log('[V2.0] Sandbox写入完成:', applyResult.filesWritten, '个文件');

            // 重启 Vite 确保依赖/热更新生效
            try {
              await restartVite(prototypeResult.sandboxId);
              console.log('[V2.0] Vite开发服务器已重启');
            } catch (restartError) {
              console.warn('[V2.0] Vite重启失败，可能需要手动刷新预览', restartError);
            }
          } catch (applyError) {
            console.error('[V2.0] 应用代码到Sandbox失败:', applyError);
            toast({
              title: '预览未能自动更新',
              description: applyError instanceof Error ? applyError.message : '应用代码失败',
              variant: 'destructive',
            });
          }
        }

        // 代码生成完成（包含写入沙箱）
        setIsCodeGenerating(false);

        // V2.0新增：调用 createAppSpec API 创建 AppSpec 实体
        let createdAppSpecId: string | null = null;
        try {
          const apiBaseUrl = getApiBaseUrl();
          const token = getToken();
          const createAppSpecResponse = await fetch(`${apiBaseUrl}/v1/prototype/create-app-spec`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              ...(token && { 'Authorization': `Bearer ${token}` }),
            },
            body: JSON.stringify({
              userRequirement: wizardData.requirement,
              sandboxId: prototypeResult.sandboxId || null,
              sandboxUrl: prototypeResult.sandboxUrl,
              designStyle: style,
              intentType: wizardData.intentResult?.intent || 'DESIGN_FROM_SCRATCH',
            }),
          });

          if (createAppSpecResponse.ok) {
            const result = await createAppSpecResponse.json();
            createdAppSpecId = result.data?.appSpecId || null;
            console.log('[V2.0] AppSpec创建成功:', createdAppSpecId);
          } else {
            console.warn('[V2.0] AppSpec创建失败，但不影响原型预览');
          }
        } catch (error) {
          console.warn('[V2.0] AppSpec创建异常:', error);
        }

        // 保存沙箱URL和appSpecId到wizardData
        setWizardData(prev => ({
          ...prev,
          sandboxUrl: prototypeResult.sandboxUrl,
          sandboxId: prototypeResult.sandboxId || prev.sandboxId,
          appSpecId: createdAppSpecId,
          prototypeError: null,
        }));

        toast({
          title: '原型生成成功',
          description: `使用"${styleInfo.displayName}"风格的原型已准备好预览`,
        });

        prototypeResult.warnings?.forEach((warning) => {
          toast({
            title: '提示',
            description: warning,
          });
        });
      } else {
        console.error('[V2.0] 原型生成失败:', prototypeResult.error);

        // 代码生成完成（失败）
        setIsCodeGenerating(false);
        setWizardData(prev => ({
          ...prev,
          sandboxUrl: null,
          sandboxId: null,
          prototypeError: prototypeResult.error || '原型生成失败，请检查依赖服务',
        }));

        toast({
          title: '原型生成失败',
          description: prototypeResult.error || '请稍后重试',
          variant: 'destructive',
        });
      }
    } catch (error) {
      console.error('[V2.0] 原型生成异常:', error);

      // 代码生成完成（异常）
      setIsCodeGenerating(false);
      toast({
        title: '原型生成失败',
        description: error instanceof Error ? error.message : '未知错误',
        variant: 'destructive',
      });

      setWizardData(prev => ({
        ...prev,
        sandboxUrl: null,
        sandboxId: null,
        prototypeError: error instanceof Error ? error.message : '原型生成失败，请检查依赖服务',
      }));
    } finally {
      // 方案A：从累积的 stream 内容解析文件（如果 conversation 事件未成功解析）
      const accumulated = streamAccumulatorRef.current;
      if (accumulated) {
        console.log('[V2.0] 流结束，累积内容长度:', accumulated.length);

        // 如果文件列表为空，尝试从累积内容解析
        setGeneratedFiles(prevFiles => {
          if (prevFiles.length === 0) {
            const parsed = parseFilesFromText(accumulated);
            if (parsed.length > 0) {
              console.log('[V2.0] 从finally解析到文件:', parsed.map(f => f.path));
              return parsed;
            }
          }
          return prevFiles;
        });
      }

      setLoadingPrototype(false);
    }
  };

  /**
   * V2.0 确认原型设计 - 进入后端代码生成阶段
   */
  const handleConfirmDesign = async () => {
    console.log('[V2.0] 用户确认原型设计，进入后端代码生成');

    // 检查 appSpecId
    if (!wizardData.appSpecId) {
      toast({
        title: '错误',
        description: '缺少 AppSpec ID，无法生成代码',
        variant: 'destructive',
      });
      return;
    }

    // 切换到后端生成步骤
    setWizardStep('BACKEND_GENERATION');

    try {
      const apiBaseUrl = getApiBaseUrl();
      const token = getToken();
      const url = `${apiBaseUrl}/v2/plan-routing/${wizardData.appSpecId}/execute-code-generation`;

      console.log('[V2.0] 调用后端代码生成 API:', url);

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token && { 'Authorization': `Bearer ${token}` }),
        },
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: '代码生成失败' }));
        throw new Error(errorData.message || `HTTP ${response.status}`);
      }

      const raw = await response.json();
      const normalized = normalizeApiResponse<unknown>(raw);
      const hasSuccessField = typeof (raw as { success?: unknown }).success === 'boolean';
      const hasCodeField = (raw as { code?: unknown }).code !== undefined;

      if ((hasSuccessField || hasCodeField) && !normalized.success) {
        throw new Error(
          normalized.message ||
          normalized.error ||
          '代码生成失败'
        );
      }

      console.log('[V2.0] 代码生成结果:', raw);

      toast({
        title: '代码生成成功',
        description: '全栈代码已生成，可以下载查看',
      });

      // TODO: 显示下载链接或跳转到结果页面
      // 可以从 result.data 中获取下载 URL

    } catch (error) {
      console.error('[V2.0] 代码生成失败:', error);

      toast({
        title: '代码生成失败',
        description: error instanceof Error ? error.message : '未知错误',
        variant: 'destructive',
      });

      // 返回到原型预览页面
      setWizardStep('PROTOTYPE_PREVIEW');
    }
  };

  /**
   * V2.0 返回风格选择 - 从原型预览返回重新选择风格
   */
  const handleBackToStyleSelection = () => {
    console.log('[V2.0] 用户选择返回风格选择');

    // 清空沙箱URL
    setWizardData(prev => ({
      ...prev,
      sandboxUrl: null,
      sandboxId: null,
      prototypeError: null,
      selectedStyle: null,
    }));

    // 返回风格选择步骤
    setWizardStep('STYLE_SELECTION');
  };

  /**
   * V2.0 重新生成原型 - 使用当前选中的风格重试
   */
  const handleRetryGeneration = async () => {
    if (!wizardData.selectedStyle) {
      console.error('[V2.0] 无法重试：未选择风格');
      return;
    }

    console.log('[V2.0] 重新生成原型:', wizardData.selectedStyle);

    // 清空错误状态、流式代码和文件
    setWizardData(prev => ({
      ...prev,
      prototypeError: null,
      sandboxUrl: null,
      sandboxId: null,
    }));
    setStreamedCode('');
    setPrototypeThinking('');
    setGeneratedFiles([]);
    streamAccumulatorRef.current = '';  // 清空累积器

    // 开始生成原型（复用 handleSelectStyle 的逻辑）
    setLoadingPrototype(true);

    /**
     * 从文本内容中解析文件
     */
    const parseFilesFromText = (text: string): FileNode[] => {
      const files: FileNode[] = [];
      const fileRegex = /<file\s+path="([^"]+)"[^>]*>([\s\S]*?)<\/file>/g;
      let match;

      while ((match = fileRegex.exec(text)) !== null) {
        const [, path, content] = match;
        if (path && content) {
          const ext = path.split('.').pop()?.toLowerCase() || '';
          let type: FileNode['type'] = 'other';
          if (ext === 'jsx' || ext === 'tsx') type = 'react';
          else if (ext === 'js') type = 'javascript';
          else if (ext === 'ts') type = 'typescript';
          else if (ext === 'css') type = 'css';
          else if (ext === 'json') type = 'json';

          files.push({
            path,
            content: content.trim(),
            type,
            completed: true,
          });
        }
      }
      return files;
    };

    try {
      // 定义SSE进度回调处理函数
      const handleProgress = (event: SSEProgressEvent) => {
        console.log('[V2.0] 重试SSE事件:', event.type);

        if (event.type === 'stream' && event.text) {
          streamAccumulatorRef.current += event.text;

          // 分离思考过程和代码
          const fullText = streamAccumulatorRef.current;
          const thinkingRegex = /<thinking>([\s\S]*?)(?:<\/thinking>|$)/;
          const thinkingMatch = thinkingRegex.exec(fullText);

          if (thinkingMatch) {
            setPrototypeThinking(thinkingMatch[1]);
          }

          // 移除思考过程标签及其内容，只显示代码部分
          const code = fullText.replace(/<thinking>[\s\S]*?(?:<\/thinking>|$)/g, '');
          setStreamedCode(code);
        }

        // 处理conversation事件 - 解析完整的文件内容
        if (event.type === 'conversation' && event.text) {
          const newFiles = parseFilesFromText(event.text);
          if (newFiles.length > 0) {
            console.log('[V2.0] 重试解析到文件:', newFiles.map(f => f.path));
            setGeneratedFiles(prev => {
              const existingPaths = new Set(prev.map(f => f.path));
              const uniqueNewFiles = newFiles.filter(f => !existingPaths.has(f.path));
              return [...prev, ...uniqueNewFiles];
            });
          }
        }

        if (event.type === 'sandbox' && event.sandboxUrl) {
          setWizardData(prev => ({
            ...prev,
            sandboxUrl: event.sandboxUrl || null,
            sandboxId: event.sandboxId || null,
          }));
          // 沙箱创建后开始生成代码，设置生成中状态
          setIsCodeGenerating(true);
        }
      };

      const prototypeResult = await generatePrototypeStream(
        {
          userRequirement: wizardData.requirement,
          selectedTemplate: wizardData.selectedTemplate
            ? {
              id: wizardData.selectedTemplate.id,
              name: wizardData.selectedTemplate.name,
              referenceUrl: (wizardData.selectedTemplate as { referenceUrl?: string }).referenceUrl,
            }
            : undefined,
          selectedStyle: wizardData.selectedStyle,
          intentResult: wizardData.intentResult,
        },
        handleProgress
      );

      if (prototypeResult.success && prototypeResult.sandboxUrl) {
        console.log('[V2.0] 重试成功，原型生成:', prototypeResult.sandboxUrl);

        // 将生成的代码写入沙箱，确保预览展示最新代码
        const rawAiResponse = streamAccumulatorRef.current;
        const sanitizedAiResponse = rawAiResponse.replace(/<thinking>[\s\S]*?(?:<\/thinking>|$)/g, '');

        if (prototypeResult.sandboxId && sanitizedAiResponse.includes('<file')) {
          try {
            console.log('[V2.0] 开始将生成代码应用到Sandbox:', prototypeResult.sandboxId);
            const applyResult = await applyAiCodeToSandbox(prototypeResult.sandboxId, sanitizedAiResponse);
            console.log('[V2.0] Sandbox写入完成:', applyResult.filesWritten, '个文件');

            try {
              await restartVite(prototypeResult.sandboxId);
              console.log('[V2.0] Vite开发服务器已重启');
            } catch (restartError) {
              console.warn('[V2.0] Vite重启失败，可能需要手动刷新预览', restartError);
            }
          } catch (applyError) {
            console.error('[V2.0] 应用代码到Sandbox失败:', applyError);
            toast({
              title: '预览未能自动更新',
              description: applyError instanceof Error ? applyError.message : '应用代码失败',
              variant: 'destructive',
            });
          }
        }

        // 代码生成完成（包含写入沙箱）
        setIsCodeGenerating(false);

        setWizardData(prev => ({
          ...prev,
          sandboxUrl: prototypeResult.sandboxUrl,
          sandboxId: prototypeResult.sandboxId || prev.sandboxId,
          prototypeError: null,
        }));

        toast({
          title: '原型生成成功',
          description: '重新生成完成，请预览确认',
        });

        prototypeResult.warnings?.forEach((warning) => {
          toast({
            title: '提示',
            description: warning,
          });
        });
      } else {
        console.error('[V2.0] 重试失败:', prototypeResult.error);

        // 代码生成完成（失败）
        setIsCodeGenerating(false);

        setWizardData(prev => ({
          ...prev,
          sandboxUrl: null,
          sandboxId: null,
          prototypeError: prototypeResult.error || '原型生成失败，请检查依赖服务',
        }));

        toast({
          title: '原型生成仍然失败',
          description: prototypeResult.error || '请检查 OpenLovable 服务状态',
          variant: 'destructive',
        });
      }
    } catch (error) {
      console.error('[V2.0] 重试异常:', error);

      // 代码生成完成（异常）
      setIsCodeGenerating(false);

      toast({
        title: '原型生成失败',
        description: error instanceof Error ? error.message : '未知错误',
        variant: 'destructive',
      });

      setWizardData(prev => ({
        ...prev,
        sandboxUrl: null,
        sandboxId: null,
        prototypeError: error instanceof Error ? error.message : '原型生成失败',
      }));
    } finally {
      // 方案A：从累积的 stream 内容解析文件
      const accumulated = streamAccumulatorRef.current;
      if (accumulated) {
        console.log('[V2.0] 重试流结束，累积内容长度:', accumulated.length);
        setGeneratedFiles(prevFiles => {
          if (prevFiles.length === 0) {
            const parsed = parseFilesFromText(accumulated);
            if (parsed.length > 0) {
              console.log('[V2.0] 重试finally解析到文件:', parsed.map(f => f.path));
              return parsed;
            }
          }
          return prevFiles;
        });
      }
      setLoadingPrototype(false);
    }
  };

  /**
   * V2.0 刷新预览
   * 重启Vite服务器并刷新iframe
   *
   * 说明：
   * - 为了修复旧沙箱中可能遗漏的 React Hook 导入等问题，
   *   刷新时会优先把当前缓存的代码重新 apply 一次到沙箱。
   */
  const handleRefresh = async () => {
    // 优先使用已保存的 sandboxId；兼容旧数据时从 URL 解析
    let sandboxId = wizardData.sandboxId || '';

    if (!sandboxId && wizardData.sandboxUrl) {
      try {
        const hostname = new URL(wizardData.sandboxUrl).hostname;
        const match = hostname.match(/^\d+-(.+?)\./);
        if (match) {
          sandboxId = match[1];
        }
      } catch (e) {
        console.error('[V2.0] 解析sandboxId失败', e);
      }
    }

    if (!sandboxId) {
      toast({
        title: '刷新失败',
        description: '无法获取沙箱ID',
        variant: 'destructive',
      });
      return;
    }

    // 从已解析文件重建 AI 输出（<file> 格式）
    const buildAiResponseFromFiles = (files: FileNode[]): string => {
      if (!files || files.length === 0) return '';
      return files
        .map(file => `<file path="${file.path}">\n${file.content}\n</file>`)
        .join('\n\n');
    };

    try {
      // Step 1: 刷新前先尝试重新 apply 一次代码（触发后端自动修复）
      const rawAiResponse = streamAccumulatorRef.current || '';
      let sanitizedAiResponse = rawAiResponse.replace(/<thinking>[\s\S]*?(?:<\/thinking>|$)/g, '');

      if (!sanitizedAiResponse.includes('<file') && generatedFiles.length > 0) {
        sanitizedAiResponse = buildAiResponseFromFiles(generatedFiles);
      }

      if (sanitizedAiResponse.includes('<file')) {
        try {
          console.log('[V2.0] 刷新前重新应用代码到Sandbox:', sandboxId);
          const applyResult = await applyAiCodeToSandbox(sandboxId, sanitizedAiResponse);
          console.log('[V2.0] 代码已重新写入Sandbox:', applyResult.filesWritten, '个文件');
        } catch (applyError) {
          console.warn('[V2.0] 刷新前 apply 失败，将继续重启Vite:', applyError);
          toast({
            title: '自动修复失败',
            description: applyError instanceof Error ? applyError.message : '应用代码失败',
            variant: 'destructive',
          });
        }
      } else {
        console.warn('[V2.0] 刷新前无可用代码缓存，跳过 apply');
      }

      // Step 2: 重启 Vite
      console.log('[V2.0] 刷新预览，重启Vite:', sandboxId);
      await restartVite(sandboxId);
      toast({
        title: '预览已刷新',
        description: '代码已重新应用并重启开发服务器，预览窗口即将刷新',
      });
      // 注意：PrototypePreviewPanel 会在 handleRefresh 返回后自动刷新 iframe src
    } catch (error) {
      console.error('[V2.0] 刷新失败:', error);
      toast({
        title: '刷新失败',
        description: '重启开发服务器失败，请稍后重试',
        variant: 'destructive',
      });
    }
  };



  /**
   * V2.0 返回上一步
   * 
   * 根据当前步骤返回到正确的上一步
   */

  return (
    <section className="relative flex min-h-[90dvh] w-full flex-col items-center justify-center overflow-hidden py-16">
      {/* 背景装饰 - 柔和的光斑 */}
      <div className="absolute inset-0 -z-10 overflow-hidden pointer-events-none">
        {/* 主光斑 - 蓝紫色 */}
        <div className="absolute top-[-10%] left-[-10%] h-[600px] w-[600px] rounded-full bg-blue-400/20 blur-[120px] animate-pulse" />
        {/* 副光斑 - 粉橙色 */}
        <div className="absolute bottom-[-10%] right-[-10%] h-[600px] w-[600px] rounded-full bg-purple-400/20 blur-[120px] animate-pulse" style={{ animationDelay: '2s' }} />
      </div>

      <div className="container flex flex-col items-center justify-center relative z-10">
        {/* V2.0 向导模式：根据wizardStep渲染不同内容 */}
        {wizardStep !== 'IDLE' ? (
          <div className="w-full max-w-7xl mx-auto">
            {/* 返回按钮（后端生成中不显示） */}
            {wizardStep !== 'BACKEND_GENERATION' && (
              <Button
                variant="ghost"
                size="sm"
                onClick={handleBack}
                className="mb-4 rounded-full"
              >
                <ArrowLeft className="mr-2 h-4 w-4" />
                返回上一步
              </Button>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
              {/* 左侧：需求展示（所有步骤都显示） */}
              <div className="space-y-6">
                {/* 1. 需求卡片 */}
                <div className="p-6 rounded-[2rem] border bg-card/50 backdrop-blur-xl shadow-sm">
                  <h2 className="text-xl font-bold mb-4 flex items-center gap-2 text-foreground">
                    <span className="p-2 bg-primary/10 rounded-xl text-primary">
                      <Sparkles className="w-5 h-5" />
                    </span>
                    你的需求
                  </h2>
                  <p className="text-sm leading-relaxed text-muted-foreground whitespace-pre-wrap font-medium">
                    {wizardData.requirement}
                  </p>
                </div>

                {/* 2. 应用类型卡片 (如果已选) */}
                {wizardData.selectedComplexityHint && (
                  (() => {
                    const category = COMPLEXITY_CATEGORIES.find(c => c.id === wizardData.selectedComplexityHint);
                    if (!category) return null;
                    const Icon = category.icon;
                    return (
                      <div className="p-4 rounded-[1.5rem] border bg-card/50 backdrop-blur-xl shadow-sm animate-in fade-in slide-in-from-bottom-4 duration-500">
                        <h2 className="text-lg font-bold mb-3 flex items-center gap-2 text-foreground">
                          <span className={`p-1.5 rounded-lg text-white bg-gradient-to-br ${category.color}`}>
                            <Icon className="w-4 h-4" />
                          </span>
                          应用类型
                        </h2>
                        <div className="space-y-2">
                          <div className="flex items-center justify-between p-2 rounded-lg bg-background/40">
                            <span className="text-xs text-muted-foreground font-medium">类型</span>
                            <span className="text-sm font-bold text-foreground">{category.title}</span>
                          </div>
                          <div className="flex items-center justify-between p-2 rounded-lg bg-background/40">
                            <span className="text-xs text-muted-foreground font-medium">技术栈</span>
                            <Badge variant="secondary" className="text-xs font-semibold h-5 px-2">{category.techStack}</Badge>
                          </div>
                        </div>
                      </div>
                    );
                  })()
                )}

                {/* 3. AI 执行链路日志 */}
                <div className="p-6 rounded-[2rem] border bg-card/50 backdrop-blur-xl shadow-sm">
                  <h2 className="text-xl font-bold mb-6 flex items-center gap-2 text-foreground">
                    <span className="p-2 bg-purple-500/10 rounded-xl text-purple-500">
                      <Cpu className="w-5 h-5" />
                    </span>
                    AI 执行链路
                  </h2>

                  <div className="flex flex-col">
                    {/* Step 1: 意图理解 */}
                    <TimelineStep
                      status={wizardData.intentResult ? 'completed' : 'active'}
                      title="意图理解 & NLU分析"
                      description={
                        wizardData.intentResult
                          ? `识别为: ${wizardData.intentResult.intent} (置信度 ${(wizardData.intentResult.confidence * 100).toFixed(0)}%)`
                          : "正在深度解析自然语言需求..."
                      }
                      details={["需求语义解析", "功能意图识别", "复杂与风险评估"]}
                    />

                    {/* Step 2: 技术架构 */}
                    <TimelineStep
                      status={
                        (wizardStep !== 'ANALYZING' && wizardStep !== 'INTENT_RESULT') ? 'completed' :
                          (wizardStep === 'INTENT_RESULT' ? 'active' : 'pending')
                      }
                      title="技术选型 & 架构设计"
                      description={
                        wizardData.selectedComplexityHint || wizardData.selectedTechStackHint
                          ? `架构方案: ${wizardData.selectedTechStackHint || 'React + Spring Boot'}`
                          : "智能匹配最优技术栈..."
                      }
                      details={[
                        "技术架构选型",
                        {
                          label: "实体关系建模",
                          content: <ERDiagramPreview complexityHint={wizardData.selectedComplexityHint} />
                        }
                      ]}
                    />

                    {/* Step 3: UI/UX 设计 */}
                    <TimelineStep
                      status={
                        wizardData.selectedStyle ? 'completed' :
                          (wizardStep === 'TEMPLATE_SELECTION' || wizardStep === 'STYLE_SELECTION' ? 'active' : 'pending')
                      }
                      title="UI/UX 设计系统"
                      description={
                        wizardData.selectedStyle
                          ? `设计风格: ${wizardData.selectedStyle}`
                          : "生成自适应视觉规范与组件库..."
                      }
                      details={["设计规范生成", "组件库匹配"]}
                    />

                    {/* Step 4: 全栈生成 */}
                    <TimelineStep
                      status={
                        wizardStep === 'BACKEND_GENERATION' ? 'active' :
                          (wizardStep === 'PROTOTYPE_PREVIEW' ? 'pending' : 'pending')
                      }
                      title="全栈代码生成"
                      description="React前端 + SpringBoot后端 + DB设计"
                      details={["前端代码生成", "后端服务构建", "数据库迁移"]}
                      isLast={true}
                    />
                  </div>
                </div>
              </div>

              {/* 右侧：根据wizardStep渲染不同内容 */}
              <div className="relative">
                <div className="sticky top-8">
                  <div className="p-6 rounded-[2rem] border bg-card/80 backdrop-blur-xl shadow-xl">
                    {/* ANALYZING步骤：显示分析进度 */}
                    {wizardStep === 'ANALYZING' && (
                      <div className="space-y-4">
                        <AnalysisProgressPanel
                          messages={state.messages}
                          isConnected={state.isConnected}
                          isCompleted={state.isCompleted}
                          error={state.error}
                        />

                        {thinkingContent && (
                          <div className="rounded-xl border bg-muted/50 p-4 font-mono text-xs text-muted-foreground animate-in fade-in slide-in-from-top-2">
                            <div className="flex items-center gap-2 mb-2 text-primary">
                              <Sparkles className="w-3 h-3" />
                              <span className="font-semibold">AI 深度思考中...</span>
                            </div>
                            <div className="whitespace-pre-wrap leading-relaxed">
                              {thinkingContent}
                              <span className="inline-block w-1.5 h-3 bg-primary/50 ml-1 animate-pulse" />
                            </div>
                          </div>
                        )}
                      </div>
                    )}

                    {/* INTENT_RESULT步骤：显示意图识别结果 */}
                    {wizardStep === 'INTENT_RESULT' && wizardData.intentResult && (
                      <IntentResultPanel
                        result={wizardData.intentResult}
                        onConfirm={handleConfirmIntent}
                        onModify={handleModifyIntent}
                        onKeywordsChange={handleKeywordsChange}
                        loading={false}
                      />
                    )}

                    {/* TEMPLATE_SELECTION步骤：显示模板选择 */}
                    {wizardStep === 'TEMPLATE_SELECTION' && (
                      <TemplateSelectionPanel
                        templates={wizardData.matchedTemplates}
                        onSelectTemplate={handleSelectTemplate}
                        onSkip={handleSkipTemplates}
                        loading={loadingTemplates}
                      />
                    )}

                    {/* STYLE_SELECTION步骤：显示7种风格选择 */}
                    {wizardStep === 'STYLE_SELECTION' && (
                      <StyleSelectionPanel
                        onSelectStyle={handleSelectStyle}
                        loading={false}
                        selectedStyle={wizardData.selectedStyle}
                      />
                    )}

                    {/* PROTOTYPE_PREVIEW步骤：显示原型预览 */}
                    {wizardStep === 'PROTOTYPE_PREVIEW' && (
                      <PrototypePreviewPanel
                        sandboxUrl={wizardData.sandboxUrl}
                        errorMessage={wizardData.prototypeError}
                        onConfirm={handleConfirmDesign}
                        onBack={handleBackToStyleSelection}
                        onRetry={handleRetryGeneration}
                        selectedTemplate={wizardData.selectedTemplate}
                        selectedStyle={wizardData.selectedStyle}
                        loading={loadingPrototype}
                        isGenerating={isCodeGenerating}
                        files={generatedFiles}
                        streamedCode={streamedCode}
                        thinking={prototypeThinking}
                        onRefresh={handleRefresh}
                        elapsedTime={elapsedTime}
                      />
                    )}

                    {wizardStep === 'BACKEND_GENERATION' && (
                      <div className="flex flex-col items-center justify-center py-16 px-8">
                        <Loader2 className="h-16 w-16 text-blue-600 dark:text-blue-400 animate-spin mb-6" />
                        <h3 className="text-2xl font-semibold text-gray-900 dark:text-gray-100 mb-3">
                          后端代码生成中
                        </h3>
                        <p className="text-sm text-gray-600 dark:text-gray-400 text-center max-w-md mb-6">
                          正在生成完整的全栈代码（前端 + 后端 + 数据库），预计需要 3-5 分钟...
                        </p>
                        {/* 进度动画 */}
                        <div className="flex items-center gap-2">
                          <div className="h-2 w-2 rounded-full bg-blue-600 dark:bg-blue-400 animate-pulse"></div>
                          <div className="h-2 w-2 rounded-full bg-purple-600 dark:bg-purple-400 animate-pulse delay-75"></div>
                          <div className="h-2 w-2 rounded-full bg-green-600 dark:bg-green-400 animate-pulse delay-150"></div>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>
        ) : (
          <>
            {/* 编辑模式：Apple Design 风格内容 */}
            {/* 主标题 - 强调核心价值主张 */}
            <div className="mb-12 text-center animate-in fade-in slide-in-from-bottom-4 duration-1000 relative z-10">
              <h1 className="text-5xl font-bold leading-tight tracking-tighter md:text-7xl lg:text-8xl mb-6">
                <span className="block text-foreground">你的创意</span>
                <span className="block bg-gradient-to-r from-blue-600 via-indigo-500 to-purple-600 bg-clip-text text-transparent pb-2">
                  由 AI 实现
                </span>
              </h1>

              {/* 副标题 - 强调教育场景 */}
              <p className="max-w-2xl mx-auto text-lg md:text-xl text-muted-foreground leading-relaxed mb-8 font-medium">
                为校园而生，让每个想法都长成应用。<br className="hidden md:block" />
                与数十万位创新者一起，用一句话自动生成小程序、APP、H5网页应用。
              </p>

              {/* 核心价值主张标签 */}
              <div className="flex flex-wrap items-center justify-center gap-3 text-sm mb-10">
                <span className="inline-flex items-center gap-1.5 rounded-full bg-blue-50/50 dark:bg-blue-900/20 px-4 py-1.5 text-blue-700 dark:text-blue-300 font-medium border border-blue-100 dark:border-blue-800/30 backdrop-blur-sm">
                  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                  一句话生成
                </span>
                <span className="inline-flex items-center gap-1.5 rounded-full bg-purple-50/50 dark:bg-purple-900/20 px-4 py-1.5 text-purple-700 dark:text-purple-300 font-medium border border-purple-100 dark:border-purple-800/30 backdrop-blur-sm">
                  <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
                  </svg>
                  全栈代码
                </span>
              </div>
            </div>

            {/* V2.0 Spotlight风格输入框容器 */}
            <div className="mb-20 w-full max-w-3xl animate-in fade-in slide-in-from-bottom-8 duration-1000 relative z-20" style={{ animationDelay: '200ms' }}>
              <div className="group relative">
                {/* 输入框外发光效果 */}
                <div className="absolute -inset-1 bg-gradient-to-r from-blue-600/20 via-purple-600/20 to-pink-600/20 rounded-[24px] blur-xl opacity-0 transition-opacity duration-500 group-hover:opacity-100" />

                <div className="relative bg-background/80 backdrop-blur-xl border border-input/50 rounded-[20px] shadow-2xl ring-1 ring-black/5 dark:ring-white/10 flex flex-col transition-all duration-300 group-hover:shadow-primary/5 group-hover:border-primary/20">

                  {/* 输入区域内容 - 根据模式动态切换 */}
                  <div className="relative min-h-[120px]">
                    {(inputMode === 'TEXT' || inputMode === 'LINK') && (
                      <Textarea
                        placeholder={inputMode === 'TEXT'
                          ? "在这里输入你想做什么小程序/APP/H5网页..."
                          : "请输入参考网站链接 (如: https://example.com)..."}
                        value={wizardData.requirement}
                        onChange={(e) => {
                          const newVal = e.target.value;
                          setWizardData(prev => ({ ...prev, requirement: newVal }));
                          onRequirementChange?.(newVal);
                        }}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" && !e.shiftKey) {
                            e.preventDefault();
                            handleSubmitRequirement();
                          }
                        }}
                        className="min-h-[120px] w-full resize-none border-0 bg-transparent text-xl leading-relaxed px-6 py-5 focus-visible:ring-0 placeholder:text-muted-foreground/40 font-medium"
                        style={{ height: 'auto' }}
                      />
                    )}

                    {inputMode === 'IMAGE' && (
                      <div
                        className="flex flex-col items-center justify-center h-[120px] border-2 border-dashed border-border/50 m-4 rounded-xl bg-muted/20 cursor-pointer hover:bg-muted/40 transition-colors"
                        onClick={() => toast({ title: "功能开发中", description: "图片上传分析功能将在下一版本上线" })}
                      >
                        <ImageIcon className="w-8 h-8 text-muted-foreground/50 mb-2" />
                        <p className="text-sm text-muted-foreground">点击上传或拖拽设计图/截图到这里</p>
                      </div>
                    )}

                    {inputMode === 'AUDIO' && (
                      <div
                        className="flex flex-col items-center justify-center h-[120px] m-4 cursor-pointer group/mic"
                        onClick={() => toast({ title: "功能开发中", description: "语音输入功能将在下一版本上线" })}
                      >
                        <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center group-hover/mic:bg-primary/20 transition-colors mb-2">
                          <Mic className="w-6 h-6 text-primary" />
                        </div>
                        <p className="text-sm text-muted-foreground">点击开始录音</p>
                      </div>
                    )}

                    {inputMode === 'DOC' && (
                      <div
                        className="flex flex-col items-center justify-center h-[120px] border-2 border-dashed border-border/50 m-4 rounded-xl bg-muted/20 cursor-pointer hover:bg-muted/40 transition-colors"
                        onClick={() => toast({ title: "功能开发中", description: "文档解析功能将在下一版本上线" })}
                      >
                        <FileText className="w-8 h-8 text-muted-foreground/50 mb-2" />
                        <p className="text-sm text-muted-foreground">点击上传 PRD/需求文档 (PDF, Word, MD)</p>
                      </div>
                    )}
                  </div>

                  {/* 底部工具栏 */}
                  <div className="flex items-center justify-between px-4 pb-3 pt-2">
                    {/* 左侧：模式切换 */}
                    <div className="flex items-center gap-1">
                      {INPUT_MODES.map((m) => (
                        <button
                          key={m.mode}
                          onClick={() => setInputMode(m.mode)}
                          className={`flex items-center justify-center p-2 rounded-lg transition-all duration-200 group/btn ${inputMode === m.mode
                            ? "bg-primary/10 text-primary"
                            : "text-muted-foreground hover:bg-muted/50 hover:text-foreground"
                            }`}
                          title={m.label}
                        >
                          {m.icon}
                          <span className="sr-only">{m.label}</span>
                        </button>
                      ))}
                    </div>

                    {/* 右侧：字符计数与提交按钮 */}
                    <div className="flex items-center gap-4">
                      <div className="text-xs font-medium text-muted-foreground/60 hidden sm:block">
                        {inputMode === 'TEXT' ? `${wizardData.requirement.length}/500` : ''}
                      </div>

                      {/* V2.0 提交按钮 - 整合在输入框内 */}
                      <Button
                        onClick={handleSubmitRequirement}
                        disabled={!['TEXT', 'LINK'].includes(inputMode) && !wizardData.requirement.trim()}
                        className="rounded-xl h-9 px-6 font-medium bg-foreground text-background hover:bg-foreground/90 transition-all shadow-sm disabled:opacity-30"
                      >
                        <Sparkles className="mr-2 h-4 w-4" />
                        生成
                      </Button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Quick Starters Capsules */}
            <div className="mb-20 w-full max-w-3xl animate-in fade-in slide-in-from-bottom-8 duration-1000 relative z-20 flex flex-wrap justify-center gap-3" style={{ animationDelay: '300ms' }}>
              {[
                { label: "🛒 智能二手交易", prompt: "I want to build a second-hand trading platform for students with image recognition." },
                { label: "📄 论文润色助手", prompt: "I want to build a document analysis tool for thesis refinement using LLM." },
                { label: "🏃 运动打卡社群", prompt: "I want to build a social fitness app with daily check-ins and leaderboards." },
                { label: "🥘 校园外卖点餐", prompt: "I want to build a food delivery mini-app for campus cafeteria." }
              ].map((starter, idx) => (
                <button
                  key={idx}
                  onClick={() => {
                    setWizardData(prev => ({ ...prev, requirement: starter.prompt }));
                    onRequirementChange?.(starter.prompt);
                  }}
                  className="px-4 py-2 rounded-full bg-white/50 dark:bg-black/20 border border-white/20 dark:border-white/10 backdrop-blur-md text-sm font-medium hover:bg-white/80 dark:hover:bg-white/10 transition-colors shadow-sm"
                >
                  {starter.label}
                </button>
              ))}
            </div>

            {/* AI Optimization Indicator */}
            {(externalComplexityHint || externalTechStackHint) && (
              <div className="absolute top-[38%] right-[10%] md:right-[20%] z-30 animate-in fade-in zoom-in duration-500 pointer-events-none">
                <div className="bg-emerald-500/10 backdrop-blur-md border border-emerald-500/20 text-emerald-600 dark:text-emerald-400 px-3 py-1 rounded-full text-xs font-bold flex items-center gap-1.5 shadow-lg">
                  <Sparkles className="w-3 h-3 animate-pulse" />
                  AI Optimization Active
                </div>
              </div>
            )}

          </>
        )}
      </div>

      {/* 登录对话框 - 未登录时弹出 */}
      <LoginDialog
        open={loginDialogOpen}
        onOpenChange={setLoginDialogOpen}
        onSuccess={() => {
          // 登录成功后自动重新触发分析
          console.log('[V2.0] 登录成功，自动重新触发意图分析');
          toast({
            title: '登录成功',
            description: '正在继续分析您的需求...',
          });
          // 延迟一下再触发，确保token已保存
          setTimeout(() => {
            handleSubmitRequirement();
          }, 500);
        }}
        title="登录以继续"
        description="登录后即可使用AI智能分析和代码生成功能"
      />
    </section>
  );
}
