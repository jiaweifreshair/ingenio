import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';

/**
 * POST /api/v1/projects/[id]/archive
 * 归档项目
 */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`📦 Archiving project: ${id}`);

  try {
    // TODO: 调用后端归档项目
    console.log(`✅ Archived project: ${id}`);

    return NextResponse.json<APIResponse<void>>({
      success: true,
      data: undefined,
      message: '项目已归档',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 35,
      },
    });

  } catch (error) {
    console.error(`❌ Error archiving project:`, error);

    return NextResponse.json<APIResponse<void>>({
      success: false,
      error: error instanceof Error ? error.message : '归档项目失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}
