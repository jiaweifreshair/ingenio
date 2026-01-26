import { get } from '@/lib/api/client';

export interface AppSpec {
  id: string;
  tenantId: string;
  createdByUserId: string;
  specContent: Record<string, unknown>;
  version: number;
  parentVersionId?: string;
  status: string;
  qualityScore?: number;
  createdAt: string;
  updatedAt: string;
  metadata?: Record<string, unknown>;
}

/**
 * 获取AppSpec详情
 * @param id AppSpec ID
 */
export async function getAppSpecById(id: string): Promise<AppSpec> {
  const result = await get<AppSpec>(`/v1/appspecs/${id}`);
  if (!result.success || !result.data) {
    throw new Error(result.message || '获取AppSpec失败');
  }
  return result.data;
}
