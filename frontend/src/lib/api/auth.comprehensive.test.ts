/**
 * 登录功能综合测试用例
 *
 * 测试目标：
 * 1. 验证登录API的错误处理逻辑
 * 2. 验证Token存储机制
 * 3. 验证错误消息规范化
 * 4. 验证并发登录场景
 * 5. 验证网络异常处理
 *
 * @author Ingenio Team
 * @since 2025-01-18
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { login, logout, refreshToken } from './auth';
import * as clientModule from '@/lib/api/client';
import * as tokenModule from '@/lib/auth/token';

// Mock API client模块
vi.mock('@/lib/api/client', () => ({
  get: vi.fn(),
  post: vi.fn(),
}));

// Mock token模块
vi.mock('@/lib/auth/token', () => ({
  setToken: vi.fn(),
  clearToken: vi.fn(),
  getToken: vi.fn(),
}));

describe('登录功能综合测试', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('问题1: AI配置错误识别', () => {
    it('应正确识别七牛云AI API Key���误', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: '七牛云AI提供商不可用：API Key未配置',
      });

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        /AI 服务配置异常/
      );
    });

    it('应正确识别DeepSeek API Key错误', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: 'DeepSeek API密钥无效',
      });

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        /AI 服务配置异常/
      );
    });

    it('应正确识别DashScope API错误', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: '阿里云DashScope提供商不可用',
      });

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        /AI 服务配置异常/
      );
    });

    it('普通登录错误不应被误判为AI配置错误', async () => {
      const errorMessage = '用户名或密码错误';
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: errorMessage,
      });

      await expect(login('wrong@example.com', 'bad-password')).rejects.toThrow(
        errorMessage
      );
    });
  });

  describe('问题2: Token存储机制', () => {
    it('登录成功后应调用setToken存储Token', async () => {
      const mockToken = 'test-jwt-token-123';
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: true,
        data: {
          token: mockToken,
          userId: 'user-123',
          username: 'testuser',
          email: 'test@example.com',
          role: 'user',
          expiresIn: 604800,
        },
      });

      await login('test@example.com', 'password');

      expect(tokenModule.setToken).toHaveBeenCalledWith(mockToken);
      expect(tokenModule.setToken).toHaveBeenCalledTimes(1);
    });

    it('登录失败时不应调用setToken', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: '用户名或密码错误',
      });

      await expect(login('wrong@example.com', 'bad-password')).rejects.toThrow();

      expect(tokenModule.setToken).not.toHaveBeenCalled();
    });

    it('登出时应调用clearToken清除Token', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: true,
      });

      await logout();

      expect(tokenModule.clearToken).toHaveBeenCalledTimes(1);
    });

    it('登出API失败时仍应清除本地Token', async () => {
      vi.mocked(clientModule.post).mockRejectedValueOnce(
        new Error('Network error')
      );

      await logout();

      expect(tokenModule.clearToken).toHaveBeenCalledTimes(1);
    });
  });

  describe('问题3: 响应数据验证', () => {
    it('后端返回success=true但data为null时应抛出错误', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: true,
        data: null,
      });

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        /登录失败：服务器未返回数据/
      );
    });

    it('后端返回success=true但data为undefined时应抛出错误', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: true,
        data: undefined,
      });

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        /登录失败：服务器未返回数据/
      );
    });

    it('后端返回success=false且无message时应有默认错误消息', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
      });

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        /登录失败/
      );
    });
  });

  describe('问题4: 网络异常处理', () => {
    it('网络请求超时应抛出明确错误', async () => {
      vi.mocked(clientModule.post).mockRejectedValueOnce(
        new Error('Request timeout')
      );

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        'Request timeout'
      );
    });

    it('网络连接失败应抛出明确错误', async () => {
      vi.mocked(clientModule.post).mockRejectedValueOnce(
        new Error('Network error')
      );

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        'Network error'
      );
    });

    it('服务器500错误应抛出明确错误', async () => {
      vi.mocked(clientModule.post).mockRejectedValueOnce(
        new Error('Internal Server Error')
      );

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        'Internal Server Error'
      );
    });
  });

  describe('问题5: 并发登录场景', () => {
    it('多次并发登录请求应正确处理', async () => {
      const mockResponse = {
        success: true,
        data: {
          token: 'test-token',
          userId: 'user-123',
          username: 'testuser',
          email: 'test@example.com',
          role: 'user',
          expiresIn: 604800,
        },
      };

      vi.mocked(clientModule.post).mockResolvedValue(mockResponse);

      // 并发发起3个登录请求
      const promises = [
        login('test@example.com', 'password'),
        login('test@example.com', 'password'),
        login('test@example.com', 'password'),
      ];

      const results = await Promise.all(promises);

      // 验证所有请求都成功
      expect(results).toHaveLength(3);
      results.forEach(result => {
        expect(result.token).toBe('test-token');
      });

      // 验证setToken被调用3次
      expect(tokenModule.setToken).toHaveBeenCalledTimes(3);
    });
  });

  describe('问题6: Token刷新功能', () => {
    it('Token刷新成功应更新本地Token', async () => {
      const oldToken = 'old-token-123';
      const newToken = 'new-token-456';

      vi.mocked(tokenModule.getToken).mockReturnValueOnce(oldToken);
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: true,
        data: {
          token: newToken,
          tokenType: 'Bearer',
          expiresIn: 86400,
          refreshedAt: Date.now(),
        },
      });

      const result = await refreshToken();

      expect(result.token).toBe(newToken);
      expect(tokenModule.setToken).toHaveBeenCalledWith(newToken);
    });

    it('无Token时刷新应抛出错误', async () => {
      vi.mocked(tokenModule.getToken).mockReturnValueOnce(null);

      await expect(refreshToken()).rejects.toThrow(
        '未找到Token，无法刷新'
      );
    });

    it('Token刷新失败应抛出错误', async () => {
      vi.mocked(tokenModule.getToken).mockReturnValueOnce('old-token');
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: true,
        data: null,
      });

      await expect(refreshToken()).rejects.toThrow(
        'Token刷新失败'
      );
    });
  });

  describe('问题7: 边界条件测试', () => {
    it('空用户名应正常发送请求', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: '用户名不能为空',
      });

      await expect(login('', 'password')).rejects.toThrow();

      expect(clientModule.post).toHaveBeenCalledWith('/v1/auth/login', {
        usernameOrEmail: '',
        password: 'password',
      });
    });

    it('空密码应正常发送请求', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: '密码不能为空',
      });

      await expect(login('test@example.com', '')).rejects.toThrow();

      expect(clientModule.post).toHaveBeenCalledWith('/v1/auth/login', {
        usernameOrEmail: 'test@example.com',
        password: '',
      });
    });

    it('超长用户名应正常发送请求', async () => {
      const longUsername = 'a'.repeat(1000);
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: '用户名过长',
      });

      await expect(login(longUsername, 'password')).rejects.toThrow();

      expect(clientModule.post).toHaveBeenCalledWith('/v1/auth/login', {
        usernameOrEmail: longUsername,
        password: 'password',
      });
    });

    it('特殊字符用户名应正常处���', async () => {
      const specialUsername = 'test@#$%^&*()';
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: true,
        data: {
          token: 'test-token',
          userId: 'user-123',
          username: specialUsername,
          email: 'test@example.com',
          role: 'user',
          expiresIn: 604800,
        },
      });

      const result = await login(specialUsername, 'password');

      expect(result.username).toBe(specialUsername);
    });
  });

  describe('问题8: 错误消息���式化', () => {
    it('应正确格式化包含API Key关键词的错误（大小写混合）', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: 'API KEY is missing',
      });

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        /AI 服务配置异常/
      );
    });

    it('应正确识别error字段中的AI配置错误', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        error: 'QINIU API Key未配置',
      });

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        /AI 服务配置异常/
      );
    });

    it('message和error同时存在时应优先使用message', async () => {
      vi.mocked(clientModule.post).mockResolvedValueOnce({
        success: false,
        message: '用户名或密码错误',
        error: 'QINIU API Key未配置',
      });

      await expect(login('test@example.com', 'password')).rejects.toThrow(
        '用户名或密码错误'
      );
    });
  });
});
