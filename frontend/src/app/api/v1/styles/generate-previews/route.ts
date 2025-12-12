import { NextRequest } from 'next/server';

/**
 * 后端API基础URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * POST /api/v1/styles/generate-previews
 * 生成7种设计风格快速预览 (代理到后端)
 */
export async function POST(request: NextRequest) {
  console.log('🎨 Style Preview API called - proxying to backend');

  try {
    const body = await request.json();
    console.log('📝 Style Generation Request:', JSON.stringify(body, null, 2));

    // 后端API路径：/api/v1/styles/generate-previews (Java Backend)
    // 注意：确保 backend/src/main/java/com/ingenio/backend/controller/StyleController.java 正在运行
    const backendUrl = `${BACKEND_API_URL}/v1/styles/generate-previews`;
    console.log(`📞 Proxying request to: ${backendUrl}`);

    const backendResponse = await fetch(backendUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // 如果需要认证，在这里传递 Authorization header
        // 'Authorization': request.headers.get('authorization') || '',
      },
      body: JSON.stringify(body),
    });

    if (!backendResponse.ok) {
      const errorText = await backendResponse.text();
      console.error(`❌ Backend API error: ${backendResponse.status} - ${errorText}`);
      return new Response(`后端服务错误: ${errorText}`, { status: backendResponse.status });
    }

    const data = await backendResponse.json();
    console.log(`✅ Backend responded with ${data.data?.styles?.length || 0} styles`);

    return Response.json(data.data || data); // 处理 Result<T> 包装或者直接返回 T

  } catch (error) {
    console.error('❌ Style Generation API error:', error);
    return new Response(
      error instanceof Error ? error.message : '风格生成失败',
      { status: 500 }
    );
  }
}
