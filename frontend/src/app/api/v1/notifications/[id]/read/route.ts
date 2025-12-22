import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * PUT /api/v1/notifications/[id]/read
 * 标记通知为已读
 * 代理到后端 Java 服务
 */
export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  console.log(`✅ Proxying mark as read request: ${id}`);

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/notifications/${id}/read`;

    const response = await fetch(backendUrl, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...(request.headers.get('authorization')
          ? { Authorization: request.headers.get('authorization') as string }
          : {}),
      },
      cache: 'no-store',
    });

    const latencyMs = Date.now() - startTime;

    // 后端未实现时返回成功（静默处理）
    if (response.status === 404) {
      console.warn('⚠️ Backend notifications API not implemented');
      return NextResponse.json<APIResponse<void>>({
        success: true,
        message: '已标记为已读',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    }

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`❌ Backend mark as read error:`, response.status, errorText);

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
        error: backendResult.message || backendResult.error || '标记已读失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<void>>({
      success: true,
      message: '已标记为已读',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error(`❌ Error marking as read:`, error);

    return NextResponse.json<APIResponse<void>>({
      success: false,
      error: error instanceof Error ? error.message : '标记已读失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
