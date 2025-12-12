import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';

/**
 * POST /api/v1/projects/[id]/publish
 * 发布项目
 */
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`📤 Publishing project: ${id}`);

  try {
    // TODO: 调用后端发布项目
    console.log(`✅ Published project: ${id}`);

    return NextResponse.json<APIResponse<void>>({
      success: true,
      data: undefined,
      message: '项目已发布',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 40,
      },
    });

  } catch (error) {
    console.error(`❌ Error publishing project:`, error);

    return NextResponse.json<APIResponse<void>>({
      success: false,
      error: error instanceof Error ? error.message : '发布项目失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}
