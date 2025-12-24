/**
 * Agent输入输出类型定义
 * 定义PlanAgent、ExecuteAgent、ValidateAgent的输入输出格式
 */

import { AppSpec, ValidationResult } from './appspec.types';

/**
 * ======================
 * PlanAgent 相关类型
 * ======================
 */

/**
 * PlanAgent输入
 */
export interface PlanAgentInput {
  requirement: string; // 用户需求描述
  context: PlanContext;
}

export interface PlanContext {
  tenantId: string;
  userId: string;
  existingModels?: string[]; // 已有数据模型
  projectType?: ProjectType;
  constraints?: string[]; // 业务约束
  preferences?: UserPreferences; // 用户偏好
}

export type ProjectType =
  | 'form'          // 表单应用
  | 'dashboard'     // 仪表盘
  | 'workflow'      // 工作流
  | 'marketplace'   // 市场/商店
  | 'social'        // 社交应用
  | 'custom';       // 自定义

export interface UserPreferences {
  uiStyle?: 'minimal' | 'modern' | 'classic';
  complexity?: 'simple' | 'moderate' | 'complex';
  mobileFirst?: boolean;
}

/**
 * PlanAgent输出
 */
export interface PlanAgentOutput {
  modules: Module[]; // 功能模块
  flows: FlowPlan[]; // 流程规划
  constraints: ConstraintPlan[]; // 约束规划
  dependencies: Dependency[]; // 依赖关系
  estimatedComplexity: ComplexityLevel;
  recommendations?: string[]; // 实现建议
}

export interface Module {
  id: string;
  name: string;
  description: string;
  type: ModuleType;
  dependencies: string[]; // 依赖的其他模块ID
  priority: 'high' | 'medium' | 'low';
}

export type ModuleType = 'page' | 'component' | 'service' | 'data' | 'auth' | 'integration';

export interface FlowPlan {
  id: string;
  name: string;
  description: string;
  triggerType: 'user' | 'system' | 'scheduled';
  steps: FlowStep[];
}

export interface FlowStep {
  id: string;
  action: string;
  target: string;
  condition?: string;
}

export interface ConstraintPlan {
  type: string;
  description: string;
  affectedModules: string[];
}

export interface Dependency {
  from: string; // 源模块ID
  to: string; // 目标模块ID
  type: 'requires' | 'extends' | 'uses';
  optional: boolean;
}

export type ComplexityLevel = 'low' | 'medium' | 'high';

/**
 * ======================
 * ExecuteAgent 相关类型
 * ======================
 */

/**
 * ExecuteAgent输入
 */
export interface ExecuteAgentInput {
  plan: PlanAgentOutput; // PlanAgent的输出
  modelProvider: ModelProvider;
  context: ExecuteContext;
}

export interface ExecuteContext {
  tenantId: string;
  templateId?: string; // 可选的模板ID
  existingSpec?: Partial<AppSpec>; // 已有的AppSpec（用于增量生成）
  generationMode: 'full' | 'incremental';
}

export type ModelProvider = 'openai' | 'anthropic' | 'local' | 'custom';

/**
 * ExecuteAgent输出
 */
export interface ExecuteAgentOutput {
  appSpec: AppSpec; // 生成的AppSpec
  generationMetadata: GenerationMetadata;
}

export interface GenerationMetadata {
  modelUsed: string;
  tokensUsed: number;
  latencyMs: number;
  confidence: number; // 生成置信度 0-1
  warnings?: string[]; // 生成过程中的警告
}

/**
 * ======================
 * ValidateAgent 相关类型
 * ======================
 */

/**
 * ValidateAgent输入
 */
export interface ValidateAgentInput {
  appSpec: AppSpec;
  validationRules?: ValidationRuleConfig[]; // 可选的额外校验规则
  strictMode?: boolean; // 严格模式（更严格的校验）
}

export interface ValidationRuleConfig {
  type: string;
  enabled: boolean;
  severity: 'error' | 'warning';
  config?: Record<string, any>;
}

/**
 * ValidateAgent输出
 */
export interface ValidateAgentOutput extends ValidationResult {
  // 继承ValidationResult的所有字段
  timestamp: string;
  validatorVersion: string;
}

/**
 * ======================
 * 通用Agent响应类型
 * ======================
 */

/**
 * Agent执行结果包装器
 */
export interface AgentResponse<T> {
  success: boolean;
  data?: T;
  error?: AgentError;
  metadata: ResponseMetadata;
}

export interface AgentError {
  code: string;
  message: string;
  details?: any;
  recoverable: boolean; // 是否可恢复
}

export interface ResponseMetadata {
  requestId: string;
  agentType: 'plan' | 'execute' | 'validate';
  timestamp: string;
  latencyMs: number;
}

/**
 * ======================
 * 模型提供者抽象接口
 * ======================
 */

/**
 * 模型提供者配置
 */
export interface ModelConfig {
  provider: ModelProvider;
  model: string;
  apiKey?: string;
  baseUrl?: string;
  temperature?: number;
  maxTokens?: number;
  timeout?: number;
}

/**
 * 模型聊天消息
 */
export interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

/**
 * 模型响应
 */
export interface ModelResponse {
  content: string;
  usage: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
  finishReason: 'stop' | 'length' | 'error';
}

/**
 * ======================
 * Agent执行日志类型
 * ======================
 */

/**
 * Agent执行日志
 */
export interface AgentExecutionLog {
  id: string;
  requestId: string;
  tenantId: string;
  agentType: 'plan' | 'execute' | 'validate';
  status: ExecutionStatus;
  inputData?: any; // 已脱敏
  outputData?: any;
  errorMessage?: string;
  latencyMs: number;
  modelProvider?: string;
  modelName?: string;
  tokenUsage?: {
    prompt: number;
    completion: number;
    total: number;
  };
  createdAt: string;
}

export type ExecutionStatus = 'success' | 'failed' | 'timeout' | 'cancelled';

/**
 * ======================
 * 批量生成类型
 * ======================
 */

/**
 * 批量生成请求
 */
export interface BatchGenerateRequest {
  requirements: string[];
  context: PlanContext;
  parallel?: boolean; // 是否并行执行
}

/**
 * 批量生成响应
 */
export interface BatchGenerateResponse {
  results: BatchGenerateResult[];
  summary: BatchSummary;
}

export interface BatchGenerateResult {
  index: number;
  success: boolean;
  appSpec?: AppSpec;
  error?: string;
}

export interface BatchSummary {
  total: number;
  succeeded: number;
  failed: number;
  totalLatencyMs: number;
  averageLatencyMs: number;
}
