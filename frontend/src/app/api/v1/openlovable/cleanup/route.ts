/**
 * Sandbox清理API端点
 *
 * 功能：
 * - 清理E2B Sandbox资源
 * - 防止资源泄漏和不必要的计费
 * - 代理前端请求到后端OpenLovable服务
 *
 * @route POST /api/v1/openlovable/cleanup
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-12-10
 */

import { NextRequest, NextResponse } from 'next/server';

/**
 * 后端服务基准URL
 * 说明：该API属于服务端代理层，必须使用服务端环境变量，避免出现同源递归或错误拼接。
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * 清理请求体
 */
interface CleanupRequest {
  sandboxId: string;
}

/**
 * POST /api/v1/openlovable/cleanup
 *
 * 清理Sandbox资源
 */
export async function POST(request: NextRequest) {
  try {
    const body: CleanupRequest = await request.json();
    const { sandboxId } = body;

    // 验证参数
    if (!sandboxId || typeof sandboxId !== 'string') {
      return NextResponse.json(
        {
          success: false,
          error: '缺少必需参数: sandboxId',
        },
        { status: 400 }
      );
    }

    // 调用后端OpenLovable服务
    const backendUrl = `${BACKEND_API_URL}/v1/openlovable/cleanup`;

    console.log(`[清理API] 转发清理请求到后端: ${sandboxId}`);

    const backendResponse = await fetch(backendUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ sandboxId }),
      cache: 'no-store',
    });

    if (!backendResponse.ok) {
      const errorText = await backendResponse.text();
      console.error(`[清理API] 后端请求失败: ${backendResponse.status} - ${errorText}`);

      return NextResponse.json(
        {
          success: false,
          error: `后端清理请求失败: HTTP ${backendResponse.status}`,
        },
        { status: backendResponse.status }
      );
    }

    const data = await backendResponse.json();

    console.log(`[清理API] 清理成功: ${sandboxId}`);

    return NextResponse.json({
      success: true,
      data,
    });
  } catch (error) {
    console.error('[清理API] 请求处理失败:', error);

    return NextResponse.json(
      {
        success: false,
        error: error instanceof Error ? error.message : '清理请求失败',
      },
      { status: 500 }
    );
  }
}
