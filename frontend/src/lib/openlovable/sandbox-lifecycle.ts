/**
 * OpenLovable 沙箱生命周期工具（可单测）
 *
 * 目标：
 * - 统一管理“创建/复用/失效重建/预览URL同步”的策略
 * - 通过可测试的纯函数与最小网络封装，验证沙箱创建时机与时效性
 *
 * 说明：
 * - 该模块只负责“沙箱是否可用”的判断与恢复策略，不绑定具体 UI/Hook
 * - 通过 `maxAgeMs` 提供“最大复用时长”兜底，避免复用临近过期的沙箱
 */

/**
 * 沙箱信息（前端侧最小闭环字段）
 */
export interface SandboxInfo {
  /** 上游是否标记为成功（兼容字段，默认 true） */
  success: boolean;
  /** 沙箱ID */
  sandboxId: string;
  /** 预览URL */
  url: string;
  /** 沙箱提供商（默认 e2b） */
  provider: string;
  /** 上游返回的消息（可选） */
  message: string;
  /** 前端记录的创建时间戳（毫秒） */
  createdAt: number;
}

/**
 * 后端统一 Result 封装（前端侧最小字段集）
 */
export interface BackendResult<T> {
  success: boolean;
  data?: T;
  message?: string;
  error?: string;
}

/**
 * 默认最大复用时长（25分钟）
 * 说明：通常上游沙箱有 30 分钟左右的回收策略，这里提前 5 分钟主动“换新”
 */
export const DEFAULT_SANDBOX_MAX_AGE_MS = 25 * 60 * 1000;

/**
 * 验证URL是否合法
 * 防止API返回的无效URL（如包含中文的测试消息）导致前端崩溃
 */
export function isValidUrl(urlString: string | null | undefined): boolean {
  if (!urlString || typeof urlString !== 'string') {
    return false;
  }
  // 检查是否包含中文字符（明显的无效URL）
  if (/[\u4e00-\u9fa5]/.test(urlString)) {
    return false;
  }
  try {
    const url = new URL(urlString);
    return url.protocol === 'http:' || url.protocol === 'https:';
  } catch {
    return false;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function getStringField(record: Record<string, unknown>, key: string): string | undefined {
  const value = record[key];
  return typeof value === 'string' ? value : undefined;
}

/**
 * 判断上游是否返回“沙箱不存在/已销毁”
 * 说明：不同提供商/版本的返回字段不完全一致，这里做容错解析。
 */
export function isSandboxNotFound(payload: Record<string, unknown>): boolean {
  const statusText = (getStringField(payload, 'status') || getStringField(payload, 'state') || '').toLowerCase();
  const messageText = (getStringField(payload, 'message') || getStringField(payload, 'error') || '').toLowerCase();
  const combined = `${statusText} ${messageText}`;

  if (typeof payload.success === 'boolean' && payload.success === false) {
    return true;
  }

  return combined.includes('sandbox not found') || combined.includes('not found') || combined.includes('not_found');
}

/**
 * 从上游状态响应中提取 sandboxId
 *
 * 兼容说明：
 * - open-lovable-cn 的 /api/sandbox-status 返回结构为：
 *   { success, active, healthy, sandboxData: { sandboxId, url, ... }, message }
 * - 旧版本/其它实现可能直接返回 { sandboxId, url, ... }
 */
export function extractSandboxId(payload: Record<string, unknown>): string | undefined {
  const direct = getStringField(payload, 'sandboxId');
  if (direct) return direct;

  const sandboxData = payload.sandboxData;
  if (isRecord(sandboxData)) {
    return getStringField(sandboxData, 'sandboxId');
  }

  return undefined;
}

/**
 * 从上游状态响应中提取预览 URL
 */
export function extractSandboxUrl(payload: Record<string, unknown>): string | undefined {
  const direct = getStringField(payload, 'url') || getStringField(payload, 'previewUrl');
  if (direct) return direct;

  const sandboxData = payload.sandboxData;
  if (isRecord(sandboxData)) {
    return getStringField(sandboxData, 'url') || getStringField(sandboxData, 'previewUrl');
  }

  return undefined;
}

/**
 * 查询指定 sandbox 的状态
 */
export async function requestOpenLovableSandboxStatus(
  apiBaseUrl: string,
  sandboxId: string,
  token: string | null
): Promise<BackendResult<Record<string, unknown>>> {
  try {
    const url = `${apiBaseUrl}/v1/openlovable/sandbox/status?sandboxId=${encodeURIComponent(sandboxId)}`;
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        ...(token ? { Authorization: token } : {}),
      },
      cache: 'no-store',
    });

    if (!response.ok) {
      return { success: false, error: `查询沙箱状态失败: HTTP ${response.status}` };
    }

    const json: unknown = await response.json();
    if (!isRecord(json) || typeof json.success !== 'boolean') {
      return { success: false, error: '查询沙箱状态响应格式异常' };
    }

    if (!json.success) {
      return {
        success: false,
        error: typeof json.error === 'string' ? json.error : typeof json.message === 'string' ? json.message : '查询沙箱状态失败',
      };
    }

    const payload = isRecord(json.data) ? json.data : null;
    if (!payload) {
      return { success: false, error: '查询沙箱状态返回空数据' };
    }

    return { success: true, data: payload };
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : '查询沙箱状态异常' };
  }
}

function parseSandboxInfo(payload: unknown, now: number): SandboxInfo {
  if (!isRecord(payload)) {
    throw new Error('沙箱创建响应格式异常');
  }

  const sandboxId = getStringField(payload, 'sandboxId');
  const url = getStringField(payload, 'url');
  if (!sandboxId || !url) {
    throw new Error('沙箱创建响应缺少 sandboxId 或 url');
  }

  return {
    success: typeof payload.success === 'boolean' ? payload.success : true,
    sandboxId,
    url,
    provider: getStringField(payload, 'provider') || 'e2b',
    message: getStringField(payload, 'message') || '',
    createdAt: now,
  };
}

/**
 * 创建新沙箱
 */
export async function requestOpenLovableCreateSandbox(
  apiBaseUrl: string,
  token: string | null,
  now: number = Date.now()
): Promise<SandboxInfo> {
  const response = await fetch(`${apiBaseUrl}/v1/openlovable/sandbox/create`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: token } : {}),
    },
    cache: 'no-store',
  });

  if (!response.ok) {
    throw new Error(`创建沙箱失败: ${response.statusText}`);
  }

  const json: unknown = await response.json();
  if (!isRecord(json) || typeof json.success !== 'boolean') {
    throw new Error('创建沙箱响应格式异常');
  }

  if (!json.success) {
    throw new Error(typeof json.error === 'string' ? json.error : typeof json.message === 'string' ? json.message : '创建沙箱失败');
  }

  return parseSandboxInfo(json.data, now);
}

/**
 * ensureSandboxAvailable 的动作类型
 */
export type EnsureSandboxAction = 'created' | 'reused' | 'recreated';

/**
 * ensureSandboxAvailable 的原因类型（用于日志/验证）
 */
export type EnsureSandboxReason = 'none' | 'expired' | 'status_failed' | 'not_found';

export interface EnsureSandboxOptions {
  /** 最大复用时长（毫秒），默认 DEFAULT_SANDBOX_MAX_AGE_MS */
  maxAgeMs?: number;
  /** 当前时间（毫秒），便于测试注入 */
  now?: number;
}

export interface EnsureSandboxResult {
  sandbox: SandboxInfo;
  action: EnsureSandboxAction;
  reason: EnsureSandboxReason;
  urlUpdated: boolean;
}

/**
 * 确保“可用的沙箱”存在：
 * - 无沙箱：创建
 * - 有沙箱：先按 maxAgeMs 判断是否过期；未过期则查询状态
 * - 状态失败/NotFound：重建
 * - 状态成功且返回了新 URL：同步更新
 */
export async function ensureSandboxAvailable(
  apiBaseUrl: string,
  currentSandbox: SandboxInfo | null,
  token: string | null,
  options: EnsureSandboxOptions = {}
): Promise<EnsureSandboxResult> {
  const now = options.now ?? Date.now();
  const maxAgeMs = options.maxAgeMs ?? DEFAULT_SANDBOX_MAX_AGE_MS;

  if (!currentSandbox) {
    const created = await requestOpenLovableCreateSandbox(apiBaseUrl, token, now);
    if (!isValidUrl(created.url)) {
      throw new Error(`沙箱URL无效: ${created.url || '空'}`);
    }
    return { sandbox: created, action: 'created', reason: 'none', urlUpdated: false };
  }

  const ageMs = now - currentSandbox.createdAt;
  if (ageMs > maxAgeMs) {
    const created = await requestOpenLovableCreateSandbox(apiBaseUrl, token, now);
    if (!isValidUrl(created.url)) {
      throw new Error(`沙箱URL无效: ${created.url || '空'}`);
    }
    return { sandbox: created, action: 'recreated', reason: 'expired', urlUpdated: false };
  }

  const statusResult = await requestOpenLovableSandboxStatus(apiBaseUrl, currentSandbox.sandboxId, token);
  if (!statusResult.success || !statusResult.data) {
    const created = await requestOpenLovableCreateSandbox(apiBaseUrl, token, now);
    if (!isValidUrl(created.url)) {
      throw new Error(`沙箱URL无效: ${created.url || '空'}`);
    }
    return { sandbox: created, action: 'recreated', reason: 'status_failed', urlUpdated: false };
  }

  if (isSandboxNotFound(statusResult.data)) {
    const created = await requestOpenLovableCreateSandbox(apiBaseUrl, token, now);
    if (!isValidUrl(created.url)) {
      throw new Error(`沙箱URL无效: ${created.url || '空'}`);
    }
    return { sandbox: created, action: 'recreated', reason: 'not_found', urlUpdated: false };
  }

  const latestSandboxId = extractSandboxId(statusResult.data);
  const latestUrl = extractSandboxUrl(statusResult.data);
  const nextSandboxId = latestSandboxId || currentSandbox.sandboxId;
  const nextUrl = latestUrl && isValidUrl(latestUrl) ? latestUrl : currentSandbox.url;
  const urlUpdated = nextUrl !== currentSandbox.url;
  const sandboxIdUpdated = nextSandboxId !== currentSandbox.sandboxId;

  return {
    sandbox: {
      ...currentSandbox,
      sandboxId: nextSandboxId,
      url: nextUrl,
      provider: getStringField(statusResult.data, 'provider') || currentSandbox.provider,
      message: getStringField(statusResult.data, 'message') || currentSandbox.message,
      createdAt: currentSandbox.createdAt,
    },
    action: sandboxIdUpdated ? 'recreated' : 'reused',
    reason: sandboxIdUpdated ? 'not_found' : 'none',
    urlUpdated,
  };
}

/**
 * 仅给定 sandboxId 的场景（例如 apply/restart 前二次校验）：
 * - 状态可用：返回带 URL 的 SandboxInfo（createdAt 使用 now 作为“可用性确认时间”）
 * - 不可用：创建新沙箱并返回
 */
export async function ensureSandboxIdAvailable(
  apiBaseUrl: string,
  sandboxId: string,
  token: string | null,
  options: EnsureSandboxOptions = {}
): Promise<EnsureSandboxResult> {
  const now = options.now ?? Date.now();

  const statusResult = await requestOpenLovableSandboxStatus(apiBaseUrl, sandboxId, token);
  const statusPayload = statusResult.success && statusResult.data ? statusResult.data : null;
  const statusSandboxId = statusPayload ? extractSandboxId(statusPayload) : undefined;
  const sandboxIdMismatch = !!(statusSandboxId && statusSandboxId !== sandboxId);

  if (!statusResult.success || !statusResult.data || isSandboxNotFound(statusResult.data) || sandboxIdMismatch) {
    const created = await requestOpenLovableCreateSandbox(apiBaseUrl, token, now);
    if (!isValidUrl(created.url)) {
      throw new Error(`沙箱URL无效: ${created.url || '空'}`);
    }
    return {
      sandbox: created,
      action: 'recreated',
      reason: !statusResult.success ? 'status_failed' : 'not_found',
      urlUpdated: false,
    };
  }

  const latestUrl = extractSandboxUrl(statusResult.data) || '';
  const url = isValidUrl(latestUrl) ? latestUrl : '';

  return {
    sandbox: {
      success: true,
      sandboxId: statusSandboxId || sandboxId,
      url,
      provider: getStringField(statusResult.data, 'provider') || 'e2b',
      message: getStringField(statusResult.data, 'message') || '',
      createdAt: now,
    },
    action: 'reused',
    reason: 'none',
    urlUpdated: false,
  };
}
