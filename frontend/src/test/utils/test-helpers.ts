/**
 * 测试辅助函数工具库
 * 秒构AI前端测试通用工具函数
 *
 * 功能：
 * - 等待工具函数
 * - 数据生成工具
 * - API测试辅助函数
 * - 断言增强函数
 *
 * Week 1 Day 3 Phase 3.1 - 测试环境搭建
 * 创建时间：2025-11-14
 */

/**
 * 等待指定时间（毫秒）
 * @param ms 毫秒数
 */
export const wait = (ms: number): Promise<void> => {
  return new Promise((resolve) => setTimeout(resolve, ms));
};

/**
 * 等待条件满足（带超时）
 * @param condition 条件函数，返回true表示满足
 * @param timeout 超时时间（毫秒），默认5000ms
 * @param interval 检查间隔（毫秒），默认100ms
 */
export const waitFor = async (
  condition: () => boolean | Promise<boolean>,
  timeout = 5000,
  interval = 100
): Promise<void> => {
  const startTime = Date.now();
  while (Date.now() - startTime < timeout) {
    const result = await condition();
    if (result) {
      return;
    }
    await wait(interval);
  }
  throw new Error(`Timeout waiting for condition after ${timeout}ms`);
};

/**
 * 生成随机字符串
 * @param length 字符串长度，默认10
 * @param prefix 前缀，默认'test-'
 */
export const randomString = (length = 10, prefix = "test-"): string => {
  const chars = "abcdefghijklmnopqrstuvwxyz0123456789";
  let result = prefix;
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
};

/**
 * 生成随机整数
 * @param min 最小值（包含）
 * @param max 最大值（包含）
 */
export const randomInt = (min: number, max: number): number => {
  return Math.floor(Math.random() * (max - min + 1)) + min;
};

/**
 * API测试辅助：构建完整的API URL
 * @param path API路径（如'/api/v1/users'）
 */
export const buildApiUrl = (path: string): string => {
  const baseUrl =
    process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
  // 确保path以/开头
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${baseUrl}${normalizedPath}`;
};

/**
 * API测试辅助：发送GET请求
 * @param path API路径
 * @param options fetch选项
 */
export const apiGet = async <T>(
  path: string,
  options?: RequestInit
): Promise<T> => {
  const url = buildApiUrl(path);
  const response = await fetch(url, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      ...options?.headers,
    },
    ...options,
  });

  if (!response.ok) {
    throw new Error(
      `API GET ${path} failed: ${response.status} ${response.statusText}`
    );
  }

  return response.json();
};

/**
 * API测试辅助：发送POST请求
 * @param path API路径
 * @param data 请求体数据
 * @param options fetch选项
 */
export const apiPost = async <T>(
  path: string,
  data?: unknown,
  options?: RequestInit
): Promise<T> => {
  const url = buildApiUrl(path);
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...options?.headers,
    },
    body: data ? JSON.stringify(data) : undefined,
    ...options,
  });

  if (!response.ok) {
    throw new Error(
      `API POST ${path} failed: ${response.status} ${response.statusText}`
    );
  }

  return response.json();
};

/**
 * 断言增强：检查对象包含指定属性
 * @param obj 对象
 * @param keys 属性名数组
 */
export const assertHasKeys = <T extends object>(
  obj: T,
  keys: (keyof T)[]
): void => {
  for (const key of keys) {
    if (!(key in obj)) {
      throw new Error(`Object missing required key: ${String(key)}`);
    }
  }
};

/**
 * 断言增强：检查数组非空
 * @param arr 数组
 * @param message 错误消息
 */
export const assertNotEmpty = <T>(arr: T[], message?: string): void => {
  if (arr.length === 0) {
    throw new Error(message || "Array is empty");
  }
};

/**
 * 测试数据工厂：生成用户数据
 */
export const createMockUser = (overrides?: Partial<MockUser>): MockUser => {
  return {
    id: randomString(8, "user-"),
    username: randomString(8, "user-"),
    email: `${randomString(8, "")}@test.com`,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
};

/**
 * 测试数据工厂：生成应用规格数据
 */
export const createMockAppSpec = (
  overrides?: Partial<MockAppSpec>
): MockAppSpec => {
  return {
    id: randomString(8, "app-"),
    name: randomString(10, "App "),
    description: "Test application description",
    userId: randomString(8, "user-"),
    status: "draft",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...overrides,
  };
};

// 类型定义
export interface MockUser {
  id: string;
  username: string;
  email: string;
  createdAt: string;
}

export interface MockAppSpec {
  id: string;
  name: string;
  description: string;
  userId: string;
  status: "draft" | "generating" | "completed" | "failed";
  createdAt: string;
  updatedAt: string;
}
