import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

interface TokenRefreshResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  refreshedAt: number;
}

/**
 * POST /api/v1/auth/refresh
 * 刷新 Token
 * 代理到后端 Java 服务
 */
export async function POST(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/auth/refresh`;
    console.log('🔄 Proxying token refresh request to backend:', backendUrl);

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
      console.error('❌ Backend token refresh error:', response.status, errorText);

      return NextResponse.json<APIResponse<TokenRefreshResponse>>({
        success: false,
        error: `Token刷新失败: ${errorText || response.statusText}`,
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: response.status });
    }

    const raw = await response.json();
    const backendResult = normalizeApiResponse<TokenRefreshResponse>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<TokenRefreshResponse>>({
        success: false,
        error: backendResult.message || backendResult.error || 'Token刷新失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 401 });
    }

    return NextResponse.json<APIResponse<TokenRefreshResponse>>({
      success: true,
      data: backendData as TokenRefreshResponse,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error during token refresh:', error);

    return NextResponse.json<APIResponse<TokenRefreshResponse>>({
      success: false,
      error: error instanceof Error ? error.message : 'Token刷新失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
