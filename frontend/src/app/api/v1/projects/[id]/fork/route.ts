import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import type { Project } from '@/types/project';
import { ProjectStatus, ProjectVisibility } from '@/types/project';

/**
 * POST /api/v1/projects/[id]/fork
 * 复制（Fork）项目
 */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`🔄 Forking project: ${id}`);

  try {
    // TODO: 调用后端复制项目
    // 目前返回模拟的复制项目
    const forkedProject: Project = {
      id: `proj-fork-${Date.now()}`,
      tenantId: 'default-tenant',
      userId: 'default-user',
      appSpecId: 'test-app-fork',
      name: '校园活动报名系统 (副本)',
      description: '支持活动发布、在线报名、签到和数据统计的综合管理平台',
      coverImageUrl: '/images/campus-event.png',
      status: ProjectStatus.DRAFT,
      visibility: ProjectVisibility.PRIVATE,
      viewCount: 0,
      likeCount: 0,
      forkCount: 0,
      commentCount: 0,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      tags: ['校园', '活动管理', '报名系统'],
    };

    console.log(`✅ Forked project: ${id} -> ${forkedProject.id}`);

    return NextResponse.json<APIResponse<Project>>({
      success: true,
      data: forkedProject,
      message: '项目已复制',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 50,
      },
    });

  } catch (error) {
    console.error(`❌ Error forking project:`, error);

    return NextResponse.json<APIResponse<Project>>({
      success: false,
      error: error instanceof Error ? error.message : '复制项目失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}
