/**
 * 发布API
 * 封装多端发布相关的API调用
 */

import { post, get } from './client';
import type { PlatformType } from '@/components/publish/publish-dialog';

// 重新导出 PlatformType 供外部使用
export type { PlatformType };

/**
 * 平台配置类型映射（与后端DTO对应）
 */
export interface AndroidConfig {
  packageName: string;
  appName: string;
  versionName: string;
  versionCode: number;
  minSdkVersion: number;
  targetSdkVersion: number;
}

export interface IosConfig {
  bundleId: string;
  appName: string;
  versionName: string;
  buildNumber: string;
  teamId: string;
  minIosVersion: string;
}

export interface H5Config {
  title: string;
  domain: string;
  seoKeywords: string;
  seoDescription: string;
}

export interface MiniAppConfig {
  appId: string;
  appName: string;
  platform: 'wechat' | 'alipay' | 'bytedance';
}

/**
 * 平台配置联合类型
 */
export type PlatformConfigs = {
  android?: AndroidConfig;
  ios?: IosConfig;
  h5?: H5Config;
  miniapp?: MiniAppConfig;
};

/**
 * 发布请求参数
 */
export interface PublishRequest {
  /** 项目ID */
  projectId: string;
  /** 发布平台列表 */
  platforms: PlatformType[];
  /** 平台配置参数（只需要包含选中平台的配置） */
  platformConfigs: Partial<Record<PlatformType, Record<string, unknown>>>;
  /** 是否并行构建 */
  parallelBuild?: boolean;
  /** 构建优先级 */
  priority?: 'LOW' | 'NORMAL' | 'HIGH';
}

/**
 * 平台构建结果
 */
export interface PlatformBuildResult {
  /** 平台类型 */
  platform: PlatformType;
  /** 构建状态 */
  status: 'PENDING' | 'IN_PROGRESS' | 'SUCCESS' | 'FAILED' | 'CANCELLED';
  /** 构建进度（0-100） */
  progress: number;
  /** 构建日志URL */
  logUrl?: string;
  /** 构建产物下载URL */
  downloadUrl?: string;
  /** 错误信息（如果失败） */
  errorMessage?: string;
  /** 开始时间 */
  startedAt?: string;
  /** 完成时间 */
  completedAt?: string;
}

/**
 * 发布响应数据
 */
export interface PublishResponse {
  /** 构建任务ID */
  buildId: string;
  /** 项目ID */
  projectId: string;
  /** 发布的平台列表 */
  platforms: PlatformType[];
  /** 总体构建状态 */
  status: 'PENDING' | 'IN_PROGRESS' | 'SUCCESS' | 'FAILED' | 'CANCELLED';
  /** 每个平台的构建结果 */
  platformResults: Record<PlatformType, PlatformBuildResult>;
  /** 预计完成时间（分钟） */
  estimatedTime: number;
  /** 创建时间 */
  createdAt: string;
  /** 更新时间 */
  updatedAt: string;
}

/**
 * 创建发布任务
 *
 * @param request 发布请求参数
 * @returns 发布响应，包含构建任务ID和初始状态
 */
export async function createPublishTask(
  request: PublishRequest
): Promise<PublishResponse> {
  const response = await post<PublishResponse>('/v1/publish/create', request);

  if (!response.success || !response.data) {
    throw new Error(response.error || '创建发布任务失败');
  }

  return response.data;
}

/**
 * 查询构建状态
 *
 * @param buildId 构建任务ID
 * @returns 构建状态响应
 */
export async function getBuildStatus(buildId: string): Promise<PublishResponse> {
  const response = await get<PublishResponse>(`/v1/publish/status/${buildId}`);

  if (!response.success || !response.data) {
    throw new Error(response.error || '查询构建状态失败');
  }

  return response.data;
}

/**
 * 取消构建任务
 *
 * @param buildId 构建任务ID
 */
export async function cancelBuild(buildId: string): Promise<void> {
  const response = await post<string>(`/v1/publish/cancel/${buildId}`, {});

  if (!response.success) {
    throw new Error(response.error || '取消构建任务失败');
  }
}

/**
 * 获取构建日志
 *
 * @param buildId 构建任务ID
 * @param platform 平台类型（可选）
 * @returns 构建日志内容
 */
export async function getBuildLogs(
  buildId: string,
  platform?: PlatformType
): Promise<string> {
  const url = platform
    ? `/v1/publish/logs/${buildId}?platform=${platform}`
    : `/v1/publish/logs/${buildId}`;

  const response = await get<string>(url);

  if (!response.success) {
    throw new Error(response.error || '获取构建日志失败');
  }

  return response.data || '';
}

/**
 * 获取下载链接
 *
 * @param buildId 构建任务ID
 * @param platform 平台类型
 * @returns 下载URL
 */
export async function getDownloadUrl(
  buildId: string,
  platform: PlatformType
): Promise<string> {
  const response = await get<string>(`/v1/publish/download/${buildId}/${platform}`);

  if (!response.success) {
    throw new Error(response.error || '获取下载链接失败');
  }

  return response.data || '';
}
