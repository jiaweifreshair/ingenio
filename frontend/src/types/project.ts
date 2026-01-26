/**
 * 项目相关类型定义
 * Dashboard页面使用
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

/**
 * 项目状态枚举
 * - DRAFT: 草稿（初始状态，需求已创建但未生成代码）
 * - GENERATING: 生成中（正在执行代码生成）
 * - COMPLETED: 生成完成（代码生成成功，可预览）
 * - ARCHIVED: 已归档（用户手动归档）
 */
export enum ProjectStatus {
  DRAFT = 'draft',
  GENERATING = 'generating',
  COMPLETED = 'completed',
  ARCHIVED = 'archived',
}

/**
 * 项目可见性枚举
 */
export enum ProjectVisibility {
  PRIVATE = 'private',
  PUBLIC = 'public',
  UNLISTED = 'unlisted',
}

/**
 * 年龄分组枚举
 */
export enum AgeGroup {
  ELEMENTARY = 'elementary',
  MIDDLE_SCHOOL = 'middle_school',
  HIGH_SCHOOL = 'high_school',
  UNIVERSITY = 'university',
}

/**
 * 项目集成配置类型
 */
export interface ProjectIntegrations {
  githubEnabled?: boolean;
  githubRepo?: string;
  customDomain?: string;
  webhookUrl?: string;
}

/**
 * 项目元数据类型
 */
export interface ProjectMetadata {
  integrations?: ProjectIntegrations;
  [key: string]: unknown;
}

/**
 * 项目数据类型
 */
export interface Project {
  id: string;
  tenantId: string;
  userId: string;
  name: string;
  description: string;
  coverImageUrl?: string;
  appSpecId?: string;
  status: ProjectStatus;
  visibility: ProjectVisibility;
  viewCount: number;
  likeCount: number;
  forkCount: number;
  commentCount: number;
  tags?: string[];
  ageGroup?: AgeGroup;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
  metadata?: ProjectMetadata;
  owner?: {
    id: string;
    username: string;
    email: string;
    avatarUrl?: string;
  };
  isLiked?: boolean;
  isFavorited?: boolean;
}

/**
 * 项目统计数据类型
 */
export interface ProjectStats {
  totalProjects: number;
  monthlyNewProjects: number;
  generatingTasks: number;
  publishedProjects: number;
  draftProjects: number;
  archivedProjects: number;
}

/**
 * 分页响应类型
 */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

/**
 * 统一响应类型
 */
export interface ApiResponse<T> {
  code: number;
  success: boolean;
  message: string;
  data: T;
  timestamp: number;
}

/**
 * 项目筛选参数
 */
export interface ProjectFilters {
  status?: ProjectStatus | '';
  keyword?: string;
  current?: number;
  size?: number;
}

/**
 * 项目排序选项
 */
export enum SortBy {
  UPDATED_AT_DESC = 'updatedAt-desc',
  CREATED_AT_DESC = 'createdAt-desc',
  NAME_ASC = 'name-asc',
  NAME_DESC = 'name-desc',
}

/**
 * 生成任务状态枚举
 */
export enum GenerationTaskStatus {
  PENDING = 'pending',
  PLANNING = 'planning',
  EXECUTING = 'executing',
  VALIDATING = 'validating',
  GENERATING = 'generating',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled',
}

/**
 * Agent类型枚举
 */
export enum AgentType {
  PLAN = 'plan',
  EXECUTE = 'execute',
  VALIDATE = 'validate',
  GENERATE = 'generate',
}

/**
 * 生成任务实体
 */
export interface GenerationTask {
  id: string;
  tenantId: string;
  userId: string;
  taskName: string;
  userRequirement: string;
  status: GenerationTaskStatus;
  currentAgent: AgentType;
  progress: number;
  agentsInfo?: Record<string, unknown>;
  planResult?: Record<string, unknown>;
  appSpecContent?: Record<string, unknown>;
  validateResult?: Record<string, unknown>;
  appSpecId?: string;
  qualityScore?: number;
  downloadUrl?: string;
  previewUrl?: string;
  tokenUsage?: Record<string, unknown>;
  errorMessage?: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
  metadata?: Record<string, unknown>;
}

/**
 * 执行历史项
 */
export interface ExecutionHistoryItem {
  task: GenerationTask;
  duration?: number;
  statusLabel: string;
  progressLabel: string;
}
