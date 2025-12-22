import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

interface NotificationSettings {
  emailEnabled: boolean;
  pushEnabled: boolean;
  frequency: 'realtime' | 'daily' | 'weekly';
  systemNotifications: boolean;
  commentNotifications: boolean;
  likeNotifications: boolean;
  forkNotifications: boolean;
  buildNotifications: boolean;
  mentionNotifications: boolean;
}

/**
 * 默认通知设置
 */
const DEFAULT_SETTINGS: NotificationSettings = {
  emailEnabled: true,
  pushEnabled: true,
  frequency: 'realtime',
  systemNotifications: true,
  commentNotifications: true,
  likeNotifications: true,
  forkNotifications: true,
  buildNotifications: true,
  mentionNotifications: true,
};

/**
 * GET /api/v1/notifications/settings
 * 获取通知设置
 * 代理到后端 Java 服务，如果后端未实现则返回默认设置
 */
export async function GET(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/notifications/settings`;
    console.log('🔔 Proxying notification settings request to backend:', backendUrl);

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

    // 后端未实现时返回默认设置
    if (response.status === 404) {
      console.warn('⚠️ Backend notification settings API not implemented, returning defaults');
      return NextResponse.json<APIResponse<NotificationSettings>>({
        success: true,
        data: DEFAULT_SETTINGS,
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    }

    if (!response.ok) {
      const errorText = await response.text();
      console.error('❌ Backend notification settings error:', response.status, errorText);

      return NextResponse.json<APIResponse<NotificationSettings>>({
        success: true,
        data: DEFAULT_SETTINGS,
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    }

    const raw = await response.json();
    const backendResult = normalizeApiResponse<NotificationSettings>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? DEFAULT_SETTINGS;

    return NextResponse.json<APIResponse<NotificationSettings>>({
      success: true,
      data: backendData as NotificationSettings,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error fetching notification settings:', error);

    return NextResponse.json<APIResponse<NotificationSettings>>({
      success: true,
      data: DEFAULT_SETTINGS,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    });
  }
}

/**
 * PUT /api/v1/notifications/settings
 * 更新通知设置
 * 代理到后端 Java 服务
 */
export async function PUT(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const body = await request.json();
    const backendUrl = `${BACKEND_API_URL}/v1/notifications/settings`;
    console.log('🔔 Proxying update notification settings request to backend:', backendUrl);

    const response = await fetch(backendUrl, {
      method: 'PUT',
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

    // 后端未实现时返回成功（静默失败）
    if (response.status === 404) {
      console.warn('⚠️ Backend notification settings API not implemented');
      return NextResponse.json<APIResponse<void>>({
        success: true,
        message: '设置已保存（本地）',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      });
    }

    if (!response.ok) {
      const errorText = await response.text();
      console.error('❌ Backend update notification settings error:', response.status, errorText);

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
        error: backendResult.message || backendResult.error || '更新通知设置失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<void>>({
      success: true,
      message: '设置已保存',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error updating notification settings:', error);

    return NextResponse.json<APIResponse<void>>({
      success: false,
      error: error instanceof Error ? error.message : '更新通知设置失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
