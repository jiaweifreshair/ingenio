/**
 * useGenerationFlow Hook
 * 管理完整的生成流程：分析 → 原型预览 → 确认生成 → 导航
 *
 * V2.0 简化版：统一使用V2 API链路
 * - routeRequirement: 意图识别 + 模板匹配
 * - selectStyleAndGeneratePrototype: 风格选择 + 原型生成
 * - confirmDesign + executeCodeGeneration: 确认设计 + 代码生成
 *
 * 流程说明：
 * 1. 用户点击"生成应用" → 启动SSE分析 + 路由识别（并行）
 * 2. 分析完成 → 自动选择默认风格生成原型 → 进入原型预览
 * 3. 用户确认设计 → 调用代码生成API → 跳转到wizard页面
 *
 * @author Ingenio Team
 * @since V2.0
 */

import { useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useToast } from '@/hooks/use-toast';
import { useAnalysisSse, type AnalysisProgressMessage } from '@/hooks/use-analysis-sse';
import {
  routeRequirement,
  selectTemplate,
  confirmDesign,
  executeCodeGeneration,
  type PlanRoutingResult
} from '@/lib/api/plan-routing';
import { subscribeToG3Logs } from '@/lib/api/g3';
import type { UniaixModel } from '@/lib/api/uniaix';
import type { PhaseType, LoadedTemplate } from '@/types/requirement-form';
import type { G3LogEntry } from '@/types/g3';

/**
 * useGenerationFlow Hook配置
 */
export interface UseGenerationFlowProps {
  /** 需求描述 */
  requirement: string;
  /** 选中的AI模型 */
  selectedModel: UniaixModel;
  /** 已加载的模板（可选，用于 F2: selectTemplate） */
  loadedTemplate?: LoadedTemplate | null;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
  /** 设置显示分析面板 */
  setShowAnalysis: (show: boolean) => void;
  /** 设置当前阶段 */
  setCurrentPhase: (phase: PhaseType) => void;
  /** 设置显示成功动画 */
  setShowSuccess: (show: boolean) => void;
  /** 路由结果 (V2) */
  routingResult?: PlanRoutingResult | null;
  /** 设置路由结果 */
  setRoutingResult?: (result: PlanRoutingResult | null) => void;
  /** 设置G3日志 */
  setG3Logs?: (logs: G3LogEntry[] | ((prev: G3LogEntry[]) => G3LogEntry[])) => void;
}

/**
 * useGenerationFlow Hook返回值
 * V2.0简化版：移除handleStyleSelected（风格选择已自动化）
 */
export interface UseGenerationFlowReturn {
  /** 分析消息列表 */
  messages: AnalysisProgressMessage[];
  /** SSE连接状态 */
  isConnected: boolean;
  /** 分析是否完成 */
  isCompleted: boolean;
  /** 分析错误信息 */
  analysisError: string | null;
  /** 处理表单提交（启动分析） */
  handleFormSubmit: (e: React.FormEvent) => Promise<void>;
  /** 处理取消/返回 */
  handleStyleCancel: () => void;
  /** 确认设计并生成代码 */
  handleConfirmDesign: () => Promise<void>;
}

/**
 * useGenerationFlow Hook
 * 管理完整的V2生成流程
 */
export function useGenerationFlow({
  requirement,
  setLoading,
  setShowAnalysis,
  setCurrentPhase,
  setShowSuccess,
  loadedTemplate,
  routingResult,
  setRoutingResult,
  setG3Logs,
}: UseGenerationFlowProps): UseGenerationFlowReturn {
  const router = useRouter();
  const { toast } = useToast();

  // ==================== SSE分析Hook ====================
  const {
    messages,
    isConnected,
    isCompleted,
    error: analysisError,
    connect: startAnalysis,
  } = useAnalysisSse({
    requirement,
    autoConnect: false,
    onComplete: () => {
      // SSE分析完成，切换到方案评审阶段 (V2.1)
      console.log('[useGenerationFlow] SSE分析完成，进入方案评审');
      setCurrentPhase('plan-review');
    },
    onError: (error) => {
      console.error('[useGenerationFlow] SSE分析失败:', error);
      toast({
        title: '分析失败',
        description: error,
        variant: 'destructive',
      });
      setLoading(false);
      setShowAnalysis(false);
      setCurrentPhase('idle');
    },
  });

  /**
   * 表单提交处理
   * 验证后启动SSE分析 + V2路由识别（并行）
   */
  const handleFormSubmit = useCallback(
    async (e: React.FormEvent): Promise<void> => {
      e.preventDefault();
      console.log('[useGenerationFlow] 表单提交开始');

      // 验证
      if (!requirement.trim()) {
        toast({
          title: '验证错误',
          description: '需求描述不能为空',
          variant: 'destructive',
        });
        return;
      }

      if (requirement.trim().length < 10) {
        toast({
          title: '验证错误',
          description: '需求描述至少需要10个字符',
          variant: 'destructive',
        });
        return;
      }

      // 显示分析面板并开始
      setLoading(true);
      setShowAnalysis(true);
      setCurrentPhase('analyzing');
      console.log('[useGenerationFlow] 开始分析阶段');

      // 1. 启动SSE分析（视觉效果）
      startAnalysis();

      // 2. 并行调用V2路由API（获取appSpecId）
      try {
        console.log('[useGenerationFlow] 调用routeRequirement:', requirement);
        const result = await routeRequirement({ userRequirement: requirement });
        console.log('[useGenerationFlow] 路由结果:', result);

        if (setRoutingResult) {
          setRoutingResult(result);
        }

        // F2: 如果用户从模板库页面带入 templateId（UUID），则立即绑定并加载 Blueprint
        const templateId = loadedTemplate?.id;
        const isUuidTemplateId =
          typeof templateId === 'string' &&
          /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(templateId);

        if (templateId && isUuidTemplateId) {
          try {
            const updated = await selectTemplate(result.appSpecId, templateId);
            setRoutingResult?.(updated);
            toast({
              title: 'Blueprint 已加载',
              description: `已绑定行业模板: ${loadedTemplate?.name || templateId}`,
            });
          } catch (e) {
            console.warn('[useGenerationFlow] 选择模板失败（已忽略，不阻塞主流程）:', e);
            toast({
              title: '模板绑定失败',
              description: e instanceof Error ? e.message : '请选择模板后重试',
              variant: 'destructive',
            });
          }
        }
      } catch (err) {
        console.error('[useGenerationFlow] 路由失败:', err);
        toast({
          title: '分析失败',
          description: err instanceof Error ? err.message : '服务器连接失败',
          variant: 'destructive',
        });
        setLoading(false);
        setShowAnalysis(false);
        setCurrentPhase('idle');
      }
    },
    [requirement, setLoading, setShowAnalysis, setCurrentPhase, startAnalysis, toast, setRoutingResult, loadedTemplate]
  );

  /**
   * 处理取消/返回
   * 重置到初始状态
   */
  const handleStyleCancel = useCallback((): void => {
    console.log('[useGenerationFlow] 取消，返回输入表单');
    setLoading(false);
    setShowAnalysis(false);
    setCurrentPhase('idle');
    if (setRoutingResult) {
      setRoutingResult(null);
    }
  }, [setLoading, setShowAnalysis, setCurrentPhase, setRoutingResult]);

  /**
   * 确认设计并生成代码
   * V2完整流程：confirmDesign → executeCodeGeneration → 跳转wizard
   */
  const handleConfirmDesign = useCallback(async (): Promise<void> => {
    if (!routingResult?.appSpecId) {
      toast({
        title: '错误',
        description: 'AppSpec ID 丢失，请重新开始',
        variant: 'destructive',
      });
      return;
    }

    setLoading(true);
    console.log('[useGenerationFlow] 开始确认设计:', routingResult.appSpecId);

    try {
      // Step 1: 确认设计
      console.log('[useGenerationFlow] 调用confirmDesign...');
      const confirmResult = await confirmDesign(routingResult.appSpecId);
      console.log('[useGenerationFlow] 确认结果:', confirmResult);

      if (!confirmResult.success || !confirmResult.canProceedToExecute) {
        throw new Error(confirmResult.message || '设计确认失败');
      }

      // Step 2: 执行代码生成 (Phase 1.5: G3 Visual Overlay)
      console.log('[useGenerationFlow] 调用executeCodeGeneration...');

      const codeResult = await executeCodeGeneration(routingResult.appSpecId);
      console.log('[useGenerationFlow] 代码生成结果:', codeResult);

      // G3 日志流（使用 executeCodeGeneration 返回的 jobId，避免重复创建任务）
      if (setG3Logs) {
        setG3Logs([{
          timestamp: new Date().toISOString(),
          role: 'SYSTEM',
          level: 'info',
          message: 'G3 任务已提交，正在订阅日志...',
        }]);
      }

      const jobId = codeResult.jobId;
      if (setG3Logs && jobId) {
        let finished = false;
        let resolveLogPromise: (() => void) | null = null;

        const logStreamPromise = new Promise<void>((resolve) => {
          resolveLogPromise = resolve;
        });

        const finish = () => {
          if (finished) return;
          finished = true;
          resolveLogPromise?.();
        };

        const cancelLogs = subscribeToG3Logs(jobId, {
          onOpen: (info) => {
            setG3Logs((prev) => [
              ...prev,
              {
                timestamp: new Date().toISOString(),
                role: 'SYSTEM',
                level: 'info',
                message: `日志连接已建立 (HTTP ${info.status})`,
              },
            ]);
          },
          onLog: (entry) => {
            if (entry.level === 'heartbeat') return;
            setG3Logs((prev) => [...prev, entry]);
          },
          onError: (error) => {
            setG3Logs((prev) => [
              ...prev,
              {
                timestamp: new Date().toISOString(),
                role: 'SYSTEM',
                level: 'warn',
                message: `日志连接异常: ${error}`,
              },
            ]);
            finish();
          },
          onComplete: () => {
            setG3Logs((prev) => [
              ...prev,
              {
                timestamp: new Date().toISOString(),
                role: 'SYSTEM',
                level: 'success',
                message: 'G3 任务已完成',
              },
            ]);
            finish();
          },
          onClose: finish,
        });

        await logStreamPromise;
        cancelLogs();
      } else if (setG3Logs && !jobId) {
        setG3Logs((prev) => [
          ...prev,
          {
            timestamp: new Date().toISOString(),
            role: 'SYSTEM',
            level: 'warn',
            message: '未返回 jobId，无法订阅实时日志',
          },
        ]);
      }

      if (codeResult.success) {
        // 显示成功动画
        setShowSuccess(true);

        toast({
          title: '生成成功',
          description: '正在跳转到应用详情页...',
          duration: 3000,
        });

        // 延迟跳转，让用户看到成功动画
        setTimeout(() => {
          router.push(`/wizard/${routingResult.appSpecId}`);
        }, 800);
      } else {
        throw new Error(codeResult.error || '代码生成失败');
      }
    } catch (err) {
      console.error('[useGenerationFlow] 生成失败:', err);
      toast({
        title: '生成失败',
        description: err instanceof Error ? err.message : '生成失败，请重试',
        variant: 'destructive',
      });
      setLoading(false);
    }
  }, [routingResult, setLoading, setShowSuccess, router, toast, setG3Logs, requirement]);

  return {
    messages,
    isConnected,
    isCompleted,
    analysisError,
    handleFormSubmit,
    handleStyleCancel,
    handleConfirmDesign,
  };
}
