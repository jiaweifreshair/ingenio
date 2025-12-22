import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

interface LoginResponse {
  token: string;
  userId: string;
  username: string;
  email: string;
  role: string;
  expiresIn: number;
}

/**
 * POST /api/v1/auth/login
 * 用户登录
 * 代理到后端 Java 服务
 */
export async function POST(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const body = await request.json();
    const backendUrl = `${BACKEND_API_URL}/v1/auth/login`;
    console.log('🔐 Proxying login request to backend:', backendUrl);

    const response = await fetch(backendUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      cache: 'no-store',
    });

    const latencyMs = Date.now() - startTime;

    if (!response.ok) {
      const errorText = await response.text();
      console.error('❌ Backend login error:', response.status, errorText);

      // 尝试解析错误响应
      let errorMessage = '登录失败';
      try {
        const errorJson = JSON.parse(errorText);
        errorMessage = errorJson.message || errorJson.error || errorMessage;
      } catch {
        errorMessage = errorText || response.statusText;
      }

      return NextResponse.json<APIResponse<LoginResponse>>({
        success: false,
        error: errorMessage,
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: response.status });
    }

    const raw = await response.json();
    const backendResult = normalizeApiResponse<LoginResponse>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<LoginResponse>>({
        success: false,
        error: backendResult.message || backendResult.error || '登录失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 401 });
    }

    return NextResponse.json<APIResponse<LoginResponse>>({
      success: true,
      data: backendData as LoginResponse,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error during login:', error);

    return NextResponse.json<APIResponse<LoginResponse>>({
      success: false,
      error: error instanceof Error ? error.message : '登录失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
