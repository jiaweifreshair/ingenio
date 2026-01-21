/**
 * 模板类型定义
 * 用于模板库页面的数据结构
 */

/**
 * 模板分类枚举
 */
export enum TemplateCategory {
  /** 全部模板 */
  ALL = "all",
  /** 安全竞赛类（本期开放） */
  SAFETY_CHALLENGE = "safety_challenge",
  /** 电商类 */
  ECOMMERCE = "ecommerce",
  /** 社交类 */
  SOCIAL = "social",
  /** 工具类 */
  TOOLS = "tools",
  /** 内容类 */
  CONTENT = "content",
  /** 教育类 */
  EDUCATION = "education",
  /** 其他 */
  OTHER = "other",
}

/**
 * 模板难度枚举
 */
export enum TemplateDifficulty {
  /** 简单 */
  SIMPLE = "simple",
  /** 中等 */
  MEDIUM = "medium",
  /** 复杂 */
  COMPLEX = "complex",
}

/**
 * 目标平台枚举
 */
export enum TargetPlatform {
  /** Android */
  ANDROID = "android",
  /** iOS */
  IOS = "ios",
  /** Web */
  WEB = "web",
  /** 微信小程序 */
  WECHAT = "wechat",
  /** 鸿蒙 */
  HARMONY = "harmony",
}

/**
 * 模板接口
 */
export interface Template {
  /** 模板ID */
  id: string;
  /** 模板名称 */
  name: string;
  /** 模板描述 */
  description: string;
  /** 详细描述 */
  detailedDescription?: string;
  /** 分类 */
  category: TemplateCategory;
  /** 难度 */
  difficulty: TemplateDifficulty;
  /** 支持的平台 */
  platforms: TargetPlatform[];
  /** 功能特性列表 */
  features: string[];
  /** 封面图URL */
  coverImage?: string;
  /** 预览截图列表 */
  screenshots: string[];
  /** 技术栈 */
  techStack: string[];
  /** 使用次数 */
  usageCount: number;
  /** 评分（0-5） */
  rating: number;
  /** 创建时间 */
  createdAt: string;
  /** 演示链接 */
  demoUrl?: string;
  /** 参考网站链接（用于模板克隆） */
  referenceUrl?: string;
  /** 标签 */
  tags: string[];
}

/**
 * 模板查询参数
 */
export interface TemplateQueryParams {
  /** 分类 */
  category?: TemplateCategory;
  /** 难度 */
  difficulty?: TemplateDifficulty;
  /** 平台 */
  platform?: TargetPlatform;
  /** 搜索关键词 */
  search?: string;
  /** 排序方式 */
  sortBy?: "newest" | "popular" | "rating";
  /** 页码 */
  page?: number;
  /** 每页数量 */
  pageSize?: number;
}

/**
 * 模板分页响应
 */
export interface TemplatePageResponse {
  /** 模板列表 */
  items: Template[];
  /** 总数 */
  total: number;
  /** 当前页 */
  page: number;
  /** 每页数量 */
  pageSize: number;
  /** 总页数 */
  totalPages: number;
}

/**
 * 分类元数据
 */
export interface CategoryMeta {
  /** 分类ID */
  id: TemplateCategory;
  /** 显示名称 */
  name: string;
  /** 图标 */
  icon: string;
  /** 模板数量 */
  count: number;
  /** 是否可用（本期开放） */
  isAvailable?: boolean;
}
