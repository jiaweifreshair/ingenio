/**
 * 向导页面类型定义
 * 定义向导页面相关的所有类型、接口和枚举
 */

/**
 * Agent类型枚举
 */
export enum AgentType {
  PLAN = 'PlanAgent',
  EXECUTE = 'ExecuteAgent',
  VALIDATE = 'ValidateAgent',
}

/**
 * Agent状态枚举
 */
export enum AgentState {
  PENDING = 'pending',
  RUNNING = 'running',
  COMPLETED = 'completed',
  FAILED = 'failed',
  PAUSED = 'paused',
}

/**
 * Token使用情况
 */
export interface TokenUsage {
  /** 输入Token数量 */
  inputTokens: number;
  /** 输出Token数量 */
  outputTokens: number;
  /** 总Token数量 */
  totalTokens: number;
  /** 预估成本（美元） */
  estimatedCost: number;
  /** 兼容性属性 */
  total?: number;
}

/**
 * Agent性能指标
 */
export interface AgentMetrics {
  /** Token使用量 */
  tokenUsage: TokenUsage;
  /** API调用次数 */
  apiCalls: number;
  /** 平均响应时间（毫秒） */
  avgResponseTime: number;
  /** 成功率 */
  successRate: number;
}

/**
 * Agent执行状态
 */
export interface AgentExecutionStatus {
  /** Agent ID */
  id: string;
  /** Agent类型 */
  type: AgentType;
  /** Agent名称 */
  name: string;
  /** 执行状态 */
  status: AgentState;
  /** 进度百分比 (0-100) */
  progress: number;
  /** 当前任务描述 */
  currentTask?: string;
  /** 开始时间 */
  startTime?: Date;
  /** 结束时间 */
  endTime?: Date;
  /** 耗时（毫秒） */
  duration?: number;
  /** 消息内容 */
  message?: string;
  /** 错误信息 */
  error?: {
    code: string;
    message: string;
    stack?: string;
  };
  /** 性能指标 */
  metrics?: AgentMetrics;
  /** 输入参数 */
  inputParams?: Record<string, unknown>;
  /** 输出结果 */
  outputResult?: unknown;
}

/**
 * 生成配置
 */
export interface GenerationConfig {
  /** 需求描述 */
  requirement: string;
  /** AI模型 */
  model?: string;
  /** 质量阈值 */
  qualityThreshold?: number;
  /** 跳过验证 */
  skipValidation?: boolean;
  /** 生成预览 */
  generatePreview?: boolean;
}

/**
 * 向导页面状态
 */
export interface WizardState {
  /** 生成任务ID */
  taskId: string;
  /** 当前状态 */
  status: 'idle' | 'generating' | 'completed' | 'failed';
  /** 总体进度 */
  progress: number;
  /** Agent状态列表 */
  agents: AgentExecutionStatus[];
  /** 配置信息 */
  config: GenerationConfig;
  /** 错误信息 */
  error?: string;
  /** 生成结果 */
  result?: {
    appSpecId: string;
    qualityScore: number;
    modules: number;
  };
}

/**
 * 面板尺寸配置
 */
export interface PanelSize {
  /** 左侧面板宽度百分比 */
  leftWidth: number;
  /** 右侧面板宽度百分比 */
  rightWidth: number;
}

/**
 * 日志级别枚举
 */
export enum LogLevel {
  DEBUG = 'debug',
  INFO = 'info',
  WARN = 'warn',
  ERROR = 'error',
}

/**
 * 日志条目
 */
export interface LogEntry {
  /** 日志ID */
  id: string;
  /** 时间戳 */
  timestamp: Date;
  /** 日志级别 */
  level: LogLevel;
  /** Agent来源 */
  source: AgentType;
  /** 日志消息 */
  message: string;
  /** 附加数据 */
  data?: Record<string, unknown>;
}

/**
 * 性能指标
 */
export interface PerformanceMetric {
  /** 指标名称 */
  name: string;
  /** 当前值 */
  value: number;
  /** 单位 */
  unit: string;
  /** 状态 */
  status: 'normal' | 'warning' | 'critical';
}
