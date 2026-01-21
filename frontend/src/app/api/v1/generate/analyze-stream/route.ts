import { NextRequest } from 'next/server';

export const runtime = 'nodejs'; // Use Node.js runtime for 'typescript' package compatibility

/**
 * 后端API基础URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * Legacy模式处理函数
 * 代理请求到后端Java服务的SSE流式分析接口
 */
async function handleLegacyMode(body: Record<string, unknown>): Promise<Response> {
  console.log('📡 Legacy Mode: Proxying to backend');
  console.log('📝 SSE Request body:', JSON.stringify(body, null, 2));

  // 验证必需参数
  if (!body.requirement || String(body.requirement).trim().length < 10) {
    return new Response('需求描述至少需要10个字符', { status: 400 });
  }

  // 后端API路径：/api/v1/generate/analyze-stream
  const backendUrl = `${BACKEND_API_URL}/v1/generate/analyze-stream`;
  console.log(`📞 Proxying SSE request to: ${backendUrl}`);

  // 发送请求到后端
  const backendResponse = await fetch(backendUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
    },
    body: JSON.stringify(body),
  });

  if (!backendResponse.ok) {
    const errorText = await backendResponse.text();
    console.error(`❌ Backend SSE API error: ${backendResponse.status} - ${errorText}`);
    return new Response(`后端服务错误: ${errorText}`, { status: backendResponse.status });
  }

  if (!backendResponse.body) {
    console.error('❌ Backend response body is null');
    return new Response('后端响应体为空', { status: 500 });
  }

  console.log('✅ SSE stream established, forwarding to client...');

  // 创建流式响应，转发SSE事件
  const stream = new ReadableStream({
    async start(controller) {
      const reader = backendResponse.body!.getReader();
      const decoder = new TextDecoder();

      try {
        while (true) {
          const { done, value } = await reader.read();

          if (done) {
            console.log('✅ SSE stream completed');
            controller.close();
            break;
          }

          // 解码并转发数据
          const chunk = decoder.decode(value, { stream: true });
          console.log('📤 Forwarding SSE chunk:', chunk.substring(0, 100));
          controller.enqueue(value);
        }
      } catch (error) {
        // 优雅处理连接关闭错误
        const errorMessage = error instanceof Error ? error.message : String(error);

        // 如果是socket关闭或连接终止，这是正常的SSE完成情况
        if (errorMessage.includes('terminated') ||
            errorMessage.includes('closed') ||
            errorMessage.includes('socket')) {
          console.log('ℹ️ SSE connection closed by backend (normal completion)');
          controller.close();
        } else {
          // 其他错误才是真正的异常
          console.error('❌ SSE stream error:', error);

          // 尝试发送错误事件给客户端
          try {
            const errorEvent = `event:error\ndata:{"error":"${errorMessage}"}\n\n`;
            controller.enqueue(new TextEncoder().encode(errorEvent));
          } catch (e) {
            console.error('Failed to send error event:', e);
          }

          controller.close();
        }
      } finally {
        // 确保reader被释放
        try {
          reader.releaseLock();
        } catch {
          // Ignore release errors
        }
      }
    },
  });

  // 返回流式响应
  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  });
}

/**
 * 安全解析请求体 JSON
 *
 * 说明：
 * - 避免空请求体触发 `request.json()` 的解析异常
 * - 返回 null 表示请求体为空
 */
async function parseJsonBody(request: NextRequest): Promise<Record<string, unknown> | null> {
  const raw = await request.text();
  if (!raw.trim()) {
    return null;
  }

  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    throw new Error('请求体不是有效的 JSON');
  }
}

/**
 * SSE流式分析API路由
 * POST /api/v1/generate/analyze-stream
 *
 * 当前仅支持 legacy：
 * - 代理请求到后端Java服务的SSE流式分析接口（默认）
 *
 * 请求体：
 * - requirement: 需求描述
 */
export async function POST(request: NextRequest) {
  console.log('🌊 SSE Analyze API called');

  try {
    const body = await parseJsonBody(request);
    if (!body) {
      return new Response('请求体不能为空', { status: 400 });
    }

    // Legacy 模式：代理到后端
    return handleLegacyMode(body);

  } catch (error) {
    const message = error instanceof Error ? error.message : '分析失败';
    const status = message.includes('JSON') ? 400 : 500;
    console.error('❌ SSE Analyze API error:', error);
    return new Response(
      message,
      { status }
    );
  }
}

/**
 * GET /api/v1/generate/analyze-stream
 * 获取API状态
 */
export async function GET() {
  return Response.json({
    status: 'SSE API endpoint ready',
    supportedModes: ['legacy'],
    defaultMode: 'legacy',
    backendUrl: BACKEND_API_URL
  });
}
