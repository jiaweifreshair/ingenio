/**
 * 版本控制类型定义
 * 时光机TimeMachine版本快照系统
 */

/**
 * 版本类型枚举
 * 对应后端VersionType枚举
 */
export enum VersionType {
  /** 规划阶段快照 */
  PLAN = 'PLAN',
  /** Schema生成快照 */
  SCHEMA = 'SCHEMA',
  /** 代码生成快照 */
  CODE = 'CODE',
  /** 验证失败快照 */
  VALIDATION_FAILED = 'VALIDATION_FAILED',
  /** 验证成功快照 */
  VALIDATION_SUCCESS = 'VALIDATION_SUCCESS',
  /** 修复快照 */
  FIX = 'FIX',
  /** 回滚快照 */
  ROLLBACK = 'ROLLBACK',
  /** 最终发布版本 */
  FINAL = 'FINAL',
}

/**
 * 版本类型显示信息
 */
export const VERSION_TYPE_INFO: Record<VersionType, {
  displayName: string;
  description: string;
  color: string;
  bgColor: string;
  borderColor: string;
}> = {
  [VersionType.PLAN]: {
    displayName: '规划',
    description: 'PlanAgent完成需求分析',
    color: 'text-blue-600',
    bgColor: 'bg-blue-50',
    borderColor: 'border-blue-300',
  },
  [VersionType.SCHEMA]: {
    displayName: '数据库设计',
    description: 'DatabaseSchemaGenerator生成DDL',
    color: 'text-green-600',
    bgColor: 'bg-green-50',
    borderColor: 'border-green-300',
  },
  [VersionType.CODE]: {
    displayName: '代码生成',
    description: 'ExecuteAgent生成Kotlin/Compose代码',
    color: 'text-purple-600',
    bgColor: 'bg-purple-50',
    borderColor: 'border-purple-300',
  },
  [VersionType.VALIDATION_FAILED]: {
    displayName: '验证失败',
    description: '编译/测试/性能验证失败',
    color: 'text-red-600',
    bgColor: 'bg-red-50',
    borderColor: 'border-red-300',
  },
  [VersionType.VALIDATION_SUCCESS]: {
    displayName: '验证成功',
    description: '所有测试通过，可发布',
    color: 'text-emerald-600',
    bgColor: 'bg-emerald-50',
    borderColor: 'border-emerald-300',
  },
  [VersionType.FIX]: {
    displayName: 'Bug修复',
    description: '修复验证失败的问题',
    color: 'text-orange-600',
    bgColor: 'bg-orange-50',
    borderColor: 'border-orange-300',
  },
  [VersionType.ROLLBACK]: {
    displayName: '版本回滚',
    description: '回滚到历史版本',
    color: 'text-gray-600',
    bgColor: 'bg-gray-50',
    borderColor: 'border-gray-300',
  },
  [VersionType.FINAL]: {
    displayName: '最终发布',
    description: '用户确认发布版本',
    color: 'text-yellow-600',
    bgColor: 'bg-yellow-50',
    borderColor: 'border-yellow-300',
  },
};

/**
 * 版本时间线项
 * 对应后端VersionTimelineItem
 */
export interface VersionTimelineItem {
  /** 版本ID */
  versionId: string;
  /** 版本类型 */
  versionType: VersionType;
  /** 版本号（如v1, v2） */
  versionNumber: string;
  /** 创建时间（ISO 8601格式） */
  createdAt: string;
  /** 快照摘要 */
  summary: string;
  /** 是否可回滚 */
  canRollback: boolean;
  /** 创建人（可选） */
  createdBy?: string;
}

/**
 * 版本详情
 * 对应后端GenerationVersionEntity
 */
export interface VersionDetail {
  /** 版本ID */
  id: string;
  /** 任务ID */
  taskId: string;
  /** 租户ID */
  tenantId: string;
  /** 版本类型 */
  versionType: string;
  /** 版本号 */
  versionNumber: number;
  /** 快照数据（JSON） */
  snapshot: Record<string, unknown>;
  /** 描述 */
  description?: string;
  /** 创建时间 */
  createdAt: string;
  /** 更新时间 */
  updatedAt: string;
}

/**
 * 版本差异
 * 对应后端VersionDiff
 */
export interface VersionDiff {
  /** 版本1信息 */
  version1: {
    versionId: string;
    versionType: VersionType;
    versionNumber: string;
    createdAt: string;
  };
  /** 版本2信息 */
  version2: {
    versionId: string;
    versionType: VersionType;
    versionNumber: string;
    createdAt: string;
  };
  /** 差异列表 */
  changes: VersionChange[];
  /** 统计信息 */
  stats: {
    additions: number;
    deletions: number;
    modifications: number;
  };
}

/**
 * 版本变更项
 */
export interface VersionChange {
  /** 变更类型 */
  type: 'addition' | 'deletion' | 'modification';
  /** 变更路径（如 "entities.User.name"） */
  path: string;
  /** 旧值 */
  oldValue?: unknown;
  /** 新值 */
  newValue?: unknown;
  /** 描述 */
  description: string;
}
