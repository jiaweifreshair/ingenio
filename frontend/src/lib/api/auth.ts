/**
 * 认证API客户端
 * 与后端AuthController交互
 *
 * 功能：
 * - 微信扫码登录（生成二维码、轮询状态）
 * - 手机号登录（待实现）
 * - 用户注册
 * - 密码重置（待实现）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */

import { setToken, clearToken, getToken } from '@/lib/auth/token';
import { get, post } from '@/lib/api/client';

/**
 * 使用Mock数据进行开发
 * 后端API开发完成后，将此标志设置为false
 */
const USE_MOCK_DATA = false; // Phase 3：对接真实后端API

/**
 * 微信二维码响应
 */
export interface WxQrcodeResponse {
  /** 二维码图片URL */
  qrcodeUrl: string;
  /** 场景值（用于轮询） */
  sceneStr: string;
  /** 过期时间（秒） */
  expiresIn: number;
}

/**
 * 扫码状态类型
 */
export type ScanStatus = 'pending' | 'scanned' | 'confirmed' | 'expired';

/**
 * 扫码状态响应
 */
export interface ScanStatusResponse {
  /** 扫码状态 */
  status: ScanStatus;
  /** 确认后返回的Token */
  token?: string;
  /** 确认后返回的用户信息 */
  userInfo?: {
    id: string;
    username: string;
    email: string;
    avatar?: string;
  };
}

/**
 * 登录响应
 */
export interface LoginResponse {
  /** JWT Token */
  token: string;
  /** 用户ID */
  userId: string;
  /** 用户名 */
  username: string;
  /** 邮箱 */
  email: string;
  /** 角色 */
  role: string;
  /** Token过期时间（秒） */
  expiresIn: number;
}

// ==================== Mock数据（开发阶段使用） ====================

/**
 * Mock二维码URL
 */
const MOCK_QRCODE_URL =
  'https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=https://ingenio.ai/wechat-login?scene=mock-scene-123';

/**
 * Mock扫码状态（模拟扫码流程）
 * 状态变化：pending(5秒) → scanned(3秒) → confirmed
 */
let mockScanStartTime: number | null = null;
let mockSceneStr: string | null = null;

/**
 * 生成Mock扫码状态
 */
function getMockScanStatus(scene: string): ScanStatusResponse {
  // 如果场景值不匹配，返回过期状态
  if (scene !== mockSceneStr) {
    return { status: 'expired' };
  }

  if (!mockScanStartTime) {
    mockScanStartTime = Date.now();
  }

  const elapsed = (Date.now() - mockScanStartTime) / 1000; // 秒

  // 模拟扫码流程：
  // 0-5秒: pending
  // 5-8秒: scanned
  // 8秒以上: confirmed
  if (elapsed < 5) {
    return { status: 'pending' };
  } else if (elapsed < 8) {
    return { status: 'scanned' };
  } else {
    // 返回Mock Token和用户信息
    const mockToken = 'mock-jwt-token-' + Date.now();
    setToken(mockToken); // 存储Token

    return {
      status: 'confirmed',
      token: mockToken,
      userInfo: {
        id: 'mock-user-001',
        username: 'mock_user',
        email: 'mock@ingenio.ai',
        avatar: 'https://i.pravatar.cc/150?img=1',
      },
    };
  }
}

// ==================== 微信登录API ====================

/**
 * 生成微信登录二维码
 *
 * 接口契约：
 * GET /v1/auth/wechat/qrcode
 */
export async function generateWechatQrcode(): Promise<WxQrcodeResponse> {
  if (USE_MOCK_DATA) {
    // Mock数据：模拟API延迟
    await new Promise((resolve) => setTimeout(resolve, 800));

    // 重置Mock状态
    mockScanStartTime = null;
    mockSceneStr = 'mock-scene-' + Date.now();

    return {
      qrcodeUrl: MOCK_QRCODE_URL,
      sceneStr: mockSceneStr,
      expiresIn: 1800, // 30分钟
    };
  }

  // 真实API调用 - 使用API client
  const response = await get<WxQrcodeResponse>('/v1/auth/wechat/qrcode');
  if (!response.data) {
    throw new Error('获取微信二维码失败');
  }
  return response.data;
}

/**
 * 检查微信扫码状态（轮询调用）
 *
 * 接口契约：
 * GET /v1/auth/wechat/check-scan?sceneStr=xxx
 */
export async function checkWechatScanStatus(
  sceneStr: string
): Promise<ScanStatusResponse> {
  if (USE_MOCK_DATA) {
    // Mock数据：模拟API延迟
    await new Promise((resolve) => setTimeout(resolve, 200));

    return getMockScanStatus(sceneStr);
  }

  // 真实API调用 - 使用API client
  const response = await get<ScanStatusResponse>(
    `/v1/auth/wechat/check-scan?sceneStr=${encodeURIComponent(sceneStr)}`
  );

  if (!response.data) {
    throw new Error('检查扫码状态失败');
  }

  // 如果已确认，存储Token
  if (response.data.status === 'confirmed' && response.data.token) {
    setToken(response.data.token);
  }

  return response.data;
}

// ==================== 手机号登录API（待实现） ====================

/**
 * 发送验证码
 *
 * 接口契约：
 * POST /v1/auth/send-code
 */
export async function sendVerificationCode(phone: string): Promise<void> {
  if (USE_MOCK_DATA) {
    throw new Error('手机号登录功能即将上线，敬请期待');
  }

  await post<void>('/v1/auth/send-code', { phone });
}

/**
 * 手机号登录
 *
 * 接口契约：
 * POST /v1/auth/login-phone
 */
export async function loginWithPhone(
  phone: string,
  code: string,
  inviteCode?: string
): Promise<LoginResponse> {
  if (USE_MOCK_DATA) {
    throw new Error('手机号登录功能即将上线，敬请期待');
  }

  const response = await post<LoginResponse>('/v1/auth/login-phone', { phone, code, inviteCode });

  if (!response.data) {
    throw new Error('手机号登录失败');
  }

  // 存储Token
  setToken(response.data.token);

  return response.data;
}

// ==================== 用户名/邮箱登录API ====================

/**
 * 用户名/邮箱登录
 *
 * 接口契约：
 * POST /v1/auth/login
 */

/**
 * 规范化登录错误文案
 *
 * 是什么：
 * - 将后端返回的原始错误信息转换为更贴近业务语义的提示文案
 *
 * 做什么：
 * - 识别AI配置类错误（例如AI API Key未配置/无效），统一提示为「AI 服务配置异常」
 * - 对普通登录错误（如用户名或密码错误）保持原样透传
 *
 * 为什么：
 * - 避免后端AI配置问题在前端被误解为「登录失败」
 * - 帮助用户快速定位问题属于配置层面，而非账号密码错误
 */
function normalizeLoginErrorMessage(message?: string, errorMessage?: string): string {
  const raw = message || errorMessage || '';
  const lower = raw.toLowerCase();

  const keywords = [
    'api key',
    'api密钥',
    'ai api',
    'ai服务',
    '七牛云ai提供商不可用',
    '阿里云dashscope提供商不可用',
    'dashscope',
    'qiniu',
    'deepseek',
  ];

  const isConfigError = keywords.some(keyword => {
    const lowerKeyword = keyword.toLowerCase();
    return raw.includes(keyword) || lower.includes(lowerKeyword);
  });

  if (isConfigError) {
    return 'AI 服务配置异常：请检查后端 AI API Key（例如 QINIU_CLOUD_API_KEY / DEEPSEEK_API_KEY / DASHSCOPE_API_KEY），修复后再尝试登录。';
  }

  return raw || '登录失败';
}

export async function login(
  usernameOrEmail: string,
  password: string
): Promise<LoginResponse> {
  const response = await post<LoginResponse>('/v1/auth/login', { usernameOrEmail, password });

  if (!response.success) {
    throw new Error(normalizeLoginErrorMessage(response.message, response.error));
  }

  if (!response.data) {
    throw new Error(normalizeLoginErrorMessage(response.message, '登录失败：服务器未返回数据'));
  }

  // 存储Token
  setToken(response.data.token);

  return response.data;
}

/**
 * 登出
 *
 * 接口契约：
 * POST /v1/auth/logout
 */
export async function logout(): Promise<void> {
  try {
    await post<void>('/v1/auth/logout', {});
  } catch (e) {
    console.error('登出API失败:', e);
  } finally {
    // 无论API是否成功，都清除本地Token
    clearToken();
  }
}

// ==================== 邮箱验证码API（Phase 5.2） ====================

/**
 * 验证码类型
 */
export type VerificationType = 'REGISTER' | 'RESET_PASSWORD' | 'CHANGE_EMAIL';

/**
 * 发送邮箱验证码
 *
 * 接口契约：
 * POST /v1/auth/verification-code/send
 */
export async function sendEmailVerificationCode(
  email: string,
  type: VerificationType
): Promise<void> {
  await post<void>('/v1/auth/verification-code/send', { email, type });
}

/**
 * 验证邮箱验证码
 *
 * 接口契约：
 * POST /v1/auth/verification-code/verify
 */
export async function verifyEmailCode(
  email: string,
  code: string,
  type: VerificationType
): Promise<void> {
  await post<void>('/v1/auth/verification-code/verify', { email, code, type });
}

// ==================== 用户注册API ====================

/**
 * 用户注册
 *
 * 接口契约：
 * POST /v1/auth/register
 */
export async function register(
  username: string,
  email: string,
  password: string
): Promise<LoginResponse> {
  const response = await post<LoginResponse>('/v1/auth/register', { username, email, password });

  if (!response.data) {
    throw new Error('注册失败');
  }

  // 存储Token
  setToken(response.data.token);

  return response.data;
}

// ==================== OAuth登录API ====================

/**
 * Google OAuth登录
 *
 * 接口契约：
 * POST /v1/auth/oauth/google/callback
 */
export async function loginWithGoogle(code: string): Promise<LoginResponse> {
  const response = await post<LoginResponse>('/v1/auth/oauth/google/callback', { code });

  if (!response.data) {
    throw new Error('Google登录失败');
  }

  // 存储Token
  setToken(response.data.token);

  return response.data;
}

/**
 * GitHub OAuth登录
 *
 * 接口契约：
 * POST /v1/auth/oauth/github/callback
 */
export async function loginWithGitHub(code: string): Promise<LoginResponse> {
  const response = await post<LoginResponse>('/v1/auth/oauth/github/callback', { code });

  if (!response.data) {
    throw new Error('GitHub登录失败');
  }

  // 存储Token
  setToken(response.data.token);

  return response.data;
}

// ==================== Token刷新API ====================

/**
 * Token刷新响应
 */
export interface TokenRefreshResponse {
  /** 刷新后的Token */
  token: string;
  /** Token类型 */
  tokenType: string;
  /** 剩余有效时间（秒） */
  expiresIn: number;
  /** 刷新时间戳 */
  refreshedAt: number;
}

/**
 * 刷新Token
 * 延长用户登录状态
 *
 * 接口契约（后端）：POST /v1/auth/refresh
 */
export async function refreshToken(): Promise<TokenRefreshResponse> {
  // 从localStorage获取当前Token
  const currentToken = getToken();

  if (!currentToken) {
    throw new Error('未找到Token，无法刷新');
  }

  // 使用client.ts处理请求，注意：client.ts会自动添加Authorization header
  // 但如果需要传递特殊header，可能需要client支持或直接使用fetch
  // 这里假设client会自动带上token
  const response = await post<TokenRefreshResponse>('/v1/auth/refresh', {});

  if (!response.data) {
    throw new Error('Token刷新失败');
  }

  // 更新本地存储的Token
  setToken(response.data.token);

  return response.data;
}
