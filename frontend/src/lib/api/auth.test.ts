/**
 * 认证API单元测试
 * 针对登录流程中的错误文案进行校验
 *
 * 目标：
 * - 区分AI配置类错误与真实登录失败
 * - 确保当后端返回AI API Key相关错误时，前端给出明确的配置提示
 *
 * @author Ingenio Team
 * @since 2025-12-11
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { login } from './auth';
import * as clientModule from '@/lib/api/client';

// Mock API client模块
vi.mock('@/lib/api/client', () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

describe('Auth API - login()', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('当后端返回AI API Key相关错误时，应提示为配置问题而非登录失败', async () => {
    vi.mocked(clientModule.post).mockResolvedValueOnce({
      success: false,
      message: '七牛云AI提供商不可用：API Key未配置',
    });

    await expect(login('test@example.com', 'password')).rejects.toThrow(
      /AI 服务配置异常/,
    );
  });

  it('当后端返回普通登录错误时，应保持原始错误文案', async () => {
    const rawMessage = '用户名或密码错误';

    vi.mocked(clientModule.post).mockResolvedValueOnce({
      success: false,
      message: rawMessage,
    });

    await expect(login('wrong@example.com', 'bad-password')).rejects.toThrow(
      rawMessage,
    );
  });
});
