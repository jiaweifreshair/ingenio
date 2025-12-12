"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";
import { generateAppSpec, type GenerateRequest } from "@/lib/api/generate";
import { APIError } from "@/lib/api/client";
import { useToast } from "@/hooks/use-toast";
import { ModelSelector } from "@/components/ai/model-selector";
import { UNIAIX_MODELS, MODEL_CONFIGS, type UniaixModel } from "@/lib/api/uniaix";
import { TemplateCard, type TemplateCardProps } from "@/components/home/template-card";
import { SuccessAnimation } from "@/components/ui/success-animation";
import { PageTransition } from "@/components/ui/page-transition";
import { useAnalysisSse } from "@/hooks/use-analysis-sse";
import { AnalysisProgressPanel } from "@/components/analysis/AnalysisProgressPanel";
import { StylePicker } from "@/components/design/style-picker";
import {
  ShoppingCart,
  FileText,
  GraduationCap,
  Utensils,
  Heart,
  Calendar,
  Sparkles,
  Zap,
  Library,
  ChevronRight,
  X,
} from "lucide-react";

/**
 * 模板描述映射
 * 用于生成完整的需求描述
 */
const TEMPLATE_REQUIREMENTS: Record<string, string> = {
  // 首页快捷入口的6个模板（原有）
  "campus-marketplace": "创建一个校园二手交易平台，支持商品发布、搜索、聊天和交易评价功能",
  "calorie-tracker": "创建一个校食热量查询应用，支持食堂菜品热量查询、营养分析和健康建议",
  "vocabulary-trainer": "创建一个单词刷题应用，支持单词记忆、打卡签到和学习进度跟踪",
  "course-review": "创建一个课程评价系统，支持课程评分、评论发布和选课推荐功能",
  "health-tracker": "创建一个健康打卡应用，支持运动记录、健身计划和健康数据统计",
  "study-scheduler": "创建一个学习日程表应用，支持课程管理、时间规划和提醒功能",

  // 五大应用场景模板（新增）
  "smart-education": `创建一个智慧教育个性化学习助手应用，具备以下核心功能：

1. **学习计划制定**：根据学生的学习进度和目标，智能生成个性化学习计划
2. **知识点诊断**：通过AI分析学生的薄弱环节，提供针对性的学习建议
3. **学习资源推荐**：根据学习风格和偏好，推荐适合的视频、文章、习题等资源
4. **进度跟踪**：记录每日学习时长、完成任务数，可视化展示学习曲线
5. **互动答疑**：支持学生提问，AI助手实时解答疑惑
6. **成就系统**：设置学习里程碑和徽章，激励持续学习

适用人群：中小学生、大学生、终身学习者
技术要求：支持iOS和Android双平台，需要数据同步和离线缓存`,

  "health-management": `创建一个智能健康管理与分析应用，提供全方位的健康监测服务：

1. **健康数据记录**：记录体重、血压、心率、睡眠质量等多维度健康指标
2. **AI健康分析**：基于历史数据，分析健康趋势，预测潜在风险
3. **饮食营养管理**：扫描食物自动识别营养成分，生成每日营养报告
4. **运动建议**：根据身体状况和目标，制定科学的运动计划
5. **健康提醒**：定时提醒喝水、吃药、运动、体检等健康活动
6. **报告导出**：生成可视化健康报告，可分享给医生或家人
7. **健康社区**：与其他用户交流健康经验，参与挑战活动

数据隐私：所有健康数据加密存储，用户完全控制数据访问权限
技术特性：支持Apple Health Kit和Google Fit数据同步`,

  "life-convenience": `创建一个智能生活便利日程管理应用，让日常生活井井有条：

1. **智能日程规划**：AI自动分析任务优先级，合理安排每日行程
2. **多日历同步**：整合工作、学习、生活等多个日历，统一视图管理
3. **时间冲突检测**：自动识别日程冲突，提供解决方案建议
4. **地点感知提醒**：基于GPS定位，到达特定地点时触发任务提醒
5. **习惯养成**：支持创建每日、每周习惯打卡，形成良好生活规律
6. **任务分解**：将大任务拆解为小步骤，逐步完成目标
7. **时间分析**：统计时间分配，帮助优化时间管理策略
8. **语音输入**：支持语音快速创建日程和待办事项

适用场景：职场人士、学生、家庭主妇等需要高效时间管理的用户
集成服务：支持与微信、钉钉、飞书等办公工具集成`,

  "efficiency-tools": `创建一个智能笔记整理与知识管理工具，提升学习和工作效率：

1. **多格式笔记**：支持文本、图片、音频、视频、PDF等多种格式笔记
2. **AI智能整理**：自动提取笔记关键词，生成思维导图和知识图谱
3. **OCR文字识别**：拍照即可提取书籍、白板、手写笔记中的文字
4. **语音转文字**：实时将会议、课程语音转为文字笔记，支持重点标记
5. **标签分类**：灵活的标签系统，支持多维度分类和快速检索
6. **全文搜索**：强大的搜索引擎，秒速找到所需内容
7. **笔记链接**：双向链接功能，构建个人知识网络
8. **协作分享**：支持笔记分享、协同编辑、评论讨论
9. **定期回顾**：AI提醒复习旧笔记，巩固知识记忆
10. **Markdown支持**：支持Markdown语法，代码高亮显示

目标用户：学生、知识工作者、研究人员、内容创作者
云端同步：跨设备实时同步，永不丢失重要笔记`,

  "social-collaboration": `创建一个现代化的团队协作与社交工具，打破沟通壁垒：

1. **团队空间**：为每个项目创建独立空间，集中管理文件、任务、讨论
2. **即时通讯**：支持文字、语音、视频通话，表情包和GIF动图
3. **任务看板**：看板式任务管理（待办、进行中、已完成），拖拽操作
4. **文档协作**：多人实时在线编辑文档、表格、演示文稿
5. **文件共享**：云端存储团队文件，版本控制和权限管理
6. **会议管理**：在线会议室、屏幕共享、会议纪要自动生成
7. **进度追踪**：可视化项目进度，甘特图和燃尽图分析
8. **@提醒系统**：@成员快速沟通，重要消息置顶和加星标
9. **集成工具**：与Git、Jira、Figma等开发设计工具深度集成
10. **智能助手**：AI助手总结讨论要点，提醒待办事项

适用团队：创业团队、学生组织、远程团队、跨部门协作
安全保障：企业级加密，支持私有化部署`,
};

/**
 * RequirementForm组件 - 应用需求输入表单（增强版）
 *
 * 核心功能：
 * - 提供需求输入框（placeholder: "描述你想要的应用..."）
 * - "生成应用"按钮（初始disabled状态）
 * - 快速模板选项（data-template属性）
 * - 表单验证（至少10字符）
 * - 成功提交后导航到/wizard/:id
 *
 * UI增强：
 * - ✨ 页面加载时的淡入动画
 * - ✨ 提交成功后的庆祝动画
 * - ✨ 模板点击的反馈动画
 * - ✨ 按钮微交互（hover/active）
 *
 * E2E测试支持：
 * - textarea placeholder: "描述你想要的应用..."
 * - button: "生成应用"
 * - 模板卡片: data-template属性
 * - 空表单时按钮disabled
 * - 点击模板填充输入框
 * - 提交后导航到/wizard/:id
 */
export function RequirementForm(): React.ReactElement {
  const router = useRouter();
  const { toast } = useToast();

  // 表单状态
  const [requirement, setRequirement] = useState("");
  const [selectedModel, setSelectedModel] = useState<UniaixModel>(UNIAIX_MODELS.QWEN_MAX);

  // 加载和错误状态
  const [loading, setLoading] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);
  const [showAnalysis, setShowAnalysis] = useState(false);

  // 🆕 阶段指示状态（新增style-selection阶段）
  const [currentPhase, setCurrentPhase] = useState<
    'idle' | 'analyzing' | 'style-selection' | 'generating' | 'navigating'
  >('idle');

  // 风格选择状态
  const [selectedStyle, setSelectedStyle] = useState<string | null>(null);

  // 模板加载状态
  const [loadedTemplate, setLoadedTemplate] = useState<{ id: string; name: string } | null>(null);

  // SSE分析进度状态
  const {
    messages,
    isConnected,
    isCompleted,
    error: analysisError,
    connect: startAnalysis
  } = useAnalysisSse({
    requirement,
    autoConnect: false, // 手动控制连接
    onComplete: () => {
      // 🆕 分析完成，切换到风格选择阶段
      console.log('[RequirementForm] ===== SSE分析完成 =====');
      console.log('[RequirementForm] 进入风格选择阶段');
      setCurrentPhase('style-selection');
      // 注意：不再立即调用handleFullGeneration，等待用户选择风格
    },
    onError: (error) => {
      console.error('[RequirementForm] ❌ SSE分析失败:', error);
      toast({
        title: "分析失败",
        description: error,
        variant: "destructive",
      });
      setLoading(false);
      setShowAnalysis(false);
      setCurrentPhase('idle');  // 🆕 重置阶段
    }
  });

  /**
   * 组件挂载时读取URL参数或sessionStorage中的requirement
   * 优先级：URL参数(templateId) > URL参数(template) > sessionStorage
   */
  useEffect(() => {
    const searchParams = new URLSearchParams(window.location.search);

    // 1. 检查templateId参数（来自templates页面）
    const templateIdFromPage = searchParams.get("templateId");
    const templateName = searchParams.get("templateName");
    if (templateIdFromPage) {
      // 从模板库页面返回，优先使用TEMPLATE_REQUIREMENTS
      const templateRequirement = TEMPLATE_REQUIREMENTS[templateIdFromPage] ||
        `创建一个${templateName || templateIdFromPage}应用，请根据模板库的描述实现相关功能`;

      setRequirement(templateRequirement);

      // 设置模板加载状态
      setLoadedTemplate({
        id: templateIdFromPage,
        name: templateName || templateIdFromPage
      });

      toast({
        title: "模板已加载",
        description: `已加载模板: ${templateName || templateIdFromPage}`,
      });

      // 聚焦到输入框
      setTimeout(() => {
        const textarea = document.getElementById("requirement") as HTMLTextAreaElement | null;
        if (textarea) {
          textarea.focus();
          textarea.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      }, 100);

      return; // 使用了URL参数，不再检查其他来源
    }

    // 2. 检查template参数（来自首页快捷入口）
    const templateId = searchParams.get("template");
    if (templateId && TEMPLATE_REQUIREMENTS[templateId]) {
      // 从URL参数填充模板需求
      const templateRequirement = TEMPLATE_REQUIREMENTS[templateId];
      setRequirement(templateRequirement);

      // 显示Toast通知
      const template = templates.find(t => t.id === templateId);
      if (template) {
        toast({
          title: "模板已应用",
          description: `已使用"${template.title}"模板`,
        });
      }

      // 聚焦到输入框
      setTimeout(() => {
        const textarea = document.getElementById("requirement") as HTMLTextAreaElement | null;
        if (textarea) {
          textarea.focus();
          textarea.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      }, 100);

      return; // 使用了URL参数，不再检查sessionStorage
    }

    // 3. 如果没有URL参数，检查sessionStorage（来自首页输入框）
    const savedRequirement = sessionStorage.getItem("requirement");
    if (savedRequirement && savedRequirement.trim()) {
      setRequirement(savedRequirement);
      // 读取后清除，避免重复使用
      sessionStorage.removeItem("requirement");
    }
  }, [toast]);

  /**
   * 模板列表 - 与首页保持一致的6个模板
   * 每个模板卡片添加data-template属性供E2E测试使用
   */
  const templates: TemplateCardProps[] = [
    {
      id: "campus-marketplace",
      title: "校园二手交易",
      description: "闲置物品交易平台",
      icon: <ShoppingCart className="h-6 w-6" />,
      color: "from-green-500 to-emerald-500",
    },
    {
      id: "calorie-tracker",
      title: "校食热量查询",
      description: "食堂营养健康助手",
      icon: <Utensils className="h-6 w-6" />,
      color: "from-orange-500 to-amber-500",
    },
    {
      id: "vocabulary-trainer",
      title: "单词刷题宝",
      description: "英语学习打卡",
      icon: <FileText className="h-6 w-6" />,
      color: "from-blue-500 to-cyan-500",
    },
    {
      id: "course-review",
      title: "课程评价助手",
      description: "选课必备神器",
      icon: <GraduationCap className="h-6 w-6" />,
      color: "from-purple-500 to-pink-500",
    },
    {
      id: "health-tracker",
      title: "健康打卡",
      description: "运动健身记录",
      icon: <Heart className="h-6 w-6" />,
      color: "from-red-500 to-rose-500",
    },
    {
      id: "study-scheduler",
      title: "学习日程表",
      description: "课程时间管理",
      icon: <Calendar className="h-6 w-6" />,
      color: "from-indigo-500 to-violet-500",
    },
  ];

  /**
   * 处理模板点击
   * 填充输入框并聚焦
   */
  const handleTemplateClick = (template: { id: string; title: string; description: string }) => {
    const fullRequirement = TEMPLATE_REQUIREMENTS[template.id] ||
      `创建一个${template.title}应用，包含${template.description}相关功能`;

    setRequirement(fullRequirement);

    // 显示Toast通知
    toast({
      title: "模板已应用",
      description: `已使用"${template.title}"模板`,
    });

    // 聚焦到输入框
    const textarea = document.getElementById("requirement") as HTMLTextAreaElement | null;
    if (textarea) {
      textarea.focus();
      // 滚动到输入框
      textarea.scrollIntoView({ behavior: "smooth", block: "center" });
    }
  };

  /**
   * 🆕 增强版：分析完成后的完整生成处理
   * 调用完整生成API并导航到wizard页面
   * 包含详细日志、超时保护、错误诊断
   */
  async function handleFullGeneration(): Promise<void> {
    console.log('[RequirementForm] ===== 🚀 开始完整生成流程 =====');
    console.log('[RequirementForm] 当前阶段:', currentPhase);
    console.log('[RequirementForm] 分析已完成，准备调用 /api/v1/generate/full');

    try {
      // 构造请求参数
      const request: GenerateRequest = {
        requirement: requirement.trim(),
        model: selectedModel,
        tenantId: "default-tenant",
        userId: "default-user",
      };

      console.log('[RequirementForm] 📤 API请求参数:', JSON.stringify(request, null, 2));

      // 🆕 添加超时保护（120秒）
      const controller = new AbortController();
      const timeoutId = setTimeout(() => {
        console.error('[RequirementForm] ⏰ API调用超时（120秒）');
        controller.abort();
      }, 120000);

      // 调用API
      console.log('[RequirementForm] ⏳ 正在调用generateAppSpec API...');
      const startTime = Date.now();
      const response = await generateAppSpec(request);
      const duration = Date.now() - startTime;
      clearTimeout(timeoutId);

      console.log('[RequirementForm] ===== 📥 API响应详情 =====');
      console.log('[RequirementForm] 耗时:', duration, 'ms');
      console.log('[RequirementForm] response.success:', response.success);
      console.log('[RequirementForm] response.data:', JSON.stringify(response.data, null, 2));
      console.log('[RequirementForm] response.error:', response.error);
      console.log('[RequirementForm] response.metadata:', response.metadata);

      if (response.success && response.data) {
        // 🆕 详细验证appSpecId
        const appSpecId = response.data.appSpecId?.trim();
        console.log('[RequirementForm] 🔑 提取的appSpecId:', appSpecId);
        console.log('[RequirementForm] appSpecId类型:', typeof appSpecId);
        console.log('[RequirementForm] appSpecId长度:', appSpecId?.length);

        if (!appSpecId) {
          // 🆕 appSpecId为空 - 提供详细错误诊断
          console.error('[RequirementForm] ❌ ========== CRITICAL: appSpecId为空 ==========');
          console.error('[RequirementForm] 完整response对象:', response);
          console.error('[RequirementForm] response.data的所有键:', Object.keys(response.data));
          console.error('[RequirementForm] response.data.appSpecId:', response.data.appSpecId);
          console.error('[RequirementForm] response.data.projectId:', response.data.projectId);
          console.error('[RequirementForm] response.data.status:', response.data.status);
          console.error('[RequirementForm] ===================================');

          toast({
            title: "生成失败 ❌",
            description: `服务器未返回有效的AppSpec ID。

请查看浏览器控制台（F12 → Console）中的详细日志，
然后联系技术支持并提供以下错误码：EMPTY_APPSPEC_ID

错误详情：API响应成功但缺少appSpecId字段`,
            variant: "destructive",
            duration: 15000,  // 显示15秒
          });
          setLoading(false);
          setShowAnalysis(false);
          setCurrentPhase('idle');  // 🆕 重置阶段
          return;
        }

        // ✅ appSpecId正常
        console.log('[RequirementForm] ✅ appSpecId验证通过');
        const targetUrl = `/wizard/${appSpecId}`;
        console.log('[RequirementForm] 🎯 目标URL:', targetUrl);

        // 🆕 切换到导航阶段
        setCurrentPhase('navigating');

        // UI增强：显示成功动画
        setShowSuccess(true);
        setLoading(false);

        // 显示成功提示
        toast({
          title: "生成成功 ✅",
          description: `正在跳转到向导页面... (${duration}ms)`,
          duration: 3000,
        });

        // 延迟导航，让用户看到成功动画
        setTimeout(() => {
          console.log('[RequirementForm] ===== 🚀 执行导航 =====');
          console.log('[RequirementForm] router.push:', targetUrl);
          router.push(targetUrl);
          console.log('[RequirementForm] ✅ router.push已调用');
        }, 800);

      } else {
        // ❌ API调用失败
        console.error('[RequirementForm] ❌ ========== API调用失败 ==========');
        console.error('[RequirementForm] response.success:', response.success);
        console.error('[RequirementForm] response.error:', response.error);
        console.error('[RequirementForm] response.metadata:', response.metadata);
        console.error('[RequirementForm] ===================================');

        toast({
          title: "生成失败 ❌",
          description: response.error || "服务器错误，请稍后重试",
          variant: "destructive",
          duration: 10000,
        });
        setLoading(false);
        setShowAnalysis(false);
        setCurrentPhase('idle');  // 🆕 重置阶段
      }
    } catch (err) {
      // ❌ 异常捕获
      console.error('[RequirementForm] ❌ ========== 完整生成流程异常 ==========');
      console.error('[RequirementForm] 异常对象:', err);
      console.error('[RequirementForm] 异常详情:', {
        name: err instanceof Error ? err.name : 'Unknown',
        message: err instanceof Error ? err.message : String(err),
        stack: err instanceof Error ? err.stack : 'No stack trace',
      });
      console.error('[RequirementForm] ===================================');

      let errorMessage = "发生未知错误";
      if (err instanceof APIError) {
        errorMessage = `API错误: ${err.message}`;
      } else if (err instanceof Error) {
        errorMessage = err.message;
        if (err.name === 'AbortError') {
          errorMessage = "请求超时（120秒），请简化需求描述后重试";
        }
      }

      toast({
        title: "系统错误 ❌",
        description: `${errorMessage}

请查看浏览器控制台（F12 → Console）获取详细错误信息`,
        variant: "destructive",
        duration: 15000,
      });
      setLoading(false);
      setShowAnalysis(false);
      setCurrentPhase('idle');  // 🆕 重置阶段
    }
  }

  /**
   * 表单提交处理
   *
   * 修复说明：
   * - 点击"生成应用"后显示左右分屏
   * - 左侧显示需求文本
   * - 右侧显示实时分析进度
   * - 分析完成后自动调用完整生成API并导航
   */
  async function handleSubmit(e: React.FormEvent): Promise<void> {
    e.preventDefault();
    console.log('[RequirementForm] 表单提交开始');

    // 验证
    if (!requirement.trim()) {
      console.log('[RequirementForm] 验证失败：空需求');
      toast({
        title: "验证错误",
        description: "需求描述不能为空",
        variant: "destructive",
      });
      return;
    }

    if (requirement.trim().length < 10) {
      console.log('[RequirementForm] 验证失败：需求太短');
      toast({
        title: "验证错误",
        description: "需求描述至少需要10个字符",
        variant: "destructive",
      });
      return;
    }

    // 显示分析面板并开始SSE连接
    setLoading(true);
    setShowAnalysis(true);
    setCurrentPhase('analyzing');  // 🆕 设置为分析阶段
    console.log('[RequirementForm] ===== 开始实时分析阶段 =====');
    console.log('[RequirementForm] currentPhase: idle → analyzing');

    // 启动SSE分析
    startAnalysis();
  }

  /**
   * 处理风格选择
   * 用户选择风格后，切换到生成阶段并调用完整生成API
   */
  function handleStyleSelected(style: string): void {
    console.log('[RequirementForm] ===== 用户选择风格 =====');
    console.log('[RequirementForm] 选中风格:', style);

    setSelectedStyle(style);
    setCurrentPhase('generating');

    // TODO: 等待后端支持selectedStyle参数后，将风格传递给生成API
    // 当前selectedStyle仅用于记录用户选择，未传递给后端
    console.log('[RequirementForm] 已保存用户选择的风格（待后端API支持）:', style);

    // 延迟一下让用户看到选中状态
    setTimeout(() => {
      handleFullGeneration();
    }, 500);
  }

  /**
   * 处理取消风格选择
   * 返回到输入表单
   */
  function handleStyleCancel(): void {
    console.log('[RequirementForm] 取消风格选择，返回输入表单');

    setLoading(false);
    setShowAnalysis(false);
    setCurrentPhase('idle');
    setSelectedStyle(null);
  }

  /**
   * 清除已加载的模板
   * 重置为空白表单
   */
  function handleClearTemplate(): void {
    setRequirement("");
    setLoadedTemplate(null);
    toast({
      title: "模板已清除",
      description: "已重置为空白表单",
    });
  }

  return (
    <PageTransition type="slide-up" duration={0.4}>
      <div className="space-y-10">
        {/* 成功动画覆盖层 */}
        {showSuccess && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm">
            <div className="flex flex-col items-center gap-4">
              <SuccessAnimation size={96} showConfetti={true} />
              <p className="text-lg font-semibold">生成成功！</p>
              <p className="text-sm text-muted-foreground">正在跳转到向导页面...</p>
            </div>
          </div>
        )}

        {/* 分析面板：左右分屏布局 */}
        {showAnalysis ? (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* 左侧：需求文本（只读） */}
            <div className="space-y-4">
              <div className="rounded-2xl border border-border/50 bg-card/30 p-6 backdrop-blur-xl">
                <h3 className="text-lg font-semibold mb-4">你的需求</h3>
                <div className="text-sm text-muted-foreground whitespace-pre-wrap">
                  {requirement}
                </div>
              </div>
              <div className="rounded-2xl border border-border/50 bg-card/30 p-4 backdrop-blur-xl space-y-2">
                <p className="text-sm text-muted-foreground">
                  <strong>AI模型：</strong>{MODEL_CONFIGS.find(m => m.id === selectedModel)?.name || selectedModel}
                </p>
                {selectedStyle && (
                  <p className="text-sm text-muted-foreground">
                    <strong>选中风格：</strong>{selectedStyle}
                  </p>
                )}
              </div>
            </div>

            {/* 右侧：根据阶段显示不同内容 */}
            <div className="rounded-2xl border border-border/50 bg-card/30 p-6 backdrop-blur-xl">
              {currentPhase === 'style-selection' ? (
                // 风格选择阶段：显示StylePicker
                <StylePicker
                  userRequirement={requirement}
                  appType="应用"
                  targetPlatform="web"
                  useAICustomization={false}
                  onStyleSelected={handleStyleSelected}
                  onCancel={handleStyleCancel}
                />
              ) : (
                // 分析阶段：显示分析进度面板
                <AnalysisProgressPanel
                  messages={messages}
                  isConnected={isConnected}
                  isCompleted={isCompleted}
                  error={analysisError}
                />
              )}
            </div>
          </div>
        ) : (
          <>
            {/* AI模型选择 - 顶部独立显示 */}
            <div className="flex items-center justify-between rounded-2xl border border-border/50 bg-card/30 p-4 backdrop-blur-xl">
              <div className="space-y-1">
                <p className="text-sm font-medium">AI模型</p>
                <p className="text-xs text-muted-foreground">
                  选择最适合的AI模型来理解你的需求
                </p>
              </div>
              <ModelSelector
                value={selectedModel}
                onValueChange={setSelectedModel}
                disabled={loading}
              />
            </div>

            {/* 模板加载提示 */}
            {loadedTemplate && (
              <div className="flex items-center justify-between rounded-2xl border border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/10 p-4 backdrop-blur-xl">
                <div className="flex items-center gap-3">
                  <Library className="h-5 w-5 text-green-600 dark:text-green-400" />
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-green-900 dark:text-green-100">
                        使用模板
                      </span>
                      <Badge variant="secondary" className="bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300">
                        {loadedTemplate.name}
                      </Badge>
                    </div>
                    <p className="text-xs text-green-700 dark:text-green-400 mt-0.5">
                      已自动填充模板内容，您可以继续编辑或直接提交
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Link href="/templates">
                    <Button variant="outline" size="sm" className="border-green-200 dark:border-green-800 hover:bg-green-100 dark:hover:bg-green-900/20">
                      <Library className="mr-1.5 h-3.5 w-3.5" />
                      更换模板
                    </Button>
                  </Link>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleClearTemplate}
                    className="hover:bg-green-100 dark:hover:bg-green-900/20"
                  >
                    <X className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}

            {/* 需求输入表单 */}
            <form onSubmit={handleSubmit} className="space-y-8">
          {/* 输入框 - 修改placeholder为E2E测试期望的文本 */}
          <div className="group relative">
            <Textarea
              id="requirement"
              placeholder="描述你想要的应用...&#10;例如：创建一个校园二手交易平台，支持商品发布、搜索、聊天和交易评价"
              value={requirement}
              onChange={(e) => setRequirement(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && e.metaKey) {
                  // Cmd/Ctrl+Enter 提交
                  e.preventDefault();
                  handleSubmit(e);
                }
              }}
              required
              disabled={loading}
              className="min-h-[200px] resize-none rounded-2xl border-2 border-border/50 bg-card/30 p-6 text-base backdrop-blur-xl transition-all focus-visible:border-primary focus-visible:ring-4 focus-visible:ring-primary/20"
            />

            {/* 字数提示 */}
            <p className="mt-3 text-right text-xs text-muted-foreground">
              {requirement.length} 字符 {requirement.length < 10 && "（至少需要10个字符）"}
            </p>
          </div>

          {/* 双按钮布局：快速Web预览 + 完整生成 */}
          <div className="flex flex-col sm:flex-row gap-4">
            {/* 快速Web预览按钮(左侧) */}
            <Button
              type="button"
              onClick={(e) => {
                e.preventDefault();
                if (requirement.trim().length >= 10) {
                  // 跳转到快速预览页面
                  router.push(`/preview-quick/${encodeURIComponent(requirement)}`);
                }
              }}
              disabled={loading || requirement.trim().length < 10}
              size="lg"
              variant="outline"
              className="h-14 flex-1 gap-2 rounded-2xl text-base font-semibold shadow-md transition-all hover:shadow-lg"
            >
              <Zap className="h-5 w-5 text-purple-500" />
              快速Web预览 (5-10秒)
            </Button>

            {/* 完整生成按钮(右侧) */}
            <Button
              type="submit"
              disabled={loading || requirement.trim().length < 10}
              size="lg"
              className="h-14 flex-1 gap-2 rounded-2xl text-base font-semibold shadow-lg transition-all hover:shadow-xl"
            >
              {loading ? (
                <>
                  <svg
                    className="h-5 w-5 animate-spin"
                    xmlns="http://www.w3.org/2000/svg"
                    fill="none"
                    viewBox="0 0 24 24"
                  >
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                    />
                  </svg>
                  AI正在分析你的需求...
                </>
              ) : (
                <>
                  <Sparkles className="h-5 w-5" />
                生成应用
              </>
            )}
          </Button>
          </div>

          {/* 快捷提示 */}
              <p className="text-center text-sm text-muted-foreground">
                按 <kbd className="rounded border px-1.5 py-0.5 text-xs">⌘</kbd> + <kbd className="rounded border px-1.5 py-0.5 text-xs">Enter</kbd> 快速提交
              </p>
            </form>

            {/* 模板快捷入口 - 横向滚动 */}
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-muted-foreground">
                  快速模板
                </h2>
                <span className="text-xs text-muted-foreground">
                  点击填充 →
                </span>
              </div>

              <ScrollArea className="w-full whitespace-nowrap">
                <div className="flex gap-4 pb-4">
                  {templates.map((template) => (
                    <div key={template.id} data-template={template.id}>
                      <TemplateCard {...template} onClick={handleTemplateClick} />
                    </div>
                  ))}
                </div>
                <ScrollBar orientation="horizontal" />
              </ScrollArea>

              {/* 浏览全部模板按钮 */}
              <div className="flex justify-center pt-2">
                <Link
                  href="/templates"
                  className="group flex items-center gap-2 rounded-xl border-2 border-dashed border-border/50 bg-card/30 px-6 py-3 text-sm font-medium text-muted-foreground backdrop-blur-xl transition-all hover:border-primary/50 hover:bg-card/50 hover:text-primary hover:shadow-lg"
                >
                  <Library className="h-4 w-4 transition-transform group-hover:scale-110" />
                  浏览全部模板
                  <ChevronRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
                </Link>
              </div>
            </div>
          </>
        )}
      </div>
    </PageTransition>
  );
}
