import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

interface ScanStatusResponse {
  status: 'pending' | 'scanned' | 'confirmed' | 'expired';
  token?: string;
  userInfo?: {
    id: string;
    username: string;
    email: string;
    avatar?: string;
  };
}

/**
 * GET /api/v1/auth/wechat/check-scan
 * 检查微信扫码状态
 * 代理到后端 Java 服务
 */
export async function GET(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const sceneStr = request.nextUrl.searchParams.get('sceneStr');
    if (!sceneStr) {
      return NextResponse.json<APIResponse<ScanStatusResponse>>({
        success: false,
        error: '缺少 sceneStr 参数',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      }, { status: 400 });
    }

    const backendUrl = `${BACKEND_API_URL}/v1/auth/wechat/check-scan?sceneStr=${encodeURIComponent(sceneStr)}`;
    console.log('🔍 Proxying wechat check-scan request to backend:', backendUrl);

    const response = await fetch(backendUrl, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
      cache: 'no-store',
    });

    const latencyMs = Date.now() - startTime;

    if (!response.ok) {
      const errorText = await response.text();
      console.error('❌ Backend wechat check-scan error:', response.status, errorText);

      return NextResponse.json<APIResponse<ScanStatusResponse>>({
        success: false,
        error: `检查扫码状态失败: ${errorText || response.statusText}`,
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: response.status });
    }

    const raw = await response.json();
    const backendResult = normalizeApiResponse<ScanStatusResponse>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<ScanStatusResponse>>({
        success: false,
        error: backendResult.message || backendResult.error || '检查扫码状态失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<ScanStatusResponse>>({
      success: true,
      data: backendData as ScanStatusResponse,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error checking wechat scan status:', error);

    return NextResponse.json<APIResponse<ScanStatusResponse>>({
      success: false,
      error: error instanceof Error ? error.message : '检查扫码状态失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
