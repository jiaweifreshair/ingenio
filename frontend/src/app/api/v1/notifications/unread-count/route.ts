import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * GET /api/v1/notifications/unread-count
 * 获取未读通知数量
 * 代理到后端 Java 服务，如果后端未实现则返回默认值
 */
export async function GET(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/notifications/unread-count`;
    console.log('🔔 Proxying unread count request to backend:', backendUrl);

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

    // 后端未实现时返回默认值 0
    if (response.status === 404) {
      console.warn('⚠️ Backend notifications API not implemented, returning default value');
      return NextResponse.json<APIResponse<{ count: number }>>({
        success: true,
        data: { count: 0 },
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    }

    if (!response.ok) {
      const errorText = await response.text();
      console.error('❌ Backend unread count error:', response.status, errorText);

      // 对于其他错误也返回默认值，避免页面崩溃
      return NextResponse.json<APIResponse<{ count: number }>>({
        success: true,
        data: { count: 0 },
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    }

    const raw = await response.json();
    const backendResult = normalizeApiResponse<{ count: number }>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? { count: 0 };

    return NextResponse.json<APIResponse<{ count: number }>>({
      success: true,
      data: backendData as { count: number },
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error fetching unread count:', error);

    // 网络错误时也返回默认值
    return NextResponse.json<APIResponse<{ count: number }>>({
      success: true,
      data: { count: 0 },
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    });
  }
}
