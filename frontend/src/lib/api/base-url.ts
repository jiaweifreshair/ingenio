/**
 * API基准URL工具
 * 负责标准化 NEXT_PUBLIC_API_BASE_URL，避免出现 `/api/v1` 重复拼接导致的404。
 * 逻辑：裁剪空白、移除末尾斜杠、剔除尾部 `/v1`，并确保最终以 `/api` 结尾。
 */
const DEFAULT_API_BASE_URL = 'http://localhost:8080/api';

/**
 * 同源BFF默认基准URL
 * 当 NEXT_PUBLIC_API_BASE_URL 显式设置为空字符串时，前端改用 Next.js API 代理，以避免跨域或协议不一致问题
 */
const RELATIVE_API_BASE_URL = '/api';

/**
 * 获取规范化后的API基准地址
 * 用于所有前端直接访问后端的场景，确保不会出现 `/api/v1` 的重复拼接。
 */
export function getApiBaseUrl(): string {
  const rawValue = process.env.NEXT_PUBLIC_API_BASE_URL;

  // 显式未配置时，回落到本地后端地址，方便本地开发直连Java服务
  if (rawValue === undefined) {
    return DEFAULT_API_BASE_URL;
  }

  const raw = rawValue.trim();

  // 显式配置为空字符串时，按同源 /api 走 Next.js BFF 代理，避免浏览器直连跨域失败
  if (raw === '') {
    return RELATIVE_API_BASE_URL;
  }

  // 防御性检查：如果包含非法字符（如中文），回退到默认值并打印错误
  // 解决 "TypeError: Failed to construct 'URL': Invalid URL" 问题
  try {
    // 尝试构建 URL 对象来验证合法性
    // 注意：raw 可能不包含协议头（虽然不推荐），这里暂时只检查明显错误
    if (/[\u4e00-\u9fa5]/.test(raw)) {
       console.error(`[BaseURL] ❌ 检测到非法 Base URL 配置 (包含中文): "${raw}". 已自动回退到默认值: ${DEFAULT_API_BASE_URL}`);
       return DEFAULT_API_BASE_URL;
    }
    
    // 简单的 URL 格式验证
    if (raw.startsWith('http')) {
        new URL(raw);
    }
  } catch (e) {
    console.error(`[BaseURL] ❌ Base URL 配置无效: "${raw}". 错误: ${(e as Error).message}. 已自动回退到默认值: ${DEFAULT_API_BASE_URL}`);
    return DEFAULT_API_BASE_URL;
  }

  let normalized = raw.replace(/\/+$/, '');

  // 如果用户仍然配置为 /api/v1 或 /v1，统一截断到 /api，避免出现 /v1/v1。
  if (normalized.toLowerCase().endsWith('/api/v1')) {
    normalized = normalized.slice(0, -3);
  } else if (normalized.toLowerCase().endsWith('/v1')) {
    normalized = normalized.slice(0, -3);
  }

  // 如果缺少 context-path，则自动追加 /api。
  if (!normalized.toLowerCase().endsWith('/api')) {
    normalized = `${normalized}/api`;
  }

  return normalized;
}

/**
 * 构建完整的API地址
 * 说明：确保传入的endpoint带上前导 `/`，再拼接规范化后的基准地址。
 */
export function buildApiUrl(endpoint: string): string {
  const baseUrl = getApiBaseUrl();
  const normalizedEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
  return `${baseUrl}${normalizedEndpoint}`;
}
