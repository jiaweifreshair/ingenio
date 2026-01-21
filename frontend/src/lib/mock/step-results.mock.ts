/**
 * Mock数据 - 用于测试步骤结果展示UI
 */

import type {
  Step1Result,
  Step2Result,
  Step3Result,
  Step4Result,
  Step5Result,
  StepResult
} from '@/types/analysis-step-results';

/**
 * Step 1 Mock数据 - 需求语义解析
 */
export const mockStep1Result: StepResult = {
  step: 1,
  data: {
    summary: '设计一个青少年压力管理智能系统，为学生、班主任和老师提供一体化支持，包括压力评估、任务管理和数据分析功能。',
    entities: ['学生 (Student)', '老师 (Teacher)', '班主任 (ClassTeacher)', '压力评估 (StressAssessment)', '任务 (Task)'],
    actions: ['创建压力评估', '管理学生信息', '生成评估报告', '分配任务', '查看数据分析'],
    businessScenario: '学校心理健康管理系统，支持多角色协作，帮助学校及时发现和干预学生心理健康问题'
  } as Step1Result
};

/**
 * Step 2 Mock数据 - 实体关系建模
 */
export const mockStep2Result: StepResult = {
  step: 2,
  data: {
    entities: [
      {
        name: 'User',
        displayName: '用户',
        fields: [
          { name: 'id', type: 'UUID', description: '主键' },
          { name: 'username', type: 'String', description: '用户名' },
          { name: 'email', type: 'String', description: '邮箱' },
          { name: 'role', type: 'Enum', description: 'Student/Teacher/Admin' },
          { name: 'created_at', type: 'Timestamp' }
        ]
      },
      {
        name: 'StressAssessment',
        displayName: '压力评估',
        fields: [
          { name: 'id', type: 'UUID', description: '主键' },
          { name: 'student_id', type: 'UUID', description: '学生ID (FK)' },
          { name: 'score', type: 'Integer', description: '评分 (0-100)' },
          { name: 'level', type: 'Enum', description: 'LOW/MEDIUM/HIGH' },
          { name: 'created_at', type: 'Timestamp' }
        ]
      },
      {
        name: 'Task',
        displayName: '任务',
        fields: [
          { name: 'id', type: 'UUID', description: '主键' },
          { name: 'title', type: 'String', description: '任务标题' },
          { name: 'student_id', type: 'UUID', description: '学生ID (FK)' },
          { name: 'teacher_id', type: 'UUID', description: '老师ID (FK)' },
          { name: 'status', type: 'Enum', description: 'PENDING/IN_PROGRESS/COMPLETED' },
          { name: 'due_date', type: 'Date' }
        ]
      }
    ],
    relationships: [
      {
        from: 'User',
        to: 'StressAssessment',
        type: 'ONE_TO_MANY',
        description: '一个学生可以有多次压力评估'
      },
      {
        from: 'User',
        to: 'Task',
        type: 'ONE_TO_MANY',
        description: '一个学生可以有多个任务'
      },
      {
        from: 'User',
        to: 'Task',
        type: 'ONE_TO_MANY',
        description: '一个老师可以分配多个任务'
      }
    ]
  } as Step2Result
};

/**
 * Step 3 Mock数据 - 功能意图识别
 */
export const mockStep3Result: StepResult = {
  step: 3,
  data: {
    intent: 'DESIGN',
    confidence: 0.85,
    keywords: ['青少年', '压力管理', '一体化支持', '心理健康', '多角色协作'],
    customizationRequirement: '支持多角色协作的心理健康管理系统，需要实时数据分析和预警功能',
    modules: [
      {
        name: 'UserManagement',
        displayName: '用户管理',
        description: '管理学生、老师、班主任等多角色用户',
        features: ['用户注册/登录', '角色权限管理', '用户信息维护']
      },
      {
        name: 'StressAssessment',
        displayName: '压力评估系统',
        description: '提供标准化的压力评估问卷和结果分析',
        features: ['自评问卷', '评估报告生成', '历史记录查询']
      },
      {
        name: 'TaskManagement',
        displayName: '任务管理',
        description: '老师为学生分配和跟踪任务',
        features: ['任务创建/分配', '进度跟踪', '完成状态管理']
      },
      {
        name: 'Analytics',
        displayName: '数据分析',
        description: '压力趋势分析和可视化报表',
        features: ['压力趋势分析', '可视化报表', '预警提醒']
      }
    ]
  } as Step3Result
};

/**
 * Step 4 Mock数据 - 技术架构选型
 */
export const mockStep4Result: StepResult = {
  step: 4,
  data: {
    frontend: [
      { name: 'React', version: '19', description: 'UI框架' },
      { name: 'Next.js', version: '15', description: '全栈框架' },
      { name: 'TailwindCSS', version: '3.x', description: 'CSS框架' },
      { name: 'Zustand', version: '4.x', description: '状态管理' },
      { name: 'React Query', description: '数据获取' }
    ],
    backend: [
      { name: 'Spring Boot', version: '3.4', description: 'Java后端框架' },
      { name: 'PostgreSQL', version: '15', description: '关系型数据库' },
      { name: 'Redis', version: '7', description: '缓存' },
      { name: 'MyBatis-Plus', version: '3.5.8', description: 'ORM框架' }
    ],
    architecturePatterns: ['前后端分离', 'RESTful API', 'JWT认证', '微服务架构 (可选)'],
    thirdPartyServices: [
      { name: '七牛云', purpose: '文件存储' },
      { name: '阿里云短信', purpose: '通知服务' }
    ],
    reasoning: 'React生态成熟，组件丰富，适合快速开发；Spring Boot企业级稳定，适合复杂业务逻辑；PostgreSQL支持复杂查询和数据分析；Redis提供高性能缓存。'
  } as Step4Result
};

/**
 * Step 5 Mock数据 - 复杂度与风险评估
 */
export const mockStep5Result: StepResult = {
  step: 5,
  data: {
    complexityScore: 7,
    complexityBreakdown: {
      frontend: 8,
      backend: 7,
      database: 6,
      integration: 5
    },
    risks: [
      {
        level: 'HIGH',
        description: '多角色权限管理复杂，需要细粒度的权限控制',
        category: 'COMPLEXITY'
      },
      {
        level: 'HIGH',
        description: '实时数据同步性能要求高，需要优化查询和缓存策略',
        category: 'PERFORMANCE'
      },
      {
        level: 'MEDIUM',
        description: '评估算法准确性需要专业心理学知识验证',
        category: 'OTHER'
      },
      {
        level: 'MEDIUM',
        description: '学生隐私数据保护需要符合相关法规',
        category: 'SECURITY'
      },
      {
        level: 'LOW',
        description: '基础CRUD操作实现难度低',
        category: 'COMPLEXITY'
      },
      {
        level: 'LOW',
        description: '静态页面渲染性能良好',
        category: 'PERFORMANCE'
      }
    ],
    estimatedWorkload: {
      featureCount: 25,
      estimatedWeeks: '6-8周',
      teamSize: '3-4人'
    },
    mitigations: [
      '使用成熟的权限框架 (Spring Security) 简化权限管理',
      '引入Redis缓存减轻数据库压力，优化查询性能',
      '采用增量开发，分阶段交付，降低风险',
      '咨询心理学专家验证评估算法的科学性',
      '严格遵守数据保护法规，实施数据加密和访问控制'
    ]
  } as Step5Result
};

/**
 * 所有步骤的Mock数据
 */
export const mockStepResults: StepResult[] = [
  mockStep1Result,
  mockStep2Result,
  mockStep3Result,
  mockStep4Result,
  mockStep5Result
];
