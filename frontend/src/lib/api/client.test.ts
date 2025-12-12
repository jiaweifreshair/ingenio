/**
 * API Client单元测试
 * 秒构AI后端API客户端核心功能测试
 *
 * 测试目标：src/lib/api/client.ts
 * 测试策略：
 * - 测试HTTP请求方法（GET/POST/PUT/DELETE）
 * - 测试错误处理（网络错误、4xx、5xx、非JSON响应）
 * - 测试Authorization header注入
 * - 测试401自动跳转登录逻辑
 * - 测试响应格式解析
 *
 * Week 1 Day 3 Phase 3.2 - 核心工具函数单元测试
 * 创建时间：2025-11-14
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { get, post, put, del, APIError } from "./client";
import * as tokenModule from "@/lib/auth/token";

// Mock全局fetch
const mockFetch = vi.fn();
global.fetch = mockFetch;

// Mock token模块
vi.mock("@/lib/auth/token", () => ({
  getToken: vi.fn(),
  clearToken: vi.fn(),
  setToken: vi.fn(),
  hasToken: vi.fn(),
}));

describe("API Client - client.ts", () => {
  const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
  const TEST_ENDPOINT = "/api/v1/test";
  const TEST_URL = `${API_BASE_URL}${TEST_ENDPOINT}`;

  beforeEach(() => {
    // 重置所有mocks
    vi.clearAllMocks();
    mockFetch.mockReset();

    // Mock浏览器环境
    Object.defineProperty(window, "location", {
      value: {
        href: "",
      },
      writable: true,
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("GET请求", () => {
    it("应该成功发起GET请求并返回数据", async () => {
      const mockData = { id: "123", name: "Test" };
      const mockResponse = {
        success: true,
        data: mockData,
        message: "Success",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => mockResponse,
      });

      const result = await get<typeof mockData>(TEST_ENDPOINT);

      expect(mockFetch).toHaveBeenCalledWith(
        TEST_URL,
        expect.objectContaining({
          method: "GET",
          headers: expect.objectContaining({
            "Content-Type": "application/json",
          }),
          credentials: "include",
        })
      );

      expect(result).toEqual(mockResponse);
      expect(result.success).toBe(true);
      expect(result.data).toEqual(mockData);
    });

    it("应该在有Token时自动添加Authorization header", async () => {
      const mockToken = "test-jwt-token-12345";
      vi.mocked(tokenModule.getToken).mockReturnValue(mockToken);

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({ success: true, data: {} }),
      });

      await get(TEST_ENDPOINT);

      expect(mockFetch).toHaveBeenCalledWith(
        TEST_URL,
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: `Bearer ${mockToken}`,
          }),
        })
      );
    });

    it("应该在无Token时不添加Authorization header", async () => {
      vi.mocked(tokenModule.getToken).mockReturnValue(null);

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({ success: true, data: {} }),
      });

      await get(TEST_ENDPOINT);

      const callArgs = mockFetch.mock.calls[0][1];
      expect(callArgs?.headers).not.toHaveProperty("Authorization");
    });
  });

  describe("POST请求", () => {
    it("应该成功发起POST请求并携带数据", async () => {
      const requestData = { name: "New Item", value: 100 };
      const mockResponse = {
        success: true,
        data: { id: "456", ...requestData },
        message: "Created",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 201,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => mockResponse,
      });

      const result = await post(TEST_ENDPOINT, requestData);

      expect(mockFetch).toHaveBeenCalledWith(
        TEST_URL,
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify(requestData),
          headers: expect.objectContaining({
            "Content-Type": "application/json",
          }),
        })
      );

      expect(result).toEqual(mockResponse);
      expect(result.success).toBe(true);
    });

    it("应该正确处理空body的POST请求", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({ success: true }),
      });

      await post(TEST_ENDPOINT, {});

      expect(mockFetch).toHaveBeenCalledWith(
        TEST_URL,
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({}),
        })
      );
    });
  });

  describe("PUT请求", () => {
    it("应该成功发起PUT请求并更新数据", async () => {
      const updateData = { name: "Updated Item" };
      const mockResponse = {
        success: true,
        data: { id: "123", ...updateData },
        message: "Updated",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => mockResponse,
      });

      const result = await put(TEST_ENDPOINT, updateData);

      expect(mockFetch).toHaveBeenCalledWith(
        TEST_URL,
        expect.objectContaining({
          method: "PUT",
          body: JSON.stringify(updateData),
        })
      );

      expect(result).toEqual(mockResponse);
    });
  });

  describe("DELETE请求", () => {
    it("应该成功发起DELETE请求", async () => {
      const mockResponse = {
        success: true,
        data: { deleted: true },
        message: "Deleted",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => mockResponse,
      });

      const result = await del(TEST_ENDPOINT);

      expect(mockFetch).toHaveBeenCalledWith(
        TEST_URL,
        expect.objectContaining({
          method: "DELETE",
        })
      );

      expect(result).toEqual(mockResponse);
      expect(result.success).toBe(true);
    });
  });

  describe("兼容后端Result结构", () => {
    it("应该将code=0000的Result转换为success=true", async () => {
      const mockData = { foo: "bar" };
      const mockResponse = {
        code: "0000",
        message: "操作成功",
        data: mockData,
        timestamp: Date.now(),
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => mockResponse,
      });

      const result = await get<typeof mockData>(TEST_ENDPOINT);

      expect(result.success).toBe(true);
      expect(result.code).toBe("0000");
      expect(result.data).toEqual(mockData);
    });

    it("应该在code非0000时抛出APIError", async () => {
      const mockResponse = {
        code: "1000",
        message: "系统错误",
        timestamp: Date.now(),
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => mockResponse,
      });

      try {
        await get(TEST_ENDPOINT);
        expect.fail("应该抛出APIError");
      } catch (error) {
        expect(error).toBeInstanceOf(APIError);
        expect((error as APIError).message).toContain("系统错误");
      }
    });
  });

  describe("错误处理 - 401未授权", () => {
    it("应该在401错误时清除Token并跳转登录页", async () => {
      const clearTokenSpy = vi.mocked(tokenModule.clearToken);

      // 修复：添加完整的mock response对象（包含json和text方法）
      // 注意：测试调用了两次get()，需要mock两次
      const mock401Response = {
        ok: false,
        status: 401,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({}),
        text: async () => "",
      };
      mockFetch.mockResolvedValue(mock401Response);

      await expect(get(TEST_ENDPOINT)).rejects.toThrow(APIError);
      await expect(get(TEST_ENDPOINT)).rejects.toThrow("未授权，请重新登录");

      expect(clearTokenSpy).toHaveBeenCalled();
      expect(window.location.href).toBe("/login");
    });

    it("401错误应该包含正确的状态码", async () => {
      // 修复：添加完整的mock response对象（包含json和text方法）
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({}),
        text: async () => "",
      });

      try {
        await get(TEST_ENDPOINT);
        expect.fail("应该抛出APIError");
      } catch (error) {
        expect(error).toBeInstanceOf(APIError);
        expect((error as APIError).statusCode).toBe(401);
      }
    });
  });

  describe("错误处理 - 非JSON响应", () => {
    it("应该处理HTML错误页面响应", async () => {
      const htmlResponse = "<!DOCTYPE html><html><body>Error</body></html>";

      // 修复：添加json方法（抛出错误，因为不是JSON）
      // 注意：测试调用了两次get()，需要使用mockResolvedValue而非mockResolvedValueOnce
      const mockHtmlResponse = {
        ok: false,
        status: 500,
        headers: new Headers({
          "content-type": "text/html",
        }),
        text: async () => htmlResponse,
        json: async () => {
          throw new Error("Unexpected token < in JSON at position 0");
        },
      };
      mockFetch.mockResolvedValue(mockHtmlResponse);

      await expect(get(TEST_ENDPOINT)).rejects.toThrow(APIError);
      await expect(get(TEST_ENDPOINT)).rejects.toThrow(
        /后端服务返回了HTML页面而不是JSON响应/
      );
    });

    it("应该处理纯文本错误响应", async () => {
      const textResponse = "Internal Server Error";

      // 修复：添加json方法（抛出错误，因为不是JSON）
      // 注意：测试调用了两次get()，需要使用mockResolvedValue而非mockResolvedValueOnce
      const mockTextResponse = {
        ok: false,
        status: 500,
        headers: new Headers({
          "content-type": "text/plain",
        }),
        text: async () => textResponse,
        json: async () => {
          throw new Error("Unexpected token I in JSON at position 0");
        },
      };
      mockFetch.mockResolvedValue(mockTextResponse);

      await expect(get(TEST_ENDPOINT)).rejects.toThrow(APIError);
      await expect(get(TEST_ENDPOINT)).rejects.toThrow(
        /后端服务返回了非JSON响应/
      );
    });

    it("应该在非JSON响应中包含状态码和响应预览", async () => {
      const longText = "a".repeat(300); // 超过200字符

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 503,
        headers: new Headers({
          "content-type": "text/plain",
        }),
        text: async () => longText,
      });

      try {
        await get(TEST_ENDPOINT);
        expect.fail("应该抛出APIError");
      } catch (error) {
        expect(error).toBeInstanceOf(APIError);
        expect((error as APIError).statusCode).toBe(503);
        expect((error as APIError).message).toContain("503");
        // 应该截断长文本
        expect((error as APIError).response).toHaveProperty("rawResponse");
      }
    });
  });

  describe("错误处理 - 4xx客户端错误", () => {
    it("应该处理400 Bad Request", async () => {
      const errorResponse = {
        success: false,
        error: "Invalid request parameters",
      };

      // 修复：添加text方法
      // 注意：测试调用了两次get()，需要使用mockResolvedValue而非mockResolvedValueOnce
      const mock400Response = {
        ok: false,
        status: 400,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => errorResponse,
        text: async () => JSON.stringify(errorResponse),
      };
      mockFetch.mockResolvedValue(mock400Response);

      await expect(get(TEST_ENDPOINT)).rejects.toThrow(APIError);
      await expect(get(TEST_ENDPOINT)).rejects.toThrow("Invalid request parameters");
    });

    it("应该处理404 Not Found", async () => {
      const errorResponse = {
        success: false,
        error: "Resource not found",
      };

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 404,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => errorResponse,
      });

      try {
        await get(TEST_ENDPOINT);
        expect.fail("应该抛出APIError");
      } catch (error) {
        expect(error).toBeInstanceOf(APIError);
        expect((error as APIError).statusCode).toBe(404);
        expect((error as APIError).message).toBe("Resource not found");
      }
    });

    it("应该在无error字段时使用默认错误消息", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 422,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({ success: false }),
      });

      await expect(get(TEST_ENDPOINT)).rejects.toThrow("请求失败 (HTTP 422)");
    });
  });

  describe("错误处理 - 5xx服务器错误", () => {
    it("应该处理500 Internal Server Error", async () => {
      const errorResponse = {
        success: false,
        error: "Internal server error",
      };

      // 修复：添加text方法
      // 注意：测试调用了两次get()，需要使用mockResolvedValue而非mockResolvedValueOnce
      const mock500Response = {
        ok: false,
        status: 500,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => errorResponse,
        text: async () => JSON.stringify(errorResponse),
      };
      mockFetch.mockResolvedValue(mock500Response);

      await expect(get(TEST_ENDPOINT)).rejects.toThrow(APIError);
      await expect(get(TEST_ENDPOINT)).rejects.toThrow("Internal server error");
    });

    it("应该处理503 Service Unavailable", async () => {
      const errorResponse = {
        success: false,
        error: "Service temporarily unavailable",
      };

      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 503,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => errorResponse,
      });

      try {
        await get(TEST_ENDPOINT);
        expect.fail("应该抛出APIError");
      } catch (error) {
        expect(error).toBeInstanceOf(APIError);
        expect((error as APIError).statusCode).toBe(503);
      }
    });
  });

  describe("错误处理 - 网络错误", () => {
    it("应该处理网络连接失败", async () => {
      mockFetch.mockRejectedValueOnce(new Error("Failed to fetch"));

      await expect(get(TEST_ENDPOINT)).rejects.toThrow(APIError);
      await expect(get(TEST_ENDPOINT)).rejects.toThrow("网络请求失败");
    });

    it("应该处理JSON解析错误", async () => {
      // 修复：添加text方法，模拟返回HTML内容导致JSON解析失败
      // 注意：测试调用了两次get()，需要使用mockResolvedValue而非mockResolvedValueOnce
      const mockJsonParseErrorResponse = {
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => {
          throw new Error("Unexpected token < in JSON at position 0");
        },
        text: async () => "<!DOCTYPE html>...",
      };
      mockFetch.mockResolvedValue(mockJsonParseErrorResponse);

      await expect(get(TEST_ENDPOINT)).rejects.toThrow(APIError);
      await expect(get(TEST_ENDPOINT)).rejects.toThrow(
        /后端服务返回了无效的JSON响应/
      );
    });

    it("应该处理超时错误", async () => {
      // 注意：这个测试直接reject，调用了两次get()，需要使用mockRejectedValue而非mockRejectedValueOnce
      mockFetch.mockRejectedValue(new Error("Request timeout"));

      await expect(get(TEST_ENDPOINT)).rejects.toThrow(APIError);
      await expect(get(TEST_ENDPOINT)).rejects.toThrow(/网络请求失败.*timeout/i);
    });
  });

  describe("APIError类", () => {
    it("应该正确创建APIError实例", () => {
      const error = new APIError("Test error", 400, { detail: "test" });

      expect(error).toBeInstanceOf(Error);
      expect(error).toBeInstanceOf(APIError);
      expect(error.name).toBe("APIError");
      expect(error.message).toBe("Test error");
      expect(error.statusCode).toBe(400);
      expect(error.response).toEqual({ detail: "test" });
    });

    it("应该支持不传statusCode和response", () => {
      const error = new APIError("Simple error");

      expect(error.message).toBe("Simple error");
      expect(error.statusCode).toBeUndefined();
      expect(error.response).toBeUndefined();
    });
  });

  describe("边界情况", () => {
    it("应该处理空endpoint", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({ success: true, data: {} }),
      });

      await get("");

      expect(mockFetch).toHaveBeenCalledWith(
        API_BASE_URL,
        expect.any(Object)
      );
    });

    it("应该处理包含查询参数的endpoint", async () => {
      const endpointWithQuery = "/api/v1/users?page=1&limit=10";

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({ success: true, data: {} }),
      });

      await get(endpointWithQuery);

      expect(mockFetch).toHaveBeenCalledWith(
        `${API_BASE_URL}${endpointWithQuery}`,
        expect.any(Object)
      );
    });

    it("应该处理包含特殊字符的数据", async () => {
      const specialData = {
        name: "测试用户",
        description: "Test with 特殊字符 & symbols <>/\"",
        emoji: "🚀✨",
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({ success: true, data: specialData }),
      });

      const result = await post(TEST_ENDPOINT, specialData);

      expect(result.data).toEqual(specialData);
      expect(mockFetch).toHaveBeenCalledWith(
        TEST_URL,
        expect.objectContaining({
          body: JSON.stringify(specialData),
        })
      );
    });

    it("应该处理超大响应数据", async () => {
      const largeData = {
        items: Array.from({ length: 1000 }, (_, i) => ({
          id: `item-${i}`,
          value: Math.random(),
        })),
      };

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({ success: true, data: largeData }),
      });

      const result = await get<typeof largeData>(TEST_ENDPOINT);

      expect(result.data).toEqual(largeData);
      // TypeScript类型断言：确保data是largeData类型
      expect((result.data as typeof largeData).items).toHaveLength(1000);
    });
  });

  describe("自定义headers合并", () => {
    it("应该正确合并自定义headers", async () => {
      vi.mocked(tokenModule.getToken).mockReturnValue("test-token");

      mockFetch.mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          "content-type": "application/json",
        }),
        json: async () => ({ success: true }),
      });

      const customHeaders = {
        "X-Custom-Header": "custom-value",
        "X-Request-ID": "req-12345",
      };

      // 直接调用request函数的封装，这里通过post来测试
      await post(TEST_ENDPOINT, {}, { headers: customHeaders });

      // 修复：Headers对象会将header名称转为小写，所以需要检查小写形式
      expect(mockFetch).toHaveBeenCalledWith(
        TEST_URL,
        expect.objectContaining({
          headers: expect.objectContaining({
            "Content-Type": "application/json",
            Authorization: "Bearer test-token",
            "x-custom-header": "custom-value",  // Headers API会将名称转为小写
            "x-request-id": "req-12345",        // Headers API会将名称转为小写
          }),
        })
      );
    });
  });
});
