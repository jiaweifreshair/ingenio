/**
 * API客户端
 * 秒构AI后端API调用封装
 *
 * 功能：
 * - 统一的HTTP请求封装
 * - 自动添加Authorization header
 * - 401错误自动跳转登录
 * - 完善的错误处理机制
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import { getToken, clearToken } from '@/lib/auth/token';
import { getApiBaseUrl } from './base-url';
import { normalizeApiResponse } from './response';
import { generateTraceId } from './trace-id';

/**
 * 默认指向本地Java后端 (8080/api) 确保不显式配置也能访问真实API
 * 注意：后端配置了 context-path: /api，所以需要包含此前缀
 */
const API_BASE_URL = getApiBaseUrl();

/**
 * API响应类型
 */
export interface APIResponse<T> {
  /** 是否成功 */
  success: boolean;
  /** 错误码（兼容后端Result结构） */
  code?: string | number;
  /** 数据 */
  data?: T;
  /** 成功消息 */
  message?: string;
  /** 错误信息 */
  error?: string;
  /** 时间戳（后端Result返回） */
  timestamp?: number;
  /** 元数据 */
  metadata?: {
    requestId: string;
    timestamp: string;
    latencyMs: number;
  };
}

/**
 * API错误类
 */
export class APIError extends Error {
  constructor(
    message: string,
    public statusCode?: number,
    public response?: unknown
  ) {
    super(message);
    this.name = "APIError";
  }
}

/**
 * 规范化网络错误提示
 *
 * 是什么：统一处理浏览器/Node 返回的网络错误信息。
 * 做什么：把“Failed to fetch”等英文错误转换为更易懂的中文提示。
 * 为什么：避免用户看到生硬的原始错误文本，提升诊断可读性。
 */
function normalizeNetworkErrorMessage(message: string): string {
  const normalized = message.toLowerCase();

  if (
    normalized.includes('failed to fetch') ||
    normalized.includes('networkerror') ||
    normalized.includes('econnrefused')
  ) {
    return '网络连接失败，请确认后端服务已启动（http://localhost:8080）';
  }

  if (normalized.includes('timeout')) {
    return '网络请求超时，请稍后重试';
  }

  return `网络请求失败: ${message}`;
}

/**
 * 发起API请求
 *
 * @param endpoint - API端点
 * @param options - 请求选项
 * @returns API响应
 */
async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<APIResponse<T>> {
  const url = `${API_BASE_URL}${endpoint}`;
  const token = getToken();
  const traceId = generateTraceId();

  // 构建headers（使用Record类型避免索引错误）
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    // 使用小写 key，便于与 Headers API 的规范化行为对齐（避免重复 header）
    "x-trace-id": traceId,
  };

  // 如果存在Token，添加Authorization header
  // SaToken不需要Bearer前缀，直接使用token值
  if (token) {
    headers['Authorization'] = token;
  }

  // 合并自定义headers
  if (options.headers) {
    const customHeaders = new Headers(options.headers);
    customHeaders.forEach((value, key) => {
      headers[key] = value;
    });
  }

  try {
    const response = await fetch(url, {
      ...options,
      headers,
      credentials: 'include', // 支持Cookie
    });

    // 处理认证相关错误（401未授权、502网关错误等）
    if (response.status === 401 || response.status === 502) {
      clearToken();

      // 只在浏览器环境跳转
      if (typeof window !== 'undefined') {
        window.location.href = '/login';
      }

      throw new APIError(
        response.status === 401 ? '未授权，请重新登录' : '服务暂时不可用，请重新登录',
        response.status
      );
    }

    // 检查Content-Type是否为JSON
    const contentType = response.headers.get("content-type");
    const isJson = contentType && contentType.includes("application/json");

    if (!isJson) {
      // 如果不是JSON响应，读取文本内容
      const text = await response.text();
      const preview = text.length > 200 ? text.substring(0, 200) + "..." : text;

      // 检查响应内容是否包含token无效/401错误（后端可能返回text/plain但内容是JSON）
      if (text.includes('"code": 401') || text.includes('"code":401') || text.includes('token 无效')) {
        clearToken();
        if (typeof window !== 'undefined') {
          window.location.href = '/login';
        }
        throw new APIError('登录已过期，请重新登录', 401);
      }

      // 检查是否是HTML错误页面
      if (text.startsWith("<!DOCTYPE") || text.startsWith("<html")) {
        throw new APIError(
          `后端服务返回了HTML页面而不是JSON响应。这通常意味着：\n` +
          `1. 后端服务可能未运行（请检查 http://localhost:8080）\n` +
          `2. API端点路径可能不正确\n` +
          `3. 网络代理配置可能有问题\n\n` +
          `HTTP状态码: ${response.status}`,
          response.status,
          { rawResponse: preview }
        );
      }

      throw new APIError(
        `后端服务返回了非JSON响应 (Content-Type: ${contentType || "unknown"})\n` +
        `HTTP状态码: ${response.status}\n` +
        `响应预览: ${preview}`,
        response.status,
        { rawResponse: preview }
      );
    }

    // 解析JSON响应
    const raw = await response.json();

    if (!response.ok) {
      throw new APIError(
        (raw as { error?: string }).error || `请求失败 (HTTP ${response.status})`,
        response.status,
        raw
      );
    }

    // 统一兼容后端 Result<T> 与前端 APIResponse<T>
    const normalized = normalizeApiResponse<T>(raw);

    if (!normalized.success) {
      throw new APIError(
        normalized.error || normalized.message || `请求失败 (HTTP ${response.status})`,
        response.status,
        raw
      );
    }

    // 如果已是标准APIResponse结构，直接返回，避免破坏调用方/单测的对象一致性
    if (typeof (raw as { success?: unknown }).success === 'boolean') {
      return raw as APIResponse<T>;
    }

    // 后端Result结构 → 转换为前端APIResponse结构
    return {
      success: true,
      code: normalized.code,
      data: normalized.data,
      message: normalized.message,
      error: normalized.error,
      timestamp: normalized.timestamp,
      metadata: normalized.metadata as APIResponse<T>['metadata'],
    };
  } catch (error) {
    if (error instanceof APIError) {
      throw error;
    }

    // 如果是AbortError (请求取消/超时)，直接抛出，由调用方处理
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }

    // 处理网络错误或JSON解析错误
    if (error instanceof Error) {
      // 检查是否是JSON解析错误
      if (error.message.includes("JSON") || error.message.includes("Unexpected token")) {
        throw new APIError(
          "后端服务返回了无效的JSON响应。请确认：\n" +
          "1. 后端服务正在运行 (http://localhost:8080)\n" +
          "2. 后端服务健康状态正常\n" +
          "3. API路径配置正确",
          undefined,
          { originalError: error.message }
        );
      }

      const normalizedMessage = normalizeNetworkErrorMessage(error.message);
      throw new APIError(
        normalizedMessage,
        undefined,
        { originalError: error.message }
      );
    }

    throw new APIError("发生未知错误");
  }
}

/**
 * GET请求
 */
export async function get<T>(endpoint: string): Promise<APIResponse<T>> {
  return request<T>(endpoint, { method: "GET" });
}

/**
 * POST请求
 *
 * @param endpoint - API端点
 * @param data - 请求数据
 * @param options - 可选的请求选项（如自定义headers）
 * @returns API响应
 */
export async function post<T>(
  endpoint: string,
  data: unknown,
  options?: RequestInit
): Promise<APIResponse<T>> {
  return request<T>(endpoint, {
    ...options,
    method: "POST",
    body: JSON.stringify(data),
  });
}

/**
 * PUT请求
 *
 * @param endpoint - API端点
 * @param data - 请求数据
 * @param options - 可选的请求选项（如自定义headers）
 * @returns API响应
 */
export async function put<T>(
  endpoint: string,
  data: unknown,
  options?: RequestInit
): Promise<APIResponse<T>> {
  return request<T>(endpoint, {
    ...options,
    method: "PUT",
    body: JSON.stringify(data),
  });
}

/**
 * DELETE请求
 */
export async function del<T>(endpoint: string): Promise<APIResponse<T>> {
  return request<T>(endpoint, { method: "DELETE" });
}
