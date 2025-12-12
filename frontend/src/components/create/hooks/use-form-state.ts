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
import type { PhaseType, LoadedTemplate } from '@/types/requirement-form';

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
  /** 当前阶段 */
  currentPhase: PhaseType;
  /** 加载的模板信息 */
  loadedTemplate: LoadedTemplate | null;

  // ==================== 方法 ====================
  /** 设置需求描述 */
  setRequirement: (value: string) => void;
  /** 设置AI模型 */
  setSelectedModel: (model: UniaixModel) => void;
  /** 设置设计风格 */
  setSelectedStyle: (style: string | null) => void;
  /** 设置加载状态 */
  setLoading: (loading: boolean) => void;
  /** 设置成功动画显示状态 */
  setShowSuccess: (show: boolean) => void;
  /** 设置分析面板显示状态 */
  setShowAnalysis: (show: boolean) => void;
  /** 设置当前阶段 */
  setCurrentPhase: (phase: PhaseType) => void;
  /** 设置加载的模板 */
  setLoadedTemplate: (template: LoadedTemplate | null) => void;
  /** 清除模板 */
  clearTemplate: () => void;
  /** 重置所有状态 */
  resetAll: () => void;
}

/**
 * useFormState Hook
 * 集中管理表单状态，提供统一的状态管理接口
 */
export function useFormState(): UseFormStateReturn {
  // ==================== 表单状态 ====================
  const [requirement, setRequirement] = useState('');
  const [selectedModel, setSelectedModel] = useState<UniaixModel>(UNIAIX_MODELS.QWEN_MAX);
  const [selectedStyle, setSelectedStyle] = useState<string | null>(null);

  // ==================== UI状态 ====================
  const [loading, setLoading] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);
  const [showAnalysis, setShowAnalysis] = useState(false);

  // ==================== 流程阶段状态 ====================
  const [currentPhase, setCurrentPhase] = useState<PhaseType>('idle');

  // ==================== 模板状态 ====================
  const [loadedTemplate, setLoadedTemplate] = useState<LoadedTemplate | null>(null);

  /**
   * 清除已加载的模板
   * 重置为空白表单
   */
  const clearTemplate = useCallback(() => {
    setRequirement('');
    setLoadedTemplate(null);
  }, []);

  /**
   * 重置所有状态到初始值
   * 用于重新开始生成流程
   */
  const resetAll = useCallback(() => {
    setRequirement('');
    setSelectedModel(UNIAIX_MODELS.QWEN_MAX);
    setSelectedStyle(null);
    setLoading(false);
    setShowSuccess(false);
    setShowAnalysis(false);
    setCurrentPhase('idle');
    setLoadedTemplate(null);
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

    // 方法
    setRequirement,
    setSelectedModel,
    setSelectedStyle,
    setLoading,
    setShowSuccess,
    setShowAnalysis,
    setCurrentPhase,
    setLoadedTemplate,
    clearTemplate,
    resetAll,
  };
}
