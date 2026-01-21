/**
 * 完整AI工作流E2E测试
 *
 * 测试范围：
 * 1. 意图识别与技术点拆解
 * 2. AI能力集成（OpenAI/AgentScope）
 * 3. JeecgBoot能力配置（认证、支付、AI Agent）
 * 4. G3编排器完整流程（PLANNING → CODING → TESTING → COACH）
 * 5. 代码生成质量验证（编译、测试、API功能）
 */

import { test, expect, type APIRequestContext } from '@playwright/test';

const BACKEND_API_BASE_URL =
  process.env.E2E_BACKEND_API_BASE_URL ||
  process.env.NEXT_PUBLIC_API_BASE_URL ||
  'http://127.0.0.1:8080/api';

const E2E_USERNAME = process.env.E2E_USERNAME || 'justin';
const E2E_PASSWORD = process.env.E2E_PASSWORD || 'Test12345';
const API_TIMEOUT_MS = Number(process.env.E2E_API_TIMEOUT_MS || 180_000);

function api(path: string) {
  return `${BACKEND_API_BASE_URL}${path}`;
}

async function ensureBackendHealthy(request: APIRequestContext) {
  const health = await request.get(api('/actuator/health'), { timeout: 10_000 });
  expect(health.ok()).toBeTruthy();
}

async function loginViaApi(request: APIRequestContext) {
  const resp = await request.post(api('/v1/auth/login'), {
    data: { usernameOrEmail: E2E_USERNAME, password: E2E_PASSWORD },
    timeout: 20_000,
  });
  expect(resp.ok()).toBeTruthy();
  const body = await resp.json();
  expect([200, '0000']).toContain(body.code);
  expect(body.data?.token).toBeTruthy();
  return { token: body.data.token as string };
}

test.describe('完整AI工作流E2E测试', () => {
  let token: string;

  test.beforeAll(async ({ request }) => {
    await ensureBackendHealthy(request);
    const login = await loginViaApi(request);
    token = login.token;
  });

  test('完整流程：需求 → 意图识别 → 能力配置 → 代码生成 → 验证', async ({ page, request }) => {
    test.setTimeout(600_000); // 10分钟超时（AI生成耗时）

    // Step 1: 登录
    await page.goto('/login');
    await page.getByLabel('用户名或邮箱').fill(E2E_USERNAME);
    await page.getByLabel('密码').fill(E2E_PASSWORD);
    await page.getByRole('button', { name: '登录' }).click();

    await expect.poll(
      () => page.evaluate(() => localStorage.getItem('auth_token')),
      { timeout: 10_000 }
    ).not.toBeNull();

    // Step 2: 提交AI驱动的电商平台需求
    await page.goto('/');
    const requirementText = `创建一个AI驱动的电商平台，包含以下功能：
1. 商品管理（CRUD操作）
2. 订单处理（支付宝/微信支付集成）
3. 用户认证（JWT令牌）
4. AI智能推荐（使用OpenAI API）
5. 实时库存管理`;

    const input = page.locator('textarea[placeholder*="在这里输入你想做什么"]');
    await input.fill(requirementText);
    await page.getByRole('button', { name: '生成' }).click();

    // Step 3: 等待意图识别完成
    await expect(page.getByText('深度分析')).toBeVisible({ timeout: 60_000 });

    // 等待分析完成（SSE流式响应）
    await page.waitForTimeout(5000);

    // Step 4: 验证意图识别结果（通过API）
    const routeResp = await request.post(api('/v2/plan-routing/route'), {
      headers: { authorization: token },
      timeout: API_TIMEOUT_MS,
      data: { userRequirement: requirementText },
    });
    expect(routeResp.ok()).toBeTruthy();

    const routeBody = await routeResp.json();
    expect(routeBody.code).toBe('0000');

    const data = routeBody.data;
    expect(data.intent).toMatch(/DESIGN_FROM_SCRATCH|HYBRID/);
    expect(data.appSpecId).toBeTruthy();

    const appSpecId = data.appSpecId as string;
    console.log(`✅ 意图识别完成: ${data.intent}, appSpecId: ${appSpecId}`);

    // Step 5: 配置JeecgBoot能力（通过API）
    // 5.1 配置认证能力
    const authConfigResp = await request.post(api(`/v1/projects/${appSpecId}/capabilities`), {
      headers: { authorization: token },
      timeout: 30_000,
      data: {
        capabilityCode: 'auth',
        configValues: {
          jwtSecret: 'test-jwt-secret-key-for-e2e',
          tokenExpiry: '7d',
        },
      },
    });
    expect(authConfigResp.ok()).toBeTruthy();
    console.log('✅ 认证能力配置完成');

    // 5.2 配置支付宝支付能力
    const alipayConfigResp = await request.post(api(`/v1/projects/${appSpecId}/capabilities`), {
      headers: { authorization: token },
      timeout: 30_000,
      data: {
        capabilityCode: 'payment_alipay',
        configValues: {
          appId: 'test-alipay-app-id',
          privateKey: 'test-private-key',
          publicKey: 'test-public-key',
        },
      },
    });
    expect(alipayConfigResp.ok()).toBeTruthy();
    console.log('✅ 支付宝支付能力配置完成');

    // 5.3 配置AI Agent能力
    const aiAgentConfigResp = await request.post(api(`/v1/projects/${appSpecId}/capabilities`), {
      headers: { authorization: token },
      timeout: 30_000,
      data: {
        capabilityCode: 'ai_agent_connect',
        configValues: {
          agentType: 'OPENAI',
          baseUrl: 'https://api.openai.com/v1',
          apiKey: 'sk-test-key-for-e2e',
        },
      },
    });
    expect(aiAgentConfigResp.ok()).toBeTruthy();
    console.log('✅ AI Agent能力配置完成');

    // Step 6: 验证能力配置已加密存储
    const capabilitiesResp = await request.get(api(`/v1/projects/${appSpecId}/capabilities`), {
      headers: { authorization: token },
      timeout: 30_000,
    });
    expect(capabilitiesResp.ok()).toBeTruthy();

    const capabilitiesBody = await capabilitiesResp.json();
    expect(capabilitiesBody.code).toBe('0000');
    expect(capabilitiesBody.data).toHaveLength(3);

    // 验证敏感字段已脱敏
    const aiAgentConfig = capabilitiesBody.data.find((c: { capabilityCode: string }) => c.capabilityCode === 'ai_agent_connect');
    expect(aiAgentConfig.configValues.apiKey).toMatch(/\*\*\*/);
    console.log('✅ 能力配置已加密存储并脱敏');

    // Step 7: 确认设计并触发G3代码生成
    const confirmDesignResp = await request.post(api(`/v2/plan-routing/${appSpecId}/confirm-design`), {
      headers: { authorization: token },
      timeout: 30_000,
    });
    expect(confirmDesignResp.ok()).toBeTruthy();
    console.log('✅ 设计确认成功');

    // Step 8: 触发代码生成（G3编排器）
    const executeResp = await request.post(api(`/v2/plan-routing/${appSpecId}/execute-code-generation`), {
      headers: { authorization: token },
      timeout: API_TIMEOUT_MS,
      data: {},
    });
    expect(executeResp.ok()).toBeTruthy();

    const executeBody = await executeResp.json();
    expect(executeBody.code).toBe('0000');
    expect(executeBody.data?.jobId).toBeTruthy();

    const jobId = executeBody.data.jobId as string;
    console.log(`✅ G3任务已创建: ${jobId}`);

    // Step 9: 轮询G3任务状态直到完成
    let jobStatus = 'QUEUED';
    let attempts = 0;
    const maxAttempts = 60; // 最多轮询10分钟（每10秒一次）

    while (jobStatus !== 'COMPLETED' && jobStatus !== 'FAILED' && attempts < maxAttempts) {
      await page.waitForTimeout(10_000); // 等待10秒

      const statusResp = await request.get(api(`/v1/g3/jobs/${jobId}`), {
        headers: { authorization: token },
        timeout: 30_000,
      });

      if (statusResp.ok()) {
        const statusBody = await statusResp.json();
        jobStatus = statusBody.data?.status || 'UNKNOWN';
        const phase = statusBody.data?.phase || 'UNKNOWN';
        console.log(`⏳ G3任务状态: ${jobStatus}, 阶段: ${phase}`);
      }

      attempts++;
    }

    expect(jobStatus).toBe('COMPLETED');
    console.log('✅ G3代码生成完成');

    // Step 10: 验证生成的产物
    const artifactsResp = await request.get(api(`/v1/g3/jobs/${jobId}/artifacts`), {
      headers: { authorization: token },
      timeout: 30_000,
    });
    expect(artifactsResp.ok()).toBeTruthy();

    const artifactsBody = await artifactsResp.json();
    expect(artifactsBody.code).toBe('0000');
    expect(artifactsBody.data).toBeTruthy();

    const artifacts = artifactsBody.data;

    // 验证OpenAPI契约
    const contractArtifact = artifacts.find((a: { artifactType: string }) => a.artifactType === 'CONTRACT');
    expect(contractArtifact).toBeTruthy();
    expect(contractArtifact.content).toContain('openapi: 3.0');
    expect(contractArtifact.content).toContain('/api/products');
    expect(contractArtifact.content).toContain('/api/orders');
    expect(contractArtifact.content).toContain('/api/recommendations'); // AI集成
    console.log('✅ OpenAPI契约验证通过');

    // 验证数据库Schema
    const schemaArtifact = artifacts.find((a: { artifactType: string }) => a.artifactType === 'SCHEMA');
    expect(schemaArtifact).toBeTruthy();
    expect(schemaArtifact.content).toContain('CREATE TABLE products');
    expect(schemaArtifact.content).toContain('CREATE TABLE orders');
    expect(schemaArtifact.content).toContain('CREATE TABLE users');
    console.log('✅ 数据库Schema验证通过');

    // 验证生成的Java代码
    const entityArtifacts = artifacts.filter((a: { artifactType: string }) => a.artifactType === 'ENTITY');
    expect(entityArtifacts.length).toBeGreaterThanOrEqual(3); // Product, Order, User

    const serviceArtifacts = artifacts.filter((a: { artifactType: string }) => a.artifactType === 'SERVICE');
    expect(serviceArtifacts.length).toBeGreaterThanOrEqual(3);

    const controllerArtifacts = artifacts.filter((a: { artifactType: string }) => a.artifactType === 'CONTROLLER');
    expect(controllerArtifacts.length).toBeGreaterThanOrEqual(3);

    console.log(`✅ 代码产物验证通过: ${entityArtifacts.length} Entities, ${serviceArtifacts.length} Services, ${controllerArtifacts.length} Controllers`);

    // Step 11: 验证质量门控
    const validationResp = await request.get(api(`/v1/g3/jobs/${jobId}/validation`), {
      headers: { authorization: token },
      timeout: 30_000,
    });
    expect(validationResp.ok()).toBeTruthy();

    const validationBody = await validationResp.json();
    expect(validationBody.code).toBe('0000');

    const validation = validationBody.data;
    expect(validation.compilationSuccess).toBe(true);
    expect(validation.testCoverage).toBeGreaterThanOrEqual(85);
    console.log(`✅ 质量门控通过: 编译成功, 测试覆盖率 ${validation.testCoverage}%`);

    // Step 12: 验证AI能力集成到生成的代码中
    const recommendationServiceArtifact = artifacts.find((a: { artifactType: string; filePath?: string }) =>
      a.artifactType === 'SERVICE' && a.filePath?.includes('Recommendation')
    );

    if (recommendationServiceArtifact) {
      expect(recommendationServiceArtifact.content).toContain('OpenAI');
      expect(recommendationServiceArtifact.content).toContain('apiKey');
      console.log('✅ AI能力已集成到生成的代码中');
    }

    // Step 13: 验证JeecgBoot能力集成
    const authServiceArtifact = artifacts.find((a: { artifactType: string; filePath?: string }) =>
      a.artifactType === 'SERVICE' && a.filePath?.includes('Auth')
    );

    if (authServiceArtifact) {
      expect(authServiceArtifact.content).toContain('JWT');
      console.log('✅ 认证能力已集成');
    }

    const paymentServiceArtifact = artifacts.find((a: { artifactType: string; filePath?: string }) =>
      a.artifactType === 'SERVICE' && a.filePath?.includes('Payment')
    );

    if (paymentServiceArtifact) {
      expect(paymentServiceArtifact.content).toContain('Alipay');
      console.log('✅ 支付能力已集成');
    }

    console.log('🎉 完整AI工作流E2E测试通过！');
  });

  test('意图识别准确性测试', async ({ request }) => {
    test.setTimeout(120_000);

    const testCases = [
      {
        requirement: '仿照 airbnb.com 做一个民宿预订平台',
        expectedIntent: 'CLONE_EXISTING_WEBSITE',
      },
      {
        requirement: '创建一个技术博客平台，支持Markdown编辑',
        expectedIntent: 'DESIGN_FROM_SCRATCH',
      },
      {
        requirement: '参考 github.com 的设计，但要添加AI代码审查功能',
        expectedIntent: 'HYBRID',
      },
    ];

    for (const testCase of testCases) {
      const resp = await request.post(api('/v2/plan-routing/route'), {
        headers: { authorization: token },
        timeout: API_TIMEOUT_MS,
        data: { userRequirement: testCase.requirement },
      });

      expect(resp.ok()).toBeTruthy();
      const body = await resp.json();
      expect(body.code).toBe('0000');
      expect(body.data.intent).toBe(testCase.expectedIntent);

      console.log(`✅ 意图识别正确: "${testCase.requirement}" → ${testCase.expectedIntent}`);
    }
  });

  test('技术点拆解验证', async ({ request, page }) => {
    test.setTimeout(180_000);

    const requirement = '创建一个在线教育平台，包含课程管理、学生管理、视频播放、作业提交、成绩统计功能';

    const resp = await request.post(api('/v2/plan-routing/route'), {
      headers: { authorization: token },
      timeout: API_TIMEOUT_MS,
      data: { userRequirement: requirement },
    });

    expect(resp.ok()).toBeTruthy();
    const body = await resp.json();
    expect(body.code).toBe('0000');

    const appSpecId = body.data.appSpecId;

    // 触发Architect Agent生成契约
    const confirmResp = await request.post(api(`/v2/plan-routing/${appSpecId}/confirm-design`), {
      headers: { authorization: token },
      timeout: 30_000,
    });
    expect(confirmResp.ok()).toBeTruthy();

    const executeResp = await request.post(api(`/v2/plan-routing/${appSpecId}/execute-code-generation`), {
      headers: { authorization: token },
      timeout: API_TIMEOUT_MS,
      data: {},
    });
    expect(executeResp.ok()).toBeTruthy();

    const jobId = (await executeResp.json()).data?.jobId;

    // 等待PLANNING阶段完成
    await page.waitForTimeout(60_000);

    // 获取生成的契约
    const artifactsResp = await request.get(api(`/v1/g3/jobs/${jobId}/artifacts`), {
      headers: { authorization: token },
      timeout: 30_000,
    });

    const artifacts = (await artifactsResp.json()).data;
    const contract = artifacts.find((a: { artifactType: string }) => a.artifactType === 'CONTRACT');

    // 验证技术点拆解
    expect(contract.content).toContain('/api/courses'); // 课程管理
    expect(contract.content).toContain('/api/students'); // 学生管理
    expect(contract.content).toContain('/api/videos'); // 视频播放
    expect(contract.content).toContain('/api/assignments'); // 作业提交
    expect(contract.content).toContain('/api/grades'); // 成绩统计

    console.log('✅ 技术点拆解验证通过：所有功能模块已识别');
  });
});
