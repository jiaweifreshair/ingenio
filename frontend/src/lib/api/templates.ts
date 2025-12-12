/**
 * 模板API客户端
 *
 * 注意：当前使用Mock数据，后续需要对接真实后端API
 */

import {
  Template,
  TemplateCategory,
  TemplateDifficulty,
  TargetPlatform,
  TemplateQueryParams,
  TemplatePageResponse,
  CategoryMeta,
} from "@/types/template";
import { RequirementIntent } from "@/types/intent";
// import { get } from "@/lib/api/client"; // 暂时注释，等后端API实现后启用

/**
 * Mock模板数据
 */
const MOCK_TEMPLATES: Template[] = [
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
    tags: ["电商", "支付", "热门"],
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
    features: [
      "动态发布",
      "关注系统",
      "点赞评论",
      "私信聊天",
      "图片上传",
      "个人主页",
    ],
    coverImage: "/templates/social.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "WebSocket"],
    usageCount: 987,
    rating: 4.6,
    createdAt: "2025-01-08",
    tags: ["社交", "实时通讯"],
  },
  {
    id: "todo-app",
    name: "任务管理工具",
    description: "简洁高效的待办事项管理，支持分类、提醒、统计",
    detailedDescription:
      "功能强大的任务管理应用，支持任务分类、优先级设置、到期提醒、完成统计、数据同步等功能。",
    category: TemplateCategory.TOOLS,
    difficulty: TemplateDifficulty.SIMPLE,
    platforms: [
      TargetPlatform.ANDROID,
      TargetPlatform.IOS,
      TargetPlatform.WEB,
      TargetPlatform.WECHAT,
    ],
    features: ["任务分类", "优先级管理", "提醒通知", "完成统计", "数据同步"],
    coverImage: "/templates/todo.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase"],
    usageCount: 2345,
    rating: 4.9,
    createdAt: "2025-01-12",
    tags: ["工具", "简单", "热门"],
  },
  {
    id: "blog-cms",
    name: "博客内容管理系统",
    description: "支持Markdown编写的博客系统，包含文章管理、分类标签",
    detailedDescription:
      "专业的博客CMS系统，支持Markdown编辑、文章分类、标签管理、评论系统、SEO优化等功能。",
    category: TemplateCategory.CONTENT,
    difficulty: TemplateDifficulty.MEDIUM,
    platforms: [TargetPlatform.WEB],
    features: [
      "Markdown编辑器",
      "文章分类",
      "标签管理",
      "评论系统",
      "SEO优化",
      "草稿箱",
    ],
    coverImage: "/templates/blog.jpg",
    screenshots: [],
    techStack: ["Next.js", "Supabase", "TailwindCSS"],
    usageCount: 756,
    rating: 4.5,
    createdAt: "2025-01-05",
    tags: ["内容", "博客", "Markdown"],
  },
  {
    id: "online-course",
    name: "在线课程平台",
    description: "在线教育平台，支持课程管理、视频播放、作业系统",
    detailedDescription:
      "完整的在线教育解决方案，包含课程管理、视频播放、作业提交、成绩统计、师生互动等功能。",
    category: TemplateCategory.EDUCATION,
    difficulty: TemplateDifficulty.COMPLEX,
    platforms: [TargetPlatform.ANDROID, TargetPlatform.IOS, TargetPlatform.WEB],
    features: [
      "课程管理",
      "视频播放",
      "作业系统",
      "成绩统计",
      "师生互动",
      "学习进度",
    ],
    coverImage: "/templates/course.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "Video.js"],
    usageCount: 543,
    rating: 4.7,
    createdAt: "2025-01-03",
    tags: ["教育", "视频", "互动"],
  },
  {
    id: "chat-app",
    name: "即时通讯应用",
    description: "实时聊天应用，支持单聊、群聊、文件传输",
    detailedDescription:
      "功能完整的即时通讯应用，支持文字聊天、图片视频发送、文件传输、群组管理、消息加密等。",
    category: TemplateCategory.SOCIAL,
    difficulty: TemplateDifficulty.COMPLEX,
    platforms: [TargetPlatform.ANDROID, TargetPlatform.IOS],
    features: [
      "实时聊天",
      "群组管理",
      "文件传输",
      "消息加密",
      "离线消息",
      "已读回执",
    ],
    coverImage: "/templates/chat.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "WebSocket"],
    usageCount: 1456,
    rating: 4.8,
    createdAt: "2025-01-11",
    tags: ["社交", "实时通讯", "热门"],
  },
  {
    id: "fitness-tracker",
    name: "健身追踪工具",
    description: "记录运动数据、制定健身计划、统计健康指标",
    detailedDescription:
      "专业的健身追踪应用，记录运动数据、制定训练计划、统计健康指标、社区分享等功能。",
    category: TemplateCategory.TOOLS,
    difficulty: TemplateDifficulty.MEDIUM,
    platforms: [TargetPlatform.ANDROID, TargetPlatform.IOS],
    features: [
      "运动记录",
      "训练计划",
      "健康统计",
      "社区分享",
      "卡路里计算",
      "目标设定",
    ],
    coverImage: "/templates/fitness.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "Charts"],
    usageCount: 678,
    rating: 4.4,
    createdAt: "2025-01-07",
    tags: ["工具", "健康", "运动"],
  },
  {
    id: "note-taking",
    name: "笔记应用",
    description: "支持富文本、标签分类的笔记工具，可云端同步",
    detailedDescription:
      "强大的笔记应用，支持富文本编辑、标签分类、全文搜索、云端同步、多端协作等功能。",
    category: TemplateCategory.TOOLS,
    difficulty: TemplateDifficulty.SIMPLE,
    platforms: [
      TargetPlatform.ANDROID,
      TargetPlatform.IOS,
      TargetPlatform.WEB,
    ],
    features: [
      "富文本编辑",
      "标签分类",
      "全文搜索",
      "云端同步",
      "多端协作",
      "离线访问",
    ],
    coverImage: "/templates/notes.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "Quill"],
    usageCount: 1890,
    rating: 4.7,
    createdAt: "2025-01-09",
    tags: ["工具", "笔记", "简单"],
  },
  {
    id: "news-aggregator",
    name: "新闻聚合阅读器",
    description: "订阅RSS源、个性化推荐、离线阅读",
    detailedDescription:
      "智能新闻阅读器，支持RSS订阅、个性化推荐、分类筛选、离线阅读、跨平台同步等功能。",
    category: TemplateCategory.CONTENT,
    difficulty: TemplateDifficulty.MEDIUM,
    platforms: [TargetPlatform.ANDROID, TargetPlatform.IOS, TargetPlatform.WEB],
    features: [
      "RSS订阅",
      "个性化推荐",
      "分类筛选",
      "离线阅读",
      "收藏夹",
      "跨平台同步",
    ],
    coverImage: "/templates/news.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "RSS Parser"],
    usageCount: 432,
    rating: 4.3,
    createdAt: "2025-01-04",
    tags: ["内容", "阅读", "RSS"],
  },
  {
    id: "recipe-book",
    name: "电子食谱",
    description: "收藏和分享食谱，支持步骤详解、食材清单",
    detailedDescription:
      "美食爱好者的最佳选择，收藏和分享食谱、步骤详解、食材清单、烹饪计时器、营养计算等功能。",
    category: TemplateCategory.OTHER,
    difficulty: TemplateDifficulty.SIMPLE,
    platforms: [TargetPlatform.ANDROID, TargetPlatform.IOS],
    features: [
      "食谱收藏",
      "步骤详解",
      "食材清单",
      "烹饪计时器",
      "营养计算",
      "社区分享",
    ],
    coverImage: "/templates/recipe.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase"],
    usageCount: 321,
    rating: 4.5,
    createdAt: "2025-01-06",
    tags: ["生活", "美食", "简单"],
  },
  {
    id: "quiz-app",
    name: "在线题库系统",
    description: "刷题练习、错题本、智能组卷",
    detailedDescription:
      "专业的题库系统，支持刷题练习、错题本管理、智能组卷、成绩分析、知识点统计等功能。",
    category: TemplateCategory.EDUCATION,
    difficulty: TemplateDifficulty.MEDIUM,
    platforms: [
      TargetPlatform.ANDROID,
      TargetPlatform.IOS,
      TargetPlatform.WEB,
    ],
    features: [
      "题库管理",
      "错题本",
      "智能组卷",
      "成绩分析",
      "知识点统计",
      "模拟考试",
    ],
    coverImage: "/templates/quiz.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase"],
    usageCount: 891,
    rating: 4.6,
    createdAt: "2025-01-10",
    tags: ["教育", "题库", "考试"],
  },
  {
    id: "calendar-scheduler",
    name: "日历日程管理",
    description: "日历视图、事件提醒、日程共享",
    detailedDescription:
      "功能全面的日历应用，支持多视图（日/周/月）、事件提醒、日程共享、重复事件、时区管理等。",
    category: TemplateCategory.TOOLS,
    difficulty: TemplateDifficulty.MEDIUM,
    platforms: [
      TargetPlatform.ANDROID,
      TargetPlatform.IOS,
      TargetPlatform.WEB,
    ],
    features: [
      "多视图日历",
      "事件提醒",
      "日程共享",
      "重复事件",
      "时区管理",
      "日程导出",
    ],
    coverImage: "/templates/calendar.jpg",
    screenshots: [],
    techStack: ["Kotlin Multiplatform", "Supabase", "FullCalendar"],
    usageCount: 1123,
    rating: 4.7,
    createdAt: "2025-01-08",
    tags: ["工具", "日历", "日程"],
  },
];

/**
 * 获取所有分类的元数据
 */
export async function getCategories(): Promise<CategoryMeta[]> {
  // 模拟网络延迟
  await new Promise((resolve) => setTimeout(resolve, 300));

  const categories: CategoryMeta[] = [
    {
      id: TemplateCategory.ALL,
      name: "全部模板",
      icon: "📦",
      count: MOCK_TEMPLATES.length,
    },
    {
      id: TemplateCategory.ECOMMERCE,
      name: "电商类",
      icon: "🛒",
      count: MOCK_TEMPLATES.filter(
        (t) => t.category === TemplateCategory.ECOMMERCE
      ).length,
    },
    {
      id: TemplateCategory.SOCIAL,
      name: "社交类",
      icon: "💬",
      count: MOCK_TEMPLATES.filter((t) => t.category === TemplateCategory.SOCIAL)
        .length,
    },
    {
      id: TemplateCategory.TOOLS,
      name: "工具类",
      icon: "🔧",
      count: MOCK_TEMPLATES.filter((t) => t.category === TemplateCategory.TOOLS)
        .length,
    },
    {
      id: TemplateCategory.CONTENT,
      name: "内容类",
      icon: "📝",
      count: MOCK_TEMPLATES.filter(
        (t) => t.category === TemplateCategory.CONTENT
      ).length,
    },
    {
      id: TemplateCategory.EDUCATION,
      name: "教育类",
      icon: "🎓",
      count: MOCK_TEMPLATES.filter(
        (t) => t.category === TemplateCategory.EDUCATION
      ).length,
    },
    {
      id: TemplateCategory.OTHER,
      name: "其他",
      icon: "📱",
      count: MOCK_TEMPLATES.filter((t) => t.category === TemplateCategory.OTHER)
        .length,
    },
  ];

  return categories;
}

/**
 * 查询模板列表
 */
export async function queryTemplates(
  params: TemplateQueryParams = {}
): Promise<TemplatePageResponse> {
  // 模拟网络延迟
  await new Promise((resolve) => setTimeout(resolve, 500));

  let filtered = [...MOCK_TEMPLATES];

  // 分类筛选
  if (params.category && params.category !== TemplateCategory.ALL) {
    filtered = filtered.filter((t) => t.category === params.category);
  }

  // 难度筛选
  if (params.difficulty) {
    filtered = filtered.filter((t) => t.difficulty === params.difficulty);
  }

  // 平台筛选
  if (params.platform) {
    filtered = filtered.filter((t) => t.platforms.includes(params.platform!));
  }

  // 搜索关键词
  if (params.search) {
    const keyword = params.search.toLowerCase();
    filtered = filtered.filter(
      (t) =>
        t.name.toLowerCase().includes(keyword) ||
        t.description.toLowerCase().includes(keyword) ||
        t.tags.some((tag) => tag.toLowerCase().includes(keyword))
    );
  }

  // 排序
  switch (params.sortBy) {
    case "newest":
      filtered.sort(
        (a, b) =>
          new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
      break;
    case "popular":
      filtered.sort((a, b) => b.usageCount - a.usageCount);
      break;
    case "rating":
      filtered.sort((a, b) => b.rating - a.rating);
      break;
    default:
      // 默认按使用次数排序
      filtered.sort((a, b) => b.usageCount - a.usageCount);
  }

  // 分页
  const page = params.page || 1;
  const pageSize = params.pageSize || 12;
  const start = (page - 1) * pageSize;
  const end = start + pageSize;
  const items = filtered.slice(start, end);

  return {
    items,
    total: filtered.length,
    page,
    pageSize,
    totalPages: Math.ceil(filtered.length / pageSize),
  };
}

/**
 * 获取模板详情
 */
export async function getTemplateById(id: string): Promise<Template | null> {
  // 模拟网络延迟
  await new Promise((resolve) => setTimeout(resolve, 200));

  return MOCK_TEMPLATES.find((t) => t.id === id) || null;
}

/**
 * 使用模板（增加使用次数）
 */
export async function useTemplate(id: string): Promise<void> {
  // 模拟网络延迟
  await new Promise((resolve) => setTimeout(resolve, 300));

  const template = MOCK_TEMPLATES.find((t) => t.id === id);
  if (template) {
    template.usageCount += 1;
  }
}

/**
 * 收藏模板
 */
export async function favoriteTemplate(id: string): Promise<void> {
  // 模拟网络延迟
  await new Promise((resolve) => setTimeout(resolve, 300));

  // TODO: 实现收藏逻辑
  console.log(`收藏模板: ${id}`);
}

/**
 * V2.0 获取与意图匹配的行业模板
 *
 * 功能：
 * - 根据用户意图类型（克隆/设计/混合）获取匹配的行业模板
 * - 返回按匹配度排序的模板列表
 * - 用于V2.0 wizard流程的模板选择步骤
 *
 * @param intent - 用户意图类型
 * @returns 匹配的模板列表（按匹配度排序）
 * @throws Error 当请求失败或后端返回错误时
 *
 * @example
 * ```typescript
 * const templates = await getMatchedTemplates(RequirementIntent.CLONE_EXISTING_WEBSITE);
 * console.log(`找到 ${templates.length} 个匹配模板`);
 * ```
 */
export async function getMatchedTemplates(
  intent: RequirementIntent
): Promise<Template[]> {
  console.log('[Templates API] 开始获取匹配模板, intent:', intent);

  // TODO Phase 8: 对接真实后端API `/api/v1/templates/matched?intent={intent}`
  // 当前使用Mock数据模拟匹配逻辑

  // 模拟网络延迟
  await new Promise((resolve) => setTimeout(resolve, 500));

  // 模拟匹配逻辑：根据不同意图返回不同的模板子集
  let matchedTemplates: Template[] = [];

  switch (intent) {
    case RequirementIntent.CLONE_EXISTING_WEBSITE:
      // 克隆意图：推荐成熟的、使用率高的模板
      matchedTemplates = MOCK_TEMPLATES.filter(
        (t) =>
          t.usageCount > 800 || // 使用次数高
          t.rating >= 4.5 || // 评分高
          t.category === TemplateCategory.ECOMMERCE || // 电商类（常见克隆需求）
          t.category === TemplateCategory.SOCIAL // 社交类（常见克隆需求）
      ).sort((a, b) => b.usageCount - a.usageCount); // 按使用次数排序
      break;

    case RequirementIntent.DESIGN_FROM_SCRATCH:
      // 从零设计意图：推荐简单易定制的模板作为参考
      matchedTemplates = MOCK_TEMPLATES.filter(
        (t) =>
          t.difficulty === TemplateDifficulty.SIMPLE || // 简单模板
          t.difficulty === TemplateDifficulty.MEDIUM // 中等难度
      ).sort((a, b) => b.rating - a.rating); // 按评分排序
      break;

    case RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE:
      // 混合意图：推荐功能完整且可定制性强的模板
      matchedTemplates = MOCK_TEMPLATES.filter(
        (t) =>
          t.features.length >= 5 || // 功能丰富
          t.platforms.length >= 3 // 多平台支持
      ).sort((a, b) => {
        // 综合排序：使用次数 * 0.6 + 评分 * 40
        const scoreA = a.usageCount * 0.6 + a.rating * 40;
        const scoreB = b.usageCount * 0.6 + b.rating * 40;
        return scoreB - scoreA;
      });
      break;

    default:
      // 未知意图：返回所有模板
      matchedTemplates = [...MOCK_TEMPLATES].sort(
        (a, b) => b.usageCount - a.usageCount
      );
  }

  // 限制返回数量（最多6个）
  const result = matchedTemplates.slice(0, 6);

  console.log(
    `[Templates API] 匹配完成，找到 ${result.length} 个模板:`,
    result.map((t) => t.name)
  );

  return result;

  // TODO Phase 8 后端API实现后，替换为以下真实API调用：
  /*
  try {
    const response = await get<Template[]>(
      `/api/v1/templates/matched?intent=${intent}`
    );

    if (!response.success || !response.data) {
      throw new Error(response.error || '获取匹配模板失败，返回数据为空');
    }

    console.log('[Templates API] 匹配模板获取成功:', response.data.length, '个');
    return response.data;
  } catch (error) {
    console.error('[Templates API] 获取匹配模板失败:', error);
    throw error;
  }
  */
}
