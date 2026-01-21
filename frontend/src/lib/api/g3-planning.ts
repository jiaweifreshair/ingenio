/**
 * G3 规划文件 API 客户端
 *
 * 对齐后端接口：
 * - GET /v1/g3/jobs/{jobId}/planning/map
 * - GET /v1/g3/jobs/{jobId}/planning/{type}
 * - PUT /v1/g3/jobs/{jobId}/planning/{type}
 */

import { get, put, type APIResponse } from './client';
import type { G3PlanningFileEntity, G3PlanningFileType, G3PlanningFilesMap } from '@/types/g3-planning';

/**
 * 获取任务的规划文件（Map 格式）
 */
export async function getG3PlanningFilesMap(
  jobId: string
): Promise<APIResponse<G3PlanningFilesMap>> {
  return get<G3PlanningFilesMap>(`/v1/g3/jobs/${jobId}/planning/map`);
}

/**
 * 获取单个规划文件（包含内容）
 */
export async function getG3PlanningFile(
  jobId: string,
  fileType: G3PlanningFileType
): Promise<APIResponse<G3PlanningFileEntity>> {
  return get<G3PlanningFileEntity>(`/v1/g3/jobs/${jobId}/planning/${fileType}`);
}

/**
 * 更新单个规划文件内容（用户手动修改）
 */
export async function updateG3PlanningFile(
  jobId: string,
  fileType: G3PlanningFileType,
  content: string
): Promise<APIResponse<G3PlanningFileEntity>> {
  return put<G3PlanningFileEntity>(`/v1/g3/jobs/${jobId}/planning/${fileType}`, {
    content,
    updatedBy: 'user',
  });
}

