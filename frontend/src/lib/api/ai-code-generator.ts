/**
 * AI代码生成API客户端
 * 与后端AICodeGenerator服务集成
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import { post, get, type APIResponse } from './client';
import { getApiBaseUrl } from './base-url';
import { generateTraceId } from './trace-id';
import { AICapabilityType } from '@/types/ai-capability';

/**
 * 生成AI代码请求参数
 */
export interface GenerateAICodeRequest {
  /** 选中的AI能力类型列表 */
  capabilities: AICapabilityType[];
  /** 包名 */
  packageName: string;
  /** 应用名称 */
  appName: string;
  /** AI复杂度 */
  complexity: 'SIMPLE' | 'MEDIUM' | 'COMPLEX';
  /** 用户需求描述（可选） */
  userRequirement?: string;
}

/**
 * 生成AI代码响应
 */
export interface GenerateAICodeResponse {
  /** 生成的代码文件映射（路径 -> 内容） */
  generatedFiles: Record<string, string>;
  /** 生成摘要 */
  summary: {
    totalFiles: number;
    aiServiceFiles: number;
    viewModelFiles: number;
    readmeFiles: number;
  };
  /** 生成时间 */
  generatedAt: string;
  /** 任务ID */
  taskId: string;
}

/**
 * 生成AI代码
 *
 * @param request - 生成请求参数
 * @returns 生成结果
 */
export async function generateAICode(
  request: GenerateAICodeRequest
): Promise<APIResponse<GenerateAICodeResponse>> {
  return post<GenerateAICodeResponse>('/v1/ai-code/generate', request);
}

/**
 * 获取生成任务状态
 *
 * @param taskId - 任务ID
 * @returns 任务状态
 */
export async function getGenerationStatus(
  taskId: string
): Promise<APIResponse<GenerateAICodeResponse>> {
  return get<GenerateAICodeResponse>(`/v1/ai-code/status/${taskId}`);
}

/**
 * 下载生成的代码ZIP
 *
 * @param taskId - 任务ID
 * @returns ZIP文件Blob
 */
export async function downloadGeneratedCode(taskId: string): Promise<Blob> {
  /**
   * 默认后端基准URL，未提供环境变量时回落到8080端口
   */
  const API_BASE_URL = getApiBaseUrl();

  const response = await fetch(`${API_BASE_URL}/v1/ai-code/download/${taskId}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/zip',
      'x-trace-id': generateTraceId(),
    },
  });

  if (!response.ok) {
    throw new Error('下载失败');
  }

  return response.blob();
}

/**
 * 根据AI能力类型推荐复杂度
 *
 * @param capabilities - AI能力列表
 * @returns 推荐复杂度
 */
export function recommendComplexity(
  capabilities: AICapabilityType[]
): 'SIMPLE' | 'MEDIUM' | 'COMPLEX' {
  if (capabilities.length === 0) {
    return 'SIMPLE';
  }

  // 复杂能力映射
  const complexCapabilities: AICapabilityType[] = [
    AICapabilityType.VIDEO_ANALYSIS,
    AICapabilityType.KNOWLEDGE_GRAPH,
    AICapabilityType.REALTIME_STREAM,
    AICapabilityType.HYPER_PERSONALIZATION,
    AICapabilityType.PREDICTIVE_ANALYTICS,
    AICapabilityType.MULTIMODAL_FUSION,
  ];

  const mediumCapabilities: AICapabilityType[] = [
    AICapabilityType.OCR_DOCUMENT,
    AICapabilityType.IMAGE_RECOGNITION,
    AICapabilityType.SPEECH_RECOGNITION,
    AICapabilityType.CONTENT_MODERATION,
    AICapabilityType.SMART_SEARCH,
  ];

  // 如果包含复杂能力，返回COMPLEX
  if (capabilities.some((c) => complexCapabilities.includes(c))) {
    return 'COMPLEX';
  }

  // 如果包含中等能力或数量>3，返回MEDIUM
  if (
    capabilities.some((c) => mediumCapabilities.includes(c)) ||
    capabilities.length > 3
  ) {
    return 'MEDIUM';
  }

  return 'SIMPLE';
}
