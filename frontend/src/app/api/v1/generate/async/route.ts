import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { type AsyncGenerateRequest, type AsyncGenerateResponse } from '@/lib/api/generate';

/**
 * 后端API基础URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * 异步生成AppSpec API路由
 * POST /api/v1/generate/async
 *
 * 代理请求到后端Java服务的异步生成接口
 */
export async function POST(request: NextRequest) {
  console.log('🚀 Async Generate API called - proxying to backend');

  try {
    const body: AsyncGenerateRequest = await request.json();
    console.log('📝 Request body:', body);

    // 验证必需参数
    if (!body.userRequirement || body.userRequirement.trim().length < 10) {
      return NextResponse.json<APIResponse<AsyncGenerateResponse>>({
        success: false,
        error: '需求描述至少需要10个字符',
        metadata: {
          requestId: `req_${Date.now()}`,
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      }, { status: 400 });
    }

    // 构建后端API请求
    const backendRequest = {
      userRequirement: body.userRequirement,
      model: body.model || 'gemini-3-pro-preview',
      skipValidation: body.skipValidation || false,
      qualityThreshold: body.qualityThreshold || 70,
      generatePreview: body.generatePreview || false,
    };

    console.log(`📞 Proxying async request to: ${BACKEND_API_URL}/v1/generate/async`);

    // 发送请求到后端
    const startTime = Date.now();
    const response = await fetch(`${BACKEND_API_URL}/v1/generate/async`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // 添加认证头（如果需要）
        // 'Authorization': request.headers.get('Authorization') || '',
      },
      body: JSON.stringify(backendRequest),
    });

    const endTime = Date.now();
    const latency = endTime - startTime;

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`❌ Backend API error: ${response.status} - ${errorText}`);

      return NextResponse.json<APIResponse<AsyncGenerateResponse>>({
        success: false,
        error: `后端服务错误 (${response.status}): ${errorText}`,
        metadata: {
          requestId: `req_${Date.now()}`,
          timestamp: new Date().toISOString(),
          latencyMs: latency,
        },
      }, { status: response.status });
    }

    const backendResponse = await response.json();
    console.log('✅ Backend async API response received');

    // 转换响应格式以匹配前端期望
    const asyncResponse: AsyncGenerateResponse = {
      taskId: backendResponse.data?.taskId || '',
    };

    return NextResponse.json<APIResponse<AsyncGenerateResponse>>({
      success: true,
      data: asyncResponse,
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: latency,
      },
    });

  } catch (error) {
    console.error('❌ Async Generate API error:', error);

    return NextResponse.json<APIResponse<AsyncGenerateResponse>>({
      success: false,
      error: error instanceof Error ? error.message : '创建异步生成任务失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}

/**
 * GET /api/v1/generate/async
 * 获取API状态
 */
export async function GET() {
  return NextResponse.json<APIResponse<{ status: string, backendUrl: string }>>({
    success: true,
    data: {
      status: 'Async API endpoint ready',
      backendUrl: BACKEND_API_URL
    },
    metadata: {
      requestId: `req_${Date.now()}`,
      timestamp: new Date().toISOString(),
      latencyMs: 0,
    },
  });
}