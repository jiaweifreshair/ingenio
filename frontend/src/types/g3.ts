/**
 * G3引擎类型定义
 * G3 = Generate-Check-Fix 自修复代码生成引擎
 *
 * 与后端 G3Controller API 对齐
 */

/**
 * Agent角色枚举
 * - PLAYER: 编码器Agent（生成代码）
 * - COACH: 修复教练（分析错误并修复）
 * - EXECUTOR: 执行器（沙箱编译验证）
 * - ARCHITECT: 架构师（生成契约）
 */
export type G3Role = 'PLAYER' | 'COACH' | 'EXECUTOR' | 'ARCHITECT';

/**
 * 日志级别
 */
export type G3LogLevel = 'info' | 'warn' | 'error' | 'success';

/**
 * 任务状态枚举
 */
export type G3JobStatus =
  | 'QUEUED'      // 排队中
  | 'PLANNING'    // 规划阶段（架构设计）
  | 'CODING'      // 编码阶段
  | 'TESTING'     // 测试阶段（沙箱验证）
  | 'COMPLETED'   // 完成
  | 'FAILED';     // 失败

/**
 * 产物类型枚举
 */
export type G3ArtifactType =
  | 'CONTRACT'    // OpenAPI契约
  | 'SCHEMA'      // 数据库Schema
  | 'ENTITY'      // 实体类
  | 'MAPPER'      // MyBatis Mapper
  | 'SERVICE'     // 服务层
  | 'CONTROLLER'  // 控制器
  | 'CONFIG'      // 配置文件
  | 'TEST'        // 测试文件
  | 'FRONTEND'    // 前端组件
  | 'OTHER';      // 其他

/**
 * 编程语言
 */
export type G3Language =
  | 'java'
  | 'typescript'
  | 'javascript'
  | 'sql'
  | 'yaml'
  | 'xml'
  | 'json'
  | 'properties';

/**
 * G3日志条目
 */
export interface G3LogEntry {
  /** 时间戳 */
  timestamp: string;
  /** Agent角色 */
  role: G3Role;
  /** 日志消息 */
  message: string;
  /** 日志级别 */
  level: G3LogLevel;
}

/**
 * G3产物（代码文件）
 */
export interface G3Artifact {
  /** 产物ID */
  id: string;
  /** 产物类型 */
  artifactType: G3ArtifactType;
  /** 文件路径 */
  filePath: string;
  /** 文件名 */
  fileName: string;
  /** 编程语言 */
  language: G3Language;
  /** 版本号 */
  version: number;
  /** 是否有编译错误 */
  hasErrors: boolean;
  /** 生成者 */
  generatedBy: string;
  /** 生成轮次 */
  generationRound: number;
  /** 创建时间 */
  createdAt: string | null;
}

/**
 * G3产物详情（包含代码内容）
 */
export interface G3ArtifactDetail extends G3Artifact {
  /** 代码内容 */
  content: string;
  /** 编译器输出 */
  compilerOutput: string;
}

/**
 * G3任务详细信息（与后端JobStatusResponse对齐）
 */
export interface G3JobInfo {
  /** 任务ID */
  id: string;
  /** 任务状态 */
  status: G3JobStatus;
  /** 当前修复轮次 */
  currentRound: number;
  /** 最大修复轮次 */
  maxRounds: number;
  /** 契约是否已锁定 */
  contractLocked: boolean;
  /** 沙箱ID */
  sandboxId: string | null;
  /** 沙箱URL */
  sandboxUrl: string | null;
  /** 最近错误 */
  lastError: string | null;
  /** 开始时间 */
  startedAt: string | null;
  /** 完成时间 */
  completedAt: string | null;
}

/**
 * G3契约信息
 */
export interface G3Contract {
  /** 契约是否已锁定 */
  contractLocked: boolean;
  /** OpenAPI契约YAML */
  contractYaml: string;
  /** 数据库Schema SQL */
  dbSchemaSql: string;
  /** 锁定时间 */
  lockedAt: string | null;
}

/**
 * 提交任务请求
 */
export interface SubmitG3JobRequest {
  /** 需求描述 */
  requirement: string;
  /** 用户ID（可选） */
  userId?: string;
  /** 租户ID（可选） */
  tenantId?: string;
}

/**
 * 提交任务响应
 */
export interface SubmitG3JobResponse {
  /** 是否成功 */
  success: boolean;
  /** 任务ID */
  jobId: string | null;
  /** 消息 */
  message: string;
}

/**
 * SSE事件类型
 */
export type G3SSEEventType = 'log' | 'heartbeat' | 'error' | 'complete';

/**
 * SSE事件
 */
export interface G3SSEEvent {
  /** 事件类型 */
  type: G3SSEEventType;
  /** 日志数据 */
  data?: G3LogEntry;
  /** 错误信息 */
  error?: string;
}

/**
 * G3引擎健康状态
 */
export interface G3HealthStatus {
  /** 服务状态 */
  status: 'UP' | 'DOWN';
  /** 服务名称 */
  service: string;
  /** 版本 */
  version: string;
}

/**
 * 获取状态对应的颜色
 */
export function getStatusColor(status: G3JobStatus): string {
  switch (status) {
    case 'QUEUED':
      return 'text-gray-500';
    case 'PLANNING':
      return 'text-blue-500';
    case 'CODING':
      return 'text-purple-500';
    case 'TESTING':
      return 'text-yellow-500';
    case 'COMPLETED':
      return 'text-green-500';
    case 'FAILED':
      return 'text-red-500';
    default:
      return 'text-gray-500';
  }
}

/**
 * 获取角色对应的颜色
 */
export function getRoleColor(role: G3Role): string {
  switch (role) {
    case 'ARCHITECT':
      return 'text-indigo-600';
    case 'PLAYER':
      return 'text-blue-600';
    case 'COACH':
      return 'text-orange-600';
    case 'EXECUTOR':
      return 'text-green-600';
    default:
      return 'text-gray-600';
  }
}

/**
 * 获取角色对应的图标
 */
export function getRoleIcon(role: G3Role): string {
  switch (role) {
    case 'ARCHITECT':
      return '🏗️';
    case 'PLAYER':
      return '💻';
    case 'COACH':
      return '🔧';
    case 'EXECUTOR':
      return '⚙️';
    default:
      return '📝';
  }
}

/**
 * 获取日志级别对应的颜色
 */
export function getLevelColor(level: G3LogLevel): string {
  switch (level) {
    case 'info':
      return 'text-gray-600';
    case 'warn':
      return 'text-yellow-600';
    case 'error':
      return 'text-red-600';
    case 'success':
      return 'text-green-600';
    default:
      return 'text-gray-600';
  }
}
