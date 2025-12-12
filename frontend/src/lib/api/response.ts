/**
 * API响应规范化工具
 *
 * 目的：兼容后端 Spring Boot `Result<T>` 与前端/代理层 `APIResponse<T>` 两种返回结构，
 * 避免因为 `success` / `code` 字段不一致导致的误判。
 *
 * 后端 Result<T> 结构示例：
 * {
 *   code: "0000" | "1000" | ...,
 *   message: "操作成功" | "系统错误" | ...,
 *   data: T,
 *   timestamp: number
 * }
 *
 * 前端/BFF APIResponse<T> 结构示例：
 * {
 *   success: boolean,
 *   data?: T,
 *   message?: string,
 *   error?: string,
 *   metadata?: Record<string, unknown>
 * }
 */

/**
 * 规范化后的响应结构
 */
export interface NormalizedApiResponse<T> {
  /** 是否成功 */
  success: boolean;
  /** 原始错误码（兼容 string/number） */
  code?: string | number;
  /** 后端/代理返回的消息 */
  message?: string;
  /** 错误信息（部分代理层使用 error 字段） */
  error?: string;
  /** 业务数据 */
  data?: T;
  /** 时间戳（如后端 Result 返回） */
  timestamp?: number;
  /** 代理层元数据（如果有） */
  metadata?: Record<string, unknown>;
}

type AnyRecord = Record<string, unknown>;

function isRecord(value: unknown): value is AnyRecord {
  return typeof value === 'object' && value !== null;
}

/**
 * 将不同形态的API响应统一为 NormalizedApiResponse<T>
 *
 * 规则：
 * - 优先使用显式 success 字段
 * - 若无 success，则按 code 是否为 "0000"/0 判断
 */
export function normalizeApiResponse<T>(raw: unknown): NormalizedApiResponse<T> {
  if (!isRecord(raw)) {
    return { success: false };
  }

  const successValue = raw.success;
  const codeValue = raw.code;
  const messageValue = raw.message;
  const errorValue = raw.error;
  const dataValue = raw.data;
  const timestampValue = raw.timestamp;
  const metadataValue = raw.metadata;

  let success = false;
  if (typeof successValue === 'boolean') {
    success = successValue;
  } else if (typeof codeValue === 'string') {
    success = codeValue === '0000' || codeValue === '0';
  } else if (typeof codeValue === 'number') {
    // 兼容少量服务使用 0/200 表示成功的情况
    success = codeValue === 0 || codeValue === 200;
  }

  return {
    success,
    code:
      typeof codeValue === 'string' || typeof codeValue === 'number'
        ? codeValue
        : undefined,
    message: typeof messageValue === 'string' ? messageValue : undefined,
    error: typeof errorValue === 'string' ? errorValue : undefined,
    data: dataValue as T | undefined,
    timestamp: typeof timestampValue === 'number' ? timestampValue : undefined,
    metadata: isRecord(metadataValue) ? metadataValue : undefined,
  };
}

