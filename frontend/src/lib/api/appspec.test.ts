/**
 * AppSpec API单元测试
 * 秒构AI AppSpec业务逻辑核心功能测试
 *
 * 测试目标：src/lib/api/appspec.ts
 * 测试策略：
 * - 测试CRUD操作（获取、更新、删除、列表查询）
 * - 测试数据验证和业务规则
 * - 测试查询参数构建（排序、分页、过滤）
 * - 测试边界情况（空数据、Unicode、特殊字符）
 * - 测试错误场景（无效ID、网络失败）
 *
 * Week 1 Day 3 Phase 3.2 - 核心工具函数单元测试
 * 创建时间：2025-11-14
 */

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import {
  getAppSpec,
  updateAppSpec,
  deleteAppSpec,
  getAppSpecList,
  type AppSpec,
  type AppSpecListItem,
  type AppSpecQueryOptions,
  type AppSpecListResponse,
} from "./appspec";
import * as clientModule from "./client";

// Mock client模块
vi.mock("./client", () => ({
  get: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  post: vi.fn(),
}));

describe("AppSpec API - appspec.ts", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("getAppSpec() - 获取AppSpec详情", () => {
    it("应该成功获取AppSpec详情", async () => {
      const mockAppSpec: AppSpec = {
        id: "app-123",
        version: "1.0.0",
        tenantId: "tenant-001",
        userId: "user-001",
        projectId: "project-001",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T11:00:00Z",
        userRequirement: "创建一个电商平台",
        projectType: "e-commerce",
        specContent: JSON.stringify({ features: ["shopping-cart", "payment"] }),
        planResult: {
          modules: [
            {
              name: "商品管理",
              description: "管理商品信息",
              priority: "high",
              complexity: 5,
              dependencies: [],
              dataModels: ["Product"],
              pages: ["ProductList", "ProductDetail"],
            },
          ],
          complexityScore: 75,
          reasoning: "标准电商系统",
          suggestedTechStack: ["React", "Node.js", "PostgreSQL"],
          estimatedHours: 160,
          recommendations: "建议使用微服务架构",
        },
        validateResult: {
          isValid: true,
          qualityScore: 92,
          issues: [],
          suggestions: ["添加缓存层提升性能"],
        },
        isValid: true,
        qualityScore: 92,
        status: "completed",
        generatedAt: "2025-01-14T11:00:00Z",
        durationMs: 45000,
      };

      const mockResponse = {
        success: true,
        data: mockAppSpec,
        message: "Success",
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce(mockResponse);

      const result = await getAppSpec("app-123");

      expect(clientModule.get).toHaveBeenCalledWith("/v1/appspecs/app-123");
      expect(result).toEqual(mockResponse);
      expect(result.data).toEqual(mockAppSpec);
      expect(result.success).toBe(true);
    });

    it("应该处理包含复杂planResult的AppSpec", async () => {
      const complexPlanResult = {
        modules: [
          {
            name: "用户系统",
            description: "用户注册、登录、权限管理",
            priority: "critical",
            complexity: 8,
            dependencies: ["AuthService", "EmailService"],
            dataModels: ["User", "Role", "Permission"],
            pages: ["Login", "Register", "Profile", "AdminPanel"],
          },
          {
            name: "支付系统",
            description: "支付接口集成",
            priority: "high",
            complexity: 9,
            dependencies: ["用户系统", "订单系统"],
            dataModels: ["Payment", "Transaction"],
            pages: ["PaymentPage", "PaymentHistory"],
          },
        ],
        complexityScore: 85,
        reasoning: "包含多个复杂子系统，需要仔细规划依赖关系",
        suggestedTechStack: [
          "React 19",
          "Next.js 15",
          "Spring Boot 3.4",
          "PostgreSQL 15",
          "Redis 7",
        ],
        estimatedHours: 320,
        recommendations:
          "1. 使用微服务架构分离支付模块\n2. 实现断路器模式防止级联故障\n3. 添加全链路追踪",
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          id: "app-456",
          version: "2.0.0",
          tenantId: "tenant-001",
          userId: "user-001",
          projectId: "project-002",
          createdAt: "2025-01-14T10:00:00Z",
          updatedAt: "2025-01-14T11:00:00Z",
          userRequirement: "复杂的企业级系统",
          planResult: complexPlanResult,
          validateResult: {
            isValid: true,
            qualityScore: 95,
            issues: [],
            suggestions: [],
          },
          isValid: true,
          qualityScore: 95,
          status: "completed",
          generatedAt: "2025-01-14T11:00:00Z",
          durationMs: 90000,
        } as AppSpec,
      });

      const result = await getAppSpec("app-456");

      expect(result.data?.planResult).toEqual(complexPlanResult);
      expect(result.data?.planResult.modules).toHaveLength(2);
      expect(result.data?.planResult.estimatedHours).toBe(320);
    });

    it("应该处理包含验证问题的AppSpec", async () => {
      const appSpecWithIssues: AppSpec = {
        id: "app-789",
        version: "1.0.0",
        tenantId: "tenant-001",
        userId: "user-001",
        projectId: "project-003",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T11:00:00Z",
        userRequirement: "需求不明确的项目",
        planResult: {
          modules: [],
          complexityScore: 30,
          reasoning: "需求描述不清晰",
          suggestedTechStack: [],
          estimatedHours: 0,
          recommendations: "请提供更详细的需求描述",
        },
        validateResult: {
          isValid: false,
          qualityScore: 45,
          issues: [
            {
              severity: "error",
              type: "incomplete_requirement",
              message: "需求描述过于简单，缺少核心功能说明",
              location: "userRequirement",
            },
            {
              severity: "warning",
              type: "missing_tech_stack",
              message: "未指定技术栈偏好",
            },
          ],
          suggestions: [
            "请详细描述核心功能模块",
            "明确用户角色和权限需求",
            "说明数据存储和访问模式",
          ],
        },
        isValid: false,
        qualityScore: 45,
        status: "failed",
        generatedAt: "2025-01-14T11:00:00Z",
        durationMs: 15000,
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: appSpecWithIssues,
      });

      const result = await getAppSpec("app-789");

      expect(result.data?.isValid).toBe(false);
      expect(result.data?.validateResult.issues).toHaveLength(2);
      expect(result.data?.validateResult.suggestions).toHaveLength(3);
    });

    it("应该处理不存在的AppSpec ID", async () => {
      const errorResponse = {
        success: false,
        error: "AppSpec not found",
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce(errorResponse);

      const result = await getAppSpec("non-existent-id");

      expect(result.success).toBe(false);
      expect(result.error).toBe("AppSpec not found");
    });

    it("应该处理包含特殊字符的userRequirement", async () => {
      const specialRequirement =
        "创建一个支持多语言的平台：中文🇨🇳、English🇺🇸、日本語🇯🇵\n" +
        "功能需求：\n" +
        "1. 用户管理 <User Management>\n" +
        '2. 数据分析 & 报表生成 "Advanced Analytics"\n' +
        "3. API接口 (REST/GraphQL)";

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          id: "app-unicode",
          version: "1.0.0",
          tenantId: "tenant-001",
          userId: "user-001",
          projectId: "project-unicode",
          createdAt: "2025-01-14T10:00:00Z",
          updatedAt: "2025-01-14T11:00:00Z",
          userRequirement: specialRequirement,
          planResult: {
            modules: [],
            complexityScore: 70,
            reasoning: "国际化需求",
            suggestedTechStack: ["i18next", "React"],
            estimatedHours: 120,
            recommendations: "使用i18n库",
          },
          validateResult: {
            isValid: true,
            qualityScore: 88,
            issues: [],
            suggestions: [],
          },
          isValid: true,
          qualityScore: 88,
          status: "completed",
          generatedAt: "2025-01-14T11:00:00Z",
          durationMs: 30000,
        } as AppSpec,
      });

      const result = await getAppSpec("app-unicode");

      expect(result.data?.userRequirement).toBe(specialRequirement);
      expect(result.data?.userRequirement).toContain("🇨🇳");
      expect(result.data?.userRequirement).toContain("日本語");
    });
  });

  describe("updateAppSpec() - 更新AppSpec", () => {
    it("应该成功更新AppSpec", async () => {
      const updateData: Partial<AppSpec> = {
        userRequirement: "更新后的需求描述",
        projectType: "updated-type",
        status: "in-progress",
      };

      const updatedAppSpec: AppSpec = {
        id: "app-123",
        version: "1.1.0",
        tenantId: "tenant-001",
        userId: "user-001",
        projectId: "project-001",
        createdAt: "2025-01-14T10:00:00Z",
        updatedAt: "2025-01-14T12:00:00Z", // 更新时间变化
        userRequirement: "更新后的需求描述",
        projectType: "updated-type",
        planResult: {
          modules: [],
          complexityScore: 0,
          reasoning: "",
          suggestedTechStack: [],
          estimatedHours: 0,
          recommendations: "",
        },
        validateResult: {
          isValid: true,
          qualityScore: 0,
          issues: [],
          suggestions: [],
        },
        isValid: true,
        qualityScore: 0,
        status: "in-progress",
        generatedAt: "2025-01-14T12:00:00Z",
        durationMs: 0,
      };

      vi.mocked(clientModule.put).mockResolvedValueOnce({
        success: true,
        data: updatedAppSpec,
        message: "Updated successfully",
      });

      const result = await updateAppSpec("app-123", updateData);

      expect(clientModule.put).toHaveBeenCalledWith(
        "/v1/appspecs/app-123",
        updateData
      );
      expect(result.data?.userRequirement).toBe("更新后的需求描述");
      expect(result.data?.status).toBe("in-progress");
    });

    it("应该支持部分字段更新", async () => {
      const partialUpdate: Partial<AppSpec> = {
        status: "completed",
      };

      vi.mocked(clientModule.put).mockResolvedValueOnce({
        success: true,
        data: {
          id: "app-123",
          status: "completed",
        } as AppSpec,
      });

      const result = await updateAppSpec("app-123", partialUpdate);

      expect(clientModule.put).toHaveBeenCalledWith(
        "/v1/appspecs/app-123",
        partialUpdate
      );
      expect(result.data?.status).toBe("completed");
    });

    it("应该处理更新失败的情况", async () => {
      vi.mocked(clientModule.put).mockResolvedValueOnce({
        success: false,
        error: "Update failed: Invalid status transition",
      });

      const result = await updateAppSpec("app-123", { status: "invalid" });

      expect(result.success).toBe(false);
      expect(result.error).toContain("Update failed");
    });

    it("应该处理空更新数据", async () => {
      vi.mocked(clientModule.put).mockResolvedValueOnce({
        success: true,
        data: {
          id: "app-123",
          version: "1.0.0",
        } as AppSpec,
      });

      const result = await updateAppSpec("app-123", {});

      expect(clientModule.put).toHaveBeenCalledWith("/v1/appspecs/app-123", {});
      expect(result.success).toBe(true);
    });

    it("应该处理包含嵌套对象的更新", async () => {
      const complexUpdate: Partial<AppSpec> = {
        validateResult: {
          isValid: true,
          qualityScore: 95,
          issues: [
            {
              severity: "info",
              type: "optimization",
              message: "可以进一步优化数据库查询",
            },
          ],
          suggestions: ["使用索引优化", "添加缓存层"],
        },
      };

      vi.mocked(clientModule.put).mockResolvedValueOnce({
        success: true,
        data: {
          id: "app-123",
          validateResult: complexUpdate.validateResult,
        } as AppSpec,
      });

      const result = await updateAppSpec("app-123", complexUpdate);

      expect(result.data?.validateResult?.qualityScore).toBe(95);
      expect(result.data?.validateResult?.issues).toHaveLength(1);
    });
  });

  describe("deleteAppSpec() - 删除AppSpec", () => {
    it("应该成功删除AppSpec", async () => {
      vi.mocked(clientModule.del).mockResolvedValueOnce({
        success: true,
        data: { deleted: true },
        message: "Deleted successfully",
      });

      const result = await deleteAppSpec("app-123");

      expect(clientModule.del).toHaveBeenCalledWith("/v1/appspecs/app-123");
      expect(result.success).toBe(true);
      expect(result.data?.deleted).toBe(true);
    });

    it("应该处理删除失败的情况", async () => {
      vi.mocked(clientModule.del).mockResolvedValueOnce({
        success: false,
        error: "Cannot delete: AppSpec is in use",
      });

      const result = await deleteAppSpec("app-in-use");

      expect(result.success).toBe(false);
      expect(result.error).toContain("Cannot delete");
    });

    it("应该处理不存在的ID", async () => {
      vi.mocked(clientModule.del).mockResolvedValueOnce({
        success: false,
        error: "AppSpec not found",
      });

      const result = await deleteAppSpec("non-existent");

      expect(result.success).toBe(false);
    });
  });

  describe("getAppSpecList() - 获取AppSpec列表", () => {
    it("应该成功获取列表（无查询参数）", async () => {
      const mockListItems: AppSpecListItem[] = [
        {
          id: "app-001",
          projectId: "project-001",
          version: "1.0.0",
          userRequirement: "电商平台",
          projectType: "e-commerce",
          qualityScore: 90,
          status: "completed",
          createdAt: "2025-01-14T10:00:00Z",
          updatedAt: "2025-01-14T11:00:00Z",
        },
        {
          id: "app-002",
          projectId: "project-002",
          version: "1.0.0",
          userRequirement: "社交应用",
          projectType: "social",
          qualityScore: 85,
          status: "completed",
          createdAt: "2025-01-13T10:00:00Z",
          updatedAt: "2025-01-13T11:00:00Z",
        },
      ];

      const mockResponse: AppSpecListResponse = {
        items: mockListItems,
        total: 2,
        page: 1,
        limit: 10,
        hasNext: false,
        hasPrev: false,
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: mockResponse,
      });

      const result = await getAppSpecList();

      expect(clientModule.get).toHaveBeenCalledWith("/v1/appspecs");
      expect(result.data?.items).toHaveLength(2);
      expect(result.data?.total).toBe(2);
    });

    it("应该正确构建排序参数", async () => {
      const options: AppSpecQueryOptions = {
        sortBy: "createdAt",
        sortOrder: "desc",
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: [],
          total: 0,
          page: 1,
          limit: 10,
          hasNext: false,
          hasPrev: false,
        },
      });

      await getAppSpecList(options);

      expect(clientModule.get).toHaveBeenCalledWith(
        "/v1/appspecs?sortBy=createdAt&sortOrder=desc"
      );
    });

    it("应该正确构建分页参数", async () => {
      const options: AppSpecQueryOptions = {
        page: 2,
        limit: 20,
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: [],
          total: 50,
          page: 2,
          limit: 20,
          hasNext: true,
          hasPrev: true,
        },
      });

      await getAppSpecList(options);

      expect(clientModule.get).toHaveBeenCalledWith(
        "/v1/appspecs?page=2&limit=20"
      );
    });

    it("应该正确构建状态过滤参数", async () => {
      const options: AppSpecQueryOptions = {
        status: "completed",
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: [],
          total: 0,
          page: 1,
          limit: 10,
          hasNext: false,
          hasPrev: false,
        },
      });

      await getAppSpecList(options);

      expect(clientModule.get).toHaveBeenCalledWith(
        "/v1/appspecs?status=completed"
      );
    });

    it("应该正确构建组合查询参数", async () => {
      const options: AppSpecQueryOptions = {
        sortBy: "qualityScore",
        sortOrder: "desc",
        status: "completed",
        page: 1,
        limit: 20,
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: [],
          total: 0,
          page: 1,
          limit: 20,
          hasNext: false,
          hasPrev: false,
        },
      });

      await getAppSpecList(options);

      expect(clientModule.get).toHaveBeenCalledWith(
        "/v1/appspecs?sortBy=qualityScore&sortOrder=desc&status=completed&page=1&limit=20"
      );
    });

    it("应该处理大量列表数据", async () => {
      const largeList: AppSpecListItem[] = Array.from({ length: 100 }, (_, i) => ({
        id: `app-${i.toString().padStart(3, "0")}`,
        projectId: `project-${i.toString().padStart(3, "0")}`,
        version: "1.0.0",
        userRequirement: `需求 ${i + 1}`,
        qualityScore: Math.floor(Math.random() * 40) + 60, // 60-100分
        status: i % 3 === 0 ? "completed" : "in-progress",
        createdAt: new Date(2025, 0, i + 1).toISOString(),
        updatedAt: new Date(2025, 0, i + 1).toISOString(),
      }));

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: largeList,
          total: 100,
          page: 1,
          limit: 100,
          hasNext: false,
          hasPrev: false,
        },
      });

      const result = await getAppSpecList({ limit: 100 });

      expect(result.data?.items).toHaveLength(100);
      expect(result.data?.total).toBe(100);
    });

    it("应该处理空列表", async () => {
      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: [],
          total: 0,
          page: 1,
          limit: 10,
          hasNext: false,
          hasPrev: false,
        },
      });

      const result = await getAppSpecList();

      expect(result.data?.items).toHaveLength(0);
      expect(result.data?.total).toBe(0);
    });

    it("应该正确处理分页边界情况", async () => {
      // 最后一页
      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: [
            {
              id: "app-last",
              projectId: "project-last",
              version: "1.0.0",
              userRequirement: "最后一个",
              qualityScore: 85,
              status: "completed",
              createdAt: "2025-01-14T10:00:00Z",
              updatedAt: "2025-01-14T11:00:00Z",
            },
          ],
          total: 51,
          page: 6,
          limit: 10,
          hasNext: false,
          hasPrev: true,
        },
      });

      const result = await getAppSpecList({ page: 6, limit: 10 });

      expect(result.data?.items).toHaveLength(1);
      expect(result.data?.hasNext).toBe(false);
      expect(result.data?.hasPrev).toBe(true);
    });

    it("应该处理包含特殊字符的userRequirement", async () => {
      const itemsWithSpecialChars: AppSpecListItem[] = [
        {
          id: "app-special-1",
          projectId: "project-special-1",
          version: "1.0.0",
          userRequirement: "创建支持中文、English和日本語的应用 🌏",
          qualityScore: 88,
          status: "completed",
          createdAt: "2025-01-14T10:00:00Z",
          updatedAt: "2025-01-14T11:00:00Z",
        },
        {
          id: "app-special-2",
          projectId: "project-special-2",
          version: "1.0.0",
          userRequirement: "包含特殊符号：<>&\"'/\\的需求",
          qualityScore: 75,
          status: "completed",
          createdAt: "2025-01-14T10:00:00Z",
          updatedAt: "2025-01-14T11:00:00Z",
        },
      ];

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: itemsWithSpecialChars,
          total: 2,
          page: 1,
          limit: 10,
          hasNext: false,
          hasPrev: false,
        },
      });

      const result = await getAppSpecList();

      expect(result.data?.items[0].userRequirement).toContain("🌏");
      expect(result.data?.items[1].userRequirement).toContain("<>&");
    });
  });

  describe("边界情况和错误处理", () => {
    it("应该处理网络超时", async () => {
      vi.mocked(clientModule.get).mockRejectedValueOnce(
        new Error("Request timeout")
      );

      await expect(getAppSpec("app-123")).rejects.toThrow("Request timeout");
    });

    it("应该处理服务器错误", async () => {
      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: false,
        error: "Internal Server Error",
      });

      const result = await getAppSpec("app-123");

      expect(result.success).toBe(false);
      expect(result.error).toBe("Internal Server Error");
    });

    it("应该处理无效的查询参数类型", async () => {
      // TypeScript会在编译时捕获类型错误，但运行时可能会传入意外值
      const invalidOptions = {
        sortBy: "invalidField" as unknown as "id" | "name" | "createdAt" | "updatedAt",
        sortOrder: "invalid" as unknown as "asc" | "desc",
      };

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: true,
        data: {
          items: [],
          total: 0,
          page: 1,
          limit: 10,
          hasNext: false,
          hasPrev: false,
        },
      });

      // 即使参数无效，函数也应该正常执行（由后端验证）
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      await expect(getAppSpecList(invalidOptions as any)).resolves.not.toThrow();
    });

    it("应该处理空字符串ID", async () => {
      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: false,
        error: "Invalid ID",
      });

      const result = await getAppSpec("");

      expect(clientModule.get).toHaveBeenCalledWith("/v1/appspecs/");
      expect(result.success).toBe(false);
    });

    it("应该处理超长ID", async () => {
      const longId = "a".repeat(1000);

      vi.mocked(clientModule.get).mockResolvedValueOnce({
        success: false,
        error: "ID too long",
      });

      const result = await getAppSpec(longId);

      expect(result.success).toBe(false);
    });
  });
});
