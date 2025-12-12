import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';

/**
 * 发布配置接口
 */
interface PublishConfig {
  /** 应用名称 */
  name: string;
  /** 应用描述 */
  description: string;
  /** 自定义域名 */
  customDomain?: string;
  /** 发布平台 */
  platforms: Array<'web' | 'mobile' | 'desktop'>;
  /** 版本号 */
  version: string;
  /** 环境配置 */
  environment?: 'development' | 'staging' | 'production';
}

/**
 * 发布状态枚举
 */
enum PublishStatus {
  PENDING = 'pending',
  BUILDING = 'building',
  DEPLOYING = 'deploying',
  PUBLISHED = 'published',
  FAILED = 'failed'
}

/**
 * 发布记录数据结构
 */
interface PublishRecord {
  id: string;
  appSpecId: string;
  config: PublishConfig;
  status: PublishStatus;
  createdAt: string;
  completedAt?: string;
  deploymentUrl?: string;
  buildLog?: string[];
  errorMessage?: string;
}

// 内存存储（生产环境应使用数据库）
const publishStore = new Map<string, PublishRecord>();

/**
 * GET /api/v1/publish/[id]
 * 获取发布记录
 */
export async function GET(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`📄 Fetching publish record: ${id}`);

  try {
    const appSpecId = id;

    // 从存储中获取发布记录
    let publishRecord = publishStore.get(appSpecId);

    // 如果不存在，返回一个示例配置
    if (!publishRecord) {
      console.log(`📄 Creating demo publish record for: ${appSpecId}`);

      publishRecord = {
        id: `publish-${Date.now()}`,
        appSpecId,
        config: {
          name: '校园活动报名系统',
          description: '一个基于AI生成的校园活动管理系统，支持活动发布、在线报名、签到和数据统计功能',
          customDomain: '',
          platforms: ['web'],
          version: '1.0.0',
          environment: 'production'
        },
        status: PublishStatus.PENDING,
        createdAt: new Date().toISOString(),
        buildLog: []
      };

      // 存储到内存
      publishStore.set(appSpecId, publishRecord);
    }

    return NextResponse.json<APIResponse<PublishRecord>>({
      success: true,
      data: publishRecord,
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 50,
      },
    });

  } catch (error) {
    console.error(`❌ Error fetching publish record:`, error);

    return NextResponse.json<APIResponse<PublishRecord>>({
      success: false,
      error: error instanceof Error ? error.message : '获取发布记录失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}

/**
 * POST /api/v1/publish/[id]
 * 开始发布流程
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  console.log(`🚀 Starting publish process: ${id}`);

  try {
    const appSpecId = id;
    const body = await request.json();
    const { config } = body;

    // 验证配置
    if (!config || !config.name || !config.description) {
      return NextResponse.json<APIResponse<PublishRecord>>({
        success: false,
        error: '请填写完整的应用信息',
        metadata: {
          requestId: `req_${Date.now()}`,
          timestamp: new Date().toISOString(),
          latencyMs: 0,
        },
      }, { status: 400 });
    }

    // 创建发布记录
    const publishRecord: PublishRecord = {
      id: `publish-${Date.now()}`,
      appSpecId,
      config,
      status: PublishStatus.BUILDING,
      createdAt: new Date().toISOString(),
      buildLog: ['开始构建...', '编译前端资源...', '优化静态文件...'],
    };

    publishStore.set(appSpecId, publishRecord);

    // 启动模拟发布流程
    startPublishSimulation(appSpecId);

    return NextResponse.json<APIResponse<PublishRecord>>({
      success: true,
      data: publishRecord,
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 100,
      },
    });

  } catch (error) {
    console.error(`❌ Error starting publish process:`, error);

    return NextResponse.json<APIResponse<PublishRecord>>({
      success: false,
      error: error instanceof Error ? error.message : '启动发布失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}

/**
 * 模拟发布流程
 */
async function startPublishSimulation(appSpecId: string) {
  const record = publishStore.get(appSpecId);
  if (!record) return;

  // 构建阶段（5秒）
  await new Promise(resolve => setTimeout(resolve, 5000));

  const buildingRecord = {
    ...record,
    status: PublishStatus.BUILDING,
    buildLog: [
      '开始构建...',
      '编译前端资源...',
      '优化静态文件...',
      '生成部署包...',
      '构建完成！'
    ]
  };
  publishStore.set(appSpecId, buildingRecord);

  // 部署阶段（3秒）
  await new Promise(resolve => setTimeout(resolve, 3000));

  const deployingRecord = {
    ...buildingRecord,
    status: PublishStatus.DEPLOYING,
    buildLog: [
      ...buildingRecord.buildLog!,
      '开始部署...',
      '上传到服务器...',
      '配置域名...',
      '启动服务...'
    ]
  };
  publishStore.set(appSpecId, deployingRecord);

  // 完成阶段（2秒）
  await new Promise(resolve => setTimeout(resolve, 2000));

  const deploymentUrl = record.config.customDomain
    ? `https://${record.config.customDomain}.app.ingenio.dev`
    : `https://${appSpecId}.app.ingenio.dev`;

  const completedRecord = {
    ...deployingRecord,
    status: PublishStatus.PUBLISHED,
    completedAt: new Date().toISOString(),
    deploymentUrl,
    buildLog: [
      ...deployingRecord.buildLog!,
      '部署完成！',
      `应用已上线: ${deploymentUrl}`
    ]
  };

  publishStore.set(appSpecId, completedRecord);
  console.log(`✅ Publish completed: ${appSpecId} -> ${deploymentUrl}`);
}