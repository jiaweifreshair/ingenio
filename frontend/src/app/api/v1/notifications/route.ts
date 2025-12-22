import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';
import type { PageResult } from '@/types/project';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

interface Notification {
  id: string;
  type: string;
  title: string;
  content: string;
  isRead: boolean;
  createdAt: string;
}

/**
 * GET /api/v1/notifications
 * 获取通知列表
 * 代理到后端 Java 服务，如果后端未实现则返回空列表
 */
export async function GET(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const searchParams = request.nextUrl.searchParams;
    const queryString = searchParams.toString();
    
    const backendUrl = `${BACKEND_API_URL}/v1/notifications${queryString ? `?${queryString}` : ''}`;
    console.log('🔔 Proxying notifications list request to backend:', backendUrl);

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
    const current = parseInt(searchParams.get('current') || '1');
    const size = parseInt(searchParams.get('size') || '20');

    // 后端未实现时返回空列表
    if (response.status === 404) {
      console.warn('⚠️ Backend notifications API not implemented, returning empty list');
      return NextResponse.json<APIResponse<PageResult<Notification>>>({
        success: true,
        data: {
          records: [],
          total: 0,
          current,
          size,
          pages: 0,
          hasNext: false,
          hasPrevious: false,
        },
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    }

    if (!response.ok) {
      const errorText = await response.text();
      console.error('❌ Backend notifications list error:', response.status, errorText);

      // 对于其他错误也返回空列表
      return NextResponse.json<APIResponse<PageResult<Notification>>>({
        success: true,
        data: {
          records: [],
          total: 0,
          current,
          size,
          pages: 0,
          hasNext: false,
          hasPrevious: false,
        },
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    }

    const raw = await response.json();
    const backendResult = normalizeApiResponse<PageResult<Notification>>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data;

    return NextResponse.json<APIResponse<PageResult<Notification>>>({
      success: true,
      data: backendData as PageResult<Notification>,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error fetching notifications:', error);

    // 网络错误时也返回空列表
    return NextResponse.json<APIResponse<PageResult<Notification>>>({
      success: true,
      data: {
        records: [],
        total: 0,
        current: 1,
        size: 20,
        pages: 0,
        hasNext: false,
        hasPrevious: false,
      },
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    });
  }
}
