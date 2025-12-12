import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';
import type { ProjectStats } from '@/types/project';

/**
 * GET /api/v1/projects/stats
 * 获取项目统计数据
 *
 * 返回用户的项目统计信息，包括总数、各状态数量等
 */
/**
 * 后端服务基准URL
 * 说明：通过Next.js API作为BFF代理，避免浏览器跨域或协议不一致导致的fetch失败
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

export async function GET(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/projects/stats`;
    console.log('📊 Proxying project stats request to backend:', backendUrl);

    const response = await fetch(backendUrl, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        ...(request.headers.get('authorization')
          ? { Authorization: request.headers.get('authorization') as string }
          : {}),
      },
      cache: 'no-store',
    });

    const latencyMs = Date.now() - startTime;

    if (!response.ok) {
      const errorText = await response.text();
      console.error('❌ Backend project stats error:', response.status, errorText);

      return NextResponse.json<APIResponse<ProjectStats>>({
        success: false,
        error: `后端接口错误(${response.status}): ${errorText || response.statusText}`,
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: response.status });
    }

    const raw = await response.json();
    const backendResult = normalizeApiResponse<ProjectStats>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<ProjectStats>>({
        success: false,
        error: backendResult.message || backendResult.error || '获取项目统计失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<ProjectStats>>({
      success: true,
      data: backendData as ProjectStats,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error fetching project stats:', error);

    return NextResponse.json<APIResponse<ProjectStats>>({
      success: false,
      error: error instanceof Error ? error.message : '获取统计数据失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
