import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';
import type { Project } from '@/types/project';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * POST /api/v1/projects/[id]/fork
 * 复制（Fork）项目
 * 代理到后端 Java 服务
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  console.log(`🔄 Proxying fork project request: ${id}`);

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/projects/${id}/fork`;

    const response = await fetch(backendUrl, {
      method: 'POST',
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
      console.error(`❌ Backend fork project error:`, response.status, errorText);

      return NextResponse.json<APIResponse<Project>>({
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
    const backendResult = normalizeApiResponse<Project>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<Project>>({
        success: false,
        error: backendResult.message || backendResult.error || '复制项目失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<Project>>({
      success: true,
      data: backendData as Project,
      message: '项目已复制',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error(`❌ Error forking project:`, error);

    return NextResponse.json<APIResponse<Project>>({
      success: false,
      error: error instanceof Error ? error.message : '复制项目失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
