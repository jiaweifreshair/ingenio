import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * POST /api/v1/auth/logout
 * 用户登出
 * 代理到后端 Java 服务
 */
export async function POST(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/auth/logout`;
    console.log('🚪 Proxying logout request to backend:', backendUrl);

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

    // 即使后端返回错误，也返回成功（前端会清除本地 Token）
    if (!response.ok) {
      console.warn('⚠️ Backend logout returned error, but proceeding:', response.status);
    }

    // 尝试解析响应
    try {
      const raw = await response.json();
      const backendResult = normalizeApiResponse<void>(raw);

      return NextResponse.json<APIResponse<void>>({
        success: true,
        message: backendResult.message || '已退出登录',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    } catch {
      // 如果响应不是 JSON，也返回成功
      return NextResponse.json<APIResponse<void>>({
        success: true,
        message: '已退出登录',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    }

  } catch (error) {
    console.error('❌ Error during logout:', error);

    // 即使出错也返回成功，让前端清除本地 Token
    return NextResponse.json<APIResponse<void>>({
      success: true,
      message: '已退出登录',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    });
  }
}
