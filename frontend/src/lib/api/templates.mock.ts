/**
 * 模板Mock数据 - 仅供开发和设计阶段使用
 *
 * ⚠️ 警告：此文件仅用于开发环境Mock数据
 * 生产环境必须使用真实API，禁止导入此文件
 *
 * @deprecated 正式发布前应移除此文件的使用
 */

import {
  Template,
  TemplateCategory,
  TemplateDifficulty,
  TargetPlatform,
} from "@/types/template";

/**
 * Mock模板数据
 * 用途：开发环境、UI设计验证、Storybook展示
 *
 * 分类说明：
 * - SAFETY_CHALLENGE: 安全竞赛类（本期开放）- 对应智能生成式应用程序设计挑战赛四个组别
 * - 其他分类：暂时不可用，显示为"即将开放"状态
 */
export const MOCK_TEMPLATES: Template[] = [
  // ============================================================================
  // 安全竞赛类模板（本期开放）- 智能生成式应用程序设计挑战赛
  // ============================================================================
  {
    id: "safety-primary",
    name: "我的安全小卫士",
    description: "小学组：从想法到现实，强调趣味性、直观性与线性流程",
    detailedDescription:
      "专为小学生设计的安全教育应用模板。通过拍照 → AI识别 → 提示修复的游戏化体验，让孩子们在趣味互动中学习安全知识。界面简洁直观，操作流程线性易懂。",
    category: TemplateCategory.SAFETY_CHALLENGE,
    difficulty: TemplateDifficulty.SIMPLE,
    platforms: [TargetPlatform.WEB, TargetPlatform.WECHAT],
    features: [
      "拍照识别安全隐患",
      "AI智能分析",
      "游戏化学习体验",
      "安全知识问答",
      "���就徽章系统",
      "家长监护模式",
    ],
    coverImage: "/templates/safety-primary.jpg",
    screenshots: [],
    techStack: ["React", "Supabase", "TensorFlow.js"],
    usageCount: 856,
    rating: 4.9,
    createdAt: "2025-01-12",
    demoUrl: "/examples/primary",
    tags: ["安全竞赛", "小学组", "AI识别", "游戏化"],
  },
  {
    id: "safety-middle",
    name: "校园逻辑哨兵",
    description: "初中组：逻辑与流程，强调问题拆解与流程图表达",
    detailedDescription:
      "面向初中生的校园安全监控系统模板。展示「摄像头 → AI分析 → 触发报警」的完整链路，培养学生的逻辑思维和系统设计能力。支持流程图可视化和事件追踪。",
    category: TemplateCategory.SAFETY_CHALLENGE,
    difficulty: TemplateDifficulty.MEDIUM,
    platforms: [TargetPlatform.WEB, TargetPlatform.ANDROID],
    features: [
      "视频流AI分析",
      "异常行为检测",
      "报警触发机制",
      "流程图可视化",
      "事件日志追踪",
      "多摄像头管理",
    ],
    coverImage: "/templates/safety-middle.jpg",
    screenshots: [],
    techStack: ["React", "Spring Boot", "OpenCV", "WebSocket"],
    usageCount: 723,
    rating: 4.8,
    createdAt: "2025-01-11",
    demoUrl: "/examples/middle",
    tags: ["安全竞赛", "初中组", "视频分析", "流程设计"],
  },
  {
    id: "safety-high",
    name: "城市应急智慧中枢",
    description: "高中组：系统架构思维，强调分层架构与多智能体调度",
    detailedDescription:
      "高中生系统架构设计模板。城市级事件监控与联动处置大屏，展示分层架构设计和多智能体协作调度。培养学生的系统思维和架构设计能力。",
    category: TemplateCategory.SAFETY_CHALLENGE,
    difficulty: TemplateDifficulty.COMPLEX,
    platforms: [TargetPlatform.WEB],
    features: [
      "城市级监控大屏",
      "多智能体调度",
      "分层架构设计",
      "实时数据可视化",
      "应急预案管理",
      "跨部门联动",
    ],
    coverImage: "/templates/safety-high.jpg",
    screenshots: [],
    techStack: ["React", "Spring Boot", "Kafka", "ECharts", "WebSocket"],
    usageCount: 512,
    rating: 4.7,
    createdAt: "2025-01-10",
    demoUrl: "/examples/high",
    tags: ["安全竞赛", "高中组", "系统架构", "智能体"],
  },
  {
    id: "safety-vocational",
    name: "工地安全专家",
    description: "中职组：行业应用，强调真实岗位场景与流程优化",
    detailedDescription:
      "面向中职学生的工地安全管理系统模板。涵盖工地巡检、违章识别、整改闭环与报表功能，贴近真实工作场景，培养学生的职业技能和流程优化能力。",
    category: TemplateCategory.SAFETY_CHALLENGE,
    difficulty: TemplateDifficulty.MEDIUM,
    platforms: [TargetPlatform.WEB, TargetPlatform.ANDROID, TargetPlatform.IOS],
    features: [
      "工地巡检管理",
      "违章行为AI识别",
      "整改任务闭环",
      "安全报表统计",
      "人员定位追踪",
      "设备状态监控",
    ],
    coverImage: "/templates/safety-vocational.jpg",
    screenshots: [],
    techStack: ["React", "Spring Boot", "MyBatis-Plus", "YOLO"],
    usageCount: 634,
    rating: 4.8,
    createdAt: "2025-01-09",
    demoUrl: "/examples/vocational",
    tags: ["安全竞赛", "中职组", "工地安全", "行业应用"],
  },
  // ============================================================================
  // 其他分类模板（暂时不可用）
  // ============================================================================
  {
    id: "ecommerce-shop",
    name: "电商购物平台",
    description: "完整的在线购物平台，支持商品展示、购物车、订单管理",
    detailedDescription:
      "功能完整的电商平台模板，包含商品浏览、搜索、分类、购物车、订单管理、支付集成等核心功能。适合快速搭建在线商城。",
    category: TemplateCategory.ECOMMERCE,
    difficulty: TemplateDifficulty.COMPLEX,
    platforms: [TargetPlatform.ANDROID, TargetPlatform.IOS, TargetPlatform.WEB],
    features: [
      "商品展示和搜索",
      "购物车管理",
      "订单处理",
      "支付集成",
      "用户认证",
      "商品评价",
    ],
    coverImage: "/templates/ecommerce.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "Stripe"],
    usageCount: 1234,
    rating: 4.8,
    createdAt: "2025-01-10",
    tags: ["电商", "支付", "即将开放"],
  },
  {
    id: "social-network",
    name: "社交网络",
    description: "类似微博的社交平台，支持动态发布、关注、点赞评论",
    detailedDescription:
      "现代化社交网络应用模板，包含用户个人主页、动态发布、图片上传、关注系统、点赞评论、私信聊天等功能。",
    category: TemplateCategory.SOCIAL,
    difficulty: TemplateDifficulty.COMPLEX,
    platforms: [TargetPlatform.ANDROID, TargetPlatform.IOS],
    features: ["动态发布", "关注系统", "点赞评论", "私信聊天", "图片上传", "个人主页"],
    coverImage: "/templates/social.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "WebSocket"],
    usageCount: 987,
    rating: 4.6,
    createdAt: "2025-01-08",
    tags: ["社交", "实时通讯", "即将开放"],
  },
  {
    id: "todo-app",
    name: "任务管理工具",
    description: "简洁高效的待办事项管理，支持分类、提醒、统计",
    detailedDescription:
      "功能强大的任务管理应用，支持任务分类、优先级设置、到期提醒、完成统计、数据同步等功能。",
    category: TemplateCategory.TOOLS,
    difficulty: TemplateDifficulty.SIMPLE,
    platforms: [TargetPlatform.ANDROID, TargetPlatform.IOS, TargetPlatform.WEB, TargetPlatform.WECHAT],
    features: ["任务分类", "优先级管理", "提醒通知", "完成统计", "数据同步"],
    coverImage: "/templates/todo.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase"],
    usageCount: 2345,
    rating: 4.9,
    createdAt: "2025-01-12",
    tags: ["工具", "简单", "即将开放"],
  },
  {
    id: "online-course",
    name: "在线课程平台",
    description: "在线教育平台，支持课程管理、视频播放、作业系���",
    detailedDescription:
      "完整的在线教育解决方案，包含课程管理、视频播放、作业提交、成绩统计、师生互动等功能。",
    category: TemplateCategory.EDUCATION,
    difficulty: TemplateDifficulty.COMPLEX,
    platforms: [TargetPlatform.ANDROID, TargetPlatform.IOS, TargetPlatform.WEB],
    features: ["课程管理", "视频播放", "作业系统", "成绩统计", "师生互动", "学习进度"],
    coverImage: "/templates/course.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "Video.js"],
    usageCount: 543,
    rating: 4.7,
    createdAt: "2025-01-03",
    tags: ["教育", "视频", "即将开放"],
  },
];
// [CHECKPOINT:FILE_END:templates.mock.ts:SUCCESS]
