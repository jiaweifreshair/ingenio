import { get, post, type APIResponse } from "./client";

import type { UniaixModel } from "./uniaix";

/**
 * 生成请求参数
 */
export interface GenerateRequest {
  /** 需求描述 */
  requirement: string;
  /** 租户ID */
  tenantId: string;
  /** 用户ID */
  userId: string;
  /** AI模型（可选，默认使用Qwen Max） */
  model?: UniaixModel;
  /** 项目类型（可选） */
  projectType?: string;
  /** 已有数据模型（可选） */
  existingModels?: string[];
  /** 业务约束（可选） */
  constraints?: string[];
}

/**
 * 生成响应数据（匹配后端GenerateFullResponse）
 */
export interface GenerateResponse {
  /** AppSpec ID */
  appSpecId: string;
  /** 项目ID */
  projectId?: string;
  /** 规划结果 */
  planResult?: {
    modules: Array<{
      name: string;
      description: string;
      priority: string;
      complexity: number;
      dependencies: string[];
      dataModels: string[];
      pages: string[];
    }>;
    complexityScore: number;
    reasoning: string;
    suggestedTechStack: string[];
    estimatedHours: number;
    recommendations: string;
  };
  /** 验证结果 */
  validateResult?: {
    isValid: boolean;
    qualityScore: number;
    issues: Array<{
      severity: string;
      type: string;
      message: string;
      location?: string;
    }>;
    suggestions: string[];
  };
  /** 是否验证通过 */
  isValid: boolean;
  /** 质量评分 */
  qualityScore: number;
  /** 代码下载URL */
  codeDownloadUrl?: string;
  /** 代码生成摘要 */
  codeSummary?: {
    /** 总文件数 */
    totalFiles: number;
    /** 数据库Schema文件数 */
    databaseSchemaFiles: number;
    /** 数据模型文件数 */
    dataModelFiles: number;
    /** Repository文件数 */
    repositoryFiles: number;
    /** ViewModel文件数 */
    viewModelFiles: number;
    /** UI界面文件数 */
    uiScreenFiles: number;
    /** AI集成文件数 */
    aiIntegrationFiles: number;
    /** 配置文件数 */
    configFiles: number;
    /** 文档文件数 */
    documentFiles: number;
    /** 总文件大小（字节） */
    totalSize: number;
    /** ZIP文件名 */
    zipFileName: string;
  };
  /** 生成的文件清单 */
  generatedFileList?: string[];
  /** 预览URL */
  previewUrl?: string;
  /** 生成状态 */
  status: string;
  /** 错误消息 */
  errorMessage?: string;
  /** 耗时（毫秒） */
  durationMs: number;
  /** 生成时间 */
  generatedAt: string;
  /** Token使用统计 */
  tokenUsage?: {
    planTokens: number;
    executeTokens: number;
    validateTokens: number;
    totalTokens: number;
    estimatedCost: number;
  };
}

/**
 * 任务状态响应接口（匹配后端TaskStatusResponse）
 */
export interface TaskStatusResponse {
  /** 任务ID */
  taskId: string;
  /** 任务名称 */
  taskName: string;
  /** 用户原始需求 */
  userRequirement: string;
  /** 任务状态 */
  status: string;
  /** 状态描述 */
  statusDescription: string;
  /** 当前执行的Agent */
  currentAgent: string;
  /** 任务进度（0-100） */
  progress: number;
  /** Agent状态列表 */
  agents: Array<{
    /** Agent类型 */
    agentType: string;
    /** Agent名称 */
    agentName: string;
    /** Agent状态 */
    status: string;
    /** 状态描述 */
    statusDescription: string;
    /** 开始时间 */
    startedAt: string;
    /** 完成时间 */
    completedAt: string;
    /** 执行时长（毫秒） */
    durationMs: number;
    /** 进度（0-100） */
    progress: number;
    /** 结果摘要 */
    resultSummary: string;
    /** 错误信息 */
    errorMessage: string;
    /** Agent元数据 */
    metadata: Record<string, unknown>;
  }>;
  /** 任务开始时间 */
  startedAt: string;
  /** 任务完成时间 */
  completedAt: string;
  /** 预估剩余时间（秒） */
  estimatedRemainingSeconds: number;
  /** AppSpec ID（如果已完成） */
  appSpecId: string;
  /** 质量评分（0-100） */
  qualityScore: number;
  /** 代码下载链接 */
  downloadUrl: string;
  /** 预览链接 */
  previewUrl: string;
  /** 代码生成摘要 */
  codeSummary?: {
    /** 总文件数 */
    totalFiles: number;
    /** 数据库Schema文件数 */
    databaseSchemaFiles: number;
    /** 数据模型文件数 */
    dataModelFiles: number;
    /** Repository文件数 */
    repositoryFiles: number;
    /** ViewModel文件数 */
    viewModelFiles: number;
    /** UI界面文件数 */
    uiScreenFiles: number;
    /** AI集成文件数 */
    aiIntegrationFiles: number;
    /** 配置文件数 */
    configFiles: number;
    /** 文档文件数 */
    documentFiles: number;
    /** 总文件大小（字节） */
    totalSize: number;
    /** ZIP文件名 */
    zipFileName: string;
  };
  /** 生成的文件清单 */
  generatedFileList?: string[];
  /** Token使用统计 */
  tokenUsage: {
    planTokens: number;
    executeTokens: number;
    validateTokens: number;
    totalTokens: number;
    estimatedCost: number;
  };
  /** 错误信息（如果失败） */
  errorMessage: string;
  /** 创建时间 */
  createdAt: string;
  /** 更新时间 */
  updatedAt: string;
}

/**
 * 异步生成请求参数
 */
export interface AsyncGenerateRequest {
  /** 需求描述 */
  userRequirement: string;
  /** AI模型（可选，默认使用gemini-3-pro-preview） */
  model?: string;
  /** 跳过验证 */
  skipValidation?: boolean;
  /** 质量阈值 */
  qualityThreshold?: number;
  /** 生成预览 */
  generatePreview?: boolean;
}

/**
 * 异步生成响应
 */
export interface AsyncGenerateResponse {
  /** 任务ID */
  taskId: string;
}

/**
 * 任务列表响应
 */
export interface TasksResponse {
  /** 任务列表 */
  tasks: Array<{
    id: string;
    taskName: string;
    status: string;
    progress: number;
    createdAt: string;
    updatedAt: string;
    qualityScore?: number;
    errorMessage?: string;
  }>;
  /** 总数 */
  total: number;
  /** 当前页码 */
  pageNum: number;
  /** 页大小 */
  pageSize: number;
  /** 总页数 */
  pages: number;
}

/**
 * 生成AppSpec（同步接口）
 *
 * @param request - 生成请求参数
 * @returns 生成结果
 */
export async function generateAppSpec(
  request: GenerateRequest
): Promise<APIResponse<GenerateResponse>> {
  // 使用完整生成流程接口，包含plan、execute、validate全流程
  // 修正：移除/api前缀，apiClient会自动处理
  return post<GenerateResponse>("/v1/generate/full", {
    userRequirement: request.requirement,
    model: request.model, // 传递选中的AI模型
    projectType: request.projectType,
    constraints: request.constraints,
    skipValidation: false,
    qualityThreshold: 70,
    generatePreview: false,
  });
}

/**
 * 创建异步生成任务
 *
 * @param request - 异步生成请求参数
 * @returns 任务ID
 */
export async function createAsyncGenerationTask(
  request: AsyncGenerateRequest
): Promise<APIResponse<AsyncGenerateResponse>> {
  return post<AsyncGenerateResponse>("/v1/generate/async", request);
}

/**
 * 查询任务状态
 *
 * @param taskId - 任务ID
 * @returns 任务状态
 */
export async function getTaskStatus(
  taskId: string
): Promise<APIResponse<TaskStatusResponse>> {
  return get<TaskStatusResponse>(`/v1/generate/status/${taskId}`);
}

/**
 * 取消任务
 *
 * @param taskId - 任务ID
 * @returns 取消结果
 */
export async function cancelTask(
  taskId: string
): Promise<APIResponse<boolean>> {
  return post<boolean>(`/v1/generate/cancel/${taskId}`, {});
}

/**
 * 获取用户任务列表
 *
 * @param pageNum - 页码，默认1
 * @param pageSize - 页大小，默认10
 * @returns 任务列表
 */
export async function getUserTasks(
  pageNum: number = 1,
  pageSize: number = 10
): Promise<APIResponse<TasksResponse>> {
  return get<TasksResponse>(`/v1/generate/tasks?pageNum=${pageNum}&pageSize=${pageSize}`);
}
