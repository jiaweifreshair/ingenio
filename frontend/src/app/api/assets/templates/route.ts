// Phase 1: 模板列表 API
// Endpoint: /api/assets/templates

import { NextResponse } from 'next/server';
import { getTemplates } from '@/lib/assets/asset-service';

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  
  const status = searchParams.get('status') || 'PUBLISHED';
  const scene = searchParams.get('scene') || undefined;
  const sort = searchParams.get('sort') || undefined;
  const page = parseInt(searchParams.get('page') || '1');
  const size = parseInt(searchParams.get('size') || '20');
  const keyword = searchParams.get('keyword') || undefined;

  try {
    const result = await getTemplates({
      status,
      scene,
      sort,
      page,
      size,
      keyword
    });

    return NextResponse.json(result);
  } catch (error) {
    console.error('[API] Failed to fetch templates:', error);
    return NextResponse.json(
      { error: 'Failed to fetch templates' },
      { status: 500 }
    );
  }
}
