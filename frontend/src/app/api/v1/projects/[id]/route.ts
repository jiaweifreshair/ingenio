import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import type { Project } from '@/types/project';
import { ProjectStatus, ProjectVisibility } from '@/types/project';

/**
 * GET /api/v1/projects/[id]
 * 获取项目详情
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`🔍 Fetching project: ${id}`);

  try {
    // TODO: 从后端或数据库获取项目详情
    // 目前返回模拟数据
    const project: Project = {
      id,
      tenantId: 'default-tenant',
      userId: 'default-user',
      appSpecId: 'test-app-123',
      name: '校园活动报名系统',
      description: '支持活动发布、在线报名、签到和数据统计的综合管理平台',
      coverImageUrl: '/images/campus-event.png',
      status: ProjectStatus.PUBLISHED,
      visibility: ProjectVisibility.PUBLIC,
      viewCount: 1250,
      likeCount: 89,
      forkCount: 12,
      commentCount: 34,
      createdAt: '2024-11-01T10:00:00Z',
      updatedAt: '2024-11-10T15:30:00Z',
      tags: ['校园', '活动管理', '报名系统'],
    };

    console.log(`✅ Retrieved project: ${id}`);

    return NextResponse.json<APIResponse<Project>>({
      success: true,
      data: project,
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 15,
      },
    });

  } catch (error) {
    console.error(`❌ Error fetching project:`, error);

    return NextResponse.json<APIResponse<Project>>({
      success: false,
      error: error instanceof Error ? error.message : '获取项目详情失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}

/**
 * DELETE /api/v1/projects/[id]
 * 删除项目
 */
export async function DELETE(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`🗑️ Deleting project: ${id}`);

  try {
    // TODO: 调用后端删除项目
    console.log(`✅ Deleted project: ${id}`);

    return NextResponse.json<APIResponse<void>>({
      success: true,
      data: undefined,
      message: '项目已删除',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 30,
      },
    });

  } catch (error) {
    console.error(`❌ Error deleting project:`, error);

    return NextResponse.json<APIResponse<void>>({
      success: false,
      error: error instanceof Error ? error.message : '删除项目失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}
