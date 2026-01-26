/**
 * step-result-normalizer 单元测试
 *
 * 是什么：针对分析步骤结果归一化逻辑的测试用例集合。
 * 做什么：覆盖后端返回 Map/Object 结构时的关键字段转换（尤其是 entities/actions 的数组化）。
 * 为什么：防止出现 `data.entities.map is not a function` 等运行时崩溃回归。
 */

import { describe, it, expect } from "vitest";
import { normalizeStepResult } from "@/components/analysis/step-result-normalizer";

describe("step-result-normalizer", () => {
  it("Step1：entities 为对象时应转换为 string[]", () => {
    const raw = {
      entities: {
        Blog: { fields: ["id", "title"], description: "博客" },
        Comment: { fields: ["id", "content"], description: "评论" },
      },
      operations: {
        createBlog: { type: "CRUD", description: "创建博客" },
        listBlogs: { type: "CRUD", description: "查询博客列表" },
      },
    };

    const result = normalizeStepResult(1, raw, { requirement: "做一个博客系统，支持发布与评论" });

    expect(result.step).toBe(1);
    if (result.step === 1) {
      expect(Array.isArray(result.data.entities)).toBe(true);
      expect(result.data.entities).toEqual(expect.arrayContaining(["Blog", "Comment"]));
      expect(result.data.actions).toEqual(expect.arrayContaining(["createBlog", "listBlogs"]));
      expect(typeof result.data.summary).toBe("string");
      expect(typeof result.data.businessScenario).toBe("string");
    }
  });

  it("Step2：relationships.type 应映射为 ONE_TO_MANY 等枚举值", () => {
    const raw = {
      entities: {
        Blog: { fields: ["id: UUID", "title (string)"], description: "博客" },
        Comment: { fields: ["id: UUID", "content (string)"], description: "评论" },
      },
      relationships: {
        BlogComments: { from: "Blog", to: "Comment", type: "one-to-many" },
      },
    };

    const result = normalizeStepResult(2, raw);

    expect(result.step).toBe(2);
    if (result.step === 2) {
      expect(result.data.entities[0]?.fields[0]).toEqual({ name: "id", type: "UUID" });
      expect(result.data.relationships[0]?.type).toBe("ONE_TO_MANY");
    }
  });

  it("Step3：operations 应映射为 modules，并可根据需求文本推断 intent", () => {
    const raw = {
      operations: {
        createBlog: { type: "CRUD", description: "创建博客" },
      },
      constraints: {
        titleMaxLength: { type: "validation", description: "标题最长 200" },
      },
    };

    const result = normalizeStepResult(3, raw, { requirement: "参考 https://example.com 做一个类似的博客" });

    expect(result.step).toBe(3);
    if (result.step === 3) {
      expect(result.data.intent).toBe("CLONE");
      expect(result.data.modules.length).toBeGreaterThan(0);
      expect(Array.isArray(result.data.keywords)).toBe(true);
    }
  });

  it("Step4：tech stack 字段应转换为 frontend/backend 数组并保留 reason", () => {
    const raw = {
      platform: "Web",
      uiFramework: "React",
      backend: "Supabase",
      database: "PostgreSQL",
      reason: "需求偏内容展示，优先选用成熟的 BaaS 方案。",
    };

    const result = normalizeStepResult(4, raw);

    expect(result.step).toBe(4);
    if (result.step === 4) {
      expect(result.data.frontend.map(t => t.name)).toEqual(expect.arrayContaining(["Web", "React"]));
      expect(result.data.backend.map(t => t.name)).toEqual(expect.arrayContaining(["Supabase", "PostgreSQL"]));
      expect(result.data.reasoning).toContain("需求偏内容展示");
    }
  });

  it("Step5：应生成稳定的复杂度/工作量结构，并可从 Step3 推导 featureCount", () => {
    const step3 = normalizeStepResult(3, {
      operations: {
        createBlog: { type: "CRUD", description: "创建博客" },
        listBlogs: { type: "CRUD", description: "查询博客" },
      },
    });

    const raw = {
      complexityLevel: "COMPLEX",
      estimatedDays: 20,
      estimatedLines: 8000,
      riskFactors: ["安全：鉴权与权限边界", "性能：高并发下的查询与缓存"],
    };

    const result = normalizeStepResult(5, raw, { previousStepResults: { 3: step3 } });

    expect(result.step).toBe(5);
    if (result.step === 5) {
      expect(result.data.complexityScore).toBeGreaterThanOrEqual(1);
      expect(result.data.complexityScore).toBeLessThanOrEqual(10);
      expect(result.data.estimatedWorkload.featureCount).toBe(2);
      expect(Array.isArray(result.data.risks)).toBe(true);
      expect(Array.isArray(result.data.mitigations)).toBe(true);
    }
  });

  it("Step5：riskFactors 为对象数组时应正确映射 risks 与 mitigations", () => {
    const raw = {
      complexityLevel: "MEDIUM",
      estimatedDays: 8,
      riskFactors: [
        { factor: "鉴权与权限边界", level: "high", mitigation: "采用 RBAC 并进行接口鉴权覆盖测试" },
        { factor: "性能：列表查询与缓存策略", level: "medium", mitigation: "引入分页/索引与缓存，压测验证 P95" },
      ],
      securityConsiderations: ["全站 HTTPS", "输入校验与 XSS 防护"],
    };

    const result = normalizeStepResult(5, raw);

    expect(result.step).toBe(5);
    if (result.step === 5) {
      expect(result.data.risks.map(r => r.description)).toEqual(
        expect.arrayContaining(["鉴权与权限边界", "性能：列表查询与缓存策略"])
      );
      expect(result.data.risks.map(r => r.level)).toEqual(
        expect.arrayContaining(["HIGH", "MEDIUM"])
      );
      expect(result.data.mitigations).toEqual(
        expect.arrayContaining([
          "采用 RBAC 并进行接口鉴权覆盖测试",
          "引入分页/索引与缓存，压测验证 P95",
          "全站 HTTPS",
          "输入校验与 XSS 防护",
        ])
      );
    }
  });
});
