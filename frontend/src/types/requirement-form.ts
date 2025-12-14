/**
 * RequirementForm 相关类型定义
 *
 * @author Ingenio Team
 * @since V2.0
 */

import { UniaixModel } from '@/lib/api/uniaix';

/**
 * 生成流程阶段类型
 */
export type PhaseType =
  | 'idle'              // 空闲状态，等待用户输入
  | 'analyzing'         // 实时分析阶段（SSE连接）
  | 'style-selection'   // 风格选择阶段 (Intermediate state for auto-transition)
  | 'prototype-preview' // 原型预览阶段 (Unified Lite Flow)
  | 'generating'        // 完整生成阶段
  | 'navigating';       // 导航跳转阶段

/**
 * 加载的模板信息
 */
export interface LoadedTemplate {
  /** 模板ID */
  id: string;
  /** 模板名称 */
  name: string;
}

/**
 * 分析消息类型（SSE）
 */
export interface AnalysisMessage {
  /** 消息类型 */
  type: string;
  /** 消息内容 */
  content: string;
  /** 时间戳 */
  timestamp: number;
}

/**
 * 表单状态接口
 */
export interface FormState {
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
}

/**
 * 模板配置
 */
export interface TemplateConfig {
  id: string;
  title: string;
  description: string;
  icon: React.ReactNode;
  color: string;
}
