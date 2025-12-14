import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * POST /api/v1/projects/[id]/publish
 * 发布项目
 * 代理到后端 Java 服务
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  console.log(`📤 Proxying publish project request: ${id}`);

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/projects/${id}/publish`;

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
      console.error(`❌ Backend publish project error:`, response.status, errorText);

      return NextResponse.json<APIResponse<void>>({
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
    const backendResult = normalizeApiResponse<void>(raw);

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<void>>({
        success: false,
        error: backendResult.message || backendResult.error || '发布项目失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<void>>({
      success: true,
      message: '项目已发布',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error(`❌ Error publishing project:`, error);

    return NextResponse.json<APIResponse<void>>({
      success: false,
      error: error instanceof Error ? error.message : '发布项目失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
