import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

interface WxQrcodeResponse {
  qrcodeUrl: string;
  sceneStr: string;
  expiresIn: number;
}

/**
 * GET /api/v1/auth/wechat/qrcode
 * 生成微信登录二维码
 * 代理到后端 Java 服务
 */
export async function GET(_request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/auth/wechat/qrcode`;
    console.log('📱 Proxying wechat qrcode request to backend:', backendUrl);

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
      console.error('❌ Backend wechat qrcode error:', response.status, errorText);

      return NextResponse.json<APIResponse<WxQrcodeResponse>>({
        success: false,
        error: `获取微信二维码失败: ${errorText || response.statusText}`,
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: response.status });
    }

    const raw = await response.json();
    const backendResult = normalizeApiResponse<WxQrcodeResponse>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<WxQrcodeResponse>>({
        success: false,
        error: backendResult.message || backendResult.error || '获取微信二维码失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<WxQrcodeResponse>>({
      success: true,
      data: backendData as WxQrcodeResponse,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error getting wechat qrcode:', error);

    return NextResponse.json<APIResponse<WxQrcodeResponse>>({
      success: false,
      error: error instanceof Error ? error.message : '获取微信二维码失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
