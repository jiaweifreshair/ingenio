import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';

/**
 * 后端Java服务基准地址
 * 说明：后端配置了 context-path=/api，因此这里默认包含 /api 前缀。
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * POST /api/v1/prototype/create-app-spec
 * 代理到后端 PrototypeController#createAppSpec
 *
 * 作用：
 * - 当 NEXT_PUBLIC_API_BASE_URL 为空字符串（走同源 /api）时，前端需要 BFF 兜底代理
 * - 避免原型生成成功但 AppSpec 未创建导致后续“确认设计”缺少 AppSpec ID
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // 最小参数校验，提升报错可读性
    if (!body?.userRequirement || typeof body.userRequirement !== 'string') {
      return NextResponse.json<APIResponse<Record<string, unknown>>>(
        {
          success: false,
          error: 'userRequirement 不能为空',
          metadata: {
            requestId: `req_${Date.now()}`,
            timestamp: new Date().toISOString(),
            latencyMs: 0,
          },
        },
        { status: 400 }
      );
    }
    if (!body?.sandboxUrl || typeof body.sandboxUrl !== 'string') {
      return NextResponse.json<APIResponse<Record<string, unknown>>>(
        {
          success: false,
          error: 'sandboxUrl 不能为空',
          metadata: {
            requestId: `req_${Date.now()}`,
            timestamp: new Date().toISOString(),
            latencyMs: 0,
          },
        },
        { status: 400 }
      );
    }

    const backendUrl = `${BACKEND_API_URL}/v1/prototype/create-app-spec`;
    const startTime = Date.now();

    const response = await fetch(backendUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // 透传鉴权信息（若存在）
        ...(request.headers.get('Authorization')
          ? { Authorization: request.headers.get('Authorization') as string }
          : {}),
      },
      body: JSON.stringify(body),
    });

    const latency = Date.now() - startTime;

    if (!response.ok) {
      const errorText = await response.text();
      return NextResponse.json<APIResponse<Record<string, unknown>>>(
        {
          success: false,
          error: `后端服务错误 (${response.status}): ${errorText}`,
          metadata: {
            requestId: `req_${Date.now()}`,
            timestamp: new Date().toISOString(),
            latencyMs: latency,
          },
        },
        { status: response.status }
      );
    }

    const backendResponse = await response.json();

    return NextResponse.json(backendResponse, {
      status: 200,
    });
  } catch (error) {
    return NextResponse.json<APIResponse<Record<string, unknown>>>(
      {
        success: false,
        error: error instanceof Error ? error.message : '创建 AppSpec 失败',
        metadata: {
          requestId: `req_${Date.now()}`,
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      },
      { status: 500 }
    );
  }
}

