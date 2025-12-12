/**
 * useGenerationFlow Hook
 * 管理完整的生成流程：分析 → 风格选择 → 全量生成 → 导航
 *
 * 职责：
 * - 集成SSE分析Hook
 * - 处理表单提交
 * - 管理流程阶段切换
 * - 调用完整生成API
 * - 处理导航跳转
 * - 统一错误处理
 *
 * 流程说明：
 * 1. 用户点击"生成应用" → 开始SSE分析（analyzing阶段）
 * 2. SSE分析完成 → 切换到风格选择（style-selection阶段）
 * 3. 用户选择风格 → 调用完整生成API（generating阶段）
 * 4. 生成成功 → 显示成功动画并导航（navigating阶段）
 *
 * @author Ingenio Team
 * @since V2.0
 */

import { useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useToast } from '@/hooks/use-toast';
import { useAnalysisSse, type AnalysisProgressMessage } from '@/hooks/use-analysis-sse';
import { generateAppSpec, type GenerateRequest } from '@/lib/api/generate';
import { APIError } from '@/lib/api/client';
import type { UniaixModel } from '@/lib/api/uniaix';
import type { PhaseType } from '@/types/requirement-form';

/**
 * useGenerationFlow Hook配置
 */
export interface UseGenerationFlowProps {
  /** 需求描述 */
  requirement: string;
  /** 选中的AI模型 */
  selectedModel: UniaixModel;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
  /** 设置显示分析面板 */
  setShowAnalysis: (show: boolean) => void;
  /** 设置当前阶段 */
  setCurrentPhase: (phase: PhaseType) => void;
  /** 设置显示成功动画 */
  setShowSuccess: (show: boolean) => void;
}

/**
 * useGenerationFlow Hook返回值
 */
export interface UseGenerationFlowReturn {
  // ==================== SSE分析状态 ====================
  /** 分析消息列表 */
  messages: AnalysisProgressMessage[];
  /** SSE连接状态 */
  isConnected: boolean;
  /** 分析是否完成 */
  isCompleted: boolean;
  /** 分析错误信息 */
  analysisError: string | null;

  // ==================== 方法 ====================
  /** 处理表单提交（启动分析） */
  handleFormSubmit: (e: React.FormEvent) => Promise<void>;
  /** 处理风格选择 */
  handleStyleSelected: (style: string) => void;
  /** 处理取消风格选择 */
  handleStyleCancel: () => void;
}

/**
 * useGenerationFlow Hook
 * 管理完整的生成流程
 */
export function useGenerationFlow({
  requirement,
  selectedModel,
  setLoading,
  setShowAnalysis,
  setCurrentPhase,
  setShowSuccess,
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
    autoConnect: false, // 手动控制连接
    onComplete: () => {
      // SSE分析完成，切换到风格选择阶段
      console.log('[useGenerationFlow] SSE分析完成，进入风格选择阶段');
      setCurrentPhase('style-selection');
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
   * 调用完整生成API并导航
   * 在用户选择风格后调用
   */
  const handleFullGeneration = useCallback(async (): Promise<void> => {
    console.log('[useGenerationFlow] 开始完整生成流程');
    console.log('[useGenerationFlow] requirement:', requirement);
    console.log('[useGenerationFlow] selectedModel:', selectedModel);

    try {
      // 构造请求参数
      const request: GenerateRequest = {
        requirement: requirement.trim(),
        model: selectedModel,
        tenantId: 'default-tenant',
        userId: 'default-user',
      };

      console.log('[useGenerationFlow] API请求参数:', JSON.stringify(request, null, 2));

      // 添加超时保护（120秒）
      const controller = new AbortController();
      const timeoutId = setTimeout(() => {
        console.error('[useGenerationFlow] API调用超时（120秒）');
        controller.abort();
      }, 120000);

      // 调用API
      console.log('[useGenerationFlow] 正在调用generateAppSpec API...');
      const startTime = Date.now();
      const response = await generateAppSpec(request);
      const duration = Date.now() - startTime;
      clearTimeout(timeoutId);

      console.log('[useGenerationFlow] API响应耗时:', duration, 'ms');
      console.log('[useGenerationFlow] response.success:', response.success);
      console.log('[useGenerationFlow] response.data:', JSON.stringify(response.data, null, 2));

      if (response.success && response.data) {
        // 验证appSpecId
        const appSpecId = response.data.appSpecId?.trim();
        console.log('[useGenerationFlow] 提取的appSpecId:', appSpecId);

        if (!appSpecId) {
          console.error('[useGenerationFlow] appSpecId为空');
          console.error('[useGenerationFlow] 完整response对象:', response);

          toast({
            title: '生成失败',
            description: '服务器未返回有效的AppSpec ID，请重试或联系技术支持',
            variant: 'destructive',
            duration: 15000,
          });
          setLoading(false);
          setShowAnalysis(false);
          setCurrentPhase('idle');
          return;
        }

        // 生成成功
        console.log('[useGenerationFlow] 生成成功，appSpecId:', appSpecId);
        const targetUrl = `/wizard/${appSpecId}`;

        // 切换到导航阶段
        setCurrentPhase('navigating');

        // 显示成功动画
        setShowSuccess(true);
        setLoading(false);

        // 显示成功提示
        toast({
          title: '生成成功',
          description: `正在跳转到向导页面... (${duration}ms)`,
          duration: 3000,
        });

        // 延迟导航，让用户看到成功动画
        setTimeout(() => {
          console.log('[useGenerationFlow] 执行导航:', targetUrl);
          router.push(targetUrl);
        }, 800);
      } else {
        // API调用失败
        console.error('[useGenerationFlow] API调用失败');
        console.error('[useGenerationFlow] response.error:', response.error);

        toast({
          title: '生成失败',
          description: response.error || '服务器错误，请稍后重试',
          variant: 'destructive',
          duration: 10000,
        });
        setLoading(false);
        setShowAnalysis(false);
        setCurrentPhase('idle');
      }
    } catch (err) {
      // 异常捕获
      console.error('[useGenerationFlow] 完整生成流程异常:', err);

      let errorMessage = '发生未知错误';
      if (err instanceof APIError) {
        errorMessage = `API错误: ${err.message}`;
      } else if (err instanceof Error) {
        errorMessage = err.message;
        if (err.name === 'AbortError') {
          errorMessage = '请求超时（120秒），请简化需求描述后重试';
        }
      }

      toast({
        title: '系统错误',
        description: errorMessage,
        variant: 'destructive',
        duration: 15000,
      });
      setLoading(false);
      setShowAnalysis(false);
      setCurrentPhase('idle');
    }
  }, [requirement, selectedModel, setLoading, setShowAnalysis, setCurrentPhase, setShowSuccess, toast, router]);

  /**
   * 表单提交处理
   * 验证后启动SSE分析
   */
  const handleFormSubmit = useCallback(
    async (e: React.FormEvent): Promise<void> => {
      e.preventDefault();
      console.log('[useGenerationFlow] 表单提交开始');

      // 验证
      if (!requirement.trim()) {
        console.log('[useGenerationFlow] 验证失败：空需求');
        toast({
          title: '验证错误',
          description: '需求描述不能为空',
          variant: 'destructive',
        });
        return;
      }

      if (requirement.trim().length < 10) {
        console.log('[useGenerationFlow] 验证失败：需求太短');
        toast({
          title: '验证错误',
          description: '需求描述至少需要10个字符',
          variant: 'destructive',
        });
        return;
      }

      // 显示分析面板并开始SSE连接
      setLoading(true);
      setShowAnalysis(true);
      setCurrentPhase('analyzing');
      console.log('[useGenerationFlow] 开始实时分析阶段');

      // 启动SSE分析
      startAnalysis();
    },
    [requirement, setLoading, setShowAnalysis, setCurrentPhase, startAnalysis, toast]
  );

  /**
   * 处理风格选择
   * 用户选择风格后，切换到生成阶段并调用完整生成API
   */
  const handleStyleSelected = useCallback(
    (style: string): void => {
      console.log('[useGenerationFlow] 用户选择风格:', style);

      setCurrentPhase('generating');

      // TODO: 等待后端支持selectedStyle参数后，将风格传递给生成API
      console.log('[useGenerationFlow] 已保存用户选择的风格（待后端API支持）:', style);

      // 延迟一下让用户看到选中状态
      setTimeout(() => {
        handleFullGeneration();
      }, 500);
    },
    [setCurrentPhase, handleFullGeneration]
  );

  /**
   * 处理取消风格选择
   * 返回到输入表单
   */
  const handleStyleCancel = useCallback((): void => {
    console.log('[useGenerationFlow] 取消风格选择，返回输入表单');

    setLoading(false);
    setShowAnalysis(false);
    setCurrentPhase('idle');
  }, [setLoading, setShowAnalysis, setCurrentPhase]);

  return {
    // SSE分析状态
    messages,
    isConnected,
    isCompleted,
    analysisError,

    // 方法
    handleFormSubmit,
    handleStyleSelected,
    handleStyleCancel,
  };
}
