import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';
import type { Project, PageResult } from '@/types/project';

/**
 * 后端服务基准URL
 * 说明：通过Next.js API作为BFF代理，避免浏览器跨域或协议不一致导致的fetch失败
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * GET /api/v1/projects
 * 分页查询项目列表，支持状态筛选和关键词搜索
 * 代理到后端 Java 服务
 */
export async function GET(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    // 获取查询参数
    const searchParams = request.nextUrl.searchParams;
    const queryString = searchParams.toString();
    
    const backendUrl = `${BACKEND_API_URL}/v1/projects${queryString ? `?${queryString}` : ''}`;
    console.log('📋 Proxying projects list request to backend:', backendUrl);

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
      console.error('❌ Backend projects list error:', response.status, errorText);

      return NextResponse.json<APIResponse<PageResult<Project>>>({
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
    const backendResult = normalizeApiResponse<PageResult<Project>>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<PageResult<Project>>>({
        success: false,
        error: backendResult.message || backendResult.error || '获取项目列表失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<PageResult<Project>>>({
      success: true,
      data: backendData as PageResult<Project>,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error fetching projects:', error);

    return NextResponse.json<APIResponse<PageResult<Project>>>({
      success: false,
      error: error instanceof Error ? error.message : '获取项目列表失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}

/**
 * POST /api/v1/projects
 * 创建新项目
 * 代理到后端 Java 服务
 */
export async function POST(request: NextRequest) {
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  try {
    const body = await request.json();
    const backendUrl = `${BACKEND_API_URL}/v1/projects`;
    console.log('📝 Proxying create project request to backend:', backendUrl);

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
      console.error('❌ Backend create project error:', response.status, errorText);

      return NextResponse.json<APIResponse<Project>>({
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
    const backendResult = normalizeApiResponse<Project>(raw);
    const backendData = backendResult.data ?? (raw as { data?: unknown }).data ?? raw;

    if (!backendResult.success) {
      return NextResponse.json<APIResponse<Project>>({
        success: false,
        error: backendResult.message || backendResult.error || '创建项目失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<Project>>({
      success: true,
      data: backendData as Project,
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error('❌ Error creating project:', error);

    return NextResponse.json<APIResponse<Project>>({
      success: false,
      error: error instanceof Error ? error.message : '创建项目失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
