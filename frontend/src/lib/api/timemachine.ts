/**
 * TimeMachine API客户端
 * 版本快照系统API调用封装
 */

import { get, post, del, type APIResponse } from './client';
import type { VersionTimelineItem, VersionDetail, VersionDiff } from '@/types/version';

/**
 * 获取版本历史时间线（通过taskId）
 *
 * @param taskId - 任务ID（generation_tasks.id）
 * @returns 版本时间线列表（按时间倒序）
 */
export async function getVersionTimeline(
  taskId: string
): Promise<APIResponse<VersionTimelineItem[]>> {
  return get<VersionTimelineItem[]>(`/v1/timemachine/timeline/${taskId}`);
}

/**
 * 获取版本历史时间线（通过appSpecId）
 *
 * 说明：
 * - 前端项目列表使用 appSpecId 作为标识
 * - 此函数通过 appSpecId 获取版本时间线
 *
 * @param appSpecId - 应用规格ID
 * @returns 版本时间线列表（按时间倒序）
 */
export async function getVersionTimelineByAppSpec(
  appSpecId: string
): Promise<APIResponse<VersionTimelineItem[]>> {
  return get<VersionTimelineItem[]>(`/v1/timemachine/appspec/${appSpecId}/timeline`);
}

/**
 * 获取版本详情
 *
 * @param versionId - 版本ID
 * @returns 版本详情（包含完整快照数据）
 */
export async function getVersionDetail(
  versionId: string
): Promise<APIResponse<VersionDetail>> {
  return get<VersionDetail>(`/v1/timemachine/version/${versionId}`);
}

/**
 * 对比两个版本的差异
 *
 * @param version1 - 版本1 ID
 * @param version2 - 版本2 ID
 * @returns 版本差异对比结果
 */
export async function compareVersions(
  version1: string,
  version2: string
): Promise<APIResponse<VersionDiff>> {
  return get<VersionDiff>(
    `/v1/timemachine/diff?version1=${encodeURIComponent(version1)}&version2=${encodeURIComponent(version2)}`
  );
}

/**
 * 回滚到指定版本
 *
 * 创建新的ROLLBACK类型版本，复制目标版本的快照数据
 *
 * @param versionId - 目标版本ID
 * @returns 新创建的ROLLBACK版本
 */
export async function rollbackToVersion(
  versionId: string
): Promise<APIResponse<VersionDetail>> {
  return post<VersionDetail>(`/v1/timemachine/rollback/${versionId}`, {});
}

/**
 * 删除版本（慎用）
 *
 * @param versionId - 版本ID
 * @returns 成功消息
 */
export async function deleteVersion(
  versionId: string
): Promise<APIResponse<string>> {
  return del<string>(`/v1/timemachine/version/${versionId}`);
}
