/**
 * Style API Client - 7种设计风格预览生成API客户端
 *
 * Phase 7实现 - SuperDesign 7风格预览API前端集成
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-15
 */

import type {
  Generate7StylesRequest,
  Generate7StylesResponse,
} from '@/types/design-style';
import { getApiBaseUrl } from '@/lib/api/base-url';
import { normalizeApiResponse } from '@/lib/api/response';
import { generateTraceId } from '@/lib/api/trace-id';

/**
 * API基础URL配置
 * 统一使用 NEXT_PUBLIC_API_BASE_URL，包含 /api 前缀
 * 与 client.ts 保持一致
 */
const API_BASE_URL = getApiBaseUrl();

/**
 * 生成7种设计风格的快速预览
 *
 * 调用后端 /v1/styles/generate-previews 接口
 * 返回7种风格的HTML预览内容
 *
 * @param request 7风格生成请求
 * @returns 7种风格预览响应
 *
 * @example
 * const response = await generate7StylePreviews({
 *   userRequirement: "创建一个民宿预订平台",
 *   appType: "web",
 *   targetPlatform: "responsive",
 *   useAICustomization: false
 * });
 *
 * if (response.success) {
 *   console.log(`生成${response.styles.length}种风格预览`);
 *   response.styles.forEach(style => {
 *     console.log(`${style.style}: ${style.generationTime}ms`);
 *   });
 * }
 */
export async function generate7StylePreviews(
  request: Generate7StylesRequest
): Promise<Generate7StylesResponse> {
  console.log(
    '[V2.0 Style API] 调用7风格预览生成API:',
    {
      userRequirement: request.userRequirement,
      appType: request.appType,
      targetPlatform: request.targetPlatform,
      useAI: request.useAICustomization,
    }
  );

  try {
    const startTime = Date.now();

    // 调用后端API
    const response = await fetch(`${API_BASE_URL}/v1/styles/generate-previews`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-trace-id': generateTraceId(),
      },
      body: JSON.stringify(request),
    });

    // 检查HTTP状态
    if (!response.ok) {
      throw new Error(
        `API请求失败: ${response.status} ${response.statusText}`
      );
    }

    // 解析响应（后端可能返回 Result<T> 或 APIResponse<T>）
    const raw = await response.json();
    const normalized = normalizeApiResponse<Generate7StylesResponse>(raw);

    if (!normalized.success) {
      throw new Error(normalized.message || normalized.error || '7风格预览生成失败');
    }

    const data: Generate7StylesResponse =
      (normalized.data as Generate7StylesResponse) ||
      ((raw as { data?: unknown }).data as Generate7StylesResponse) ||
      (raw as Generate7StylesResponse);

    const totalTime = Date.now() - startTime;

    console.log('[V2.0 Style API] 7风格预览生成完成:', {
      success: data.success,
      stylesCount: data.styles?.length || 0,
      backendTime: data.totalGenerationTime,
      frontendTime: totalTime,
      warnings: data.warnings,
    });

    // 性能警告
    if (data.totalGenerationTime && data.totalGenerationTime > 15000) {
      console.warn(
        `[V2.0 Style API] 性能警告: 7风格生成耗时${data.totalGenerationTime}ms，超过15秒目标`
      );
    }

    return data;
  } catch (error) {
    console.error('[V2.0 Style API] 7风格预览生成失败:', error);

    // 返回失败响应
    return {
      success: false,
      styles: [],
      totalGenerationTime: 0,
      error: error instanceof Error ? error.message : '未知错误',
      warnings: ['API调用失败，请检查网络连接或后端服务状态'],
    };
  }
}

/**
 * 健康检查 - 测试Style API服务是否正常
 *
 * @returns 服务状态
 */
export async function checkStyleServiceHealth(): Promise<{
  healthy: boolean;
  message: string;
}> {
  try {
    const response = await fetch(`${API_BASE_URL}/v1/styles/health`, {
      headers: {
        'x-trace-id': generateTraceId(),
      },
    });

    if (!response.ok) {
      return {
        healthy: false,
        message: `Health check failed: ${response.status}`,
      };
    }

    const raw = await response.json();
    const normalized = normalizeApiResponse<string>(raw);

    if (!normalized.success) {
      return {
        healthy: false,
        message: normalized.message || normalized.error || 'Health check failed',
      };
    }

    const message =
      (normalized.data as string) ||
      ((raw as { data?: unknown }).data as string) ||
      String(raw);

    return {
      healthy: true,
      message,
    };
  } catch (error) {
    return {
      healthy: false,
      message: error instanceof Error ? error.message : 'Unknown error',
    };
  }
}
