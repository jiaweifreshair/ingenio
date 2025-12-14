import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { normalizeApiResponse } from '@/lib/api/response';
import type { Project } from '@/types/project';

/**
 * 后端服务基准URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * GET /api/v1/projects/[id]
 * 获取项目详情
 * 代理到后端 Java 服务
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  console.log(`🔍 Proxying get project request: ${id}`);

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/projects/${id}`;

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
      console.error(`❌ Backend get project error:`, response.status, errorText);

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
        error: backendResult.message || backendResult.error || '获取项目详情失败',
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
    console.error(`❌ Error fetching project:`, error);

    return NextResponse.json<APIResponse<Project>>({
      success: false,
      error: error instanceof Error ? error.message : '获取项目详情失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}

/**
 * PUT /api/v1/projects/[id]
 * 更新项目
 * 代理到后端 Java 服务
 */
export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  console.log(`📝 Proxying update project request: ${id}`);

  try {
    const body = await request.json();
    const backendUrl = `${BACKEND_API_URL}/v1/projects/${id}`;

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

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`❌ Backend update project error:`, response.status, errorText);

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
        error: backendResult.message || backendResult.error || '更新项目失败',
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
    console.error(`❌ Error updating project:`, error);

    return NextResponse.json<APIResponse<Project>>({
      success: false,
      error: error instanceof Error ? error.message : '更新项目失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}

/**
 * DELETE /api/v1/projects/[id]
 * 删除项目
 * 代理到后端 Java 服务
 */
export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const requestId = `req_${Date.now()}`;
  const startTime = Date.now();

  console.log(`🗑️ Proxying delete project request: ${id}`);

  try {
    const backendUrl = `${BACKEND_API_URL}/v1/projects/${id}`;

    const response = await fetch(backendUrl, {
      method: 'DELETE',
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
      console.error(`❌ Backend delete project error:`, response.status, errorText);

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
        error: backendResult.message || backendResult.error || '删除项目失败',
        metadata: {
          requestId,
          timestamp: new Date().toISOString(),
          latencyMs,
        },
      }, { status: 502 });
    }

    return NextResponse.json<APIResponse<void>>({
      success: true,
      message: '项目已删除',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs,
      },
    });

  } catch (error) {
    console.error(`❌ Error deleting project:`, error);

    return NextResponse.json<APIResponse<void>>({
      success: false,
      error: error instanceof Error ? error.message : '删除项目失败',
      metadata: {
        requestId,
        timestamp: new Date().toISOString(),
        latencyMs: Date.now() - startTime,
      },
    }, { status: 500 });
  }
}
