import { NextRequest, NextResponse } from 'next/server';

export const runtime = 'nodejs';

/**
 * 后端 API 基础地址（默认本地 Java 服务）
 * 注意：后端配置了 context-path=/api，因此这里默认包含 /api 前缀。
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * 构建一个仅包含“单条错误日志”的 SSE 响应
 *
 * 目的：
 * - Lab 页统一使用 SSE 显示日志
 * - 即使后端调用失败，也能在 UI 中看到明确错误信息（而不是 fetch 抛异常）
 */
function buildSingleErrorSseResponse(message: string, status = 500): NextResponse {
  const encoder = new TextEncoder();

  const stream = new ReadableStream({
    start(controller) {
      const entry = {
        timestamp: new Date().toISOString(),
        role: 'EXECUTOR',
        level: 'error',
        message,
      };

      controller.enqueue(encoder.encode(`data: ${JSON.stringify(entry)}\n\n`));
      controller.close();
    },
  });

  return new NextResponse(stream, {
    status,
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  });
}

/**
 * POST /api/lab/g3-poc
 *
 * 将旧的“前端 Mock G3”替换为“后端 Java G3”：
 * 1) 创建 G3 Job（POST /v1/g3/jobs）
 * 2) 转发日志 SSE（GET /v1/g3/jobs/{id}/logs）
 */
export async function POST(req: NextRequest) {
  let body: Record<string, unknown> = {};
  try {
    body = await req.json();
  } catch {
    // ignore
  }

  const requirement = typeof body.requirement === 'string' ? body.requirement : '';

  if (!requirement || requirement.trim().length < 10) {
    return buildSingleErrorSseResponse('requirement 不能为空且至少 10 个字符', 400);
  }

  const authorization = req.headers.get('Authorization');

  try {
    // 1) 创建 Job
    const createResp = await fetch(`${BACKEND_API_URL}/v1/g3/jobs`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(authorization ? { Authorization: authorization } : {}),
      },
      body: JSON.stringify({
        requirement,
        // 允许透传可选上下文（用于调试 Blueprint）
        appSpecId: body.appSpecId,
        templateId: body.templateId,
        maxRounds: body.maxRounds,
      }),
    });

    if (!createResp.ok) {
      const errorText = await createResp.text();
      return buildSingleErrorSseResponse(`创建 G3 任务失败: HTTP ${createResp.status} - ${errorText}`, createResp.status);
    }

    const createJson = (await createResp.json()) as {
      success?: boolean;
      message?: string;
      data?: { jobId?: string };
    };

    const jobId = createJson?.data?.jobId;
    if (!jobId) {
      return buildSingleErrorSseResponse(`创建 G3 任务失败: jobId 为空（message=${createJson?.message || 'unknown'}）`, 500);
    }

    // 2) 转发日志 SSE
    const logsResp = await fetch(`${BACKEND_API_URL}/v1/g3/jobs/${jobId}/logs`, {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...(authorization ? { Authorization: authorization } : {}),
      },
    });

    if (!logsResp.ok || !logsResp.body) {
      const errorText = await logsResp.text().catch(() => '');
      return buildSingleErrorSseResponse(
        `订阅日志流失败: HTTP ${logsResp.status} - ${errorText || 'no body'}`,
        logsResp.status || 500
      );
    }

    return new NextResponse(logsResp.body, {
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
      },
    });
  } catch (error) {
    const msg = error instanceof Error ? error.message : String(error);
    return buildSingleErrorSseResponse(`G3 代理异常: ${msg}`, 500);
  }
}
