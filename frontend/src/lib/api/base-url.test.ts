import { describe, it, expect, afterEach } from 'vitest';
import { getApiBaseUrl, buildApiUrl } from './base-url';

/**
 * 保存测试前的API基址配置，确保用例结束后恢复环境，避免互相污染。
 *
 * 是什么：缓存 NEXT_PUBLIC_API_BASE_URL 与 NEXT_PUBLIC_API_URL 的原始值。
 * 做什么：在每个用例结束后恢复环境变量。
 * 为什么：避免不同用例之间相互污染导致测试不稳定。
 */
const ORIGINAL_API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;
const ORIGINAL_API_URL = process.env.NEXT_PUBLIC_API_URL;

afterEach(() => {
  if (ORIGINAL_API_BASE_URL === undefined) {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
  } else {
    process.env.NEXT_PUBLIC_API_BASE_URL = ORIGINAL_API_BASE_URL;
  }

  if (ORIGINAL_API_URL === undefined) {
    delete process.env.NEXT_PUBLIC_API_URL;
  } else {
    process.env.NEXT_PUBLIC_API_URL = ORIGINAL_API_URL;
  }
});

describe('getApiBaseUrl', () => {
  it('未配置时应回落到默认后端地址', () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    delete process.env.NEXT_PUBLIC_API_URL;
    expect(getApiBaseUrl()).toBe('http://localhost:8080/api');
  });

  it('显式配置为空字符串时应走同源 /api BFF 代理', () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = '   ';
    expect(getApiBaseUrl()).toBe('/api');
  });

  it('仅配置 NEXT_PUBLIC_API_URL 时应兼容读取', () => {
    delete process.env.NEXT_PUBLIC_API_BASE_URL;
    process.env.NEXT_PUBLIC_API_URL = 'https://legacy.example.com/api/v1/';
    expect(getApiBaseUrl()).toBe('https://legacy.example.com/api');
  });

  it('同时配置时应优先使用 NEXT_PUBLIC_API_BASE_URL', () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://primary.example.com/api';
    process.env.NEXT_PUBLIC_API_URL = 'https://legacy.example.com/api/v1';
    expect(getApiBaseUrl()).toBe('https://primary.example.com/api');
  });

  it('配置为 /api/v1 时应裁剪到 /api，避免重复拼接', () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://prod.example.com/api/v1/';
    expect(getApiBaseUrl()).toBe('https://prod.example.com/api');
  });

  it('缺少 context-path 时应自动补齐 /api', () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = 'https://prod.example.com';
    expect(getApiBaseUrl()).toBe('https://prod.example.com/api');
  });

  it('buildApiUrl 应保持单个斜杠拼接', () => {
    process.env.NEXT_PUBLIC_API_BASE_URL = '/api';
    expect(buildApiUrl('v1/projects')).toBe('/api/v1/projects');
    expect(buildApiUrl('/v1/projects')).toBe('/api/v1/projects');
  });
});
