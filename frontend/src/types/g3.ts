/**
 * G3 引擎类型定义
 *
 * G3 = Game (博弈) + Generator (生成) + Guard (守护)
 *
 * @module types/g3
 * @author Ingenio Team
 * @since Phase 1 - G3 Engine MVP
 */

/**
 * G3 角色类型
 * - PLAYER: 蓝方，负责代码生成
 * - COACH: 红方，负责代码修复
 * - EXECUTOR: 裁判，负责编译验证
 * - ARCHITECT: 架构师，负责契约设计
 * - SYSTEM: 系统消息（心跳、连接状态等）
 */
export type G3Role = 'PLAYER' | 'COACH' | 'EXECUTOR' | 'ARCHITECT' | 'SYSTEM';

/**
 * G3 任务状态
 * 与后端 G3JobEntity.status 保持一致
 */
export type G3JobStatus =
  | 'QUEUED'      // 排队中
  | 'PLANNING'    // 架构设计中
  | 'CODING'      // 代码生成中
  | 'TESTING'     // 编译验证中
  | 'COMPLETED'   // 已完成
  | 'FAILED'      // 失败
  | 'CANCELLED';  // 已取消

/**
 * G3 日志条目
 */
export interface G3LogEntry {
  /** 时间戳 (ISO 8601 格式) */
  timestamp: string;
  /** 角色 */
  role: G3Role;
  /** 日志消息 */
  message: string;
  /** 日志级别 */
  level: 'info' | 'warn' | 'error' | 'success' | 'heartbeat';
}

/**
 * G3 产物（基础信息）
 */
export interface G3ArtifactSummary {
  /** 产物ID */
  id: string;
  /** 文件路径（相对路径） */
  filePath: string;
  /** 生成者 */
  generatedBy: string;
  /** 生成轮次 */
  round: number;
  /** 是否标记为错误 */
  hasErrors: boolean;
  /** 创建时间（ISO 字符串，可为空） */
  createdAt: string | null;
}

/**
 * G3 产物内容（包含完整文件内容）
 */
export interface G3ArtifactContent extends G3ArtifactSummary {
  /** 文件名 */
  fileName: string;
  /** 编译器输出（可为空） */
  compilerOutput: string | null;
  /** 文件内容 */
  content: string;
}

/**
 * G3 契约（API + 数据库 Schema）
 */
export interface G3Contract {
  /** 契约ID */
  id: string;
  /** OpenAPI YAML 内容 */
  openApiYaml: string;
  /** 数据库 Schema SQL */
  dbSchemaSql: string;
  /** 是否已锁定 */
  locked: boolean;
  /** 创建时间 */
  createdAt: string;
  /** 锁定时间 */
  lockedAt?: string;
}

/**
 * G3 任务详情（完整实体）
 */
export interface G3Job {
  id: string;
  requirement: string;
  status: G3JobStatus;
  currentRound: number;
  maxRounds: number;
  contractLocked: boolean;
  logs: G3LogEntry[];
  createdAt: string;
  updatedAt: string;
  completedAt?: string;
  
  // Blueprint 相关
  blueprintSpec?: Record<string, unknown>;
  matchedTemplateId?: string;
  blueprintModeEnabled?: boolean;
  templateContext?: string;
}

/**
 * 提交 G3 任务请求
 */
export interface SubmitG3JobRequest {
  /** 需求描述 */
  requirement: string;

  /**
   * AppSpec ID（可选）
   *
   * 说明：
   * - 传入后端会自动从 AppSpec 加载 tenantId/userId/blueprintSpec 等上下文
   * - 同时可读取 AppSpec.specContent.userRequirement 作为“最新需求”兜底
   */
  appSpecId?: string;

  /**
   * 行业模板ID（可选）
   * 说明：用于后端补齐 Blueprint（快速试跑/模板化生成）
   */
  templateId?: string;

  /** 最大修复轮次（可选，默认 3） */
  maxRounds?: number;
  /** 是否启用详细日志（可选，默认 true） */
  verbose?: boolean;
  
  // 可选：直接关联模板（如果是从模板创建）
  matchedTemplateId?: string;
  blueprintSpec?: Record<string, unknown>;
}

/**
 * 提交 G3 任务响应
 */
export interface SubmitG3JobResponse {
  /** 任务ID */
  jobId: string;
  /** 任务状态 */
  status: G3JobStatus;
  /** 创建时间 */
  createdAt: string;
}

/**
 * G3 健康检查状态
 */
export interface G3HealthStatus {
  /** 服务状态 */
  status: 'UP' | 'DOWN' | 'DEGRADED';
  /** 版本号 */
  version: string;
  /** 活跃任务数 */
  activeJobs: number;
  /** 沙箱可用数 */
  availableSandboxes: number;
  /** 检查时间 */
  timestamp: string;
}
