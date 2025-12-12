import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';

/**
 * AppSpec数据结构
 */
interface AppSpec {
  id: string;
  version: string;
  tenantId: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
  userRequirement: string;
  projectType?: string;
  planResult: {
    modules: Array<{
      name: string;
      description: string;
      priority: string;
      complexity: number;
      dependencies: string[];
      dataModels: string[];
      pages: string[];
    }>;
    complexityScore: number;
    reasoning: string;
    suggestedTechStack: string[];
    estimatedHours: number;
    recommendations: string;
  };
  validateResult: {
    isValid: boolean;
    qualityScore: number;
    issues: Array<{
      severity: string;
      type: string;
      message: string;
      location?: string;
    }>;
    suggestions: string[];
  };
  isValid: boolean;
  qualityScore: number;
  status: string;
  generatedAt: string;
  durationMs: number;
}

// 内存存储（生产环境应使用数据库）
const appSpecStore = new Map<string, AppSpec>();

/**
 * GET /api/v1/appspecs/[id]
 * 获取AppSpec详情
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`🔍 Fetching AppSpec: ${id}`);

  try {
    const appSpecId = id;

    // 从存储中获取AppSpec
    let appSpec = appSpecStore.get(appSpecId);

    // 如果不存在，返回一个示例数据（用于demo）
    if (!appSpec) {
      console.log(`📄 Creating demo AppSpec for: ${appSpecId}`);

      appSpec = {
        id: appSpecId,
        version: '1.0.0',
        tenantId: 'demo-tenant',
        userId: 'demo-user',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        userRequirement: '创建一个校园活动报名系统，支持活动发布、在线报名、签到和数据统计',
        projectType: 'campus-management',
        planResult: {
          modules: [
            {
              name: '活动管理模块',
              description: '处理活动发布、编辑、删除等功能',
              priority: 'high',
              complexity: 3,
              dependencies: ['用户管理模块'],
              dataModels: ['Event', 'Registration', 'Attendance'],
              pages: ['活动列表页', '活动详情页', '创建活动页']
            },
            {
              name: '报名管理模块',
              description: '处理用户报名、取消报名、报名统计等功能',
              priority: 'high',
              complexity: 2,
              dependencies: ['活动管理模块', '用户管理模块'],
              dataModels: ['Registration', 'Participant'],
              pages: ['报名页', '我的报名页']
            },
            {
              name: '签到管理模块',
              description: '处理现场签到、签到统计、签到导出等功能',
              priority: 'medium',
              complexity: 2,
              dependencies: ['活动管理模块', '报名管理模块'],
              dataModels: ['Attendance', 'AttendanceStats'],
              pages: ['签到页', '签到统计页']
            },
            {
              name: '数据统计模块',
              description: '处理活动数据分析、报表生成、数据导出等功能',
              priority: 'medium',
              complexity: 3,
              dependencies: ['活动管理模块', '报名管理模块', '签到管理模块'],
              dataModels: ['Report', 'Analytics'],
              pages: ['统计报表页', '数据导出页']
            }
          ],
          complexityScore: 8.2,
          reasoning: '基于校园活动报名需求，设计了完整的活动生命周期管理流程，从活动发布到数据统计分析的全链路解决方案',
          suggestedTechStack: ['Next.js', 'TypeScript', 'Tailwind CSS', 'PostgreSQL', 'Redis', 'Chart.js'],
          estimatedHours: 48,
          recommendations: '建议优先实现核心的活动管理和报名功能，然后逐步完善签到和统计功能'
        },
        validateResult: {
          isValid: true,
          qualityScore: 88,
          issues: [
            {
              severity: 'warning',
              type: 'security',
              message: '建议添加身份验证和权限控制',
              location: 'API接口'
            },
            {
              severity: 'info',
              type: 'performance',
              message: '建议添加数据缓存优化',
              location: '数据访问层'
            }
          ],
          suggestions: ['考虑添加移动端适配', '建议实现邮件通知功能', '添加数据备份机制']
        },
        isValid: true,
        qualityScore: 88,
        status: 'completed',
        generatedAt: new Date().toISOString(),
        durationMs: 3000
      };

      // 存储到内存
      appSpecStore.set(appSpecId, appSpec);
    }

    console.log(`✅ Retrieved AppSpec: ${appSpecId}`);

    return NextResponse.json<APIResponse<AppSpec>>({
      success: true,
      data: appSpec,
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 50,
      },
    });

  } catch (error) {
    console.error(`❌ Error fetching AppSpec:`, error);

    return NextResponse.json<APIResponse<AppSpec>>({
      success: false,
      error: error instanceof Error ? error.message : '获取AppSpec失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}

/**
 * PUT /api/v1/appspecs/[id]
 * 更新AppSpec
 */
export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`📝 Updating AppSpec: ${id}`);

  try {
    const appSpecId = id;
    const body = await request.json();

    const existingAppSpec = appSpecStore.get(appSpecId);
    if (!existingAppSpec) {
      return NextResponse.json<APIResponse<AppSpec>>({
        success: false,
        error: 'AppSpec不存在',
        metadata: {
          requestId: `req_${Date.now()}`,
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      }, { status: 404 });
    }

    const updatedAppSpec: AppSpec = {
      ...existingAppSpec,
      ...body,
      id: appSpecId,
      updatedAt: new Date().toISOString(),
    };

    appSpecStore.set(appSpecId, updatedAppSpec);

    return NextResponse.json<APIResponse<AppSpec>>({
      success: true,
      data: updatedAppSpec,
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 100,
      },
    });

  } catch (error) {
    console.error(`❌ Error updating AppSpec:`, error);

    return NextResponse.json<APIResponse<AppSpec>>({
      success: false,
      error: error instanceof Error ? error.message : '更新AppSpec失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}

/**
 * DELETE /api/v1/appspecs/[id]
 * 删除AppSpec
 */
export async function DELETE(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`🗑️ Deleting AppSpec: ${id}`);

  try {
    const appSpecId = id;

    if (!appSpecStore.has(appSpecId)) {
      return NextResponse.json<APIResponse<{ deleted: boolean }>>({
        success: false,
        error: 'AppSpec不存在',
        metadata: {
          requestId: `req_${Date.now()}`,
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      }, { status: 404 });
    }

    appSpecStore.delete(appSpecId);

    return NextResponse.json<APIResponse<{ deleted: boolean }>>({
      success: true,
      data: { deleted: true },
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 50,
      },
    });

  } catch (error) {
    console.error(`❌ Error deleting AppSpec:`, error);

    return NextResponse.json<APIResponse<{ deleted: boolean }>>({
      success: false,
      error: error instanceof Error ? error.message : '删除AppSpec失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}