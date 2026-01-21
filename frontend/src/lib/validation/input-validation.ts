/**
 * 前端输入验证工具
 *
 * 功能：
 * - 邮箱格式验证
 * - 密码强度验证
 * - 用户名格式验证
 *
 * @author Ingenio Team
 * @since 2026-01-18
 */

/**
 * 验证结果接口
 */
export interface ValidationResult {
  /** 是否有效 */
  valid: boolean;
  /** 错误消息 */
  error?: string;
}

/**
 * 验证邮箱格式
 *
 * @param email - 邮箱地址
 * @returns 验证结果
 */
export function validateEmail(email: string): ValidationResult {
  if (!email) {
    return { valid: false, error: '邮箱不能为空' };
  }

  // RFC 5322 简化版邮箱正则
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  if (!emailRegex.test(email)) {
    return { valid: false, error: '邮箱格式不正确' };
  }

  return { valid: true };
}

/**
 * 验证密码强度
 *
 * 要求：
 * - 至少8个字符
 * - 包含大写字母
 * - 包含小写字母
 * - 包含数字
 *
 * @param password - 密码
 * @returns 验证结果
 */
export function validatePassword(password: string): ValidationResult {
  if (!password) {
    return { valid: false, error: '密码不能为空' };
  }

  if (password.length < 8) {
    return { valid: false, error: '密码至少需要8个字符' };
  }

  if (!/[A-Z]/.test(password)) {
    return { valid: false, error: '密码必须包含至少一个大写字母' };
  }

  if (!/[a-z]/.test(password)) {
    return { valid: false, error: '密码必须包含至少一个小写字母' };
  }

  if (!/[0-9]/.test(password)) {
    return { valid: false, error: '密码必须包含至少一个数字' };
  }

  return { valid: true };
}

/**
 * 验证用户名格式
 *
 * 要求：
 * - 3-20个字符
 * - 只能包含字母、数字、下划线
 * - 必须以字母开头
 *
 * @param username - 用户名
 * @returns 验证结果
 */
export function validateUsername(username: string): ValidationResult {
  if (!username) {
    return { valid: false, error: '用户名不能为空' };
  }

  if (username.length < 3) {
    return { valid: false, error: '用户名至少需要3个字符' };
  }

  if (username.length > 20) {
    return { valid: false, error: '用户名最多20个字符' };
  }

  if (!/^[a-zA-Z]/.test(username)) {
    return { valid: false, error: '用户名必须以字母开头' };
  }

  if (!/^[a-zA-Z0-9_]+$/.test(username)) {
    return { valid: false, error: '用户名只能包含字母、数字和下划线' };
  }

  return { valid: true };
}

/**
 * 验证登录输入
 *
 * @param usernameOrEmail - 用户名或邮箱
 * @param password - 密码
 * @returns 验证结果
 */
export function validateLoginInput(
  usernameOrEmail: string,
  password: string
): ValidationResult {
  if (!usernameOrEmail) {
    return { valid: false, error: '请输入用户名或邮箱' };
  }

  if (!password) {
    return { valid: false, error: '请输入密码' };
  }

  return { valid: true };
}
