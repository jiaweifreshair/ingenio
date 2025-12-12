import { NextRequest, NextResponse } from 'next/server';
import { APIResponse } from '@/lib/api/client';
import type { Project, PageResult } from '@/types/project';
import { ProjectStatus as ProjectStatusEnum, ProjectVisibility } from '@/types/project';

// 内存存储（生产环境应使用数据库）
const projectStore = new Map<string, Project>();

// Helper函数：创建Mock项目对象
function createMockProject(partial: Partial<Project> & Pick<Project, 'id' | 'name' | 'description'>): Project {
  return {
    tenantId: 'default-tenant',
    userId: 'default-user',
    visibility: ProjectVisibility.PUBLIC,
    viewCount: Math.floor(Math.random() * 2000),
    likeCount: Math.floor(Math.random() * 100),
    forkCount: Math.floor(Math.random() * 50),
    commentCount: Math.floor(Math.random() * 80),
    status: ProjectStatusEnum.PUBLISHED,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    ...partial,
  };
}

// 初始化示例数据
function initializeSampleProjects() {
  if (projectStore.size > 0) return;

  const sampleProjects: Project[] = [
    createMockProject({ id: 'proj-001', appSpecId: 'test-app-123', name: '校园活动报名系统', description: '支持活动发布、在线报名、签到和数据统计的综合管理平台', coverImageUrl: '/images/campus-event.png', tags: ['校园', '活动管理', '报名系统'] }),
    createMockProject({ id: 'proj-002', appSpecId: 'test-app-124', name: '在线课程学习平台', description: '提供视频课程、在线测验、学习进度跟踪等功能', coverImageUrl: '/images/online-course.png', tags: ['教育', '在线学习', '视频课程'] }),
    createMockProject({ id: 'proj-003', appSpecId: 'test-app-125', name: '社团管理系统', description: '社团活动组织、成员管理、财务管理一体化解决方案', status: ProjectStatusEnum.DRAFT, coverImageUrl: '/images/club-management.png', tags: ['社团', '管理系统', '校园'] }),
    createMockProject({ id: 'proj-004', appSpecId: 'test-app-126', name: '智能排课系统', description: '基于AI的智能排课，自动处理时间冲突和教室分配', coverImageUrl: '/images/scheduling.png', tags: ['AI', '排课', '教务管理'] }),
    createMockProject({ id: 'proj-005', appSpecId: 'test-app-127', name: '图书馆座位预约', description: '实时座位状态查看、在线预约、签到签退管理', coverImageUrl: '/images/library.png', tags: ['预约系统', '图书馆', '座位管理'] }),
    createMockProject({ id: 'proj-006', appSpecId: 'test-app-128', name: '失物招领平台', description: '校园失物信息发布、认领流程管理、失物统计分析', status: ProjectStatusEnum.DRAFT, coverImageUrl: '/images/lost-found.png', tags: ['校园服务', '失物招领'] }),
    createMockProject({ id: 'proj-007', appSpecId: 'test-app-129', name: '宿舍报修系统', description: '在线报修、维修进度跟踪、报修统计和评价反馈', coverImageUrl: '/images/dormitory-repair.png', tags: ['报修', '宿舍管理', '后勤服务'] }),
    createMockProject({ id: 'proj-008', appSpecId: 'test-app-130', name: '校园二手交易平台', description: '安全可信的校园闲置物品交易、社交分享平台', coverImageUrl: '/images/marketplace.png', tags: ['二手交易', '社交', '校园'] }),
    createMockProject({ id: 'proj-009', appSpecId: 'test-app-131', name: '学生评教系统', description: '匿名评教、数据统计分析、教学质量评估报告生成', status: ProjectStatusEnum.ARCHIVED, coverImageUrl: '/images/evaluation.png', tags: ['评教', '教学质量', '数据分析'] }),
    createMockProject({ id: 'proj-010', appSpecId: 'test-app-132', name: '体育场馆预约', description: '场馆实时状态、在线预约、预约管理和使用统计', coverImageUrl: '/images/sports-venue.png', tags: ['场馆预约', '体育', '预约系统'] }),
    createMockProject({ id: 'proj-011', appSpecId: 'test-app-133', name: '校园问答社区', description: '学习交流、问题解答、知识分享的校园社区平台', status: ProjectStatusEnum.DRAFT, coverImageUrl: '/images/qa-community.png', tags: ['社区', '问答', '学习交流'] }),
    createMockProject({ id: 'proj-012', appSpecId: 'test-app-134', name: '毕业设计管理', description: '毕设选题、导师分配、进度管理、答辩安排一站式平台', status: ProjectStatusEnum.ARCHIVED, coverImageUrl: '/images/graduation-project.png', tags: ['毕业设计', '教务管理', '进度跟踪'] }),
  ];

  sampleProjects.forEach(project => {
    projectStore.set(project.id, project);
  });

  console.log(`📦 Initialized ${projectStore.size} sample projects`);
}

/**
 * GET /api/v1/projects
 * 分页查询项目列表，支持状态筛选和关键词搜索
 */
export async function GET(request: NextRequest) {
  console.log('📋 Fetching projects list');

  try {
    // 初始化示例数据
    initializeSampleProjects();

    // 解析查询参数
    const searchParams = request.nextUrl.searchParams;
    const current = parseInt(searchParams.get('current') || '1');
    const size = parseInt(searchParams.get('size') || '12');
    const status = searchParams.get('status') || '';
    const keyword = searchParams.get('keyword') || '';

    console.log(`🔍 Query params: page=${current}, size=${size}, status=${status}, keyword=${keyword}`);

    // 获取所有项目
    let projects = Array.from(projectStore.values());

    // 状态筛选
    if (status) {
      projects = projects.filter(p => p.status === status);
    }

    // 关键词搜索
    if (keyword) {
      const lowerKeyword = keyword.toLowerCase();
      projects = projects.filter(p =>
        p.name.toLowerCase().includes(lowerKeyword) ||
        p.description?.toLowerCase().includes(lowerKeyword) ||
        p.tags?.some(tag => tag.toLowerCase().includes(lowerKeyword))
      );
    }

    // 排序：按更新时间倒序
    projects.sort((a, b) =>
      new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    );

    // 分页
    const total = projects.length;
    const pages = Math.ceil(total / size);
    const start = (current - 1) * size;
    const end = start + size;
    const records = projects.slice(start, end);

    const result: PageResult<Project> = {
      records,
      current,
      size,
      total,
      pages,
      hasNext: current < pages,
      hasPrevious: current > 1,
    };

    console.log(`✅ Retrieved ${records.length}/${total} projects (page ${current}/${pages})`);

    return NextResponse.json<APIResponse<PageResult<Project>>>({
      success: true,
      data: result,
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 20,
      },
    });

  } catch (error) {
    console.error('❌ Error fetching projects:', error);

    return NextResponse.json<APIResponse<PageResult<Project>>>({
      success: false,
      error: error instanceof Error ? error.message : '获取项目列表失败',
      metadata: {
        requestId: `req_${Date.now()}`,
        timestamp: new Date().toISOString(),
        latencyMs: 0,
      },
    }, { status: 500 });
  }
}
