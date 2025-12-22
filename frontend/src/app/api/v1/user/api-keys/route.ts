import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

interface ApiKey {
  id: string;
  name: string;
  keyPrefix: string;
  fullKey?: string;
  description?: string;
  scopes?: string[];
  isActive?: boolean;
  lastUsedAt?: string;
  lastUsedIp?: string;
  usageCount?: number;
  rateLimit?: number;
  expiresAt?: string;
  createdAt: string;
}

/**
 * GET /api/v1/user/api-keys
 * 获取 API 密钥列表
 * 代理到后端 Java 服务
 */
export async function GET(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/user/api-keys`;
    console.log('🔑 Proxying list API keys request to backend:', backendUrl);

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
      console.error('❌ Backend list API keys error:', response.status, errorText);

      return NextResponse.json<APIResponse<ApiKey[]>>({
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
    const backendResult = normalizeApiResponse<ApiKey[]>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<ApiKey[]>>({
        success: false,
        error: backendResult.message || backendResult.error || '获取API密钥列表失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<ApiKey[]>>({
      success: true,
      data: backendData as ApiKey[],
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error fetching API keys:', error);

    return NextResponse.json<APIResponse<ApiKey[]>>({
      success: false,
      error: error instanceof Error ? error.message : '获取API密钥列表失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}

/**
 * POST /api/v1/user/api-keys
 * 创建新的 API 密钥
 * 代理到后端 Java 服务
 */
export async function POST(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const body = await request.json();
    const backendUrl = `${BACKEND_API_URL}/v1/user/api-keys`;
    console.log('🔑 Proxying create API key request to backend:', backendUrl);

    const response = await fetch(backendUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(request.headers.get('authorization')
          ? { Authorization: request.headers.get('authorization') as string }
          : {}),
      },
      body: JSON.stringify(body),
      cache: 'no-store',
    });

    const latencyMs = Date.now() - startTime;

    if (!response.ok) {
      const errorText = await response.text();
      console.error('❌ Backend create API key error:', response.status, errorText);

      return NextResponse.json<APIResponse<ApiKey>>({
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
    const backendResult = normalizeApiResponse<ApiKey>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<ApiKey>>({
        success: false,
        error: backendResult.message || backendResult.error || '创建API密钥失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<ApiKey>>({
      success: true,
      data: backendData as ApiKey,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error creating API key:', error);

    return NextResponse.json<APIResponse<ApiKey>>({
      success: false,
      error: error instanceof Error ? error.message : '创建API密钥失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
