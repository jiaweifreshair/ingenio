/**
 * useFormState Hook
 * 集中管理RequirementForm的所有状态
 *
 * 职责：
 * - 管理表单输入状态（requirement, selectedModel）
 * - 管理UI状态（loading, showSuccess, showAnalysis）
 * - 管理流程阶段（currentPhase）
 * - 管理风格选择（selectedStyle）
 * - 管理模板加载（loadedTemplate）
 *
 * @author Ingenio Team
 * @since V2.0
 */

import { useState, useCallback } from 'react';
import { UNIAIX_MODELS, type UniaixModel } from '@/lib/api/uniaix';
import type { PlanRoutingResult } from '@/lib/api/plan-routing';
import type { PhaseType, LoadedTemplate } from '@/types/requirement-form';
import type { G3LogEntry } from '@/types/g3';

/**
 * useFormState Hook返回值
 */
export interface UseFormStateReturn {
  // ==================== 状态 ====================
  /** 需求描述文本 */
  requirement: string;
  /** 选中的AI模型 */
  selectedModel: UniaixModel;
  /** 选中的设计风格 */
  selectedStyle: string | null;
  /** 是否正在加载 */
  loading: boolean;
  /** 是否显示成功动画 */
  showSuccess: boolean;
  /** 是否显示分析面板 */
  showAnalysis: boolean;
  /** 当前流程阶段 */
  currentPhase: PhaseType;
  /** 加载的模板信息 */
  loadedTemplate: LoadedTemplate | null;
  /** 路由结果 */
  routingResult: PlanRoutingResult | null;
  /** G3引擎日志 */
  g3Logs: G3LogEntry[];

  // ==================== 状态设置方法 ====================
  /** 设置需求描述 */
  setRequirement: (value: string) => void;
  /** 设置选中的AI模型 */
  setSelectedModel: (model: UniaixModel) => void;
  /** 设置选中的设计风格 */
  setSelectedStyle: (style: string | null) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
  /** 设置是否显示成功动画 */
  setShowSuccess: (show: boolean) => void;
  /** 设置是否显示分析面板 */
  setShowAnalysis: (show: boolean) => void;
  /** 设置当前流程阶段 */
  setCurrentPhase: (phase: PhaseType) => void;
  /** 设置加载的模板信息 */
  setLoadedTemplate: (template: LoadedTemplate | null) => void;
  /** 设置路由结果 */
  setRoutingResult: (result: PlanRoutingResult | null) => void;
  /** 设置G3日志 */
  setG3Logs: (logs: G3LogEntry[] | ((prev: G3LogEntry[]) => G3LogEntry[])) => void;

  // ==================== 操作方法 ====================
  /** 清除已加载的模板 */
  clearTemplate: () => void;
  /** 重置所有状态到初始值 */
  resetAll: () => void;
}

/**
 * useFormState Hook
 * 集中管理表单状态，提供统一的状态管理接口
 */
export function useFormState(): UseFormStateReturn {
  // ==================== 状态定义 ====================
  const [requirement, setRequirement] = useState('');
  const [selectedModel, setSelectedModel] = useState<UniaixModel>(UNIAIX_MODELS.GEMINI_3_PRO_PREVIEW);
  const [selectedStyle, setSelectedStyle] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);
  const [showAnalysis, setShowAnalysis] = useState(false);
  const [currentPhase, setCurrentPhase] = useState<PhaseType>('idle');
  const [loadedTemplate, setLoadedTemplate] = useState<LoadedTemplate | null>(null);
  const [routingResult, setRoutingResult] = useState<PlanRoutingResult | null>(null);
  const [g3Logs, setG3Logs] = useState<G3LogEntry[]>([]);

  /**
   * 清除已加载的模板
   * 同时清空需求描述和模板信息
   */
  const clearTemplate = useCallback(() => {
    setRequirement('');
    setLoadedTemplate(null);
  }, []);

  /**
   * 重置所有状态到初始值
   * 用于用户取消或重新开始流程
   */
  const resetAll = useCallback(() => {
    setRequirement('');
    setSelectedModel(UNIAIX_MODELS.GEMINI_3_PRO_PREVIEW);
    setSelectedStyle(null);
    setLoading(false);
    setShowSuccess(false);
    setShowAnalysis(false);
    setCurrentPhase('idle');
    setLoadedTemplate(null);
    setRoutingResult(null);
    setG3Logs([]);
  }, []);

  return {
    // 状态
    requirement,
    selectedModel,
    selectedStyle,
    loading,
    showSuccess,
    showAnalysis,
    currentPhase,
    loadedTemplate,
    routingResult,
    g3Logs,

    // 方法
    setRequirement,
    setSelectedModel,
    setSelectedStyle,
    setLoading,
    setShowSuccess,
    setShowAnalysis,
    setCurrentPhase,
    setLoadedTemplate,
    setRoutingResult,
    setG3Logs,
    clearTemplate,
    resetAll,
  };
}
