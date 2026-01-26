export interface Version {
  id: string;
  version: number;
  parentVersionId?: string;
  status: string;
  qualityScore?: number;
  intentType?: string;
  selectedStyle?: string;
  designConfirmed?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface VersionCompareResult {
  sourceVersion: Version;
  targetVersion: Version;
  differences: {
    versionDiff: number;
    statusChanged: boolean;
    styleChanged: boolean;
    intentChanged: boolean;
  };
}

export interface CreateVersionRequest {
  parentVersionId: string;
  userId: string;
  tenantId: string;
}

export type VersionType = 'INITIAL' | 'REQUIREMENT_UPDATE' | 'INTENT_UPDATE' | 'TEMPLATE_UPDATE' | 'STYLE_UPDATE' | 'DESIGN_CONFIRM' | 'CODE_GENERATION' | 'ROLLBACK';

export interface VersionTimelineItem {
  versionId: string;
  versionNumber: number;
  versionType: VersionType;
  createdAt: string;
  canRollback?: boolean;
  summary?: string;
  createdBy?: string;
}

export interface VersionDetail {
  id: string;
  versionId: string;
  versionNumber: number;
  versionType: VersionType;
  status: string;
  intentType?: string;
  selectedStyle?: string;
  designConfirmed?: boolean;
  description?: string;
  snapshot?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface VersionChange {
  field: string;
  oldValue: unknown;
  newValue: unknown;
  label?: string;
  type?: 'addition' | 'deletion' | 'modification';
  path?: string;
  description?: string;
}

export interface VersionDiff {
  sourceVersion: VersionDetail;
  targetVersion: VersionDetail;
  version1?: VersionDetail;
  version2?: VersionDetail;
  changes: VersionChange[];
  stats?: {
    additions: number;
    modifications: number;
    deletions: number;
  };
}

export const VERSION_TYPE_INFO: Record<VersionType, { label: string; color: string; bgColor: string; displayName: string; description: string }> = {
  INITIAL: { label: '初始版本', color: 'blue', bgColor: 'bg-blue-100', displayName: '初始版本', description: '项目初始化版本' },
  REQUIREMENT_UPDATE: { label: '需求更新', color: 'purple', bgColor: 'bg-purple-100', displayName: '需求更新', description: '需求分析更新' },
  INTENT_UPDATE: { label: '意图更新', color: 'cyan', bgColor: 'bg-cyan-100', displayName: '意图更新', description: '用户意图识别更新' },
  TEMPLATE_UPDATE: { label: '模板更新', color: 'green', bgColor: 'bg-green-100', displayName: '模板更新', description: '应用模板选择更新' },
  STYLE_UPDATE: { label: '风格更新', color: 'orange', bgColor: 'bg-orange-100', displayName: '风格更新', description: '设计风格更新' },
  DESIGN_CONFIRM: { label: '设计确认', color: 'teal', bgColor: 'bg-teal-100', displayName: '设计确认', description: '设计方案确认' },
  CODE_GENERATION: { label: '代码生成', color: 'indigo', bgColor: 'bg-indigo-100', displayName: '代码生成', description: '代码生成完成' },
  ROLLBACK: { label: '版本回滚', color: 'red', bgColor: 'bg-red-100', displayName: '版本回滚', description: '回滚到历史版本' },
};
