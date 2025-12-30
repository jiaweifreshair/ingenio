// Phase 1: 资产服务层
// 负责处理 Mock 数据与真实 API 的切换逻辑

import { Template } from '@/types/assets';
import { MOCK_TEMPLATES } from './mock-data';

// 环境变量控制是否使用 Mock
// 在 .env.local 中设置 NEXT_PUBLIC_USE_MOCK_ASSETS=true 开启
const USE_MOCK = process.env.NEXT_PUBLIC_USE_MOCK_ASSETS === 'true' || true; // Phase 1 默认开启

const JEECG_API_BASE = process.env.JEECG_API_BASE || 'http://localhost:8080';

export interface GetTemplatesParams {
  status?: string;
  scene?: string;
  sort?: string;
  page?: number;
  size?: number;
  keyword?: string;
}

export interface PaginatedResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

/**
 * 获取模板列表
 */
export async function getTemplates(params: GetTemplatesParams = {}): Promise<PaginatedResult<Template>> {
  if (USE_MOCK) {
    console.log('[AssetService] Using MOCK data for templates');
    // 模拟网络延迟
    await new Promise(resolve => setTimeout(resolve, 500));
    
    let filtered = [...MOCK_TEMPLATES];
    
    // 模拟筛选
    if (params.scene) {
      filtered = filtered.filter(t => t.scene === params.scene);
    }
    if (params.keyword) {
      const lowerKeyword = params.keyword.toLowerCase();
      filtered = filtered.filter(t => 
        t.name.toLowerCase().includes(lowerKeyword) || 
        t.description.toLowerCase().includes(lowerKeyword)
      );
    }
    
    return {
      records: filtered,
      total: filtered.length,
      page: params.page || 1,
      size: params.size || 20
    };
  }

  // 真实 API 调用 (Phase 1 暂未完全启用，预留逻辑)
  try {
    const queryParams = new URLSearchParams();
    if (params.status) queryParams.append('status', params.status);
    if (params.scene) queryParams.append('scene', params.scene);
    if (params.sort) queryParams.append('sort', params.sort);
    if (params.page) queryParams.append('page', params.page.toString());
    if (params.size) queryParams.append('size', params.size.toString());
    if (params.keyword) queryParams.append('keyword', params.keyword);

    const res = await fetch(`${JEECG_API_BASE}/api/v1/templates?${queryParams}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
      cache: 'no-store' // 避免缓存过期数据
    });

    if (!res.ok) {
      throw new Error(`API Error: ${res.statusText}`);
    }

    return await res.json();
  } catch (error) {
    console.error('[AssetService] Failed to fetch templates:', error);
    // 失败降级到 Mock
    return {
      records: MOCK_TEMPLATES,
      total: MOCK_TEMPLATES.length,
      page: 1,
      size: 20
    };
  }
}

/**
 * 获取单个模板详情
 */
export async function getTemplateById(id: string): Promise<Template | null> {
  if (USE_MOCK) {
    await new Promise(resolve => setTimeout(resolve, 300));
    return MOCK_TEMPLATES.find(t => t.id === id) || null;
  }

  try {
    const res = await fetch(`${JEECG_API_BASE}/api/v1/templates/${id}`);
    if (!res.ok) return null;
    return await res.json();
  } catch (error) {
    console.error('[AssetService] Failed to fetch template:', error);
    return MOCK_TEMPLATES.find(t => t.id === id) || null;
  }
}
