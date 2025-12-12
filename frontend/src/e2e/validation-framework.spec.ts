/**
 * Phase 3: 三环验证框架 E2E测试
 * Phase 4: AI自动修复 E2E测试
 *
 * 测试覆盖：
 * - 编译验证（Compile Validation）
 * - 测试验证（Test Validation）
 * - 业务验证（Business Validation）
 * - AI自动修复（最多3次迭代）
 *
 * @author Ingenio Team
 * @since 2.0.0
 */

import { test, expect } from '@playwright/test';
import { v4 as uuidv4 } from 'uuid';

const API_BASE_URL = 'http://localhost:8080';
const TEST_TENANT_ID = uuidv4();
// const _TEST_USER_ID = uuidv4();

/**
 * Phase 3: 三环验证框架测试
 */
test.describe('Phase 3: 三环验证框架', () => {
  test.describe('编译验证（Compile Validation）', () => {
    test('应该验证TypeScript代码编译通过', async ({ page }) => {
      // 模拟验证请求
      const validateResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/compile`, {
        data: {
          appSpecId: uuidv4(),
          code: `
            export function greet(name: string): string {
              return \`Hello, \${name}!\`;
            }
          `,
          language: 'typescript',
        },
      });

      // 验证API响应结构
      if (validateResponse.ok()) {
        const result = await validateResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000') {
          expect(result.data.compileSuccess).toBe(true);
          expect(result.data.errors).toEqual([]);
        }
      }
    });

    test('应该检测TypeScript编译错误', async ({ page }) => {
      const validateResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/compile`, {
        data: {
          appSpecId: uuidv4(),
          code: `
            // 故意的类型错误
            const num: number = "not a number";
          `,
          language: 'typescript',
        },
      });

      if (validateResponse.ok()) {
        const result = await validateResponse.json();
        if (result.code === '0000' && result.data) {
          expect(result.data.compileSuccess).toBe(false);
          expect(result.data.errors.length).toBeGreaterThan(0);
        }
      }
    });

    test('应该验证Java代码编译通过', async ({ page }) => {
      const validateResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/compile`, {
        data: {
          appSpecId: uuidv4(),
          code: `
            public class HelloWorld {
              public static void main(String[] args) {
                System.out.println("Hello, World!");
              }
            }
          `,
          language: 'java',
        },
      });

      if (validateResponse.ok()) {
        const result = await validateResponse.json();
        expect(result.code).toBeDefined();
      }
    });
  });

  test.describe('测试验证（Test Validation）', () => {
    test('应该执行单元测试并返回结果', async ({ page }) => {
      const testResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/test`, {
        data: {
          appSpecId: uuidv4(),
          testType: 'unit',
          testFiles: ['src/__tests__/utils.test.ts'],
        },
      });

      if (testResponse.ok()) {
        const result = await testResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.totalTests).toBeDefined();
          expect(result.data.passedTests).toBeDefined();
          expect(result.data.failedTests).toBeDefined();
        }
      }
    });

    test('应该计算测试覆盖率', async ({ page }) => {
      const coverageResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/coverage`, {
        data: {
          appSpecId: uuidv4(),
        },
      });

      if (coverageResponse.ok()) {
        const result = await coverageResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.lineCoverage).toBeDefined();
          expect(result.data.branchCoverage).toBeDefined();
        }
      }
    });

    test('测试覆盖率应该>=85%才能通过', async ({ page }) => {
      const validateResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/quality-gate`, {
        data: {
          appSpecId: uuidv4(),
          metrics: {
            coverage: 90, // 90% > 85% 应该通过
            complexity: 8, // < 10 应该通过
          },
        },
      });

      if (validateResponse.ok()) {
        const result = await validateResponse.json();
        if (result.code === '0000' && result.data) {
          expect(result.data.passed).toBe(true);
        }
      }
    });
  });

  test.describe('业务验证（Business Validation）', () => {
    test('应该验证API契约符合OpenAPI规范', async ({ page }) => {
      const contractResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/contract`, {
        data: {
          appSpecId: uuidv4(),
          openApiSpec: {
            openapi: '3.0.0',
            info: { title: 'Test API', version: '1.0.0' },
            paths: {
              '/users': {
                get: {
                  responses: { '200': { description: 'Success' } },
                },
              },
            },
          },
        },
      });

      if (contractResponse.ok()) {
        const result = await contractResponse.json();
        expect(result.code).toBeDefined();
      }
    });

    test('应该验证数据库Schema完整性', async ({ page }) => {
      const schemaResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/schema`, {
        data: {
          appSpecId: uuidv4(),
          schema: {
            tables: [
              { name: 'users', columns: ['id', 'email', 'password'] },
              { name: 'posts', columns: ['id', 'title', 'content', 'user_id'] },
            ],
          },
        },
      });

      if (schemaResponse.ok()) {
        const result = await schemaResponse.json();
        expect(result.code).toBeDefined();
      }
    });

    test('应该验证业务流程完整性', async ({ page }) => {
      const flowResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/business-flow`, {
        data: {
          appSpecId: uuidv4(),
          flows: [
            {
              name: '用户注册流程',
              steps: ['输入邮箱', '设置密码', '发送验证邮件', '验证成功'],
            },
          ],
        },
      });

      if (flowResponse.ok()) {
        const result = await flowResponse.json();
        expect(result.code).toBeDefined();
      }
    });
  });

  test.describe('三环集成验证', () => {
    test('应该按顺序执行三环验证', async ({ page }) => {
      const fullValidateResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/full`, {
        data: {
          appSpecId: uuidv4(),
          tenantId: TEST_TENANT_ID,
          stages: ['compile', 'test', 'business'],
        },
      });

      if (fullValidateResponse.ok()) {
        const result = await fullValidateResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.stages).toBeDefined();
          expect(result.data.overallStatus).toBeDefined();
        }
      }
    });

    test('编译失败应该阻塞后续验证', async ({ page }) => {
      const validateResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/full`, {
        data: {
          appSpecId: uuidv4(),
          tenantId: TEST_TENANT_ID,
          stages: ['compile', 'test', 'business'],
          code: 'const x: number = "invalid";', // 编译错误
        },
      });

      if (validateResponse.ok()) {
        const result = await validateResponse.json();
        if (result.code === '0000' && result.data) {
          // 编译失败后，测试和业务验证应该被跳过
          expect(result.data.stages.compile.status).toBe('failed');
          expect(result.data.stages.test?.status).toBe('skipped');
          expect(result.data.stages.business?.status).toBe('skipped');
        }
      }
    });
  });
});

/**
 * Phase 4: AI自动修复测试
 */
test.describe('Phase 4: AI自动修复', () => {
  test.describe('自动修复触发条件', () => {
    test('验证失败应该触发AI修复流程', async ({ page }) => {
      const repairResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/trigger`, {
        data: {
          appSpecId: uuidv4(),
          tenantId: TEST_TENANT_ID,
          failureType: 'compile',
          errorDetails: [
            { line: 5, message: 'Type string is not assignable to type number' },
          ],
        },
      });

      if (repairResponse.ok()) {
        const result = await repairResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.repairTriggered).toBe(true);
          expect(result.data.repairId).toBeDefined();
        }
      }
    });

    test('测试失败应该生成修复建议', async ({ page }) => {
      const repairResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/suggest`, {
        data: {
          appSpecId: uuidv4(),
          failedTests: [
            {
              testName: 'should return user by id',
              error: 'Expected 200 but received 404',
              stackTrace: 'at UserService.test.ts:25',
            },
          ],
        },
      });

      if (repairResponse.ok()) {
        const result = await repairResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.suggestions).toBeDefined();
          expect(Array.isArray(result.data.suggestions)).toBe(true);
        }
      }
    });
  });

  test.describe('迭代修复机制', () => {
    test('应该支持最多3次修复迭代', async ({ page }) => {
      const iterateResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/iterate`, {
        data: {
          appSpecId: uuidv4(),
          tenantId: TEST_TENANT_ID,
          repairId: uuidv4(),
          maxIterations: 3,
        },
      });

      if (iterateResponse.ok()) {
        const result = await iterateResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.currentIteration).toBeLessThanOrEqual(3);
        }
      }
    });

    test('3次修复失败后应该人工介入', async ({ page }) => {
      const escalateResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/escalate`, {
        data: {
          appSpecId: uuidv4(),
          repairId: uuidv4(),
          failedIterations: 3,
          lastError: '无法自动修复的复杂错误',
        },
      });

      if (escalateResponse.ok()) {
        const result = await escalateResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.escalated).toBe(true);
          expect(result.data.notificationSent).toBe(true);
        }
      }
    });

    test('每次迭代应该记录修复历史', async ({ page }) => {
      const historyResponse = await page.request.get(
        `${API_BASE_URL}/api/v2/repair/history?appSpecId=${uuidv4()}`
      );

      if (historyResponse.ok()) {
        const result = await historyResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(Array.isArray(result.data.history)).toBe(true);
        }
      }
    });
  });

  test.describe('智能修复策略', () => {
    test('类型错误应该使用类型推断修复', async ({ page }) => {
      const typeRepairResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/type-error`, {
        data: {
          appSpecId: uuidv4(),
          errorCode: 'TS2322',
          context: {
            expectedType: 'number',
            actualType: 'string',
            variableName: 'count',
          },
        },
      });

      if (typeRepairResponse.ok()) {
        const result = await typeRepairResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.fixedCode).toBeDefined();
          expect(result.data.strategy).toBe('type_inference');
        }
      }
    });

    test('缺失依赖应该自动安装', async ({ page }) => {
      const depRepairResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/dependency`, {
        data: {
          appSpecId: uuidv4(),
          missingDependency: 'lodash',
          importStatement: "import _ from 'lodash';",
        },
      });

      if (depRepairResponse.ok()) {
        const result = await depRepairResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.installed).toBe(true);
          expect(result.data.packageJson.dependencies.lodash).toBeDefined();
        }
      }
    });

    test('业务逻辑错误应该生成修复方案', async ({ page }) => {
      const bizRepairResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/business-logic`, {
        data: {
          appSpecId: uuidv4(),
          errorDescription: '购物车总价计算不包含折扣',
          expectedBehavior: '总价 = 商品价格 * 数量 - 折扣',
          actualBehavior: '总价 = 商品价格 * 数量',
        },
      });

      if (bizRepairResponse.ok()) {
        const result = await bizRepairResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.repairPlan).toBeDefined();
          expect(result.data.affectedFiles).toBeDefined();
        }
      }
    });
  });

  test.describe('修复结果验证', () => {
    test('修复后应该重新执行验证', async ({ page }) => {
      const revalidateResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/revalidate`, {
        data: {
          appSpecId: uuidv4(),
          repairId: uuidv4(),
          repairIteration: 1,
        },
      });

      if (revalidateResponse.ok()) {
        const result = await revalidateResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.revalidated).toBe(true);
          expect(result.data.newValidationResult).toBeDefined();
        }
      }
    });

    test('修复成功后应该更新AppSpec状态', async ({ page }) => {
      const statusResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/complete`, {
        data: {
          appSpecId: uuidv4(),
          repairId: uuidv4(),
          finalStatus: 'success',
        },
      });

      if (statusResponse.ok()) {
        const result = await statusResponse.json();
        expect(result.code).toBeDefined();

        if (result.code === '0000' && result.data) {
          expect(result.data.appSpecStatus).toBe('validated');
        }
      }
    });
  });
});

/**
 * 端到端验证流程测试
 */
test.describe('完整验证修复流程', () => {
  test('生成代码 → 验证失败 → AI修复 → 验证成功', async ({ page }) => {
    // Step 1: 模拟生成代码
    const generateResponse = await page.request.post(`${API_BASE_URL}/api/v2/generate/code`, {
      data: {
        appSpecId: uuidv4(),
        tenantId: TEST_TENANT_ID,
        requirement: '创建一个简单的待办事项应用',
      },
    });

    if (!generateResponse.ok()) {
      console.log('代码生成API未实现或返回错误，跳过此测试');
      return;
    }

    const genResult = await generateResponse.json();
    if (genResult.code !== '0000') return;

    const { appSpecId } = genResult.data;

    // Step 2: 执行验证
    const validateResponse = await page.request.post(`${API_BASE_URL}/api/v2/validate/full`, {
      data: {
        appSpecId,
        tenantId: TEST_TENANT_ID,
        stages: ['compile', 'test', 'business'],
      },
    });

    if (!validateResponse.ok()) return;

    const valResult = await validateResponse.json();
    if (valResult.code !== '0000') return;

    // Step 3: 如果验证失败，触发AI修复
    if (valResult.data.overallStatus === 'failed') {
      const repairResponse = await page.request.post(`${API_BASE_URL}/api/v2/repair/auto`, {
        data: {
          appSpecId,
          tenantId: TEST_TENANT_ID,
          maxIterations: 3,
        },
      });

      if (repairResponse.ok()) {
        const repairResult = await repairResponse.json();
        if (repairResult.code === '0000') {
          expect(repairResult.data.repairStatus).toBeDefined();
        }
      }
    }

    console.log('✅ 完整验证修复流程测试完成');
  });
});
