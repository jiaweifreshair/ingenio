import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import { type GenerateResponse } from '@/lib/api/generate';

/**
 * 后端API基础URL
 */
const BACKEND_API_URL = process.env.BACKEND_API_URL || 'http://localhost:8080/api';

/**
 * 同步生成AppSpec API路由
 * POST /api/v1/generate/full
 *
 * 代理请求到后端Java服务的同步生成接口
 *
 * 修复说明：
 * - 后端返回的appSpecId是UUID对象，会被Jackson序列化为字符串
 * - 添加详细日志记录，便于调试
 * - 确保正确提取appSpecId字符串值
 * - 添加备用方案：如果appSpecId不存在，使用taskId
 */
export async function POST(request: NextRequest) {
  console.log('🚀 Generate API called - proxying to backend');

  try {
    const body = await request.json();
    console.log('📝 Request body:', JSON.stringify(body, null, 2));

    // 验证必需参数
    if (!body.userRequirement || body.userRequirement.trim().length < 10) {
      return NextResponse.json<APIResponse<GenerateResponse>>({
        success: false,
        error: '需求描述至少需要10个字符',
        metadata: {
          requestId: `req_${Date.now()}`,
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      }, { status: 400 });
    }

    // 构建后端API请求
    const backendRequest = {
      userRequirement: body.userRequirement,
      model: body.model || 'gemini-3-pro-preview',
      skipValidation: body.skipValidation || false,
      qualityThreshold: body.qualityThreshold || 70,
      generatePreview: body.generatePreview || false,
    };

    // 后端API路径：/api/v1/generate/full（注意：后端配置了context-path=/api）
    const backendUrl = `${BACKEND_API_URL}/v1/generate/full`;
    console.log(`📞 Proxying request to: ${backendUrl}`);
    console.log(`📤 Backend request:`, JSON.stringify(backendRequest, null, 2));

    // 发送请求到后端
    const startTime = Date.now();
    const response = await fetch(backendUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        // 添加认证头（如果需要）
        // 'Authorization': request.headers.get('Authorization') || '',
      },
      body: JSON.stringify(backendRequest),
    });

    const endTime = Date.now();
    const latency = endTime - startTime;
    console.log(`⏱️ Backend response time: ${latency}ms`);

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`❌ Backend API error: ${response.status} - ${errorText}`);

      return NextResponse.json<APIResponse<GenerateResponse>>({
        success: false,
        error: `后端服务错误 (${response.status}): ${errorText}`,
        metadata: {
          requestId: `req_${Date.now()}`,
          timestamp: new Date().toISOString(),
          latencyMs: latency,
        },
      }, { status: response.status });
    }

    const backendResponse = await response.json();
    console.log('✅ Backend API response received');
    console.log('📥 Raw backend response:', JSON.stringify(backendResponse, null, 2));

    // 提取后端响应数据
    const backendData = backendResponse.data || backendResponse;
    console.log('📦 Backend data:', JSON.stringify(backendData, null, 2));

    // 关键修复：确保appSpecId正确提取
    // 后端返回的appSpecId是UUID，Jackson会序列化为字符串
    let appSpecId = '';

    if (backendData.appSpecId) {
      // 如果是字符串，直接使用
      if (typeof backendData.appSpecId === 'string') {
        appSpecId = backendData.appSpecId.trim();
      } else if (typeof backendData.appSpecId === 'object') {
        // 如果是UUID对象，转换为字符串
        appSpecId = String(backendData.appSpecId).trim();
      }
    }

    // 备用方案：如果appSpecId仍然为空，尝试使用taskId或projectId
    if (!appSpecId) {
      console.warn('⚠️ appSpecId is empty, trying fallback...');

      if (backendData.taskId && typeof backendData.taskId === 'string') {
        appSpecId = backendData.taskId.trim();
        console.log('✅ Using taskId as appSpecId:', appSpecId);
      } else if (backendData.projectId) {
        appSpecId = String(backendData.projectId).trim();
        console.log('✅ Using projectId as appSpecId:', appSpecId);
      }
    }

    console.log('🔑 Final appSpecId:', appSpecId);

    // 如果appSpecId仍然为空，返回错误
    if (!appSpecId) {
      console.error('❌ Failed to extract appSpecId from backend response');
      console.error('❌ Backend data keys:', Object.keys(backendData));
      console.error('❌ Debug info:', {
        hasAppSpecId: !!backendData.appSpecId,
        hasTaskId: !!backendData.taskId,
        hasProjectId: !!backendData.projectId,
        dataKeys: Object.keys(backendData),
      });

      return NextResponse.json<APIResponse<GenerateResponse>>({
        success: false,
        error: '后端未返回有效的AppSpec ID，请联系技术支持',
        metadata: {
          requestId: `req_${Date.now()}`,
          timestamp: new Date().toISOString(),
          latencyMs: latency,
        },
      }, { status: 500 });
    }

    // 转换响应格式以匹配前端期望
    const generateResponse: GenerateResponse = {
      appSpecId: appSpecId, // 确保appSpecId是字符串
      projectId: backendData.projectId ? String(backendData.projectId) : undefined,
      planResult: backendData.planResult,
      validateResult: backendData.validateResult,
      isValid: backendData.isValid || false,
      qualityScore: backendData.qualityScore || 0,
      codeDownloadUrl: backendData.codeDownloadUrl,
      codeSummary: backendData.codeSummary,
      generatedFileList: backendData.generatedFileList,
      previewUrl: backendData.previewUrl,
      status: backendData.status || 'unknown',
      errorMessage: backendData.errorMessage,
      durationMs: backendData.durationMs || 0,
      generatedAt: backendData.generatedAt || new Date().toISOString(),
      tokenUsage: backendData.tokenUsage,
    };

    console.log('✅ Transformed response:', JSON.stringify(generateResponse, null, 2));

    return NextResponse.json<APIResponse<GenerateResponse>>({
      success: true,
      data: generateResponse,
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: latency,
      },
    });

  } catch (error) {
    console.error('❌ Generate API error:', error);
    console.error('❌ Error stack:', error instanceof Error ? error.stack : 'N/A');

    return NextResponse.json<APIResponse<GenerateResponse>>({
      success: false,
      error: error instanceof Error ? error.message : '生成失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}

/**
 * GET /api/v1/generate/full
 * 获取API状态
 */
export async function GET() {
  return NextResponse.json<APIResponse<{ status: string, backendUrl: string }>>({
    success: true,
    data: {
      status: 'API endpoint ready',
      backendUrl: BACKEND_API_URL
    },
    metadata: {
      requestId: `req_${Date.now()}`,
      timestamp: new Date().toISOString(),
      latencyMs: 0,
    },
  });
}
