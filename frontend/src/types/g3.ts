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
 */
export type G3Role = 'PLAYER' | 'COACH' | 'EXECUTOR' | 'ARCHITECT';

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
  level: 'info' | 'warn' | 'error' | 'success';
}

/**
 * G3 产物（基础信息）
 */
export interface G3Artifact {
  /** 产物ID */
  id?: string;
  /** 代码内容 */
  code: string;
  /** 文件名 */
  filename: string;
  /** 语言类型 */
  language: 'typescript' | 'java' | 'python';
  /** 是否有效 */
  isValid: boolean;
}

/**
 * G3 产物详情（包含完整内容）
 */
export interface G3ArtifactDetail extends G3Artifact {
  /** 产物类型 */
  type: 'SOURCE' | 'TEST' | 'CONFIG' | 'DOC';
  /** 创建时间 */
  createdAt: string;
  /** 更新时间 */
  updatedAt: string;
  /** 版本号 */
  version: number;
  /** 关联的修复轮次 */
  round: number;
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
 * 提交 G3 任务请求
 */
export interface SubmitG3JobRequest {
  /** 需求描述 */
  requirement: string;
  /** 最大修复轮次（可选，默认 3） */
  maxRounds?: number;
  /** 是否启用详细日志（可选，默认 true） */
  verbose?: boolean;
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
